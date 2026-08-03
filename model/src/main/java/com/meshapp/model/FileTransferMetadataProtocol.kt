package com.meshapp.model

object FileTransferMetadataProtocol : Field<FileTransferMetadata> {
    override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<FileTransferMetadata> {
        var cursor = baseOffset
        
        val filenameLen = readU8(data, cursor).also { cursor += 1 }
        val filename = readString(data, cursor, filenameLen).also { cursor += filenameLen }
        
        val size = readI64(data, cursor).also { cursor += 8 }
        
        val checksumLen = readU8(data, cursor).also { cursor += 1 }
        val checksum = readString(data, cursor, checksumLen).also { cursor += checksumLen }
        
        val chunkSize = readU32(data, cursor).toInt().also { cursor += 4 }
        val totalChunks = readU32(data, cursor).toInt().also { cursor += 4 }
        
        val senderNodeId = NodeId(readBytes(data, cursor, 32)).also { cursor += 32 }
        
        val createdAt = readI64(data, cursor).also { cursor += 8 }
        
        return ReadWithLength(
            FileTransferMetadata(filename, size, checksum, chunkSize, totalChunks, senderNodeId, createdAt),
            cursor - baseOffset
        )
    }

    override fun write(data: ByteArray, value: FileTransferMetadata, baseOffset: Int): Int {
        var cursor = baseOffset
        
        val filenameBytes = value.filename.encodeToByteArray()
        writeU8(data, cursor, filenameBytes.size).also { cursor += 1 }
        writeBytes(data, cursor, filenameBytes, filenameBytes.size).also { cursor += filenameBytes.size }
        
        writeI64(data, cursor, value.size).also { cursor += 8 }
        
        val checksumBytes = value.checksum.encodeToByteArray()
        writeU8(data, cursor, checksumBytes.size).also { cursor += 1 }
        writeBytes(data, cursor, checksumBytes, checksumBytes.size).also { cursor += checksumBytes.size }
        
        writeU32(data, cursor, value.chunkSize.toLong()).also { cursor += 4 }
        writeU32(data, cursor, value.totalChunks.toLong()).also { cursor += 4 }
        
        writeBytes(data, cursor, value.senderNodeId.bytes, 32).also { cursor += 32 }
        
        writeI64(data, cursor, value.createdAt).also { cursor += 8 }
        
        return cursor - baseOffset
    }
}
