package com.pocketpass.app.input

import android.view.KeyEvent
import com.pocketpass.app.model.PocketPassDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationGamepadInputTest {
    @Test
    fun shoulderButtonsCycleTabsOnInitialPress() {
        assertEquals(
            NavigationGamepadKeyAction.TabPrev,
            classify(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.ACTION_DOWN, 0),
        )
        assertEquals(
            NavigationGamepadKeyAction.TabNext,
            classify(KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.ACTION_DOWN, 0),
        )
    }

    @Test
    fun shoulderButtonReleaseIsConsumedWithoutSwitching() {
        assertEquals(
            NavigationGamepadKeyAction.Consume,
            classify(KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.ACTION_UP, 0),
        )
    }

    @Test
    fun shoulderButtonsPassThroughWhenOverlayIsOpen() {
        assertEquals(
            NavigationGamepadKeyAction.PassThrough,
            classify(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.ACTION_DOWN, 0, hasBlockingOverlay = true),
        )
    }

    @Test
    fun dpadMovesWhenTargetsExistAndPassesThroughOtherwise() {
        assertEquals(
            NavigationGamepadKeyAction.Move,
            classify(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN, 0),
        )
        assertEquals(
            NavigationGamepadKeyAction.PassThrough,
            classify(
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.ACTION_DOWN,
                0,
                hasFocusTargets = false,
            ),
        )
    }

    @Test
    fun heldDpadKeepsMovingAtAThrottledRate() {
        assertEquals(
            NavigationGamepadKeyAction.Consume,
            classify(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN, 1),
        )
        assertEquals(
            NavigationGamepadKeyAction.Move,
            classify(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN, 2),
        )
        assertEquals(
            NavigationGamepadKeyAction.Consume,
            classify(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN, 3),
        )
        assertEquals(
            NavigationGamepadKeyAction.Move,
            classify(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN, 5),
        )
        assertEquals(
            NavigationGamepadKeyAction.Consume,
            classify(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_UP, 5),
        )
    }

    @Test
    fun buttonAActivatesWhenTargetsExist() {
        assertEquals(
            NavigationGamepadKeyAction.Activate,
            classify(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_DOWN, 0),
        )
        assertEquals(
            NavigationGamepadKeyAction.Activate,
            classify(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN, 0),
        )
    }

    @Test
    fun adjacentDestinationWrapsAroundAtTheEnds() {
        assertEquals(
            PocketPassDestination.Settings,
            adjacentDestination(PocketPassDestination.Messages, -1),
        )
        assertEquals(
            PocketPassDestination.Messages,
            adjacentDestination(PocketPassDestination.Settings, 1),
        )
        assertEquals(
            PocketPassDestination.Activities,
            adjacentDestination(PocketPassDestination.Home, 1),
        )
    }

    @Test
    fun buttonXSwapsScreensOnlyWhenTheOtherScreenHasTargets() {
        assertEquals(
            NavigationGamepadKeyAction.SwapDisplay,
            classify(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.ACTION_DOWN, 0, canSwapDisplay = true),
        )
        assertEquals(
            NavigationGamepadKeyAction.Consume,
            classify(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.ACTION_UP, 0, canSwapDisplay = true),
        )
        assertEquals(
            NavigationGamepadKeyAction.Consume,
            classify(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.ACTION_DOWN, 1, canSwapDisplay = true),
        )
        assertEquals(
            NavigationGamepadKeyAction.PassThrough,
            classify(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.ACTION_DOWN, 0, canSwapDisplay = false),
        )
    }

    private fun classify(
        keyCode: Int,
        action: Int,
        repeatCount: Int,
        hasBlockingOverlay: Boolean = false,
        hasFocusTargets: Boolean = true,
        canSwapDisplay: Boolean = false,
    ): NavigationGamepadKeyAction = classifyNavigationGamepadKey(
        keyCode = keyCode,
        action = action,
        repeatCount = repeatCount,
        hasBlockingOverlay = hasBlockingOverlay,
        hasFocusTargets = hasFocusTargets,
        canSwapDisplay = canSwapDisplay,
    )
}
