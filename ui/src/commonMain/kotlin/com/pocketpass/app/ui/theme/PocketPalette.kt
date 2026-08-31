package com.pocketpass.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.pocketpass.app.model.PocketPassDestination
import kotlin.math.abs

@Immutable
data class BackgroundPair(val top: Color, val bottom: Color)

@Immutable
data class PocketPalette(
    val isDark: Boolean,
    val surface: Color,
    val surfaceLow: Color,
    val surfaceLower: Color,
    val surfaceSunken: Color,
    val chrome: Color,
    val scrim: Color,
    val shadowAlpha: Float,
    val borderGrey: Color,
    val borderSoft: Color,
    val tealBorder: Color,
    val teal: Color,
    val tealSoft: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textOnAccent: Color,
    val topBackgrounds: Map<PocketPassDestination, BackgroundPair>,
    val bottomBackgrounds: Map<PocketPassDestination, BackgroundPair>,
) {
    fun tint(light: Color): Color = if (isDark) duskOf(light) else light

    fun ink(light: Color): Color = if (isDark) liftOf(light) else light

    fun line(light: Color): Color = if (isDark) strokeOf(light) else light

    fun background(destination: PocketPassDestination, top: Boolean): BackgroundPair =
        (if (top) topBackgrounds else bottomBackgrounds).getValue(destination)
}

val LightPalette = PocketPalette(
    isDark = false,
    surface = Color.White,
    surfaceLow = Color(0xFFECECEC),
    surfaceLower = Color(0xFFD9D9D9),
    surfaceSunken = Color.White,
    chrome = Color.White,
    scrim = Color.Black.copy(alpha = 0.18f),
    shadowAlpha = 0.32f,
    borderGrey = Color(0xFF9F9F9F),
    borderSoft = Color(0xFFD9D9D9),
    tealBorder = Color(0xFF5E9AAC),
    teal = Color(0xFF1D596B),
    tealSoft = Color(0xFF26706A),
    textPrimary = Color(0xFF5C5C5C),
    textSecondary = Color(0x8F575757),
    textMuted = Color(0xFF8A8A8A),
    textOnAccent = Color.White,
    topBackgrounds = mapOf(
        PocketPassDestination.Home to BackgroundPair(Color(0xFF92EBAE), Color.White),
        PocketPassDestination.Activities to BackgroundPair(Color(0xFFFCA5A5), Color.White),
        PocketPassDestination.Messages to BackgroundPair(Color(0xFF7FC5EC), Color.White),
        PocketPassDestination.Friends to BackgroundPair(Color(0xFFFCC8FC), Color.White),
        PocketPassDestination.Settings to BackgroundPair(Color(0xFFCDCDCD), Color.White),
    ),
    bottomBackgrounds = mapOf(
        PocketPassDestination.Home to BackgroundPair(Color(0xFFE9F6F4), Color(0xFF92EBAE)),
        PocketPassDestination.Activities to BackgroundPair(Color(0xFFF6EEE9), Color(0xFFFCBFBC)),
        PocketPassDestination.Messages to BackgroundPair(Color(0xFFE9F1F6), Color(0xFF92CDEB)),
        PocketPassDestination.Friends to BackgroundPair(Color(0xFFF4E9F6), Color(0xFFFFBCF8)),
        PocketPassDestination.Settings to BackgroundPair(Color(0xFFEDEDED), Color(0xFFCBCBCB)),
    ),
)

val DarkPalette = PocketPalette(
    isDark = true,
    surface = Color(0xFF1F2A31),
    surfaceLow = Color(0xFF17222A),
    surfaceLower = Color(0xFF131C22),
    surfaceSunken = Color(0xFF131C22),
    chrome = Color(0xFF1B252C),
    scrim = Color.Black.copy(alpha = 0.45f),
    shadowAlpha = 0.32f,
    borderGrey = Color(0xFF536470),
    borderSoft = Color(0xFF34434C),
    tealBorder = Color(0xFF6FA9BC),
    teal = Color(0xFFA6D8E8),
    tealSoft = Color(0xFF8FC6C0),
    textPrimary = Color(0xFFE8EEF1),
    textSecondary = Color(0xFFA9BAC2),
    textMuted = Color(0xFF8A9BA3),
    textOnAccent = Color.White,
    topBackgrounds = mapOf(
        PocketPassDestination.Home to BackgroundPair(Color(0xFF1B4A34), Color(0xFF16242A)),
        PocketPassDestination.Activities to BackgroundPair(Color(0xFF4A2A28), Color(0xFF2A2220)),
        PocketPassDestination.Messages to BackgroundPair(Color(0xFF1C3E52), Color(0xFF17232B)),
        PocketPassDestination.Friends to BackgroundPair(Color(0xFF4A1F45), Color(0xFF241A2A)),
        PocketPassDestination.Settings to BackgroundPair(Color(0xFF2A3035), Color(0xFF202427)),
    ),
    bottomBackgrounds = mapOf(
        PocketPassDestination.Home to BackgroundPair(Color(0xFF16242A), Color(0xFF1B4A34)),
        PocketPassDestination.Activities to BackgroundPair(Color(0xFF2A2220), Color(0xFF4A2A28)),
        PocketPassDestination.Messages to BackgroundPair(Color(0xFF17232B), Color(0xFF1C3E52)),
        PocketPassDestination.Friends to BackgroundPair(Color(0xFF241A2A), Color(0xFF4A1F45)),
        PocketPassDestination.Settings to BackgroundPair(Color(0xFF202427), Color(0xFF2A3035)),
    ),
)

val LocalPocketPalette = staticCompositionLocalOf { LightPalette }

val pocketPalette: PocketPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalPocketPalette.current

private data class Hsl(val h: Float, val s: Float, val l: Float)

private fun Color.toHsl(): Hsl {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val l = (max + min) / 2f
    val d = max - min
    if (d < 0.0001f) return Hsl(0f, 0f, l)
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        red -> ((green - blue) / d + (if (green < blue) 6f else 0f)) / 6f
        green -> ((blue - red) / d + 2f) / 6f
        else -> ((red - green) / d + 4f) / 6f
    }
    return Hsl(h, s, l)
}

private fun hslColor(h: Float, s: Float, l: Float, alpha: Float): Color {
    if (s < 0.0001f) return Color(l, l, l, alpha)
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    fun channel(t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    return Color(channel(h + 1f / 3f), channel(h), channel(h - 1f / 3f), alpha)
}

internal fun duskOf(light: Color): Color {
    val hsl = light.toHsl()
    val lightness = 0.14f + (1f - hsl.l).coerceIn(0f, 0.6f) * 0.25f
    return hslColor(hsl.h, (hsl.s * 0.55f).coerceAtMost(0.5f), lightness, light.alpha)
}

internal fun liftOf(light: Color): Color {
    if (light.luminance() > 0.45f) return light
    val hsl = light.toHsl()
    val lightness = 0.72f + abs(0.5f - hsl.l) * 0.16f
    return hslColor(hsl.h, (hsl.s * 0.85f).coerceAtMost(0.7f), lightness.coerceAtMost(0.84f), light.alpha)
}

internal fun strokeOf(light: Color): Color {
    if (light.luminance() > 0.12f) return light
    val hsl = light.toHsl()
    return hslColor(hsl.h, hsl.s, 0.5f, light.alpha)
}
