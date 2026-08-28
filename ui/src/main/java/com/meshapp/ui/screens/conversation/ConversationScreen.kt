package com.meshapp.ui.screens.conversation

import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshapp.messaging.MessageDeliveryStatus
import com.meshapp.ui.components.EmptyState
import com.meshapp.ui.components.StatusDot
import com.meshapp.ui.state.ConversationMessageUiState
import com.meshapp.ui.state.FileTransferUiState
import com.meshapp.ui.theme.MeshBg0
import com.meshapp.ui.theme.MeshBg2
import com.meshapp.ui.theme.MeshBg3
import com.meshapp.ui.theme.MeshBubbleInbound
import com.meshapp.ui.theme.MeshBubbleOutbound
import com.meshapp.ui.theme.MeshDanger
import com.meshapp.ui.theme.MeshGreen
import com.meshapp.ui.theme.MeshGreenOnAccent
import com.meshapp.ui.theme.MeshMuted
import com.meshapp.ui.theme.MeshOffline
import com.meshapp.ui.theme.MeshRadius
import com.meshapp.ui.theme.MeshShapes
import com.meshapp.ui.theme.MeshSpacing
import com.meshapp.ui.theme.MeshTextPrimary
import com.meshapp.ui.theme.MeshTextSecondary
import com.meshapp.ui.viewmodel.ConversationViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("MissingPermission")
@Composable
fun ConversationScreen(
    nodeId: String,
    onBack: () -> Unit,
    viewModel: ConversationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var draftMessage by remember { mutableStateOf("") }
    var showScrollToBottom by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.attachFile(context, it) }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.attachFile(context, it) }
    }

    val cameraPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let { viewModel.attachCapturedImage(context, it) }
    }

    LaunchedEffect(nodeId) {
        viewModel.initialize(nodeId)
    }

    val entries = remember(uiState.messages) { buildConversationEntries(uiState.messages) }

    val isAtBottom by remember(entries) {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= entries.size - 1
        }
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom) showScrollToBottom = false
    }

    LaunchedEffect(entries.size) {
        if (entries.isEmpty()) return@LaunchedEffect
        if (isAtBottom) {
            listState.animateScrollToItem(entries.lastIndex)
        } else {
            showScrollToBottom = true
        }
    }

    // tracks which message ids have already been shown once, so only newly
    // arriving messages animate in and the existing history never replays
    val hasLoadedInitialMessages = remember { mutableStateOf(false) }
    val animatedIds = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(entries) {
        if (!hasLoadedInitialMessages.value) {
            entries.forEach { entry ->
                if (entry is ConversationListEntry.MessageEntry) {
                    animatedIds[entry.message.id] = true
                }
            }
            hasLoadedInitialMessages.value = true
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MeshBg0,
        topBar = {
            ConversationTopBar(
                title = uiState.node.name.ifBlank { "Conversation" },
                initials = uiState.node.avatarInitials.ifBlank { "?" }.take(1),
                isOnline = uiState.node.isOnline,
                onBack = onBack,
                onCall = {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.dial()
                    } else {
                        // permission flow is handled from MainActivity, just log here
                        Log.w("ConversationScreen", "RECORD_AUDIO permission missing for dial")
                    }
                }
            )
        },
        bottomBar = {
            ConversationInputBar(
                draftMessage = draftMessage,
                onDraftChange = { draftMessage = it },
                onSend = {
                    if (draftMessage.isNotBlank()) {
                        viewModel.sendMessage(draftMessage.trim())
                        draftMessage = ""
                    }
                },
                onAttachDocument = { documentPickerLauncher.launch("*/*") },
                onAttachGallery = {
                    galleryPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                onAttachCamera = { cameraPickerLauncher.launch(null) },
                onRecordStart = {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.startVoiceMessageRecording()
                    } else {
                        Log.w("ConversationScreen", "RECORD_AUDIO permission missing for recording")
                        false
                    }
                },
                onRecordStop = { viewModel.stopVoiceMessageRecording() },
                onRecordCancel = { viewModel.cancelVoiceMessageRecording() }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (entries.isEmpty()) {
                EmptyState(
                    title = "No messages yet",
                    subtitle = "",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(MeshSpacing.lg)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = MeshSpacing.md),
                    state = listState,
                    verticalArrangement = Arrangement.Top
                ) {
                    itemsIndexed(
                        items = entries,
                        key = { _, entry ->
                            when (entry) {
                                is ConversationListEntry.DateHeader -> "date_${entry.label}"
                                is ConversationListEntry.MessageEntry -> entry.message.id
                            }
                        }
                    ) { _, entry ->
                        when (entry) {
                            is ConversationListEntry.DateHeader -> DateDivider(label = entry.label)
                            is ConversationListEntry.MessageEntry -> {
                                val alreadyAnimated = animatedIds[entry.message.id] == true
                                var visible by remember(entry.message.id) { mutableStateOf(alreadyAnimated) }
                                LaunchedEffect(entry.message.id) {
                                    if (!visible) {
                                        visible = true
                                        animatedIds[entry.message.id] = true
                                    }
                                }
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn(tween(220)) + slideInVertically(
                                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                                        initialOffsetY = { it / 4 }
                                    ) + slideInHorizontally(
                                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                                        initialOffsetX = { if (entry.message.isOutgoing) it / 10 else -it / 10 }
                                    )
                                ) {
                                    ConversationBubble(
                                        message = entry.message,
                                        showTimestamp = entry.isLastInGroup,
                                        isPlaying = uiState.playingTransferId == entry.message.fileTransfer?.transferId,
                                        onFileClick = { viewModel.openFile(context, it) },
                                        onVoiceMessagePlayToggle = { transfer -> viewModel.toggleVoicePlayback(transfer) },
                                        modifier = Modifier.padding(bottom = if (entry.isLastInGroup) MeshSpacing.md else MeshSpacing.xxs)
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(6.dp)) }
                }

                AnimatedVisibility(
                    visible = showScrollToBottom,
                    enter = fadeIn(tween(150)) + scaleIn(tween(150)),
                    exit = fadeOut(tween(120)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(MeshSpacing.md)
                ) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(entries.lastIndex)
                            }
                            showScrollToBottom = false
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MeshGreen)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Scroll to latest",
                            tint = MeshGreenOnAccent
                        )
                    }
                }
            }
        }
    }
}

