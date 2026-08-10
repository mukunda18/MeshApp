package com.meshapp.filetransfer

import java.io.File
import java.io.RandomAccessFile

object FileChunker {
    fun splitFile(file: File, chunkSize: Int): List<ByteArray> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        val result = mutableListOf<ByteArray>()
        file.inputStream().use { input ->
            val buffer = ByteArray(chunkSize)
            while (true) {
                var totalRead = 0
                while (totalRead < chunkSize) {
                    val bytesRead = input.read(buffer, totalRead, chunkSize - totalRead)
                    if (bytesRead == -1) break
                    totalRead += bytesRead
                }
                if (totalRead == 0) break
                result.add(buffer.copyOfRange(0, totalRead))
                if (totalRead < chunkSize) break
            }
        }
        return result
    }

    /** Streams a file chunk by chunk without loading the whole file into memory */
    suspend fun streamFile(file: File, chunkSize: Int, onChunk: suspend (Int, ByteArray) -> Unit) {
        if (!file.exists() || file.length() == 0L) return
        
        file.inputStream().use { input ->
            val buffer = ByteArray(chunkSize)
            var chunkIndex = 0
            while (true) {
                var totalRead = 0
                while (totalRead < chunkSize) {
                    val bytesRead = input.read(buffer, totalRead, chunkSize - totalRead)
                    if (bytesRead == -1) break
                    totalRead += bytesRead
                }
                
                if (totalRead == 0) break
                
                // Copy data to ensure caller can handle it asynchronously/suspendably
                val data = buffer.copyOfRange(0, totalRead)
                onChunk(chunkIndex++, data)
                
                if (totalRead < chunkSize) break
            }
        }
    }

    /** Helper to write a specific chunk to a file at the correct offset */
    fun writeChunk(file: File, chunkIndex: Int, chunkSize: Int, data: ByteArray) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(chunkIndex.toLong() * chunkSize)
            raf.write(data)
        }
    }
}
