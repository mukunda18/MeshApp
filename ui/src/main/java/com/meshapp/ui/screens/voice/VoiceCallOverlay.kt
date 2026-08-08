package com.meshapp.ui.screens.voice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshapp.ui.theme.MeshBg0
import com.meshapp.ui.theme.MeshBg2
import com.meshapp.ui.theme.MeshBg3
import com.meshapp.ui.theme.MeshDanger
import com.meshapp.ui.theme.MeshGreen
import com.meshapp.ui.theme.MeshGreenOnAccent
import com.meshapp.ui.theme.MeshMuted
import com.meshapp.ui.theme.MeshShapes
import com.meshapp.ui.theme.MeshTextPrimary
import com.meshapp.voice.CallState
import kotlinx.coroutines.delay

@Composable
fun VoiceCallOverlay(
    state: CallState,
    isMinimized: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    onHangup: () -> Unit,
    onMinimize: () -> Unit
) {
    if (state is CallState.Idle || isMinimized) return

    BackHandler {
        onMinimize()
    }

    val peerLabel = when (state) {
        is CallState.Dialing -> state.peerNodeId.toString().take(8)
        is CallState.Ringing -> state.peerNodeId.toString().take(8)
        is CallState.Active -> state.peerNodeId.toString().take(8)
        is CallState.Ended -> state.peerNodeId.toString().take(8)
        else -> ""
    }

    val isLive = state is CallState.Active || state is CallState.Ringing || state is CallState.Dialing

    // Live call duration timer
    var activeCallDurationSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state) {
        if (state is CallState.Active) {
            activeCallDurationSeconds = 0L
            while (true) {
                delay(1000L)
                activeCallDurationSeconds++
            }
        }
    }

    val statusText = when (state) {
        is CallState.Dialing -> "Calling..."
        is CallState.Ringing -> "Incoming Call"
        is CallState.Active -> {
            val mins = activeCallDurationSeconds / 60
            val secs = activeCallDurationSeconds % 60
            "%02d:%02d".format(mins, secs)
        }
        is CallState.Ended -> "Call Ended"
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeshBg0.copy(alpha = 0.98f))
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        // Top Minimize Bar Action
        IconButton(
            onClick = onMinimize,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Minimize Call",
                tint = MeshMuted,
                modifier = Modifier.size(32.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Pulse Avatar Container
            Box(contentAlignment = Alignment.Center) {
                if (isLive) {
                    val transition = rememberInfiniteTransition(label = "call-pulse")
                    val pulseScale by transition.animateFloat(
                        initialValue = 1.0f,
                        targetValue = 1.25f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseScale"
                    )

                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .drawBehind {
                                // Outer Ring
                                drawCircle(
                                    color = MeshGreen.copy(alpha = 0.10f),
                                    radius = (size.minDimension / 2f) * pulseScale * 1.12f,
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                                // Inner Ring
                                drawCircle(
                                    color = MeshGreen.copy(alpha = 0.22f),
                                    radius = (size.minDimension / 2f) * pulseScale,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                    )
                }

                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(MeshBg2),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = peerLabel.take(1).uppercase(),
                        color = MeshGreen,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = peerLabel,
                color = MeshTextPrimary,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = MeshShapes.chip,
                color = if (state is CallState.Active) MeshGreen.copy(alpha = 0.15f) else MeshBg3
            ) {
                Text(
                    text = statusText,
                    color = if (state is CallState.Active) MeshGreen else MeshMuted,
                    style = MaterialTheme.typography.bodyLarge,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(64.dp))

            // Action Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (state) {
                    is CallState.Dialing -> {
                        CallActionButton(
                            icon = Icons.Default.CallEnd,
                            label = "Cancel",
                            color = MeshDanger,
                            onClick = onCancel
                        )
                    }
                    is CallState.Ringing -> {
                        CallActionButton(
                            icon = Icons.Default.CallEnd,
                            label = "Decline",
                            color = MeshDanger,
                            onClick = onReject
                        )
                        CallActionButton(
                            icon = Icons.Default.Call,
                            label = "Answer",
                            color = MeshGreen,
                            onClick = onAccept
                        )
                    }
                    is CallState.Active -> {
                        CallActionButton(
                            icon = Icons.Default.CallEnd,
                            label = "End Call",
                            color = MeshDanger,
                            onClick = onHangup
                        )
                    }
                    is CallState.Ended -> {
                        // Auto-dismiss state
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            TextButton(
                onClick = onMinimize,
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = "Minimize Overlay",
                    color = MeshMuted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CallActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = color,
                contentColor = MeshGreenOnAccent
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = MeshTextPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}