package com.minor.routing

/**
 * Records all TCP sends and UDP broadcasts so tests can assert on them
 * No real network operations are performed
 */
class FakeMeshTransport : MeshTransport {
    val tcpSends = mutableListOf<Pair<ByteArray, String>>()
    val broadcasts = mutableListOf<ByteArray>()

    override suspend fun sendTcp(bytes: ByteArray, ip: String) {
        tcpSends.add(bytes to ip)
    }

    override suspend fun broadcastUdp(bytes: ByteArray) {
        broadcasts.add(bytes)
    }

    fun clearAll() {
        tcpSends.clear()
        broadcasts.clear()
    }
}