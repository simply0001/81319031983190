package com.pocketpass.app.input

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import com.pocketpass.app.boards.BoardsScreen
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.controller.ControllerFocus

/** The left stick pans zoomed Boards paper independently of the selected drawing tool. */
class BoardCanvasJoystickHandler(
    private val state: () -> PocketPassUiState,
    private val focus: ControllerFocus,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var x = 0f
    private var y = 0f
    private var running = false
    private var lastFrame = 0L

    private val frame = object : Runnable {
        override fun run() {
            val pan = focus.boardCanvasPan
            if (!active() || pan == null || (x == 0f && y == 0f)) { release(); return }
            val now = SystemClock.uptimeMillis()
            val seconds = ((now - lastFrame).coerceIn(0, 50)) / 1000f
            lastFrame = now
            pan(x * seconds, y * seconds)
            handler.postDelayed(this, 16L)
        }
    }

    fun handle(event: MotionEvent): Boolean {
        if (!active()) { release(); return false }
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.action != MotionEvent.ACTION_MOVE) return false
        val travel = processJoystickDeflection(
            event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y),
        )
        x = travel?.x ?: 0f
        y = travel?.y ?: 0f
        if (travel == null) release()
        else if (!running) {
            running = true
            lastFrame = SystemClock.uptimeMillis()
            handler.post(frame)
        }
        return true
    }

    fun release() {
        handler.removeCallbacks(frame)
        running = false
        x = 0f
        y = 0f
    }

    private fun active(): Boolean = state().let {
        it.rootDestination == PocketPassDestination.Messages && it.boards.screen == BoardsScreen.Compose &&
            focus.boardCanvasPan != null && focus.keyboardSubmit == null && focus.transientBack == null
    }
}
