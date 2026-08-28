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
    private val scope = CoroutineScope(Dispatchers.IO)

    val isPlaying: Boolean
        get() = playbackJob?.isActive == true

    fun play(file: File, onComplete: () -> Unit = {}) {
        if (isPlaying) stop()
        if (!file.exists()) {
            MeshLogger.error("VoiceMessagePlayer", "File does not exist: ${file.absolutePath}")
            return
        }

        val started = audioController.startSession(AudioSessionType.VOICE_MESSAGE, mode = settings.audioMode) {
            stop()
        }
        if (!started) return

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
        audioTrack = track
        track.play()

        playbackJob = scope.launch {
            file.inputStream().use { input ->
                val compressedFrame = ByteArray(VoiceCodec.COMPRESSED_FRAME_SIZE)
                while (isPlaying) {
                    val bytesRead = input.read(compressedFrame)
                    if (bytesRead != VoiceCodec.COMPRESSED_FRAME_SIZE) break
                    val pcm = codec.decode(compressedFrame)
                    track.write(pcm, 0, pcm.size)
                }
            }
            track.stop()
            track.release()
            audioTrack = null
            audioController.stopSession(AudioSessionType.VOICE_MESSAGE)
            onComplete()
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        audioTrack?.apply {
            runCatching { stop() }
            release()
        }
        audioTrack = null
        audioController.stopSession(AudioSessionType.VOICE_MESSAGE)
    }

    fun release() {
        codec.release()
    }
}
