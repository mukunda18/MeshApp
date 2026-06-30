package com.minor.routing

/** Packet type codes from the protocol specification */
object PacketType {
    const val HELLO: Byte = 0x01
    const val MSG: Byte = 0x02
    const val RREQ: Byte = 0x03
    const val RREP: Byte = 0x04
    const val ACK: Byte = 0x05
    const val RERR: Byte = 0x06
}

/** Minimal header fields needed by routing logic */
data class PacketHeader(
    val type: Byte,
    val flags: Byte,
    var hopCount: Int,
    val ttl: Int,
    var sourceNodeID: String,
    val destinationNodeID: String,
    val id: Long,
    val originTimestamp: Long
)

/** Generic packet wrapper carrying header and optional payload bytes */
data class Packet(
    val header: PacketHeader,
    val payload: ByteArray = ByteArray(0)
)

/** Route entry advertised inside a HELLO payload */
data class RouteEntry(
    val nodeID: String,
    val hopCount: Int,
    val name: String
)

/** HELLO packet payload */
data class HelloPayload(
    val name: String,
    val publicKey: ByteArray,
    val routes: List<RouteEntry>
)

/** RERR packet payload listing unreachable destinations */
data class RerrPayload(
    val destinations: List<String>
)

/** Snapshot of a known peer */
data class Peer(
    val nodeID: String,
    val ip: String,
    val name: String,
    val lastSeen: Long
)

/** Snapshot of a routing table entry */
data class RouteInfo(
    val destinationNodeID: String,
    val nextHopNodeID: String,
    val hopCount: Int,
    val lastUpdated: Long,
    val valid: Boolean = true
)

/** Event emitted whenever the peer table changes */
sealed class PeerEvent {
    data class Added(val peer: Peer) : PeerEvent()
    data class Updated(val peer: Peer) : PeerEvent()
    data class Removed(val nodeID: String) : PeerEvent()
}

const val BROADCAST_ID = "00000000000000000000000000000000000000000000000000000000000000"