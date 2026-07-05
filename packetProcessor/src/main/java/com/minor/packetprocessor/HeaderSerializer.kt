package com.minor.packetprocessor

import com.minor.model.Header
import com.minor.model.HeaderProtocol

object HeaderSerializer {
    fun serialize(header: Header, buffer: ByteArray, offset: Int): Int {
        require(header.payloadLength in (0..65535)) {
            "PayloadLength ${header.payloadLength} overflows UInt16"
        }
        require(buffer.size >= offset + HeaderProtocol.HEADER_SIZE) {
            "Buffer too small: ${buffer.size} < ${offset + HeaderProtocol.HEADER_SIZE}"
        }

        HeaderProtocol.Magic.write(buffer, header.magic, offset)
        HeaderProtocol.Version.write(buffer, header.version, offset)
        HeaderProtocol.Type.write(buffer, header.type, offset)
        HeaderProtocol.Flags.write(buffer, header.flags, offset)
        HeaderProtocol.Hopcount.write(buffer, header.hopcount, offset)
        HeaderProtocol.TTL.write(buffer, header.ttl, offset)
        HeaderProtocol.Reserved.write(buffer, header.reserved, offset)
        HeaderProtocol.SourceNodeId.write(buffer, header.sourceNodeId, offset)
        HeaderProtocol.DestNodeId.write(buffer, header.destNodeId, offset)
        HeaderProtocol.Id.write(buffer, header.id, offset)
        HeaderProtocol.OriginTimestamp.write(buffer, header.originTimestamp, offset)
        HeaderProtocol.PayloadLength.write(buffer, header.payloadLength, offset)

        return HeaderProtocol.HEADER_SIZE
    }
}
