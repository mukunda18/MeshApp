package com.meshapp.ui.screens.conversation

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.meshapp.ui.theme.MeshBubbleInboundBorder
import com.meshapp.ui.theme.MeshBubbleOutbound
import com.meshapp.ui.theme.MeshBubbleOutboundBorder
import com.meshapp.ui.theme.MeshDanger
import com.meshapp.ui.theme.MeshGreen
import com.meshapp.ui.theme.MeshGreenOnAccent
import com.meshapp.ui.theme.MeshMuted
import com.meshapp.ui.theme.MeshOffline
import com.meshapp.ui.theme.MeshRadius
import com.meshapp.ui.theme.MeshShapes
import com.meshapp.ui.theme.MeshSpacing
import com.meshapp.ui.theme.MeshTextPrimary
import com.meshapp.ui.viewmodel.ConversationViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MeshBg0,
        topBar = {
            ConversationTopBar(
                title = uiState.node.name.ifBlank { "Conversation" },
                initials = uiState.node.avatarInitials.ifBlank { "?" }.take(1),
                isOnline = uiState.node.isOnline,
                onBack = onBack,
                onCall = { viewModel.dial() }
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
                onRecordStart = { viewModel.startVoiceMessageRecording() },
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
                    subtitle = "Say hello to ${uiState.node.name.ifBlank { "this node" }} to start the conversation.",
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
                            is ConversationListEntry.MessageEntry -> ConversationBubble(
                                message = entry.message,
                                showTimestamp = entry.isLastInGroup,
                                isPlaying = uiState.playingTransferId == entry.message.fileTransfer?.transferId,
                                onFileClick = { viewModel.openFile(context, it) },
                                onVoiceMessagePlayToggle = { transfer -> viewModel.toggleVoicePlayback(transfer) },
                                modifier = Modifier.padding(bottom = if (entry.isLastInGroup) MeshSpacing.sm else 2.dp)
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(6.dp)) }
                }

                if (showScrollToBottom) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(entries.lastIndex)
                            }
                            showScrollToBottom = false
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(MeshSpacing.md)
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

@Composable
private fun DateDivider(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MeshSpacing.md, bottom = MeshSpacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MeshShapes.chip,
            color = MeshBg3
        ) {
            Text(
                text = label,
                color = MeshMuted,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = MeshSpacing.md, vertical = MeshSpacing.xs)
            )
        }
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
            .padding(horizontal = MeshSpacing.sm, vertical = MeshSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MeshGreen
            )
        }
        Box(modifier = Modifier.padding(start = 4.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
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
                size = 12.dp,
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
                text = if (isOnline) "ONLINE" else "OFFLINE",
                color = if (isOnline) MeshGreen else MeshOffline,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))

        if (isOnline) {
            IconButton(onClick = onCall) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = MeshGreen
                )
            }
        }

        IconButton(onClick = { /* Menu Action */ }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "More",
                tint = MeshMuted
            )
        }
    }
}

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
    val borderColor = if (message.isOutgoing) MeshBubbleOutboundBorder else MeshBubbleInboundBorder
    val isVoiceMessage = message.fileTransfer?.isVoiceMessage == true

    val bubbleShape = RoundedCornerShape(
        topStart = MeshRadius.lg,
        topEnd = MeshRadius.lg,
        bottomStart = if (message.isOutgoing) MeshRadius.lg else 6.dp,
        bottomEnd = if (message.isOutgoing) 6.dp else MeshRadius.lg
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(bubbleShape)
                .background(bubbleColor)
                .border(1.dp, borderColor, bubbleShape)
                .then(
                    if (message.fileTransfer != null && !isVoiceMessage) {
                        Modifier.clickable { onFileClick(message.fileTransfer) }
                    } else Modifier
                )
                .padding(MeshSpacing.md)
        ) {
            if (message.fileTransfer != null) {
                FileTransferContent(
                    transfer = message.fileTransfer,
                    isPlaying = isPlaying,
                    onPlayToggle = { onVoiceMessagePlayToggle(message.fileTransfer) }
                )
            } else {
                Text(
                    text = message.text,
                    color = MeshTextPrimary,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        if (showTimestamp) {
            Row(
                modifier = Modifier.padding(top = 4.dp, start = MeshSpacing.xs, end = MeshSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.timestamp,
                    color = MeshMuted,
                    style = MaterialTheme.typography.bodySmall,
                    letterSpacing = 0.6.sp,
                    fontWeight = FontWeight.Medium
                )
                if (message.isOutgoing) {
                    Spacer(modifier = Modifier.size(MeshSpacing.xs))
                    DeliveryStatusIcon(status = message.deliveryStatus)
                }
            }
        }
    }
}

@Composable
private fun DeliveryStatusIcon(status: MessageDeliveryStatus?) {
    if (status == null) return
    val (icon, iconColor) = when (status) {
        MessageDeliveryStatus.QUEUED, MessageDeliveryStatus.SENT -> Icons.Filled.Done to MeshGreen
        MessageDeliveryStatus.DELIVERED, MessageDeliveryStatus.READ -> Icons.Filled.DoneAll to MeshGreen
        MessageDeliveryStatus.FAILED -> Icons.Filled.Close to MeshDanger
        else -> Icons.Filled.Done to MeshGreen
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconColor,
        modifier = Modifier.size(16.dp)
    )
}

@Composable
private fun FileTransferContent(
    transfer: FileTransferUiState,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit
) {
    if (transfer.isVoiceMessage) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlayToggle, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MeshGreen
                )
            }
            Spacer(modifier = Modifier.size(MeshSpacing.xs))
            Text(
                text = "%.0fs".format(transfer.durationMs / 1000f),
                color = MeshMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.AttachFile,
                contentDescription = null,
                tint = MeshGreen,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.size(MeshSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transfer.filename,
                    color = MeshTextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = transfer.status,
                    color = MeshMuted,
                    style = MaterialTheme.typography.labelMedium
                )
                if (transfer.status == "TRANSFERRING") {
                    Spacer(modifier = Modifier.height(MeshSpacing.xs))
                    LinearProgressIndicator(
                        progress = { transfer.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp)),
                        color = MeshGreen,
                        trackColor = MeshBg3
                    )
                }
            }
        }
    }
}

/**
 * The three mutually exclusive states the bottom input bar can be in.
 * NORMAL   -> text field, attach icon on the left, mic/send on the right
 * RECORDING -> pulsing recording indicator, replaces text field + attach icon
 * ATTACHING -> inline row of attachment type chips, replaces text field + mic/send
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
        targetValue = 1.15f,
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
                delay(1000L)
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
        // Leading icon: attach <-> close. Hidden while recording, same as before.
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

        // Mic/send circle is hidden while the attachment chips are shown -
        // there's nothing to send and no draft to hold.
        if (mode != InputBarMode.ATTACHING) {
            val isRecording = mode == InputBarMode.RECORDING
            val micIcon = when {
                isRecording -> Icons.Filled.Stop
                draftMessage.isBlank() -> Icons.Filled.Mic
                else -> Icons.Filled.Send
            }
            val micDescription = when {
                isRecording -> "Stop and send recording"
                draftMessage.isBlank() -> "Record voice message"
                else -> "Send"
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
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
                    tint = MeshGreenOnAccent
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
                text = "Type a message...",
                color = MeshMuted,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MeshBg2,
            unfocusedContainerColor = MeshBg2,
            focusedBorderColor = MeshGreen.copy(alpha = 0.5f),
            unfocusedBorderColor = MeshBg3,
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
            icon = Icons.Filled.InsertDriveFile,
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
            .border(1.dp, MeshBg3, MeshShapes.input)
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