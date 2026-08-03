package com.meshapp.filetransfer

import com.meshapp.model.FileTransferMetadata
import com.meshapp.model.MessageId
import com.meshapp.model.NodeId

enum class FileTransferStatus {
    IDLE,
    OFFER_SENT,
    OFFER_RECEIVED,
    ACCEPTED,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REJECTED
}

data class FileTransferRecord(
    val transferId: MessageId,
    val metadata: FileTransferMetadata,
    var status: FileTransferStatus,
    val peerNodeId: NodeId,
    val isIncoming: Boolean,
    val sourcePath: String? = null,
    val outputPath: String? = null,
    var bytesTransferred: Long = 0,
    val isLoopback: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val receivedChunks: MutableSet<Int> = mutableSetOf()
) {
    val progress: Float
        get() = if (metadata.size > 0) bytesTransferred.toFloat() / metadata.size else 0f
}

sealed class FileTransferEvent {
    data class OfferSent(val record: FileTransferRecord) : FileTransferEvent()
    data class OfferReceived(val record: FileTransferRecord) : FileTransferEvent()
    data class ProgressUpdated(val record: FileTransferRecord) : FileTransferEvent()
    data class Completed(val record: FileTransferRecord) : FileTransferEvent()
    data class Failed(val record: FileTransferRecord, val reason: String) : FileTransferEvent()
    data class Cancelled(val record: FileTransferRecord) : FileTransferEvent()
}
