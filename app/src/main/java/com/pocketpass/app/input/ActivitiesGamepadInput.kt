package com.pocketpass.app.input

import android.view.KeyEvent
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState

internal enum class ActivitiesGamepadKeyAction {
    PassThrough,
    Consume,
    Toggle,
}

internal fun classifyActivitiesGamepadKey(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    destination: PocketPassDestination,
): ActivitiesGamepadKeyAction {
    if (
        keyCode != KeyEvent.KEYCODE_BUTTON_Y ||
        destination != PocketPassDestination.Activities
    ) {
        return ActivitiesGamepadKeyAction.PassThrough
    }

    return if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
        ActivitiesGamepadKeyAction.Toggle
    } else {
        ActivitiesGamepadKeyAction.Consume
    }
}

fun handleActivitiesGamepadKeyEvent(
    event: KeyEvent,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
): Boolean = when (
    classifyActivitiesGamepadKey(
        keyCode = event.keyCode,
        action = event.action,
        repeatCount = event.repeatCount,
        destination = state.rootDestination,
    )
) {
    ActivitiesGamepadKeyAction.PassThrough -> false
    ActivitiesGamepadKeyAction.Consume -> true
    ActivitiesGamepadKeyAction.Toggle -> {
        dispatch(PocketPassEvent.ShuffleActivities)
        true
    }
}
