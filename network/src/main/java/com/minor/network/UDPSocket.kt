package com.minor.network

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import com.minor.model.Datagram

class UdpSocket(
    context: Context,
    private val port: Int,
    private val scope: CoroutineScope,
    useMulticastLock: Boolean = true,
) {
    private val multicastLock = if (useMulticastLock) {
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifi.createMulticastLock("mesh-udp")
    } else {
        null
    }

    private val socket = DatagramSocket(null).apply {
        reuseAddress = true
        broadcast = true
        soTimeout = RECEIVE_TIMEOUT_MS
        bind(InetSocketAddress(port))
    }

    private val channel = Channel<Datagram>(
        capacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val incoming: Channel<Datagram> get() = channel

    private var job: Job? = null

    suspend fun send(payload: ByteArray, address: String) = withContext(Dispatchers.IO) {
        socket.send(
            DatagramPacket(payload, payload.size, InetSocketAddress(address, port)),
        )
    }

    fun start() {
        if (job != null) return
        multicastLock?.acquire()
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val buffer = ByteArray(MAX_PACKET_SIZE)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    channel.trySend(Datagram(
                        address = packet.address.hostAddress!!,
                        port = packet.port,
                        data = packet.data
                    ))
                } catch (_: SocketTimeoutException) {
                    // Loop back to re-check isActive so cancellation is responsive.
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        if (multicastLock?.isHeld == true) multicastLock.release()
    }

    fun close() {
        stop()
        channel.close()
        socket.close()
    }

    private companion object {
        const val MAX_PACKET_SIZE = 64 * 1024
        const val RECEIVE_TIMEOUT_MS = 500
        const val BUFFER_CAPACITY = 1024
    }
}
