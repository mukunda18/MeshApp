package com.meshapp.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Core palette for MeshApp.
 *
 * Design direction: dark, calm, "premium hardware" feel rather than a neon/hacker
 * look. Backgrounds are layered near-blacks with a slight cool tint; the mesh green
 * is desaturated so it reads as a signal color, not a highlighter.
 */

// --- Surfaces (layered from deepest to most elevated) ---
val MeshBg0 = Color(0xFF0B0D10)          // App / screen background
val MeshBg1 = Color(0xFF121519)          // Base surface (cards, list rows)
val MeshBg2 = Color(0xFF191D22)          // Elevated surface (headers, sheets)
val MeshBg3 = Color(0xFF20252B)          // Top-most chrome (top bars, inputs)

// --- Borders / separators ---
val MeshBorder = Color(0xFF262B32)
val MeshBorderStrong = Color(0xFF343B44)
val MeshDivider = Color(0xFF1E2329)

// --- Text ---
val MeshTextPrimary = Color(0xFFE9ECEF)
val MeshTextSecondary = Color(0xFFA1A9B2)
val MeshTextTertiary = Color(0xFF6E7680)

// --- Brand accent: a softened, slightly teal-leaning green ---
val MeshGreen = Color(0xFF3ECF8E)        // Primary accent (buttons, active states)
val MeshGreenDark = Color(0xFF23A06A)    // Pressed / darker variant
val MeshGreenMuted = Color(0xFF1C3A2D)   // Low-opacity fill / chips background
val MeshGreenOnAccent = Color(0xFF08221A) // Text/icon color drawn ON a green fill

// --- Legacy alias kept for compatibility with existing references ---
val MeshSurface = MeshBg1
val MeshMuted = MeshTextSecondary

// --- Status / semantic colors ---
val MeshOnline = MeshGreen
val MeshOffline = Color(0xFF5B636C)
val MeshDanger = Color(0xFFE0636B)
val MeshDangerMuted = Color(0xFF3A2226)
val MeshWarning = Color(0xFFE3B45B)
val MeshWarningMuted = Color(0xFF3B3222)

// --- Chat bubbles ---
val MeshBubbleInbound = Color(0xFF1B2029)
val MeshBubbleInboundBorder = Color(0xFF2A313C)
val MeshBubbleOutbound = Color(0xFF17332A)
val MeshBubbleOutboundBorder = Color(0xFF285240)

// --- Light theme (secondary support, same accent family) ---
val MeshLightBg0 = Color(0xFFF6F8F7)
val MeshLightBg1 = Color(0xFFFFFFFF)
val MeshLightBorder = Color(0xFFE1E6E3)
val MeshLightTextPrimary = Color(0xFF14201B)
val MeshLightTextSecondary = Color(0xFF5B6A63)
