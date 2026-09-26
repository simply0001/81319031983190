package com.pocketpass.app.ui.phone

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhoneGameLayoutTest {
    @Test
    fun foregroundsFitPhoneTabletAndShortLandscapeViewports() {
        for ((width, height) in listOf(1100f to 2100f, 1600f to 2400f, 2300f to 1400f, 2100f to 800f, 720f to 600f, 0f to 0f)) {
            val layout = phoneGameLayout(width, height)
            val contentWidth = if (layout.sideBySide) layout.heroWidth + layout.boardWidth else maxOf(layout.heroWidth, layout.boardWidth)
            val contentHeight = if (layout.sideBySide) maxOf(layout.heroHeight, layout.boardHeight) else layout.heroHeight + layout.boardHeight
            assertTrue(contentWidth <= width + 0.01f)
            assertTrue(contentHeight <= height + 0.01f)
            assertTrue(layout.boardWidth >= 0f && layout.boardHeight >= 0f)
        }
    }

    @Test
    fun controlsGetMoreRoomThanTheDecorativeTitle() {
        val portrait = phoneGameLayout(1600f, 2400f)
        assertTrue(portrait.boardWidth > portrait.heroWidth)
        assertTrue(portrait.boardWidth <= 1440f)
        val landscape = phoneGameLayout(2300f, 1400f)
        assertTrue(landscape.boardHeight > landscape.heroHeight)
        assertEquals(1240f / 1080f, landscape.boardWidth / landscape.boardHeight, 0.0001f)
    }
}
