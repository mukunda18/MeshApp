package com.meshapp.ui.screens.chats

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshapp.ui.components.ProfileAvatar
import com.meshapp.ui.state.NodeCardState
import com.meshapp.ui.theme.MeshGreen
import com.meshapp.ui.theme.MeshMuted
import com.meshapp.ui.viewmodel.ChatsViewModel

@Composable
fun ChatsScreen(viewModel: ChatsViewModel = viewModel(), onNodeClick: (NodeCardState) -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatsBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ChatsTopBar()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chats",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFF2F3F4),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "NETWORK SECURE",
                    color = MeshGreen,
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.nodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No conversations yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MeshMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.nodes) { node ->
                        ChatListItem(
                            node = node,
                            onClick = { onNodeClick(node) }
                        )
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            listOf(
                                                Color.Transparent,
                                                MeshGreen.copy(alpha = 0.22f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            Text(
                                text = "END TO END ENCRYPTED MESH",
                                color = Color(0xFF51575B),
                                style = MaterialTheme.typography.labelMedium,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private val ChatsBackground = Color(0xFF090B0C)
private val ChatsTopBarColor = Color(0xFF111314)

@Composable
private fun ChatsTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(ChatsTopBarColor)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MeshGlyph(modifier = Modifier.size(30.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "MeshApp",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFFF2F3F4),
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search",
                tint = Color(0xFFC7CCCF)
            )
        }
        Box {
            ProfileAvatar(initials = "P", size = 36.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MeshGreen)
                    .border(1.5.dp, ChatsTopBarColor, CircleShape)
            )
        }
    }
}

@Composable
private fun ChatListItem(node: NodeCardState, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "unread-glow")
    val glowPulse by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badge-pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0E1012))
            .border(1.dp, Color(0xFF1A2940), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF23262A)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = node.avatarInitials.take(1),
                color = avatarColorForName(node.name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFF2F3F4),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = node.lastMessageTimestamp ?: "",
                    color = Color(0xFF8A9196),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = node.lastMessagePreview ?: "No messages yet",
                    color = Color(0xFFB0B6BA),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (node.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .size(28.dp)
                            .drawBehind {
                                drawCircle(
                                    color = MeshGreen.copy(alpha = glowPulse),
                                    radius = size.minDimension / 1.25f,
                                    style = Stroke(width = 0f)
                                )
                            }
                            .clip(CircleShape)
                            .background(MeshGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = node.unreadCount.toString(),
                            color = Color(0xFF052A12),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun avatarColorForName(name: String): Color {
    val palette = listOf(
        Color(0xFF4BE277),
        Color(0xFF58DCC5),
        Color(0xFFA7E39B),
        Color(0xFF7BE4DE)
    )
    return palette[(name.hashCode().ushr(1)) % palette.size]
}

@Composable
private fun MeshGlyph(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawBehind {
            val lineColor = MeshGreen
            val dotColor = MeshGreen
            val r = 2.4.dp.toPx()
            val x1 = size.width * 0.22f
            val x2 = size.width * 0.5f
            val x3 = size.width * 0.78f
            val y1 = size.height * 0.24f
            val y2 = size.height * 0.5f
            val y3 = size.height * 0.76f

            drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(x1, y1), end = androidx.compose.ui.geometry.Offset(x2, y2), strokeWidth = 1.8.dp.toPx())
            drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(x2, y2), end = androidx.compose.ui.geometry.Offset(x3, y1), strokeWidth = 1.8.dp.toPx())
            drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(x1, y3), end = androidx.compose.ui.geometry.Offset(x2, y2), strokeWidth = 1.8.dp.toPx())
            drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(x2, y2), end = androidx.compose.ui.geometry.Offset(x3, y3), strokeWidth = 1.8.dp.toPx())
            drawCircle(dotColor, r, center = androidx.compose.ui.geometry.Offset(x1, y1))
            drawCircle(dotColor, r, center = androidx.compose.ui.geometry.Offset(x3, y1))
            drawCircle(dotColor, r, center = androidx.compose.ui.geometry.Offset(x2, y2))
            drawCircle(dotColor, r, center = androidx.compose.ui.geometry.Offset(x1, y3))
            drawCircle(dotColor, r, center = androidx.compose.ui.geometry.Offset(x3, y3))
        }
    )
}

@Preview(showBackground = true)
@Composable
fun ChatsScreenPreview() {
    ChatsScreen(viewModel = viewModel(), onNodeClick = {})
}
