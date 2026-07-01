package com.minor.network

import android.content.Context
import android.net.wifi.WifiManager
import com.minor.packetprocessor.HeaderParser
import com.minor.model.HeaderProtocol
import com.minor.model.Packet
import com.minor.model.ParseResult
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
import kotlinx.coroutines.channels.ReceiveChannel

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

    private val channel = Channel<Packet>(
        capacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val incoming: ReceiveChannel<Packet> get() = channel

    private var job: Job? = null

    suspend fun send(
        payload: ByteArray,
        address: String,
        offset: Int = 0,
        length: Int = payload.size
    ) = withContext(Dispatchers.IO) {
        socket.send(
            DatagramPacket(payload, offset, length, InetSocketAddress(address, port)),
        )
    }

    fun start() {
        if (job != null) return
        multicastLock?.acquire()
        job = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(MAX_PACKET_SIZE)
            while (isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    val dataCopy = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                    val result = HeaderParser.parse(dataCopy)
                    if (result is ParseResult.Success) {
                        channel.trySend(Packet(
                            header = result.value,
                            payload = dataCopy.copyOfRange(HeaderProtocol.HEADER_SIZE, dataCopy.size)
                        ))
                    }
                } catch (_: SocketTimeoutException) {
                    // Loop back to re-check isActive so cancellation is responsive.
                }
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        socket.close()
        channel.close()
        job?.cancel()
        job = null
        if (multicastLock?.isHeld == true) multicastLock.release()
    }
    private companion object {
        const val MAX_PACKET_SIZE = 64 * 1024
        const val RECEIVE_TIMEOUT_MS = 500
        const val BUFFER_CAPACITY = 1024
    }
}
