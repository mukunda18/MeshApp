package com.meshapp.routing

import com.meshapp.model.MessageId
import com.meshapp.model.NodeId
import com.meshapp.model.Payload

/** A directly reachable neighbour discovered via HELLO */
data class Peer(
    val nodeId: NodeId,
    val ip: String,
    val lastSeen: Long
)

/** One entry in the routing table mapping a destination to a next hop */
data class RouteInfo(
    val destinationNodeId: NodeId,
    val nextHopNodeId: NodeId,
    val hopCount: Int,
    val routeTimestamp: Long,
    val valid: Boolean = true
)

/** Emitted whenever the routing table is modified */
sealed class RouteEvent {
    data class Updated(val route: RouteInfo) : RouteEvent()
    data class Invalidated(val nodeId: NodeId) : RouteEvent()
}

/** Emitted whenever the peer table changes so upper layers can observe neighbour status */
sealed class PeerEvent {
    data class Added(val peer: Peer) : PeerEvent()
    data class Updated(val peer: Peer) : PeerEvent()
    data class Removed(val nodeId: NodeId) : PeerEvent()
}

/** An outbound message waiting in the Sender queue */
data class QueuedMessage(
    val messageId: MessageId,
    val payload: Payload.Message,
    val destinationNodeId: NodeId,
    val enqueueTime: Long = System.currentTimeMillis()
)

/** Delivery lifecycle states emitted on the Sender status channel */
enum class SendStatus { SENT, DELIVERED, FAILED }

/**
 * Returns a bounded window of up to limit elements starting at offset
 * Wraps around to the front of the list once the end is reached so repeated
 * calls with an advancing offset eventually cycle through every element
 * Used to rotate a large routing table across successive HELLO emissions
 * Returns an empty list when the source list is empty or limit is not positive
 */
fun <T> List<T>.chunkFrom(offset: Int, limit: Int): List<T> {
    if (isEmpty() || limit <= 0) return emptyList()
    val start = offset.mod(size)
    val result = mutableListOf<T>()
    var index = start
    while (result.size < limit && result.size < size) {
        result.add(this[index])
        index = (index + 1) % size
    }
    return result
}