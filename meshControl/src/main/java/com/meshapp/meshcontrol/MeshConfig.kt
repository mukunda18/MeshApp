package com.meshapp.meshcontrol

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaRecorder
import com.meshapp.model.NodeId
import com.meshapp.model.PublicKey

/**
 * Standardized audio processing settings for a specific feature.
 */
data class AudioFeatureSettings(
    val gain: Float,
    val aecEnabled: Boolean,
    val nsEnabled: Boolean,
    val agcEnabled: Boolean,
    val noiseGateThreshold: Int,
    val audioSource: Int,
    val audioMode: Int,
    val usage: Int,
    val contentType: Int
)

/**
 * Central audio configuration for all mesh voice features.
 */
data class AudioConfig(
    val callSettings: AudioFeatureSettings = AudioFeatureSettings(
        gain = 1.0f,
        aecEnabled = true,
        nsEnabled = true,
        agcEnabled = true,
        noiseGateThreshold = 100,
        audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        audioMode = AudioManager.MODE_IN_COMMUNICATION,
        usage = AudioAttributes.USAGE_VOICE_COMMUNICATION,
        contentType = AudioAttributes.CONTENT_TYPE_SPEECH
    ),
    val messageSettings: AudioFeatureSettings = AudioFeatureSettings(
        gain = 1.0f,
        aecEnabled = false,
        nsEnabled = true,
        agcEnabled = false,
        noiseGateThreshold = 50,
        audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION,
        audioMode = AudioManager.MODE_NORMAL,
        usage = AudioAttributes.USAGE_MEDIA,
        contentType = AudioAttributes.CONTENT_TYPE_SPEECH
    )
)

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
    val udpReceiveTimeoutMs: Int = 500,
    val callDialingTimeoutMs: Long = 60_000L,
    val callRingingTimeoutMs: Long = 45_000L,
    val callEndedDisplayMs: Long = 3_000L,
    val audioConfig: AudioConfig = AudioConfig()
) {
    init {
        require(udpBroadcastPort in 1..65535) { "Invalid UDP port: $udpBroadcastPort" }
        require(tcpPort in 1..65535) { "Invalid TCP port: $tcpPort" }
        require(helloIntervalMs > 0) { "helloIntervalMs must be positive" }
        require(peerTimeoutMs > helloIntervalMs) { "peerTimeoutMs must be greater than helloIntervalMs" }
        require(maxHopCount > 0) { "maxHopCount must be positive" }
    }
}
