package com.pocketpass.app.widget

import kotlin.math.max
import kotlin.math.min

data class WidgetRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height
    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f
}

enum class WidgetTextAlign { Start, Center, End }

data class WidgetTextBox(
    val rect: WidgetRect,
    val fontSize: Float,
    val align: WidgetTextAlign,
    val heavy: Boolean = true,
)

data class WidgetTileFrame(
    val glyph: WidgetRect,
    val value: WidgetTextBox,
    val label: WidgetTextBox? = null,
)

data class WidgetFrames(
    val width: Float,
    val height: Float,
    val cornerRadius: Float,
    val border: Float,
    val patternTile: Float,
    val heroGlyph: WidgetRect,
    val heroValue: WidgetTextBox,
    val heroLabel: WidgetTextBox?,
    val divider: WidgetRect?,
    val tiles: List<WidgetTileFrame>,
)

object WidgetLayout {
    private const val LINE = 1.2f
    private const val PATTERN_SCALE = 1.7f
    private const val CHAR_EM = 0.62f

    private fun patternSize(w: Float, h: Float): Float = max(PATTERN_SCALE * min(w, h), max(w, h))

    fun frames(size: WidgetSize, width: Float, height: Float, tileCount: Int, heroChars: Int = 2): WidgetFrames =
        when (size) {
            WidgetSize.Mini -> mini(width, height, heroChars)
            WidgetSize.Small -> small(width, height, tileCount, heroChars)
            WidgetSize.Wide -> wide(width, height, tileCount, heroChars)
            WidgetSize.Tall -> tall(width, height, tileCount, heroChars)
        }

    private fun mini(w: Float, h: Float, heroChars: Int): WidgetFrames {
        val u = min(w, h)
        val glyph = 0.462f * u
        val pad = 0.11f * u
        val textW = w - 2f * pad
        val font = min(0.20f * u, textW / (max(heroChars, 1) * CHAR_EM))
        val gap = 0.048f * u
        val total = glyph + gap + font * LINE
        val top = (h - total) / 2f
        return WidgetFrames(
            width = w,
            height = h,
            cornerRadius = 0.108f * u,
            border = 0.048f * u,
            patternTile = patternSize(w, h),
            heroGlyph = WidgetRect((w - glyph) / 2f, top, glyph, glyph),
            heroValue = WidgetTextBox(WidgetRect(pad, top + glyph + gap, textW, font * LINE), font, WidgetTextAlign.Center),
            heroLabel = null,
            divider = null,
            tiles = emptyList(),
        )
    }

    private fun small(w: Float, h: Float, tileCount: Int, heroChars: Int): WidgetFrames {
        val u = min(w, h)
        val padX = 0.071f * u
        val glyph = 0.245f * u
        val font = 0.228f * u
        val heroGap = 0.086f * u
        val heroTextW = min(max(1.5f, heroChars * 0.62f) * font, w - 2f * padX - glyph - heroGap)
        val heroH = max(glyph, font * LINE)
        val heroW = glyph + heroGap + heroTextW
        val sectionGap = 0.079f * u
        val dividerH = 0.0246f * u
        val tileGlyph = 0.162f * u
        val tileGap = 0.033f * u
        val tileFont = 0.109f * u
        val tilesH = if (tileCount > 0) tileGlyph + tileGap + tileFont * LINE else 0f
        val total = heroH + (if (tileCount > 0) sectionGap + dividerH + sectionGap + tilesH else 0f)
        var y = (h - total) / 2f
        val heroX = (w - heroW) / 2f
        val heroGlyph = WidgetRect(heroX, y + (heroH - glyph) / 2f, glyph, glyph)
        val heroValue = WidgetTextBox(
            WidgetRect(heroGlyph.right + heroGap, y, heroTextW, heroH),
            font,
            WidgetTextAlign.Start,
        )
        y += heroH
        var divider: WidgetRect? = null
        val tiles = mutableListOf<WidgetTileFrame>()
        if (tileCount > 0) {
            y += sectionGap
            divider = WidgetRect(padX, y, w - 2f * padX, dividerH)
            y += dividerH + sectionGap
            val columnW = max(tileGlyph, 1.6f * tileFont)
            val columnGap = 0.089f * u
            val rowW = tileCount * columnW + (tileCount - 1) * columnGap
            var x = (w - rowW) / 2f
            repeat(tileCount) {
                tiles += WidgetTileFrame(
                    glyph = WidgetRect(x + (columnW - tileGlyph) / 2f, y, tileGlyph, tileGlyph),
                    value = WidgetTextBox(
                        WidgetRect(x, y + tileGlyph + tileGap, columnW, tileFont * LINE),
                        tileFont,
                        WidgetTextAlign.Center,
                    ),
                )
                x += columnW + columnGap
            }
        }
        return WidgetFrames(
            width = w,
            height = h,
            cornerRadius = 0.074f * u,
            border = 0.033f * u,
            patternTile = patternSize(w, h),
            heroGlyph = heroGlyph,
            heroValue = heroValue,
            heroLabel = null,
            divider = divider,
            tiles = tiles,
        )
    }

