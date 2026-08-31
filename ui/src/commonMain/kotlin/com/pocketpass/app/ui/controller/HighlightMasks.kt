package com.pocketpass.app.ui.controller

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.roundToInt

// The focus ring's soft outer glow and inner gloss are pre-blurred once into small alpha
// masks, then stretched over the ring. Only the blur filter itself differs per platform.
internal expect fun Paint.applyBlurMaskFilter(radius: Float)

internal class HighlightMask(
    val glow: ImageBitmap,
    val gloss: ImageBitmap,
    val pad: Float,
    val width: Int,
    val height: Int,
    val radius: Int,
    val scale: Float,
)

internal class HighlightMaskCache {
    private var current: HighlightMask? = null

    fun forRing(width: Float, height: Float, innerRadius: Float, scale: Float): HighlightMask {
        val w = width.roundToInt()
        val h = height.roundToInt()
        val r = innerRadius.roundToInt()
        current?.let { if (it.width == w && it.height == h && it.radius == r && it.scale == scale) return it }
        return buildHighlightMask(w.toFloat(), h.toFloat(), r.toFloat(), scale).also { current = it }
    }
}

internal fun DrawScope.drawHighlightGlow(
    masks: HighlightMaskCache,
    bounds: Rect,
    innerRadius: Float,
    scale: Float,
) {
    val mask = masks.forRing(bounds.width, bounds.height, innerRadius, scale)
    drawMask(mask.glow, mask.pad, bounds, ColorFilter.tint(Color(HIGHLIGHT_GLOW_COLOR)), BlendMode.SrcOver)
}

internal fun DrawScope.drawHighlightGloss(
    masks: HighlightMaskCache,
    bounds: Rect,
    innerRadius: Float,
    scale: Float,
) {
    val mask = masks.forRing(bounds.width, bounds.height, innerRadius, scale)
    drawMask(mask.gloss, mask.pad, bounds, ColorFilter.tint(Color.White), BlendMode.Overlay)
}

private fun DrawScope.drawMask(
    image: ImageBitmap,
    pad: Float,
    bounds: Rect,
    colorFilter: ColorFilter,
    blendMode: BlendMode,
) {
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(
            (bounds.left - pad).roundToInt(),
            (bounds.top - pad).roundToInt(),
        ),
        dstSize = IntSize(
            (bounds.width + pad * 2f).roundToInt(),
            (bounds.height + pad * 2f).roundToInt(),
        ),
        colorFilter = colorFilter,
        blendMode = blendMode,
    )
}

private fun buildHighlightMask(width: Float, height: Float, innerRadius: Float, scale: Float): HighlightMask {
    val stroke = HIGHLIGHT_STROKE * scale
    val glowBlur = HIGHLIGHT_GLOW_BLUR * scale
    val glossBlur = HIGHLIGHT_GLOSS_BLUR * scale
    val pad = stroke + maxOf(glowBlur * 2.5f, glossBlur * 3f)
    val s = HIGHLIGHT_MASK_SCALE
    val maskWidth = ceil((width + pad * 2f) * s).toInt().coerceAtLeast(1)
    val maskHeight = ceil((height + pad * 2f) * s).toInt().coerceAtLeast(1)
    val base = Rect(pad * s, pad * s, (pad + width) * s, (pad + height) * s)
    fun inflated(amount: Float) = Rect(
        base.left - amount * s,
        base.top - amount * s,
        base.right + amount * s,
        base.bottom + amount * s,
    )
    fun roundRectPath(rect: Rect, radius: Float) = Path().apply {
        addRoundRect(RoundRect(rect, CornerRadius(radius * s)))
    }
    val outerPath = roundRectPath(inflated(stroke), innerRadius + stroke)
    val innerPath = roundRectPath(base, innerRadius)

    val glow = ImageBitmap(maskWidth, maskHeight, ImageBitmapConfig.Alpha8)
    Canvas(glow).apply {
        clipPath(outerPath, ClipOp.Difference)
        val ring = inflated(stroke / 2f)
        val ringRadius = (innerRadius + stroke / 2f) * s
        drawRoundRect(
            ring.left,
            ring.top,
            ring.right,
            ring.bottom,
            ringRadius,
            ringRadius,
            Paint().apply {
                isAntiAlias = true
                style = PaintingStyle.Stroke
                strokeWidth = stroke * s
                applyBlurMaskFilter((glowBlur * s).coerceAtLeast(0.5f))
            },
        )
    }

    val gloss = ImageBitmap(maskWidth, maskHeight, ImageBitmapConfig.Alpha8)
    Canvas(gloss).apply {
        val ringPath = Path().apply {
            fillType = PathFillType.EvenOdd
            addPath(outerPath)
            addPath(innerPath)
        }
        clipPath(ringPath)
        val glossPath = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(inflated(stroke + glossBlur * 3f))
            addPath(outerPath)
            addPath(innerPath)
        }
        drawPath(
            glossPath,
            Paint().apply {
                isAntiAlias = true
                applyBlurMaskFilter((glossBlur * s).coerceAtLeast(0.5f))
            },
        )
    }

    return HighlightMask(
        glow = glow,
        gloss = gloss,
        pad = pad,
        width = width.roundToInt(),
        height = height.roundToInt(),
        radius = innerRadius.roundToInt(),
        scale = scale,
    )
}
