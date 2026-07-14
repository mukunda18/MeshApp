package com.minor.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minor.ui.components.MeshFooterNavigation
import com.minor.ui.theme.MeshAccentBlue
import com.minor.ui.theme.MeshBackground
import com.minor.ui.theme.MeshBorder
import com.minor.ui.theme.MeshGreen
import com.minor.ui.theme.MeshHeader
import com.minor.ui.theme.MeshMuted
import com.minor.ui.theme.MeshSurface
import com.minor.ui.theme.MeshTextPrimary
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
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    val nodeId = uiState.profile.nodeId

    Scaffold(
        containerColor = MeshBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MeshBackground)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MeshTextPrimary,
                    modifier = Modifier.weight(1f)
                )
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                ProfileCard(
                    name = uiState.profile.name,
                    initials = uiState.profile.avatarInitials,
                    nodeId = nodeId,
                    onCopyNodeId = {
                        clipboardManager.setText(AnnotatedString(nodeId))
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                MeshControlCard(
                    isMeshOn = uiState.isMeshOn,
                    onToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.toggleMesh()
                    }
                )
            }

            item { SectionLabel("Options") }

            item {
                OptionsCard {
                    OptionRow("Nearby Nodes", Icons.Filled.DeviceHub, onNavigateNearbyNodes)
                    DividerLine()
                    OptionRow("Network Interfaces", Icons.Filled.Router, onNavigateNetworkInterfaces)
                    DividerLine()
                    OptionRow("About", Icons.Filled.Info, onNavigateAbout)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ProfileCard(
    name: String,
    initials: String,
    nodeId: String,
    onCopyNodeId: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MeshSurface)
            .border(1.dp, MeshBorder, RoundedCornerShape(20.dp))
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MeshHeader),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = MeshTextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Column(modifier = Modifier.padding(start = 18.dp)) {
                Text(
                    text = name,
                    color = MeshTextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Device profile",
                    color = MeshMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "NODE ID",
            color = MeshMuted,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MeshHeader)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = nodeId,
                color = MeshTextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopyNodeId, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy node id",
                    tint = MeshAccentBlue,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MeshControlCard(
    isMeshOn: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MeshSurface)
            .border(1.dp, MeshBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isMeshOn) MeshGreen else MeshMuted)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isMeshOn) "Mesh Active" else "Mesh Offline",
                    color = if (isMeshOn) MeshGreen else MeshMuted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Controls the local mesh networking service",
                color = MeshMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, start = 16.dp)
            )
        }

        Switch(
            checked = isMeshOn,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MeshBackground,
                checkedTrackColor = MeshGreen,
                checkedBorderColor = MeshGreen,
                uncheckedThumbColor = MeshMuted,
                uncheckedTrackColor = MeshHeader,
                uncheckedBorderColor = MeshBorder
            )
        )
    }
}

@Composable
private fun SectionLabel(value: String) {
    Text(
        text = value,
        color = MeshMuted,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun OptionsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MeshSurface)
            .border(1.dp, MeshBorder, RoundedCornerShape(20.dp))
    ) {
        content()
    }
}

@Composable
private fun OptionRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MeshMuted, modifier = Modifier.size(20.dp))
        Text(
            text = title,
            color = MeshTextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MeshMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 18.dp)
            .background(MeshBorder)
    )
}