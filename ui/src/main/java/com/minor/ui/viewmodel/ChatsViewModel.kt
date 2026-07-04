package com.minor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minor.meshcontrol.MeshService
import com.minor.meshcontrol.PeerStatus
import com.minor.messaging.Message
import com.minor.messaging.MessagingService
import com.minor.ui.state.NodeCardState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ChatsViewModel(
    private val messagingService: MessagingService,
    private val meshService: MeshService
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ChatsUiState(nodes = emptyList())
    )

    val uiState: StateFlow<ChatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                messagingService.conversationsStream,
                meshService.peersStream
            ) { conversations, peers ->
                val peerByNodeId = peers.associateBy { it.nodeId.toString() }
                conversations.map { conversation ->
                    val nodeId = conversation.nodeID.toString()
                    val peer = peerByNodeId[nodeId]
                    val name = peer?.name?.takeIf { it.isNotBlank() } ?: shortId(nodeId)
                    NodeCardState(
                        id = nodeId,
                        name = name,
                        isOnline = peer?.status == PeerStatus.ACTIVE,
                        avatarInitials = initialsFrom(name),
                        lastMessagePreview = conversation.lastMessage?.plaintextContent,
                        lastMessageTimestamp = conversation.lastMessage?.let(::formatTime),
                        unreadCount = conversation.unreadCount,
                        isPinned = false
                    )
                }
            }.collect { nodes ->
                _uiState.value = ChatsUiState(nodes = nodes)
            }
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

    private fun shortId(nodeId: String): String =
        if (nodeId.length <= 8) nodeId else "${nodeId.take(8)}..."

    private fun formatTime(message: Message): String {
        return DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(message.composeTimestamp.millis))
    }
}

data class ChatsUiState(
    val nodes: List<NodeCardState> = emptyList()
)
