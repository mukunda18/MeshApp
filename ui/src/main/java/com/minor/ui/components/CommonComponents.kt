package com.minor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minor.ui.state.ConversationMessageUiState
import com.minor.ui.state.NodeCardState
import com.minor.ui.theme.MeshGreen
import com.minor.ui.theme.MeshMuted

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
        Text(initials, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun OnlineIndicator(isOnline: Boolean, modifier: Modifier = Modifier) {
    val color by animateColorAsState(if (isOnline) MeshGreen else MeshMuted)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(if (isOnline) "Online" else "Offline", style = MaterialTheme.typography.labelMedium, color = MeshMuted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshTopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MeshMuted)
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            }
        },
        actions = { trailing() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

@Composable
fun BottomNavigationBar(currentRoute: String, onNavigate: (String) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = { onNavigate("home") },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = currentRoute == "chats",
            onClick = { onNavigate("chats") },
            icon = { Icon(Icons.Filled.ChatBubble, contentDescription = null) },
            label = { Text("Chats") }
        )
    }
}

@Composable
fun MeshFooterNavigation(
    currentRoute: String,
    onHome: () -> Unit,
    onChats: () -> Unit,
    onProfile: (() -> Unit)? = null
) {
    NavigationBar(
        containerColor = Color(0xFF1A1A1D),
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = onHome,
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Home") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MeshGreen,
                selectedTextColor = MeshGreen,
                indicatorColor = MeshGreen.copy(alpha = 0.14f),
                unselectedIconColor = Color(0xFFB9C0B7),
                unselectedTextColor = Color(0xFFB9C0B7)
            )
        )
        NavigationBarItem(
            selected = currentRoute == "chats",
            onClick = onChats,
            icon = { Icon(Icons.Filled.ChatBubble, contentDescription = null) },
            label = { Text("Chats") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MeshGreen,
                selectedTextColor = MeshGreen,
                indicatorColor = MeshGreen.copy(alpha = 0.14f),
                unselectedIconColor = Color(0xFFB9C0B7),
                unselectedTextColor = Color(0xFFB9C0B7)
            )
        )
        if (onProfile != null) {
            NavigationBarItem(
                selected = currentRoute == "profile",
                onClick = onProfile,
                icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                label = { Text("Profile") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MeshGreen,
                    selectedTextColor = MeshGreen,
                    indicatorColor = MeshGreen.copy(alpha = 0.14f),
                    unselectedIconColor = Color(0xFFB9C0B7),
                    unselectedTextColor = Color(0xFFB9C0B7)
                )
            )
        }
    }
}

@Composable
fun NodeCard(node: NodeCardState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(initials = node.avatarInitials, size = 48.dp)
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(node.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                        Surface(shape = RoundedCornerShape(10.dp), color = MeshGreen.copy(alpha = 0.18f)) {
                            Text(
                                text = node.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MeshGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
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
    val bubbleColor = if (message.isOutgoing) MeshGreen else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.isOutgoing) Color.White else MaterialTheme.colorScheme.onSurface
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .background(bubbleColor, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(message.text, color = textColor)
                Spacer(modifier = Modifier.height(4.dp))
                val metaColor = if (message.isOutgoing) Color(0xFFD9FBE6) else MeshMuted
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message.timestamp, style = MaterialTheme.typography.labelSmall, color = metaColor)
                    if (!message.deliveryStatusLabel.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(message.deliveryStatusLabel, style = MaterialTheme.typography.labelSmall, color = metaColor)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Person, contentDescription = null, tint = MeshMuted, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MeshMuted)
    }
}
