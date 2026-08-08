package com.meshapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SignalWifiStatusbarConnectedNoInternet4
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.meshapp.ui.state.ConversationMessageUiState
import com.meshapp.ui.state.NodeCardState
import com.meshapp.ui.theme.MeshBg2
import com.meshapp.ui.theme.MeshBg3
import com.meshapp.ui.theme.MeshBorder
import com.meshapp.ui.theme.MeshBubbleInbound
import com.meshapp.ui.theme.MeshBubbleInboundBorder
import com.meshapp.ui.theme.MeshBubbleOutbound
import com.meshapp.ui.theme.MeshBubbleOutboundBorder
import com.meshapp.ui.theme.MeshGreen
import com.meshapp.ui.theme.MeshGreenOnAccent
import com.meshapp.ui.theme.MeshMuted
import com.meshapp.ui.theme.MeshOffline
import com.meshapp.ui.theme.MeshRadius
import com.meshapp.ui.theme.MeshShapes
import com.meshapp.ui.theme.MeshSpacing
import com.meshapp.ui.theme.MeshTextPrimary

/**
 * A circular initials avatar, used for both self and peer identities across the app.
 */
@Composable
fun ProfileAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    containerColor: Color = MeshGreen
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = MeshGreenOnAccent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/**
 * Small dot + label used to show a peer's live connection state. A soft pulse on the
 * dot communicates "actively broadcasting" without being distracting.
 */
@Composable
fun OnlineIndicator(isOnline: Boolean, modifier: Modifier = Modifier) {
    val color by animateColorAsState(if (isOnline) MeshGreen else MeshOffline, label = "status-color")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        StatusDot(isOnline = isOnline)
        Spacer(modifier = Modifier.size(6.dp))
        Text(if (isOnline) "Online" else "Offline", style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/**
 * The animated presence dot on its own, reused by avatars, list rows, and headers
 * so every "is this peer reachable right now" signal looks identical.
 */
@Composable
fun StatusDot(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp
) {
    val color by animateColorAsState(
        targetValue = if (isOnline) MeshGreen else MeshOffline,
        label = "dot-color"
    )

    if (isOnline) {
        val transition = rememberInfiniteTransition(label = "presence-pulse")
        val pulse by transition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "presence-pulse-scale"
        )

        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            // Pulse outer halo using graphicsLayer for zero layout invalidation
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                        alpha = 0.22f
                    }
                    .clip(CircleShape)
                    .background(color)
            )
            // Solid center dot
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/**
 * A small pill-shaped status/label chip, e.g. "MESH ONLINE", "NETWORK SECURE",
 * or per-node signal labels. Consolidates what used to be one-off Surface+Text blocks.
 */
@Composable
fun MeshStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MeshGreen,
    filled: Boolean = false
) {
    Surface(
        shape = MeshShapes.chip,
        color = if (filled) color else color.copy(alpha = 0.14f),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = if (filled) MeshGreenOnAccent else color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshTopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, color = MeshTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MeshMuted)
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MeshTextPrimary)
                }
            }
        },
        actions = { trailing() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

/**
 * Shared bottom navigation chrome. [MeshFooterNavigation] and [BottomNavigationBar] both
 * delegate here so every screen's nav bar matches pixel-for-pixel; only the item set differs.
 */
@Composable
private fun MeshNavigationBar(
    currentRoute: String,
    onHome: () -> Unit,
    onChats: () -> Unit,
    onProfile: (() -> Unit)? = null
) {
    NavigationBar(
        containerColor = MeshBg2,
        tonalElevation = 0.dp
    ) {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MeshGreen,
            selectedTextColor = MeshGreen,
            indicatorColor = MeshGreen.copy(alpha = 0.14f),
            unselectedIconColor = MeshMuted,
            unselectedTextColor = MeshMuted
        )
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = onHome,
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Home") },
            colors = colors
        )
        NavigationBarItem(
            selected = currentRoute == "chats",
            onClick = onChats,
            icon = { Icon(Icons.Filled.ChatBubble, contentDescription = null) },
            label = { Text("Chats") },
            colors = colors
        )
        if (onProfile != null) {
            NavigationBarItem(
                selected = currentRoute == "profile",
                onClick = onProfile,
                icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                label = { Text("Profile") },
                colors = colors
            )
        }
    }
}

@Composable
fun BottomNavigationBar(currentRoute: String, onNavigate: (String) -> Unit) {
    MeshNavigationBar(
        currentRoute = currentRoute,
        onHome = { onNavigate("home") },
        onChats = { onNavigate("chats") }
    )
}

@Composable
fun MeshFooterNavigation(
    currentRoute: String,
    onHome: () -> Unit,
    onChats: () -> Unit,
    onProfile: (() -> Unit)? = null
) {
    MeshNavigationBar(
        currentRoute = currentRoute,
        onHome = onHome,
        onChats = onChats,
        onProfile = onProfile
    )
}

@Composable
fun NodeCard(node: NodeCardState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MeshShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MeshBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(MeshSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(initials = node.avatarInitials, size = 48.dp)
            Spacer(modifier = Modifier.size(MeshSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(node.name, style = MaterialTheme.typography.titleMedium, color = MeshTextPrimary, fontWeight = FontWeight.SemiBold)
                Text(node.id, style = MaterialTheme.typography.bodySmall, color = MeshMuted)
                if (!node.lastMessagePreview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        node.lastMessagePreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MeshMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!node.lastMessageTimestamp.isNullOrBlank()) {
                        Text(
                            node.lastMessageTimestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MeshMuted
                        )
                    }
                    if (node.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        MeshStatusChip(text = node.unreadCount.toString(), color = MeshGreen)
                    }
                }
            }
            OnlineIndicator(isOnline = node.isOnline)
        }
    }
}

@Composable
fun ChatBubble(message: ConversationMessageUiState, modifier: Modifier = Modifier) {
    val alignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isOutgoing) MeshBubbleOutbound else MeshBubbleInbound
    val borderColor = if (message.isOutgoing) MeshBubbleOutboundBorder else MeshBubbleInboundBorder
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .background(bubbleColor, RoundedCornerShape(MeshRadius.md))
                .border(1.dp, borderColor, RoundedCornerShape(MeshRadius.md))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(message.text, color = MeshTextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message.timestamp, style = MaterialTheme.typography.labelSmall, color = MeshMuted)
                    if (!message.deliveryStatusLabel.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(message.deliveryStatusLabel, style = MaterialTheme.typography.labelSmall, color = MeshMuted)
                    }
                }
            }
        }
    }
}

/**
 * Generic "nothing here yet" placeholder shared by empty chat lists, peer lists, etc.
 * Uses a muted, low-emphasis wifi icon so it doubles as a subtle P2P visual cue.
 */
@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MeshBg3),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.SignalWifiStatusbarConnectedNoInternet4,
                contentDescription = null,
                tint = MeshMuted,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(MeshSpacing.md))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MeshTextPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MeshMuted)
    }
}
