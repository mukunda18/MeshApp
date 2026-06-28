package com.minor.model

interface parser<T>{
    fun parse(data: ByteArray): ParseResult<T>
}
sealed interface ParseError {
    data class TooShort(val actual: Int, val expected: Int) : ParseError {
        override fun toString() =
            "Packet too short: got $actual bytes, need at least $expected"
    }

    data class InvalidMagic(val actual: Int) : ParseError {
        override fun toString() =
            "Invalid magic: expected 0x${"%04X".format(HeaderProtocol.magic.EXPECTED)}, " +
                    "got 0x${"%04X".format(actual)}"
    }

    data class InvalidVersion(val actual: Int) : ParseError {
        override fun toString() = "Unsupported version: $actual"
    }

    data class InvalidPayloadLength(val actual: Int) : ParseError {
        override fun toString() = "Invalid payload length: $actual"
    }
}

sealed interface ParseResult<out T> {
    data class Success<T>(val value: T) : ParseResult<T>
    data class Failure(val error: ParseError) : ParseResult<Nothing>
}