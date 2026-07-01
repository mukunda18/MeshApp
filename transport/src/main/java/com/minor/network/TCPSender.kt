package com.minor.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.Socket
import kotlin.time.Duration.Companion.milliseconds

class TCPSender(
    private val port: Int,
    private val scope: CoroutineScope
) {
    private val pool = ConnectionPool()

    suspend fun send(
        payload: ByteArray,
        address: String,
        offset: Int = 0,
        length: Int = payload.size
    ) = withContext(Dispatchers.IO) {
        val socket = pool.getOrCreate(address)
        try {
            socket.getOutputStream().write(payload, offset, length)
            socket.getOutputStream().flush()
        } catch (e: Exception) {
            pool.purge(address)
            throw e
        } finally {
            pool.scheduleCleanup(address)
        }
    }

    suspend fun close() = pool.closeAll()

    private inner class ConnectionPool {
        private val connections = mutableMapOf<String, Socket>()
        private val idleJobs = mutableMapOf<String, Job>()
        private val mutex = Mutex()

        suspend fun getOrCreate(address: String): Socket = mutex.withLock {
            idleJobs.remove(address)?.cancel()
            val existing = connections[address]
            if (existing != null && existing.isConnected && !existing.isClosed) {
                existing
            } else {
                Socket(address, port).also {
                    it.tcpNoDelay = true
                    connections[address] = it
                }
            }
        }

        suspend fun purge(address: String) = mutex.withLock {
            idleJobs.remove(address)?.cancel()
            connections.remove(address)?.let { try { it.close() } catch (_: Exception) {} }
        }

        suspend fun scheduleCleanup(address: String) = mutex.withLock {
            if (!connections.containsKey(address)) return@withLock
            idleJobs[address] = scope.launch {
                delay(IDLE_TIMEOUT_MS.milliseconds)
                mutex.withLock {
                    connections.remove(address)?.let { try { it.close() } catch (_: Exception) {} }
                    idleJobs.remove(address)
                }
            }
        }

        suspend fun closeAll() = mutex.withLock {
            idleJobs.values.forEach { it.cancel() }
            idleJobs.clear()
            connections.values.forEach { try { it.close() } catch (_: Exception) {} }
            connections.clear()
        }
    }

    companion object {
        const val IDLE_TIMEOUT_MS = 30_000L
    }
}
