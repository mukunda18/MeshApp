package com.minor.routing

import com.minor.network.NetworkScanner
import com.minor.network.TCPSender
import com.minor.network.UdpSocket

/**
 * Send only transport contract used by Sender to dispatch packets
 * The receive side is handled by TCPReceiver and UdpSocket which now emit Envelope
 * so RoutingModule can extract the sender IP directly from remoteAddress
 */
interface MeshTransport {
    suspend fun sendTcp(bytes: ByteArray, ip: String)
    suspend fun broadcastUdp(bytes: ByteArray)
}

/**
 * Production adapter wrapping the real TCPSender and UdpSocket
 * Broadcasts are sent on every active network interface returned by NetworkScanner
 */
class RealMeshTransport(
    private val tcpSender: TCPSender,
    private val udpSocket: UdpSocket
) : MeshTransport {

    override suspend fun sendTcp(bytes: ByteArray, ip: String) {
        tcpSender.send(bytes, ip)
    }

    override suspend fun broadcastUdp(bytes: ByteArray) {
        for (iface in NetworkScanner.getNetworkInterfaceInfo()) {
            val addr = iface.broadcastAddress.hostAddress ?: continue
            try {
                udpSocket.send(bytes, addr)
            } catch (_: Exception) {
              
            }
        }
    }
}