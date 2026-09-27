package com.pocketpass.app.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope

expect class RoundedShadowMask

expect fun roundedShadowMask(size: Size, radiusPx: Float, blurPx: Float): RoundedShadowMask

expect fun DrawScope.drawRoundedShadow(mask: RoundedShadowMask, alpha: Float, offsetY: Float = 0f)
