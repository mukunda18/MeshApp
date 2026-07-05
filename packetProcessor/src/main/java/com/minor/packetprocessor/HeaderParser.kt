package com.minor.packetprocessor

import com.minor.model.Header
import com.minor.model.HeaderProtocol
import com.minor.model.ParseError
import com.minor.model.ParseResult

object HeaderParser {
    fun parse(data: ByteArray): ParseResult<Header> {
        if (data.size < HeaderProtocol.HEADER_SIZE) {
            return ParseResult.Failure(
                ParseError.TooShort(data.size, HeaderProtocol.HEADER_SIZE),
            )
        }

        val magic = HeaderProtocol.Magic.read(data).value
        if (magic != HeaderProtocol.Magic.EXPECTED) {
            return ParseResult.Failure(ParseError.InvalidMagic(magic))
        }

        val version = HeaderProtocol.Version.read(data).value
        if (version != HeaderProtocol.Version.SUPPORTED_VERSION) {
            return ParseResult.Failure(ParseError.InvalidVersion(version))
        }

        val payloadLength = HeaderProtocol.PayloadLength.read(data).value
        if (payloadLength !in (0..HeaderProtocol.MAX_PAYLOAD)) {
            return ParseResult.Failure(ParseError.InvalidPayloadLength(payloadLength))
        }

        val header = Header(
            magic = magic,
            version = version,
            type = HeaderProtocol.Type.read(data).value,
            flags = HeaderProtocol.Flags.read(data).value,
            hopcount = HeaderProtocol.Hopcount.read(data).value,
            ttl = HeaderProtocol.TTL.read(data).value,
            reserved = HeaderProtocol.Reserved.read(data).value,
            sourceNodeId = HeaderProtocol.SourceNodeId.read(data).value,
            destNodeId = HeaderProtocol.DestNodeId.read(data).value,
            id = HeaderProtocol.Id.read(data).value,
            originTimestamp = HeaderProtocol.OriginTimestamp.read(data).value,
            payloadLength = payloadLength
        )

        return ParseResult.Success(header)
    }
}