private sealed interface ConversationListEntry {
    data class DateHeader(val label: String) : ConversationListEntry
    data class MessageEntry(val message: ConversationMessageUiState, val isLastInGroup: Boolean) : ConversationListEntry
}

private fun buildConversationEntries(messages: List<ConversationMessageUiState>): List<ConversationListEntry> {
    if (messages.isEmpty()) return emptyList()
    val entries = mutableListOf<ConversationListEntry>()
    var lastDateLabel: String? = null

    messages.forEachIndexed { index, message ->
        val dateLabel = dateLabelFor(message.rawTimestamp)
        if (dateLabel != lastDateLabel) {
            entries += ConversationListEntry.DateHeader(dateLabel)
            lastDateLabel = dateLabel
        }
        val next = messages.getOrNull(index + 1)
        val sameDayAsNext = next != null && dateLabelFor(next.rawTimestamp) == dateLabel
        val isLastInGroup = next == null || next.isOutgoing != message.isOutgoing || !sameDayAsNext
        entries += ConversationListEntry.MessageEntry(message, isLastInGroup)
    }
    return entries
}

private fun dateLabelFor(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> DateTimeFormatter.ofPattern("EEEE, MMM d").format(date)
        else -> DateTimeFormatter.ofPattern("MMM d, yyyy").format(date)
    }
}

// -- file and media presentation helpers -------------------------------

private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp")
private val videoExtensions = setOf("mp4", "mkv", "webm", "3gp", "mov", "avi")
private val audioExtensions = setOf("mp3", "wav", "m4a", "ogg", "flac", "aac")

