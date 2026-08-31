package com.pocketpass.app.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.roundToInt

actual class RoundedShadowMask internal constructor(
    private val bitmap: android.graphics.Bitmap,
    private val destination: android.graphics.RectF,
) {
    fun draw(canvas: android.graphics.Canvas, paint: android.graphics.Paint, offsetY: Float = 0f) {
        if (offsetY == 0f) {
            canvas.drawBitmap(bitmap, null, destination, paint)
            return
        }
        canvas.save()
        canvas.translate(0f, offsetY)
        canvas.drawBitmap(bitmap, null, destination, paint)
        canvas.restore()
    }
}

actual fun roundedShadowMask(size: Size, radiusPx: Float, blurPx: Float): RoundedShadowMask {
    val blur = blurPx.coerceAtLeast(0.5f)
    val pad = blur * SHADOW_MASK_PADDING
    val scale = SHADOW_MASK_SCALE
    val bitmap = android.graphics.Bitmap.createBitmap(
        kotlin.math.ceil((size.width + pad * 2f) * scale).toInt().coerceAtLeast(1),
        kotlin.math.ceil((size.height + pad * 2f) * scale).toInt().coerceAtLeast(1),
        android.graphics.Bitmap.Config.ALPHA_8,
    )
    android.graphics.Canvas(bitmap).drawRoundRect(
        pad * scale,
        pad * scale,
        (pad + size.width) * scale,
        (pad + size.height) * scale,
        radiusPx * scale,
        radiusPx * scale,
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter((blur * scale).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
        },
    )
    return RoundedShadowMask(
        bitmap,
        android.graphics.RectF(-pad, -pad, size.width + pad, size.height + pad),
    )
}

actual fun DrawScope.drawRoundedShadow(mask: RoundedShadowMask, alpha: Float, offsetY: Float) {
    mask.draw(drawContext.canvas.nativeCanvas, shadowPaint(alpha), offsetY)
}

fun shadowPaint(alpha: Float): android.graphics.Paint =
    android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
        color = android.graphics.Color.argb((alpha * 255).roundToInt(), 0, 0, 0)
    }
