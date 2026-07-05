package com.minor.security

import com.minor.model.AckProtocol
import com.minor.model.MessageId
import com.minor.model.MessageProtocol
import com.minor.model.NodeId
import com.minor.model.NodesStore
import com.minor.model.PacketSigner
import com.minor.model.PacketVerifier
import com.minor.model.Signature
import com.minor.model.Timestamp
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
    fun encode(plaintext: String, recipientNodeID: NodeId, messageID: MessageId): ByteArray {
        val recipientPubKeyBytes = nodesStore.getPublicKey(recipientNodeID)
            ?: throw IllegalStateException("Public key not found for $recipientNodeID")

        // 1. Reconstruct recipient public key from bytes
        val recipientPubKey = keyFactory.generatePublic(X509EncodedKeySpec(recipientPubKeyBytes.bytes))

        // 2. Generate ephemeral ECDH keypair
        val ephemeralKeyPairGen = java.security.KeyPairGenerator.getInstance("EC").apply {
            initialize(256)  // NIST P-256
        }
        val ephemeralKeyPair = ephemeralKeyPairGen.generateKeyPair()

        // 3. Derive shared secret via ECDH
        keyAgreement.init(identity.privateKeyObj)
        keyAgreement.doPhase(recipientPubKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        // 4. Derive AES-256 key from shared secret (take first 32 bytes)
        val derivedKey = sharedSecret.take(32).toByteArray()
        val aesKey = SecretKeySpec(derivedKey, 0, 32, "AES")

        // 5. Create Inner Plaintext Block
        val timestamp = System.currentTimeMillis()
        val contentBytes = plaintext.encodeToByteArray()
        val innerBlockSize = MessageProtocol.MESSAGE_ID_LENGTH + 
                          MessageProtocol.TIMESTAMP_LENGTH + 
                          MessageProtocol.CONTENT_LEN_LENGTH + contentBytes.size
        val innerBlock = ByteArray(innerBlockSize)
        
        var innerOffset = 0
        innerOffset += MessageProtocol.messageId.write(innerBlock, messageID, innerOffset)
        innerOffset += MessageProtocol.timestamp.write(innerBlock, Timestamp(timestamp), innerOffset)
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

        // 9. Sign envelope using ECDSA (P1363 format = fixed 64 bytes for P-256)
        val signer = JavaSignature.getInstance("SHA256withECDSAinP1363Format")
        signer.initSign(identity.privateKeyObj)
        signer.update(envelope, 0, cursor)
        val signatureBytes = signer.sign()

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
            ?: throw IllegalStateException("Public key not found for ${senderNodeIdRead.value}")

        // 2. Verify ECDSA signature (P1363 format)
        val senderPubKey = keyFactory.generatePublic(X509EncodedKeySpec(senderPubKeyBytes.bytes))
        val verifier = JavaSignature.getInstance("SHA256withECDSAinP1363Format")
        verifier.initVerify(senderPubKey)
        verifier.update(envelopeBytes, 0, dataToVerifySize)
        if (!verifier.verify(signatureRead.value.bytes)) {
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
        
        val signer = JavaSignature.getInstance("SHA256withECDSAinP1363Format")
        signer.initSign(identity.privateKeyObj)
        signer.update(data)
        return Signature(signer.sign())
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
            val verifier = JavaSignature.getInstance("SHA256withECDSAinP1363Format")
            verifier.initVerify(pubKey)
            verifier.update(data)
            verifier.verify(signature.bytes)
        } catch (_: Exception) {
            false
        }
    }
}


data class DecodedContent(
    val senderNodeId: NodeId,
    val messageId: MessageId,
    val timestamp: Timestamp,
    val content: String
)
