package com.pocketpass.app.widget

import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

actual fun startOfLocalDayEpochMillis(nowEpochMillis: Long): Long {
    val now = NSDate.dateWithTimeIntervalSince1970(nowEpochMillis / 1000.0)
    val start = NSCalendar.currentCalendar.startOfDayForDate(now)
    return (start.timeIntervalSince1970 * 1000.0).toLong()
}
