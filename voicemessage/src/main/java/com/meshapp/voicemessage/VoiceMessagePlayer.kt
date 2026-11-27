package com.meshapp.voicemessage

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.meshapp.logger.MeshLogger
import com.meshapp.meshcontrol.AudioController
import com.meshapp.meshcontrol.AudioFeatureSettings
import com.meshapp.meshcontrol.AudioSessionType
import com.meshapp.voice.VoiceCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Plays back a mu-law encoded voice message file produced by
 * [VoiceMessageRecorder], reusing the same [VoiceCodec] frame decoding used
 * for live call playback.
 */
class VoiceMessagePlayer(
    private val audioController: AudioController,
    private val settings: AudioFeatureSettings,
    private val codec: VoiceCodec = VoiceCodec()
) {
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val trackLock = Any()
    private val scope = CoroutineScope(Dispatchers.IO)

    val isPlaying: Boolean
        get() = playbackJob?.isActive == true

    fun play(
        file: File,
        onProgress: (Float) -> Unit = {},
        onComplete: () -> Unit = {}
    ): Boolean {
        if (isPlaying) stop()
        if (!file.exists()) {
            MeshLogger.error("VoiceMessagePlayer", "File does not exist: ${file.absolutePath}")
            return false
        }

        val started = audioController.startSession(AudioSessionType.VOICE_MESSAGE, mode = settings.audioMode) {
            stop()
        }
        if (!started) return false

        val minBufferSize = AudioTrack.getMinBufferSize(
            VoiceCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

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
            .setBufferSizeInBytes(maxOf(minBufferSize, VoiceCodec.BYTES_PER_FRAME * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        synchronized(trackLock) { audioTrack = track }
        track.play()

        playbackJob = scope.launch {
            var completed = false
            try {
                val totalSamples = file.length() / VoiceCodec.COMPRESSED_FRAME_SIZE *
                    VoiceCodec.SAMPLES_PER_FRAME
                var samplesWritten = 0L

                file.inputStream().use { input ->
                    val compressedFrame = ByteArray(VoiceCodec.COMPRESSED_FRAME_SIZE)
                    while (isPlaying) {
                        var frameBytesRead = 0
                        while (frameBytesRead < compressedFrame.size) {
                            val bytesRead = input.read(
                                compressedFrame,
                                frameBytesRead,
                                compressedFrame.size - frameBytesRead
                            )
                            if (bytesRead < 0) break
                            frameBytesRead += bytesRead
                        }
                        if (frameBytesRead != compressedFrame.size) break

                        val pcm = codec.decode(compressedFrame)
                        var pcmBytesWritten = 0
                        while (pcmBytesWritten < pcm.size && isPlaying) {
                            val bytesWritten = track.write(
                                pcm,
                                pcmBytesWritten,
                                pcm.size - pcmBytesWritten,
                                AudioTrack.WRITE_BLOCKING
                            )
                            if (bytesWritten <= 0) {
                                MeshLogger.error(
                                    "VoiceMessagePlayer",
                                    "AudioTrack write failed: $bytesWritten"
                                )
                                break
                            }
                            pcmBytesWritten += bytesWritten
                            samplesWritten += bytesWritten / 2
                            val playedSamples = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                            onProgress(
                                (playedSamples.toFloat() / totalSamples.coerceAtLeast(1L))
                                    .coerceIn(0f, 1f)
                            )
                        }
                        if (pcmBytesWritten != pcm.size) break
                    }
                }

                while (isPlaying && samplesWritten > 0L) {
                    val playedSamples = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                    onProgress((playedSamples.toFloat() / totalSamples.coerceAtLeast(1L)).coerceIn(0f, 1f))
                    if (playedSamples >= samplesWritten) {
                        completed = true
                        break
                    }
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) break
                    delay(20)
                }
            }
            finally {
                releaseTrack(track)
                audioController.stopSession(AudioSessionType.VOICE_MESSAGE)
                if (completed) {
                    onProgress(1f)
                    onComplete()
                }
            }
        }
        return true
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        val track = synchronized(trackLock) {
            audioTrack.also { audioTrack = null }
        }
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioController.stopSession(AudioSessionType.VOICE_MESSAGE)
    }

    private fun releaseTrack(track: AudioTrack) {
        val shouldRelease = synchronized(trackLock) {
            if (audioTrack === track) {
                audioTrack = null
                true
            } else {
                false
            }
        }
        if (shouldRelease) {
            runCatching { track.stop() }
            track.release()
        }
    }

    fun release() {
        codec.release()
    }
}
