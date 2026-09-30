package com.pocketpass.app.model

import kotlin.test.Test
import kotlin.test.assertEquals

class StatusClockTest {
    @Test
    fun twentyFourHourClockKeepsLeadingZerosAndHasNoAmPm() {
        assertEquals("00:05", StatusClock.time(0, 5, twentyFourHour = true))
        assertEquals("09:30", StatusClock.time(9, 30, twentyFourHour = true))
        assertEquals("23:59", StatusClock.time(23, 59, twentyFourHour = true))
        assertEquals("", StatusClock.amPm(14, twentyFourHour = true))
    }

    @Test
    fun twelveHourClockShowsTwelveAtMidnightAndNoon() {
        assertEquals("12:05", StatusClock.time(0, 5, twentyFourHour = false))
        assertEquals("AM", StatusClock.amPm(0, twentyFourHour = false))
        assertEquals("9:30", StatusClock.time(9, 30, twentyFourHour = false))
        assertEquals("AM", StatusClock.amPm(11, twentyFourHour = false))
        assertEquals("12:00", StatusClock.time(12, 0, twentyFourHour = false))
        assertEquals("PM", StatusClock.amPm(12, twentyFourHour = false))
        assertEquals("11:59", StatusClock.time(23, 59, twentyFourHour = false))
        assertEquals("PM", StatusClock.amPm(23, twentyFourHour = false))
    }
}
