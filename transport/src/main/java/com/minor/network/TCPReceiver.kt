package com.minor.network

import com.minor.model.Envelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException

class TCPReceiver(
    private val port: Int,
    private val scope: CoroutineScope
) {
    val incoming = Channel<Envelope>(
        capacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val serverSocket = ServerSocket().apply {
        reuseAddress = true
        soTimeout = ACCEPT_TIMEOUT_MS
        bind(InetSocketAddress(port))
    }

    private var serverJob: Job? = null
    private val activeClients = mutableListOf<Client>()
    private val clientsMutex = Mutex()
    private val removeChannel = Channel<Client>(Channel.UNLIMITED)

    fun start() {
        if (serverJob != null) return
        serverJob = scope.launch(Dispatchers.IO) {
            launch { processRemovals() }
            while (isActive) {
                try {
                    val clientSocket = serverSocket.accept()
                    val client = Client(clientSocket, scope, { incoming.trySend(it) }, removeChannel)
                    clientsMutex.withLock { activeClients.add(client) }
                    client.start()
                } catch (_: SocketTimeoutException) {
                    // Responsive to cancellation
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
        serverSocket.close()
        incoming.close()
        serverJob?.cancel()
        serverJob = null
        
        clientsMutex.withLock { 
            activeClients.forEach { it.close() }
            activeClients.clear()
        }
    }

    companion object {
        const val ACCEPT_TIMEOUT_MS = 500
        const val BUFFER_CAPACITY = 1024
    }
}
