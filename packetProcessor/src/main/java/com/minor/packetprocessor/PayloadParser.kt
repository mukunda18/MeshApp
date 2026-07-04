package com.minor.packetprocessor

import com.minor.model.AckProtocol
import com.minor.model.HeaderProtocol
import com.minor.model.HelloProtocol
import com.minor.model.MessageProtocol
import com.minor.model.Packet
import com.minor.model.ParseError
import com.minor.model.ParseResult
import com.minor.model.Payload
import com.minor.model.RREPProtocol
import com.minor.model.RREQProtocol
import com.minor.model.RERRProtocol

object PayloadParser {
    fun parse(packet: Packet): ParseResult<Payload> = parse(packet.header.type, packet.payload)

    fun parse(type: Int, data: ByteArray): ParseResult<Payload> = when (type) {
        HeaderProtocol.Type.HELLO -> parseHello(data)
        HeaderProtocol.Type.MESSAGE -> parseMessage(data)
        HeaderProtocol.Type.RREQ -> parseRreq(data)
        HeaderProtocol.Type.RREP -> parseRrep(data)
        HeaderProtocol.Type.ACK -> parseAck(data)
        HeaderProtocol.Type.RERR -> parseRerr(data)
        else -> ParseResult.Failure(ParseError.UnsupportedType(type))
    }

    private fun parseHello(data: ByteArray): ParseResult<Payload> {
        try {
            var cursor = 0
            val nameRead = HelloProtocol.name.read(data, baseOffset = cursor)
            cursor += nameRead.bytesRead

            val publicKeyRead = HelloProtocol.publicKey.read(data, baseOffset = cursor)
            cursor += publicKeyRead.bytesRead

            val routeEntriesRead = HelloProtocol.routeEntries.read(data, baseOffset = cursor)
            cursor += routeEntriesRead.bytesRead
            
            if (cursor != data.size) {
                return ParseResult.Failure(ParseError.MalformedPayload("HELLO has trailing bytes"))
            }
            
            return ParseResult.Success(
                Payload.Hello(
                    name = nameRead.value,
                    publicKey = publicKeyRead.value,
                    routeEntries = routeEntriesRead.value
                )
            )
        } catch (e: IndexOutOfBoundsException) {
            return ParseResult.Failure(ParseError.TooShort(data.size, data.size + 1))
        }
    }

    private fun parseMessage(data: ByteArray): ParseResult<Payload> {
        try {
            var cursor = 0
            val messageIdRead = MessageProtocol.messageId.read(data, baseOffset = cursor)
            cursor += messageIdRead.bytesRead
            
            val timestampRead = MessageProtocol.timestamp.read(data, baseOffset = cursor)
            cursor += timestampRead.bytesRead
            
            val contentRead = MessageProtocol.content.read(data, baseOffset = cursor)
            cursor += contentRead.bytesRead
            
            if (data.size != cursor) {
                return ParseResult.Failure(ParseError.MalformedPayload("MESSAGE has trailing bytes"))
            }
            
            return ParseResult.Success(
                Payload.Message(
                    messageId = messageIdRead.value,
                    timestamp = timestampRead.value,
                    content = contentRead.value
                )
            )
        } catch (e: IndexOutOfBoundsException) {
            return ParseResult.Failure(ParseError.TooShort(data.size, data.size + 1))
        }
    }

    private fun parseAck(data: ByteArray): ParseResult<Payload> {
        try {
            var cursor = 0
            val statusRead = AckProtocol.status.read(data, baseOffset = cursor)
            cursor += statusRead.bytesRead
            
            val signatureRead = AckProtocol.signature.read(data, baseOffset = cursor)
            cursor += signatureRead.bytesRead
            
            if (data.size != cursor) {
                return ParseResult.Failure(ParseError.MalformedPayload("ACK has trailing bytes"))
            }
            
            return ParseResult.Success(
                Payload.Ack(status = statusRead.value, signature = signatureRead.value)
            )
        } catch (e: IndexOutOfBoundsException) {
            return ParseResult.Failure(ParseError.TooShort(data.size, data.size + 1))
        }
    }

    private fun parseRreq(data: ByteArray): ParseResult<Payload> {
        try {
            var cursor = 0
            val nameRead = RREQProtocol.name.read(data, baseOffset = cursor)
            cursor += nameRead.bytesRead
            
            val publicKeyRead = RREQProtocol.publicKey.read(data, baseOffset = cursor)
            cursor += publicKeyRead.bytesRead
            
            if (data.size != cursor) {
                return ParseResult.Failure(ParseError.MalformedPayload("RREQ has trailing bytes"))
            }
            
            return ParseResult.Success(
                Payload.RREQ(name = nameRead.value, publicKey = publicKeyRead.value)
            )
        } catch (e: IndexOutOfBoundsException) {
            return ParseResult.Failure(ParseError.TooShort(data.size, data.size + 1))
        }
    }

    private fun parseRrep(data: ByteArray): ParseResult<Payload> {
        try {
            var cursor = 0
            val nameRead = RREPProtocol.Name.read(data, baseOffset = cursor)
            cursor += nameRead.bytesRead
            
            val publicKeyRead = RREPProtocol.PublicKey.read(data, baseOffset = cursor)
            cursor += publicKeyRead.bytesRead
            
            if (data.size != cursor) {
                return ParseResult.Failure(ParseError.MalformedPayload("RREP has trailing bytes"))
            }
            
            return ParseResult.Success(
                Payload.RREP(name = nameRead.value, publicKey = publicKeyRead.value)
            )
        } catch (e: IndexOutOfBoundsException) {
            return ParseResult.Failure(ParseError.TooShort(data.size, data.size + 1))
        }
    }

    private fun parseRerr(data: ByteArray): ParseResult<Payload> {
        try {
            val destinationsRead = RERRProtocol.destinations.read(data, baseOffset = 0)
            
            if (data.size != destinationsRead.bytesRead) {
                return ParseResult.Failure(ParseError.MalformedPayload("RERR has trailing bytes"))
            }
            
            return ParseResult.Success(Payload.RERR(destinations = destinationsRead.value))
        } catch (e: IndexOutOfBoundsException) {
            return ParseResult.Failure(ParseError.TooShort(data.size, data.size + 1))
        }
    }
}
