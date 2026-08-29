package com.meshapp.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshapp.ui.components.EmptyState
import com.meshapp.ui.components.ProfileAvatar
import com.meshapp.ui.components.StatusDot
import com.meshapp.ui.state.HomeNodeUiState
import com.meshapp.ui.theme.MeshBg1
import com.meshapp.ui.theme.MeshBg2
import com.meshapp.ui.theme.MeshBg3
import com.meshapp.ui.theme.MeshBorder
import com.meshapp.ui.theme.MeshGreen
import com.meshapp.ui.theme.MeshGreenOnAccent
import com.meshapp.ui.theme.MeshMuted
import com.meshapp.ui.theme.MeshShapes
import com.meshapp.ui.theme.MeshSpacing
import com.meshapp.ui.theme.MeshTextPrimary
import com.meshapp.ui.theme.MeshTextSecondary
import com.meshapp.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNodeClick: (HomeNodeUiState) -> Unit,
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
            onToggle = viewModel::toggleMesh
        )

        Spacer(modifier = Modifier.height(MeshSpacing.md))

        // Animated color transition for power status title
        val textColor by animateColorAsState(
            targetValue = if (uiState.isMeshOn) MeshGreen else MeshTextSecondary,
            animationSpec = tween(durationMillis = 300),
            label = "textColor"
        )

        Text(
            text = if (uiState.isMeshOn) "Mesh active" else "Mesh off",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Smooth vertical roll transition for subtext updates
        AnimatedContent(
            targetState = uiState.connectionStatus,
            transitionSpec = {
                (slideInVertically { height -> height } + fadeIn()) togetherWith
                        (slideOutVertically { height -> -height } + fadeOut())
            },
            label = "statusText"
        ) { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MeshMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(MeshSpacing.xl))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MeshSpacing.sm)
        ) {
            StatCard(
                label = "Peers",
                value = uiState.connectedNodes
                    .count { it.isOnline }
                    .toString(),
                modifier = Modifier.weight(1f)
            )

            StatCard(
                label = "Interfaces",
                value = uiState.networkInterfaceCount.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(MeshSpacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nearby",
                style = MaterialTheme.typography.labelLarge,
                color = MeshTextSecondary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(MeshSpacing.xs))

        // Fade transition between list and empty state
        Crossfade(
            targetState = uiState.connectedNodes.isEmpty(),
            animationSpec = tween(durationMillis = 300),
            label = "emptyStateCrossfade"
        ) { isEmpty ->
            if (isEmpty) {
                EmptyState(
                    title = "No nodes nearby",
                    subtitle = "Turn the mesh on and stay close to other devices to connect.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MeshSpacing.lg)
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(MeshSpacing.md),
                    contentPadding = PaddingValues(
                        vertical = MeshSpacing.xxs
                    )
                ) {
                    items(
                        items = uiState.connectedNodes.take(8),
                        key = { it.nodeId }
                    ) { node ->
                        PeerPreviewChip(
                            node = node,
                            onClick = { onNodeClick(node) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth press feedback scale
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(stiffness = 400f),
        label = "pressScale"
    )

    // Animated colors for power state toggle
    val bgColor by animateColorAsState(
        targetValue = if (isOn) MeshGreen else MeshBg2,
        animationSpec = tween(durationMillis = 300),
        label = "bgColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isOn) MeshGreen else MeshBorder,
        animationSpec = tween(durationMillis = 300),
        label = "borderColor"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isOn) MeshGreenOnAccent else MeshMuted,
        animationSpec = tween(durationMillis = 300),
        label = "iconTint"
    )

    // Pulse aura animation when active
    val auraAlpha by animateFloatAsState(
        targetValue = if (isOn) 0.10f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "auraAlpha"
    )

    Box(
        modifier = modifier.size(168.dp),
        contentAlignment = Alignment.Center
    ) {
        val transition = rememberInfiniteTransition(label = "meshPulse")
        val pulseScale by transition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )

        Box(
            modifier = Modifier
                .size(150.dp)
                .graphicsLayer {
                    scaleX = if (isOn) pulseScale else 1f
                    scaleY = if (isOn) pulseScale else 1f
                    alpha = auraAlpha
                }
                .clip(CircleShape)
                .background(MeshGreen)
        )

        Box(
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(CircleShape)
                .background(bgColor)
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onToggle
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = if (isOn) "Turn mesh off" else "Turn mesh on",
                tint = iconTint,
                modifier = Modifier.size(44.dp)
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
        modifier = modifier,
        shape = MeshShapes.card,
        color = MeshBg1
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MeshSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Number roller transition when counts change
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInVertically { height -> height } + fadeIn()) togetherWith
                                (slideOutVertically { height -> -height } + fadeOut())
                    } else {
                        (slideInVertically { height -> -height } + fadeIn()) togetherWith
                                (slideOutVertically { height -> height } + fadeOut())
                    }
                },
                label = "statCounter"
            ) { countText ->
                Text(
                    text = countText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MeshTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MeshMuted
            )
        }
    }
}

@Composable
private fun PeerPreviewChip(
    node: HomeNodeUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile press scale feedback on chip
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(stiffness = 400f),
        label = "chipPressScale"
    )

    Column(
        modifier = modifier
            .width(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            ProfileAvatar(
                initials = node.avatarInitials,
                size = 52.dp,
                containerColor = if (node.isOnline) MeshGreen else MeshBg3
            )

            StatusDot(
                isOnline = node.isOnline,
                size = 12.dp,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = node.name,
            style = MaterialTheme.typography.labelSmall,
            color = MeshTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}