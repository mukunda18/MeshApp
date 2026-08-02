package com.meshapp.voice

import com.meshapp.logger.MeshLogger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Derives a per-call symmetric key from the ephemeral ECDH keys exchanged in
 * CallOffer / CallAccept and provides AES-GCM encryption/decryption for voice packets.
 *
 * Two directional keys are derived so that sender and receiver nonces never collide
 * under the same key:
 *   - this side's send key
 *   - this side's receive key (peer's send key)
 */
class CallCrypto(
    ownEphemeralPrivateKeyBytes: ByteArray,
    ownEphemeralPublicKeyBytes: ByteArray,
    peerEphemeralPublicKeyBytes: ByteArray,
    private val isCaller: Boolean
) {
    private val keyFactory = KeyFactory.getInstance("EC")
    private val ownPrivateKey: PrivateKey = keyFactory.generatePrivate(
        PKCS8EncodedKeySpec(ownEphemeralPrivateKeyBytes)
    )

    val ownEphemeralPublicKey: ByteArray = ownEphemeralPublicKeyBytes
    val peerEphemeralPublicKey: ByteArray = peerEphemeralPublicKeyBytes

    private val sendKey: SecretKeySpec
    private val receiveKey: SecretKeySpec

    init {
        val sharedSecret = deriveSharedSecret(peerEphemeralPublicKeyBytes)
        sendKey = deriveKey(sharedSecret, if (isCaller) "caller" else "callee")
        receiveKey = deriveKey(sharedSecret, if (isCaller) "callee" else "caller")
    }

    private fun deriveSharedSecret(peerPublicKeyBytes: ByteArray): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(ownPrivateKey)
        keyAgreement.doPhase(keyFactory.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes)), true)
        return keyAgreement.generateSecret()
    }

    private fun deriveKey(sharedSecret: ByteArray, label: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(sharedSecret)
        digest.update(label.encodeToByteArray())
        return SecretKeySpec(digest.digest(), 0, 32, "AES")
    }

    private fun nonceFor(sequenceNumber: Int): ByteArray {
        // 12-byte nonce: 4-byte big-endian sequence number + 8 zero bytes.
        // Sequence numbers are unique per direction, so this never repeats for a key.
        return ByteArray(12).apply {
            this[0] = (sequenceNumber shr 24).toByte()
            this[1] = (sequenceNumber shr 16).toByte()
            this[2] = (sequenceNumber shr 8).toByte()
            this[3] = sequenceNumber.toByte()
        }
    }

    fun encrypt(sequenceNumber: Int, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, sendKey, GCMParameterSpec(128, nonceFor(sequenceNumber)))
        return cipher.doFinal(plaintext)
    }

    fun decrypt(sequenceNumber: Int, ciphertext: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, receiveKey, GCMParameterSpec(128, nonceFor(sequenceNumber)))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            MeshLogger.error("CallCrypto", "Failed to decrypt voice packet seq=$sequenceNumber", e.toString())
            null
        }
    }
}
