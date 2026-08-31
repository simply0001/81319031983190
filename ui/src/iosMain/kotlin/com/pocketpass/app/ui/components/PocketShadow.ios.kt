package com.pocketpass.app.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.roundToInt
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect

actual class RoundedShadowMask internal constructor(
    internal val size: Size,
    internal val radiusPx: Float,
    internal val blurPx: Float,
)

actual fun roundedShadowMask(size: Size, radiusPx: Float, blurPx: Float): RoundedShadowMask =
    RoundedShadowMask(size, radiusPx, blurPx.coerceAtLeast(0.5f))

actual fun DrawScope.drawRoundedShadow(mask: RoundedShadowMask, alpha: Float, offsetY: Float) {
    val paint = Paint().apply {
        color = ((alpha * 255).roundToInt().coerceIn(0, 255) shl 24)
        isAntiAlias = true
        // Skia's radius-to-sigma rule, matching BlurMaskFilter's interpretation on Android.
        maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, mask.blurPx * 0.57735f + 0.5f)
    }
    drawContext.canvas.nativeCanvas.drawRRect(
        RRect.makeXYWH(0f, offsetY, mask.size.width, mask.size.height, mask.radiusPx),
        paint,
    )
}
