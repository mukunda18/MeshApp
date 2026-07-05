package com.minor.packetprocessor

import com.minor.model.AckProtocol
import com.minor.model.HeaderProtocol
import com.minor.model.HelloProtocol
import com.minor.model.MessageProtocol
import com.minor.model.Packet
import com.minor.model.ParseError
import com.minor.model.ParseResult
import com.minor.model.Payload
import com.minor.logger.MeshLogger
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
        else -> {
            val error = ParseError.UnsupportedType(type)
            MeshLogger.error("PayloadParser", "Unsupported payload type: $type")
            ParseResult.Failure(error)
        }
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
                val error = ParseError.MalformedPayload("HELLO has trailing bytes")
                MeshLogger.error("PayloadParser", "Failed to parse HELLO", error.toString())
                return ParseResult.Failure(error)
            }
            
            return ParseResult.Success(
                Payload.Hello(
                    name = nameRead.value,
                    publicKey = publicKeyRead.value,
                    routeEntries = routeEntriesRead.value
                )
            )
        } catch (e: IndexOutOfBoundsException) {
            val error = ParseError.TooShort(data.size, data.size + 1)
            MeshLogger.error("PayloadParser", "Failed to parse HELLO: Buffer too short", e.toString())
            return ParseResult.Failure(error)
        }
    }

    private fun parseMessage(data: ByteArray): ParseResult<Payload> {
        try {
            var cursor = 0
            val versionRead = MessageProtocol.envVersion.read(data, cursor)
            cursor += versionRead.bytesRead

            val senderNodeIdRead = MessageProtocol.senderNodeId.read(data, cursor)
            cursor += senderNodeIdRead.bytesRead

            val encSymKeyRead = MessageProtocol.encSymKey.read(data, cursor)
            cursor += encSymKeyRead.bytesRead

            val nonceRead = MessageProtocol.nonce.read(data, cursor)
            cursor += nonceRead.bytesRead

            val ciphertextRead = MessageProtocol.ciphertext.read(data, cursor)
            cursor += ciphertextRead.bytesRead

            val signatureRead = MessageProtocol.signature.read(data, cursor)
            cursor += signatureRead.bytesRead

            if (data.size != cursor) {
                val error = ParseError.MalformedPayload("MESSAGE has trailing bytes")
                MeshLogger.error("PayloadParser", "Failed to parse MESSAGE", error.toString())
                return ParseResult.Failure(error)
            }

            return ParseResult.Success(
                Payload.Message(
                    envelope = com.minor.model.SecureEnvelope(
                        envVersion = versionRead.value,
                        senderNodeId = senderNodeIdRead.value,
                        encSymKey = encSymKeyRead.value,
                        nonce = nonceRead.value,
                        ciphertext = ciphertextRead.value,
                        signature = signatureRead.value
                    )
                )
            )
        } catch (e: IndexOutOfBoundsException) {
            val error = ParseError.TooShort(data.size, data.size + 1)
            MeshLogger.error("PayloadParser", "Failed to parse MESSAGE: Buffer too short", e.toString())
            return ParseResult.Failure(error)
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
                val error = ParseError.MalformedPayload("ACK has trailing bytes")
                MeshLogger.error("PayloadParser", "Failed to parse ACK", error.toString())
                return ParseResult.Failure(error)
            }
            
            return ParseResult.Success(
                Payload.Ack(status = statusRead.value, signature = signatureRead.value)
            )
        } catch (e: IndexOutOfBoundsException) {
            val error = ParseError.TooShort(data.size, data.size + 1)
            MeshLogger.error("PayloadParser", "Failed to parse ACK: Buffer too short", e.toString())
            return ParseResult.Failure(error)
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
                val error = ParseError.MalformedPayload("RREQ has trailing bytes")
                MeshLogger.error("PayloadParser", "Failed to parse RREQ", error.toString())
                return ParseResult.Failure(error)
            }
            
            return ParseResult.Success(
                Payload.RREQ(name = nameRead.value, publicKey = publicKeyRead.value)
            )
        } catch (e: IndexOutOfBoundsException) {
            val error = ParseError.TooShort(data.size, data.size + 1)
            MeshLogger.error("PayloadParser", "Failed to parse RREQ: Buffer too short", e.toString())
            return ParseResult.Failure(error)
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
                val error = ParseError.MalformedPayload("RREP has trailing bytes")
                MeshLogger.error("PayloadParser", "Failed to parse RREP", error.toString())
                return ParseResult.Failure(error)
            }
            
            return ParseResult.Success(
                Payload.RREP(name = nameRead.value, publicKey = publicKeyRead.value)
            )
        } catch (e: IndexOutOfBoundsException) {
            val error = ParseError.TooShort(data.size, data.size + 1)
            MeshLogger.error("PayloadParser", "Failed to parse RREP: Buffer too short", e.toString())
            return ParseResult.Failure(error)
        }
    }

    private fun parseRerr(data: ByteArray): ParseResult<Payload> {
        try {
            val destinationsRead = RERRProtocol.destinations.read(data, baseOffset = 0)
            
            if (data.size != destinationsRead.bytesRead) {
                val error = ParseError.MalformedPayload("RERR has trailing bytes")
                MeshLogger.error("PayloadParser", "Failed to parse RERR", error.toString())
                return ParseResult.Failure(error)
            }
            
            return ParseResult.Success(Payload.RERR(destinations = destinationsRead.value))
        } catch (e: IndexOutOfBoundsException) {
            val error = ParseError.TooShort(data.size, data.size + 1)
            MeshLogger.error("PayloadParser", "Failed to parse RERR: Buffer too short", e.toString())
            return ParseResult.Failure(error)
        }
    }
}
