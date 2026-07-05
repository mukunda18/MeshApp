package com.minor.ui.screens.about

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
import com.minor.ui.theme.MeshGreen

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = AboutBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111316))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFFE4E7E9)
                    )
                }
                Text(
                    text = "About",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFE5E9EA),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .size(160.dp)
                    .drawBehind {
                        drawCircle(color = MeshGreen.copy(alpha = 0.18f), radius = size.minDimension / 2.1f)
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = Color(0xFF1B1B20),
                    modifier = Modifier
                        .size(120.dp)
                        .border(1.dp, Color(0xFF1D2A3E), RoundedCornerShape(30.dp))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MeshIconGlyph(modifier = Modifier.size(64.dp))
                    }
                }
            }

            Text(
                text = "MeshApp",
                color = Color(0xFFE5EAE9),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MeshGreen.copy(alpha = 0.14f),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = "Version 1.0.0",
                    color = MeshGreen,
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 0.8.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF191A1D),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .border(1.dp, Color(0xFF1E2C40), RoundedCornerShape(18.dp))
            ) {
                Text(
                    text = "A decentralized messaging application designed for absolute resilience. MeshApp operates over local Wi-Fi mesh networks, ensuring your communication remains private and functional even when the global internet is inaccessible.",
                    color = Color(0xFFBFC6BF),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF191A1D),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .border(1.dp, Color(0xFF1E2C40), RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "CONNECTIVITY",
                        color = MeshGreen,
                        style = MaterialTheme.typography.titleLarge,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Wifi, contentDescription = null, tint = Color(0xFFB9C1B8))
                        Text(
                            text = " Wi-Fi Direct / Ad-hoc",
                            color = Color(0xFFC4CBC5),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFFB9C1B8))
                        Text(
                            text = " End-to-End Encrypted",
                            color = Color(0xFFC4CBC5),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "© 2025 All rights reserved.",
                color = Color(0xFF7F8982),
                style = MaterialTheme.typography.bodyLarge,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 22.dp)
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

private val AboutBg = Color(0xFF090B0D)
