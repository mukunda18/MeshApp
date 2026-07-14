package com.meshapp.model

object RERRProtocol {
    const val COUNT_LENGTH = 1
    const val NODE_ID_LENGTH = 32

    object destinations : Field<List<NodeId>> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<List<NodeId>> {
            val count = readU8(data, baseOffset)
            val list = mutableListOf<NodeId>()
            var currentOffset = baseOffset + COUNT_LENGTH
            repeat(count) {
                list.add(NodeId(readBytes(data, currentOffset, NODE_ID_LENGTH)))
                currentOffset += NODE_ID_LENGTH
            }
            return ReadWithLength(list, currentOffset - baseOffset)
        }

        override fun write(data: ByteArray, value: List<NodeId>, baseOffset: Int): Int {
            writeU8(data, baseOffset, value.size)
            var currentOffset = baseOffset + COUNT_LENGTH
            for (nodeId in value) {
                writeBytes(data, currentOffset, nodeId.bytes, NODE_ID_LENGTH)
                currentOffset += NODE_ID_LENGTH
            }
            return currentOffset - baseOffset
        }
    }
}