private fun extensionOf(filename: String): String =
    filename.substringAfterLast('.', "").lowercase()

private fun isImageFile(filename: String): Boolean = extensionOf(filename) in imageExtensions
private fun isVideoFile(filename: String): Boolean = extensionOf(filename) in videoExtensions
private fun isAudioFile(filename: String): Boolean = extensionOf(filename) in audioExtensions

private fun fileIconFor(filename: String): ImageVector = when (extensionOf(filename)) {
    "pdf" -> Icons.Filled.PictureAsPdf
    "doc", "docx", "txt", "rtf" -> Icons.Filled.Description
    "xls", "xlsx", "csv" -> Icons.Filled.TableChart
    "ppt", "pptx" -> Icons.Filled.Slideshow
    in audioExtensions -> Icons.Filled.AudioFile
    in videoExtensions -> Icons.Filled.VideoFile
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun formatStatusLabel(status: String): String =
    status.lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun rememberLocalThumbnail(path: String?, isVideo: Boolean): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, path, isVideo) {
        value = if (path.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try {
                    if (isVideo) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(path)
                            retriever.getFrameAtTime()?.asImageBitmap()
                        } finally {
                            retriever.release()
                        }
                    } else {
                        BitmapFactory.decodeFile(path)?.asImageBitmap()
                    }
                } catch (error: Exception) {
                    null
                }
            }
        }
    }
    return state.value
}

