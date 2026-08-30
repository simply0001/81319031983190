package com.pocketpass.app.ui.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSurfaceTest {
    @Test
    fun theShortSideSpansTheDeckWidthOnAPhone() {
        val scale = phoneScale(1080f, 2340f, density = 2.8125f)
        assertEquals(1240f, 1080f / scale, 0.01f)
        assertEquals(1240f, 1080f / phoneScale(2340f, 1080f, density = 2.8125f), 0.01f)
    }

    @Test
    fun tinyAndHugeScreensClampTheUnitInsteadOfTheLayout() {
        val small = phoneScale(600f, 1000f, density = 2f)
        assertTrue(600f / small < 1240f)
        assertEquals(0.26f * 2f, small, 0.0001f)
        val tablet = phoneScale(1600f, 2560f, density = 2f)
        assertTrue(1600f / tablet > 1240f)
        assertEquals(0.36f * 2f, tablet, 0.0001f)
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
