package com.meshapp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meshapp.meshcontrol.MeshService
import com.meshapp.meshcontrol.MeshState
import com.meshapp.meshcontrol.PeerState
import com.meshapp.meshcontrol.PeerStatus
import com.meshapp.model.PublicKey
import com.meshapp.network.NetworkInfo
import com.meshapp.network.NetworkScanner
import com.meshapp.routing.PeerEvent
import com.meshapp.security.NodesStore
import com.meshapp.ui.state.HomeNodeUiState
import com.meshapp.ui.state.HomeUiState
import com.meshapp.ui.state.ProfileUiState
import com.meshapp.voice.CallState
import com.meshapp.voice.VoiceCallManager
import com.meshapp.model.NodeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    application: Application,
    private val meshService: MeshService,
    private val meshController: MeshController,
    private val voiceCallManager: VoiceCallManager,
    private val nodesStore: NodesStore,
    private val ownNodeId: NodeId,
    private val ownPublicKey: PublicKey,
    appName: String,
    deviceName: String,
    nodeId: String
) : AndroidViewModel(application) {
    private val networkInfo = NetworkInfo(application)

    private val _peerMap = MutableStateFlow<Map<String, PeerState>>(emptyMap())

    private val _uiState = MutableStateFlow(
        HomeUiState(
            appName = appName,
            profile = ProfileUiState(
                name = deviceName,
                nodeId = nodeId,
                avatarInitials = initialsFrom(deviceName)
            ),
            isStaApSupported = networkInfo.isStaApSupported(),
            isStaApLikelySupported = networkInfo.isLikelySupported(),
            networkInterfaceCount = 0
        )
    )

    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeMeshState()
        observeVoiceSimState()
        observeVoiceCallState()
        refreshNetworkInterfaces()
    }

    fun dial(nodeId: String) {
        val peer = _peerMap.value[nodeId] ?: return
        voiceCallManager.dial(peer.nodeId)
        _uiState.update { it.copy(isCallMinimized = false) }
    }

    fun acceptCall() {
        voiceCallManager.accept()
    }

    fun rejectCall() {
        voiceCallManager.reject()
    }

    fun cancelCall() {
        voiceCallManager.cancel()
    }

    fun hangupCall() {
        voiceCallManager.hangup()
    }

    fun minimizeCall() {
        _uiState.update { it.copy(isCallMinimized = true) }
    }

    fun maximizeCall() {
        _uiState.update { it.copy(isCallMinimized = false) }
    }

    private fun observeVoiceCallState() {
        viewModelScope.launch {
            voiceCallManager.callState.collect { state ->
                _uiState.update {
                    val wasIdle = it.voiceCallState is CallState.Idle
                    val isIdle = state is CallState.Idle
                    it.copy(
                        voiceCallState = state,
                        isCallMinimized = when {
                            isIdle -> false
                            wasIdle -> false
                            else -> it.isCallMinimized
                        }
                    )
                }
            }
        }
    }

    fun toggleMesh() {
        // Drive the foreground service so the notification reflects mesh state.
        // The UI state itself updates by observing meshService.stateStream.
        if (meshService.isRunning) {
            meshController.stop()
        } else {
            meshController.start()
        }
    }

    fun toggleVoiceSimulation() {
        if (meshController.isVoiceSimActive.value) {
            meshController.stopVoiceSim()
        } else {
            // Ensure mesh is running first, as simulation lives in the service
            if (!meshService.isRunning) {
                meshController.start()
            }
            meshController.startVoiceSim()
        }
    }

    private fun observeVoiceSimState() {
        viewModelScope.launch {
            meshController.isVoiceSimActive.collect { active ->
                _uiState.update { it.copy(isVoiceSimActive = active) }
            }
        }
    }

    fun refreshNetworkInterfaces() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = NetworkScanner.getNetworkInterfaceInfo().size
            _uiState.update { it.copy(networkInterfaceCount = count) }
        }
    }

    fun updateDeviceName(newName: String) {
        val sanitized = newName.trim()
        if (sanitized.isBlank()) return

        val previousName = _uiState.value.profile.name

        _uiState.update {
            it.copy(
                profile = it.profile.copy(
                    name = sanitized,
                    avatarInitials = initialsFrom(sanitized)
                )
            )
        }

        // Keep shared identity/name mapping aligned so other UI viewmodels
        // (Chats/Conversation) pick up the updated display name source.
        nodesStore.addOrUpdateNode(ownNodeId, sanitized, ownPublicKey)
        nodesStore.listNodes()
            .filter { it.name == previousName }
            .forEach { knownNode ->
                nodesStore.addOrUpdateNode(knownNode.nodeId, sanitized, knownNode.publicKey)
            }
    }

    private fun observeMeshState() {
        // Seed with peers already known to the running service so they appear
        // immediately when the screen is reopened (not just on future events).
        _peerMap.update {
            meshService.getPeers().associate { peer ->
                peer.nodeId.toString() to PeerState(
                    peer.nodeId, peer.ip, null, null, PeerStatus.ACTIVE, peer.lastSeen
                )
            }
        }

        viewModelScope.launch {
            meshService.peerEventsStream.collect { event ->
                _peerMap.update { current ->
                    when (event) {
                        is PeerEvent.Added -> current + (event.peer.nodeId.toString() to
                            PeerState(event.peer.nodeId, event.peer.ip, null, null, PeerStatus.ACTIVE, event.peer.lastSeen))
                        is PeerEvent.Updated -> {
                            val existing = current[event.peer.nodeId.toString()]
                            current + (event.peer.nodeId.toString() to
                                PeerState(event.peer.nodeId, event.peer.ip, existing?.name, existing?.publicKey, PeerStatus.ACTIVE, event.peer.lastSeen))
                        }
                        is PeerEvent.Removed -> current - event.nodeId.toString()
                    }
                }
            }
        }

        viewModelScope.launch {
            combine(
                meshService.stateStream,
                _peerMap
            ) { meshState, peerMap ->
                meshState to peerMap
            }.collect { (meshState, peerMap) ->
                val knownNamesByNodeId = nodesStore.listNodes().associate {
                    it.nodeId.toString() to it.name
                }

                val nodes = peerMap.values
                    .sortedBy { peer ->
                        val resolvedName = knownNamesByNodeId[peer.nodeId.toString()]
                            ?.takeIf { it.isNotBlank() }
                            ?: peer.name
                            ?: peer.nodeId.toString()
                        resolvedName.lowercase()
                    }
                    .map { peer ->
                        val resolvedName = knownNamesByNodeId[peer.nodeId.toString()]
                            ?.takeIf { it.isNotBlank() }
                            ?: peer.name
                            ?: shortId(peer.nodeId.toString())

                        HomeNodeUiState(
                            nodeId = peer.nodeId.toString(),
                            name = resolvedName,
                            avatarInitials = initialsFrom(resolvedName),
                            isOnline = peer.status == PeerStatus.ACTIVE,
                            status = peer.status.name,
                            ip = peer.ip,
                            hopCount = null
                        )
                    }
                _uiState.update {
                    it.copy(
                        isMeshOn = meshState == MeshState.RUNNING,
                        meshStatusLabel = meshState.name,
                        connectionStatus = connectionStatus(meshState, nodes.count { n -> n.isOnline }),
                        connectedNodes = nodes
                    )
                }
            }
        }
    }

    private fun connectionStatus(meshState: MeshState, activePeers: Int): String {
        return when {
            meshState != MeshState.RUNNING -> "Mesh is offline"
            activePeers == 0 -> "Running - no nearby nodes"
            activePeers == 1 -> "Running - 1 nearby node"
            else -> "Running - $activePeers nearby nodes"
        }
    }

    private fun initialsFrom(value: String): String {
        val tokens = value.trim().split(" ").filter { it.isNotBlank() }
        return when {
            tokens.isEmpty() -> "NA"
            tokens.size == 1 -> tokens[0].take(2).uppercase()
            else -> "${tokens[0].first()}${tokens[1].first()}".uppercase()
        }
    }

    private fun shortId(nodeId: String): String {
        return if (nodeId.length <= 8) nodeId else "${nodeId.take(8)}..."
    }
}
