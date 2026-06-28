package com.minor.packetprocessor

import com.minor.model.Header
import com.minor.model.HeaderProtocol
import com.minor.model.ParseError
import com.minor.model.ParseResult
import com.minor.model.parser

object HeaderParser: parser<Header> {

    private const val SUPPORTED_VERSION = 1
    private const val MAX_PAYLOAD = 65535

    override fun parse(data: ByteArray): ParseResult<Header> {
        if (data.size < HeaderProtocol.HEADER_SIZE) {
            return ParseResult.Failure(
                ParseError.TooShort(data.size, HeaderProtocol.HEADER_SIZE)
            )
        }

        val magic = HeaderProtocol.magic.read(data)
        if (magic != HeaderProtocol.magic.EXPECTED) {
            return ParseResult.Failure(ParseError.InvalidMagic(magic))
        }

        val version = HeaderProtocol.version.read(data)
        if (version != SUPPORTED_VERSION) {
            return ParseResult.Failure(ParseError.InvalidVersion(version))
        }

        val payloadLength = HeaderProtocol.payloadLength.read(data)
        if (payloadLength !in 0..MAX_PAYLOAD) {
            return ParseResult.Failure(ParseError.InvalidPayloadLength(payloadLength))
        }

        val header = Header(
            magic = magic,
            version = version,
            type = HeaderProtocol.type.read(data),
            flags = HeaderProtocol.flags.read(data),
            hopcount = HeaderProtocol.hopcount.read(data),
            ttl = HeaderProtocol.ttl.read(data),
            reserved = HeaderProtocol.reserved.read(data),
            sourceNodeId = HeaderProtocol.sourceNodeId.read(data),
            destNodeId = HeaderProtocol.destNodeId.read(data),
            id = HeaderProtocol.id.read(data),
            originTimestamp = HeaderProtocol.originTimestamp.read(data),
            payloadLength = payloadLength
        )

        return ParseResult.Success(header)
    }
}

object HeaderSerializer {
    fun serialize(header: Header): ByteArray {
        require(header.payloadLength in 0..65535) {
            "payloadLength ${header.payloadLength} overflows UInt16"
        }

        val buf = ByteArray(HeaderProtocol.HEADER_SIZE)

        HeaderProtocol.magic.write(buf, header.magic)
        HeaderProtocol.version.write(buf, header.version)
        HeaderProtocol.type.write(buf, header.type)
        HeaderProtocol.flags.write(buf, header.flags)
        HeaderProtocol.hopcount.write(buf, header.hopcount)
        HeaderProtocol.ttl.write(buf, header.ttl)
        HeaderProtocol.reserved.write(buf, header.reserved)
        HeaderProtocol.sourceNodeId.write(buf, header.sourceNodeId)
        HeaderProtocol.destNodeId.write(buf, header.destNodeId)
        HeaderProtocol.id.write(buf, header.id)
        HeaderProtocol.originTimestamp.write(buf, header.originTimestamp)
        HeaderProtocol.payloadLength.write(buf, header.payloadLength)

        return buf
    }

    /** Serialize header + payload into a single contiguous buffer. */
    fun serializeWithPayload(header: Header, payload: ByteArray): ByteArray {
        require(payload.size == header.payloadLength) {
            "payload size ${payload.size} != header.payloadLength ${header.payloadLength}"
        }
        val headerBytes = serialize(header)
        return headerBytes + payload
    }
}