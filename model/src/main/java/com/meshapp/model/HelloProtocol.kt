package com.meshapp.model

object HelloProtocol {
    const val PUBLIC_KEY_LENGTH = 91  // P-256 DER-encoded public key
    const val ROUTE_COUNT_LENGTH = 2
    const val NAME_LEN_LENGTH = 1

    object name : Field<String> {
        override fun read(data: ByteArray, baseOffset: Int): ReadWithLength<String> {
            val len = readU8(data, baseOffset)
            return ReadWithLength(
                readString(data, baseOffset + 1, len),
                NAME_LEN_LENGTH + len
            )
        }

        override fun write(data: ByteArray, value: String, baseOffset: Int): Int {
            val bytes = value.encodeToByteArray()
            writeU8(data, baseOffset, bytes.size)
            writeBytes(data, baseOffset + 1, bytes, bytes.size)
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
                currentOffset += nodeIdRead.bytesRead

                val hopcountRead = RouteProtocol.hopcount.read(data, currentOffset)
                currentOffset += hopcountRead.bytesRead

                val publicKeyRead = RouteProtocol.publicKey.read(data, currentOffset)
                currentOffset += publicKeyRead.bytesRead

                val timestampRead = RouteProtocol.timestamp.read(data, currentOffset)
                currentOffset += timestampRead.bytesRead

                val nameRead = RouteProtocol.name.read(data, currentOffset)
                currentOffset += nameRead.bytesRead

                val entry = RouteEntry(
                    nodeId = nodeIdRead.value,
                    hopcount = hopcountRead.value,
                    publicKey = publicKeyRead.value,
                    timestamp = timestampRead.value,
                    name = nameRead.value
                )
                entries.add(entry)
            }
            return ReadWithLength(entries, currentOffset - baseOffset)
        }

        override fun write(data: ByteArray, value: List<RouteEntry>, baseOffset: Int): Int {
            writeU16(data, baseOffset, value.size)
            var currentOffset = baseOffset + ROUTE_COUNT_LENGTH
            
            for (entry in value) {
                currentOffset += RouteProtocol.nodeId.write(data, entry.nodeId, currentOffset)
                currentOffset += RouteProtocol.hopcount.write(data, entry.hopcount, currentOffset)
                currentOffset += RouteProtocol.publicKey.write(data, entry.publicKey, currentOffset)
                currentOffset += RouteProtocol.timestamp.write(data, entry.timestamp, currentOffset)
                currentOffset += RouteProtocol.name.write(data, entry.name, currentOffset)
            }
            return currentOffset - baseOffset
        }
    }
}
