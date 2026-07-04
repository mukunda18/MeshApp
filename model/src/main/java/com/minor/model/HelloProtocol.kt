package com.minor.model

object HelloProtocol {
    const val NAME_LEN_OFFSET = 0
    const val NAME_LEN_LENGTH = 1
    const val NAME_OFFSET = 1
    
    const val PUBLIC_KEY_LENGTH = 32
    const val ROUTE_COUNT_LENGTH = 2

    object name : Field<String> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<String> {
            val len = readU8(data, baseOffset + NAME_LEN_OFFSET)
            return ReadWithLength(
                readString(data, baseOffset + NAME_OFFSET, len),
                NAME_LEN_LENGTH + len
            )
        }

        override fun write(data: ByteArray, value: String, baseOffset: Int): Int {
            val bytes = value.encodeToByteArray()
            writeU8(data, baseOffset + NAME_LEN_OFFSET, bytes.size)
            writeBytes(data, baseOffset + NAME_OFFSET, bytes, bytes.size)
            return NAME_LEN_LENGTH + bytes.size
        }
    }

    object publicKey : Field<PublicKey> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<PublicKey> =
            ReadWithLength(PublicKey(readBytes(data, baseOffset, PUBLIC_KEY_LENGTH)), PUBLIC_KEY_LENGTH)

        override fun write(data: ByteArray, value: PublicKey, baseOffset: Int): Int {
            writeBytes(data, baseOffset, value.bytes, PUBLIC_KEY_LENGTH)
            return PUBLIC_KEY_LENGTH
        }
    }

    object routeEntries : Field<List<RouteEntry>> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<List<RouteEntry>> {
            val count = readU16(data, baseOffset)
            val entries = mutableListOf<RouteEntry>()
            var currentOffset = baseOffset + ROUTE_COUNT_LENGTH
            
            repeat(count) {
                val nodeIdRead = RouteProtocol.nodeId.read(data, currentOffset)
                val hopcountRead = RouteProtocol.hopcount.read(data, currentOffset)
                val publicKeyRead = RouteProtocol.publicKey.read(data, currentOffset)
                val nameRead = RouteProtocol.name.read(data, currentOffset)

                val entry = RouteEntry(
                    nodeId = nodeIdRead.value,
                    hopcount = hopcountRead.value,
                    publicKey = publicKeyRead.value,
                    name = nameRead.value
                )
                entries.add(entry)

                currentOffset += nodeIdRead.bytesRead +
                        hopcountRead.bytesRead +
                        publicKeyRead.bytesRead +
                        nameRead.bytesRead
            }
            return ReadWithLength(entries, currentOffset - baseOffset)
        }

        override fun write(data: ByteArray, value: List<RouteEntry>, baseOffset: Int): Int {
            writeU16(data, baseOffset, value.size)
            var currentOffset = baseOffset + ROUTE_COUNT_LENGTH
            
            for (entry in value) {
                var cursor = currentOffset
                cursor += RouteProtocol.nodeId.write(data, entry.nodeId, cursor)
                cursor += RouteProtocol.hopcount.write(data, entry.hopcount, cursor)
                cursor += RouteProtocol.publicKey.write(data, entry.publicKey, cursor)
                cursor += RouteProtocol.name.write(data, entry.name, cursor)
                currentOffset = cursor
            }
            return currentOffset - baseOffset
        }
    }
}
