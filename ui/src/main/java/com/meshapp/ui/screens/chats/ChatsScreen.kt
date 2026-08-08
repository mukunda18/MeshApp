package com.meshapp.ui.screens.chats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshapp.ui.components.EmptyState
import com.meshapp.ui.components.NodeCard
import com.meshapp.ui.state.NodeCardState
import com.meshapp.ui.theme.MeshBg2
import com.meshapp.ui.theme.MeshBg3
import com.meshapp.ui.theme.MeshGreen
import com.meshapp.ui.theme.MeshMuted
import com.meshapp.ui.theme.MeshShapes
import com.meshapp.ui.theme.MeshSpacing
import com.meshapp.ui.theme.MeshTextPrimary
import com.meshapp.ui.viewmodel.ChatsViewModel

@Composable
fun ChatsScreen(
    viewModel: ChatsViewModel,
    onNodeClick: (NodeCardState) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    val filteredNodes = if (searchQuery.isBlank()) {
        uiState.nodes
    } else {
        uiState.nodes.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val onlineNodes = filteredNodes.filter { it.isOnline }
    val offlineNodes = filteredNodes.filter { !it.isOnline }
    val showSections = onlineNodes.isNotEmpty() && offlineNodes.isNotEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        ChatsSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MeshSpacing.md, vertical = MeshSpacing.sm)
        )

        if (filteredNodes.isEmpty()) {
            EmptyState(
                title = if (searchQuery.isBlank()) "No conversations yet" else "No matches",
                subtitle = if (searchQuery.isBlank())
                    "Start chatting once nodes are nearby."
                else
                    "No nodes match \"$searchQuery\".",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(MeshSpacing.md)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = MeshSpacing.md, vertical = MeshSpacing.xs)
            ) {
                if (showSections) {
                    item(key = "section_online") {
                        SectionLabel(text = "ONLINE — ${onlineNodes.size}")
                    }
                }
                items(items = onlineNodes, key = { it.id }) { node ->
                    NodeCard(
                        node = node,
                        onClick = { onNodeClick(node) },
                        modifier = Modifier.padding(bottom = MeshSpacing.sm)
                    )
                }

                if (showSections) {
                    item(key = "section_offline") {
                        SectionLabel(text = "OFFLINE — ${offlineNodes.size}")
                    }
                }
                items(items = offlineNodes, key = { it.id }) { node ->
                    NodeCard(
                        node = node,
                        onClick = { onNodeClick(node) },
                        modifier = Modifier.padding(bottom = MeshSpacing.sm)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MeshGreen,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = MeshSpacing.sm, bottom = MeshSpacing.xs)
    )
}

@Composable
private fun ChatsSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text("Search conversations...", color = MeshMuted) },
        singleLine = true,
        shape = MeshShapes.input,
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MeshMuted)
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = MeshMuted)
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MeshBg2,
            unfocusedContainerColor = MeshBg2,
            focusedBorderColor = MeshGreen.copy(alpha = 0.5f),
            unfocusedBorderColor = MeshBg3,
            focusedTextColor = MeshTextPrimary,
            unfocusedTextColor = MeshTextPrimary,
            cursorColor = MeshGreen
        )
    )
}