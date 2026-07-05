package com.minor.ui.screens.home

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material.icons.outlined.ListAlt
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
import com.minor.ui.theme.MeshGreen
import com.minor.ui.theme.MeshMuted
import com.minor.ui.viewmodel.HomeViewModel

private val HomeBackground = Color(0xFF050706)
private val TopBarBackground = Color(0xFF111314)
private val HeroCenter = Color(0xFF1A1C1E)
private val PrimaryCtaColor = Color(0xFF29DC67)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToNearbyNodes: () -> Unit,
    onNavigateToNetworkInterfaces: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToLogs: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
                    color = Color(0xFFF3F4F5),
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = {
                        viewModel.refreshNetworkInterfaces()
                        menuExpanded = true
                    }) {
                        ProfileAvatar(initials = uiState.profile.avatarInitials, size = 36.dp)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .width(220.dp)
                            .background(Color(0xFF1E2123))
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
                        DropdownMenuItem(
                            text = { Text("System Logs") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.ListAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onNavigateToLogs()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            MeshStatusOrb(
                isMeshOn = uiState.isMeshOn,
                onToggle = { viewModel.toggleMesh() }
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
                color = Color(0xFFCFD3D6),
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
                    contentColor = Color(0xFF0D2B1A)
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
                    .clickable { viewModel.toggleMesh() },
                color = Color(0xFFB5B9BD),
                style = MaterialTheme.typography.bodyMedium,
                letterSpacing = 0.8.sp
            )

            Icon(
                imageVector = Icons.Outlined.PowerSettingsNew,
                contentDescription = null,
                tint = Color(0xFFB5B9BD),
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
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mesh-pulse"
    )

    Box(
        modifier = Modifier
            .size(288.dp)
            .drawBehind {
                val color = if (isMeshOn) MeshGreen else Color(0xFF596268)
                val center = this.center
                drawCircle(
                    color = color.copy(alpha = 0.25f),
                    radius = 112.dp.toPx() * pulse,
                    center = center,
                    style = Stroke(width = 1.4.dp.toPx())
                )
                drawCircle(
                    color = color.copy(alpha = 0.18f),
                    radius = 132.dp.toPx() * pulse,
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx())
                )
                drawCircle(
                    color = color.copy(alpha = 0.12f),
                    radius = 152.dp.toPx() * pulse,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
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
                    color = if (isMeshOn) MeshGreen else Color(0xFF6A757C),
                    shape = CircleShape
                )
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            MeshGlyph(
                modifier = Modifier.size(110.dp),
                color = if (isMeshOn) MeshGreen else Color(0xFF6A757C)
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
            val r = (size.width * 0.08f).coerceIn(2.dp.toPx(), 8.dp.toPx())
            val x1 = size.width * 0.22f
            val x2 = size.width * 0.5f
            val x3 = size.width * 0.78f
            val y1 = size.height * 0.24f
            val y2 = size.height * 0.5f
            val y3 = size.height * 0.76f

            val stroke = (size.width * 0.06f).coerceIn(1.8.dp.toPx(), 6.dp.toPx())

            drawLine(color, start = androidx.compose.ui.geometry.Offset(x1, y1), end = androidx.compose.ui.geometry.Offset(x2, y2), strokeWidth = stroke)
            drawLine(color, start = androidx.compose.ui.geometry.Offset(x2, y2), end = androidx.compose.ui.geometry.Offset(x3, y1), strokeWidth = stroke)
            drawLine(color, start = androidx.compose.ui.geometry.Offset(x1, y3), end = androidx.compose.ui.geometry.Offset(x2, y2), strokeWidth = stroke)
            drawLine(color, start = androidx.compose.ui.geometry.Offset(x2, y2), end = androidx.compose.ui.geometry.Offset(x3, y3), strokeWidth = stroke)
            drawCircle(color, r, center = androidx.compose.ui.geometry.Offset(x1, y1))
            drawCircle(color, r, center = androidx.compose.ui.geometry.Offset(x3, y1))
            drawCircle(color, r, center = androidx.compose.ui.geometry.Offset(x2, y2))
            drawCircle(color, r, center = androidx.compose.ui.geometry.Offset(x1, y3))
            drawCircle(color, r, center = androidx.compose.ui.geometry.Offset(x3, y3))
        }
    )
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen(
        onNavigateToNearbyNodes = {},
        onNavigateToNetworkInterfaces = {},
        onNavigateToProfile = {},
        onNavigateToAbout = {},
        onNavigateToLogs = {}
    )
}
