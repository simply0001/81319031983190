package com.pocketpass.app.input

import android.view.InputDevice
import android.view.KeyEvent
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.hasDismissableLayer
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.controller.ControllerFocus

internal enum class BackGamepadKeyAction {
    PassThrough,
    Consume,
    Back,
    Backspace,
}

internal fun classifyBackGamepadKey(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    hasDismissableLayer: Boolean,
    fromGamepad: Boolean = false,
    keyboardActive: Boolean = false,
    canBackspace: Boolean = false,
): BackGamepadKeyAction {
    val isDown = action == KeyEvent.ACTION_DOWN
    if (keyboardActive && canBackspace && keyCode == KeyEvent.KEYCODE_BUTTON_B) {
        val isInitialPress = isDown && repeatCount == 0
        val isHeldStep = isDown &&
            repeatCount >= BACKSPACE_HOLD_START_REPEAT &&
            (repeatCount - BACKSPACE_HOLD_START_REPEAT) % BACKSPACE_HOLD_STEP_REPEATS == 0
        return if (isInitialPress || isHeldStep) {
            BackGamepadKeyAction.Backspace
        } else {
            BackGamepadKeyAction.Consume
        }
    }
    val isGamepadBackButton = keyCode == KeyEvent.KEYCODE_BUTTON_B ||
        (keyboardActive && keyCode == KeyEvent.KEYCODE_BUTTON_X)
    val isBackKey = isGamepadBackButton || keyCode == KeyEvent.KEYCODE_BACK
    if (!isBackKey) return BackGamepadKeyAction.PassThrough
    if (!hasDismissableLayer) {
        return if (isGamepadBackButton || fromGamepad) {
            BackGamepadKeyAction.Consume
        } else {
            BackGamepadKeyAction.PassThrough
        }
    }

    return if (isDown && repeatCount == 0) {
        BackGamepadKeyAction.Back
    } else {
        BackGamepadKeyAction.Consume
    }
}

private const val BACKSPACE_HOLD_START_REPEAT = 2
private const val BACKSPACE_HOLD_STEP_REPEATS = 2

fun handleBackGamepadKeyEvent(
    event: KeyEvent,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    focus: ControllerFocus? = null,
): Boolean = when (
    classifyBackGamepadKey(
        keyCode = event.keyCode,
        action = event.action,
        repeatCount = event.repeatCount,
        hasDismissableLayer = state.hasDismissableLayer(),
        fromGamepad = event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD,
        keyboardActive = focus?.keyboardActive() == true,
        canBackspace = focus?.keyboardCanBackspace() == true,
    )
) {
    BackGamepadKeyAction.PassThrough -> false
    BackGamepadKeyAction.Consume -> true
    BackGamepadKeyAction.Back -> {
        dispatch(PocketPassEvent.Back)
        true
    }

    BackGamepadKeyAction.Backspace -> {
        focus?.keyboardBackspace?.invoke()
        true
    }
}
