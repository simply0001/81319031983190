package com.pocketpass.app.ui.theme

import com.pocketpass.app.model.ThemeMode

fun resolveDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System -> systemDark
}

fun paletteFor(dark: Boolean): PocketPalette = if (dark) DarkPalette else LightPalette
