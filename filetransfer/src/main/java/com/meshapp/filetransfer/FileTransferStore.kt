package com.meshapp.filetransfer

import com.meshapp.model.MessageId
import java.util.concurrent.ConcurrentHashMap

interface FileTransferStore {
    fun save(record: FileTransferRecord)
    fun get(transferId: MessageId, isIncoming: Boolean): FileTransferRecord?
    fun list(): List<FileTransferRecord>
    fun delete(transferId: MessageId, isIncoming: Boolean)
}

class InMemoryFileTransferStore : FileTransferStore {
    private val transfers = ConcurrentHashMap<String, FileTransferRecord>()

    private fun key(transferId: MessageId, isIncoming: Boolean) = 
        "${transferId}-${if (isIncoming) "in" else "out"}"

    override fun save(record: FileTransferRecord) {
        transfers[key(record.transferId, record.isIncoming)] = record
    }

    override fun get(transferId: MessageId, isIncoming: Boolean): FileTransferRecord? {
        return transfers[key(transferId, isIncoming)]
    }

    override fun list(): List<FileTransferRecord> = transfers.values.toList().sortedByDescending { it.createdAt }

    override fun delete(transferId: MessageId, isIncoming: Boolean) {
        transfers.remove(key(transferId, isIncoming))
    }
}
