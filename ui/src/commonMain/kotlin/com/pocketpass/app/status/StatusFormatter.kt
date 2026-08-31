package com.pocketpass.app.status

import kotlin.math.roundToInt

object StatusFormatter {
    fun battery(percent: Int): String = "${percent.coerceIn(0, 100)}%"

    fun batteryPercent(
        level: Int,
        scale: Int,
        fallback: Int,
    ): Int = if (level >= 0 && scale > 0) {
        ((level.toFloat() / scale) * 100f).roundToInt().coerceIn(0, 100)
    } else {
        fallback.coerceIn(0, 100)
    }

    fun batteryFillFraction(percent: Int): Float =
        percent.coerceIn(0, 100) / 100f

    fun wifiSignalLevel(
        connected: Boolean,
        signalStrength: Int,
    ): Int = when {
        !connected -> -1
        signalStrength == Int.MIN_VALUE -> 2
        signalStrength >= -65 -> 2
        signalStrength >= -75 -> 1
        else -> 0
    }
}
