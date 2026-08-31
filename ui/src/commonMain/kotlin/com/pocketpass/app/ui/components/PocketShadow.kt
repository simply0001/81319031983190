package com.pocketpass.app.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope

// A pre-blurred rounded-rectangle silhouette. Android rasterises it once into a small
// alpha bitmap; Skia platforms blur the round rect directly with a mask filter.
expect class RoundedShadowMask

expect fun roundedShadowMask(size: Size, radiusPx: Float, blurPx: Float): RoundedShadowMask

expect fun DrawScope.drawRoundedShadow(mask: RoundedShadowMask, alpha: Float, offsetY: Float = 0f)
