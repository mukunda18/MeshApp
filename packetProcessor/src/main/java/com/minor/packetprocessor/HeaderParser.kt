package com.minor.packetprocessor

import com.minor.model.Header
import com.minor.model.HeaderProtocol
import com.minor.model.ParseError
import com.minor.model.ParseResult

object HeaderParser {
    fun parse(data: ByteArray): ParseResult<Header> {
        if (data.size < HeaderProtocol.HEADER_SIZE) {
            return ParseResult.Failure(
                ParseError.TooShort(data.size, HeaderProtocol.HEADER_SIZE)
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
        if (payloadLength !in 0..HeaderProtocol.MAX_PAYLOAD) {
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
            id = HeaderProtocol.id.read(data).value,
            originTimestamp = HeaderProtocol.OriginTimestamp.read(data).value,
            payloadLength = payloadLength
        )

        return ParseResult.Success(header)
    }
}

object HeaderSerializer {
    fun serialize(header: Header): ByteArray {
        require(header.payloadLength in 0..65535) {
            "PayloadLength ${header.payloadLength} overflows UInt16"
        }

        val buf = ByteArray(HeaderProtocol.HEADER_SIZE)

        HeaderProtocol.Magic.write(buf, header.magic)
        HeaderProtocol.Version.write(buf, header.version)
        HeaderProtocol.Type.write(buf, header.type)
        HeaderProtocol.Flags.write(buf, header.flags)
        HeaderProtocol.Hopcount.write(buf, header.hopcount)
        HeaderProtocol.TTL.write(buf, header.ttl)
        HeaderProtocol.Reserved.write(buf, header.reserved)
        HeaderProtocol.SourceNodeId.write(buf, header.sourceNodeId)
        HeaderProtocol.DestNodeId.write(buf, header.destNodeId)
        HeaderProtocol.id.write(buf, header.id)
        HeaderProtocol.OriginTimestamp.write(buf, header.originTimestamp)
        HeaderProtocol.PayloadLength.write(buf, header.payloadLength)

        return buf
    }

    /** Serialize header + payload into a single contiguous buffer. */
    fun serializeWithPayload(header: Header, payload: ByteArray): ByteArray {
        require(payload.size == header.payloadLength) {
            "payload size ${payload.size} != header.PayloadLength ${header.payloadLength}"
        }
        val headerBytes = serialize(header)
        return headerBytes + payload
    }
}