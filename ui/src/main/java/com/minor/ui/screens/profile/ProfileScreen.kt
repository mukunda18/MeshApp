package com.minor.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsEthernet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minor.ui.components.MeshFooterNavigation
import com.minor.ui.theme.MeshGreen
import com.minor.ui.viewmodel.HomeViewModel

@Composable
fun ProfileScreen(
    viewModel: HomeViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateChats: () -> Unit,
    onNavigateNearbyNodes: () -> Unit,
    onNavigateNetworkInterfaces: () -> Unit,
    onNavigateAbout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = ProfileBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111316))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    text = "Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE7EAEC),
                    modifier = Modifier.weight(1f)
                )
                Box {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F1418))
                            .border(1.5.dp, MeshGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.profile.avatarInitials,
                            color = MeshGreen,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(MeshGreen)
                            .border(2.dp, Color(0xFF111316), CircleShape)
                    )
                }
            }
        },
        bottomBar = {
            MeshFooterNavigation(
                currentRoute = "profile",
                onHome = onNavigateHome,
                onChats = onNavigateChats,
                onProfile = {}
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MeshGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.profile.avatarInitials,
                            color = Color(0xFF053114),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = uiState.profile.name,
                        color = Color(0xFFE7EAEC),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "NODE ID:",
                            color = Color(0xFFAAB1A8),
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = " Unavailable",
                            color = MeshGreen,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item {
                SectionTitle("DEVICE INFO")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF0E1012),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF1D2B3F), RoundedCornerShape(18.dp))
                ) {
                    Column {
                        InfoRow("Device Name", uiState.profile.name, Icons.Filled.DeviceHub)
                        DividerLine()
                        InfoRow("Node ID", "Unavailable", Icons.Filled.SettingsEthernet)
                        DividerLine()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SettingsEthernet,
                                contentDescription = null,
                                tint = Color(0xFFA9B0A8),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Status",
                                color = Color(0xFFE4E8E9),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MeshGreen)
                            )
                            Text(
                                text = " Online",
                                color = MeshGreen,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item {
                SectionTitle("OPTIONS")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF0E1012),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF1D2B3F), RoundedCornerShape(18.dp))
                ) {
                    Column {
                        OptionRow("View Nearby Nodes", Icons.Filled.DeviceHub, onNavigateNearbyNodes)
                        DividerLine()
                        OptionRow("Network Interfaces", Icons.Filled.Router, onNavigateNetworkInterfaces)
                        DividerLine()
                        OptionRow("About", Icons.Filled.Info, onNavigateAbout)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(
        text = value,
        color = MeshGreen,
        style = MaterialTheme.typography.labelLarge,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    )
}

@Composable
private fun InfoRow(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFA9B0A8),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            color = Color(0xFFE4E8E9),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = Color(0xFFBEC4BE),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun OptionRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF22262A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFFB9C1B7), modifier = Modifier.size(20.dp))
        }
        Text(
            text = title,
            color = Color(0xFFE6E9EB),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 14.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF8B948B), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 14.dp)
            .background(Color(0xFF1F272D))
    )
}

private val ProfileBg = Color(0xFF090B0D)
