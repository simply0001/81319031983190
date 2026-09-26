package com.pocketpass.app.ui.phone

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class PhoneSurfaceTest {
    @Test
    fun theShortSideSpansTheDeckWidthOnAPhone() {
        val scale = phoneScale(1080f, 2340f, density = 2.8125f)
        assertEquals(1240f, 1080f / scale, 0.01f)
        assertEquals(1240f, 1080f / phoneScale(2340f, 1080f, density = 2.8125f), 0.01f)
    }

    @Test
    fun tinyScreensRetainTheirMinimumAndTabletsGetALargerBoundedScale() {
        val small = phoneScale(600f, 1000f, density = 2f)
        assertTrue(600f / small < 1240f)
        assertEquals(0.26f * 2f, small, 0.0001f)
        val tablet = phoneScale(1600f, 2560f, density = 2f)
        assertTrue(1600f / tablet > 1240f)
        assertEquals(0.48f * 2f, tablet, 0.0001f)
        assertEquals(tablet, phoneScale(2400f, 3200f, density = 2f), 0.0001f)
    }

    @Test
    fun scaleGrowsContinuouslyBeyondPhoneSizes() {
        var previous = 0f
        for (shortSide in 320..1000) {
            val scale = phoneScale(shortSide.toFloat(), shortSide * 1.6f, 1f)
            assertTrue(scale >= previous)
            if (previous > 0f) assertTrue(scale - previous < 0.001f)
            previous = scale
        }
    }

    @Test
    fun landscapeTabletsFitBothPanesIncludingTheDividerAndSystemInsets() {
        for ((width, height) in listOf(960f to 600f, 1024f to 768f, 1280f to 800f, 1600f to 1000f)) {
            for (density in listOf(1f, 1.5f, 2f, 3f)) {
                val scale = phoneScale(width * density, height * density, density, 48f * density)
                val designWidth = width * density / scale
                val startInset = 24f * density / scale
                val endInset = startInset
                assertEquals(PhoneLayout.Wide, phoneLayout(designWidth - startInset - endInset, height * density / scale))
                val panes = widePanes(designWidth, startInset, endInset)
                assertTrue(panes.stage >= 880f)
                assertTrue(panes.margin >= 0f)
                assertEquals(designWidth, panes.rail + panes.stage + panes.gap + panes.deck + 2f * panes.margin + endInset, 0.01f)
                assertTrue(panes.deck * scale / density >= 446f)
            }
        }
    }

    @Test
    fun narrowTabletWindowsRemainCompact() {
        for ((width, height) in listOf(600f to 960f, 800f to 1280f, 800f to 600f, 880f to 650f, 480f to 800f)) {
            val scale = phoneScale(width, height, 1f)
            assertEquals(PhoneLayout.Compact, phoneLayout(width / scale, height / scale))
            assertTrue(PHONE_DECK_WIDTH * scale <= width + 0.01f)
            assertTrue(scale >= 0.36f)
        }
    }

    @Test
    fun portraitIsCompactAndPhoneLandscapeIsWide() {
        assertEquals(PhoneLayout.Compact, phoneLayout(1240f, 2687f))
        assertEquals(PhoneLayout.Wide, phoneLayout(2687f, 1240f))
        assertEquals(PhoneLayout.Compact, phoneLayout(2222f, 3555f))
        assertEquals(PhoneLayout.Wide, phoneLayout(3555f, 2222f))
    }

    @Test
    fun aSquatLandscapeThatCannotFitAStageStaysCompact() {
        assertEquals(PhoneLayout.Compact, phoneLayout(1600f, 1240f))
    }

    @Test
    fun widePanesKeepTheDeckAtItsNativeWidth() {
        val panes = widePanes(2687f, startInset = 120f, endInset = 0f)
        assertEquals(PHONE_DECK_WIDTH, panes.deck, 0.01f)
        assertEquals(PHONE_RAIL_WIDTH + 120f, panes.rail, 0.01f)
        assertEquals(2687f - panes.rail - PHONE_PANE_GAP - PHONE_DECK_WIDTH, panes.stage, 0.01f)
        assertEquals(0f, panes.margin, 0.01f)
    }

    @Test
    fun extraTabletWidthBecomesSymmetricMargin() {
        val panes = widePanes(4000f, startInset = 0f, endInset = 0f)
        assertEquals(PHONE_STAGE_MAX_WIDTH, panes.stage, 0.01f)
        assertEquals((4000f - PHONE_RAIL_WIDTH - PHONE_PANE_GAP - PHONE_DECK_WIDTH - PHONE_STAGE_MAX_WIDTH) / 2f, panes.margin, 0.01f)
    }
}
