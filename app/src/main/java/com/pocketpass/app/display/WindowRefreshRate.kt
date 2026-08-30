package com.pocketpass.app.display

import android.view.Display
import android.view.Window
import kotlin.math.abs

fun Window.preferSixtyHertz(display: Display?) {
    val mode = display?.mode ?: return
    val target = display.supportedModes
        .filter {
            it.physicalWidth == mode.physicalWidth &&
                it.physicalHeight == mode.physicalHeight &&
                abs(it.refreshRate - 60f) <= 1f
        }
        .minByOrNull { abs(it.refreshRate - 60f) }
        ?: return
    attributes = attributes.apply { preferredDisplayModeId = target.modeId }
}
