package com.pocketpass.app.model

object StatusClock {
    fun time(hour: Int, minute: Int, twentyFourHour: Boolean): String {
        val shownHour = when {
            twentyFourHour -> hour.toString().padStart(2, '0')
            hour % 12 == 0 -> "12"
            else -> (hour % 12).toString()
        }
        return "$shownHour:${minute.toString().padStart(2, '0')}"
    }

    fun amPm(hour: Int, twentyFourHour: Boolean): String = when {
        twentyFourHour -> ""
        hour < 12 -> "AM"
        else -> "PM"
    }
}
