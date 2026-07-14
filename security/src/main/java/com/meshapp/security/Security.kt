package com.meshapp.security

import com.meshapp.model.AckProtocol
import com.meshapp.model.MessageId
import com.meshapp.model.MessageProtocol
import com.meshapp.model.NodeId
import com.meshapp.model.Signature
import com.meshapp.model.Timestamp
import com.meshapp.logger.MeshLogger
import java.security.KeyFactory
import java.security.Signature as JavaSignature
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

class Security(
    private val identity: Identity,
    private val nodesStore: NodesStore,
    private val freshnessWindowMs: Long = 30_000L
) : PacketSigner, PacketVerifier {
    private val keyFactory = KeyFactory.getInstance("EC")
    private val keyAgreement = KeyAgreement.getInstance("ECDH")
    private val secureRandom = SecureRandom()

    /**
     * Constructs the Secure Envelope using ECDSA/P1363 signature and AES-GCM encryption.
     * Layout: [ Version(1) | SenderNodeId(32) | EphemeralPubKey(91) | Nonce(12) | CiphertextLen(4) | Ciphertext(var) | Signature(64) ]
     */
    fun encode(
        plaintext: String,
        recipientNodeID: NodeId,
        messageID: MessageId,
        timestamp: Timestamp = Timestamp(System.currentTimeMillis())
    ): ByteArray {
        val recipientPubKeyBytes = nodesStore.getPublicKey(recipientNodeID)
            ?: run {
                MeshLogger.error("Security", "Encryption failed: Public key not found for $recipientNodeID")
                throw IllegalStateException("Public key not found for $recipientNodeID. Identity discovery (RREQ) may be required.")
            }

        // 1. Reconstruct recipient public key from bytes
        val recipientPubKey = keyFactory.generatePublic(X509EncodedKeySpec(recipientPubKeyBytes.bytes))

        // 2. Generate ephemeral ECDH keypair
        val ephemeralKeyPairGen = java.security.KeyPairGenerator.getInstance("EC").apply {
            initialize(256)  // NIST P-256
        }
        val ephemeralKeyPair = ephemeralKeyPairGen.generateKeyPair()

        // 3. Derive shared secret via ECDH using ephemeral private key
        keyAgreement.init(ephemeralKeyPair.private)
        keyAgreement.doPhase(recipientPubKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        // 4. Derive AES-256 key from shared secret (take first 32 bytes)
        val derivedKey = sharedSecret.take(32).toByteArray()
        val aesKey = SecretKeySpec(derivedKey, 0, 32, "AES")

        // 5. Create Inner Plaintext Block
        val contentBytes = plaintext.encodeToByteArray()
        val innerBlockSize = MessageProtocol.MESSAGE_ID_LENGTH + 
                          MessageProtocol.TIMESTAMP_LENGTH + 
                          MessageProtocol.CONTENT_LEN_LENGTH + contentBytes.size
        val innerBlock = ByteArray(innerBlockSize)
        
        var innerOffset = 0
        innerOffset += MessageProtocol.messageId.write(innerBlock, messageID, innerOffset)
        innerOffset += MessageProtocol.timestamp.write(innerBlock, timestamp, innerOffset)
        MessageProtocol.content.write(innerBlock, plaintext, innerOffset)

        // 6. Generate nonce (12 bytes for AES-GCM)
        val nonce = ByteArray(12)
        secureRandom.nextBytes(nonce)

        // 7. Encrypt using AES-256-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, nonce)  // 128-bit auth tag
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec)
        val ciphertext = cipher.doFinal(innerBlock)

        // 8. Assemble envelope
        val envelopeSize = MessageProtocol.ENV_VERSION_LENGTH +
                MessageProtocol.SENDER_NODE_ID_LENGTH +
                MessageProtocol.ENC_SYM_KEY_LENGTH +
                MessageProtocol.NONCE_LENGTH +
                MessageProtocol.CIPHER_LEN_LENGTH + ciphertext.size +
                MessageProtocol.SIGNATURE_LENGTH
        
        val envelope = ByteArray(envelopeSize)
        var cursor = 0
        cursor += MessageProtocol.envVersion.write(envelope, 1, cursor)
        cursor += MessageProtocol.senderNodeId.write(envelope, identity.nodeId, cursor)
        cursor += MessageProtocol.encSymKey.write(envelope, ephemeralKeyPair.public.encoded, cursor)
        cursor += MessageProtocol.nonce.write(envelope, nonce, cursor)
        cursor += MessageProtocol.ciphertext.write(envelope, ciphertext, cursor)

        // 9. Sign envelope using ECDSA
        val signer = JavaSignature.getInstance("SHA256withECDSA")
        signer.initSign(identity.privateKeyObj)
        signer.update(envelope, 0, cursor)
        val derSignature = signer.sign()
        val signatureBytes = derToP1363(derSignature)

        MessageProtocol.signature.write(envelope, Signature(signatureBytes), cursor)

        return envelope
    }

    /**
     * Decodes and verifies the envelope.
     */
    fun decode(envelopeBytes: ByteArray): DecodedContent {
        val minSize = MessageProtocol.ENV_VERSION_LENGTH +
                MessageProtocol.SENDER_NODE_ID_LENGTH +
                MessageProtocol.ENC_SYM_KEY_LENGTH +
                MessageProtocol.NONCE_LENGTH +
                MessageProtocol.CIPHER_LEN_LENGTH +
                16 +  // GCM auth tag
                MessageProtocol.SIGNATURE_LENGTH
        
        if (envelopeBytes.size < minSize) {
            // Thrown if the packet was truncated or malformed during transit.
            MeshLogger.error("Security", "Decryption failed: Envelope too short (${envelopeBytes.size} bytes)")
            throw SecurityException("Envelope too short")
        }

        var cursor = 0

        // 1. Extract components
        val versionRead = MessageProtocol.envVersion.read(envelopeBytes, cursor)
        cursor += versionRead.bytesRead
        
        val senderNodeIdRead = MessageProtocol.senderNodeId.read(envelopeBytes, cursor)
        cursor += senderNodeIdRead.bytesRead
        
        val ephemeralPubKeyRead = MessageProtocol.encSymKey.read(envelopeBytes, cursor)
        cursor += ephemeralPubKeyRead.bytesRead
        
        val nonceRead = MessageProtocol.nonce.read(envelopeBytes, cursor)
        cursor += nonceRead.bytesRead
        
        val ciphertextRead = MessageProtocol.ciphertext.read(envelopeBytes, cursor)
        val dataToVerifySize = cursor + ciphertextRead.bytesRead
        cursor += ciphertextRead.bytesRead
        
        val signatureRead = MessageProtocol.signature.read(envelopeBytes, cursor)

        val senderPubKeyBytes = nodesStore.getPublicKey(senderNodeIdRead.value)
            ?: run {
                MeshLogger.error("Security", "Decryption failed: Public key not found for ${senderNodeIdRead.value}")
                throw IllegalStateException("Public key not found for ${senderNodeIdRead.value}. Cannot verify signature of unknown node.")
            }

        // 2. Verify ECDSA signature
        val senderPubKey = keyFactory.generatePublic(X509EncodedKeySpec(senderPubKeyBytes.bytes))
        val verifier = JavaSignature.getInstance("SHA256withECDSA")
        verifier.initVerify(senderPubKey)
        verifier.update(envelopeBytes, 0, dataToVerifySize)
        val derSignature = p1363ToDer(signatureRead.value.bytes)
        if (!verifier.verify(derSignature)) {
            // Thrown if any part of the envelope was tampered with after signing.
            MeshLogger.error("Security", "Decryption failed: Invalid signature from ${senderNodeIdRead.value}")
            throw SecurityException("Invalid signature")
        }

        // 3. Reconstruct ephemeral public key
        val ephemeralPubKey = keyFactory.generatePublic(X509EncodedKeySpec(ephemeralPubKeyRead.value))

        // 4. Derive shared secret via ECDH
        keyAgreement.init(identity.privateKeyObj)
        keyAgreement.doPhase(ephemeralPubKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        // 5. Derive AES-256 key from shared secret
        val derivedKey = sharedSecret.take(32).toByteArray()
        val aesKey = SecretKeySpec(derivedKey, 0, 32, "AES")

        // 6. Decrypt using AES-256-GCM
        val ciphertext = ciphertextRead.value
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, nonceRead.value)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec)
        val decrypted = cipher.doFinal(ciphertext)

        // 7. Parse Inner Block
        var innerCursor = 0
        val msgIdRead = MessageProtocol.messageId.read(decrypted, innerCursor)
        innerCursor += msgIdRead.bytesRead
        val timestampRead = MessageProtocol.timestamp.read(decrypted, innerCursor)
        innerCursor += timestampRead.bytesRead
        val contentRead = MessageProtocol.content.read(decrypted, innerCursor)
        
        if (System.currentTimeMillis() - timestampRead.value.millis > freshnessWindowMs) {
            // Thrown if the message is too old, likely indicating a replay attack.
            MeshLogger.error("Security", "Decryption failed: Message expired from ${senderNodeIdRead.value}", "Age: ${System.currentTimeMillis() - timestampRead.value.millis}ms")
            throw SecurityException("Message expired (anti-replay)")
        }

        return DecodedContent(
            senderNodeId = senderNodeIdRead.value,
            messageId = msgIdRead.value,
            timestamp = timestampRead.value,
            content = contentRead.value
        )
    }

    /**
     * Produces an ECDSA signature over messageID (8 B) || status (1 B).
     */
    override fun signAck(messageId: MessageId, status: Int): Signature {
        val data = ByteArray(MessageProtocol.MESSAGE_ID_LENGTH + AckProtocol.STATUS_LENGTH)
        var cursor = 0
        cursor += MessageProtocol.messageId.write(data, messageId, cursor)
        AckProtocol.status.write(data, status, cursor)
        
        val signer = JavaSignature.getInstance("SHA256withECDSA")
        signer.initSign(identity.privateKeyObj)
        signer.update(data)
        val derSignature = signer.sign()
        return Signature(derToP1363(derSignature))
    }

    /**
     * Verifies the ECDSA signature against the ACK sender's known public key.
     */
    override fun verifyAck(messageId: MessageId, status: Int, signature: Signature, senderNodeId: NodeId): Boolean {
        val pubKeyBytes = nodesStore.getPublicKey(senderNodeId) ?: return false
        val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(pubKeyBytes.bytes))
        val data = ByteArray(MessageProtocol.MESSAGE_ID_LENGTH + AckProtocol.STATUS_LENGTH)
        var cursor = 0
        cursor += MessageProtocol.messageId.write(data, messageId, cursor)
        AckProtocol.status.write(data, status, cursor)
        
        return try {
            val verifier = JavaSignature.getInstance("SHA256withECDSA")
            verifier.initVerify(pubKey)
            verifier.update(data)
            val derSignature = p1363ToDer(signature.bytes)
            verifier.verify(derSignature)
        } catch (_: Exception) {
            false
        }
    }

    private fun derToP1363(der: ByteArray): ByteArray {
        val result = ByteArray(64)
        var offset = 0
        if (der[offset++] != 0x30.toByte()) throw IllegalArgumentException("Invalid DER")
        val totalLen = der[offset++].toInt() and 0xFF
        
        // R
        if (der[offset++] != 0x02.toByte()) throw IllegalArgumentException("Invalid DER R")
        val rLen = der[offset++].toInt() and 0xFF
        val rStart = if (der[offset] == 0.toByte() && rLen > 32) offset + 1 else offset
        val rCopyLen = if (rLen > 32) 32 else rLen
        der.copyInto(result, 32 - rCopyLen, rStart, rStart + rCopyLen)
        offset += rLen
        
        // S
        if (der[offset++] != 0x02.toByte()) throw IllegalArgumentException("Invalid DER S")
        val sLen = der[offset++].toInt() and 0xFF
        val sStart = if (der[offset] == 0.toByte() && sLen > 32) offset + 1 else offset
        val sCopyLen = if (sLen > 32) 32 else sLen
        der.copyInto(result, 64 - sCopyLen, sStart, sStart + sCopyLen)
        
        return result
    }

    private fun p1363ToDer(p1363: ByteArray): ByteArray {
        val r = p1363.sliceArray(0 until 32).dropWhile { it == 0.toByte() }.toByteArray()
        val rFinal = if (r.isEmpty() || r[0].toInt() < 0) byteArrayOf(0) + r else r
        val s = p1363.sliceArray(32 until 64).dropWhile { it == 0.toByte() }.toByteArray()
        val sFinal = if (s.isEmpty() || s[0].toInt() < 0) byteArrayOf(0) + s else s
        val len = 2 + rFinal.size + 2 + sFinal.size
        val der = ByteArray(2 + len)
        der[0] = 0x30; der[1] = len.toByte()
        var pos = 2
        der[pos++] = 0x02; der[pos++] = rFinal.size.toByte()
        rFinal.copyInto(der, pos); pos += rFinal.size
        der[pos++] = 0x02; der[pos++] = sFinal.size.toByte()
        sFinal.copyInto(der, pos)
        return der
    }
}


data class DecodedContent(
    val senderNodeId: NodeId,
    val messageId: MessageId,
    val timestamp: Timestamp,
    val content: String
)
