package com.minor.ui.screens.networkinterfaces

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minor.ui.viewmodel.NetworkInterfacesViewModel

@Composable
fun NetworkInterfacesScreen(
    viewModel: NetworkInterfacesViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val staInterface = uiState.interfaces.firstOrNull { isStaInterface(it.interfaceName) }
    val apInterface = uiState.interfaces.firstOrNull { isApInterface(it.interfaceName) }

    Scaffold(
        containerColor = ScreenBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ScreenBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFFE3E5E7)
                    )
                }
                Text(
                    text = "Network Interfaces",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFE7E8EA),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = MeshAccent
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = HeroBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MeshAccent.copy(alpha = 0.2f))
                                    .border(1.dp, MeshAccent.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Wifi,
                                    contentDescription = null,
                                    tint = MeshAccent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Column(modifier = Modifier.padding(start = 14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Wi-Fi",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFFE4E6E7),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = " Active",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MeshAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Connected to local mesh infrastructure.",
                                    color = Color(0xFFB6BCBF),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "INTERFACES",
                    style = MaterialTheme.typography.labelLarge,
                    color = MeshAccent,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                InterfaceDetailCard(
                    title = "Wi-Fi (STA)",
                    badgeText = "Active",
                    badgeColor = MeshAccent,
                    icon = Icons.Filled.Router,
                    ip = staInterface?.localIp ?: uiState.interfaces.firstOrNull()?.localIp.orEmpty().ifBlank { "--" },
                    statusText = "Connected",
                    statusColor = MeshAccent,
                    thirdLabel = "SIGNAL  STRENGTH",
                    thirdValue = {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.width(4.dp).height(14.dp).background(MeshAccent, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
                            Box(Modifier.width(4.dp).height(18.dp).background(MeshAccent, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
                            Box(Modifier.width(4.dp).height(22.dp).background(MeshAccent, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
                            Box(Modifier.width(4.dp).height(26.dp).background(Color(0xFF6D7A74), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
                        }
                    }
                )
            }

            item {
                InterfaceDetailCard(
                    title = "Wi-Fi (AP)",
                    badgeText = "Idle",
                    badgeColor = Color(0xFF8AD8C3),
                    icon = Icons.Filled.SignalWifi4Bar,
                    ip = apInterface?.localIp ?: uiState.interfaces.getOrNull(1)?.localIp.orEmpty().ifBlank { "--" },
                    statusText = "Idle",
                    statusColor = Color(0xFF8AD8C3),
                    thirdLabel = "CLIENTS",
                    thirdValue = {
                        Text(
                            text = "0 Connected",
                            color = Color(0xFFD8DCDD),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF102119),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 24.dp)
                        .border(1.dp, MeshAccent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MeshAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = "STA + AP Supported",
                                color = Color(0xFFE2E6E4),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = capabilitySubtitle(uiState.isStaApSupported, uiState.isLikelySupported),
                                color = Color(0xFFB7C1BB),
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
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HeaderBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(18.dp))
                Text(
                    text = title,
                    color = Color(0xFFE4E7E9),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 10.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(badgeColor.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                InterfaceRow(label = "IP ADDRESS") {
                    Text(
                        text = ip,
                        color = Color(0xFFE4E7E9),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp
                    )
                }
                DividerLine()
                InterfaceRow(label = "STATUS") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = " $statusText",
                            color = statusColor,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                DividerLine()
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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFFA9B0AF),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        value()
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color(0xFF2A353A), Color.Transparent)
                )
            )
    )
}

private fun isStaInterface(name: String): Boolean {
    val value = name.lowercase()
    return value.contains("wlan") || value.contains("wifi") || value.contains("sta")
}

private fun isApInterface(name: String): Boolean {
    val value = name.lowercase()
    return value.contains("ap") || value.contains("softap") || value.contains("p2p")
}

private fun capabilitySubtitle(isStaApSupported: Boolean, isLikelySupported: Boolean): String {
    return when {
        isStaApSupported -> "Your device supports simultaneous mesh hosting and station connection modes."
        isLikelySupported -> "Your hardware likely supports concurrent modes, but official support is not exposed."
        else -> "This device currently reports no simultaneous station and access-point support."
    }
}

private val ScreenBg = Color(0xFF090B0D)
private val MeshAccent = Color(0xFF44E67B)
private val CardBg = Color(0xFF0C0E10)
private val CardBorder = Color(0xFF1E2E43)
private val HeroBg = Color(0xFF0E1113)
private val HeaderBg = Color(0xFF1A1A1F)

@Preview(showBackground = true)
@Composable
fun NetworkInterfacesScreenPreview() {
    NetworkInterfacesScreen(onBack = {})
}
