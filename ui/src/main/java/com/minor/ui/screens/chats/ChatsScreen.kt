package com.minor.ui.screens.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minor.ui.components.EmptyState
import com.minor.ui.components.MeshTopBar
import com.minor.ui.components.NodeCard
import com.minor.ui.state.NodeCardState
import com.minor.ui.viewmodel.ChatsUiState
import com.minor.ui.viewmodel.ChatsViewModel

@Composable
fun ChatsScreen(viewModel: ChatsViewModel = viewModel(), onNodeClick: (NodeCardState) -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = { MeshTopBar(title = "Chats", subtitle = "Conversations") }) { paddingValues ->
        if (uiState.nodes.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.Center
            ) {
                EmptyState(title = "No conversations yet", subtitle = "Messages will appear here")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.nodes, key = { it.id }) { node ->
                    NodeCard(node = node, onClick = { onNodeClick(node) })
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatsScreenPreview() {
    ChatsScreen(viewModel = viewModel(), onNodeClick = {})
}
