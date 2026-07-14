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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatsViewModel(
    private val messagingService: MessagingService,
    private val meshService: MeshService,
    private val nodesStore: NodesStore
) : ViewModel() {
    private val _peerMap = MutableStateFlow<Map<String, PeerState>>(emptyMap())
    private val _knownNodes = MutableStateFlow<List<KnownNode>>(emptyList())
    private val _routeNodeIds = MutableStateFlow<Set<String>>(emptySet())

    private val _uiState = MutableStateFlow(ChatsUiState(nodes = emptyList()))
    val uiState: StateFlow<ChatsUiState> = _uiState.asStateFlow()

    init {
        // 1. Initial load off the main thread so startup doesn't freeze
        viewModelScope.launch {
            refreshFromRouting()
        }

        // 2. React to peer events + refresh routing tables safely
        viewModelScope.launch {
            meshService.peerEventsStream.collect { event ->
                // Trigger the background data refresh first
                refreshFromRouting()

                _peerMap.update { current ->
                    when (event) {
                        is PeerEvent.Added -> {
                            current + (event.peer.nodeId.toString() to
                                    PeerState(event.peer.nodeId, event.peer.ip, null, null, PeerStatus.ACTIVE, event.peer.lastSeen))
                        }
                        is PeerEvent.Updated -> {
                            val existing = current[event.peer.nodeId.toString()]
                            current + (event.peer.nodeId.toString() to
                                    PeerState(event.peer.nodeId, event.peer.ip, existing?.name, existing?.publicKey, PeerStatus.ACTIVE, event.peer.lastSeen))
                        }
                        is PeerEvent.Removed -> {
                            current - event.nodeId.toString()
                        }
                    }
                }
            }
        }

        // 3. Periodic route refresh shifted entirely to IO thread pool
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                refreshFromRouting()
            }
        }

        // 4. Build the UI list computation off-thread
        viewModelScope.launch {
            combine(
                _knownNodes,
                _peerMap,
                _routeNodeIds,
                messagingService.conversationsStream
            ) { knownNodes, peerMap, routeNodeIds, conversations ->
                // Shifting computational work out of the layout layer
                val convByNodeId = conversations.associateBy { it.nodeID.toString() }
                val onlineIds = peerMap.keys + routeNodeIds

                val seenNodeIds = mutableSetOf<String>()
                val nodes = mutableListOf<NodeCardState>()

                // All nodes in NodesStore
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

                // Route-only nodes
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

                // Orphan conversations
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

                nodes.sortWith(compareByDescending<NodeCardState> { it.isOnline }.thenBy { it.name })
                nodes
            }.collect { nodes ->
                _uiState.value = ChatsUiState(nodes = nodes)
            }
        }
    }

    // Explicitly switches context to background workers for heavy lifting
    private suspend fun refreshFromRouting() = withContext(Dispatchers.IO) {
        val fetchedNodes = nodesStore.listNodes()
        val fetchedRoutes = meshService.getRoutes().map { it.destinationNodeId.toString() }.toSet()

        // Push the values back to the UI state flows
        _knownNodes.value = fetchedNodes
        _routeNodeIds.value = fetchedRoutes
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