package com.meshapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshapp.meshcontrol.MeshService
import com.meshapp.meshcontrol.PeerState
import com.meshapp.meshcontrol.PeerStatus
import com.meshapp.messaging.Message
import com.meshapp.messaging.MessageDeliveryStatus
import com.meshapp.messaging.MessagingService
import com.meshapp.model.NodeId
import com.meshapp.voice.VoiceCallManager
import com.meshapp.routing.PeerEvent
import com.meshapp.ui.state.ConversationMessageUiState
import com.meshapp.ui.state.ConversationUiState
import com.meshapp.ui.state.NodeCardState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConversationViewModel(
    private val ownNodeId: NodeId,
    private val messagingService: MessagingService,
    private val meshService: MeshService,
    private val voiceCallManager: VoiceCallManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConversationUiState(node = NodeCardState("", "", false, "")))
    private val _peerMap = MutableStateFlow<Map<String, PeerState>>(emptyMap())
    private val _routeNodeIds = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var activeNodeId: NodeId? = null

    init {
        collectPeerEvents()
        startRouteRefreshLoop()
        observeConversationUpdates()
    }

    fun initialize(nodeId: String) {
        if (nodeId.isBlank()) return
        val parsedNodeId = parseNodeId(nodeId) ?: return
        if (activeNodeId?.toString() == parsedNodeId.toString()) return
        activeNodeId = parsedNodeId

        messagingService.markConversationAsRead(parsedNodeId)

        val history = messagingService.getHistory(parsedNodeId)
        val peer = _peerMap.value[nodeId]
        val isInRouteTable = nodeId in _routeNodeIds.value
        val displayName = peer?.name?.takeIf { it.isNotBlank() } ?: shortId(nodeId)
        
        _uiState.value = ConversationUiState(
            node = NodeCardState(
                id = nodeId,
                name = displayName,
                isOnline = (peer?.status == PeerStatus.ACTIVE) || isInRouteTable,
                avatarInitials = initialsFrom(displayName)
            ),
            messages = history.map { message ->
                message.toUiMessage(isOutgoing = message.senderNodeId.toString() == ownNodeId.toString())
            }
        )
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val destination = activeNodeId ?: return
        try {
            messagingService.send(destinationNodeID = destination, plaintext = text.trim())
        } catch (e: Exception) {
            android.util.Log.e("ConversationViewModel", "Failed to send message", e)
        }
    }

    fun dial() {
        val destination = activeNodeId ?: return
        voiceCallManager.dial(destination)
    }

    private fun startRouteRefreshLoop() {
        viewModelScope.launch {
            while (true) {
                _routeNodeIds.value = meshService.getRoutes().map { it.destinationNodeId.toString() }.toSet()
                delay(5_000)
            }
        }
    }

    private fun collectPeerEvents() {
        viewModelScope.launch {
            meshService.peerEventsStream.collect { event ->
                _peerMap.update { current ->
                    val next = when (event) {
                        is PeerEvent.Added -> {
                            current + (event.peer.nodeId.toString() to
                                PeerState(event.peer.nodeId, event.peer.ip, null, null, PeerStatus.ACTIVE, event.peer.lastSeen))
                        }
                        is PeerEvent.Updated -> {
                            val existing = current[event.peer.nodeId.toString()]
                            current + (event.peer.nodeId.toString() to
                                PeerState(event.peer.nodeId, event.peer.ip, existing?.name, existing?.publicKey, PeerStatus.ACTIVE, event.peer.lastSeen))
                        }
                        is PeerEvent.Removed -> current - event.nodeId.toString()
                    }
                    // Trigger an immediate route refresh when peers change
                    _routeNodeIds.value = meshService.getRoutes().map { it.destinationNodeId.toString() }.toSet()
                    next
                }
            }
        }
    }

    private fun observeConversationUpdates() {
        viewModelScope.launch {
            combine(
                messagingService.messagesStream,
                _peerMap,
                _routeNodeIds
            ) { messageUpdate, peerMap, routeNodeIds ->
                Triple(messageUpdate, peerMap, routeNodeIds)
            }.collect { (messageUpdate, peerMap, routeNodeIds) ->
                val destination = activeNodeId ?: return@collect
                if (messageUpdate.nodeID.toString() != destination.toString()) return@collect

                messagingService.markConversationAsRead(destination)

                val peer = peerMap[destination.toString()]
                val isInRouteTable = destination.toString() in routeNodeIds
                val currentNode = _uiState.value.node
                val updatedNodeName = peer?.name?.takeIf { it.isNotBlank() } ?: currentNode.name
                val messageList = messagingService.getHistory(destination)
                
                _uiState.value = _uiState.value.copy(
                    node = currentNode.copy(
                        name = updatedNodeName,
                        isOnline = (peer?.status == PeerStatus.ACTIVE) || isInRouteTable,
                        avatarInitials = initialsFrom(updatedNodeName)
                    ),
                    messages = messageList.map { msg ->
                        msg.toUiMessage(isOutgoing = msg.senderNodeId.toString() == ownNodeId.toString())
                    }
                )
            }
        }
    }

    private fun Message.toUiMessage(isOutgoing: Boolean): ConversationMessageUiState {
        return ConversationMessageUiState(
            id = messageId.toString(),
            text = plaintextContent,
            isOutgoing = isOutgoing,
            timestamp = formatTime(this),
            deliveryStatusLabel = if (isOutgoing) deliveryStatus.toUiLabel() else null,
            deliveryStatus = if (isOutgoing) deliveryStatus else null
        )
    }

    private fun MessageDeliveryStatus.toUiLabel(): String = when (this) {
        MessageDeliveryStatus.QUEUED -> "Queued"
        MessageDeliveryStatus.SENT -> "Sent"
        MessageDeliveryStatus.DELIVERED -> "Delivered"
        MessageDeliveryStatus.READ -> "Read"
        MessageDeliveryStatus.FAILED -> "Failed"
    }

    private fun formatTime(message: Message): String {
        return DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(message.composeTimestamp.millis))
    }

    private fun shortId(nodeId: String): String =
        if (nodeId.length <= 8) nodeId else "${nodeId.take(8)}..."

    private fun initialsFrom(value: String): String {
        val tokens = value.trim().split(" ").filter { it.isNotBlank() }
        return when {
            tokens.isEmpty() -> "NA"
            tokens.size == 1 -> tokens[0].take(2).uppercase()
            else -> "${tokens[0].first()}${tokens[1].first()}".uppercase()
        }
    }

    private fun parseNodeId(value: String): NodeId? {
        if (value.length != 64) return null
        return runCatching {
            val bytes = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            NodeId(bytes)
        }.getOrNull()
    }
}
