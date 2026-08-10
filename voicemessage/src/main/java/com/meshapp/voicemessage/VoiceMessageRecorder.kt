package com.meshapp.voicemessage

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.meshapp.logger.MeshLogger
import com.meshapp.meshcontrol.AudioController
import com.meshapp.meshcontrol.AudioFeatureSettings
import com.meshapp.meshcontrol.AudioSessionType
import com.meshapp.voice.VoiceCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Records a voice message using the same capture pipeline as live calls
 * (16 kHz mono PCM, mu-law compressed via VoiceCodec), but persists the
 * encoded frames to a local file instead of streaming them over UDP.
 *
 * Output is written using the naming convention in [VoiceMessageFile], so
 * FileTransferService can treat the result as an ordinary file with no
 * protocol changes.
 */
class VoiceMessageRecorder(
    private val context: Context,
    private val audioController: AudioController,
    private val codec: VoiceCodec = VoiceCodec(),
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private var outputStream: FileOutputStream? = null
    private var outputFile: File? = null
    private var frameCount: Long = 0L

    val isRecording: Boolean
        get() = recordingJob?.isActive == true

    /**
     * Starts capturing audio into a temp file. Returns false if RECORD_AUDIO
     * has not been granted or a recording is already in progress.
     */
    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(settings: AudioFeatureSettings): Boolean {
        if (isRecording) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            MeshLogger.error("VoiceMessageRecorder", "RECORD_AUDIO permission not granted")
            return false
        }

        val started = audioController.startSession(AudioSessionType.VOICE_MESSAGE, mode = settings.audioMode) {
            cancel()
        }
        if (!started) return false

        val minBufferSize = AudioRecord.getMinBufferSize(
            VoiceCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, VoiceCodec.BYTES_PER_FRAME * 4)

        val record = try {
            AudioRecord(
                settings.audioSource,
                VoiceCodec.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: SecurityException) {
            MeshLogger.error("VoiceMessageRecorder", "SecurityException during AudioRecord creation", e.toString())
            audioController.stopSession(AudioSessionType.VOICE_MESSAGE)
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            MeshLogger.error("VoiceMessageRecorder", "AudioRecord failed to initialize")
            record.release()
            audioController.stopSession(AudioSessionType.VOICE_MESSAGE)
            return false
        }

        // Hardware audio enhancements
        runCatching {
            if (settings.aecEnabled && AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId)?.enabled = true
            if (settings.nsEnabled && NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId)?.enabled = true
            if (settings.agcEnabled && AutomaticGainControl.isAvailable()) AutomaticGainControl.create(record.audioSessionId)?.enabled = true
        }

        val file = VoiceMessageFile.newOutgoingTempFile(context)
        outputFile = file
        outputStream = FileOutputStream(file)
        audioRecord = record
        frameCount = 0L

        record.startRecording()

        recordingJob = scope.launch {
            val pcmBuffer = ByteArray(VoiceCodec.BYTES_PER_FRAME)
            while (isRecording) {
                val bytesRead = record.read(pcmBuffer, 0, pcmBuffer.size)
                if (bytesRead == VoiceCodec.BYTES_PER_FRAME) {
                    val processed = applyGain(pcmBuffer, settings.gain)
                    val encoded = codec.encode(processed)
                    withContext(Dispatchers.IO) {
                        outputStream?.write(encoded)
                    }
                    frameCount++
                }
            }
        }
        return true
    }

    private fun applyGain(pcm16: ByteArray, gain: Float): ByteArray {
        if (gain == 1.0f) return pcm16
        val out = ByteArray(pcm16.size)
        var i = 0
        while (i < (pcm16.size - 1)) {
            val low = pcm16[i].toInt() and 0xFF
            val high = (pcm16[i + 1].toInt() and 0xFF) shl 8
            var sample = high or low
            if (sample and 0x8000 != 0) sample -= 0x10000

            val amplified = (sample * gain).toInt().coerceIn(-32768, 32767)
            out[i] = (amplified and 0xFF).toByte()
            out[i + 1] = ((amplified shr 8) and 0xFF).toByte()
            i += 2
        }
        return out
    }

    /**
     * Stops capture and finalizes the file. Returns the recorded voice
     * message (file + duration), or null if nothing usable was recorded.
     */
    fun stop(): RecordedVoiceMessage? {
        if (!isRecording) return null
        
        audioRecord?.apply {
            runCatching { stop() }
            release()
        }
        audioRecord = null
        
        recordingJob?.cancel()
        recordingJob = null

        outputStream?.flush()
        outputStream?.close()
        outputStream = null

        val durationMs = frameCount * VoiceCodec.FRAME_MS
        val tempFile = outputFile ?: return null
        outputFile = null

        audioController.stopSession(AudioSessionType.VOICE_MESSAGE)

        if (durationMs <= 0L) {
            tempFile.delete()
            return null
        }

        val finalFile = VoiceMessageFile.renameWithDuration(tempFile, durationMs)
        return RecordedVoiceMessage(finalFile, durationMs)
    }

    /** Discards an in-progress recording without producing a file. */
    fun cancel() {
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.apply {
            runCatching { stop() }
            release()
        }
        audioRecord = null
        outputStream?.close()
        outputStream = null
        outputFile?.delete()
        outputFile = null
        audioController.stopSession(AudioSessionType.VOICE_MESSAGE)
    }

    fun release() {
        codec.release()
    }
}

data class RecordedVoiceMessage(
    val file: File,
    val durationMs: Long
)
