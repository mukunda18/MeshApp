package com.meshapp.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SignalWifiStatusbarConnectedNoInternet4
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshapp.ui.state.ConversationMessageUiState
import com.meshapp.ui.state.NodeCardState
import com.meshapp.ui.theme.MeshBg0
import com.meshapp.ui.theme.MeshBg1
import com.meshapp.ui.theme.MeshBg2
import com.meshapp.ui.theme.MeshBubbleInbound
import com.meshapp.ui.theme.MeshBubbleOutbound
import com.meshapp.ui.theme.MeshGreen
import com.meshapp.ui.theme.MeshGreenMuted
import com.meshapp.ui.theme.MeshGreenOnAccent
import com.meshapp.ui.theme.MeshMuted
import com.meshapp.ui.theme.MeshOffline
import com.meshapp.ui.theme.MeshRadius
import com.meshapp.ui.theme.MeshShapes
import com.meshapp.ui.theme.MeshSpacing
import com.meshapp.ui.theme.MeshTextPrimary
import com.meshapp.ui.theme.MeshTextSecondary

object MeshMotion {
    const val fast = 120
    const val medium = 200
    const val slow = 320
    val easing = FastOutSlowInEasing
}

object MeshAvatarSize {
    val small: Dp = 32.dp
    val medium: Dp = 40.dp
    val large: Dp = 48.dp
    val extraLarge: Dp = 56.dp
}

@Composable
fun ProfileAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = MeshAvatarSize.medium,
    containerColor: Color = MeshGreen,
    isSelf: Boolean = false,
    showRing: Boolean = false
) {
    val textSize = (size.value * 0.36f).sp
    val ringColor = if (isSelf) MeshGreen else MeshMuted.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (isSelf || showRing) {
                    Modifier.border(1.5.dp, ringColor, CircleShape)
                } else {
                    Modifier
                }
            )
            .padding(if (isSelf || showRing) 2.dp else 0.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = MeshGreenOnAccent,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(fontSize = textSize)
        )
    }
}

@Composable
fun OnlineIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val color by animateColorAsState(
        targetValue = if (isOnline) MeshGreen else MeshTextSecondary,
        animationSpec = tween(MeshMotion.medium),
        label = "status-color"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        StatusDot(isOnline = isOnline)
        Spacer(modifier = Modifier.size(MeshSpacing.xxs))
        Text(
            text = if (isOnline) "Online" else "Offline",
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
fun StatusDot(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp
) {
    val color by animateColorAsState(
        targetValue = if (isOnline) MeshGreen else MeshOffline,
        animationSpec = tween(MeshMotion.medium),
        label = "dot-color"
    )

    if (isOnline) {
        val transition = rememberInfiniteTransition(label = "presence-pulse")
        val pulse by transition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    1800,
                    easing = MeshMotion.easing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "presence-pulse-scale"
        )

        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                        alpha = 0.14f
                    }
                    .clip(CircleShape)
                    .background(color)
            )

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

enum class MeshChipSize {
    Dense,
    Regular
}

@Composable
fun MeshStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MeshGreen,
    filled: Boolean = false,
    size: MeshChipSize = MeshChipSize.Regular
) {
    val horizontalPadding =
        if (size == MeshChipSize.Dense) MeshSpacing.xs else MeshSpacing.sm

    val verticalPadding =
        if (size == MeshChipSize.Dense) 2.dp else MeshSpacing.xxs

    Surface(
        shape = MeshShapes.chip,
        color = if (filled) color else color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = if (filled) MeshGreenOnAccent else color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (filled) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.padding(
                horizontal = horizontalPadding,
                vertical = verticalPadding
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    showDivider: Boolean = false,
    trailing: @Composable () -> Unit = {}
) {
    Column {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MeshTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MeshMuted
                        )
                    }
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MeshTextPrimary
                        )
                    }
                }
            },
            actions = {
                trailing()
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MeshBg2)
            )
        }
    }
}

