package com.minor.network

import android.util.Log
import com.minor.logger.MeshLogger

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
        MeshLogger.info("MeshTransport", "TCP Send request to $ip", "Size: ${bytes.size} bytes")
        tcpSender.send(bytes, ip)
    }

    suspend fun broadcastUdp(bytes: ByteArray) {
        MeshLogger.info("MeshTransport", "UDP Broadcast request", "Size: ${bytes.size} bytes")
        for (iface in NetworkScanner.getNetworkInterfaceInfo()) {
            val addr = iface.broadcastAddress.hostAddress ?: continue
            try {
                udpSocket.send(bytes, addr)
            } catch (e: Exception) {
                Log.w("MeshTransport", "Failed to send UDP broadcast to $addr", e)
                MeshLogger.error("MeshTransport", "Failed to send UDP broadcast to $addr", e.toString())
            }
        }
    }
}