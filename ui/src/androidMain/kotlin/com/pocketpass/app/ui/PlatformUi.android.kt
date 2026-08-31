package com.pocketpass.app.ui

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

actual fun platformAnimationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun stableStatusBarTop(density: Density): Int =
    WindowInsets.statusBarsIgnoringVisibility.getTop(density)

actual fun supportsAnimatedPatterns(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

actual fun requiresLegacyLocationPermission(): Boolean = Build.VERSION.SDK_INT <= 30

actual fun formatInstant(instant: Instant, pattern: String): String =
    DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
        .withZone(ZoneId.systemDefault())
        .format(java.time.Instant.ofEpochSecond(instant.epochSeconds, instant.nanosecondsOfSecond.toLong()))

actual fun isoCountryCodes(): List<String> = Locale.getISOCountries().toList()

actual fun fileExists(path: String): Boolean = java.io.File(path).isFile

actual fun displayCountryName(code: String): String =
    Locale.Builder()
        .setRegion(code)
        .build()
        .getDisplayCountry(Locale.ENGLISH)
        .ifBlank { code }
