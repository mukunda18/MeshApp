package com.minor.ui.screens.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minor.ui.components.ChatBubble
import com.minor.ui.components.MeshTopBar
import com.minor.ui.components.OnlineIndicator
import com.minor.ui.components.ProfileAvatar
import com.minor.ui.fake.FakeDataProvider
import com.minor.ui.state.NodeCardState
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
        val node = FakeDataProvider.nodes.firstOrNull { it.id == nodeId }
        if (node != null) {
            viewModel.initialize(node)
        }
    }

    Scaffold(
        topBar = {
            MeshTopBar(
                title = uiState.node.name.ifBlank { "Conversation" },
                subtitle = uiState.node.id.ifBlank { null },
                onBack = onBack,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileAvatar(initials = uiState.node.avatarInitials.ifBlank { "?" }, size = 32.dp)
                        OnlineIndicator(isOnline = uiState.node.isOnline)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    ChatBubble(message = message)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draftMessage,
                    onValueChange = { draftMessage = it },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Type a message") }
                )
                Button(
                    onClick = {
                        viewModel.sendMessage(draftMessage)
                        draftMessage = ""
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Send")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConversationScreenPreview() {
    ConversationScreen(nodeId = "node-alpha", onBack = {})
}
