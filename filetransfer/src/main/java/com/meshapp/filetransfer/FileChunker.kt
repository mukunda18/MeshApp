package com.meshapp.filetransfer

import java.io.File
import java.io.RandomAccessFile

object FileChunker {
    fun splitFile(file: File, chunkSize: Int): List<ByteArray> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        
        val result = mutableListOf<ByteArray>()
        file.inputStream().use { input ->
            val buffer = ByteArray(chunkSize)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                result.add(buffer.copyOfRange(0, bytesRead))
            }
        }
        return result
    }

    /** Helper to write a specific chunk to a file at the correct offset */
    fun writeChunk(file: File, chunkIndex: Int, chunkSize: Int, data: ByteArray) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(chunkIndex.toLong() * chunkSize)
            raf.write(data)
        }
    }
}
