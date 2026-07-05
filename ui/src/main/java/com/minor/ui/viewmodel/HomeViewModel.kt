package com.minor.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minor.meshcontrol.MeshService
import com.minor.meshcontrol.MeshState
import com.minor.meshcontrol.PeerStatus
import com.minor.network.NetworkInfo
import com.minor.network.NetworkScanner
import com.minor.ui.state.HomeNodeUiState
import com.minor.ui.state.HomeUiState
import com.minor.ui.state.ProfileUiState
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
    appName: String,
    deviceName: String
) : AndroidViewModel(application) {
    private val networkInfo = NetworkInfo(application)

    private val _uiState = MutableStateFlow(
        HomeUiState(
            appName = appName,
            profile = ProfileUiState(
                name = deviceName,
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
        refreshNetworkInterfaces()
    }

    fun toggleMesh() {
        viewModelScope.launch {
            if (_uiState.value.isMeshOn) {
                meshService.stop()
            } else {
                meshService.start()
            }
        }
    }

    fun refreshNetworkInterfaces() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = NetworkScanner.getNetworkInterfaceInfo().size
            _uiState.update { it.copy(networkInterfaceCount = count) }
        }
    }

    private fun observeMeshState() {
        viewModelScope.launch {
            combine(
                meshService.meshStateStream,
                meshService.peersStream,
                meshService.routeStateStream
            ) { meshState, peers, routeState ->
                Triple(meshState, peers, routeState)
            }.collect { (meshState, peers, routeState) ->
                val hopMap = routeState.routes.associate { it.destinationNodeId.toString() to it.hopCount }
                val nodes = peers
                    .sortedBy { it.name ?: it.nodeId.toString() }
                    .map { peer ->
                        HomeNodeUiState(
                            nodeId = peer.nodeId.toString(),
                            name = peer.name ?: shortId(peer.nodeId.toString()),
                            avatarInitials = initialsFrom(peer.name ?: peer.nodeId.toString()),
                            isOnline = peer.status == PeerStatus.ACTIVE,
                            status = peer.status.name,
                            ip = peer.ip,
                            hopCount = hopMap[peer.nodeId.toString()]
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
