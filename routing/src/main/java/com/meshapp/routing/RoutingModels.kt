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