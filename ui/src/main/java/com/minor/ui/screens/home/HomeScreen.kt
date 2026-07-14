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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
                MeshGlyph(modifier = Modifier.size(30.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = uiState.appName.replace(" ", ""),
                    style = MaterialTheme.typography.titleLarge,
                    color = MeshTextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = {
                        onRefreshNetwork()
                        menuExpanded = true
                    }) {
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
                            text = { Text("Device Profile") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
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
                                Icon(
                                    imageVector = Icons.Outlined.SettingsInputAntenna,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
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
                                Icon(
                                    imageVector = Icons.Outlined.Article,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
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
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
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
                Icon(imageVector = Icons.Outlined.Group, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "View Nearby Nodes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = if (uiState.isMeshOn) "Tap to disable mesh" else "Tap to enable mesh",
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clickable(onClick = onToggleMesh),
                color = MeshMuted,
                style = MaterialTheme.typography.bodyMedium,
                letterSpacing = 0.8.sp
            )

            Icon(
                imageVector = Icons.Outlined.PowerSettingsNew,
                contentDescription = null,
                tint = MeshMuted,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(18.dp)
            )
        }
    }
}

@Composable
private fun MeshStatusOrb(
    isMeshOn: Boolean,
    onToggle: () -> Unit
) {
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
                .background(HeroCenter)
                .border(
                    width = 3.dp,
                    color = if (isMeshOn) MeshGreen else MeshBorder,
                    shape = CircleShape
                )
                .clickable(onClick = onToggle)
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
            MeshGlyph(
                modifier = Modifier.size(110.dp),
                color = if (isMeshOn) MeshGreen else MeshMuted
            )
        }
    }
}

@Composable
private fun MeshGlyph(
    modifier: Modifier = Modifier,
    color: Color = MeshGreen
) {
    Box(
        modifier = modifier.drawBehind {
            val lineColor = color
            val dotColor = color
            val r = (size.width * 0.08f).coerceIn(2.dp.toPx(), 8.dp.toPx())
            val x1 = size.width * 0.22f
            val x2 = size.width * 0.5f
            val x3 = size.width * 0.78f
            val y1 = size.height * 0.24f
            val y2 = size.height * 0.5f
            val y3 = size.height * 0.76f

            val stroke = (size.width * 0.06f).coerceIn(1.8.dp.toPx(), 6.dp.toPx())

            drawLine(lineColor, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = stroke)
            drawLine(lineColor, start = Offset(x2, y2), end = Offset(x3, y1), strokeWidth = stroke)
            drawLine(lineColor, start = Offset(x1, y3), end = Offset(x2, y2), strokeWidth = stroke)
            drawLine(lineColor, start = Offset(x2, y2), end = Offset(x3, y3), strokeWidth = stroke)
            drawCircle(dotColor, r, center = Offset(x1, y1))
            drawCircle(dotColor, r, center = Offset(x3, y1))
            drawCircle(dotColor, r, center = Offset(x2, y2))
            drawCircle(dotColor, r, center = Offset(x1, y3))
            drawCircle(dotColor, r, center = Offset(x3, y3))
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