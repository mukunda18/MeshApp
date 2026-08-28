package com.meshapp.ui.screens.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshapp.ui.theme.MeshBg0
import com.meshapp.ui.theme.MeshBg1
import com.meshapp.ui.theme.MeshBorder
import com.meshapp.ui.theme.MeshGreen
import com.meshapp.ui.theme.MeshMuted
import com.meshapp.ui.theme.MeshShapes
import com.meshapp.ui.theme.MeshSpacing
import com.meshapp.ui.theme.MeshTextPrimary

@Composable
fun AboutScreen() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Scaffold(
        containerColor = MeshBg0
    ) { paddingValues ->
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(360)) + slideInVertically(
                animationSpec = tween(360, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 8 }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = MeshSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BreathingLogo(modifier = Modifier.padding(top = MeshSpacing.lg))

                Text(
                    text = "MeshApp",
                    color = MeshTextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = MeshSpacing.sm)
                )

                Surface(
                    shape = MeshShapes.chip,
                    color = MeshGreen.copy(alpha = 0.14f),
                    modifier = Modifier.padding(top = MeshSpacing.xs)
                ) {
                    Text(
                        text = "Version 1.0.0",
                        color = MeshGreen,
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = MeshSpacing.md, vertical = MeshSpacing.xs)
                    )
                }

                Surface(
                    shape = MeshShapes.card,
                    color = MeshBg1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MeshSpacing.lg)
                        .border(1.dp, MeshBorder, MeshShapes.card)
                ) {
                    Text(
                        text = "A decentralized messaging application designed for absolute resilience. MeshApp operates over local Wi-Fi mesh networks, ensuring your communication remains private and functional even when the global internet is inaccessible.",
                        color = MeshMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(MeshSpacing.md)
                    )
                }

                Surface(
                    shape = MeshShapes.card,
                    color = MeshBg1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MeshSpacing.sm)
                        .border(1.dp, MeshBorder, MeshShapes.card)
                ) {
                    Column(modifier = Modifier.padding(MeshSpacing.md), verticalArrangement = Arrangement.spacedBy(MeshSpacing.sm)) {
                        Text(
                            text = "CONNECTIVITY",
                            color = MeshGreen,
                            style = MaterialTheme.typography.titleMedium,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        ConnectivityRow(icon = Icons.Filled.Wifi, label = "Wi-Fi Direct / Ad-hoc")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MeshBorder)
                        )
                        ConnectivityRow(icon = Icons.Filled.Lock, label = "End-to-End Encrypted")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "\u00A9 2026 All rights reserved.",
                        color = MeshMuted,
                        style = MaterialTheme.typography.bodySmall,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = MeshSpacing.lg)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectivityRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MeshGreen.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MeshGreen, modifier = Modifier.size(15.dp))
        }
        Spacer(modifier = Modifier.padding(start = MeshSpacing.xs))
        Text(
            text = label,
            color = MeshTextPrimary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun BreathingLogo(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo-breathe")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo-glow-scale"
    )

    Box(
        modifier = modifier
            .size(160.dp)
            .graphicsLayer {
                scaleX = glowScale
                scaleY = glowScale
            }
            .drawBehind {
                drawCircle(color = MeshGreen.copy(alpha = 0.16f), radius = size.minDimension / 2.1f)
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MeshShapes.card,
            color = MeshBg1,
            modifier = Modifier
                .size(120.dp)
                .border(1.dp, MeshBorder, MeshShapes.card)
        ) {
            Box(contentAlignment = Alignment.Center) {
                MeshIconGlyph(modifier = Modifier.size(64.dp))
            }
        }
    }
}

@Composable
private fun MeshIconGlyph(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawBehind {
            val lineColor = MeshGreen
            val dotColor = MeshGreen
            val r = 4.dp.toPx()
            val x1 = size.width * 0.2f
            val x2 = size.width * 0.5f
            val x3 = size.width * 0.8f
            val y1 = size.height * 0.2f
            val y2 = size.height * 0.5f
            val y3 = size.height * 0.8f

            drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(x1, y1), end = androidx.compose.ui.geometry.Offset(x2, y2), strokeWidth = 3.dp.toPx())
            drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(x2, y2), end = androidx.compose.ui.geometry.Offset(x3, y1), strokeWidth = 3.dp.toPx())
            drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(x1, y3), end = androidx.compose.ui.geometry.Offset(x2, y2), strokeWidth = 3.dp.toPx())
            drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(x2, y2), end = androidx.compose.ui.geometry.Offset(x3, y3), strokeWidth = 3.dp.toPx())
            drawCircle(dotColor, r, center = androidx.compose.ui.geometry.Offset(x1, y1))
            drawCircle(dotColor, r, center = androidx.compose.ui.geometry.Offset(x3, y1))
            drawCircle(dotColor, r, center = androidx.compose.ui.geometry.Offset(x2, y2))
            drawCircle(dotColor, r, center = androidx.compose.ui.geometry.Offset(x1, y3))
            drawCircle(dotColor, r, center = androidx.compose.ui.geometry.Offset(x3, y3))
        }
    )
}