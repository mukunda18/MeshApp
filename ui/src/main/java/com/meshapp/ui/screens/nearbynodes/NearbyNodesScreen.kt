package com.meshapp.ui.screens.nearbynodes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshapp.ui.components.MeshFooterNavigation
import com.meshapp.ui.state.HomeNodeUiState
import com.meshapp.ui.theme.MeshGreen
import com.meshapp.ui.theme.MeshMuted
import com.meshapp.ui.viewmodel.HomeViewModel

@Composable
fun NearbyNodesScreen(
    viewModel: HomeViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateChats: () -> Unit,
    onNodeClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = NearbyBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NearbyBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFFE5E8EA)
                    )
                }
                Text(
                    text = "Nearby Nodes",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFF2F3F4),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = MeshGreen
                    )
                }
            }
        },
        bottomBar = {
            MeshFooterNavigation(
                currentRoute = "nearby",
                onHome = onNavigateHome,
                onChats = onNavigateChats
            )
        }
    ) { paddingValues ->
        if (uiState.connectedNodes.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "0 nodes found",
                    color = Color(0xFF8A9196),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 24.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0F1113))
                        .border(1.dp, Color(0xFF1C2B3F), RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "No nearby mesh peers discovered yet. Keep mesh running while scanning for new peers.",
                        color = Color(0xFFB8BEBA),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Text(
                    text = "SCANNING FOR NEW PEERS...",
                    color = MeshGreen.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 24.dp)
                )
                Text(
                    text = "MESH",
                    color = MeshGreen.copy(alpha = 0.22f),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "${uiState.connectedNodes.size} nodes found",
                        color = Color(0xFF8A9196),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )
                }

                items(uiState.connectedNodes, key = { it.nodeId }) { node ->
                    NearbyNodeCard(node = node, onClick = { onNodeClick(node.nodeId) })
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "SCANNING FOR NEW PEERS...",
                            color = MeshGreen.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "MESH",
                            color = MeshGreen.copy(alpha = 0.22f),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyNodeCard(node: HomeNodeUiState, onClick: () -> Unit) {
    SurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(avatarBg(node.name))
                    .border(2.dp, avatarBorder(node.name), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = node.avatarInitials.take(1),
                    color = MeshGreen,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = node.name,
                    color = Color(0xFFF2F3F4),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = node.ip ?: "Unknown address",
                    color = Color(0xFFB0B6BA),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (node.isOnline) MeshGreen else MeshMuted)
                    )
                    Text(
                        text = if (node.isOnline) " Online" else " Offline",
                        color = if (node.isOnline) MeshGreen else MeshMuted,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                SignalBars(level = signalLevel(node), modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun SignalBars(level: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(4) { index ->
            val height = (index + 1) * 6
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(if (index < level) MeshGreen else Color(0xFF58655F))
            )
        }
    }
}

@Composable
private fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0E1012))
            .border(1.dp, Color(0xFF1D2D42), RoundedCornerShape(18.dp))
    ) {
        content()
    }
}

private fun signalLevel(node: HomeNodeUiState): Int {
    val hop = node.hopCount ?: return if (node.isOnline) 4 else 1
    return when {
        hop <= 1 -> 4
        hop == 2 -> 3
        hop == 3 -> 2
        else -> 1
    }
}

private fun avatarBg(name: String): Color {
    val colors = listOf(
        Color(0xFF123B23),
        Color(0xFF003E35),
        Color(0xFF243E3A),
        Color(0xFF2E3330)
    )
    return colors[(name.hashCode().ushr(1)) % colors.size]
}

private fun avatarBorder(name: String): Color {
    val colors = listOf(
        Color(0xFF29C967),
        Color(0xFF00A78E),
        Color(0xFF74AFA3),
        Color(0xFF7B877F)
    )
    return colors[(name.hashCode().ushr(1)) % colors.size]
}

private val NearbyBg = Color(0xFF090B0D)
