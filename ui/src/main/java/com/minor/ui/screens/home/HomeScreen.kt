package com.minor.ui.screens.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minor.ui.components.ProfileAvatar
import com.minor.ui.state.HomeUiState
import com.minor.ui.theme.MeshAccentBlue
import com.minor.ui.theme.MeshBackground
import com.minor.ui.theme.MeshBorder
import com.minor.ui.theme.MeshGreen
import com.minor.ui.theme.MeshHeader
import com.minor.ui.theme.MeshMuted
import com.minor.ui.theme.MeshSurface
import com.minor.ui.theme.MeshTextPrimary
import com.minor.ui.viewmodel.HomeViewModel

private val HomeBackground = MeshBackground
private val TopBarBackground = MeshHeader
private val HeroCenter = MeshSurface
private val PrimaryCtaColor = MeshGreen

/**
 * Every icon in this screen (top bar logo, menu rows, CTA glyph, power hint)
 * is now drawn from the same "node + speech-bubble" language as the app's
 * favicon, instead of pulling in generic Material icon glyphs.
 */
private enum class MeshIconType {
    Bubble,        // app mark: speech-bubble containing mesh nodes
    ProfileNode,   // single node in a ring -> "Device Profile"
    InterfaceMesh, // three linked nodes -> "Network Interfaces"
    LogLines,      // stacked entries with a node -> "Logs"
    InfoNode,      // node with halo -> "About"
    ClusterNodes,  // triangular node cluster -> "Nearby Nodes" CTA
    PowerRing      // ring with center dot -> power / toggle hint
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToNearbyNodes: () -> Unit,
    onNavigateToNetworkInterfaces: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToLogs: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreenContent(
        uiState = uiState,
        onToggleMesh = { viewModel.toggleMesh() },
        onRefreshNetwork = { viewModel.refreshNetworkInterfaces() },
        onNavigateToNearbyNodes = onNavigateToNearbyNodes,
        onNavigateToNetworkInterfaces = onNavigateToNetworkInterfaces,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToAbout = onNavigateToAbout,
        onNavigateToLogs = onNavigateToLogs
    )
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    onToggleMesh: () -> Unit,
    onRefreshNetwork: () -> Unit,
    onNavigateToNearbyNodes: () -> Unit,
    onNavigateToNetworkInterfaces: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToLogs: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(TopBarBackground)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MeshIcon(
                    type = MeshIconType.Bubble,
                    modifier = Modifier.size(30.dp),
                    color = MeshGreen
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = uiState.appName.replace(" ", ""),
                    style = MaterialTheme.typography.titleLarge,
                    color = MeshTextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.semantics {
                            contentDescription = "Open menu"
                        }
                    ) {
                        ProfileAvatar(initials = uiState.profile.avatarInitials, size = 36.dp)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .width(220.dp)
                            .background(MeshHeader),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Refresh Network") },
                            leadingIcon = {
                                MeshIcon(
                                    type = MeshIconType.InterfaceMesh,
                                    modifier = Modifier.size(20.dp),
                                    color = MeshGreen
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRefreshNetwork()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Device Profile") },
                            leadingIcon = {
                                MeshIcon(
                                    type = MeshIconType.ProfileNode,
                                    modifier = Modifier.size(20.dp),
                                    color = MeshTextPrimary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onNavigateToProfile()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Network Interfaces") },
                            leadingIcon = {
                                MeshIcon(
                                    type = MeshIconType.InterfaceMesh,
                                    modifier = Modifier.size(20.dp),
                                    color = MeshTextPrimary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onNavigateToNetworkInterfaces()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Logs") },
                            leadingIcon = {
                                MeshIcon(
                                    type = MeshIconType.LogLines,
                                    modifier = Modifier.size(20.dp),
                                    color = MeshTextPrimary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onNavigateToLogs()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("About") },
                            leadingIcon = {
                                MeshIcon(
                                    type = MeshIconType.InfoNode,
                                    modifier = Modifier.size(20.dp),
                                    color = MeshTextPrimary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onNavigateToAbout()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            MeshStatusOrb(
                isMeshOn = uiState.isMeshOn,
                onToggle = onToggleMesh
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isMeshOn) PrimaryCtaColor else MeshMuted)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isMeshOn) "MESH ONLINE" else "MESH OFFLINE",
                    color = if (uiState.isMeshOn) PrimaryCtaColor else MeshMuted,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = uiState.connectionStatus,
                modifier = Modifier.padding(horizontal = 32.dp),
                color = MeshTextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onNavigateToNearbyNodes,
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryCtaColor,
                    contentColor = MeshBackground
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(56.dp),
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                MeshIcon(
                    type = MeshIconType.ClusterNodes,
                    modifier = Modifier.size(22.dp),
                    color = MeshBackground
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "View Nearby Nodes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                // NOTE: wire this to a real count, e.g. uiState.nearbyNodeCount,
                // once that field is exposed on HomeUiState. Hidden at 0 so it
                // never lies to the user in the meantime.
                val nearbyNodeCount = 0
                if (nearbyNodeCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MeshBackground.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = nearbyNodeCount.toString(),
                            color = MeshBackground,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Toggle hint is now a real tappable row with a proper touch target
            // and semantics, instead of bare clickable text.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .height(48.dp)
                    .clickable(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleMesh()
                    })
                    .padding(horizontal = 16.dp)
                    .semantics {
                        contentDescription = "Toggle mesh"
                        stateDescription = if (uiState.isMeshOn) "Mesh is on" else "Mesh is off"
                    }
            ) {
                MeshIcon(
                    type = MeshIconType.PowerRing,
                    modifier = Modifier.size(18.dp),
                    color = MeshMuted
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isMeshOn) "Tap to disable mesh" else "Tap to enable mesh",
                    color = MeshMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

@Composable
private fun MeshStatusOrb(
    isMeshOn: Boolean,
    onToggle: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val transition = rememberInfiniteTransition(label = "mesh-rings")

    // Three rings scanning outward at staggered offsets
    val ringOne by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring-one"
    )
    val ringTwo by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing, delayMillis = 1000),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring-two"
    )
    val ringThree by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing, delayMillis = 2000),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring-three"
    )

    val corePulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core-pulse"
    )

    val ringColor = if (isMeshOn) MeshGreen else MeshMuted
    val minRadiusDp = 96.dp
    val maxRadiusDp = 152.dp

    Box(
        modifier = Modifier
            .size(288.dp)
            .drawBehind {
                val center = this.center
                val minRadius = minRadiusDp.toPx()
                val maxRadius = maxRadiusDp.toPx()
                val span = maxRadius - minRadius

                listOf(ringOne, ringTwo, ringThree).forEach { progress ->
                    val radius = minRadius + span * progress
                    val alpha = (1f - progress) * 0.6f
                    drawCircle(
                        color = ringColor.copy(alpha = alpha),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(204.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(HeroCenter, HeroCenter.copy(alpha = 0.92f)),
                    )
                )
                .border(
                    width = 3.dp,
                    color = if (isMeshOn) MeshGreen else MeshBorder,
                    shape = CircleShape
                )
                .clickable(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                })
                .semantics {
                    contentDescription = "Mesh status, tap to toggle"
                    stateDescription = if (isMeshOn) "Mesh is on" else "Mesh is off"
                }
                .drawBehind {
                    if (isMeshOn) {
                        val inset = size.minDimension * 0.06f * (corePulse - 1f) * 10f
                        drawCircle(
                            color = MeshAccentBlue.copy(alpha = 0.18f),
                            radius = (size.minDimension / 2f) - inset,
                            center = this.center
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // The hero mark now mirrors the favicon directly: a speech
            // bubble outline wrapping the mesh-node glyph.
            MeshIcon(
                type = MeshIconType.Bubble,
                modifier = Modifier.size(120.dp),
                color = if (isMeshOn) MeshGreen else MeshMuted
            )
        }
    }
}

/**
 * Single drawing surface for every glyph on this screen. All shapes are
 * built from the same primitives as the favicon: dots ("nodes"), thin
 * connecting lines, and an optional speech-bubble outline. This keeps the
 * whole screen visually anchored to the app mark instead of borrowing a
 * generic icon set.
 */
@Composable
private fun MeshIcon(
    type: MeshIconType,
    modifier: Modifier = Modifier,
    color: Color = MeshGreen
) {
    Box(
        modifier = modifier.drawBehind {
            val w = size.width
            val h = size.height
            val stroke = (w * 0.09f).coerceIn(1.4.dp.toPx(), 5.dp.toPx())
            val dot = (w * 0.1f).coerceIn(1.6.dp.toPx(), 7.dp.toPx())

            when (type) {
                MeshIconType.Bubble -> {
                    // Speech-bubble outline, matching the favicon silhouette
                    val bubble = Path().apply {
                        val corner = w * 0.28f
                        val tailW = w * 0.16f
                        val tailH = h * 0.12f
                        val rect = androidx.compose.ui.geometry.Rect(
                            offset = Offset(w * 0.02f, h * 0.02f),
                            size = Size(w * 0.96f, h * 0.82f)
                        )
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                rect,
                                CornerRadius(corner, corner)
                            )
                        )
                        moveTo(w * 0.30f, h * 0.84f)
                        lineTo(w * 0.30f - tailW * 0.2f, h * 0.84f + tailH)
                        lineTo(w * 0.30f + tailW, h * 0.84f)
                        close()
                    }
                    drawPath(bubble, color = color, style = Stroke(width = stroke))

                    // Mesh nodes inside the bubble
                    val x1 = w * 0.28f; val x2 = w * 0.5f; val x3 = w * 0.72f
                    val y1 = h * 0.26f; val y2 = h * 0.46f; val y3 = h * 0.62f
                    drawLine(color, Offset(x1, y1), Offset(x2, y2), stroke)
                    drawLine(color, Offset(x2, y2), Offset(x3, y1), stroke)
                    drawLine(color, Offset(x1, y3), Offset(x2, y2), stroke)
                    drawLine(color, Offset(x2, y2), Offset(x3, y3), stroke)
                    listOf(
                        Offset(x1, y1), Offset(x3, y1), Offset(x2, y2),
                        Offset(x1, y3), Offset(x3, y3)
                    ).forEach { drawCircle(color, dot, it) }
                }

                MeshIconType.ProfileNode -> {
                    drawCircle(color, w * 0.22f, Offset(w / 2, h * 0.42f), style = Stroke(stroke))
                    drawCircle(color, dot, Offset(w / 2, h * 0.42f))
                    // shoulders hint, echoes a small "account" silhouette
                    val path = Path().apply {
                        moveTo(w * 0.2f, h * 0.95f)
                        quadraticBezierTo(w * 0.5f, h * 0.68f, w * 0.8f, h * 0.95f)
                    }
                    drawPath(path, color = color, style = Stroke(width = stroke))
                }

                MeshIconType.InterfaceMesh -> {
                    val left = Offset(w * 0.2f, h * 0.75f)
                    val right = Offset(w * 0.8f, h * 0.75f)
                    val top = Offset(w * 0.5f, h * 0.2f)
                    drawLine(color, left, top, stroke)
                    drawLine(color, right, top, stroke)
                    drawLine(color, left, right, stroke)
                    listOf(left, right, top).forEach { drawCircle(color, dot, it) }
                }

                MeshIconType.LogLines -> {
                    val xs = h * 0.22f
                    val gap = h * 0.28f
                    for (i in 0..2) {
                        val y = xs + gap * i
                        drawLine(
                            color,
                            Offset(w * 0.18f, y),
                            Offset(w * (if (i == 1) 0.62f else 0.82f), y),
                            stroke
                        )
                    }
                    drawCircle(color, dot * 0.9f, Offset(w * 0.18f, xs + gap * 1))
                }

                MeshIconType.InfoNode -> {
                    drawCircle(color, w * 0.38f, Offset(w / 2, h / 2), style = Stroke(stroke))
                    drawCircle(color, dot, Offset(w / 2, h * 0.35f))
                    drawLine(
                        color,
                        Offset(w / 2, h * 0.5f),
                        Offset(w / 2, h * 0.72f),
                        stroke
                    )
                }

                MeshIconType.ClusterNodes -> {
                    val a = Offset(w * 0.5f, h * 0.18f)
                    val b = Offset(w * 0.18f, h * 0.8f)
                    val c = Offset(w * 0.82f, h * 0.8f)
                    drawLine(color, a, b, stroke)
                    drawLine(color, a, c, stroke)
                    drawLine(color, b, c, stroke)
                    listOf(a, b, c).forEach { drawCircle(color, dot * 1.1f, it) }
                }

                MeshIconType.PowerRing -> {
                    drawCircle(color, w * 0.38f, Offset(w / 2, h / 2), style = Stroke(stroke))
                    drawLine(
                        color,
                        Offset(w / 2, h * 0.14f),
                        Offset(w / 2, h * 0.46f),
                        stroke
                    )
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreenContent(
        uiState = HomeUiState(),
        onToggleMesh = {},
        onRefreshNetwork = {},
        onNavigateToNearbyNodes = {},
        onNavigateToNetworkInterfaces = {},
        onNavigateToProfile = {},
        onNavigateToAbout = {},
        onNavigateToLogs = {}
    )
}