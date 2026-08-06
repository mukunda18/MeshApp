package com.meshapp.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshapp.filetransfer.FileTransferEvent
import com.meshapp.filetransfer.FileTransferRecord
import com.meshapp.filetransfer.FileTransferService
import com.meshapp.filetransfer.FileTransferStatus
import com.meshapp.meshcontrol.MeshService
import com.meshapp.meshcontrol.PeerState
import com.meshapp.meshcontrol.PeerStatus
import com.meshapp.messaging.Message
import com.meshapp.messaging.MessageDeliveryStatus
import com.meshapp.messaging.MessagingService
import com.meshapp.model.NodeId
import com.meshapp.voice.VoiceCallManager
import com.meshapp.routing.PeerEvent
import com.meshapp.ui.state.ConversationMessageUiState
import com.meshapp.ui.state.ConversationUiState
import com.meshapp.ui.state.FileTransferUiState
import com.meshapp.ui.state.NodeCardState
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConversationViewModel(
    private val ownNodeId: NodeId,
    private val messagingService: MessagingService,
    private val meshService: MeshService,
    private val voiceCallManager: VoiceCallManager,
    private val fileTransferService: FileTransferService
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConversationUiState(node = NodeCardState("", "", false, "")))
    private val _peerMap = MutableStateFlow<Map<String, PeerState>>(emptyMap())
    private val _routeNodeIds = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var activeNodeId: NodeId? = null

    init {
        collectPeerEvents()
        startRouteRefreshLoop()
        observeConversationUpdates()
        observeFileTransfers()
    }

    fun initialize(nodeId: String) {
        if (nodeId.isBlank()) return
        val parsedNodeId = parseNodeId(nodeId) ?: return
        if (activeNodeId?.toString() == parsedNodeId.toString()) return
        activeNodeId = parsedNodeId
        refreshUI(parsedNodeId)
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val destination = activeNodeId ?: return
        try {
            messagingService.send(destinationNodeID = destination, plaintext = text.trim())
        } catch (e: Exception) {
            android.util.Log.e("ConversationViewModel", "Failed to send message", e)
        }
    }

    fun attachFile(context: Context, uri: Uri) {
        val destination = activeNodeId ?: return
        viewModelScope.launch {
            try {
                val fileName = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
                val outgoingDir = File(context.cacheDir, "file_transfer/outgoing").apply {
                    if (!exists()) mkdirs()
                }
                val tempFile = File(outgoingDir, fileName)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                fileTransferService.sendFile(destination, tempFile)
            } catch (e: Exception) {
                android.util.Log.e("ConversationViewModel", "Failed to attach file", e)
            }
        }
    }

    fun openFile(context: Context, fileUiState: FileTransferUiState) {
        val path = fileUiState.localPath ?: return
        val file = File(path)
        if (!file.exists()) return

        try {
            val uri = FileProvider.getUriForFile(context, "com.minor.meshapp.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("ConversationViewModel", "Failed to open file", e)
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) name = cursor.getString(index)
                }
            }
        }
        return name ?: uri.path?.let { File(it).name }
    }

    private fun observeFileTransfers() {
        viewModelScope.launch {
            fileTransferService.events.collect { event ->
                val transferRecord = when (event) {
                    is FileTransferEvent.OfferSent -> event.record
                    is FileTransferEvent.OfferReceived -> event.record
                    is FileTransferEvent.ProgressUpdated -> event.record
                    is FileTransferEvent.Completed -> event.record
                    is FileTransferEvent.Failed -> event.record
                    is FileTransferEvent.Cancelled -> event.record
                }
                
                val destination = activeNodeId ?: return@collect
                if (transferRecord.peerNodeId.toString() != destination.toString()) return@collect
                
                refreshUI(destination)
            }
        }
    }

    private fun observeConversationUpdates() {
        viewModelScope.launch {
            combine(
                messagingService.messagesStream,
                _peerMap,
                _routeNodeIds
            ) { _, _, _ ->
                activeNodeId
            }.collect { destination ->
                destination?.let { refreshUI(it) }
            }
        }
    }

    private fun refreshUI(destination: NodeId) {
        messagingService.markConversationAsRead(destination)

        val peer = _peerMap.value[destination.toString()]
        val isInRouteTable = destination.toString() in _routeNodeIds.value
        val currentNode = _uiState.value.node
        val displayName = peer?.name?.takeIf { it.isNotBlank() } ?: shortId(destination.toString())
        
        val textMessages = messagingService.getHistory(destination)
        val fileTransfers = fileTransferService.store.list().filter { 
            it.peerNodeId.toString() == destination.toString() 
        }

        // Merge and sort
        val allMessages = (textMessages.map { it.toUiMessage(it.senderNodeId.toString() == ownNodeId.toString()) } +
            fileTransfers.map { it.toUiMessage() })
            .sortedBy { it.rawTimestamp } // Corrected sorting
        
        _uiState.update { state ->
            state.copy(
                node = currentNode.copy(
                    id = destination.toString(),
                    name = displayName,
                    isOnline = (peer?.status == PeerStatus.ACTIVE) || isInRouteTable,
                    avatarInitials = initialsFrom(displayName)
                ),
                messages = allMessages
            )
        }
    }

    private fun Message.toUiMessage(isOutgoing: Boolean): ConversationMessageUiState {
        return ConversationMessageUiState(
            id = messageId.toString(),
            text = plaintextContent,
            isOutgoing = isOutgoing,
            timestamp = formatTime(composeTimestamp.millis),
            rawTimestamp = composeTimestamp.millis,
            deliveryStatusLabel = if (isOutgoing) deliveryStatus.toUiLabel() else null,
            deliveryStatus = if (isOutgoing) deliveryStatus else null
        )
    }

    private fun FileTransferRecord.toUiMessage(): ConversationMessageUiState {
        return ConversationMessageUiState(
            id = transferId.toString(),
            text = if (isIncoming) "Incoming File: ${metadata.filename}" else "Sending File: ${metadata.filename}",
            isOutgoing = !isIncoming,
            timestamp = formatTime(createdAt),
            rawTimestamp = createdAt,
            deliveryStatusLabel = status.name,
            fileTransfer = FileTransferUiState(
                transferId = transferId.toString(),
                filename = metadata.filename,
                progress = progress,
                status = status.name,
                isIncoming = isIncoming,
                localPath = if (isIncoming) outputPath else sourcePath
            )
        )
    }

    private fun MessageDeliveryStatus.toUiLabel(): String = when (this) {
        MessageDeliveryStatus.QUEUED -> "Queued"
        MessageDeliveryStatus.SENT -> "Sent"
        MessageDeliveryStatus.DELIVERED -> "Delivered"
        MessageDeliveryStatus.READ -> "Read"
        MessageDeliveryStatus.FAILED -> "Failed"
    }

    private fun formatTime(millis: Long): String {
        return DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(millis))
    }

    fun dial() {
        val destination = activeNodeId ?: return
        voiceCallManager.dial(destination)
    }

    private fun startRouteRefreshLoop() {
        viewModelScope.launch {
            while (true) {
                _routeNodeIds.value = meshService.getRoutes().map { it.destinationNodeId.toString() }.toSet()
                delay(5_000)
            }
        }
    }

    private fun collectPeerEvents() {
        viewModelScope.launch {
            meshService.peerEventsStream.collect { event ->
                _peerMap.update { current ->
                    val next = when (event) {
                        is PeerEvent.Added -> {
                            current + (event.peer.nodeId.toString() to
                                PeerState(event.peer.nodeId, event.peer.ip, null, null, PeerStatus.ACTIVE, event.peer.lastSeen))
                        }
                        is PeerEvent.Updated -> {
                            val existing = current[event.peer.nodeId.toString()]
                            current + (event.peer.nodeId.toString() to
                                PeerState(event.peer.nodeId, event.peer.ip, existing?.name, existing?.publicKey, PeerStatus.ACTIVE, event.peer.lastSeen))
                        }
                        is PeerEvent.Removed -> current - event.nodeId.toString()
                    }
                    // Trigger an immediate route refresh when peers change
                    _routeNodeIds.value = meshService.getRoutes().map { it.destinationNodeId.toString() }.toSet()
                    next
                }
            }
        }
    }

    private fun shortId(nodeId: String): String =
        if (nodeId.length <= 8) nodeId else "${nodeId.take(8)}..."

    private fun initialsFrom(value: String): String {
        val tokens = value.trim().split(" ").filter { it.isNotBlank() }
        return when {
            tokens.isEmpty() -> "NA"
            tokens.size == 1 -> tokens[0].take(2).uppercase()
            else -> "${tokens[0].first()}${tokens[1].first()}".uppercase()
        }
    }

    private fun parseNodeId(value: String): NodeId? {
        if (value.length != 64) return null
        return runCatching {
            val bytes = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            NodeId(bytes)
        }.getOrNull()
    }
}
