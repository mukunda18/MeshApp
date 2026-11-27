package com.meshapp.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.meshapp.logger.MeshLogger
import com.meshapp.meshcontrol.MeshService
import com.meshapp.model.MessageId
import com.meshapp.model.NodeId
import com.meshapp.model.Payload
import com.meshapp.model.VoicePacket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.tanh

/**
 * Manages a real-time encrypted voice call session over the P2P mesh.
 *
 * Recording & Transmission:
 * - Records 16-bit PCM at 16 kHz mono in 20 ms frames.
 * - Encodes with mu-law and encrypts via AES-GCM (ECDH-derived key).
 * - Transmits frames over UDP.
 *
 * Reception & Playback:
 * - Collects, decrypts, and reorders frames in a sequence-indexed jitter buffer.
 * - Pre-buffers incoming frames before playback to prevent jitter/choppiness.
 * - Applies soft-clipping (tanh) to avoid distortion when volume/gain is boosted.
 */
class VoiceSessionManager(
    private val context: Context,
    private val meshService: MeshService,
    private val callId: MessageId,
    private val peerNodeId: NodeId,
    private val callCrypto: CallCrypto,
    private val settings: com.meshapp.meshcontrol.AudioFeatureSettings,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var isRunning = false
    private var sessionJob: Job? = null

    private val codec = VoiceCodec()
    private val nextSequenceNumber = AtomicInteger(0)

    // Jitter buffer management
    private val jitterBuffer = ConcurrentHashMap<Int, ByteArray>()
    private val expectedSequence = AtomicInteger(0)

    // Buffer tuning parameters
    private var isBuffering = true
    private val initialBufferCount = 3 // ~60 ms initial delay cushion
    private val maxJitterPackets = 6    // ~120 ms latency ceiling

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (isRunning) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            MeshLogger.error("VoiceSessionManager", "RECORD_AUDIO permission not granted")
            return
        }

        isRunning = true

        sessionJob = scope.launch {
            try {
                launch { receiveLoop() }
                launch { recordLoop() }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                MeshLogger.error("VoiceSessionManager", "Session error", e.toString())
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        sessionJob?.cancel()
        sessionJob = null
        codec.release()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun CoroutineScope.recordLoop() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            VoiceCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
            MeshLogger.error("VoiceSessionManager", "Invalid AudioRecord parameters")
            return
        }

        val recorder = try {
            AudioRecord(
                settings.audioSource,
                VoiceCodec.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize.coerceAtLeast(VoiceCodec.BYTES_PER_FRAME * 2)
            )
        } catch (e: SecurityException) {
            MeshLogger.error("VoiceSessionManager", "SecurityException during AudioRecord creation", e.toString())
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            MeshLogger.error("VoiceSessionManager", "AudioRecord initialization failed")
            recorder.release()
            return
        }

        val readBuffer = ByteArray(minBufferSize.coerceAtLeast(VoiceCodec.BYTES_PER_FRAME))
        val frameAccumulator = ByteBuffer.allocate(VoiceCodec.BYTES_PER_FRAME * 2)
            .order(ByteOrder.LITTLE_ENDIAN)

        recorder.startRecording()

        // Hardware audio enhancements
        runCatching {
            if (settings.aecEnabled && AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(recorder.audioSessionId)?.enabled = true
            }
            if (settings.nsEnabled && NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(recorder.audioSessionId)?.enabled = true
            }
            if (settings.agcEnabled && AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(recorder.audioSessionId)?.enabled = true
            }
        }

        try {
            while (isActive && isRunning) {
                val read = recorder.read(readBuffer, 0, readBuffer.size)
                if (read > 0) {
                    frameAccumulator.put(readBuffer, 0, read)

                    while (frameAccumulator.position() >= VoiceCodec.BYTES_PER_FRAME) {
                        val frame = ByteArray(VoiceCodec.BYTES_PER_FRAME)
                        frameAccumulator.flip()
                        frameAccumulator[frame]
                        frameAccumulator.compact()

                        val seq = nextSequenceNumber.getAndIncrement()
                        sendFrame(seq, frame)
                    }
                } else if (read < 0) {
                    val error = when (read) {
                        AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
                        AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
                        AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
                        else -> "Unknown error $read"
                    }
                    MeshLogger.error("VoiceSessionManager", "AudioRecord read error: $error")
                    break
                }
            }
        } catch (e: Exception) {
            MeshLogger.error("VoiceSessionManager", "Record loop crash", e.toString())
        } finally {
            runCatching {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            }
            recorder.release()
        }
    }

    private fun sendFrame(sequenceNumber: Int, pcmFrame: ByteArray) {
        try {
            val processed = applyAudioFilters(pcmFrame, gain = settings.gain, useNoiseGate = settings.agcEnabled)
            val encoded = codec.encode(processed)
            val encrypted = callCrypto.encrypt(sequenceNumber, encoded)

            require(encrypted.size <= 0xFFFF) {
                "Encrypted voice frame too large: ${encrypted.size}"
            }

            val packet = VoicePacket(
                callId = callId,
                sequenceNumber = sequenceNumber,
                timestampMs = System.currentTimeMillis(),
                encodedAudio = encrypted
            )
            meshService.sendVoice(peerNodeId, Payload.Voice(packet))
        } catch (e: Exception) {
            MeshLogger.error("VoiceSessionManager", "Failed to send voice frame seq=$sequenceNumber", e.toString())
        }
    }

    private suspend fun receiveLoop() {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(settings.usage)
                    .setContentType(settings.contentType)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(VoiceCodec.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(VoiceCodec.BYTES_PER_FRAME * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track.play()

        try {
            meshService.incomingVoiceStream.collect { (sourceNodeId, voicePayload) ->
                if (sourceNodeId != peerNodeId) return@collect
                val packet = voicePayload.packet
                if (!packet.callId.bytes.contentEquals(callId.bytes)) return@collect

                if (packet.sequenceNumber < expectedSequence.get()) return@collect

                val decrypted = callCrypto.decrypt(packet.sequenceNumber, packet.encodedAudio)
                    ?: return@collect
                val pcm = codec.decode(decrypted)

                jitterBuffer[packet.sequenceNumber] = pcm
                drainJitterBuffer(track)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            MeshLogger.error("VoiceSessionManager", "Receive loop error", e.toString())
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private fun drainJitterBuffer(track: AudioTrack) {
        // Pre-buffer frames before playback starts to withstand initial jitter
        if (isBuffering) {
            if (jitterBuffer.size >= initialBufferCount) {
                isBuffering = false
                val minSeq = jitterBuffer.keys.minOrNull() ?: 0
                expectedSequence.set(minSeq)
            } else {
                return
            }
        }

        val minSeq = jitterBuffer.keys.minOrNull() ?: return

        // Jump sequence forward if intermediate packets were dropped by network
        if (expectedSequence.get() < minSeq) {
            expectedSequence.set(minSeq)
        }

        // Limit latency build-up if buffer overfills
        if (jitterBuffer.size > maxJitterPackets) {
            expectedSequence.set(minSeq)
        }

        while (true) {
            val seq = expectedSequence.get()
            val frame = jitterBuffer.remove(seq) ?: break

            val amplified = applyAudioFilters(frame, gain = settings.gain, useNoiseGate = false)
            track.write(amplified, 0, amplified.size)
            expectedSequence.incrementAndGet()
        }

        // Re-enter buffering state if the stream completely stalls
        if (jitterBuffer.isEmpty()) {
            isBuffering = true
        }
    }

    /**
     * Applies noise gating, software gain amplification, and soft-clipping (tanh)
     * to prevent digital waveform truncation at high gain.
     */
    private fun applyAudioFilters(pcm16: ByteArray, gain: Float, useNoiseGate: Boolean): ByteArray {
        if (!useNoiseGate && gain == 1.0f) return pcm16

        val out = ByteArray(pcm16.size)
        var i = 0
        while (i < pcm16.size - 1) {
            val low = pcm16[i].toInt() and 0xFF
            val high = (pcm16[i + 1].toInt() and 0xFF) shl 8
            var sample = high or low
            if (sample and 0x8000 != 0) sample -= 0x10000

            // 1. Noise Gate
            if (useNoiseGate && abs(sample) < settings.noiseGateThreshold) {
                sample = 0
            }

            // 2. Gain
            val amplified = sample * gain

            // 3. Hyperbolic Tangent Soft Clipping
            val maxVal = 32767.0f
            val normalized = amplified / maxVal
            val softClipped = if (abs(normalized) > 0.8f) {
                tanh(normalized.toDouble()).toFloat() * maxVal
            } else {
                amplified
            }

            val finalSample = softClipped.toInt().coerceIn(-32768, 32767)

            out[i] = (finalSample and 0xFF).toByte()
            out[i + 1] = ((finalSample shr 8) and 0xFF).toByte()
            i += 2
        }
        return out
    }
}
