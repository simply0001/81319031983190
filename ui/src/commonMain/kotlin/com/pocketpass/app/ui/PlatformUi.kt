package com.pocketpass.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density

// True when the platform wants looping decorative animations (the user has not disabled
// animations system-wide).
expect fun platformAnimationsEnabled(): Boolean

// The height of the status bar even while it is hidden. Android is the only platform that
// reports hidden bars separately; elsewhere the safe-drawing insets already cover it.
@Composable
expect fun stableStatusBarTop(density: Density): Int

// True when the runtime shader behind the animated background patterns is available.
expect fun supportsAnimatedPatterns(): Boolean
