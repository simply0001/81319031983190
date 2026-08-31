package com.pocketpass.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

actual fun platformAnimationsEnabled(): Boolean = !UIAccessibilityIsReduceMotionEnabled()

@Composable
actual fun stableStatusBarTop(density: Density): Int = 0

actual fun supportsAnimatedPatterns(): Boolean = true
