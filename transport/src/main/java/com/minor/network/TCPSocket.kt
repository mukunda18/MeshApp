package com.minor.network

import com.minor.packetprocessor.HeaderParser
import com.minor.model.Packet
import com.minor.model.HeaderProtocol
import com.minor.model.ParseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.Socket
import java.net.SocketTimeoutException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.ServerSocket

class Client(
    private val socket: Socket,
    private val scope: CoroutineScope,
    private val onMessage: (Packet) -> Unit,
    private val removeChannel: SendChannel<Client>
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            socket.soTimeout = READ_TIMEOUT_MS
            val buffer = ByteArray(MAX_PACKET_SIZE)
            var offset = 0
            val input = socket.getInputStream()
            while (isActive) {
                if (offset < HeaderProtocol.HEADER_SIZE) {
                    val result = readFully(input, buffer, offset, HeaderProtocol.HEADER_SIZE - offset)
                    offset += result.second
                    if (!result.first) break
                } else {
                    val r = HeaderParser.parse(buffer)
                    if (r is ParseResult.Failure) break
                    val header = (r as ParseResult.Success).value
                    val payloadLength = header.payloadLength
                    if (payloadLength + HeaderProtocol.HEADER_SIZE > MAX_PACKET_SIZE) break
                    val result = readFully(input, buffer, offset, payloadLength + HeaderProtocol.HEADER_SIZE - offset)
                    offset += result.second
                    if (!result.first) break
                    onMessage(Packet(
                        header = header,
                        payload = buffer.copyOfRange(HeaderProtocol.HEADER_SIZE, offset)
                    ))
                    offset = 0
                }
            }
            removeChannel.trySend(this@Client)
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        job?.cancel()
        job = null
        socket.close()
    }

    private fun readFully(input: InputStream, buffer: ByteArray, off: Int, len: Int): Pair<Boolean, Int> {
        if (len == 0) return Pair(true, 0)
        var total = 0
        while (total < len) {
            try {
                val read = input.read(buffer, off + total, len - total)
                if (read == -1) return Pair(false, total)
                total += read
            } catch (_: SocketTimeoutException) {
                return Pair(true, total)
            }
        }
        return Pair(true, total)
    }

    companion object {
        const val READ_TIMEOUT_MS = 500
        const val MAX_PACKET_SIZE = 64 * 1024
    }
}

class TCPSocket(
    private val port: Int,
    private val scope: CoroutineScope
) {
    val channel = Channel<Packet>(
        capacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val serverSocket = ServerSocket().apply {
        reuseAddress = true
        soTimeout = ACCEPT_TIMEOUT_MS
        bind(InetSocketAddress(port))
    }

    private var job: Job? = null
    private val clients = mutableListOf<Client>()
    private val mutex = Mutex()
    private val removeChannel = Channel<Client>(Channel.UNLIMITED)

    suspend fun send(payload: ByteArray, address: String) = withContext(Dispatchers.IO) {
        Socket(address, port).use { it.getOutputStream().write(payload) }
    }

    fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val clientSocket = serverSocket.accept()
                    val client = Client(clientSocket, scope, { channel.trySend(it) }, removeChannel)
                    mutex.withLock { clients.add(client) }
                    client.start()
                } catch (_: SocketTimeoutException) {
                    // Loop back to re-check isActive so cancellation is responsive.
                }
                while (true) {
                    val client = removeChannel.tryReceive().getOrNull() ?: break
                    client.close()
                    mutex.withLock { clients.remove(client) }
                }
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        serverSocket.close()
        channel.close()
        job?.cancel()
        job = null
        val snapshot = mutex.withLock { clients.toList().also { clients.clear() } }
        snapshot.forEach { it.close() }
    }

    companion object {
        const val ACCEPT_TIMEOUT_MS = 500
        const val BUFFER_CAPACITY = 1024
    }
}