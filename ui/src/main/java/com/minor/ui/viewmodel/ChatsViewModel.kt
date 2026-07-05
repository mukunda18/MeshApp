package com.minor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minor.meshcontrol.MeshService
import com.minor.meshcontrol.PeerState
import com.minor.meshcontrol.PeerStatus
import com.minor.messaging.Message
import com.minor.messaging.MessagingService
import com.minor.model.KnownNode
import com.minor.model.NodesStore
import com.minor.routing.PeerEvent
import com.minor.ui.state.NodeCardState
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

class ChatsViewModel(
    private val messagingService: MessagingService,
    private val meshService: MeshService,
    private val nodesStore: NodesStore
) : ViewModel() {
    private val _peerMap = MutableStateFlow<Map<String, PeerState>>(emptyMap())

    // Known nodes from NodesStore (have name + publicKey from HELLO/routing)
    private val _knownNodes = MutableStateFlow<List<KnownNode>>(emptyList())

    // Node IDs that have a valid route (includes nodes NOT in NodesStore)
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
                delay(5_000)
                refreshFromRouting()
            }
        }

        // Build the UI list from all sources
        viewModelScope.launch {
            combine(
                _knownNodes,
                _peerMap,
                _routeNodeIds,
                messagingService.conversationsStream
            ) { knownNodes, peerMap, routeNodeIds, conversations ->
                val convByNodeId = conversations.associateBy { it.nodeID.toString() }
                val onlineIds = peerMap.keys + routeNodeIds

                val seenNodeIds = mutableSetOf<String>()
                val nodes = mutableListOf<NodeCardState>()

                // 1. All nodes in NodesStore (have real names)
                knownNodes.forEach { node ->
                    val nodeId = node.nodeId.toString()
                    seenNodeIds.add(nodeId)
                    val conv = convByNodeId[nodeId]
                    val name = node.name.ifBlank { shortId(nodeId) }
                    nodes += NodeCardState(
                        id = nodeId,
                        name = name,
                        isOnline = nodeId in onlineIds,
                        avatarInitials = initialsFrom(name),
                        lastMessagePreview = conv?.lastMessage?.plaintextContent,
                        lastMessageTimestamp = conv?.lastMessage?.let(::formatTime),
                        unreadCount = conv?.unreadCount ?: 0,
                        isPinned = false
                    )
                }

                // 2. Route-only nodes: have a route but are NOT in NodesStore (no name/key)
                routeNodeIds.forEach { nodeId ->
                    if (nodeId !in seenNodeIds) {
                        seenNodeIds.add(nodeId)
                        val conv = convByNodeId[nodeId]
                        nodes += NodeCardState(
                            id = nodeId,
                            name = shortId(nodeId),
                            isOnline = true,
                            avatarInitials = initialsFrom(shortId(nodeId)),
                            lastMessagePreview = conv?.lastMessage?.plaintextContent,
                            lastMessageTimestamp = conv?.lastMessage?.let(::formatTime),
                            unreadCount = conv?.unreadCount ?: 0,
                            isPinned = false
                        )
                    }
                }

                // 3. Orphan conversations: message history but not in NodesStore or route table
                convByNodeId.forEach { (nodeId, conv) ->
                    if (nodeId !in seenNodeIds) {
                        nodes += NodeCardState(
                            id = nodeId,
                            name = shortId(nodeId),
                            isOnline = nodeId in onlineIds,
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
        _knownNodes.value = nodesStore.listNodes()
        _routeNodeIds.value = meshService.getRoutes().map { it.destinationNodeId.toString() }.toSet()
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
