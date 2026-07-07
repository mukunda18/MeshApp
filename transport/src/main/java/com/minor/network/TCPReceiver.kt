package com.minor.network

import android.util.Log
import com.minor.model.Envelope
import com.minor.logger.MeshLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlin.time.Duration.Companion.milliseconds

class TCPReceiver(
    private val port: Int,
    private val scope: CoroutineScope,
    private val acceptTimeoutMs: Int,
    private val bufferCapacity: Int,
    private val tcpReadTimeoutMs: Int,
    private val tcpMaxPacketSize: Int
) {
    private val incomingChannel = Channel<Envelope>(
        capacity = bufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val incoming: ReceiveChannel<Envelope> get() = incomingChannel

    private val serverSocket = ServerSocket().apply {
        reuseAddress = true
        soTimeout = acceptTimeoutMs
        bind(InetSocketAddress(port))
    }

    private var serverJob: Job? = null
    private val activeClients = mutableListOf<Client>()
    private val clientsMutex = Mutex()
    private val removeChannel = Channel<Client>(Channel.UNLIMITED)

    fun start() {
        if (serverJob != null) return
        MeshLogger.info("TCPReceiver", "Starting TCP receiver on port $port")
        serverJob = scope.launch(Dispatchers.IO) {
            launch { processRemovals() }
            while (isActive) {
                try {
                    val clientSocket = serverSocket.accept()
                    MeshLogger.info("TCPReceiver", "Accepted new TCP connection from ${clientSocket.remoteSocketAddress}")
                    val client = Client(clientSocket, scope, tcpMaxPacketSize, tcpReadTimeoutMs, { incomingChannel.trySend(it) }, removeChannel)
                    clientsMutex.withLock { activeClients.add(client) }
                    client.start()
                } catch (_: SocketTimeoutException) {
                    // Responsive to cancellation
                } catch (e: SocketException) {
                    // Expected when serverSocket.close() is called during shutdown
                    if (isActive && !serverSocket.isClosed) {
                        Log.e("TCPReceiver", "Socket error in accept loop", e)
                        MeshLogger.error("TCPReceiver", "Socket error in accept loop", e.toString())
                    }
                    break
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (isActive) {
                        Log.e("TCPReceiver", "Unexpected error in accept loop", e)
                        MeshLogger.error("TCPReceiver", "Unexpected error in accept loop", e.toString())
                        delay(100.milliseconds)
                    }
                }
            }
        }
    }

    private suspend fun processRemovals() {
        for (client in removeChannel) {
            client.close()
            clientsMutex.withLock { activeClients.remove(client) }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        MeshLogger.info("TCPReceiver", "Closing TCP receiver on port $port")
        serverSocket.close()
        incomingChannel.close()
        serverJob?.cancel()
        serverJob = null
        
        clientsMutex.withLock { 
            activeClients.forEach { it.close() }
            activeClients.clear()
        }
    }
}
