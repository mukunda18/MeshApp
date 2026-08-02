package com.meshapp.voice

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresPermission
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

/**
 * Manages a real-time encrypted voice call session.
 *
 * - Records 16-bit PCM at 16 kHz mono.
 * - Encodes with mu-law (20 ms frames).
 * - Encrypts each frame with the per-call AES-GCM key derived from ECDH.
 * - Sends VOICE packets via UDP through the mesh.
 * - Receives VOICE packets, decrypts, places them in a small jitter buffer,
 *   reorders by sequence number, and plays them via AudioTrack.
 */
class VoiceSessionManager(
    private val context: Context,
    private val meshService: MeshService,
    private val callId: MessageId,
    private val peerNodeId: NodeId,
    private val callCrypto: CallCrypto,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var isRunning = false
    private var sessionJob: Job? = null

    private val codec = VoiceCodec()
    private val nextSequenceNumber = AtomicInteger(0)

    // Jitter buffer: sequence number -> decrypted PCM frame.
    private val jitterBuffer = ConcurrentHashMap<Int, ByteArray>()
    private val expectedSequence = AtomicInteger(0)
    private val maxJitterPackets = 6 // ~120 ms at 20 ms/frame

    // Keep unity gain for natural call audio and to avoid clipping artifacts.
    private val playbackGain = 1.0f

    // Disabled by default; aggressive gating causes choppy/buzzy speech.
    private val noiseGateThreshold = 0

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (isRunning) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            MeshLogger.error("VoiceSessionManager", "RECORD_AUDIO permission not granted")
            return
        }

        isRunning = true
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

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
        audioManager.mode = AudioManager.MODE_NORMAL
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

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            VoiceCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize.coerceAtLeast(VoiceCodec.BYTES_PER_FRAME * 2)
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            MeshLogger.error("VoiceSessionManager", "AudioRecord initialization failed")
            recorder.release()
            return
        }

        // Buffer to collect raw bytes from the hardware.
        val readBuffer = ByteArray(minBufferSize.coerceAtLeast(VoiceCodec.BYTES_PER_FRAME))
        // Accumulator to assemble exact 20ms (BYTES_PER_FRAME) chunks.
        val frameAccumulator = ByteBuffer.allocate(VoiceCodec.BYTES_PER_FRAME * 2)
            .order(ByteOrder.LITTLE_ENDIAN)

        recorder.startRecording()

        // Attempt to enable hardware Echo Cancellation, Noise Suppression, and AGC.
        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(recorder.audioSessionId)?.enabled = true
        }
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(recorder.audioSessionId)?.enabled = true
        }
        if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(recorder.audioSessionId)?.enabled = true
        }

        try {
            while (isActive && isRunning) {
                val read = recorder.read(readBuffer, 0, readBuffer.size)
                if (read > 0) {
                    frameAccumulator.put(readBuffer, 0, read)
                    
                    // While we have at least one full frame (640 bytes) in the accumulator...
                    while (frameAccumulator.position() >= VoiceCodec.BYTES_PER_FRAME) {
                        // 1. Extract exactly one frame.
                        val frame = ByteArray(VoiceCodec.BYTES_PER_FRAME)
                        frameAccumulator.flip()
                        frameAccumulator.get(frame)
                        
                        // 2. Compact the buffer to move remaining bytes to the front.
                        frameAccumulator.compact()
                        
                        // 3. Send it.
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
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } catch (e: Exception) {
                MeshLogger.error("VoiceSessionManager", "Error stopping recorder", e.toString())
            }
            recorder.release()
        }
    }

    private fun sendFrame(sequenceNumber: Int, pcmFrame: ByteArray) {
        try {
            // Keep sender path clean and avoid destructive pre-processing.
            val processed = applyAudioFilters(pcmFrame, gain = 1.0f, useNoiseGate = false)
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
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
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
            track.stop()
            track.release()
        }
    }

    private fun drainJitterBuffer(track: AudioTrack) {
        val minSeq = jitterBuffer.keys.minOrNull() ?: return

        // If we're waiting for a packet that is older than the oldest one in the buffer,
        // we likely missed it. Jump to the oldest available to resume playback.
        if (expectedSequence.get() < minSeq) {
            expectedSequence.set(minSeq)
        }

        // Latency control: if buffer grows too large, fast-forward to the oldest available.
        if (jitterBuffer.size > maxJitterPackets) {
            expectedSequence.set(minSeq)
        }

        while (true) {
            val seq = expectedSequence.get()
            val frame = jitterBuffer.remove(seq) ?: break
            // Apply playback gain and soft limiting on the receiver side.
            val amplified = applyAudioFilters(frame, gain = playbackGain, useNoiseGate = false)
            track.write(amplified, 0, amplified.size)
            expectedSequence.incrementAndGet()
        }
    }

    /**
     * Applies noise gating, software gain, and soft-limiting to a PCM frame.
     */
    private fun applyAudioFilters(pcm16: ByteArray, gain: Float, useNoiseGate: Boolean): ByteArray {
        if (!useNoiseGate && gain == 1.0f) {
            return pcm16
        }
        val out = ByteArray(pcm16.size)
        var i = 0
        while (i < pcm16.size - 1) {
            val low = pcm16[i].toInt() and 0xFF
            val high = (pcm16[i + 1].toInt() and 0xFF) shl 8
            var sample = high or low
            if (sample and 0x8000 != 0) sample -= 0x10000

            // 1. Noise Gate (if requested, typically for the sender).
            if (useNoiseGate && Math.abs(sample) < noiseGateThreshold) {
                sample = 0
            }

            // 2. Apply Gain.
            var processed = sample * gain

            // 3. Advanced Soft-Limiting: Smoothly compress samples as they approach clipping.
            // This prevents "sharp static" by rounding off the peaks.
            val limit = 28000.0f
            if (processed > limit) {
                processed = limit + (processed - limit) * 0.1f
            } else if (processed < -limit) {
                processed = -limit + (processed + limit) * 0.1f
            }

            val finalSample = processed.toInt().coerceIn(-32768, 32767)
            out[i] = (finalSample and 0xFF).toByte()
            out[i + 1] = ((finalSample shr 8) and 0xFF).toByte()
            i += 2
        }
        return out
    }
}
