package com.minor.meshapp.security

import com.minor.messaging.DecodedMessage
import com.minor.messaging.MessageSecurityCodec
import com.minor.model.MessageId
import com.minor.model.NodeId
import com.minor.model.Packet
import com.minor.model.Payload
import com.minor.model.Timestamp
import com.minor.packetprocessor.PayloadParser
import com.minor.model.ParseResult

/**
 * Passthrough (no-op) implementation of MessageSecurityCodec.
 *
 * Does not apply any encryption or signing.
 * Used as a placeholder until the :security module is implemented.
 *
 * Phase 3 or the security module implementation will replace this.
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
                "PassthroughSecurityCodec: failed to parse packet payload — ${result.error}"
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