@Composable
private fun DateDivider(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MeshSpacing.lg, bottom = MeshSpacing.sm),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = MeshTextSecondary,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun ConversationTopBar(
    title: String,
    initials: String,
    isOnline: Boolean,
    onBack: () -> Unit,
    onCall: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MeshBg0)
            .padding(horizontal = MeshSpacing.xs, vertical = MeshSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MeshTextPrimary
            )
        }
        Box(modifier = Modifier.padding(start = 4.dp)) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MeshGreen),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = MeshGreenOnAccent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            StatusDot(
                isOnline = isOnline,
                size = 11.dp,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
        Column(modifier = Modifier.padding(start = MeshSpacing.sm)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MeshTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (isOnline) "Online" else "Offline",
                color = if (isOnline) MeshGreen else MeshOffline,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = onCall) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Call",
                tint = MeshGreen
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationBubble(
    message: ConversationMessageUiState,
    showTimestamp: Boolean,
    isPlaying: Boolean,
    onFileClick: (FileTransferUiState) -> Unit,
    onVoiceMessagePlayToggle: (FileTransferUiState) -> Unit,
    modifier: Modifier = Modifier
) {
    val alignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isOutgoing) MeshBubbleOutbound else MeshBubbleInbound
    val fileTransfer = message.fileTransfer
    val isImage = fileTransfer != null && isImageFile(fileTransfer.filename)
    val isVideo = fileTransfer != null && isVideoFile(fileTransfer.filename)
    val isMedia = isImage || isVideo

    val bubbleShape = RoundedCornerShape(
        topStart = MeshRadius.lg,
        topEnd = MeshRadius.lg,
        bottomStart = if (message.isOutgoing) MeshRadius.lg else MeshRadius.sm,
        bottomEnd = if (message.isOutgoing) MeshRadius.sm else MeshRadius.lg
    )

    var showMenu by remember(message.id) { mutableStateOf(false) }
    var isSelectable by remember(message.id) { mutableStateOf(false) }
    var fullscreenImagePath by remember(message.id) { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val maxBubbleWidth = maxWidth * 0.78f

            Box(
                modifier = Modifier.align(if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart)
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = maxBubbleWidth)
                        .clip(bubbleShape)
                        .background(if (isMedia) MeshBg2 else bubbleColor)
                        .combinedClickable(
                            onClick = {
                                when {
                                    fileTransfer == null -> Unit
                                    fileTransfer.isVoiceMessage -> Unit
                                    isVideo -> onFileClick(fileTransfer)
                                    isImage && fileTransfer.localPath != null -> fullscreenImagePath = fileTransfer.localPath
                                    else -> onFileClick(fileTransfer)
                                }
                            },
                            onLongClick = { showMenu = true }
                        )
                        .then(if (isMedia) Modifier else Modifier.padding(horizontal = MeshSpacing.sm, vertical = MeshSpacing.xs))
                ) {
                    if (fileTransfer != null) {
                        AnimatedContent(
                            targetState = fileTransfer.status,
                            transitionSpec = { (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.96f)) togetherWith fadeOut(tween(120)) },
                            label = "file-content"
                        ) {
                            FileTransferContent(transfer = fileTransfer, isPlaying = isPlaying, onPlayToggle = { onVoiceMessagePlayToggle(fileTransfer) })
                        }
                    } else {
                        if (isSelectable) {
                            SelectionContainer {
                                Text(text = message.text, color = MeshTextPrimary, style = MaterialTheme.typography.bodyLarge)
                            }
                        } else {
                            Text(text = message.text, color = MeshTextPrimary, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (fileTransfer == null) {
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            onClick = {
                                clipboardManager.setText(AnnotatedString(message.text))
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Select text") },
                            leadingIcon = { Icon(Icons.Filled.Highlight, contentDescription = null) },
                            onClick = {
                                isSelectable = true
                                showMenu = false
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Copy filename") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            onClick = {
                                clipboardManager.setText(AnnotatedString(fileTransfer.filename))
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }

        if (showTimestamp) {
            Row(
                modifier = Modifier.padding(top = 3.dp, start = MeshSpacing.xxs, end = MeshSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.timestamp,
                    color = MeshTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                if (message.isOutgoing) {
                    Spacer(modifier = Modifier.size(MeshSpacing.xxs))
                    DeliveryStatusIcon(status = message.deliveryStatus)
                }
            }
        }
    }

    fullscreenImagePath?.let { path ->
        FullscreenImageDialog(path = path, onDismiss = { fullscreenImagePath = null })
    }
}

@Composable
private fun DeliveryStatusIcon(status: MessageDeliveryStatus?) {
    if (status == null) return
    val (icon, iconColor) = when (status) {
        MessageDeliveryStatus.QUEUED, MessageDeliveryStatus.SENT -> Icons.Filled.Done to MeshTextSecondary
        MessageDeliveryStatus.DELIVERED, MessageDeliveryStatus.READ -> Icons.Filled.DoneAll to MeshGreen
        MessageDeliveryStatus.FAILED -> Icons.Filled.ErrorOutline to MeshDanger
    }

    AnimatedContent(
        targetState = icon,
        transitionSpec = { (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.7f)) togetherWith fadeOut(tween(100)) },
        label = "delivery-status"
    ) { animatedIcon ->
        Icon(
            imageVector = animatedIcon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun FileTransferContent(
    transfer: FileTransferUiState,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit
) {
    when {
        transfer.isVoiceMessage -> VoiceMessageContent(transfer, isPlaying, onPlayToggle)
        isImageFile(transfer.filename) || isVideoFile(transfer.filename) -> MediaMessageContent(transfer)
        else -> GenericFileContent(transfer)
    }
}

@Composable
private fun VoiceMessageContent(
    transfer: FileTransferUiState,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MeshGreen)
                .clickable(onClick = onPlayToggle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MeshGreenOnAccent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.size(MeshSpacing.sm))
        Text(
            text = "%.0fs".format(transfer.durationMs / 1000f),
            color = MeshTextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun MediaMessageContent(transfer: FileTransferUiState) {
    val isVideo = isVideoFile(transfer.filename)
    val thumbnail = rememberLocalThumbnail(transfer.localPath, isVideo)
    val isLoading = transfer.localPath != null && thumbnail == null
    val isTransferring = transfer.status == "TRANSFERRING"

    Box(
        modifier = Modifier
            .widthIn(min = 160.dp)
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(MeshRadius.md))
            .background(MeshBg3)
    ) {
        AnimatedVisibility(
            visible = thumbnail != null,
            enter = fadeIn(tween(260))
        ) {
            thumbnail?.let {
                Image(
                    bitmap = it,
                    contentDescription = transfer.filename,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (thumbnail == null) {
            Icon(
                imageVector = if (isVideo) Icons.Filled.VideoFile else Icons.Filled.Image,
                contentDescription = null,
                tint = MeshMuted,
                modifier = Modifier.align(Alignment.Center).size(36.dp)
            )
        }

        if (isTransferring || isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { if (isTransferring) transfer.progress else 0f },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(28.dp)
                )
            }
        } else if (isVideo && thumbnail != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (transfer.status == "FAILED") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = "Failed",
                    tint = MeshDanger,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun GenericFileContent(transfer: FileTransferUiState) {
    val isFailed = transfer.status == "FAILED"
    val isTransferring = transfer.status == "TRANSFERRING"

    Row(
        modifier = Modifier.widthIn(min = 200.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(MeshRadius.sm))
                .background(MeshBg3),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFailed) Icons.Filled.ErrorOutline else fileIconFor(transfer.filename),
                contentDescription = null,
                tint = if (isFailed) MeshDanger else MeshGreen,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.size(MeshSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transfer.filename,
                color = MeshTextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = formatStatusLabel(transfer.status),
                color = if (isFailed) MeshDanger else MeshMuted,
                style = MaterialTheme.typography.labelSmall
            )
            if (isTransferring) {
                Spacer(modifier = Modifier.height(MeshSpacing.xxs))
                LinearProgressIndicator(
                    progress = { transfer.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(MeshRadius.pill)),
                    color = MeshGreen,
                    trackColor = MeshBg2
                )
            }
        }
    }
}

@Composable
private fun FullscreenImageDialog(path: String, onDismiss: () -> Unit) {
    val bitmap = rememberLocalThumbnail(path, isVideo = false)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(MeshSpacing.md)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(MeshSpacing.sm)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

/**
 * The three mutually exclusive states the bottom input bar can be in.
 * NORMAL    text field, attach icon on the left, mic or send on the right
 * RECORDING pulsing recording indicator, replaces text field and attach icon
 * ATTACHING inline row of attachment type chips, replaces text field and mic or send
 */
private enum class InputBarMode { NORMAL, RECORDING, ATTACHING }

@Composable
private fun ConversationInputBar(
    draftMessage: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachDocument: () -> Unit,
    onAttachGallery: () -> Unit,
    onAttachCamera: () -> Unit,
    onRecordStart: () -> Boolean,
    onRecordStop: () -> Unit,
    onRecordCancel: () -> Unit
) {
    var mode by remember { mutableStateOf(InputBarMode.NORMAL) }
    var recordingTimeSeconds by remember { mutableLongStateOf(0L) }

    val infiniteTransition = rememberInfiniteTransition(label = "recordingPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    LaunchedEffect(mode) {
        if (mode == InputBarMode.RECORDING) {
            recordingTimeSeconds = 0L
            while (true) {
                delay(1000.milliseconds)
                recordingTimeSeconds++
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MeshBg0)
            .padding(horizontal = MeshSpacing.md, vertical = MeshSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (mode != InputBarMode.RECORDING) {
            IconButton(
                onClick = {
                    mode = if (mode == InputBarMode.ATTACHING) InputBarMode.NORMAL else InputBarMode.ATTACHING
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (mode == InputBarMode.ATTACHING) Icons.Filled.Close else Icons.Filled.AttachFile,
                    contentDescription = if (mode == InputBarMode.ATTACHING) "Close attachment options" else "Attach",
                    tint = if (mode == InputBarMode.ATTACHING) MeshTextPrimary else MeshMuted
                )
            }
        }

        AnimatedContent(
            targetState = mode,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (mode == InputBarMode.RECORDING) MeshSpacing.sm else 0.dp),
            transitionSpec = {
                (fadeIn(tween(180)) + slideInVertically(tween(180)) { height -> height / 3 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(120)) { height -> -height / 3 })
            },
            label = "inputBarContent"
        ) { targetMode ->
            when (targetMode) {
                InputBarMode.RECORDING -> RecordingIndicator(
                    recordingTimeSeconds = recordingTimeSeconds,
                    dotAlpha = dotAlpha
                )
                InputBarMode.ATTACHING -> AttachmentChipRow(
                    onDocument = { onAttachDocument(); mode = InputBarMode.NORMAL },
                    onGallery = { onAttachGallery(); mode = InputBarMode.NORMAL },
                    onCamera = { onAttachCamera(); mode = InputBarMode.NORMAL }
                )
                InputBarMode.NORMAL -> MessageTextField(
                    draftMessage = draftMessage,
                    onDraftChange = onDraftChange,
                    onSend = onSend
                )
            }
        }

        Spacer(modifier = Modifier.size(MeshSpacing.sm))

        if (mode == InputBarMode.RECORDING) {
            IconButton(
                onClick = {
                    onRecordCancel()
                    mode = InputBarMode.NORMAL
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Cancel recording",
                    tint = MeshMuted
                )
            }
            Spacer(modifier = Modifier.size(MeshSpacing.xs))
        }

        if (mode != InputBarMode.ATTACHING) {
            val isRecording = mode == InputBarMode.RECORDING
            val micIcon = when {
                isRecording -> Icons.Filled.Stop
                draftMessage.isBlank() -> Icons.Filled.Mic
                else -> Icons.AutoMirrored.Filled.Send
            }
            val micDescription = when {
                isRecording -> "Stop and send recording"
                draftMessage.isBlank() -> "Record voice message"
                else -> "Send"
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .graphicsLayer {
                        if (isRecording) {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                    }
                    .clip(CircleShape)
                    .background(if (isRecording) MeshDanger else MeshGreen)
                    .clickable {
                        when {
                            isRecording -> {
                                onRecordStop()
                                mode = InputBarMode.NORMAL
                            }
                            draftMessage.isBlank() -> {
                                if (onRecordStart()) mode = InputBarMode.RECORDING
                            }
                            else -> onSend()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = micIcon,
                    contentDescription = micDescription,
                    tint = MeshGreenOnAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun MessageTextField(
    draftMessage: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    OutlinedTextField(
        value = draftMessage,
        onValueChange = onDraftChange,
        modifier = Modifier.fillMaxWidth(),
        shape = MeshShapes.input,
        placeholder = {
            Text(
                text = "Type a message",
                color = MeshMuted,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MeshBg2,
            unfocusedContainerColor = MeshBg2,
            focusedBorderColor = MeshGreen.copy(alpha = 0.4f),
            unfocusedBorderColor = MeshBg2,
            focusedTextColor = MeshTextPrimary,
            unfocusedTextColor = MeshTextPrimary
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() })
    )
}

@Composable
private fun RecordingIndicator(
    recordingTimeSeconds: Long,
    dotAlpha: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .graphicsLayer { alpha = dotAlpha }
                .clip(CircleShape)
                .background(MeshDanger)
        )
        Spacer(modifier = Modifier.width(MeshSpacing.xs))
        Text(
            text = "Recording  %02d:%02d".format(recordingTimeSeconds / 60, recordingTimeSeconds % 60),
            color = MeshTextPrimary,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun AttachmentChipRow(
    onDocument: () -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MeshSpacing.xs)
    ) {
        AttachmentChip(
            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
            label = "Document",
            modifier = Modifier.weight(1f),
            onClick = onDocument
        )
        AttachmentChip(
            icon = Icons.Filled.Image,
            label = "Gallery",
            modifier = Modifier.weight(1f),
            onClick = onGallery
        )
        AttachmentChip(
            icon = Icons.Filled.PhotoCamera,
            label = "Camera",
            modifier = Modifier.weight(1f),
            onClick = onCamera
        )
    }
}

@Composable
private fun AttachmentChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(MeshShapes.input)
            .background(MeshBg2)
            .clickable(onClick = onClick)
            .padding(vertical = MeshSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MeshGreen,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = MeshTextPrimary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}