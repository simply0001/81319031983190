package com.pocketpass.spike

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density

@Composable
internal actual fun PatternSurface(
    topColor: Color,
    bottomColor: Color,
    holdFraction: Float,
    designWidth: Float,
    designHeight: Float,
    modifier: Modifier,
    geometryWidth: Float,
) {
    SkiaPatternSurface(
        topColor = topColor,
        bottomColor = bottomColor,
        holdFraction = holdFraction,
        designWidth = designWidth,
        designHeight = designHeight,
        modifier = modifier,
        geometryWidth = geometryWidth,
    )
}

@Composable
internal actual fun stableStatusBarTop(density: Density): Int = 0
