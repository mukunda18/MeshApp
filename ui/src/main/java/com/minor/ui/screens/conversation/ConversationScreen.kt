package com.minor.ui.screens.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minor.ui.state.ConversationMessageUiState
import com.minor.ui.theme.MeshAccentBlue
import com.minor.ui.theme.MeshBackground
import com.minor.ui.theme.MeshBorder
import com.minor.ui.theme.MeshGreen
import com.minor.ui.theme.MeshHeader
import com.minor.ui.theme.MeshMuted
import com.minor.ui.theme.MeshSurface
import com.minor.ui.theme.MeshTextPrimary
import com.minor.ui.viewmodel.ConversationViewModel

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
        containerColor = MeshBackground,
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
                    .padding(top = 20.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MeshHeader
                ) {
                    Text(
                        text = "Today",
                        color = MeshMuted,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    ConversationBubble(message = message)
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

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
            .background(MeshBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MeshHeader),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = MeshTextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) MeshGreen else MeshMuted)
                    .border(2.dp, MeshBackground, CircleShape)
            )
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MeshTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (isOnline) "Online" else "Offline",
                color = MeshMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "More",
                tint = MeshMuted
            )
        }
    }
}

@Composable
private fun ConversationBubble(message: ConversationMessageUiState) {
    val alignment = if (message.isOutgoing) Alignment.End else Alignment.Start

    // Asymmetric taper based on sender, no gradients
    val bubbleShape: Shape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (message.isOutgoing) 20.dp else 4.dp,
        bottomEnd = if (message.isOutgoing) 4.dp else 20.dp
    )

    var statusVisible by remember { mutableStateOf(false) }
    LaunchedEffect(message.id) {
        statusVisible = true
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .clip(bubbleShape)
                .then(
                    if (message.isOutgoing) {
                        Modifier.background(MeshAccentBlue)
                    } else {
                        Modifier
                            .background(MeshHeader.copy(alpha = 0.85f))
                            .border(1.dp, MeshBorder, bubbleShape)
                    }
                )
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Text(
                text = message.text,
                color = if (message.isOutgoing) Color.White else MeshTextPrimary,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Row(
            modifier = Modifier.padding(top = 6.dp, start = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message.timestamp,
                color = MeshMuted,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.4.sp,
                fontWeight = FontWeight.Normal
            )
            if (message.isOutgoing) {
                Spacer(modifier = Modifier.size(6.dp))
                AnimatedVisibility(
                    visible = statusVisible,
                    enter = fadeIn(animationSpec = tween(durationMillis = 500))
                ) {
                    Text(
                        text = message.deliveryStatusLabel ?: "Sent",
                        color = MeshMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Normal
                    )
                }
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
            .background(MeshBackground)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.AttachFile,
                contentDescription = "Attach",
                tint = MeshMuted
            )
        }

        OutlinedTextField(
            value = draftMessage,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            placeholder = {
                Text(
                    text = "Type a message",
                    color = MeshMuted,
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MeshSurface,
                unfocusedContainerColor = MeshSurface,
                focusedBorderColor = MeshAccentBlue,
                unfocusedBorderColor = MeshBorder,
                focusedTextColor = MeshTextPrimary,
                unfocusedTextColor = MeshTextPrimary
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() })
        )

        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(MeshAccentBlue)
                .clickable(onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = "Send",
                tint = Color.White
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConversationScreenPreview() {
    ConversationScreen(nodeId = "node-alpha", onBack = {})
}