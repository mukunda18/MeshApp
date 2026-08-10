package com.meshapp.ui.screens.profile

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshapp.ui.components.MeshFooterNavigation
import com.meshapp.ui.components.StatusDot
import com.meshapp.ui.theme.MeshBg0
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
import com.meshapp.ui.viewmodel.HomeViewModel

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
    var showEditNameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MeshBg0,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MeshBg2)
                    .padding(horizontal = MeshSpacing.sm, vertical = MeshSpacing.xs),
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
                    text = "Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MeshTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MeshBg3)
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
                    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                        StatusDot(isOnline = true, size = 12.dp)
                    }
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
                .padding(horizontal = MeshSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MeshSpacing.sm)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MeshSpacing.sm),
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
                            color = MeshGreenOnAccent,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = uiState.profile.name,
                        color = MeshTextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = MeshSpacing.xs + 2.dp)
                    )
                    Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "NODE ID:",
                            color = MeshMuted,
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = " ${uiState.profile.nodeId}",
                            color = MeshGreen,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            item {
                SectionTitle("DEVICE INFO")
            }

            item {
                Surface(
                    shape = MeshShapes.card,
                    color = MeshBg1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MeshBorder, MeshShapes.card)
                ) {
                    Column {
                        InfoRow(
                            title = "Device Name",
                            value = uiState.profile.name,
                            icon = Icons.Filled.DeviceHub,
                            onClick = {
                                newName = uiState.profile.name
                                showEditNameDialog = true
                            }
                        )
                        DividerLine()
                        InfoRow("Node ID", uiState.profile.nodeId, Icons.Filled.SettingsEthernet)
                        DividerLine()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MeshSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SettingsEthernet,
                                contentDescription = null,
                                tint = MeshMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Status",
                                color = MeshTextPrimary,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = MeshSpacing.sm)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            StatusDot(isOnline = true, size = 8.dp)
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
                    shape = MeshShapes.card,
                    color = MeshBg1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MeshBorder, MeshShapes.card)
                        .padding(bottom = MeshSpacing.sm)
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

    if (showEditNameDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Change Device Name", color = MeshTextPrimary) },
            text = {
                Column {
                    Text(
                        "This name is broadcast to nearby nodes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeshMuted,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    TextField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MeshBg2,
                            unfocusedContainerColor = MeshBg2,
                            focusedTextColor = MeshTextPrimary,
                            unfocusedTextColor = MeshTextPrimary,
                            cursorColor = MeshGreen,
                            focusedIndicatorColor = MeshGreen
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.updateName(newName.trim())
                            showEditNameDialog = false
                        }
                    }
                ) {
                    Text("Save", color = MeshGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = MeshMuted)
                }
            },
            containerColor = MeshBg1
        )
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
        modifier = Modifier.padding(top = MeshSpacing.xs, start = 4.dp)
    )
}

@Composable
private fun InfoRow(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(MeshSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MeshMuted,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            color = MeshTextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = MeshSpacing.sm)
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                color = MeshMuted,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MeshMuted.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp).padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(MeshSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MeshShapes.cardSmall)
                .background(MeshBg3),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MeshMuted, modifier = Modifier.size(20.dp))
        }
        Text(
            text = title,
            color = MeshTextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = MeshSpacing.sm + 2.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MeshMuted, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = MeshSpacing.sm + 2.dp)
            .background(MeshBorder)
    )
}
