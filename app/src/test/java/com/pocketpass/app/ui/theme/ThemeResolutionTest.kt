package com.pocketpass.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.pocketpass.app.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeResolutionTest {
    @Test
    fun systemFollowsTheOsAndExplicitModesIgnoreIt() {
        assertTrue(resolveDarkTheme(ThemeMode.System, systemDark = true))
        assertFalse(resolveDarkTheme(ThemeMode.System, systemDark = false))
        assertTrue(resolveDarkTheme(ThemeMode.Dark, systemDark = false))
        assertFalse(resolveDarkTheme(ThemeMode.Light, systemDark = true))
        assertTrue(paletteFor(true).isDark)
        assertFalse(paletteFor(false).isDark)
    }

    @Test
    fun lightPaletteLeavesColoursUntouched() {
        val pastel = Color(0xFFBDF8CB)
        val ink = Color(0xFF820A79)
        assertEquals(pastel, LightPalette.tint(pastel))
        assertEquals(ink, LightPalette.ink(ink))
        assertEquals(ink, LightPalette.line(ink))
    }

    @Test
    fun darkPaletteDarkensPastelsAndLightensInk() {
        val pastel = Color(0xFFBDF8CB)
        val ink = Color(0xFF820A79)
        assertTrue(DarkPalette.tint(pastel).luminance() < 0.08f)
        assertTrue(DarkPalette.ink(ink).luminance() > 0.35f)
        assertEquals(Color.White, DarkPalette.ink(Color.White))
        assertTrue(DarkPalette.line(Color(0xFF0E4A17)).luminance() > Color(0xFF0E4A17).luminance())
        assertEquals(Color(0xFFCB4AC0), DarkPalette.line(Color(0xFFCB4AC0)))
    }
}
