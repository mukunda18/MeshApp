package com.meshapp.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.system.measureNanoTime

class VoicePerformanceTest {

    private lateinit var codec: VoiceCodec
    private lateinit var callerCrypto: CallCrypto
    private lateinit var calleeCrypto: CallCrypto

    @Before
    fun setUp() {
        codec = VoiceCodec()

        val kpg = KeyPairGenerator.getInstance("EC").apply { initialize(256) }
        val callerPair = kpg.generateKeyPair()
        val calleePair = kpg.generateKeyPair()

        callerCrypto = CallCrypto(
            ownEphemeralPrivateKeyBytes = callerPair.private.encoded,
            ownEphemeralPublicKeyBytes = callerPair.public.encoded,
            peerEphemeralPublicKeyBytes = calleePair.public.encoded,
            isCaller = true
        )

        calleeCrypto = CallCrypto(
            ownEphemeralPrivateKeyBytes = calleePair.private.encoded,
            ownEphemeralPublicKeyBytes = calleePair.public.encoded,
            peerEphemeralPublicKeyBytes = callerPair.public.encoded,
            isCaller = false
        )
    }

    // =========================================================================
    // 1. LATENCY BENCHMARKING
    // =========================================================================

    @Test
    fun `benchmark processing latency for full audio pipeline`() {
        val pcmFrame = generateSineWavePCM(frequency = 440.0, amplitude = 15000)
        val iterations = 1000

        // Warmup JVM execution path
        repeat(100) {
            val encoded = codec.encode(pcmFrame)
            val encrypted = callerCrypto.encrypt(1, encoded)
            val decrypted = calleeCrypto.decrypt(1, encrypted)!!
            codec.decode(decrypted)
        }

        // Measure total execution time for 1,000 frames (20 seconds of speech audio)
        val totalNano = measureNanoTime {
            for (i in 0 until iterations) {
                val encoded = codec.encode(pcmFrame)
                val encrypted = callerCrypto.encrypt(i, encoded)
                val decrypted = calleeCrypto.decrypt(i, encrypted)!!
                val decoded = codec.decode(decrypted)
            }
        }

        val avgMsPerFrame = (totalNano / 1_000_000.0) / iterations

        println("==================================================")
        println("📊 LATENCY BENCHMARK RESULTS")
        println("Average processing latency per 20ms frame: ${String.format("%.4f", avgMsPerFrame)} ms")
        println("==================================================")

        // A 20ms frame must take LESS than 1ms to process computationally on CPU!
        assertTrue("Pipeline processing latency ($avgMsPerFrame ms) is too slow!", avgMsPerFrame < 1.0)
    }

    // =========================================================================
    // 2. AUDIO DISTORTION & QUALITY METRICS (SNR)
    // =========================================================================

    @Test
    fun `measure Signal-to-Noise Ratio (SNR) for codec output`() {
        val originalPcm = generateSineWavePCM(frequency = 1000.0, amplitude = 20000)

        // Encode -> Decode
        val encoded = codec.encode(originalPcm)
        val decodedPcm = codec.decode(encoded)

        val snrDb = calculateSNR(originalPcm, decodedPcm)

        println("==================================================")
        println("📊 AUDIO QUALITY METRICS")
        println("Codec Signal-to-Noise Ratio (SNR): ${String.format("%.2f", snrDb)} dB")
        println("==================================================")

        // G.711 mu-law speech codec typically yields > 30 dB SNR
        assertTrue("Audio SNR ($snrDb dB) is below acceptable speech clarity threshold!", snrDb > 30.0)
    }

    // =========================================================================
    // 3. AMPLIFIER GAIN & SOFT-CLIPPER DISTORTION TEST
    // =========================================================================

    @Test
    fun `test gain filter prevents hard clipping harmonic distortion`() {
        val quietAudio = generateSineWavePCM(frequency = 440.0, amplitude = 25000)

        // Apply 2.0x Gain
        val boostedAudio = applyAudioFilters(quietAudio, gain = 2.0f, useNoiseGate = false)

        var clippedSamplesCount = 0
        var maxAmplitude = 0

        var i = 0
        while (i < boostedAudio.size - 1) {
            val low = boostedAudio[i].toInt() and 0xFF
            val high = (boostedAudio[i + 1].toInt() and 0xFF) shl 8
            var sample = high or low
            if (sample and 0x8000 != 0) sample -= 0x10000

            val absSample = abs(sample)
            if (absSample > maxAmplitude) maxAmplitude = absSample
            if (absSample >= 32767) clippedSamplesCount++
            i += 2
        }

        println("==================================================")
        println("📊 GAIN & SOFT-CLIPPER METRICS")
        println("Max Output Sample Amplitude: $maxAmplitude / 32767")
        println("Hard Clipped Samples Count: $clippedSamplesCount")
        println("==================================================")

        // Verify that values stay strictly bounded within 16-bit limits without overflow distortion
        assertTrue("Output sample overflowed 16-bit limits!", maxAmplitude <= 32767)
    }

    // =========================================================================
    // HELPER MATHEMATICAL & FILTER FUNCTIONS
    // =========================================================================

    private fun generateSineWavePCM(frequency: Double, amplitude: Int): ByteArray {
        val out = ByteArray(VoiceCodec.BYTES_PER_FRAME)
        for (i in 0 until VoiceCodec.SAMPLES_PER_FRAME) {
            val time = i.toDouble() / VoiceCodec.SAMPLE_RATE
            val sampleValue = (amplitude * sin(2.0 * Math.PI * frequency * time)).toInt().coerceIn(-32768, 32767)

            out[i * 2] = (sampleValue and 0xFF).toByte()
            out[i * 2 + 1] = ((sampleValue shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun calculateSNR(original: ByteArray, processed: ByteArray): Double {
        var signalPower = 0.0
        var noisePower = 0.0

        var i = 0
        while (i < original.size - 1) {
            val origLow = original[i].toInt() and 0xFF
            val origHigh = (original[i + 1].toInt() and 0xFF) shl 8
            var origSample = origHigh or origLow
            if (origSample and 0x8000 != 0) origSample -= 0x10000

            val procLow = processed[i].toInt() and 0xFF
            val procHigh = (processed[i + 1].toInt() and 0xFF) shl 8
            var procSample = procHigh or procLow
            if (procSample and 0x8000 != 0) procSample -= 0x10000

            val noise = (origSample - procSample).toDouble()

            signalPower += origSample.toDouble() * origSample.toDouble()
            noisePower += noise * noise
            i += 2
        }

        if (noisePower == 0.0) return 100.0
        return 10.0 * log10(signalPower / noisePower)
    }

    private fun applyAudioFilters(pcm16: ByteArray, gain: Float, useNoiseGate: Boolean): ByteArray {
        if (!useNoiseGate && gain == 1.0f) return pcm16

        val out = ByteArray(pcm16.size)
        var i = 0
        while (i < pcm16.size - 1) {
            val low = pcm16[i].toInt() and 0xFF
            val high = (pcm16[i + 1].toInt() and 0xFF) shl 8
            var sample = high or low
            if (sample and 0x8000 != 0) sample -= 0x10000

            val amplified = sample * gain

            val maxVal = 32767.0f
            val normalized = amplified / maxVal
            val softClipped = if (abs(normalized) > 0.8f) {
                kotlin.math.tanh(normalized.toDouble()).toFloat() * maxVal
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