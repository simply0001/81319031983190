package com.pocketpass.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import kotlin.time.Instant

// True when the platform wants looping decorative animations (the user has not disabled
// animations system-wide).
expect fun platformAnimationsEnabled(): Boolean

// The height of the status bar even while it is hidden. Android is the only platform that
// reports hidden bars separately; elsewhere the safe-drawing insets already cover it.
@Composable
expect fun stableStatusBarTop(density: Density): Int

// True when the runtime shader behind the animated background patterns is available.
expect fun supportsAnimatedPatterns(): Boolean

// True on Android 11 and below, where scanning for nearby devices needs the location
// permission and the permission dialogs must explain that.
expect fun requiresLegacyLocationPermission(): Boolean

// Formats an instant in the device time zone with a Unicode date pattern (e.g. "d MMM yyyy"),
// always with English month names to match the rest of the interface.
expect fun formatInstant(instant: Instant, pattern: String): String

// Two-letter ISO 3166 codes known to the platform.
expect fun isoCountryCodes(): List<String>

// English display name for a two-letter country code, or the code itself when unknown.
expect fun displayCountryName(code: String): String

// True when a regular file exists at the given absolute path.
expect fun fileExists(path: String): Boolean

// The application's version name, provided by each platform's entry point.
val LocalAppVersionName = staticCompositionLocalOf { "" }
