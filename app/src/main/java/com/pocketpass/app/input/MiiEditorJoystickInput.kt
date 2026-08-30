package com.pocketpass.app.input

import android.view.InputDevice
import android.view.MotionEvent
import com.pocketpass.app.model.PocketPassUiState
import kotlin.math.abs
import kotlin.math.hypot

internal const val JOYSTICK_DEADZONE = 0.15f

internal data class JoystickDeflection(val x: Float, val y: Float)

internal sealed interface MiiEditorJoystickAction {
    data object PassThrough : MiiEditorJoystickAction
    data class Deflect(val x: Float, val y: Float) : MiiEditorJoystickAction
    data object Release : MiiEditorJoystickAction
}

internal fun processJoystickDeflection(
    x: Float,
    y: Float,
    deadzone: Float = JOYSTICK_DEADZONE,
): JoystickDeflection? {
    val magnitude = hypot(x, y)
    if (magnitude <= deadzone) return null
    val scale = ((magnitude - deadzone) / (1f - deadzone)).coerceAtMost(1f) / magnitude
    val rescaledX = x * scale
    val rescaledY = y * scale
    return JoystickDeflection(
        x = (rescaledX * abs(rescaledX)).coerceIn(-1f, 1f),
        y = (rescaledY * abs(rescaledY)).coerceIn(-1f, 1f),
    )
}

internal fun classifyMiiEditorJoystick(
    isJoystickMove: Boolean,
    editorEnabled: Boolean,
    editorVisible: Boolean,
    x: Float,
    y: Float,
    wasDeflected: Boolean,
): MiiEditorJoystickAction {
    if (!isJoystickMove || !editorEnabled || !editorVisible) {
        return MiiEditorJoystickAction.PassThrough
    }
    val deflection = processJoystickDeflection(x, y)
    return when {
        deflection != null ->
            MiiEditorJoystickAction.Deflect(deflection.x, deflection.y)

        wasDeflected -> MiiEditorJoystickAction.Release
        else -> MiiEditorJoystickAction.PassThrough
    }
}

class MiiEditorJoystickHandler(
    private val sink: (Float, Float) -> Unit,
) {
    private var wasDeflected = false

    fun handle(event: MotionEvent, state: PocketPassUiState): Boolean =
        when (
            val action = classifyMiiEditorJoystick(
                isJoystickMove = event.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
                    event.action == MotionEvent.ACTION_MOVE,
                editorEnabled = state.miiEditorEnabled,
                editorVisible = state.miiEditor.isEditorVisible,
                x = event.getAxisValue(MotionEvent.AXIS_X),
                y = event.getAxisValue(MotionEvent.AXIS_Y),
                wasDeflected = wasDeflected,
            )
        ) {
            is MiiEditorJoystickAction.Deflect -> {
                wasDeflected = true
                sink(action.x, action.y)
                true
            }

            MiiEditorJoystickAction.Release -> {
                wasDeflected = false
                sink(0f, 0f)
                true
            }

            MiiEditorJoystickAction.PassThrough -> false
        }
}
