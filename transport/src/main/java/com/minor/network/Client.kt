package com.minor.network

import com.minor.model.HeaderProtocol
import com.minor.model.Packet
import com.minor.model.ParseResult
import com.minor.packetprocessor.HeaderParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.Socket
import java.net.SocketTimeoutException

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
