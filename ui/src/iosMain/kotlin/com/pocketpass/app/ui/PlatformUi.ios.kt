package com.pocketpass.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.Density
import kotlin.time.Instant
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.ISOCountryCodes
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

actual fun platformAnimationsEnabled(): Boolean = !UIAccessibilityIsReduceMotionEnabled()

@Composable
actual fun stableStatusBarTop(density: Density): Int = 0

actual fun supportsAnimatedPatterns(): Boolean = true

actual fun requiresLegacyLocationPermission(): Boolean = false

actual fun formatInstant(instant: Instant, pattern: String): String {
    val formatter = NSDateFormatter().apply {
        dateFormat = pattern
        locale = NSLocale("en_US_POSIX")
    }
    val seconds = instant.epochSeconds.toDouble() + instant.nanosecondsOfSecond / 1_000_000_000.0
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(seconds))
}

actual fun isoCountryCodes(): List<String> =
    NSLocale.ISOCountryCodes().filterIsInstance<String>()

actual fun fileExists(path: String): Boolean =
    NSFileManager.defaultManager.fileExistsAtPath(path)

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

actual fun pocketPlatformTextStyle(): PlatformTextStyle? = null

actual fun displayCountryName(code: String): String =
    NSLocale("en_US")
        .displayNameForKey(NSLocaleCountryCode, code)
        ?.takeIf { it.isNotBlank() }
        ?: code
