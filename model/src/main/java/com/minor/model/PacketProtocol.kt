package com.minor.model

enum class FieldLength(val length: Int) {
    MAGIC(2),
    VERSION(1),
    TYPE(1),
    FLAGS(1),
    HOPCOUNT(1),
    TTL(1),
    RESERVED(1),
    SOURCE_NODE_ID(32),
    DEST_NODE_ID(32),
    ID(8),
    ORIGIN_TIMESTAMP(8),
    PAYLOAD_LENGTH(2)
}

class Field(val offset: Int, val length: Int)

object Protocol {

    private var cursor = 0

    private fun f(len: FieldLength): Field {
        val field = Field(cursor, len.length)
        cursor += len.length
        return field
    }

    // Reset + build layout once
    private fun build() {
        cursor = 0
        MAGIC = f(FieldLength.MAGIC)
        VERSION = f(FieldLength.VERSION)
        TYPE = f(FieldLength.TYPE)
        FLAGS = f(FieldLength.FLAGS)
        HOPCOUNT = f(FieldLength.HOPCOUNT)
        TTL = f(FieldLength.TTL)
        RESERVED = f(FieldLength.RESERVED)
        SOURCE_NODE_ID = f(FieldLength.SOURCE_NODE_ID)
        DEST_NODE_ID = f(FieldLength.DEST_NODE_ID)
        ID = f(FieldLength.ID)
        ORIGIN_TIMESTAMP = f(FieldLength.ORIGIN_TIMESTAMP)
        PAYLOAD_LENGTH = f(FieldLength.PAYLOAD_LENGTH)
    }

    lateinit var MAGIC: Field
    lateinit var VERSION: Field
    lateinit var TYPE: Field
    lateinit var FLAGS: Field
    lateinit var HOPCOUNT: Field
    lateinit var TTL: Field
    lateinit var RESERVED: Field
    lateinit var SOURCE_NODE_ID: Field
    lateinit var DEST_NODE_ID: Field
    lateinit var ID: Field
    lateinit var ORIGIN_TIMESTAMP: Field
    lateinit var PAYLOAD_LENGTH: Field

    val HEADER_SIZE: Int

    init {
        build()
        HEADER_SIZE = cursor
    }
}