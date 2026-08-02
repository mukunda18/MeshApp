package com.meshapp.voice

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.AudioAttributes
import android.media.AudioManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.milliseconds

class VoiceSimulator(private val context: Context) {
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val playbackGain = 2.0f
    private val noiseGateThreshold = 600

    private var isRunning = false
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (isRunning) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        isRunning = true
        // Set mode to communication for call routing and volume control
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        job = scope.launch {
            try {
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize,
                )

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
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                val audioQueue = ConcurrentLinkedQueue<ByteArray>()

                recorder.startRecording()
                track.play()

                val recordingJob = launch {
                    while (isActive && isRunning) {
                        val buffer = ByteArray(bufferSize)
                        val read = recorder.read(buffer, 0, bufferSize)
                        if (read > 0) {
                            audioQueue.add(buffer.copyOf(read))
                        }
                    }
                }

                val playbackJob = launch {
                    while (isActive && isRunning) {
                        val chunk = audioQueue.poll()
                        if (chunk != null) {
                            val amplified = applyGain(chunk)
                            track.write(amplified, 0, amplified.size)
                        } else {
                            delay(10.milliseconds)
                        }
                    }
                }

                joinAll(recordingJob, playbackJob)

                recorder.stop()
                recorder.release()
                track.stop()
                track.release()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(NonCancellable) {
                    isRunning = false
                    audioManager.mode = AudioManager.MODE_NORMAL
                }
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        audioManager.mode = AudioManager.MODE_NORMAL
        job?.cancel()
        job = null
    }

    private fun applyGain(pcm16: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(pcm16).order(ByteOrder.LITTLE_ENDIAN)
        // We only process complete 16-bit samples (2 bytes each).
        // Any trailing byte in an odd-sized buffer is ignored to prevent crashes.
        val samplesCount = pcm16.size / 2
        val out = ByteArray(samplesCount * 2)
        val outBuffer = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)

        repeat(samplesCount) {
            var sample = buffer.short.toInt()

            // 1. Noise Gate: Prune low-level background noise.
            if (Math.abs(sample) < noiseGateThreshold) {
                sample = 0
            }

            // 2. Apply Gain.
            var amplified = sample * playbackGain

            // 3. Simple Soft-Limiting.
            val limit = 30000.0f
            if (amplified > limit) {
                amplified = limit + (amplified - limit) * 0.2f
            } else if (amplified < -limit) {
                amplified = -limit + (amplified + limit) * 0.2f
            }

            outBuffer.putShort(amplified.toInt().coerceIn(-32768, 32767).toShort())
        }
        return out
    }
}
