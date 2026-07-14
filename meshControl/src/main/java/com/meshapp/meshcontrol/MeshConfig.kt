package com.meshapp.meshcontrol

import com.meshapp.model.NodeId
import com.meshapp.model.PublicKey

/**
 * Central mesh runtime configuration.
 */
data class MeshConfig(
    val udpBroadcastPort: Int,
    val tcpPort: Int,
    val helloIntervalMs: Long = 5_000L,
    val peerTimeoutMs: Long = 15_000L,
    val peerReaperCheckMs: Long = 5_000L,
    val routeExpiryMs: Long = 60_000L,
    val routeExpiryCheckIntervalMs: Long = 10_000L,
    val rreqRetryTimeoutMs: Long = 15_000L,
    val deliveryAckTimeoutMs: Long = 15_000L,
    val maxHopCount: Int = 8,
    val originTimestampFreshnessWindowMs: Long = 30_000L,
    val ownNodeId: NodeId,
    val ownPublicKey: PublicKey,
    val ownName: String,
    val routeStateIntervalMs: Long = 5_000L,
    val identityResolutionTimeoutMs: Long = 15_000L,
    val streamBufferCapacity: Int = 64,
    val tcpIdleTimeoutMs: Long = 60_000L,
    val udpMaxPacketSize: Int = 65536,
    val udpBufferCapacity: Int = 1024,
    val routeRetryBackoffMs: Long = 500L,
    val tcpReadTimeoutMs: Int = 500,
    val tcpAcceptTimeoutMs: Int = 500,
    val udpReceiveTimeoutMs: Int = 500
) {
    init {
        require(udpBroadcastPort in 1..65535) { "Invalid UDP port: $udpBroadcastPort" }
        require(tcpPort in 1..65535) { "Invalid TCP port: $tcpPort" }
        require(helloIntervalMs > 0) { "helloIntervalMs must be positive" }
        require(peerTimeoutMs > helloIntervalMs) { "peerTimeoutMs must be greater than helloIntervalMs" }
        require(maxHopCount > 0) { "maxHopCount must be positive" }
    }
}
