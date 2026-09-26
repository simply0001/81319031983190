package com.pocketpass.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetLayoutTest {
    private fun WidgetRect.inside(w: Float, h: Float) = x >= -0.5f && y >= -0.5f && right <= w + 0.5f && bottom <= h + 0.5f

    @Test
    fun everySizeKeepsItsFramesInsideTheCard() {
        val cases = listOf(
            Triple(WidgetSize.Mini, 200f, 200f),
            Triple(WidgetSize.Small, 450f, 450f),
            Triple(WidgetSize.Wide, 930f, 450f),
            Triple(WidgetSize.Tall, 450f, 960f),
        )
        cases.forEach { (size, w, h) ->
            val frames = WidgetLayout.frames(size, w, h, size.tileCount, heroChars = 3)
            assertTrue(frames.heroGlyph.inside(w, h), "$size hero glyph")
            assertTrue(frames.heroValue.rect.inside(w, h), "$size hero value")
            frames.divider?.let { assertTrue(it.inside(w, h), "$size divider") }
            assertEquals(size.tileCount, frames.tiles.size, "$size tiles")
            frames.tiles.forEach { tile ->
                assertTrue(tile.glyph.inside(w, h), "$size tile glyph")
                assertTrue(tile.value.rect.inside(w, h), "$size tile value")
            }
            assertTrue(frames.cornerRadius > 0f && frames.border > 0f)
        }
    }

    @Test
    fun onlyTheTallLayoutLabelsItsRows() {
        val tall = WidgetLayout.frames(WidgetSize.Tall, 450f, 960f, 4)
        val small = WidgetLayout.frames(WidgetSize.Small, 450f, 450f, 3)
        val mini = WidgetLayout.frames(WidgetSize.Mini, 200f, 200f, 0)

        assertNotNull(tall.heroLabel)
        assertTrue(tall.tiles.all { it.label != null })
        assertNull(small.heroLabel)
        assertTrue(small.tiles.all { it.label == null })
        assertNull(mini.divider)
        assertTrue(mini.tiles.isEmpty())
    }

    @Test
    fun heroValueSitsUnderTheGlyphOnMiniAndBesideItOnSmall() {
        val mini = WidgetLayout.frames(WidgetSize.Mini, 200f, 200f, 0)
        val small = WidgetLayout.frames(WidgetSize.Small, 450f, 450f, 3)

        assertTrue(mini.heroValue.rect.y >= mini.heroGlyph.bottom)
        assertTrue(small.heroValue.rect.x >= small.heroGlyph.right)
    }

    @Test
    fun tallRowsKeepTheirPitchWhenSlotsAreEmpty() {
        val twoRows = WidgetLayout.frames(WidgetSize.Tall, 450f, 960f, 2)
        val fourRows = WidgetLayout.frames(WidgetSize.Tall, 450f, 960f, 4)

        assertEquals(fourRows.tiles[1].glyph.y, twoRows.tiles[1].glyph.y, 0.01f)
        assertTrue(twoRows.tiles.last().glyph.bottom < fourRows.tiles[2].glyph.y)
    }

    @Test
    fun miniShrinksLongNamesInsideTheBorder() {
        val number = WidgetLayout.frames(WidgetSize.Mini, 200f, 200f, 0, heroChars = 2)
        val name = WidgetLayout.frames(WidgetSize.Mini, 200f, 200f, 0, heroChars = 12)

        assertEquals(40f, number.heroValue.fontSize, 0.01f)
        assertTrue(name.heroValue.fontSize < 25f)
        assertTrue(name.heroValue.rect.x >= name.border)
        assertTrue(name.heroValue.rect.right <= 200f - name.border)
        assertTrue(name.heroValue.fontSize * 0.62f * 12 <= name.heroValue.rect.width + 0.01f)
    }

    @Test
    fun compactNumbersReadLikeTheDesign() {
        assertEquals("0", WidgetBlockValues.compact(0))
        assertEquals("999", WidgetBlockValues.compact(999))
        assertEquals("2k", WidgetBlockValues.compact(2_000))
        assertEquals("2.4k", WidgetBlockValues.compact(2_449))
        assertEquals("12k", WidgetBlockValues.compact(12_500))
        assertEquals("1.2M", WidgetBlockValues.compact(1_200_000))
    }
}
