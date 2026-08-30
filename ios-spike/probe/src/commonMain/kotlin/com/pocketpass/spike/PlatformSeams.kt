package com.pocketpass.spike

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density

@Composable
internal expect fun PatternSurface(
    topColor: Color,
    bottomColor: Color,
    holdFraction: Float,
    designWidth: Float,
    designHeight: Float,
    modifier: Modifier,
    geometryWidth: Float,
)

@Composable
internal expect fun stableStatusBarTop(density: Density): Int
