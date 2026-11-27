package com.meshapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MeshBg0: Color
    @Composable get() = MaterialTheme.colorScheme.background
val MeshBg1: Color
    @Composable get() = MaterialTheme.colorScheme.surface
val MeshBg2: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val MeshBg3: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

val MeshBorder: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant
val MeshBorderStrong: Color
    @Composable get() = MaterialTheme.colorScheme.outline
val MeshDivider: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant

val MeshTextPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
val MeshTextSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val MeshTextTertiary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

val MeshGreen: Color
    @Composable get() = MaterialTheme.colorScheme.primary
val MeshGreenDark: Color
    @Composable get() = MaterialTheme.colorScheme.primary
val MeshGreenMuted: Color
    @Composable get() = MaterialTheme.colorScheme.primaryContainer
val MeshGreenOnAccent: Color
    @Composable get() = MaterialTheme.colorScheme.onPrimary

val MeshSurface: Color
    @Composable get() = MeshBg1
val MeshMuted: Color
    @Composable get() = MeshTextSecondary
val MeshOnline: Color
    @Composable get() = MeshGreen
val MeshOffline: Color
    @Composable get() = MaterialTheme.colorScheme.outline
val MeshDanger: Color
    @Composable get() = MaterialTheme.colorScheme.error
val MeshDangerMuted: Color
    @Composable get() = MaterialTheme.colorScheme.errorContainer
val MeshWarning: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary
val MeshWarningMuted: Color
    @Composable get() = MaterialTheme.colorScheme.tertiaryContainer

val MeshBubbleInbound: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val MeshBubbleInboundBorder: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant
val MeshBubbleOutbound: Color
    @Composable get() = MaterialTheme.colorScheme.secondaryContainer
val MeshBubbleOutboundBorder: Color
    @Composable get() = MaterialTheme.colorScheme.outline

val MeshLightBg0: Color
    @Composable get() = MaterialTheme.colorScheme.background
val MeshLightBg1: Color
    @Composable get() = MaterialTheme.colorScheme.surface
val MeshLightBorder: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant
val MeshLightTextPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
val MeshLightTextSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
