package com.minor.security

import com.minor.messaging.DecodedMessage
import com.minor.messaging.MessageSecurityCodec
import com.minor.model.MessageId
import com.minor.model.NodeId
import com.minor.model.Packet
import com.minor.model.ParseResult
import com.minor.model.Payload
import com.minor.model.Timestamp
import com.minor.packetprocessor.PayloadParser

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
    ): Payload {
        return Payload.Message(
            messageId = messageID,
            timestamp = Timestamp(System.currentTimeMillis()),
            content = plaintext
        )
    }

    override fun decode(packet: Packet): DecodedMessage {
        val result = PayloadParser.parse(packet)
        val payload = when (result) {
            is ParseResult.Success -> result.value as? Payload.Message
                ?: throw IllegalArgumentException(
                    "PassthroughSecurityCodec: expected Payload.Message, got ${result.value::class.simpleName}"
                )

            is ParseResult.Failure -> throw IllegalArgumentException(
                "PassthroughSecurityCodec: failed to parse packet payload - ${result.error}"
            )
        }
        return DecodedMessage(
            senderNodeId = packet.header.sourceNodeId,
            plaintext = payload.content,
            composeTimestamp = payload.timestamp,
            messageID = payload.messageId
        )
    }
}
