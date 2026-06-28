package com.minor.model

import java.net.InetSocketAddress

data class NetworkMessage(
    val address: InetSocketAddress,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NetworkMessage

        if (address != other.address) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}