    private fun wide(w: Float, h: Float, tileCount: Int, heroChars: Int): WidgetFrames {
        val u = min(w, h)
        val pad = 0.11f * u
        val contentH = h - 2f * pad
        val glyph = min(0.462f * u, contentH * 0.62f)
        val font = 0.248f * u
        val gap = 0.124f * u
        val dividerW = 0.036f * u
        val tileGlyph = 0.238f * u
        val tileGap = 0.048f * u
        val tileFont = 0.16f * u
        val tileW = tileGlyph + tileGap + 1.6f * tileFont
        val tilesSpan = if (tileCount > 0) gap + dividerW + gap + tileW else 0f
        val heroW = min(max(glyph, heroChars * 0.62f * font), w - 2f * pad - tilesSpan)
        val total = heroW + tilesSpan
        val left = max(pad, (w - total) / 2f)
        val heroGlyph = WidgetRect(left + (heroW - glyph) / 2f, pad, glyph, glyph)
        val heroValue = WidgetTextBox(
            WidgetRect(left, pad + contentH - font * LINE, heroW, font * LINE),
            font,
            WidgetTextAlign.Center,
        )
        var divider: WidgetRect? = null
        val tiles = mutableListOf<WidgetTileFrame>()
        if (tileCount > 0) {
            divider = WidgetRect(left + heroW + gap, pad, dividerW, contentH)
            val tileX = divider.right + gap
            val step = if (tileCount > 1) (contentH - tileGlyph) / (tileCount - 1) else 0f
            repeat(tileCount) { index ->
                val y = if (tileCount > 1) pad + index * step else pad + (contentH - tileGlyph) / 2f
                tiles += WidgetTileFrame(
                    glyph = WidgetRect(tileX, y, tileGlyph, tileGlyph),
                    value = WidgetTextBox(
                        WidgetRect(tileX + tileGlyph + tileGap, y, 1.6f * tileFont, tileGlyph),
                        tileFont,
                        WidgetTextAlign.Start,
                    ),
                )
            }
        }
        return WidgetFrames(
            width = w,
            height = h,
            cornerRadius = 0.108f * u,
            border = 0.048f * u,
            patternTile = patternSize(w, h),
            heroGlyph = heroGlyph,
            heroValue = heroValue,
            heroLabel = null,
            divider = divider,
            tiles = tiles,
        )
    }

    private fun tall(w: Float, h: Float, tileCount: Int, heroChars: Int): WidgetFrames {
        val u = min(w, h)
        val pad = 0.071f * u
        val innerW = w - 2f * pad
        val glyph = 0.211f * u
        val gap = 0.053f * u
        val font = 0.205f * u
        val heroH = max(glyph, font * LINE)
        val subtitleFont = 0.087f * u
        val subtitleH = subtitleFont * 1.25f
        val dividerH = 0.0246f * u
        val rowGlyph = 0.129f * u
        val rowGap = 0.026f * u
        val labelFont = 0.087f * u
        val valueFont = 0.109f * u
        var y = pad
        val heroGlyph = WidgetRect(pad, y + (heroH - glyph) / 2f, glyph, glyph)
        val heroValue = WidgetTextBox(
            WidgetRect(heroGlyph.right + gap, y, innerW - glyph - gap, heroH),
            font,
            WidgetTextAlign.Start,
        )
        y += heroH + 0.03f * u
        val heroLabel = WidgetTextBox(WidgetRect(pad, y, innerW, subtitleH), subtitleFont, WidgetTextAlign.Start, heavy = false)
        y += subtitleH + 0.045f * u
        var divider: WidgetRect? = null
        val tiles = mutableListOf<WidgetTileFrame>()
        if (tileCount > 0) {
            divider = WidgetRect(pad, y, innerW, dividerH)
            y += dividerH + 0.03f * u
            val remaining = h - pad - y
            val pitch = remaining / max(tileCount, WidgetSize.Tall.tileCount)
            val rowH = max(rowGlyph, valueFont * LINE)
            repeat(tileCount) { index ->
                val rowY = y + index * pitch + (pitch - rowH) / 2f
                val glyphRect = WidgetRect(pad, rowY + (rowH - rowGlyph) / 2f, rowGlyph, rowGlyph)
                val valueW = 2.4f * valueFont
                tiles += WidgetTileFrame(
                    glyph = glyphRect,
                    value = WidgetTextBox(
                        WidgetRect(pad + innerW - valueW, rowY, valueW, rowH),
                        valueFont,
                        WidgetTextAlign.End,
                    ),
                    label = WidgetTextBox(
                        WidgetRect(glyphRect.right + rowGap, rowY, innerW - rowGlyph - rowGap - valueW - rowGap, rowH),
                        labelFont,
                        WidgetTextAlign.Start,
                        heavy = false,
                    ),
                )
            }
        }
        return WidgetFrames(
            width = w,
            height = h,
            cornerRadius = 0.074f * u,
            border = 0.033f * u,
            patternTile = patternSize(w, h),
            heroGlyph = heroGlyph,
            heroValue = heroValue,
            heroLabel = heroLabel,
            divider = divider,
            tiles = tiles,
        )
    }
}
