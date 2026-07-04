package com.minor.network

import android.util.Log

/**
 * Send only transport contract used by Sender to dispatch packets
 * The receive side is handled by TCPReceiver and UdpSocket which now emit Envelope
 * so RoutingModule can extract the sender IP directly from remoteAddress
 */
class MeshTransport(
    private val tcpSender: TCPSender,
    private val udpSocket: UdpSocket,
)  {

    suspend fun sendTcp(bytes: ByteArray, ip: String) {
        tcpSender.send(bytes, ip)
    }

    suspend fun broadcastUdp(bytes: ByteArray) {
        for (iface in NetworkScanner.getNetworkInterfaceInfo()) {
            val addr = iface.broadcastAddress.hostAddress ?: continue
            try {
                udpSocket.send(bytes, addr)
            } catch (e: Exception) {
                Log.w("MeshTransport", "Failed to send UDP broadcast to $addr", e)
            }
        }
    }
}