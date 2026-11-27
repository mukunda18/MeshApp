package com.meshapp.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.net.SocketTimeoutException

class ClientReadTest {
    @Test
    fun timeoutDuringPacketReadKeepsReadingUntilPacketIsComplete() {
        val input = object : InputStream() {
            private var readCount = 0

            override fun read(): Int = error("Bulk read expected")

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                return when (readCount++) {
                    0 -> {
                        buffer[offset] = 42
                        1
                    }
                    1 -> throw SocketTimeoutException()
                    else -> {
                        buffer[offset] = 7
                        1
                    }
                }
            }
        }

        val result = readFully(input, ByteArray(2), 0, 2)

        assertEquals(2, result.bytesRead)
        assertTrue(result.complete)
        assertFalse(result.timedOut)
    }

    @Test
    fun timeoutBeforeHeaderBytesIsReportedAsIdleTimeout() {
        val input = object : InputStream() {
            override fun read(): Int = error("Bulk read expected")

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                throw SocketTimeoutException()
            }
        }

        val result = readFully(input, ByteArray(2), 0, 2)

        assertEquals(0, result.bytesRead)
        assertFalse(result.complete)
        assertTrue(result.timedOut)
    }
}
