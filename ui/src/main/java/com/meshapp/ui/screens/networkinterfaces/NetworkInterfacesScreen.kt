package com.meshapp.ui.screens.networkinterfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshapp.ui.theme.MeshBg0
import com.meshapp.ui.theme.MeshBg1
import com.meshapp.ui.theme.MeshBg2
import com.meshapp.ui.theme.MeshBorder
import com.meshapp.ui.theme.MeshGreen
import com.meshapp.ui.theme.MeshMuted
import com.meshapp.ui.theme.MeshShapes
import com.meshapp.ui.theme.MeshSpacing
import com.meshapp.ui.theme.MeshTextPrimary
import com.meshapp.ui.viewmodel.NetworkInterfacesViewModel

@Composable
fun NetworkInterfacesScreen(
    viewModel: NetworkInterfacesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val staInterface = uiState.interfaces.firstOrNull { isStaInterface(it.interfaceName) }
    val apInterface = uiState.interfaces.firstOrNull { isApInterface(it.interfaceName) }

    Scaffold(
        containerColor = MeshBg0
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = MeshSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MeshSpacing.md)
        ) {
            // Status Summary Header
            item {
                Surface(
                    shape = MeshShapes.cardSmall,
                    color = MeshBg1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MeshSpacing.xs)
                        .border(1.dp, MeshBorder, MeshShapes.cardSmall)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MeshSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = MeshShapes.cardSmall,
                            color = MeshGreen.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MeshGreen.copy(alpha = 0.24f)),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Wifi,
                                contentDescription = null,
                                tint = MeshGreen,
                                modifier = Modifier
                                    .padding(MeshSpacing.xs)
                                    .fillMaxSize()
                            )
                        }
                        Column(
                            modifier = Modifier
                                .padding(start = MeshSpacing.md)
                                .weight(1f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Wi-Fi Mesh Status: ",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MeshTextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MeshGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Connected to local mesh infrastructure.",
                                color = MeshMuted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            // Section Header
            item {
                Text(
                    text = "ACTIVE INTERFACES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MeshGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = MeshSpacing.xs)
                )
            }

            // Station Interface Card
            item {
                InterfaceDetailCard(
                    title = "Wi-Fi (STA)",
                    badgeText = "Active",
                    badgeColor = MeshGreen,
                    icon = Icons.Filled.Router,
                    ip = staInterface?.localIp?.ifBlank { "--" } ?: "--",
                    statusText = "Connected",
                    statusColor = MeshGreen,
                    thirdLabel = "SIGNAL STRENGTH",
                    thirdValue = { SignalBarsVisualizer() }
                )
            }

            // Access Point Interface Card
            item {
                InterfaceDetailCard(
                    title = "Wi-Fi (AP)",
                    badgeText = "Idle",
                    badgeColor = MeshMuted,
                    icon = Icons.Filled.SignalWifi4Bar,
                    ip = apInterface?.localIp?.ifBlank { "--" } ?: "--",
                    statusText = "Active",
                    statusColor = MeshGreen,
                    thirdLabel = "CLIENTS",
                    thirdValue = {
                        Text(
                            text = "0 Connected",
                            color = MeshTextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }

            // Capability Indicator Card
            item {
                val isSupported = uiState.isStaApSupported
                val cardColor = if (isSupported) MeshGreen.copy(alpha = 0.08f) else Color(0x22E57373)
                val borderColor = if (isSupported) MeshGreen.copy(alpha = 0.25f) else Color(0x55E57373)
                val iconColor = if (isSupported) MeshGreen else Color(0xFFE57373)

                Surface(
                    shape = MeshShapes.cardSmall,
                    color = cardColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = MeshSpacing.lg)
                        .border(1.dp, borderColor, MeshShapes.cardSmall)
                ) {
                    Row(
                        modifier = Modifier.padding(MeshSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSupported) Icons.Filled.CheckCircle else Icons.Filled.Info,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.padding(start = MeshSpacing.md)) {
                            Text(
                                text = if (isSupported) "STA + AP Supported" else "STA + AP Not Supported",
                                color = MeshTextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = capabilitySubtitle(isSupported),
                                color = MeshMuted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InterfaceDetailCard(
    title: String,
    badgeText: String,
    badgeColor: Color,
    icon: ImageVector,
    ip: String,
    statusText: String,
    statusColor: Color,
    thirdLabel: String,
    thirdValue: @Composable () -> Unit
) {
    Surface(
        shape = MeshShapes.cardSmall,
        color = MeshBg1,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MeshBorder, MeshShapes.cardSmall)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MeshBg2)
                    .padding(horizontal = MeshSpacing.md, vertical = MeshSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(18.dp))
                Text(
                    text = title,
                    color = MeshTextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = MeshSpacing.xs)
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = MeshShapes.chip,
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = MeshSpacing.sm, vertical = 4.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = MeshSpacing.md, vertical = MeshSpacing.xs)) {
                InterfaceRow(label = "IP ADDRESS") {
                    Text(
                        text = ip,
                        color = MeshTextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider(color = MeshBorder.copy(alpha = 0.5f), thickness = 1.dp)
                InterfaceRow(label = "STATUS") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MeshSpacing.xs)
                    ) {
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = CircleShape,
                            color = statusColor
                        ) {}
                        Text(
                            text = statusText,
                            color = statusColor,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                HorizontalDivider(color = MeshBorder.copy(alpha = 0.5f), thickness = 1.dp)
                InterfaceRow(label = thirdLabel, value = thirdValue)
            }
        }
    }
}

@Composable
private fun InterfaceRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MeshSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MeshMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        value()
    }
}

@Composable
private fun SignalBarsVisualizer() {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val heights = listOf(10.dp, 14.dp, 18.dp, 22.dp)
        heights.forEachIndexed { index, height ->
            val color = if (index < 3) MeshGreen else MeshBorder
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .height(height),
                shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
                color = color
            ) {}
        }
    }
}

private fun isStaInterface(name: String): Boolean {
    val value = name.lowercase()
    return value.contains("wlan") || value.contains("wifi") || value.contains("sta")
}

private fun isApInterface(name: String): Boolean {
    val value = name.lowercase()
    return value.contains("ap") || value.contains("softap") || value.contains("p2p")
}

private fun capabilitySubtitle(isStaApSupported: Boolean): String {
    return if (isStaApSupported) {
        "Your device supports simultaneous mesh hosting and station connection modes."
    } else {
        "This device currently reports no simultaneous station and access-point support."
    }
}

@Preview(showBackground = true)
@Composable
fun NetworkInterfacesScreenPreview() {
    NetworkInterfacesScreen()
}