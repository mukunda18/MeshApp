package com.minor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minor.meshcontrol.MeshService
import com.minor.meshcontrol.PeerStatus
import com.minor.messaging.Message
import com.minor.messaging.MessageDeliveryStatus
import com.minor.messaging.MessagingService
import com.minor.model.NodeId
import com.minor.ui.state.ConversationMessageUiState
import com.minor.ui.state.ConversationUiState
import com.minor.ui.state.NodeCardState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ConversationViewModel(
    private val ownNodeId: NodeId,
    private val messagingService: MessagingService,
    private val meshService: MeshService
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConversationUiState(node = NodeCardState("", "", false, "")))

    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var activeNodeId: NodeId? = null

    init {
        observeConversationUpdates()
    }

    fun initialize(nodeId: String) {
        if (nodeId.isBlank()) return
        val parsedNodeId = parseNodeId(nodeId) ?: return
        if (activeNodeId?.toString() == parsedNodeId.toString()) return
        activeNodeId = parsedNodeId

        val history = messagingService.getHistory(parsedNodeId)
        val peer = meshService.peersStream.value.firstOrNull { it.nodeId.toString() == nodeId }
        val displayName = peer?.name?.takeIf { it.isNotBlank() } ?: shortId(nodeId)
        _uiState.value = ConversationUiState(
            node = NodeCardState(
                id = nodeId,
                name = displayName,
                isOnline = peer?.status == PeerStatus.ACTIVE,
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
        messagingService.send(destinationNodeID = destination, plaintext = text.trim())
    }

    private fun observeConversationUpdates() {
        viewModelScope.launch {
            combine(
                messagingService.messagesStream,
                meshService.peersStream
            ) { messageUpdate, peers ->
                messageUpdate to peers
            }.collect { (messageUpdate, peers) ->
                val destination = activeNodeId ?: return@collect
                if (messageUpdate.nodeID.toString() != destination.toString()) return@collect

                val peer = peers.firstOrNull { it.nodeId.toString() == destination.toString() }
                val currentNode = _uiState.value.node
                val updatedNodeName = peer?.name?.takeIf { it.isNotBlank() } ?: currentNode.name
                val messageList = messagingService.getHistory(destination)
                _uiState.value = _uiState.value.copy(
                    node = currentNode.copy(
                        name = updatedNodeName,
                        isOnline = peer?.status == PeerStatus.ACTIVE,
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
            id = messageId.value.toString(),
            text = plaintextContent,
            isOutgoing = isOutgoing,
            timestamp = formatTime(this),
            deliveryStatusLabel = if (isOutgoing) deliveryStatus.toUiLabel() else null
        )
    }

    private fun MessageDeliveryStatus.toUiLabel(): String = when (this) {
        MessageDeliveryStatus.QUEUED -> "Queued"
        MessageDeliveryStatus.SENT -> "Sent"
        MessageDeliveryStatus.DELIVERED -> "Delivered"
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
        if (value.length != 64 || value.length % 2 != 0) return null
        return runCatching {
            val bytes = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            NodeId(bytes)
        }.getOrNull()
    }
}
