package com.meshapp.model

object FileSignalProtocol : Field<FileSignal> {
    private const val SIGNAL_TYPE_LENGTH = 1
    private const val PAYLOAD_LEN_LENGTH = 4

    override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<FileSignal> {
        var cursor = baseOffset
        val transferId = MessageProtocol.messageId.read(data, cursor).also { cursor += it.bytesRead }.value
        val type = readU8(data, cursor).also { cursor += SIGNAL_TYPE_LENGTH }
        val len = readU32(data, cursor).toInt().also { cursor += PAYLOAD_LEN_LENGTH }
        val payload = readBytes(data, cursor, len).also { cursor += len }
        return ReadWithLength(FileSignal(transferId, type, payload), cursor - baseOffset)
    }

    override fun write(data: ByteArray, value: FileSignal, baseOffset: Int): Int {
        var cursor = baseOffset
        cursor += MessageProtocol.messageId.write(data, value.transferId, cursor)
        writeU8(data, cursor, value.type).also { cursor += SIGNAL_TYPE_LENGTH }
        writeU32(data, cursor, value.payload.size.toLong()).also { cursor += PAYLOAD_LEN_LENGTH }
        writeBytes(data, cursor, value.payload, value.payload.size).also { cursor += value.payload.size }
        return cursor - baseOffset
    }
}
