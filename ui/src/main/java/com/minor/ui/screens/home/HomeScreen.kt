package com.minor.ui.screens.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minor.ui.components.MeshTopBar
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

    Scaffold(
        topBar = {
            MeshTopBar(
                title = "Mesh App",
                subtitle = "Presentation layer",
                trailing = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            ProfileAvatar(initials = uiState.profile.avatarInitials, size = 36.dp)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(uiState.profile.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text("Connected User", style = MaterialTheme.typography.bodySmall, color = MeshMuted)
                                    }
                                },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val glowSize by animateDpAsState(if (uiState.isMeshOn) 24.dp else 0.dp)
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .shadow(elevation = if (uiState.isMeshOn) 24.dp else 0.dp, shape = CircleShape, clip = false)
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
                        text = if (uiState.isMeshOn) "Mesh is ON" else "Mesh is OFF",
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
                text = "${uiState.profile.name} is connected to the mesh",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 24.dp)
            )

            Button(onClick = onNavigateToChats, modifier = Modifier.padding(top = 24.dp)) {
                Text("View Nearby Nodes")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen(onNavigateToChats = {}, onNavigateToNetworkInterfaces = {})
}
