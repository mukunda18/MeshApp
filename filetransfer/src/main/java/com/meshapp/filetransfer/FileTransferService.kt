package com.meshapp.filetransfer

import android.content.Context
import com.meshapp.logger.MeshLogger
import com.meshapp.messaging.MessagingService
import com.meshapp.meshcontrol.MeshService
import com.meshapp.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

class FileTransferService(
    private val context: Context,
    private val ownNodeId: NodeId,
    private val meshService: MeshService,
    private val messagingService: MessagingService,
    val store: FileTransferStore = InMemoryFileTransferStore(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    
    private val _events = MutableSharedFlow<FileTransferEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<FileTransferEvent> = _events.asSharedFlow()
    
    private val offerRetryJobs = mutableMapOf<MessageId, Job>()
    
    private val incomingDir = File(context.filesDir, "file_transfer/incoming").apply {
        if (!exists()) mkdirs()
    }

    fun start() {
        scope.launch {
            messagingService.fileSignalsStream.collect { (peerId, signal) ->
                handleIncomingSignal(peerId, signal)
            }
        }
        
        scope.launch {
            meshService.incomingFileChunkStream.collect { (peerId, payload) ->
                handleIncomingChunk(peerId, payload.packet)
            }
        }
    }

    fun stop() {
        scope.cancel()
        offerRetryJobs.values.forEach { it.cancel() }
        offerRetryJobs.clear()
    }

    fun sendFile(destinationNodeId: NodeId, file: File) {
        if (!file.exists()) {
            MeshLogger.error("FileTransferService", "File does not exist: ${file.absolutePath}")
            return
        }

        val transferId = randomMessageId()
        val chunkSize = 32 * 1024
        val totalChunks = ((file.length() + chunkSize - 1) / chunkSize).toInt()
        val checksum = FileChecksum.sha256Hex(file)
        
        val metadata = FileTransferMetadata(
            filename = file.name,
            size = file.length(),
            checksum = checksum,
            chunkSize = chunkSize,
            totalChunks = totalChunks,
            senderNodeId = ownNodeId,
            createdAt = System.currentTimeMillis()
        )

        val record = FileTransferRecord(
            transferId = transferId,
            metadata = metadata,
            status = FileTransferStatus.OFFER_SENT,
            peerNodeId = destinationNodeId,
            isIncoming = false,
            sourcePath = file.absolutePath
        )
        
        store.save(record)
        _events.tryEmit(FileTransferEvent.OfferSent(record))
        
        sendFileOffer(destinationNodeId, record)
    }

    private fun sendFileOffer(destinationNodeId: NodeId, record: FileTransferRecord) {
        val metadataBytes = ByteArray(1024) // Buffer for metadata
        val size = FileTransferMetadataProtocol.write(metadataBytes, record.metadata, 0)
        val signal = FileSignal(record.transferId, FileSignalType.OFFER, metadataBytes.copyOfRange(0, size))
        
        messagingService.sendFileSignal(destinationNodeId, signal)
        
        // Start retry job
        offerRetryJobs[record.transferId] = scope.launch {
            var attempts = 0
            while (attempts < 5 && (record.status == FileTransferStatus.OFFER_SENT)) {
                delay(5000)
                if (record.status == FileTransferStatus.OFFER_SENT) {
                    messagingService.sendFileSignal(destinationNodeId, signal)
                    attempts++
                    MeshLogger.info("FileTransferService", "Retrying offer for ${record.transferId}, attempt $attempts")
                }
            }
            if (record.status == FileTransferStatus.OFFER_SENT) {
                record.status = FileTransferStatus.FAILED
                store.save(record)
                _events.emit(FileTransferEvent.Failed(record, "Offer timeout"))
            }
        }
    }

    private suspend fun handleIncomingSignal(peerId: NodeId, signal: FileSignal) {
        when (signal.type) {
            FileSignalType.OFFER -> handleOffer(peerId, signal)
            FileSignalType.ACCEPT -> handleAccept(peerId, signal)
            FileSignalType.REJECT -> handleReject(peerId, signal)
            FileSignalType.CANCEL -> handleCancel(peerId, signal)
            FileSignalType.COMPLETE -> handleComplete(peerId, signal)
        }
    }

    private suspend fun handleOffer(peerId: NodeId, signal: FileSignal) {
        val metadata = FileTransferMetadataProtocol.read(signal.payload, 0).value
        val record = FileTransferRecord(
            transferId = signal.transferId,
            metadata = metadata,
            status = FileTransferStatus.OFFER_RECEIVED,
            peerNodeId = peerId,
            isIncoming = true,
            outputPath = File(incomingDir, "${signal.transferId}-${metadata.filename}").absolutePath
        )
        store.save(record)
        _events.emit(FileTransferEvent.OfferReceived(record))
        
        // Auto-accept as per architecture
        acceptTransfer(record)
    }

    private fun acceptTransfer(record: FileTransferRecord) {
        record.status = FileTransferStatus.ACCEPTED
        store.save(record)
        
        val signal = FileSignal(record.transferId, FileSignalType.ACCEPT, ByteArray(0))
        messagingService.sendFileSignal(record.peerNodeId, signal)
    }

    private suspend fun handleAccept(peerId: NodeId, signal: FileSignal) {
        val record = store.get(signal.transferId, false) ?: return
        if (record.status != FileTransferStatus.OFFER_SENT) return
        
        offerRetryJobs[signal.transferId]?.cancel()
        offerRetryJobs.remove(signal.transferId)
        
        record.status = FileTransferStatus.TRANSFERRING
        store.save(record)
        
        scope.launch {
            performSend(record)
        }
    }

    private suspend fun performSend(record: FileTransferRecord) {
        val file = File(record.sourcePath ?: return)
        
        FileChunker.streamFile(file, record.metadata.chunkSize) { index, data ->
            if (record.status != FileTransferStatus.TRANSFERRING) return@streamFile
            
            val packet = FileChunkPacket(record.transferId, index, record.metadata.totalChunks, data)
            meshService.sendFileChunk(record.peerNodeId, Payload.FileChunk(packet))
            
            record.bytesTransferred += data.size
            store.save(record)
            _events.emit(FileTransferEvent.ProgressUpdated(record))
            
            // Small delay to avoid saturating the network
            delay(10)
        }
        
        if (record.status == FileTransferStatus.TRANSFERRING) {
            record.status = FileTransferStatus.COMPLETED
            store.save(record)
            _events.emit(FileTransferEvent.Completed(record))
        }
    }

    private suspend fun handleIncomingChunk(peerId: NodeId, packet: FileChunkPacket) {
        val record = store.get(packet.transferId, true) ?: return
        if (record.status == FileTransferStatus.OFFER_RECEIVED || record.status == FileTransferStatus.ACCEPTED) {
            record.status = FileTransferStatus.TRANSFERRING
        }
        
        if (record.status != FileTransferStatus.TRANSFERRING) return
        
        val file = File(record.outputPath ?: return)
        
        // Reassembly logic: Use RandomAccessFile to write at correct offset
        withContext(Dispatchers.IO) {
            FileChunker.writeChunk(file, packet.chunkIndex, record.metadata.chunkSize, packet.data)
        }
        
        if (record.receivedChunks.add(packet.chunkIndex)) {
            record.bytesTransferred += packet.data.size
            store.save(record)
            _events.emit(FileTransferEvent.ProgressUpdated(record))
        }
        
        if (record.receivedChunks.size >= record.metadata.totalChunks) {
            verifyAndComplete(record)
        }
    }

    private suspend fun verifyAndComplete(record: FileTransferRecord) {
        val file = File(record.outputPath ?: return)
        val actualChecksum = FileChecksum.sha256Hex(file)
        
        if (actualChecksum == record.metadata.checksum) {
            record.status = FileTransferStatus.COMPLETED
            store.save(record)
            _events.emit(FileTransferEvent.Completed(record))
            
            // Send COMPLETE signal back to sender
            val signal = FileSignal(record.transferId, FileSignalType.COMPLETE, ByteArray(0))
            messagingService.sendFileSignal(record.peerNodeId, signal)
        } else {
            MeshLogger.error("FileTransferService", "Checksum mismatch for ${record.transferId}")
            record.status = FileTransferStatus.FAILED
            store.save(record)
            _events.emit(FileTransferEvent.Failed(record, "Checksum mismatch"))
        }
    }

    private suspend fun handleComplete(peerId: NodeId, signal: FileSignal) {
        val record = store.get(signal.transferId, false) ?: return
        if (record.status == FileTransferStatus.COMPLETED) {
            // Already marked locally, but this confirms receiver finished too
            MeshLogger.info("FileTransferService", "Peer $peerId confirmed completion of ${signal.transferId}")
        } else {
            record.status = FileTransferStatus.COMPLETED
            store.save(record)
            _events.emit(FileTransferEvent.Completed(record))
        }
    }

    private suspend fun handleReject(peerId: NodeId, signal: FileSignal) {
        val record = store.get(signal.transferId, false) ?: return
        record.status = FileTransferStatus.REJECTED
        store.save(record)
        _events.emit(FileTransferEvent.Failed(record, "Rejected by peer"))
        offerRetryJobs[signal.transferId]?.cancel()
        offerRetryJobs.remove(signal.transferId)
    }

    private suspend fun handleCancel(peerId: NodeId, signal: FileSignal) {
        // Try to find as incoming first, then outgoing
        val record = store.get(signal.transferId, true) ?: store.get(signal.transferId, false)
        if (record == null || !record.peerNodeId.bytes.contentEquals(peerId.bytes)) return
        
        record.status = FileTransferStatus.CANCELLED
        store.save(record)
        _events.emit(FileTransferEvent.Cancelled(record))
        offerRetryJobs[signal.transferId]?.cancel()
        offerRetryJobs.remove(signal.transferId)
    }
}
