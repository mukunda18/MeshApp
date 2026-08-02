package com.meshapp.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator

class CallCryptoTest {

    private lateinit var callerCrypto: CallCrypto
    private lateinit var calleeCrypto: CallCrypto

    @Before
    fun setUp() {
        // Generate ephemeral EC key pairs for caller and callee
        val kpg = KeyPairGenerator.getInstance("EC").apply { initialize(256) }
        val callerPair = kpg.generateKeyPair()
        val calleePair = kpg.generateKeyPair()

        // Instantiate Caller crypto handler
        callerCrypto = CallCrypto(
            ownEphemeralPrivateKeyBytes = callerPair.private.encoded,
            ownEphemeralPublicKeyBytes = callerPair.public.encoded,
            peerEphemeralPublicKeyBytes = calleePair.public.encoded,
            isCaller = true
        )

        // Instantiate Callee crypto handler
        calleeCrypto = CallCrypto(
            ownEphemeralPrivateKeyBytes = calleePair.private.encoded,
            ownEphemeralPublicKeyBytes = calleePair.public.encoded,
            peerEphemeralPublicKeyBytes = callerPair.public.encoded,
            isCaller = false
        )
    }

    @Test
    fun `caller can encrypt voice frame and callee can decrypt it`() {
        val originalFrame = ByteArray(320) { it.toByte() }
        val seqNum = 42

        val encrypted = callerCrypto.encrypt(seqNum, originalFrame)
        val decrypted = calleeCrypto.decrypt(seqNum, encrypted)

        assertNotNull("Decryption returned null", decrypted)
        assertArrayEquals(originalFrame, decrypted)
    }

    @Test
    fun `callee can encrypt voice frame and caller can decrypt it`() {
        val originalFrame = ByteArray(320) { (255 - it).toByte() }
        val seqNum = 101

        val encrypted = calleeCrypto.encrypt(seqNum, originalFrame)
        val decrypted = callerCrypto.decrypt(seqNum, encrypted)

        assertNotNull("Decryption returned null", decrypted)
        assertArrayEquals(originalFrame, decrypted)
    }

    @Test
    fun `decrypt fails if sequence number does not match`() {
        val originalFrame = "Voice Payload Data".toByteArray()
        val seqNum = 5

        val encrypted = callerCrypto.encrypt(seqNum, originalFrame)

        // Attempting to decrypt with seqNum = 6 instead of 5
        val decrypted = calleeCrypto.decrypt(6, encrypted)

        assertNull("Decryption should fail when nonce sequence number mismatches", decrypted)
    }

    @Test
    fun `decrypt fails if ciphertext is tampered with`() {
        val originalFrame = "Voice Payload Data".toByteArray()
        val seqNum = 12

        val encrypted = callerCrypto.encrypt(seqNum, originalFrame)

        // Corrupt a byte in the payload
        encrypted[0] = (encrypted[0].toInt() xor 0xFF).toByte()

        val decrypted = calleeCrypto.decrypt(seqNum, encrypted)

        assertNull("Decryption should fail when ciphertext is corrupted", decrypted)
    }
}