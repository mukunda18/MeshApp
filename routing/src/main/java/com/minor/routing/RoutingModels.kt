package com.minor.routing

import com.minor.model.MessageId
import com.minor.model.NodeId
import com.minor.model.PublicKey

/** A directly reachable neighbour discovered via HELLO */
data class Peer(
    val nodeId: NodeId,
    val ip: String,
    val name: String,
    val publicKey: PublicKey,
    val lastSeen: Long
)

/** One entry in the routing table mapping a destination to a next hop */
data class RouteInfo(
    val destinationNodeId: NodeId,
    val name: String,
    val nextHopNodeId: NodeId,
    val hopCount: Int,
    val lastUpdated: Long,
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
    val content: String,
    val destinationNodeId: NodeId,
    var rreqFlag: Boolean = false,
    val enqueueTime: Long = System.currentTimeMillis()
)

/** Delivery lifecycle states emitted on the Sender status channel */
enum class SendStatus { SENT, DELIVERED, FAILED }