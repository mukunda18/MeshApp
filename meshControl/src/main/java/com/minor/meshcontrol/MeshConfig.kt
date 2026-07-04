package com.minor.meshcontrol

import com.minor.model.NodeId
import com.minor.model.PublicKey

/**
 * Central mesh runtime configuration.
 *
 * MeshApplication/AppContainer should populate the identity fields from the
 * Identity module before constructing MeshService.
 */
data class MeshConfig(
    val udpBroadcastPort: Int,
    val tcpPort: Int,
    val helloIntervalMs: Long = 5_000L,
    val peerTimeoutMs: Long = 15_000L,
    val peerReaperCheckMs: Long = 5_000L,
    val routeExpiryMs: Long = 60_000L,
    val routeExpiryCheckIntervalMs: Long = 10_000L,
    val rreqRetryTimeoutMs: Long = 8_000L,
    val deliveryAckTimeoutMs: Long = 8_000L,
    val maxHopCount: Int = 8,
    val originTimestampFreshnessWindowMs: Long = 30_000L,
    val ownNodeId: NodeId,
    val ownPublicKey: PublicKey,
    val ownName: String,
    val routeStateIntervalMs: Long = 5_000L,
) {
    init {
        require(udpBroadcastPort in 1..65535) { "Invalid UDP port: $udpBroadcastPort" }
        require(tcpPort in 1..65535) { "Invalid TCP port: $tcpPort" }
        require(helloIntervalMs > 0) { "helloIntervalMs must be positive" }
        require(peerTimeoutMs > helloIntervalMs) { "peerTimeoutMs must be greater than helloIntervalMs" }
        require(maxHopCount > 0) { "maxHopCount must be positive" }
    }
}
