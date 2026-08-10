package com.meshapp.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.meshapp.meshcontrol.AudioController
import com.meshapp.meshcontrol.AudioSessionType
import com.meshapp.meshcontrol.AudioFeatureSettings
import com.meshapp.logger.MeshLogger
import kotlinx.coroutines.*
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Loopback test that records audio and plays it back locally.
 * Aligned with VoiceSessionManager for consistent quality and performance.
 */
class VoiceSimulator(
    private val context: Context,
    private val audioController: AudioController
) {
    // 16kHz is standard for VoIP and better supported by hardware filters
    private val sampleRate = VoiceCodec.SAMPLE_RATE
    private val channelIn = AudioFormat.CHANNEL_IN_MONO
    private val channelOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var isRunning = false
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(settings: AudioFeatureSettings) {
        if (isRunning) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            MeshLogger.error("VoiceSimulator", "RECORD_AUDIO not granted")
            return
        }

        val started = audioController.startSession(AudioSessionType.LOOPBACK) {
            isRunning = false
            job?.cancel()
            job = null
        }

        if (!started) {
            MeshLogger.info("VoiceSimulator", "Loopback session rejected by controller")
            return
        }

        isRunning = true

        job = scope.launch {
            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelIn, audioFormat)
            
            val recorder = try {
                AudioRecord(
                    settings.audioSource,
                    sampleRate,
                    channelIn,
                    audioFormat,
                    minBufSize.coerceAtLeast(VoiceCodec.BYTES_PER_FRAME * 2),
                )
            } catch (e: SecurityException) {
                MeshLogger.error("VoiceSimulator", "SecurityException during AudioRecord creation", e.toString())
                isRunning = false
                audioController.stopSession(AudioSessionType.LOOPBACK)
                return@launch
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelOut)
                        .build()
                )
                .setBufferSizeInBytes(VoiceCodec.BYTES_PER_FRAME * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (recorder.state != AudioRecord.STATE_INITIALIZED || track.state != AudioTrack.STATE_INITIALIZED) {
                recorder.release()
                track.release()
                isRunning = false
                audioController.stopSession(AudioSessionType.LOOPBACK)
                return@launch
            }

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
                recorder.startRecording()
                track.play()

                val readBuffer = ByteArray(minBufSize)
                
                while (isActive && isRunning) {
                    val read = recorder.read(readBuffer, 0, readBuffer.size)
                    if (read > 0) {
                        // Process and play back immediately for lowest latency
                        val processed = applyAudioFilters(readBuffer, read, settings.gain, settings.noiseGateThreshold, settings.agcEnabled)
                        track.write(processed, 0, read)
                    } else if (read < 0) {
                        break
                    }
                }
            } catch (e: Exception) {
                MeshLogger.error("VoiceSimulator", "Simulation crash", e.toString())
            } finally {
                withContext(NonCancellable) {
                    runCatching {
                        if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            recorder.stop()
                        }
                        recorder.release()
                    }
                    runCatching {
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            track.stop()
                        }
                        track.release()
                    }
                    
                    isRunning = false
                    audioController.stopSession(AudioSessionType.LOOPBACK)
                }
            }
        }
    }

    fun stop() {
        audioController.stopSession(AudioSessionType.LOOPBACK)
    }

    /**
     * Applies noise gating, software gain, and soft-limiting to a PCM frame.
     * Aligned with VoiceSessionManager logic.
     */
    private fun applyAudioFilters(pcm16: ByteArray, length: Int, gain: Float, threshold: Int, useNoiseGate: Boolean): ByteArray {
        val out = ByteArray(length)
        var i = 0
        while (i < length - 1) {
            val low = pcm16[i].toInt() and 0xFF
            val high = (pcm16[i + 1].toInt() and 0xFF) shl 8
            var sample = high or low
            if (sample and 0x8000 != 0) sample -= 0x10000

            // 1. Noise Gate
            if (useNoiseGate && abs(sample) < threshold) {
                sample = 0
            }

            // 2. Apply Gain
            var processed = sample * gain

            // 3. Soft-Limiting
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
