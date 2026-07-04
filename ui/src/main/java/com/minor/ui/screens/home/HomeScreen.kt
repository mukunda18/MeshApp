package com.minor.ui.screens.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minor.ui.components.MeshTopBar
import com.minor.ui.components.OnlineIndicator
import com.minor.ui.components.ProfileAvatar
import com.minor.ui.theme.MeshGreen
import com.minor.ui.theme.MeshMuted
import com.minor.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToChats: () -> Unit,
    onNavigateToNetworkInterfaces: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            MeshTopBar(
                title = uiState.appName,
                subtitle = "Mesh status: ${uiState.meshStatusLabel}",
                trailing = {
                    Box {
                        IconButton(onClick = {
                            viewModel.refreshNetworkInterfaces()
                            menuExpanded = true
                        }) {
                            ProfileAvatar(initials = uiState.profile.avatarInitials, size = 36.dp)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(uiState.appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text("Device: ${uiState.profile.name}", style = MaterialTheme.typography.bodySmall, color = MeshMuted)
                                    }
                                },
                                onClick = {},
                                enabled = false
                            )
                            DropdownMenuItem(
                                text = { Text("Current Mesh Status: ${uiState.meshStatusLabel}") },
                                onClick = {},
                                enabled = false
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "STA + AP: ${if (uiState.isStaApSupported) "Supported" else if (uiState.isStaApLikelySupported) "Likely Supported" else "Not Supported"}"
                                    )
                                },
                                onClick = {},
                                enabled = false
                            )
                            DropdownMenuItem(
                                text = { Text("Network Interfaces: ${uiState.networkInterfaceCount}") },
                                onClick = {},
                                enabled = false
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Network Interfaces") },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateToNetworkInterfaces()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("About") },
                                onClick = {},
                                enabled = false
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            val glowSize by animateDpAsState(if (uiState.isMeshOn) 24.dp else 0.dp, label = "meshGlow")
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .shadow(elevation = glowSize, shape = CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(if (uiState.isMeshOn) MeshGreen.copy(alpha = 0.2f) else Color.LightGray)
                    .border(
                        width = 3.dp,
                        color = if (uiState.isMeshOn) MeshGreen else Color.Gray,
                        shape = CircleShape
                    )
                    .clickable { viewModel.toggleMesh() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Mesh ${uiState.meshStatusLabel}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isMeshOn) MeshGreen else MeshMuted
                    )
                    Text(
                        text = "Tap to toggle",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeshMuted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Text(
                text = uiState.connectionStatus,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 24.dp)
            )

            Button(onClick = onNavigateToChats, modifier = Modifier.padding(top = 24.dp)) {
                Text("View Nearby Nodes")
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Connected Nodes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (uiState.connectedNodes.isEmpty()) {
                    Text(
                        text = "No nodes discovered yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeshMuted
                    )
                } else {
                    uiState.connectedNodes.forEach { node ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = node.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    OnlineIndicator(
                                        isOnline = node.isOnline,
                                        modifier = Modifier.align(Alignment.CenterEnd)
                                    )
                                }
                                Text(
                                    text = node.nodeId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MeshMuted,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Text(
                                    text = "Status: ${node.status}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MeshMuted,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                                if (!node.ip.isNullOrBlank()) {
                                    Text(
                                        text = "IP: ${node.ip}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MeshMuted
                                    )
                                }
                                if (node.hopCount != null) {
                                    Text(
                                        text = "Hop Count: ${node.hopCount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MeshMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen(onNavigateToChats = {}, onNavigateToNetworkInterfaces = {})
}
