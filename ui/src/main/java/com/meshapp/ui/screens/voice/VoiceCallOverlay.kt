package com.meshapp.ui.screens.voice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Spacer
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
import com.meshapp.ui.components.MeshMotion
import com.meshapp.ui.theme.MeshBg0
import com.meshapp.ui.theme.MeshBg2
import com.meshapp.ui.theme.MeshBg3
import com.meshapp.ui.theme.MeshDanger
import com.meshapp.ui.theme.MeshGreen
import com.meshapp.ui.theme.MeshGreenMuted
import com.meshapp.ui.theme.MeshGreenOnAccent
import com.meshapp.ui.theme.MeshMuted
import com.meshapp.ui.theme.MeshShapes
import com.meshapp.ui.theme.MeshSpacing
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

    val isLive =
        state is CallState.Active ||
                state is CallState.Ringing ||
                state is CallState.Dialing

    var activeCallDurationSeconds by remember {
        mutableLongStateOf(0L)
    }

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
        is CallState.Dialing -> "Calling"

        is CallState.Ringing -> "Incoming call"

        is CallState.Active -> {
            val mins = activeCallDurationSeconds / 60
            val secs = activeCallDurationSeconds % 60
            "%02d:%02d".format(mins, secs)
        }

        is CallState.Ended -> "Call ended"

        else -> ""
    }

    val statusColor =
        if (state is CallState.Active) MeshGreen else MeshMuted

    val statusChipColor =
        if (state is CallState.Active) MeshGreenMuted else MeshBg3

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeshBg0)
            .statusBarsPadding()
            .padding(MeshSpacing.md)
    ) {
        IconButton(
            onClick = onMinimize,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Minimize call",
                tint = MeshMuted,
                modifier = Modifier.size(28.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CallAvatar(
                peerLabel = peerLabel,
                isLive = isLive
            )

            Spacer(modifier = Modifier.height(MeshSpacing.lg))

            Text(
                text = peerLabel,
                color = MeshTextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(MeshSpacing.sm))

            AnimatedContent(
                targetState = statusText,
                transitionSpec = {
                    fadeIn(
                        tween(MeshMotion.medium)
                    ) togetherWith fadeOut(
                        tween(MeshMotion.fast)
                    )
                },
                label = "call-status"
            ) { text ->
                Surface(
                    shape = MeshShapes.chip,
                    color = statusChipColor
                ) {
                    Text(
                        text = text,
                        color = statusColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(
                            horizontal = MeshSpacing.md,
                            vertical = MeshSpacing.xs
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(MeshSpacing.xxl))

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
                            label = "End call",
                            color = MeshDanger,
                            onClick = onHangup
                        )
                    }

                    is CallState.Ended -> Unit

                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun CallAvatar(
    peerLabel: String,
    isLive: Boolean
) {
    Box(
        contentAlignment = Alignment.Center
    ) {
        if (isLive) {
            val transition = rememberInfiniteTransition(
                label = "call-pulse"
            )

            val pulseScale by transition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        1800,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse-scale"
            )

            Box(
                modifier = Modifier
                    .size(152.dp)
                    .drawBehind {
                        drawCircle(
                            color = MeshGreen.copy(alpha = 0.16f),
                            radius =
                                (size.minDimension / 2f) * pulseScale,
                            style = Stroke(
                                width = 1.5.dp.toPx()
                            )
                        )
                    }
            )
        }

        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(CircleShape)
                .background(MeshBg2),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = peerLabel
                    .take(1)
                    .uppercase(),
                color = MeshGreen,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold
            )
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
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = color,
                contentColor = MeshGreenOnAccent
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(MeshSpacing.xs))

        Text(
            text = label,
            color = MeshTextPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}