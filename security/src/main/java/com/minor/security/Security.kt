package com.minor.security

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.AEAD
import com.goterl.lazysodium.interfaces.Box
import com.goterl.lazysodium.interfaces.Sign
import com.minor.model.MessageId
import com.minor.model.MessageProtocol
import com.minor.model.NodeId
import com.minor.model.Signature
import com.minor.model.Timestamp
import java.nio.ByteBuffer

class Security(
    private val identity: Identity,
    private val nodesStore: NodesStore,
    private val freshnessWindowMs: Long = 30_000L
) {
    private val sodium = LazySodiumAndroid(SodiumAndroid())

    /**
     * Constructs the Secure Envelope.
     * Layout: [ SenderNodeId(32) | EphemeralPubKey(32) | Nonce(12) | Ciphertext(var) | Signature(64) ]
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
        val innerBlockSize = MessageProtocol.MESSAGE_ID_LENGTH + MessageProtocol.TIMESTAMP_LENGTH + MessageProtocol.CONTENT_LEN_LENGTH + contentBytes.size
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
        val envelopeWithoutSigSize = 32 + 32 + 12 + ciphertext.size
        val envelopeWithoutSig = ByteBuffer.allocate(envelopeWithoutSigSize)
            .put(identity.nodeId.bytes)
            .put(ephemeralKeyPair.publicKey.asBytes)
            .put(nonce) 
            .put(ciphertext)
            .array()

        val signature = ByteArray(Sign.ED25519_BYTES)
        sodium.cryptoSignDetached(signature, envelopeWithoutSig, envelopeWithoutSig.size.toLong(), identity.privateKey)

        return ByteBuffer.allocate(envelopeWithoutSig.size + 64)
            .put(envelopeWithoutSig)
            .put(signature)
            .array()
    }

    /**
     * Decodes the fixed-layout envelope.
     */
    fun decode(envelopeBytes: ByteArray): DecodedContent {
        if (envelopeBytes.size < 32 + 32 + 12 + AEAD.CHACHA20POLY1305_IETF_ABYTES + 64) {
            throw SecurityException("Envelope too short")
        }

        // 1. Extract components
        val senderNodeID = NodeId(envelopeBytes.copyOfRange(0, 32))
        val ephemeralPubKey = envelopeBytes.copyOfRange(32, 64)
        val nonce = envelopeBytes.copyOfRange(64, 76)

        val senderEdPubKey = nodesStore.getPublicKey(senderNodeID)
            ?: throw IllegalStateException("Public key not found for $senderNodeID")

        // 2. Verify Signature
        val dataToVerifySize = envelopeBytes.size - 64
        val dataToVerify = envelopeBytes.copyOfRange(0, dataToVerifySize)
        val signature = envelopeBytes.copyOfRange(dataToVerifySize, envelopeBytes.size)
        
        val verified = sodium.cryptoSignVerifyDetached(signature, dataToVerify, dataToVerifySize, senderEdPubKey.bytes)
        if (!verified) {
            throw SecurityException("Invalid signature")
        }

        val ciphertext = envelopeBytes.copyOfRange(76, dataToVerifySize)

        // 3. Convert own Ed25519 secret key to X25519
        val ownXSecretKey = ByteArray(32)
        sodium.convertSecretKeyEd25519ToCurve25519(identity.privateKey, ownXSecretKey)

        // 4. Derive shared secret
        val sharedSecret = ByteArray(Box.BEFORENMBYTES)
        sodium.cryptoBoxBeforeNm(sharedSecret, ephemeralPubKey, ownXSecretKey)

        // 5. AEAD Decrypt
        val decrypted = ByteArray(ciphertext.size - AEAD.CHACHA20POLY1305_IETF_ABYTES)
        val success = sodium.cryptoAeadChaCha20Poly1305IetfDecrypt(
            decrypted,
            null,
            null,
            ciphertext,
            ciphertext.size.toLong(),
            null,
            0,
            nonce,
            sharedSecret 
        )
        if (!success) {
            throw SecurityException("Decryption failed")
        }

        // 6. Parse Inner Block
        var cursor = 0
        val msgIdRead = MessageProtocol.messageId.read(decrypted, cursor)
        cursor += msgIdRead.bytesRead
        val timestampRead = MessageProtocol.timestamp.read(decrypted, cursor)
        cursor += timestampRead.bytesRead
        val contentRead = MessageProtocol.content.read(decrypted, cursor)
        
        if (System.currentTimeMillis() - timestampRead.value.millis > freshnessWindowMs) {
            throw SecurityException("Message expired (anti-replay)")
        }

        return DecodedContent(
            senderNodeId = senderNodeID,
            messageId = msgIdRead.value,
            timestamp = timestampRead.value,
            content = contentRead.value
        )
    }

    /**
     * Produces a 64-byte Ed25519 signature over messageID (8 B) || status (1 B).
     */
    fun signAck(messageID: MessageId, status: Int): Signature {
        val data = ByteBuffer.allocate(9)
            .putLong(messageID.value)
            .put(status.toByte())
            .array()
        
        val signature = ByteArray(Sign.ED25519_BYTES)
        sodium.cryptoSignDetached(signature, data, data.size.toLong(), identity.privateKey)
        return Signature(signature)
    }

    /**
     * Verifies the 64-byte signature against the ACK sender's known public key.
     */
    fun verifyAck(messageID: MessageId, status: Int, signature: Signature, senderNodeID: NodeId): Boolean {
        val pubKey = nodesStore.getPublicKey(senderNodeID) ?: return false
        val data = ByteBuffer.allocate(9)
            .putLong(messageID.value)
            .put(status.toByte())
            .array()
        
        return sodium.cryptoSignVerifyDetached(signature.bytes, data, data.size, pubKey.bytes)
    }
}


data class DecodedContent(
    val senderNodeId: NodeId,
    val messageId: MessageId,
    val timestamp: Timestamp,
    val content: String
)
