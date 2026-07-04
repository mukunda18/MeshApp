package com.minor.model

internal fun readU8(data: ByteArray, offset: Int): Int =
    data[offset].toInt() and 0xFF

internal fun writeU8(data: ByteArray, offset: Int, value: Int) {
    data[offset] = (value and 0xFF).toByte()
}

internal fun readU16(data: ByteArray, offset: Int): Int =
    ((data[offset].toInt() and 0xFF) shl 8) or
            (data[offset + 1].toInt() and 0xFF)

internal fun writeU16(data: ByteArray, offset: Int, value: Int) {
    data[offset]     = ((value shr 8) and 0xFF).toByte()
    data[offset + 1] = (value and 0xFF).toByte()
}

internal fun readU32(data: ByteArray, offset: Int): Long {
    var result = 0L
    for (i in 0..3) result = (result shl 8) or (data[offset + i].toLong() and 0xFF)
    return result
}

internal fun writeU32(data: ByteArray, offset: Int, value: Long) {
    for (i in 0..3) data[offset + i] = ((value shr (24 - i * 8)) and 0xFF).toByte()
}

internal fun readI64(data: ByteArray, offset: Int): Long {
    var result = 0L
    for (i in 0..7) result = (result shl 8) or (data[offset + i].toLong() and 0xFF)
    return result
}

internal fun writeI64(data: ByteArray, offset: Int, value: Long) {
    for (i in 0..7) data[offset + i] = ((value shr (56 - i * 8)) and 0xFF).toByte()
}

internal fun readBytes(data: ByteArray, offset: Int, length: Int): ByteArray =
    data.copyOfRange(offset, offset + length)

internal fun writeBytes(data: ByteArray, offset: Int, value: ByteArray, length: Int) {
    require(value.size == length) { "Expected $length bytes, got ${value.size}" }
    value.copyInto(data, offset)
}
internal fun readString(data: ByteArray, offset: Int, length: Int): String {
    val actualLength = length
    return data.decodeToString(offset, offset + actualLength)
}

internal fun writeString(data: ByteArray, offset: Int, value: String, length: Int) {
    val bytes = value.encodeToByteArray()
    writeBytes(data,offset, bytes, length)
}