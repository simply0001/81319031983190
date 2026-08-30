package com.pocketpass.app.input

import android.view.KeyEvent
import com.pocketpass.app.mii.MiiEditorEvent
import com.pocketpass.app.mii.verticalUpDelta
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState

internal sealed interface MiiEditorGamepadKeyAction {
    data object PassThrough : MiiEditorGamepadKeyAction
    data object Consume : MiiEditorGamepadKeyAction
    data object Continue : MiiEditorGamepadKeyAction
    data class Adjust(val delta: Int) : MiiEditorGamepadKeyAction
    data object CloseAdjustment : MiiEditorGamepadKeyAction
}

internal fun classifyMiiEditorGamepadKey(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    editorEnabled: Boolean,
    editorVisible: Boolean,
    adjustmentOpen: Boolean = false,
    verticalUpDelta: Int? = null,
): MiiEditorGamepadKeyAction {
    if (!editorEnabled || !editorVisible) return MiiEditorGamepadKeyAction.PassThrough
    val isDown = action == KeyEvent.ACTION_DOWN
    val isInitialPress = isDown && repeatCount == 0
    return when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_Y ->
            if (isInitialPress) MiiEditorGamepadKeyAction.Continue
            else MiiEditorGamepadKeyAction.Consume

        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT ->
            if (!adjustmentOpen) MiiEditorGamepadKeyAction.PassThrough
            else if (isDown && verticalUpDelta == null) {
                MiiEditorGamepadKeyAction.Adjust(
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1,
                )
            } else MiiEditorGamepadKeyAction.Consume

        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN ->
            if (!adjustmentOpen) MiiEditorGamepadKeyAction.PassThrough
            else if (isDown && verticalUpDelta != null) {
                MiiEditorGamepadKeyAction.Adjust(
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) verticalUpDelta else -verticalUpDelta,
                )
            } else MiiEditorGamepadKeyAction.Consume

        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER ->
            if (!adjustmentOpen) MiiEditorGamepadKeyAction.PassThrough
            else if (isInitialPress) MiiEditorGamepadKeyAction.CloseAdjustment
            else MiiEditorGamepadKeyAction.Consume

        else -> MiiEditorGamepadKeyAction.PassThrough
    }
}

fun handleMiiEditorGamepadKeyEvent(
    event: KeyEvent,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
): Boolean = when (
    val action = classifyMiiEditorGamepadKey(
        keyCode = event.keyCode,
        action = event.action,
        repeatCount = event.repeatCount,
        editorEnabled = state.miiEditorEnabled,
        editorVisible = state.miiEditor.isEditorVisible,
        adjustmentOpen = state.miiEditor.activeAdjustment != null,
        verticalUpDelta = state.miiEditor.activeAdjustment?.verticalUpDelta,
    )
) {
    MiiEditorGamepadKeyAction.PassThrough -> false
    MiiEditorGamepadKeyAction.Consume -> true
    MiiEditorGamepadKeyAction.Continue -> {
        if (state.miiEditor.canContinue) {
            dispatch(PocketPassEvent.Mii(MiiEditorEvent.Continue))
        }
        true
    }
    is MiiEditorGamepadKeyAction.Adjust -> {
        val field = state.miiEditor.activeAdjustment
        val value = state.miiEditor.activeAdjustmentValue
        if (field != null && value != null) {
            dispatch(PocketPassEvent.Mii(MiiEditorEvent.SetAdjustment(field, value + action.delta)))
        }
        true
    }
    MiiEditorGamepadKeyAction.CloseAdjustment -> {
        dispatch(PocketPassEvent.Mii(MiiEditorEvent.CloseAdjustment))
        true
    }
}
