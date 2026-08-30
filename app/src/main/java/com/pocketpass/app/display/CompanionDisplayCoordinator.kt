package com.pocketpass.app.display

import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pocketpass.app.state.PocketPassViewModel

class CompanionDisplayCoordinator(
    private val activity: ComponentActivity,
    private val viewModel: PocketPassViewModel,
) : DisplayManager.DisplayListener {
    private val displayManager = activity.getSystemService(DisplayManager::class.java)
    private var presentation: CompanionDisplayPresentation? = null
    private var registered = false

    var companionAttached: Boolean by mutableStateOf(companionDisplay() != null)
        private set

    fun start() {
        if (!registered) {
            displayManager.registerDisplayListener(this, null)
            registered = true
        }
        showBottomDisplayIfAvailable()
    }

    fun stop() {
        if (registered) {
            displayManager.unregisterDisplayListener(this)
            registered = false
        }
        presentation?.dismiss()
        presentation = null
    }

    override fun onDisplayAdded(displayId: Int) {
        showBottomDisplayIfAvailable()
    }

    override fun onDisplayRemoved(displayId: Int) {
        if (presentation?.display?.displayId == displayId) {
            presentation?.dismiss()
            presentation = null
        }
        showBottomDisplayIfAvailable()
    }

    override fun onDisplayChanged(displayId: Int) {
        val currentDisplayId = presentation?.display?.displayId
        if (currentDisplayId == displayId) {
            presentation?.dismiss()
            presentation = null
        }
        showBottomDisplayIfAvailable()
    }

    private fun showBottomDisplayIfAvailable() {
        if (presentation?.isShowing == true) {
            companionAttached = true
            return
        }

        val target = companionDisplay()
        if (target == null) {
            companionAttached = false
            return
        }

        val candidate = CompanionDisplayPresentation(activity, target, viewModel)
        try {
            candidate.show()
            presentation = candidate
            companionAttached = true
        } catch (_: WindowManager.InvalidDisplayException) {
            candidate.dismiss()
            companionAttached = false
        }
    }

    private fun companionDisplay(): Display? = displayManager
        .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        .filterNot { it.displayId == runCatching { activity.display?.displayId }.getOrNull() }
        .sortedWith(
            compareByDescending<Display> { display ->
                val mode = display.mode
                if (
                    minOf(mode.physicalWidth, mode.physicalHeight) == THOR_BOTTOM_SHORT_EDGE &&
                    maxOf(mode.physicalWidth, mode.physicalHeight) == THOR_BOTTOM_LONG_EDGE
                ) {
                    1
                } else {
                    0
                }
            }.thenBy { it.displayId },
        )
        .firstOrNull()

    private companion object {
        const val THOR_BOTTOM_SHORT_EDGE = 1080
        const val THOR_BOTTOM_LONG_EDGE = 1240
    }
}
