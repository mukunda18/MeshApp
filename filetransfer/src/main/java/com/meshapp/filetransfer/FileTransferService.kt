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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

class FileTransferService(
    context: Context,
    private val ownNodeId: NodeId,
    private val meshService: MeshService,
    private val messagingService: MessagingService,
    val store: FileTransferStore = InMemoryFileTransferStore(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "FileTransferService"
        private const val CHUNK_SIZE_BYTES = 32 * 1024
        private const val OFFER_RETRY_MAX_ATTEMPTS = 5
        private const val OFFER_RETRY_INTERVAL_MS = 5_000L
        private const val CHUNK_SEND_MAX_ATTEMPTS = 3
        private const val CHUNK_RETRY_BASE_DELAY_MS = 300L
        private const val MAX_CONSECUTIVE_CHUNK_FAILURES = 5
        private const val CHUNK_PACING_DELAY_MS = 10L

        // completion wait now scales with chunk count instead of a flat value
        // base covers small files, per chunk term covers network plus hash time
        // on the receiver, max caps it so a broken peer cannot hang forever
        private const val COMPLETE_WAIT_BASE_MS = 30_000L
        private const val COMPLETE_WAIT_PER_CHUNK_MS = 50L
        private const val COMPLETE_WAIT_MAX_MS = 600_000L

        // local disk write retry for the receiver side
        // a couple of quick retries absorb a transient io hiccup on a chunk
        // instead of silently dropping it and stalling the whole transfer
        private const val CHUNK_WRITE_MAX_ATTEMPTS = 3
        private const val CHUNK_WRITE_RETRY_DELAY_MS = 100L
    }

    // Recreated on every start() so stop/start cycles fully work.
    private var scope: CoroutineScope? = null

    @Volatile
    private var started = false

    private val _events = MutableSharedFlow<FileTransferEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<FileTransferEvent> = _events.asSharedFlow()

    // Serializes every mutation of FileTransferRecord state so signal handlers,
    // the chunk handler, retry jobs and performSend never interleave transitions.
    private val recordMutex = Mutex()

    // Signal/job maps accessed from multiple threads: concurrent structures.
    private val offerRetryJobs = ConcurrentHashMap<MessageId, Job>()
    private val completionSignals = ConcurrentHashMap<MessageId, CompletableDeferred<Boolean>>()

    // one open file handle per incoming transfer instead of open close per chunk
    // one write lock per transfer so writes to the same file stay ordered
    // without holding recordMutex while the disk io happens
    private val receiveFileHandles = ConcurrentHashMap<MessageId, RandomAccessFile>()
    private val receiveWriteLocks = ConcurrentHashMap<MessageId, Mutex>()

    private val incomingDir = File(context.filesDir, "file_transfer/incoming").apply {
        if (!exists()) mkdirs()
    }

    fun start() {
        // Idempotent: previously every call appended ANOTHER pair of collectors,
        // which double-handled signals/chunks and caused duplicated OFFER traffic.
        if (started) {
            MeshLogger.info(TAG, "start() called while already running - ignoring")
            return
        }
        started = true
        val s = CoroutineScope(SupervisorJob() + dispatcher)
        scope = s

        s.launch {
            messagingService.fileSignalsStream.collect { (peerId, signal) ->
                try {
                    handleIncomingSignal(peerId, signal)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    MeshLogger.error(TAG, "Error handling file signal", e.toString())
                }
            }
        }

        s.launch {
            meshService.incomingFileChunkStream.collect { (_, payload) ->
                try {
                    handleIncomingChunk(payload.packet)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    MeshLogger.error(TAG, "Error handling file chunk", e.toString())
                }
            }
        }
        MeshLogger.info(TAG, "FileTransferService STARTED")
    }

    fun stop() {
        started = false
        offerRetryJobs.values.forEach { it.cancel() }
        offerRetryJobs.clear()
        completionSignals.values.forEach { it.complete(false) }
        completionSignals.clear()
        closeAllReceiveHandles()
        scope?.cancel()
        scope = null
        MeshLogger.info(TAG, "FileTransferService STOPPED")
    }

    fun sendFile(destinationNodeId: NodeId, file: File) {
        if (!file.exists()) {
            MeshLogger.error(TAG, "File does not exist: ${file.absolutePath}")
            return
        }
        if (file.length() == 0L) {
            // A zero-byte file produces totalChunks == 0 and can never complete;
            // refuse it explicitly instead of leaving both sides waiting forever.
            MeshLogger.error(TAG, "Refusing to send empty file: ${file.absolutePath}")
            return
        }

        val s = scope
        if (s == null) {
            MeshLogger.error(TAG, "sendFile called before start")
            return
        }

        // hashing a large file is slow, run it off the caller thread
        // instead of blocking whoever called sendFile such as the ui thread
        s.launch {
            val transferId = randomMessageId()
            val chunkSize = CHUNK_SIZE_BYTES
            val totalChunks = ((file.length() + chunkSize - 1) / chunkSize).toInt()
            val checksum = withContext(dispatcher) { FileChecksum.sha256Hex(file) }

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
    }

    private fun sendFileOffer(destinationNodeId: NodeId, record: FileTransferRecord) {
        val metadataBytes = ByteArray(1024)
        val size = FileTransferMetadataProtocol.write(metadataBytes, record.metadata, 0)
        val signal = FileSignal(record.transferId, FileSignalType.OFFER, metadataBytes.copyOfRange(0, size))

        messagingService.sendFileSignal(destinationNodeId, signal)

        val jobScope = scope ?: return
        offerRetryJobs[record.transferId]?.cancel()
        offerRetryJobs[record.transferId] = jobScope.launch {
            var resent = 1
            while (resent < OFFER_RETRY_MAX_ATTEMPTS) {
                delay(OFFER_RETRY_INTERVAL_MS.milliseconds)
                // Stop retrying as soon as any other state has been reached
                // (ACCEPTED, REJECTED, FAILED, TRANSFERRING...).
                if (record.status != FileTransferStatus.OFFER_SENT) return@launch
                resent++
                MeshLogger.info(TAG, "Resending OFFER (attempt ${resent}/${OFFER_RETRY_MAX_ATTEMPTS}) for ${record.transferId}")
                messagingService.sendFileSignal(destinationNodeId, signal)
            }

            // All retries exhausted without any response: surface the failure to the
            // UI instead of leaving the record stuck at OFFER_SENT forever.
            recordMutex.withLock {
                if (record.status == FileTransferStatus.OFFER_SENT) {
                    record.status = FileTransferStatus.FAILED
                    store.save(record)
                }
            }
            if (record.status == FileTransferStatus.FAILED) {
                _events.tryEmit(FileTransferEvent.Failed(record, "No response to file offer"))
                MeshLogger.error(TAG, "Offer failed for ${record.transferId}: no ACCEPT/REJECT received")
            }
            offerRetryJobs.remove(record.transferId)
        }
    }

    private suspend fun handleIncomingSignal(peerId: NodeId, signal: FileSignal) {
        when (signal.type) {
            FileSignalType.OFFER -> handleOffer(peerId, signal)
            FileSignalType.ACCEPT -> handleAccept(signal)
            FileSignalType.REJECT -> handleReject(peerId, signal)
            FileSignalType.CANCEL -> handleCancel(peerId, signal)
            FileSignalType.COMPLETE -> handleComplete(peerId, signal)
        }
    }

    private suspend fun handleOffer(peerId: NodeId, signal: FileSignal) {
        var acceptedRecord: FileTransferRecord? = null

        recordMutex.withLock {
            val existing = store.get(signal.transferId, true)
            if (existing != null && existing.isIncoming) {
                val active = existing.status in setOf(
                    FileTransferStatus.OFFER_RECEIVED,
                    FileTransferStatus.ACCEPTED,
                    FileTransferStatus.TRANSFERRING
                )
                if (active || existing.status == FileTransferStatus.COMPLETED) {
                    // Duplicate/retried OFFER for a transfer we already have: NEVER reset
                    // an active record here (that used to wipe receivedChunks mid-transfer).
                    MeshLogger.info(TAG, "Duplicate OFFER for active/completed transfer ${signal.transferId} - ignoring")
                    if (active) acceptedRecord = existing
                    return@withLock
                }
            }

            val metadata = try {
                FileTransferMetadataProtocol.read(signal.payload).value
            } catch (e: Exception) {
                MeshLogger.error(TAG, "Malformed offer payload", e.toString())
                return@withLock
            }

            if (metadata.totalChunks <= 0 || metadata.chunkSize <= 0) {
                MeshLogger.error(TAG, "Rejected malformed metadata (chunks=${metadata.totalChunks})")
                return@withLock
            }

            val outputFile = File(incomingDir, "${signal.transferId}-${metadata.filename}")
            // Remove any stale partial output from an earlier incarnation
            if (outputFile.exists()) outputFile.delete()

            val record = FileTransferRecord(
                transferId = signal.transferId,
                metadata = metadata,
                status = FileTransferStatus.OFFER_RECEIVED,
                peerNodeId = peerId,
                isIncoming = true,
                outputPath = outputFile.absolutePath
            )
            store.save(record)
            _events.emit(FileTransferEvent.OfferReceived(record))

            // Auto-accept architecture: prepare for chunks immediately
            record.status = FileTransferStatus.ACCEPTED
            store.save(record)
            acceptedRecord = record
        }

        acceptedRecord?.let { rec ->
            sendSignal(rec.peerNodeId, rec.transferId, FileSignalType.ACCEPT)
        }
    }

    private suspend fun handleAccept(signal: FileSignal) {
        recordMutex.withLock {
            val record = store.get(signal.transferId, false) ?: return@withLock
            if (record.status != FileTransferStatus.OFFER_SENT) return@withLock

            record.status = FileTransferStatus.TRANSFERRING
            store.save(record)
        }

        val record = store.get(signal.transferId, false) ?: return
        if (record.status != FileTransferStatus.TRANSFERRING) return

        offerRetryJobs.remove(signal.transferId)?.cancel()

        // Register the completion signal BEFORE the first chunk leaves so a very fast
        // COMPLETE reply can never be missed.
        completionSignals.getOrPut(signal.transferId) { CompletableDeferred() }

        val s = scope ?: return
        s.launch { performSend(record) }
    }

    private suspend fun performSend(record: FileTransferRecord) {
        val sourceFile = File(record.sourcePath ?: return)
        val peerId = record.peerNodeId
        var consecutiveFailures = 0
        var aborted = false
        var streamedChunks = 0

        MeshLogger.info(TAG, "Starting file transfer ${record.transferId} (${record.metadata.totalChunks} chunks) to $peerId")

        try {
            FileChunker.streamFile(sourceFile, record.metadata.chunkSize) { index, data ->
                if (record.status != FileTransferStatus.TRANSFERRING) {
                    aborted = true // cancelled/rejected/failed elsewhere
                    return@streamFile false
                }

                var delivered = false
                repeat(CHUNK_SEND_MAX_ATTEMPTS) { attempt ->
                    val payload = Payload.FileChunk(
                        FileChunkPacket(record.transferId, index, record.metadata.totalChunks, data)
                    )
                    delivered = meshService.sendFileChunk(peerId, payload)
                    if (!delivered) {
                        delay(CHUNK_RETRY_BASE_DELAY_MS * (attempt + 1))
                    }
                }

                if (!delivered) {
                    consecutiveFailures++
                    MeshLogger.error(TAG, "Chunk ${index} failed after ${CHUNK_SEND_MAX_ATTEMPTS} attempts (consecutive=${consecutiveFailures})")
                    if (consecutiveFailures >= MAX_CONSECUTIVE_CHUNK_FAILURES) {
                        failSend(record, "Route lost or repeated chunk delivery failures")
                        aborted = true
                        return@streamFile false
                    }
                    // Keep going; later chunks may still succeed once route returns.
                    return@streamFile true
                }

                consecutiveFailures = 0
                streamedChunks++

                recordMutex.withLock {
                    record.bytesTransferred += data.size
                    store.save(record)
                }
                _events.tryEmit(FileTransferEvent.ProgressUpdated(record))

                delay(CHUNK_PACING_DELAY_MS.milliseconds) // gentle pacing keeps sockets responsive
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MeshLogger.error(TAG, "Error streaming file ${record.transferId}", e.toString())
            failSend(record, "Local file error: ${e.message}")
            return
        }

        if (aborted) return

        if (streamedChunks < record.metadata.totalChunks) {
            failSend(record, "Only ${streamedChunks}/${record.metadata.totalChunks} chunks could be queued")
            return
        }

        MeshLogger.info(TAG, "All ${streamedChunks} chunks sent for ${record.transferId}, awaiting receiver confirmation")

        // wait longer for bigger transfers, capped so a dead peer still fails eventually
        val totalChunks = record.metadata.totalChunks.toLong()
        val timeoutMs = (COMPLETE_WAIT_BASE_MS + COMPLETE_WAIT_PER_CHUNK_MS * totalChunks)
            .coerceAtMost(COMPLETE_WAIT_MAX_MS)

        val confirmed = completionSignals[record.transferId]
        val completeOk = if (confirmed != null) {
            withTimeoutOrNull(timeoutMs.milliseconds) { confirmed.await() } ?: false
        } else {
            false
        }

        if (!completeOk && record.status == FileTransferStatus.TRANSFERRING) {
            failSend(record, "Receiver did not confirm completion within ${timeoutMs}ms")
        }
        completionSignals.remove(record.transferId)
    }

    private suspend fun failSend(record: FileTransferRecord, reason: String) {
        recordMutex.withLock {
            if (record.status == FileTransferStatus.COMPLETED) return@withLock
            record.status = FileTransferStatus.FAILED
            store.save(record)
        }
        completionSignals.remove(record.transferId)?.complete(false)
        offerRetryJobs.remove(record.transferId)?.cancel()
        _events.emit(FileTransferEvent.Failed(record, reason))
        MeshLogger.error(TAG, "Transfer ${record.transferId} failed: ${reason}")
        // Tell the peer to drop its side of the transfer as well.
        sendSignal(record.peerNodeId, record.transferId, FileSignalType.CANCEL)
    }

    private suspend fun failReceive(record: FileTransferRecord, reason: String) {
        recordMutex.withLock {
            if (record.status in setOf(
                    FileTransferStatus.COMPLETED,
                    FileTransferStatus.FAILED,
                    FileTransferStatus.CANCELLED,
                    FileTransferStatus.REJECTED
                )
            ) return@withLock
            record.status = FileTransferStatus.FAILED
            store.save(record)
        }
        closeReceiveHandle(record.transferId)
        _events.emit(FileTransferEvent.Failed(record, reason))
        MeshLogger.error(TAG, "Transfer ${record.transferId} failed: ${reason}")
        sendSignal(record.peerNodeId, record.transferId, FileSignalType.CANCEL)
    }

    private suspend fun handleIncomingChunk(packet: FileChunkPacket) {
        var target: FileTransferRecord? = null
        var shouldWrite = false
        var outputPath: String? = null
        var chunkSize = 0

        // short critical section only for validation and status transition
        // the actual disk write happens after this lock is released
        recordMutex.withLock {
            val r = store.get(packet.transferId, true) ?: return@withLock
            target = r

            if (packet.chunkIndex < 0 || packet.chunkIndex >= r.metadata.totalChunks) {
                MeshLogger.error(TAG, "Chunk index ${packet.chunkIndex} out of bounds for ${r.transferId}")
                return@withLock
            }

            // Duplicates (retries) are idempotent writes at the same offset; do not
            // recount bytes or re-add to receivedChunks.
            if (packet.chunkIndex in r.receivedChunks) return@withLock

            when (r.status) {
                FileTransferStatus.OFFER_RECEIVED, FileTransferStatus.ACCEPTED -> r.status = FileTransferStatus.TRANSFERRING
                FileTransferStatus.TRANSFERRING -> Unit
                else -> return@withLock // terminal states: ignore stray chunks
            }

            outputPath = r.outputPath
            chunkSize = r.metadata.chunkSize
            shouldWrite = true
        }

        if (!shouldWrite) return
        val r = target ?: return
        val path = outputPath ?: return

        // write is serialized per transfer only, not against every other
        // record in the store, and keeps one file handle open for the
        // whole transfer instead of open and close on every chunk
        val writeOk = writeChunkWithRetry(r.transferId, path, packet.chunkIndex, chunkSize, packet.data)

        if (!writeOk) {
            failReceive(r, "Local write error on chunk ${packet.chunkIndex}")
            return
        }

        var completed = false
        recordMutex.withLock {
            r.receivedChunks.add(packet.chunkIndex)
            r.bytesTransferred += packet.data.size
            store.save(r)
            if (r.receivedChunks.size >= r.metadata.totalChunks) completed = true
        }
        _events.emit(FileTransferEvent.ProgressUpdated(r))

        if (completed) {
            closeReceiveHandle(r.transferId)
            verifyAndComplete(r)
        }
    }

    private suspend fun writeChunkWithRetry(
        transferId: MessageId,
        outputPath: String,
        chunkIndex: Int,
        chunkSize: Int,
        data: ByteArray
    ): Boolean {
        return try {
            writeLockFor(transferId).withLock {
                withContext(Dispatchers.IO) {
                    val raf = receiveFileHandles.getOrPut(transferId) {
                        RandomAccessFile(File(outputPath), "rw")
                    }
                    var lastError: Exception? = null
                    var attempt = 0
                    while (attempt < CHUNK_WRITE_MAX_ATTEMPTS) {
                        try {
                            FileChunker.writeChunkTo(raf, chunkIndex, chunkSize, data)
                            lastError = null
                            break
                        } catch (e: Exception) {
                            lastError = e
                            attempt++
                            if (attempt < CHUNK_WRITE_MAX_ATTEMPTS) delay(CHUNK_WRITE_RETRY_DELAY_MS)
                        }
                    }
                    lastError?.let { throw it }
                }
            }
            true
        } catch (e: Exception) {
            MeshLogger.error(TAG, "Failed to write chunk ${chunkIndex} for ${transferId} after retries", e.toString())
            false
        }
    }

    private fun writeLockFor(transferId: MessageId): Mutex =
        receiveWriteLocks.getOrPut(transferId) { Mutex() }

    private fun closeReceiveHandle(transferId: MessageId) {
        receiveFileHandles.remove(transferId)?.let {
            try {
                it.close()
            } catch (e: Exception) {
                MeshLogger.error(TAG, "Error closing handle for ${transferId}", e.toString())
            }
        }
        receiveWriteLocks.remove(transferId)
    }

    private fun closeAllReceiveHandles() {
        receiveFileHandles.keys.toList().forEach { closeReceiveHandle(it) }
    }

    private suspend fun verifyAndComplete(record: FileTransferRecord) {
        val file = File(record.outputPath ?: return)
        val actualChecksum = withContext(Dispatchers.IO) { FileChecksum.sha256Hex(file) }

        if (actualChecksum.equals(record.metadata.checksum, ignoreCase = true)) {
            var okToComplete = false
            recordMutex.withLock {
                if (record.status == FileTransferStatus.TRANSFERRING || record.status == FileTransferStatus.ACCEPTED) {
                    record.status = FileTransferStatus.COMPLETED
                    store.save(record)
                    okToComplete = true
                }
            }
            if (okToComplete) {
                _events.emit(FileTransferEvent.Completed(record))
                MeshLogger.info(TAG, "Received file ${record.metadata.filename} verified OK")
                sendSignal(record.peerNodeId, record.transferId, FileSignalType.COMPLETE)
            }
        } else {
            MeshLogger.error(TAG, "Checksum mismatch for ${record.transferId}")
            recordMutex.withLock {
                record.status = FileTransferStatus.FAILED
                store.save(record)
            }
            _events.emit(FileTransferEvent.Failed(record, "Checksum mismatch"))
            // Notify the sender so its side fails too instead of waiting for COMPLETE.
            sendSignal(record.peerNodeId, record.transferId, FileSignalType.CANCEL)
        }
    }

    private suspend fun handleComplete(peerId: NodeId, signal: FileSignal) {
        val record = store.get(signal.transferId, false) ?: return
        if (!record.peerNodeId.bytes.contentEquals(peerId.bytes)) return

        when (record.status) {
            FileTransferStatus.COMPLETED -> {
                MeshLogger.info(TAG, "Peer $peerId confirmed completion of ${signal.transferId}")
            }
            else -> {
                recordMutex.withLock {
                    record.status = FileTransferStatus.COMPLETED
                    store.save(record)
                }
                _events.emit(FileTransferEvent.Completed(record))
            }
        }
        completionSignals.remove(signal.transferId)?.complete(true)
        offerRetryJobs.remove(signal.transferId)?.cancel()
    }

    private suspend fun handleReject(peerId: NodeId, signal: FileSignal) {
        val record = store.get(signal.transferId, false) ?: return
        if (!record.peerNodeId.bytes.contentEquals(peerId.bytes)) return

        recordMutex.withLock {
            record.status = FileTransferStatus.REJECTED
            store.save(record)
        }
        _events.emit(FileTransferEvent.Failed(record, "Rejected by peer"))
        offerRetryJobs.remove(signal.transferId)?.cancel()
        completionSignals.remove(signal.transferId)?.complete(false)
    }

    private suspend fun handleCancel(peerId: NodeId, signal: FileSignal) {
        // Peer-initiated cancel: may target an outgoing transfer (receiver gave up /
        // mismatch) or an incoming one (sender aborted).
        val record = store.get(signal.transferId, true) ?: store.get(signal.transferId, false) ?: return
        if (!record.peerNodeId.bytes.contentEquals(peerId.bytes)) return

        var changed = false
        recordMutex.withLock {
            if (record.status in setOf(FileTransferStatus.COMPLETED, FileTransferStatus.CANCELLED, FileTransferStatus.FAILED, FileTransferStatus.REJECTED)) return@withLock
            record.status = FileTransferStatus.CANCELLED
            store.save(record)
            changed = true
        }
        closeReceiveHandle(signal.transferId)
        if (changed) {
            _events.emit(FileTransferEvent.Cancelled(record))
        }
        offerRetryJobs.remove(signal.transferId)?.cancel()
        completionSignals.remove(signal.transferId)?.complete(false)
    }

    private fun sendSignal(to: NodeId, transferId: MessageId, type: Int) {
        messagingService.sendFileSignal(to, FileSignal(transferId, type, ByteArray(0)))
    }
}