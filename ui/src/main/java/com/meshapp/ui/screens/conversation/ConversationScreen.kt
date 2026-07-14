package com.meshapp.ui.screens.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshapp.ui.state.ConversationMessageUiState
import com.meshapp.ui.theme.MeshGreen
import com.meshapp.ui.theme.MeshMuted
import com.meshapp.ui.viewmodel.ConversationViewModel
import com.meshapp.messaging.MessageDeliveryStatus

@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel = viewModel(),
    nodeId: String,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var draftMessage by remember { mutableStateOf("") }

    LaunchedEffect(nodeId) {
        viewModel.initialize(nodeId)
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Scaffold(
        containerColor = ConversationBg,
        topBar = {
            ConversationTopBar(
                title = uiState.node.name.ifBlank { "Conversation" },
                initials = uiState.node.avatarInitials.ifBlank { "?" }.take(1),
                isOnline = uiState.node.isOnline,
                onBack = onBack
            )
        },
        bottomBar = {
            ConversationInputBar(
                draftMessage = draftMessage,
                onDraftChange = { draftMessage = it },
                onSend = {
                    viewModel.sendMessage(draftMessage)
                    draftMessage = ""
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF222529)
                ) {
                    Text(
                        text = "Today",
                        color = Color(0xFFD7D9DB),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    ConversationBubble(message = message)
                }
                item { Spacer(modifier = Modifier.height(6.dp)) }
            }
        }
    }
}

private val ConversationBg = Color(0xFF0A0B0D)
private val InboundBg = Color(0xFF1F2E44)
private val InboundBorder = Color(0xFF334865)
private val OutboundBg = Color(0xFF05543F)
private val OutboundBorder = Color(0xFF0C6D50)

@Composable
private fun ConversationTopBar(
    title: String,
    initials: String,
    isOnline: Boolean,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConversationBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MeshGreen),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = Color(0xFF052A12),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) MeshGreen else MeshMuted)
                    .border(2.dp, ConversationBg, CircleShape)
            )
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFE6E9EA),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (isOnline) "ONLINE" else "OFFLINE",
                color = if (isOnline) MeshGreen else MeshMuted,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "More",
                tint = Color(0xFFB5BABE)
            )
        }
    }
}

@Composable
private fun ConversationBubble(message: ConversationMessageUiState) {
    val alignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isOutgoing) OutboundBg else InboundBg
    val borderColor = if (message.isOutgoing) OutboundBorder else InboundBorder

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(
                    RoundedCornerShape(
                        topStart = 22.dp,
                        topEnd = 22.dp,
                        bottomStart = if (message.isOutgoing) 22.dp else 6.dp,
                        bottomEnd = if (message.isOutgoing) 6.dp else 22.dp
                    )
                )
                .background(bubbleColor)
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(
                        topStart = 22.dp,
                        topEnd = 22.dp,
                        bottomStart = if (message.isOutgoing) 22.dp else 6.dp,
                        bottomEnd = if (message.isOutgoing) 6.dp else 22.dp
                    )
                )
                .padding(16.dp)
        ) {
            Text(
                text = message.text,
                color = Color(0xFFE4E7E9),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Row(
            modifier = Modifier.padding(top = 6.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message.timestamp,
                color = Color(0xFFA9B0B4),
                style = MaterialTheme.typography.bodySmall,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (message.isOutgoing) {
                Spacer(modifier = Modifier.size(8.dp))
                val icon = when (message.deliveryStatus) {
                    MessageDeliveryStatus.QUEUED, MessageDeliveryStatus.SENT -> Icons.Filled.Done
                    MessageDeliveryStatus.DELIVERED, MessageDeliveryStatus.READ -> Icons.Filled.DoneAll
                    MessageDeliveryStatus.FAILED -> Icons.Filled.Close
                    else -> Icons.Filled.Done
                }
                val iconColor = if (message.deliveryStatus == MessageDeliveryStatus.FAILED) Color.Red else MeshGreen
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ConversationInputBar(
    draftMessage: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConversationBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.AttachFile,
                contentDescription = "Attach",
                tint = Color(0xFFB8BDBF)
            )
        }

        OutlinedTextField(
            value = draftMessage,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(30.dp),
            placeholder = {
                Text(
                    text = "Type a message...",
                    color = Color(0xFF7F868A),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF07090A),
                unfocusedContainerColor = Color(0xFF07090A),
                focusedBorderColor = Color(0xFF304A42),
                unfocusedBorderColor = Color(0xFF304A42),
                focusedTextColor = Color(0xFFE1E4E6),
                unfocusedTextColor = Color(0xFFE1E4E6)
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() })
        )

        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(MeshGreen)
                .clickable(onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = "Send",
                tint = Color(0xFF052A12)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConversationScreenPreview() {
    ConversationScreen(nodeId = "node-alpha", onBack = {})
}
