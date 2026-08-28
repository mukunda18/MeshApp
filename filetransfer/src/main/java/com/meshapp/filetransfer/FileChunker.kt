package com.meshapp.filetransfer

import java.io.File
import java.io.RandomAccessFile

object FileChunker {
    /** Streams a file chunk by chunk without loading the whole file into memory.
     * The onChunk callback returns false to abort the stream early
     * (e.g. transfer cancelled or unrecoverable send failures). */
    suspend fun streamFile(
        file: File,
        chunkSize: Int,
        onChunk: suspend (Int, ByteArray) -> Boolean
    ) {
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
                if (!onChunk(chunkIndex++, data)) break

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
