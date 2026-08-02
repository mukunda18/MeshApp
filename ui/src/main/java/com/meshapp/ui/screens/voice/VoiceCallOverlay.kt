package com.meshapp.ui.screens.voice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshapp.voice.CallState

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B0D).copy(alpha = 0.95f))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val peerLabel = when (state) {
                is CallState.Dialing -> state.peerNodeId.toString().take(8)
                is CallState.Ringing -> state.peerNodeId.toString().take(8)
                is CallState.Active -> state.peerNodeId.toString().take(8)
                is CallState.Ended -> state.peerNodeId.toString().take(8)
                else -> ""
            }

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E2123)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peerLabel.take(1).uppercase(),
                    color = Color(0xFF29DC67),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = peerLabel,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            val statusText = when (state) {
                is CallState.Dialing -> "Calling..."
                is CallState.Ringing -> "Incoming Call"
                is CallState.Active -> "In Call"
                is CallState.Ended -> "Call Ended"
                else -> ""
            }

            Text(
                text = statusText,
                color = Color(0xFFB5B9BD),
                style = MaterialTheme.typography.bodyLarge,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(64.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                when (state) {
                    is CallState.Dialing -> {
                        CallButton(
                            icon = Icons.Default.CallEnd,
                            color = Color(0xFFDC2929),
                            onClick = onCancel
                        )
                    }
                    is CallState.Ringing -> {
                        CallButton(
                            icon = Icons.Default.Call,
                            color = Color(0xFF29DC67),
                            onClick = onAccept
                        )
                        CallButton(
                            icon = Icons.Default.CallEnd,
                            color = Color(0xFFDC2929),
                            onClick = onReject
                        )
                    }
                    is CallState.Active -> {
                        CallButton(
                            icon = Icons.Default.CallEnd,
                            color = Color(0xFFDC2929),
                            onClick = onHangup
                        )
                    }
                    is CallState.Ended -> {
                        // Just wait for auto-dismiss
                    }
                    else -> {}
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            TextButton(onClick = onMinimize) {
                Text("Minimize", color = Color(0xFFB5B9BD))
            }
        }
    }
}

@Composable
private fun CallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = color,
            contentColor = Color.White
        )
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(32.dp))
    }
}
