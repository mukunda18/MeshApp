package com.meshapp.network

import android.util.Log
import com.meshapp.model.HeaderProtocol
import com.meshapp.model.Packet
import com.meshapp.model.Envelope
import com.meshapp.model.ParseResult
import com.meshapp.packetprocessor.HeaderParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException

class Client(
    private val socket: Socket,
    private val scope: CoroutineScope,
    private val maxPacketSize: Int,
    private val readTimeoutMs: Int,
    private val onMessage: (Envelope) -> Unit,
    private val removeChannel: SendChannel<Client>
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            socket.soTimeout = readTimeoutMs
            val buffer = ByteArray(maxPacketSize)
            var offset = 0
            val input = try {
                socket.getInputStream()
            } catch (_: Exception) {
                removeChannel.trySend(this@Client)
                return@launch
            }
            try {
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
                        
                        if (payloadLength + HeaderProtocol.HEADER_SIZE > maxPacketSize) break
                        
                        val result = readFully(input, buffer, offset, payloadLength + HeaderProtocol.HEADER_SIZE - offset)
                        offset += result.second
                        if (!result.first) break
                        
                        onMessage(Envelope(
                            packet = Packet(
                                header = header,
                                payload = buffer.copyOfRange(HeaderProtocol.HEADER_SIZE, offset)
                            ),
                            remoteAddress = socket.remoteSocketAddress as InetSocketAddress
                        ))
                        offset = 0
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (isActive && (e !is SocketException || !socket.isClosed)) {
                    Log.e("Client", "Error in client receive loop", e)
                }
            } finally {
                removeChannel.trySend(this@Client)
            }
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
}

