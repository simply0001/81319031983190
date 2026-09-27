package com.pocketpass.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.Density
import kotlin.time.Instant

expect fun platformAnimationsEnabled(): Boolean

@Composable
expect fun stableStatusBarTop(density: Density): Int

expect fun supportsAnimatedPatterns(): Boolean

expect fun requiresLegacyLocationPermission(): Boolean

expect fun asksToRunInBackground(): Boolean

expect fun formatInstant(instant: Instant, pattern: String): String

expect fun isoCountryCodes(): List<String>

expect fun displayCountryName(code: String): String

expect fun fileExists(path: String): Boolean

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

expect fun pocketPlatformTextStyle(): PlatformTextStyle?

val LocalAppVersionName = staticCompositionLocalOf { "" }
