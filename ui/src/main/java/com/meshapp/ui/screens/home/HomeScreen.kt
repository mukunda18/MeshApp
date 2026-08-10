package com.meshapp.ui.screens.home

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshapp.ui.components.StatusDot
import com.meshapp.ui.theme.MeshBg1
import com.meshapp.ui.theme.MeshBg2
import com.meshapp.ui.theme.MeshBorder
import com.meshapp.ui.theme.MeshDanger
import com.meshapp.ui.theme.MeshGreen
import com.meshapp.ui.theme.MeshGreenOnAccent
import com.meshapp.ui.theme.MeshMuted
import com.meshapp.ui.theme.MeshShapes
import com.meshapp.ui.theme.MeshSpacing
import com.meshapp.ui.theme.MeshTextPrimary
import com.meshapp.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToNearbyNodes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MeshSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(MeshSpacing.lg))

        MeshPowerControl(
            isOn = uiState.isMeshOn,
            onToggle = { viewModel.toggleMesh() }
        )

        Spacer(modifier = Modifier.height(MeshSpacing.md))

        Text(
            text = if (uiState.isMeshOn) "MESH ACTIVE" else "MESH OFF",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (uiState.isMeshOn) MeshGreen else MeshMuted,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = uiState.connectionStatus,
            style = MaterialTheme.typography.bodyMedium,
            color = MeshMuted
        )

        if (uiState.isMeshOn) {
            Spacer(modifier = Modifier.height(MeshSpacing.md))
            Surface(
                onClick = { viewModel.toggleVoiceSimulation() },
                shape = MeshShapes.chip,
                color = if (uiState.isVoiceSimActive) MeshDanger else MeshBg2,
                modifier = Modifier.border(
                    1.dp, 
                    if (uiState.isVoiceSimActive) MeshDanger else MeshBorder, 
                    MeshShapes.chip
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = MeshSpacing.md, vertical = MeshSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusDot(
                        isOnline = uiState.isVoiceSimActive,
                        size = 8.dp
                    )
                    Spacer(modifier = Modifier.width(MeshSpacing.xs))
                    Text(
                        text = if (uiState.isVoiceSimActive) "STOP LOOPBACK" else "START LOOPBACK",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isVoiceSimActive) MeshGreenOnAccent else MeshTextPrimary
                    )
                }
            }
            
            if (uiState.isVoiceSimActive) {
                Text(
                    text = "Audio is playing back locally with 3s delay",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeshDanger,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(MeshSpacing.xl))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MeshSpacing.sm)
        ) {
            StatCard(
                label = "PEERS",
                value = uiState.connectedNodes.count { it.isOnline }.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "INTERFACES",
                value = uiState.networkInterfaceCount.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(MeshSpacing.lg))

        TextButton(
            onClick = onNavigateToNearbyNodes,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View Nearby Nodes", color = MeshGreen)
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(MeshSpacing.md))
    }
}

@Composable
private fun MeshPowerControl(
    isOn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(180.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isOn) {
            val transition = rememberInfiniteTransition(label = "mesh-power-pulse")
            val pulse by transition.animateFloat(
                initialValue = 0.92f,
                targetValue = 1.18f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "mesh-power-pulse-scale"
            )

            Box(
                modifier = Modifier
                    .size(180.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                        alpha = 0.16f
                    }
                    .clip(CircleShape)
                    .background(MeshGreen)
            )
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .graphicsLayer {
                        scaleX = pulse * 0.94f
                        scaleY = pulse * 0.94f
                        alpha = 0.20f
                    }
                    .clip(CircleShape)
                    .background(MeshGreen)
            )
        }

        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(CircleShape)
                .background(if (isOn) MeshGreen else MeshBg2)
                .border(2.dp, if (isOn) MeshGreen else MeshBorder, CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = if (isOn) "Turn mesh off" else "Turn mesh on",
                tint = if (isOn) MeshGreenOnAccent else MeshMuted,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MeshShapes.cardSmall,
        color = MeshBg1,
        modifier = modifier.border(1.dp, MeshBorder, MeshShapes.cardSmall)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MeshSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MeshTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MeshMuted,
                letterSpacing = 1.sp
            )
        }
    }
}
