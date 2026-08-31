package com.pocketpass.app.status

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusFormatterTest {
    @Test
    fun batteryIsClampedAndSuffixed() {
        assertEquals("0%", StatusFormatter.battery(-1))
        assertEquals("99%", StatusFormatter.battery(99))
        assertEquals("100%", StatusFormatter.battery(101))
    }

    @Test
    fun batteryLevelUsesScaleAndFallsBackWhenUnavailable() {
        assertEquals(50, StatusFormatter.batteryPercent(25, 50, 81))
        assertEquals(81, StatusFormatter.batteryPercent(-1, 100, 81))
        assertEquals(81, StatusFormatter.batteryPercent(25, 0, 81))
    }

    @Test
    fun batteryFillFractionIsClamped() {
        assertEquals(0f, StatusFormatter.batteryFillFraction(-1))
        assertEquals(0.45f, StatusFormatter.batteryFillFraction(45))
        assertEquals(1f, StatusFormatter.batteryFillFraction(101))
    }

    @Test
    fun wifiSignalMapsToFigmaArcLevels() {
        assertEquals(-1, StatusFormatter.wifiSignalLevel(false, -40))
        assertEquals(0, StatusFormatter.wifiSignalLevel(true, -80))
        assertEquals(1, StatusFormatter.wifiSignalLevel(true, -70))
        assertEquals(2, StatusFormatter.wifiSignalLevel(true, -45))
        assertEquals(2, StatusFormatter.wifiSignalLevel(true, Int.MIN_VALUE))
    }
}
