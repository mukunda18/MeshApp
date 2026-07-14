package com.meshapp.packetprocessor

import com.meshapp.model.Header
import com.meshapp.model.HeaderProtocol
import com.meshapp.model.ParseError
import com.meshapp.model.ParseResult
import com.meshapp.logger.MeshLogger

object HeaderParser {
    fun parse(data: ByteArray): ParseResult<Header> {
        if (data.size < HeaderProtocol.HEADER_SIZE) {
            val error = ParseError.TooShort(data.size, HeaderProtocol.HEADER_SIZE)
            MeshLogger.error("HeaderParser", "Failed to parse header: Too short", error.toString())
            return ParseResult.Failure(error)
        }

        val magic = HeaderProtocol.Magic.read(data).value
        if (magic != HeaderProtocol.Magic.EXPECTED) {
            val error = ParseError.InvalidMagic(magic)
            MeshLogger.error("HeaderParser", "Failed to parse header: Invalid magic", error.toString())
            return ParseResult.Failure(error)
        }

        val version = HeaderProtocol.Version.read(data).value
        if (version != HeaderProtocol.Version.SUPPORTED_VERSION) {
            val error = ParseError.InvalidVersion(version)
            MeshLogger.error("HeaderParser", "Failed to parse header: Invalid version", error.toString())
            return ParseResult.Failure(error)
        }

        val payloadLength = HeaderProtocol.PayloadLength.read(data).value
        if (payloadLength !in (0..HeaderProtocol.MAX_PAYLOAD)) {
            val error = ParseError.InvalidPayloadLength(payloadLength)
            MeshLogger.error("HeaderParser", "Failed to parse header: Invalid payload length", error.toString())
            return ParseResult.Failure(error)
        }

        val header = Header(
            magic = magic,
            version = version,
            type = HeaderProtocol.Type.read(data).value,
            flags = HeaderProtocol.Flags.read(data).value,
            hopcount = HeaderProtocol.Hopcount.read(data).value,
            ttl = HeaderProtocol.TTL.read(data).value,
            reserved = HeaderProtocol.Reserved.read(data).value,
            immediateSenderNodeId = HeaderProtocol.ImmediateSenderNodeId.read(data).value,
            sourceNodeId = HeaderProtocol.SourceNodeId.read(data).value,
            destNodeId = HeaderProtocol.DestNodeId.read(data).value,
            id = HeaderProtocol.Id.read(data).value,
            originTimestamp = HeaderProtocol.OriginTimestamp.read(data).value,
            payloadLength = payloadLength
        )

        MeshLogger.packetReceived("HeaderParser", "Parsed header for packet ${header.id}", "From: ${header.sourceNodeId} to ${header.destNodeId}")
        return ParseResult.Success(header)
    }
}