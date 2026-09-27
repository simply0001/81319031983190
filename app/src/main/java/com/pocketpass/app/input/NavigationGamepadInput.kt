package com.pocketpass.app.input

import android.view.KeyEvent
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.blocksShoulderTabs
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.model.hasDismissableLayer
import com.pocketpass.app.boards.BoardAction
import com.pocketpass.app.boards.BoardsScreen
import com.pocketpass.app.ui.controller.ControllerFocus
import com.pocketpass.app.ui.controller.FocusDirection

internal enum class NavigationGamepadKeyAction {
    PassThrough,
    Consume,
    TabPrev,
    TabNext,
    Move,
    Activate,
    Submit,
    SwapDisplay,
}

internal fun classifyNavigationGamepadKey(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    hasBlockingOverlay: Boolean,
    hasFocusTargets: Boolean,
    hasKeyboardSubmit: Boolean = false,
    canSwapDisplay: Boolean = false,
): NavigationGamepadKeyAction {
    val isDown = action == KeyEvent.ACTION_DOWN
    val isInitialPress = isDown && repeatCount == 0
    val isHeldStep = isDown &&
        repeatCount >= DPAD_HOLD_START_REPEAT &&
        (repeatCount - DPAD_HOLD_START_REPEAT) % DPAD_HOLD_STEP_REPEATS == 0
    return when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_L1 ->
            if (hasBlockingOverlay) NavigationGamepadKeyAction.PassThrough
            else if (isInitialPress) NavigationGamepadKeyAction.TabPrev
            else NavigationGamepadKeyAction.Consume

        KeyEvent.KEYCODE_BUTTON_R1 ->
            if (hasBlockingOverlay) NavigationGamepadKeyAction.PassThrough
            else if (isInitialPress) NavigationGamepadKeyAction.TabNext
            else NavigationGamepadKeyAction.Consume

        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        ->
            if (!hasFocusTargets) NavigationGamepadKeyAction.PassThrough
            else if (isInitialPress || isHeldStep) NavigationGamepadKeyAction.Move
            else NavigationGamepadKeyAction.Consume

        KeyEvent.KEYCODE_BUTTON_START ->
            if (!hasKeyboardSubmit) NavigationGamepadKeyAction.PassThrough
            else if (isInitialPress) NavigationGamepadKeyAction.Submit
            else NavigationGamepadKeyAction.Consume

        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_DPAD_CENTER,
        ->
            if (!hasFocusTargets) NavigationGamepadKeyAction.PassThrough
            else if (isInitialPress) NavigationGamepadKeyAction.Activate
            else NavigationGamepadKeyAction.Consume

        KeyEvent.KEYCODE_BUTTON_X ->
            if (!canSwapDisplay) NavigationGamepadKeyAction.PassThrough
            else if (isInitialPress) NavigationGamepadKeyAction.SwapDisplay
            else NavigationGamepadKeyAction.Consume

        else -> NavigationGamepadKeyAction.PassThrough
    }
}

private const val DPAD_HOLD_START_REPEAT = 2
private const val DPAD_HOLD_STEP_REPEATS = 3

internal fun adjacentDestination(
    current: PocketPassDestination,
    step: Int,
): PocketPassDestination {
    val order = PocketPassDestination.entries
    val index = order.indexOf(current)
    val next = Math.floorMod(index + step, order.size)
    return order[next]
}

private fun directionFor(keyCode: Int): FocusDirection? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_LEFT -> FocusDirection.Left
    KeyEvent.KEYCODE_DPAD_RIGHT -> FocusDirection.Right
    KeyEvent.KEYCODE_DPAD_UP -> FocusDirection.Up
    KeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.Down
    else -> null
}

fun handleNavigationGamepadKeyEvent(
    event: KeyEvent,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    focus: ControllerFocus,
): Boolean {
    if(state.rootDestination == PocketPassDestination.Messages && state.boardsVisible &&
        event.keyCode in listOf(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1)) {
        val overlayOpen = state.copy(routes = listOf(PocketPassRoute.Root(PocketPassDestination.Home))).hasDismissableLayer()
        if(overlayOpen || focus.keyboardSubmit != null) return false
        if(event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val route = state.routes.lastOrNull()
            val current = when {
                route is PocketPassRoute.MessageDetail || route is PocketPassRoute.NewGroup -> 1
                state.boards.screen == BoardsScreen.Chats -> 1
                state.boards.screen == BoardsScreen.Inbox -> 2
                else -> 0
            }
            val next = Math.floorMod(current + if(event.keyCode == KeyEvent.KEYCODE_BUTTON_R1) 1 else -1, 3)
            if(route !is PocketPassRoute.Root) dispatch(PocketPassEvent.SelectDestination(PocketPassDestination.Messages))
            dispatch(PocketPassEvent.Boards(when(next) {
                1 -> BoardAction.OpenChats
                2 -> BoardAction.Inbox
                else -> BoardAction.Directory()
            }))
            focus.focus(when(next) { 1 -> "boards_chats"; 2 -> "boards_inbox"; else -> "boards_directory" })
        }
        return true
    }
    return when (
    classifyNavigationGamepadKey(
        keyCode = event.keyCode,
        action = event.action,
        repeatCount = event.repeatCount,
        hasBlockingOverlay = state.blocksShoulderTabs(),
        hasFocusTargets = focus.hasTargets(),
        hasKeyboardSubmit = focus.keyboardSubmit != null,
        canSwapDisplay = focus.canSwapDisplay(),
    )
) {
    NavigationGamepadKeyAction.PassThrough -> false
    NavigationGamepadKeyAction.Consume -> true
    NavigationGamepadKeyAction.TabPrev -> {
        dispatch(PocketPassEvent.SelectDestination(adjacentDestination(state.rootDestination, -1)))
        true
    }
    NavigationGamepadKeyAction.TabNext -> {
        dispatch(PocketPassEvent.SelectDestination(adjacentDestination(state.rootDestination, 1)))
        true
    }
    NavigationGamepadKeyAction.Move -> {
        val direction = directionFor(event.keyCode)
        val adjustDelta = when (direction) {
            FocusDirection.Left -> -1
            FocusDirection.Right -> 1
            else -> 0
        }
        if (adjustDelta == 0 || !focus.adjust(adjustDelta)) {
            direction?.let { focus.move(it, held = event.repeatCount > 0) }
        }
        true
    }
    NavigationGamepadKeyAction.Activate -> {
        focus.activate()
        true
    }
    NavigationGamepadKeyAction.Submit -> {
        focus.keyboardSubmit?.invoke()
        true
    }
    NavigationGamepadKeyAction.SwapDisplay -> {
        focus.swapDisplay()
        true
    }
}
}
