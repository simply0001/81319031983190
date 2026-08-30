package com.pocketpass.app.input

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.pocketpass.app.model.PocketPassUiState
import kotlin.math.abs

internal const val RIGHT_STICK_ENGAGE = 0.5f
internal const val RIGHT_STICK_RELEASE = 0.3f

internal enum class RightStickDirection(val keyCode: Int) {
    Left(KeyEvent.KEYCODE_DPAD_LEFT),
    Right(KeyEvent.KEYCODE_DPAD_RIGHT),
    Up(KeyEvent.KEYCODE_DPAD_UP),
    Down(KeyEvent.KEYCODE_DPAD_DOWN),
}

internal fun resolveRightStickDirection(
    x: Float,
    y: Float,
    current: RightStickDirection?,
    engage: Float = RIGHT_STICK_ENGAGE,
    release: Float = RIGHT_STICK_RELEASE,
): RightStickDirection? {
    val held = when (current) {
        RightStickDirection.Left -> -x
        RightStickDirection.Right -> x
        RightStickDirection.Up -> -y
        RightStickDirection.Down -> y
        null -> 0f
    }
    if (current != null && held > release) return current
    return when {
        abs(x) < engage && abs(y) < engage -> null
        abs(x) >= abs(y) -> if (x < 0f) RightStickDirection.Left else RightStickDirection.Right
        else -> if (y < 0f) RightStickDirection.Up else RightStickDirection.Down
    }
}

class MiiEditorRightStickHandler(
    private val state: () -> PocketPassUiState,
    private val sendKey: (KeyEvent) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var direction: RightStickDirection? = null
    private var deviceId = 0
    private var source = InputDevice.SOURCE_JOYSTICK
    private var downTime = 0L
    private var repeatCount = 0
    private var axesDeviceId = Int.MIN_VALUE
    private var usesRxRy = false

    private val repeat = object : Runnable {
        override fun run() {
            val held = direction ?: return
            if (!editorActive()) {
                release()
                return
            }
            repeatCount++
            sendKey(keyEvent(KeyEvent.ACTION_DOWN, held, repeatCount))
            handler.postDelayed(this, ViewConfiguration.getKeyRepeatDelay().toLong())
        }
    }

    fun handle(event: MotionEvent): Boolean {
        if (!editorActive()) {
            release()
            return false
        }
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.action != MotionEvent.ACTION_MOVE) {
            return false
        }
        val next = resolveRightStickDirection(rightStickX(event), rightStickY(event), direction)
        if (next == direction) return next != null
        release()
        if (next != null) press(next, event)
        return true
    }

    fun release() {
        val held = direction ?: return
        handler.removeCallbacks(repeat)
        direction = null
        sendKey(keyEvent(KeyEvent.ACTION_UP, held, 0))
    }

    private fun editorActive(): Boolean = state().let { it.miiEditorEnabled && it.miiEditor.isEditorVisible }

    private fun press(next: RightStickDirection, event: MotionEvent) {
        direction = next
        deviceId = event.deviceId
        source = event.source
        downTime = SystemClock.uptimeMillis()
        repeatCount = 0
        sendKey(keyEvent(KeyEvent.ACTION_DOWN, next, 0))
        handler.postDelayed(repeat, ViewConfiguration.getKeyRepeatTimeout().toLong())
    }

    private fun keyEvent(action: Int, held: RightStickDirection, repeat: Int): KeyEvent = KeyEvent(
        downTime,
        SystemClock.uptimeMillis(),
        action,
        held.keyCode,
        repeat,
        0,
        deviceId,
        0,
        KeyEvent.FLAG_FALLBACK,
        source,
    )

    private fun rightStickX(event: MotionEvent): Float =
        event.getAxisValue(if (usesRxRy(event)) MotionEvent.AXIS_RX else MotionEvent.AXIS_Z)

    private fun rightStickY(event: MotionEvent): Float =
        event.getAxisValue(if (usesRxRy(event)) MotionEvent.AXIS_RY else MotionEvent.AXIS_RZ)

    private fun usesRxRy(event: MotionEvent): Boolean {
        if (event.deviceId != axesDeviceId) {
            axesDeviceId = event.deviceId
            val device = event.device
            usesRxRy = device != null &&
                device.getMotionRange(MotionEvent.AXIS_Z, InputDevice.SOURCE_JOYSTICK) == null &&
                device.getMotionRange(MotionEvent.AXIS_RX, InputDevice.SOURCE_JOYSTICK) != null
        }
        return usesRxRy
    }
}
