package com.meshapp.ui.screens.nearbynodes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshapp.ui.components.EmptyState
import com.meshapp.ui.components.MeshFooterNavigation
import com.meshapp.ui.components.StatusDot
import com.meshapp.ui.state.HomeNodeUiState
import com.meshapp.ui.theme.MeshBg0
import com.meshapp.ui.theme.MeshBg1
import com.meshapp.ui.theme.MeshBg3
import com.meshapp.ui.theme.MeshBorder
import com.meshapp.ui.theme.MeshGreen
import com.meshapp.ui.theme.MeshMuted
import com.meshapp.ui.theme.MeshOffline
import com.meshapp.ui.theme.MeshShapes
import com.meshapp.ui.theme.MeshSpacing
import com.meshapp.ui.theme.MeshTextPrimary
import com.meshapp.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyNodesScreen(
    viewModel: HomeViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateChats: () -> Unit,
    onNodeClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Smooth continuous rotation animation for scanning indication
    val infiniteTransition = rememberInfiniteTransition(label = "scan-transition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar-spin"
    )

    Scaffold(
        containerColor = MeshBg0,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Nearby Nodes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MeshTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MeshTextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshNetworkInterfaces() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh Nodes",
                            tint = MeshGreen,
                            modifier = Modifier.rotate(rotation)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MeshBg0)
            )
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
                    .padding(horizontal = MeshSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "0 nodes found",
                    color = MeshMuted,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(MeshSpacing.md))

                EmptyState(
                    title = "Scanning for peers",
                    subtitle = "Keep mesh running while we search for nearby nodes in your physical range.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MeshShapes.card)
                        .background(MeshBg1)
                        .border(1.dp, MeshBorder, MeshShapes.card)
                        .padding(vertical = MeshSpacing.xl, horizontal = MeshSpacing.md)
                )

                Spacer(modifier = Modifier.height(MeshSpacing.lg))

                ScanningFooterBadge()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = MeshSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MeshSpacing.sm)
            ) {
                item {
                    Text(
                        text = "${uiState.connectedNodes.size} nodes found",
                        color = MeshMuted,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = MeshSpacing.sm, bottom = MeshSpacing.xs)
                    )
                }

                items(uiState.connectedNodes, key = { it.nodeId }) { node ->
                    NearbyNodeCard(
                        node = node,
                        onClick = { onNodeClick(node.nodeId) },
                        onCall = { viewModel.dial(node.nodeId) }
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = MeshSpacing.xl),
                        contentAlignment = Alignment.Center
                    ) {
                        ScanningFooterBadge()
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyNodeCard(
    node: HomeNodeUiState,
    onClick: () -> Unit,
    onCall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MeshShapes.card,
        colors = CardDefaults.cardColors(containerColor = MeshBg1),
        border = androidx.compose.foundation.BorderStroke(1.dp, MeshBorder)
    ) {
        Row(
            modifier = Modifier.padding(MeshSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Avatar with dynamic hash styling
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(avatarBg(node.name))
                    .border(2.dp, avatarBorder(node.name), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = node.avatarInitials.take(1).uppercase(),
                    color = MeshGreen,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Node Details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MeshSpacing.md)
            ) {
                Text(
                    text = node.name,
                    color = MeshTextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = node.ip ?: "Unknown address",
                    color = MeshMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Status, Call, and Signal Metrics
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (node.isOnline) {
                        IconButton(
                            onClick = onCall,
                            modifier = Modifier
                                .size(36.dp)
                                .background(MeshBg3, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call Node",
                                tint = MeshGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(MeshSpacing.xs))
                    }

                    StatusDot(isOnline = node.isOnline, size = 8.dp)
                    Text(
                        text = if (node.isOnline) " Online" else " Offline",
                        color = if (node.isOnline) MeshGreen else MeshOffline,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(MeshSpacing.xs))
                SignalBars(level = signalLevel(node))
            }
        }
    }
}

@Composable
private fun SignalBars(level: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(4) { index ->
            val height = (index + 1) * 5
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
                    .background(if (index < level) MeshGreen else MeshBg3)
            )
        }
    }
}

@Composable
private fun ScanningFooterBadge() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse-alpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "SCANNING FOR NEW PEERS...",
            color = MeshGreen,
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.graphicsLayer { this.alpha = alpha }
        )
        Text(
            text = "MESH",
            color = MeshGreen.copy(alpha = 0.15f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 2.dp)
        )
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
        Color(0xFF16302A),
        Color(0xFF163531),
        Color(0xFF1E2E2D),
        Color(0xFF262B2A)
    )
    return colors[(name.hashCode().ushr(1)) % colors.size]
}

private fun avatarBorder(name: String): Color {
    val colors = listOf(
        Color(0xFF3ECF8E),
        Color(0xFF39B3A6),
        Color(0xFF74AFA3),
        Color(0xFF7B877F)
    )
    return colors[(name.hashCode().ushr(1)) % colors.size]
}