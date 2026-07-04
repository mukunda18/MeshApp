package com.minor.security

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.AEAD
import com.goterl.lazysodium.interfaces.Box
import com.goterl.lazysodium.interfaces.Sign
import com.minor.model.AckProtocol
import com.minor.model.MessageId
import com.minor.model.MessageProtocol
import com.minor.model.NodeId
import com.minor.model.NodesStore
import com.minor.model.PacketSigner
import com.minor.model.PacketVerifier
import com.minor.model.Signature
import com.minor.model.Timestamp

class Security(
    private val identity: Identity,
    private val nodesStore: NodesStore,
    private val freshnessWindowMs: Long = 30_000L
) : PacketSigner, PacketVerifier {
    private val sodium = LazySodiumAndroid(SodiumAndroid())

    /**
     * Constructs the Secure Envelope.
     * Layout: [ Version(1) | SenderNodeId(32) | EphemeralPubKey(32) | Nonce(12) | CiphertextLen(4) | Ciphertext(var) | Signature(64) ]
     */
    fun encode(plaintext: String, recipientNodeID: NodeId, messageID: MessageId): ByteArray {
        val recipientEdPubKey = nodesStore.getPublicKey(recipientNodeID)
            ?: throw IllegalStateException("Public key not found for $recipientNodeID")

        // 1. Convert recipient Ed25519 public key to X25519
        val recipientXPubKey = ByteArray(32)
        sodium.convertPublicKeyEd25519ToCurve25519(recipientEdPubKey.bytes, recipientXPubKey)

        // 2. Generate ephemeral X25519 keypair
        val ephemeralKeyPair = sodium.cryptoBoxKeypair()

        // 3. Derive shared secret
        val sharedSecret = ByteArray(Box.BEFORENMBYTES)
        sodium.cryptoBoxBeforeNm(sharedSecret, recipientXPubKey, ephemeralKeyPair.secretKey.asBytes)

        // 4. AEAD Encrypt Inner Plaintext Block
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

        val nonce = sodium.nonce(AEAD.CHACHA20POLY1305_IETF_NPUBBYTES)
        val ciphertext = ByteArray(innerBlock.size + AEAD.CHACHA20POLY1305_IETF_ABYTES)
        
        val encrypted = sodium.cryptoAeadChaCha20Poly1305IetfEncrypt(
            ciphertext,
            null,
            innerBlock,
            innerBlock.size.toLong(),
            null,
            0,
            null,
            nonce,
            sharedSecret 
        )
        if (!encrypted) throw SecurityException("Encryption failed")

        // 5. Assemble and Sign
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
        cursor += MessageProtocol.encSymKey.write(envelope, ephemeralKeyPair.publicKey.asBytes, cursor)
        cursor += MessageProtocol.nonce.write(envelope, nonce, cursor)
        cursor += MessageProtocol.ciphertext.write(envelope, ciphertext, cursor)

        val signature = ByteArray(Sign.ED25519_BYTES)
        sodium.cryptoSignDetached(signature, envelope, cursor.toLong(), identity.privateKey)

        MessageProtocol.signature.write(envelope, Signature(signature), cursor)

        return envelope
    }

    /**
     * Decodes the fixed-layout envelope using MessageProtocol definitions.
     */
    fun decode(envelopeBytes: ByteArray): DecodedContent {
        val minSize = MessageProtocol.ENV_VERSION_LENGTH +
                MessageProtocol.SENDER_NODE_ID_LENGTH +
                MessageProtocol.ENC_SYM_KEY_LENGTH +
                MessageProtocol.NONCE_LENGTH +
                MessageProtocol.CIPHER_LEN_LENGTH +
                AEAD.CHACHA20POLY1305_IETF_ABYTES +
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

        val senderEdPubKey = nodesStore.getPublicKey(senderNodeIdRead.value)
            ?: throw IllegalStateException("Public key not found for ${senderNodeIdRead.value}")

        // 2. Verify Signature
        val dataToVerify = envelopeBytes.copyOfRange(0, dataToVerifySize)
        val verified = sodium.cryptoSignVerifyDetached(signatureRead.value.bytes, dataToVerify, dataToVerifySize, senderEdPubKey.bytes)
        if (!verified) {
            throw SecurityException("Invalid signature")
        }

        // 3. Convert own Ed25519 secret key to X25519
        val ownXSecretKey = ByteArray(32)
        sodium.convertSecretKeyEd25519ToCurve25519(identity.privateKey, ownXSecretKey)

        // 4. Derive shared secret
        val sharedSecret = ByteArray(Box.BEFORENMBYTES)
        sodium.cryptoBoxBeforeNm(sharedSecret, ephemeralPubKeyRead.value, ownXSecretKey)

        // 5. AEAD Decrypt
        val ciphertext = ciphertextRead.value
        val decrypted = ByteArray(ciphertext.size - AEAD.CHACHA20POLY1305_IETF_ABYTES)
        val success = sodium.cryptoAeadChaCha20Poly1305IetfDecrypt(
            decrypted,
            null,
            null,
            ciphertext,
            ciphertext.size.toLong(),
            null,
            0,
            nonceRead.value,
            sharedSecret 
        )
        if (!success) {
            throw SecurityException("Decryption failed")
        }

        // 6. Parse Inner Block
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
     * Produces a 64-byte Ed25519 signature over messageID (8 B) || status (1 B).
     */
    override fun signAck(messageId: MessageId, status: Int): Signature {
        val data = ByteArray(MessageProtocol.MESSAGE_ID_LENGTH + AckProtocol.STATUS_LENGTH)
        var cursor = 0
        cursor += MessageProtocol.messageId.write(data, messageId, cursor)
        AckProtocol.status.write(data, status, cursor)
        
        val signature = ByteArray(Sign.ED25519_BYTES)
        sodium.cryptoSignDetached(signature, data, data.size.toLong(), identity.privateKey)
        return Signature(signature)
    }

    /**
     * Verifies the 64-byte signature against the ACK sender's known public key.
     */
    override fun verifyAck(messageId: MessageId, status: Int, signature: Signature, senderNodeId: NodeId): Boolean {
        val pubKey = nodesStore.getPublicKey(senderNodeId) ?: return false
        val data = ByteArray(MessageProtocol.MESSAGE_ID_LENGTH + AckProtocol.STATUS_LENGTH)
        var cursor = 0
        cursor += MessageProtocol.messageId.write(data, messageId, cursor)
        AckProtocol.status.write(data, status, cursor)
        
        return sodium.cryptoSignVerifyDetached(signature.bytes, data, data.size, pubKey.bytes)
    }
}


data class DecodedContent(
    val senderNodeId: NodeId,
    val messageId: MessageId,
    val timestamp: Timestamp,
    val content: String
)
