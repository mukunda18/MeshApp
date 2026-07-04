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
    val routeStateIntervalMs: Long = 5_000L
)