@Composable
private fun MeshNavigationBar(
    currentRoute: String,
    onHome: () -> Unit,
    onChats: () -> Unit,
    onProfile: (() -> Unit)? = null
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MeshBg2)
        )

        NavigationBar(
            containerColor = MeshBg0,
            tonalElevation = 0.dp
        ) {
            val colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MeshGreen,
                selectedTextColor = MeshGreen,
                indicatorColor = MeshGreen.copy(alpha = 0.10f),
                unselectedIconColor = MeshMuted,
                unselectedTextColor = MeshMuted
            )

            NavigationBarItem(
                selected = currentRoute == "home",
                onClick = onHome,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = "Home",
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = "Home",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = colors
            )

            NavigationBarItem(
                selected = currentRoute == "chats",
                onClick = onChats,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.ChatBubble,
                        contentDescription = "Chats",
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = "Chats",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = colors
            )

            if (onProfile != null) {
                NavigationBarItem(
                    selected = currentRoute == "profile",
                    onClick = onProfile,
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Profile",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Profile",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = colors
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    MeshNavigationBar(
        currentRoute = currentRoute,
        onHome = {
            onNavigate("home")
        },
        onChats = {
            onNavigate("chats")
        }
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
fun NodeCard(
    node: NodeCardState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MeshShapes.card)
            .background(MeshBg1)
            .clickable(onClick = onClick)
            .padding(MeshSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                ProfileAvatar(
                    initials = node.avatarInitials,
                    size = MeshAvatarSize.large
                )

                StatusDot(
                    isOnline = node.isOnline,
                    size = 11.dp,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }

            Spacer(modifier = Modifier.size(MeshSpacing.sm))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MeshTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = node.lastMessagePreview
                        ?.takeIf { it.isNotBlank() }
                        ?: "No messages yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeshMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!node.lastMessageTimestamp.isNullOrBlank() || node.unreadCount > 0) {
                Spacer(modifier = Modifier.size(MeshSpacing.sm))

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    if (!node.lastMessageTimestamp.isNullOrBlank()) {
                        Text(
                            text = node.lastMessageTimestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MeshMuted
                        )
                    }

                    if (node.unreadCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))

                        MeshStatusChip(
                            text = node.unreadCount.toString(),
                            color = MeshGreen,
                            filled = true,
                            size = MeshChipSize.Dense
                        )
                    }
                }
            }
        }
    }
}

enum class ChatBubbleGroupPosition {
    Single,
    First,
    Middle,
    Last
}

private fun chatBubbleShape(
    isOutgoing: Boolean,
    groupPosition: ChatBubbleGroupPosition
): RoundedCornerShape {
    val big = MeshRadius.lg
    val small = MeshRadius.sm

    val isTopOfGroup =
        groupPosition == ChatBubbleGroupPosition.Single ||
                groupPosition == ChatBubbleGroupPosition.First

    val isBottomOfGroup =
        groupPosition == ChatBubbleGroupPosition.Single ||
                groupPosition == ChatBubbleGroupPosition.Last

    return if (isOutgoing) {
        RoundedCornerShape(
            topStart = big,
            topEnd = if (isTopOfGroup) big else small,
            bottomEnd = if (isBottomOfGroup) small else big,
            bottomStart = big
        )
    } else {
        RoundedCornerShape(
            topStart = if (isTopOfGroup) big else small,
            topEnd = big,
            bottomEnd = big,
            bottomStart = if (isBottomOfGroup) small else big
        )
    }
}

@Composable
fun ChatBubble(
    message: ConversationMessageUiState,
    modifier: Modifier = Modifier,
    groupPosition: ChatBubbleGroupPosition = ChatBubbleGroupPosition.Single,
    showTimestamp: Boolean =
        groupPosition == ChatBubbleGroupPosition.Single ||
                groupPosition == ChatBubbleGroupPosition.Last,
    content: (@Composable () -> Unit)? = null
) {
    val alignment =
        if (message.isOutgoing) Alignment.End else Alignment.Start

    val bubbleColor =
        if (message.isOutgoing) MeshBubbleOutbound else MeshBubbleInbound

    val shape =
        chatBubbleShape(message.isOutgoing, groupPosition)

    val topPadding =
        if (
            groupPosition == ChatBubbleGroupPosition.Single ||
            groupPosition == ChatBubbleGroupPosition.First
        ) {
            MeshSpacing.md
        } else {
            MeshSpacing.xxs
        }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(
                    horizontal = MeshSpacing.sm,
                    vertical = MeshSpacing.xs
                )
        ) {
            content?.invoke() ?: Text(
                text = message.text,
                color = MeshTextPrimary,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        if (showTimestamp) {
            Row(
                modifier = Modifier.padding(
                    top = 3.dp,
                    start = MeshSpacing.xxs,
                    end = MeshSpacing.xxs
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshTextSecondary
                )

                if (!message.deliveryStatusLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(MeshSpacing.xxs))

                    Text(
                        text = message.deliveryStatusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MeshTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector =
        Icons.Filled.SignalWifiStatusbarConnectedNoInternet4
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                fadeIn(tween(MeshMotion.medium)) togetherWith
                        fadeOut(tween(MeshMotion.fast))
            },
            label = "empty-state-icon"
        ) { animatedIcon ->
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MeshGreenMuted.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = animatedIcon,
                    contentDescription = null,
                    tint = MeshMuted.copy(alpha = 0.8f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(MeshSpacing.md))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MeshTextPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MeshMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 260.dp)
            )
        }
    }
}