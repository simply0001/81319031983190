package com.pocketpass.app.ui

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density

actual fun platformAnimationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun stableStatusBarTop(density: Density): Int =
    WindowInsets.statusBarsIgnoringVisibility.getTop(density)

actual fun supportsAnimatedPatterns(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
