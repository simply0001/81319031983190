package com.pocketpass.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.pocketpass.app.ui.widget.WidgetAssets
import com.pocketpass.app.ui.widget.widgetGlyph
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetPainter(context: Context) {
    private val appContext = context.applicationContext

    suspend fun paint(
        design: WidgetDesign,
        snapshot: WidgetSnapshot,
        widthPx: Int,
        heightPx: Int,
        nowEpochMillis: Long,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val theme = design.theme()
        val heroBlock = design.hero
        val heroText = heroBlock?.let { WidgetBlockValues.heroNumber(it, snapshot, nowEpochMillis) } ?: "PocketPass"
        val tiles = design.tiles.filterNotNull()
        val frames = WidgetLayout.frames(design.size, w.toFloat(), h.toFloat(), tiles.size, heroText.length)
        val heavy = WidgetAssets.typeface(appContext, HEAVY_FONT)
        val regular = WidgetAssets.typeface(appContext, REGULAR_FONT)
        val heroGlyph = when (heroBlock) {
            null -> null
            WidgetBlock.Profile -> {
                val portrait = snapshot.portraitFileName?.let { WidgetSnapshotStore.portraitFile(appContext) }
                WidgetAssets.avatar(appContext, portrait, frames.heroGlyph.width.roundToInt(), dark = false)
            }
            else -> glyph(heroBlock, frames.heroGlyph.width)
        }
        val tileGlyphs = tiles.zip(frames.tiles).map { (block, frame) -> glyph(block, frame.glyph.width) }
        val pattern = WidgetAssets.pattern(appContext)
        return withContext(Dispatchers.Default) {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawCard(canvas, frames, theme, pattern)
            heroGlyph?.let { drawBitmap(canvas, it, frames.heroGlyph) }
            drawText(canvas, heroText, frames.heroValue, heavy, theme.ink.toInt())
            frames.heroLabel?.let { box ->
                drawText(canvas, heroBlock?.let { WidgetBlockValues.heroLabel(it, snapshot) } ?: "", box, regular, theme.softInk.toInt())
            }
            frames.divider?.let { drawDivider(canvas, it) }
            tiles.forEachIndexed { index, block ->
                val frame = frames.tiles[index]
                tileGlyphs[index]?.let { drawBitmap(canvas, it, frame.glyph) }
                val value = if (frame.label != null) {
                    WidgetBlockValues.tile(block, snapshot, nowEpochMillis).value
                } else {
                    WidgetBlockValues.tileNumber(block, snapshot, nowEpochMillis)
                }
                drawText(canvas, value, frame.value, heavy, theme.ink.toInt())
                frame.label?.let { drawText(canvas, block.shortLabel, it, regular, theme.softInk.toInt()) }
            }
            bitmap
        }
    }

    suspend fun paintNotice(widthPx: Int, heightPx: Int, title: String, subtitle: String): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val frames = WidgetLayout.frames(WidgetSize.Mini, w.toFloat(), h.toFloat(), 0)
        val theme = WidgetTheme.Green
        val heavy = WidgetAssets.typeface(appContext, HEAVY_FONT)
        val regular = WidgetAssets.typeface(appContext, REGULAR_FONT)
        val pattern = WidgetAssets.pattern(appContext)
        return withContext(Dispatchers.Default) {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawCard(canvas, frames, theme, pattern)
            val u = minOf(w, h).toFloat()
            val titleBox = WidgetTextBox(WidgetRect(u * 0.08f, h / 2f - u * 0.16f, w - u * 0.16f, u * 0.16f), u * 0.11f, WidgetTextAlign.Center)
            val subtitleBox = WidgetTextBox(WidgetRect(u * 0.08f, h / 2f + u * 0.02f, w - u * 0.16f, u * 0.12f), u * 0.07f, WidgetTextAlign.Center, heavy = false)
            drawText(canvas, title, titleBox, heavy, theme.ink.toInt())
            drawText(canvas, subtitle, subtitleBox, regular, theme.softInk.toInt())
            bitmap
        }
    }

    private suspend fun glyph(block: WidgetBlock, sizePx: Float): Bitmap? {
        val width = sizePx.roundToInt().coerceAtLeast(1)
        return WidgetAssets.icon(appContext, block.widgetGlyph(), width, (width * GLYPH_ASPECT).roundToInt())
    }

    private fun drawCard(canvas: Canvas, frames: WidgetFrames, theme: WidgetTheme, pattern: Bitmap?) {
        val bounds = RectF(0f, 0f, frames.width, frames.height)
        val clip = Path().apply { addRoundRect(bounds, frames.cornerRadius, frames.cornerRadius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        val background = theme.background.toInt()
        canvas.drawRect(
            bounds,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, frames.height,
                    intArrayOf(background, background, Color.WHITE),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )
        if (pattern != null) {
            val size = frames.patternTile
            val left = (frames.width - size) / 2f
            val top = (frames.height - size) / 2f
            canvas.drawBitmap(
                pattern,
                null,
                RectF(left, top, left + size, top + size),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    blendMode = BlendMode.COLOR_BURN
                    alpha = (PATTERN_OPACITY * 255).roundToInt()
                },
            )
        }
        val inset = frames.border / 2f
        canvas.drawRoundRect(
            RectF(inset, inset, frames.width - inset, frames.height - inset),
            frames.cornerRadius - inset,
            frames.cornerRadius - inset,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = frames.border
                color = Color.argb((0.38f * 255).roundToInt(), 255, 255, 255)
            },
        )
        canvas.restore()
    }

    private fun drawDivider(canvas: Canvas, rect: WidgetRect) {
        val radius = minOf(rect.width, rect.height) / 2f
        canvas.drawRoundRect(
            RectF(rect.x, rect.y, rect.right, rect.bottom),
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb((0.61f * 255).roundToInt(), 255, 255, 255) },
        )
    }

    private fun drawBitmap(canvas: Canvas, bitmap: Bitmap, rect: WidgetRect) {
        val height = rect.width * bitmap.height / bitmap.width
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(rect.x, rect.y, rect.right, rect.y + height),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
    }

    private fun drawText(canvas: Canvas, text: String, box: WidgetTextBox, typeface: Typeface, color: Int) {
        if (text.isBlank()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.color = color
            textSize = box.fontSize
        }
        val measured = paint.measureText(text)
        if (measured > box.rect.width && measured > 0f) {
            paint.textSize = box.fontSize * box.rect.width / measured
        }
        val width = paint.measureText(text)
        val x = when (box.align) {
            WidgetTextAlign.Start -> box.rect.x
            WidgetTextAlign.Center -> box.rect.centerX - width / 2f
            WidgetTextAlign.End -> box.rect.right - width
        }
        val metrics = paint.fontMetrics
        val baseline = box.rect.centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, x, baseline, paint)
    }

    companion object {
        const val GLYPH_ASPECT = 123.241f / 119f
        private const val PATTERN_OPACITY = 0.37f
        private const val HEAVY_FONT = "font/rubik_800.ttf"
        private const val REGULAR_FONT = "font/rubik_600.ttf"
    }
}
