package com.meshapp.ui.screens.about

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshapp.ui.theme.MeshBg0
import com.meshapp.ui.theme.MeshBg1
import com.meshapp.ui.theme.MeshBg2
import com.meshapp.ui.theme.MeshBorder
import com.meshapp.ui.theme.MeshGreen
import com.meshapp.ui.theme.MeshMuted
import com.meshapp.ui.theme.MeshShapes
import com.meshapp.ui.theme.MeshSpacing
import com.meshapp.ui.theme.MeshTextPrimary

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MeshBg0,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MeshBg2)
                    .padding(horizontal = MeshSpacing.xs + 2.dp, vertical = MeshSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MeshTextPrimary
                    )
                }
                Text(
                    text = "About",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MeshTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = MeshSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .padding(top = MeshSpacing.lg)
                    .size(160.dp)
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

            Text(
                text = "MeshApp",
                color = MeshTextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
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
                    style = MaterialTheme.typography.bodyLarge,
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
                        style = MaterialTheme.typography.titleLarge,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Wifi, contentDescription = null, tint = MeshMuted)
                        Text(
                            text = " Wi-Fi Direct / Ad-hoc",
                            color = MeshTextPrimary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MeshMuted)
                        Text(
                            text = " End-to-End Encrypted",
                            color = MeshTextPrimary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "\u00A9 2025 All rights reserved.",
                color = MeshMuted,
                style = MaterialTheme.typography.bodyLarge,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = MeshSpacing.lg)
            )
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
