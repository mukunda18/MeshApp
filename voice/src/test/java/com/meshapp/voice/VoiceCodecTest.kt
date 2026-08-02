package com.meshapp.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class VoiceCodecTest {

    private lateinit var codec: VoiceCodec

    @Before
    fun setUp() {
        codec = VoiceCodec()
    }

    @Test
    fun `encode shrinks 640 byte PCM frame to 320 byte mu-law frame`() {
        val dummyPcm = ByteArray(VoiceCodec.BYTES_PER_FRAME) // 640 bytes
        val encoded = codec.encode(dummyPcm)

        assertEquals(VoiceCodec.COMPRESSED_FRAME_SIZE, encoded.size) // 320 bytes
    }

    @Test
    fun `decode converts 320 byte mu-law frame back to 640 byte PCM frame`() {
        val dummyMuLaw = ByteArray(VoiceCodec.COMPRESSED_FRAME_SIZE) // 320 bytes
        val decoded = codec.decode(dummyMuLaw)

        assertEquals(VoiceCodec.BYTES_PER_FRAME, decoded.size) // 640 bytes
    }

    @Test
    fun `encode and decode preserves audio wave fidelity within mu-law tolerance`() {
        // Create a 16-bit PCM Sine Wave frame
        val originalPcm = ByteArray(VoiceCodec.BYTES_PER_FRAME)
        for (i in 0 until VoiceCodec.SAMPLES_PER_FRAME) {
            val sample = (10000 * kotlin.math.sin(i * 0.1)).toInt().toShort()
            originalPcm[i * 2] = (sample.toInt() and 0xFF).toByte()
            originalPcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        val encoded = codec.encode(originalPcm)
        val decoded = codec.decode(encoded)

        // Validate that decoded PCM samples approximate original samples (G.711 lossy bounds)
        for (i in 0 until VoiceCodec.SAMPLES_PER_FRAME) {
            val origLow = originalPcm[i * 2].toInt() and 0xFF
            val origHigh = (originalPcm[i * 2 + 1].toInt() and 0xFF) shl 8
            var origSample = origHigh or origLow
            if (origSample and 0x8000 != 0) origSample -= 0x10000

            val decLow = decoded[i * 2].toInt() and 0xFF
            val decHigh = (decoded[i * 2 + 1].toInt() and 0xFF) shl 8
            var decSample = decHigh or decLow
            if (decSample and 0x8000 != 0) decSample -= 0x10000

            val delta = abs(origSample - decSample)
            // Mu-law tolerance for 10000 amplitude signal is typically < 250 units
            assertTrue("Sample $i error $delta exceeds tolerance!", delta < 300)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encode throws exception when frame size is invalid`() {
        codec.encode(ByteArray(100))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode throws exception when frame size is invalid`() {
        codec.decode(ByteArray(100))
    }
}