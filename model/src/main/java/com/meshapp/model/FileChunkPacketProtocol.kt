package com.meshapp.model

object FileChunkPacketProtocol : Field<FileChunkPacket> {
    private const val CHUNK_INDEX_LENGTH = 4
    private const val TOTAL_CHUNKS_LENGTH = 4
    private const val DATA_LEN_LENGTH = 2

    override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<FileChunkPacket> {
        var cursor = baseOffset
        val transferId = MessageProtocol.messageId.read(data, cursor).also { cursor += it.bytesRead }.value
        val chunkIndex = readU32(data, cursor).toInt().also { cursor += CHUNK_INDEX_LENGTH }
        val totalChunks = readU32(data, cursor).toInt().also { cursor += TOTAL_CHUNKS_LENGTH }
        val dataLen = readU16(data, cursor).also { cursor += DATA_LEN_LENGTH }
        val chunkData = readBytes(data, cursor, dataLen).also { cursor += dataLen }
        return ReadWithLength(FileChunkPacket(transferId, chunkIndex, totalChunks, chunkData), cursor - baseOffset)
    }

    override fun write(data: ByteArray, value: FileChunkPacket, baseOffset: Int): Int {
        var cursor = baseOffset
        cursor += MessageProtocol.messageId.write(data, value.transferId, cursor)
        writeU32(data, cursor, value.chunkIndex.toLong()).also { cursor += CHUNK_INDEX_LENGTH }
        writeU32(data, cursor, value.totalChunks.toLong()).also { cursor += TOTAL_CHUNKS_LENGTH }
        writeU16(data, cursor, value.data.size).also { cursor += DATA_LEN_LENGTH }
        writeBytes(data, cursor, value.data, value.data.size).also { cursor += value.data.size }
        return cursor - baseOffset
    }
}
