package com.meshapp.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.meshapp.packetprocessor.HeaderParser
import com.meshapp.model.HeaderProtocol
import com.meshapp.model.Packet
import com.meshapp.model.Envelope
import com.meshapp.model.ParseResult
import com.meshapp.logger.MeshLogger
import kotlinx.coroutines.CancellationException
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
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class UdpSocket(
    context: Context,
    private val port: Int,
    private val scope: CoroutineScope,
    private val maxPacketSize: Int,
    private val receiveTimeoutMs: Int,
    private val bufferCapacity: Int,
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
        soTimeout = receiveTimeoutMs
        bind(InetSocketAddress(this@UdpSocket.port))
    }

    private val incomingChannel = Channel<Envelope>(
        capacity = bufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val incoming: ReceiveChannel<Envelope> get() = incomingChannel

    private var job: Job? = null

    suspend fun send(
        payload: ByteArray,
        address: String,
        offset: Int = 0,
        length: Int = payload.size
    ) = withContext(Dispatchers.IO) {
        try {
            socket.send(
                DatagramPacket(payload, offset, length, InetSocketAddress(address, port)),
            )
        } catch (e: Exception) {
            MeshLogger.error("UdpSocket", "Failed to send UDP to $address", e.toString())
            throw e
        }
    }

    fun start() {
        if (job != null) return
        MeshLogger.info("UdpSocket", "Starting UDP socket on port $port")
        multicastLock?.acquire()
        job = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(maxPacketSize)
            while (isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    val dataCopy = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                    val result = HeaderParser.parse(dataCopy)
                    if (result is ParseResult.Success) {
                        incomingChannel.trySend(Envelope(
                            packet = Packet(
                                header = result.value,
                                payload = dataCopy.copyOfRange(HeaderProtocol.HEADER_SIZE, dataCopy.size)
                            ),
                            remoteAddress = packet.socketAddress as InetSocketAddress
                        ))
                    } else if (result is ParseResult.Failure) {
                        Log.w("UdpSocket", "Failed to parse header: ${result.error}")
                        MeshLogger.error("UdpSocket", "Failed to parse header from ${packet.socketAddress}", result.error.toString())
                    }
                } catch (_: SocketTimeoutException) {
                    // Loop back to re-check isActive so cancellation is responsive.
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (isActive) {
                        // Avoid logging expected closure during shutdown
                        if (e is SocketException && socket.isClosed) {
                            break
                        }
                        Log.e("UdpSocket", "Error in receive loop", e)
                        MeshLogger.error("UdpSocket", "Error in receive loop", e.toString())
                        delay(100.milliseconds) // Prevent tight loop on persistent error
                    }
                }
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        MeshLogger.info("UdpSocket", "Closing UDP socket on port $port")
        socket.close()
        incomingChannel.close()
        job?.cancel()
        job = null
        if (multicastLock?.isHeld == true) multicastLock.release()
    }
}
