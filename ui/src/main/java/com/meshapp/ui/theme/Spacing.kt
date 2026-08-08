package com.meshapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Shared spacing scale so padding/margins stay consistent across screens
 * instead of ad-hoc dp values scattered through each Composable.
 */
object MeshSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

/**
 * Shared corner-radius scale. MeshApp favors soft, rounded surfaces
 * (cards, bubbles, inputs) over sharp edges.
 */
object MeshRadius {
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 28.dp
    val pill = 999.dp
}

object MeshShapes {
    val card = RoundedCornerShape(MeshRadius.lg)
    val cardSmall = RoundedCornerShape(MeshRadius.md)
    val chip = RoundedCornerShape(MeshRadius.pill)
    val input = RoundedCornerShape(MeshRadius.xl)
}
