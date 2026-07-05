package com.minor.security

import com.minor.model.DecodedContent
import com.minor.model.MessageId
import com.minor.model.MessageProtocol
import com.minor.model.MessageSecurityCodec
import com.minor.model.NodeId
import com.minor.model.Signature
import com.minor.model.Timestamp

/**
 * Default security codec used until a stronger cryptographic codec is supplied.
 *
 * This codec preserves the current message format/flow and keeps integration stable
 * across modules. It performs payload pass-through without encryption/signing.
 */
class PassthroughSecurityCodec : MessageSecurityCodec {

    override fun encode(
        plaintext: String,
        recipientNodeID: NodeId,
        messageID: MessageId
    ): ByteArray {
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

        val envelopeSize = MessageProtocol.ENV_VERSION_LENGTH +
                MessageProtocol.SENDER_NODE_ID_LENGTH +
                MessageProtocol.ENC_SYM_KEY_LENGTH +
                MessageProtocol.NONCE_LENGTH +
                MessageProtocol.CIPHER_LEN_LENGTH + innerBlock.size +
                MessageProtocol.SIGNATURE_LENGTH
        
        val envelope = ByteArray(envelopeSize)
        var cursor = 0
        cursor += MessageProtocol.envVersion.write(envelope, 1, cursor)
        cursor += MessageProtocol.senderNodeId.write(envelope, NodeId(ByteArray(32)), cursor)
        cursor += MessageProtocol.encSymKey.write(envelope, ByteArray(32), cursor)
        cursor += MessageProtocol.nonce.write(envelope, ByteArray(12), cursor)
        cursor += MessageProtocol.ciphertext.write(envelope, innerBlock, cursor)
        MessageProtocol.signature.write(envelope, Signature(ByteArray(64)), cursor)

        return envelope
    }

    override fun decode(envelopeBytes: ByteArray): DecodedContent {
        var cursor = 0

        // 1. Extract components (Skip to ciphertext)
        MessageProtocol.envVersion.read(envelopeBytes, cursor).also { cursor += it.bytesRead }
        val senderNodeIdRead = MessageProtocol.senderNodeId.read(envelopeBytes, cursor)
        cursor += senderNodeIdRead.bytesRead
        MessageProtocol.encSymKey.read(envelopeBytes, cursor).also { cursor += it.bytesRead }
        MessageProtocol.nonce.read(envelopeBytes, cursor).also { cursor += it.bytesRead }
        
        val ciphertextRead = MessageProtocol.ciphertext.read(envelopeBytes, cursor)
        val innerBlock = ciphertextRead.value

        // 2. Parse Inner Block
        var innerCursor = 0
        val msgIdRead = MessageProtocol.messageId.read(innerBlock, innerCursor)
        innerCursor += msgIdRead.bytesRead
        val timestampRead = MessageProtocol.timestamp.read(innerBlock, innerCursor)
        innerCursor += timestampRead.bytesRead
        val contentRead = MessageProtocol.content.read(innerBlock, innerCursor)

        return DecodedContent(
            senderNodeId = senderNodeIdRead.value,
            messageId = msgIdRead.value,
            timestamp = timestampRead.value,
            content = contentRead.value
        )
    }
}
