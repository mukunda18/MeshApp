package com.meshapp.ui.screens.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshapp.ui.components.EmptyState
import com.meshapp.ui.components.NodeCard
import com.meshapp.ui.state.NodeCardState
import com.meshapp.ui.theme.MeshBg2
import com.meshapp.ui.theme.MeshGreen
import com.meshapp.ui.theme.MeshGreenMuted
import com.meshapp.ui.theme.MeshMuted
import com.meshapp.ui.theme.MeshShapes
import com.meshapp.ui.theme.MeshSpacing
import com.meshapp.ui.theme.MeshTextPrimary
import com.meshapp.ui.theme.MeshTextSecondary
import com.meshapp.ui.viewmodel.ChatsViewModel

@Composable
fun ChatsScreen(
    viewModel: ChatsViewModel,
    onNodeClick: (NodeCardState) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val query = searchQuery.trim()

    val filteredNodes = if (query.isBlank()) {
        uiState.nodes
    } else {
        uiState.nodes.filter { node ->
            node.name.contains(query, ignoreCase = true) ||
                    node.lastMessagePreview.orEmpty()
                        .contains(query, ignoreCase = true)
        }
    }

    val onlineNodes = filteredNodes.filter { it.isOnline }
    val offlineNodes = filteredNodes.filter { !it.isOnline }

    val showSections =
        onlineNodes.isNotEmpty() && offlineNodes.isNotEmpty()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        ChatsSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MeshSpacing.md,
                    vertical = MeshSpacing.sm
                )
        )

        if (filteredNodes.isEmpty()) {
            EmptyState(
                title = if (query.isBlank()) {
                    "No conversations yet"
                } else {
                    "No matches found"
                },
                subtitle = if (query.isBlank()) {
                    "Start chatting once nodes are nearby."
                } else {
                    "Nothing matches \"$query\". Try a different search."
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(MeshSpacing.lg)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = MeshSpacing.md,
                    vertical = MeshSpacing.xs
                ),
                verticalArrangement = Arrangement.spacedBy(MeshSpacing.xs)
            ) {
                if (showSections) {
                    item(key = "section_online") {
                        SectionLabel(
                            text = "Online",
                            count = onlineNodes.size
                        )
                    }
                }

                items(
                    items = onlineNodes,
                    key = { it.id }
                ) { node ->
                    NodeCard(
                        node = node,
                        onClick = { onNodeClick(node) }
                    )
                }

                if (showSections) {
                    item(key = "section_offline") {
                        SectionLabel(
                            text = "Offline",
                            count = offlineNodes.size
                        )
                    }
                }

                items(
                    items = offlineNodes,
                    key = { it.id }
                ) { node ->
                    NodeCard(
                        node = node,
                        onClick = { onNodeClick(node) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = MeshSpacing.md,
                bottom = MeshSpacing.xs
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MeshSpacing.xs)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MeshTextSecondary,
            fontWeight = FontWeight.SemiBold
        )

        Surface(
            shape = CircleShape,
            color = MeshGreenMuted
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MeshGreen,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(
                    horizontal = 7.dp,
                    vertical = 1.dp
                )
            )
        }
    }
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
        placeholder = {
            Text(
                text = "Search conversations",
                color = MeshMuted
            )
        },
        singleLine = true,
        shape = MeshShapes.input,
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = MeshMuted
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange("") }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = MeshMuted
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MeshBg2,
            unfocusedContainerColor = MeshBg2,
            focusedBorderColor = MeshGreen.copy(alpha = 0.4f),
            unfocusedBorderColor = MeshBg2,
            focusedTextColor = MeshTextPrimary,
            unfocusedTextColor = MeshTextPrimary,
            cursorColor = MeshGreen
        )
    )
}