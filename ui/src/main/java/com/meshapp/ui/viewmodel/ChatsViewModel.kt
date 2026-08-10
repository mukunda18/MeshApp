package com.meshapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshapp.meshcontrol.MeshService
import com.meshapp.meshcontrol.PeerState
import com.meshapp.meshcontrol.PeerStatus
import com.meshapp.messaging.Message
import com.meshapp.messaging.MessagingService
import com.meshapp.routing.PeerEvent
import com.meshapp.model.NodeId
import com.meshapp.voice.VoiceCallManager
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
import kotlin.time.Duration.Companion.milliseconds

class ChatsViewModel(
    private val messagingService: MessagingService,
    private val meshService: MeshService,
    private val ownNodeId: NodeId
) : ViewModel() {
    private val _peerMap = MutableStateFlow<Map<String, PeerState>>(emptyMap())

    // Node IDs that have a valid route
    private val _routeNodeIds = MutableStateFlow<Set<String>>(emptySet())

    private val _uiState = MutableStateFlow(ChatsUiState(nodes = emptyList()))
    val uiState: StateFlow<ChatsUiState> = _uiState.asStateFlow()

    init {
        refreshFromRouting()

        // React to peer events + refresh routing tables
        viewModelScope.launch {
            meshService.peerEventsStream.collect { event ->
                _peerMap.update { current ->
                    when (event) {
                        is PeerEvent.Added -> {
                            refreshFromRouting()
                            current + (event.peer.nodeId.toString() to
                                PeerState(event.peer.nodeId, event.peer.ip, null, null, PeerStatus.ACTIVE, event.peer.lastSeen))
                        }
                        is PeerEvent.Updated -> {
                            val existing = current[event.peer.nodeId.toString()]
                            current + (event.peer.nodeId.toString() to
                                PeerState(event.peer.nodeId, event.peer.ip, existing?.name, existing?.publicKey, PeerStatus.ACTIVE, event.peer.lastSeen))
                        }
                        is PeerEvent.Removed -> {
                            refreshFromRouting()
                            current - event.nodeId.toString()
                        }
                    }
                }
            }
        }

        // Periodic route refresh (routes change without peer events, e.g. RREP received)
        viewModelScope.launch {
            while (true) {
                delay(5_000.milliseconds)
                refreshFromRouting()
            }
        }

        // Build the UI list from all sources
        viewModelScope.launch {
            combine(
                _peerMap,
                _routeNodeIds,
                messagingService.conversationsStream
            ) { peerMap, routeNodeIds, conversations ->
                val convByNodeId = conversations.associateBy { it.nodeID.toString() }
                val onlineIds = (peerMap.keys + routeNodeIds)
                    .filter { it != ownNodeId.toString() }

                val seenNodeIds = mutableSetOf<String>()
                seenNodeIds.add(ownNodeId.toString())

                val nodes = mutableListOf<NodeCardState>()

                // 1. Online nodes (Peers + Routes)
                onlineIds.forEach { nodeId ->
                    if (seenNodeIds.add(nodeId)) {
                        val conv = convByNodeId[nodeId]
                        val peer = peerMap[nodeId]
                        val name = peer?.name ?: shortId(nodeId)
                        nodes += NodeCardState(
                            id = nodeId,
                            name = name,
                            isOnline = true,
                            avatarInitials = initialsFrom(name),
                            lastMessagePreview = conv?.lastMessage?.plaintextContent,
                            lastMessageTimestamp = conv?.lastMessage?.let(::formatTime),
                            unreadCount = conv?.unreadCount ?: 0,
                            isPinned = false
                        )
                    }
                }

                // 2. Offline conversations: message history but no current route
                convByNodeId.forEach { (nodeId, conv) ->
                    if (seenNodeIds.add(nodeId)) {
                        nodes += NodeCardState(
                            id = nodeId,
                            name = shortId(nodeId),
                            isOnline = false,
                            avatarInitials = initialsFrom(shortId(nodeId)),
                            lastMessagePreview = conv.lastMessage?.plaintextContent,
                            lastMessageTimestamp = conv.lastMessage?.let(::formatTime),
                            unreadCount = conv.unreadCount,
                            isPinned = false
                        )
                    }
                }

                // Online nodes first, then alphabetical
                nodes.sortWith(compareByDescending<NodeCardState> { it.isOnline }.thenBy { it.name })
                nodes
            }.collect { nodes ->
                _uiState.value = ChatsUiState(nodes = nodes)
            }
        }
    }

    private fun refreshFromRouting() {
        val updatedRoutes = meshService.getRoutes()
            .map { it.destinationNodeId.toString() }
            .toSet()

        _routeNodeIds.update { updatedRoutes }
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
