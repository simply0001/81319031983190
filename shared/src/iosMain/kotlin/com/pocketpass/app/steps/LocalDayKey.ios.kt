package com.pocketpass.app.steps

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone

actual fun localDayKey(nowEpochMillis: Long): String {
    val formatter = NSDateFormatter().apply {
        dateFormat = "yyyy-MM-dd"
        timeZone = NSTimeZone.localTimeZone
    }
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(nowEpochMillis / 1000.0))
}

actual fun localUtcOffsetMinutes(nowEpochMillis: Long): Int {
    val date = NSDate.dateWithTimeIntervalSince1970(nowEpochMillis / 1000.0)
    return (NSTimeZone.localTimeZone.secondsFromGMTForDate(date) / 60).toInt()
}
