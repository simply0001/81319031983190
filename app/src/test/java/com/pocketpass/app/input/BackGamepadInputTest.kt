package com.pocketpass.app.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class BackGamepadInputTest {
    @Test
    fun backLeavesCardActionsBeforeClosingTheShopAndConsumesRepeats() {
        for (key in listOf(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK)) {
            assertEquals(
                BackGamepadKeyAction.FocusParent,
                classifyBackGamepadKey(key, KeyEvent.ACTION_DOWN, 0, hasDismissableLayer = true, hasFocusParent = true),
            )
            assertEquals(
                BackGamepadKeyAction.Consume,
                classifyBackGamepadKey(key, KeyEvent.ACTION_UP, 0, hasDismissableLayer = true, hasFocusParent = true),
            )
            assertEquals(
                BackGamepadKeyAction.Consume,
                classifyBackGamepadKey(key, KeyEvent.ACTION_DOWN, 1, hasDismissableLayer = true, hasFocusParent = false),
            )
        }
        assertEquals(
            BackGamepadKeyAction.Backspace,
            classifyBackGamepadKey(
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.ACTION_DOWN, 0,
                hasDismissableLayer = true, keyboardActive = true, canBackspace = true, hasFocusParent = true,
            ),
        )
    }

    @Test
    fun bButtonBackspacesWhileTheKeyboardIsActiveAndRepeatsWhenHeld() {
        assertEquals(
            BackGamepadKeyAction.Backspace,
            classify(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.ACTION_DOWN, 0, keyboardActive = true),
        )
        assertEquals(
            BackGamepadKeyAction.Consume,
            classify(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.ACTION_DOWN, 1, keyboardActive = true),
        )
        assertEquals(
            BackGamepadKeyAction.Backspace,
            classify(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.ACTION_DOWN, 2, keyboardActive = true),
        )
        assertEquals(
            BackGamepadKeyAction.Consume,
            classify(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.ACTION_DOWN, 3, keyboardActive = true),
        )
        assertEquals(
            BackGamepadKeyAction.Backspace,
            classify(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.ACTION_DOWN, 4, keyboardActive = true),
        )
        assertEquals(
            BackGamepadKeyAction.Consume,
            classify(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.ACTION_UP, 0, keyboardActive = true),
        )
    }

    @Test
    fun xButtonClosesWhileTheKeyboardIsActive() {
        assertEquals(
            BackGamepadKeyAction.Back,
            classify(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.ACTION_DOWN, 0, keyboardActive = true),
        )
        assertEquals(
            BackGamepadKeyAction.Consume,
            classify(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.ACTION_UP, 0, keyboardActive = true),
        )
        assertEquals(
            BackGamepadKeyAction.Consume,
            classify(
                KeyEvent.KEYCODE_BUTTON_X,
                KeyEvent.ACTION_DOWN,
                0,
                hasDismissableLayer = false,
                keyboardActive = true,
            ),
        )
        assertEquals(
            BackGamepadKeyAction.PassThrough,
            classify(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.ACTION_DOWN, 0, keyboardActive = false),
        )
    }

    @Test
    fun bButtonGoesBackWhenTheActiveKeyboardHasNothingToDelete() {
        assertEquals(
            BackGamepadKeyAction.Back,
            classify(
                KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.ACTION_DOWN,
                0,
                keyboardActive = true,
                canBackspace = false,
            ),
        )
        assertEquals(
            BackGamepadKeyAction.Consume,
            classify(
                KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.ACTION_DOWN,
                2,
                keyboardActive = true,
                canBackspace = false,
            ),
        )
    }

    @Test
    fun bButtonStillGoesBackWithoutAnActiveKeyboard() {
        assertEquals(
            BackGamepadKeyAction.Back,
            classify(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.ACTION_DOWN, 0),
        )
        assertEquals(
            BackGamepadKeyAction.Consume,
            classify(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.ACTION_DOWN, 0, hasDismissableLayer = false),
        )
        assertEquals(
            BackGamepadKeyAction.PassThrough,
            classify(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN, 0, hasDismissableLayer = false),
        )
    }

    @Test
    fun bLeavesTheEmojiOrSymbolsPageBeforeClosingTheKeyboard() {
        assertEquals(
            BackGamepadKeyAction.KeyboardEscape,
            classify(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.ACTION_DOWN, 0, keyboardActive = true, canBackspace = false, canEscape = true),
        )
        assertEquals(
            BackGamepadKeyAction.Backspace,
            classify(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.ACTION_DOWN, 0, keyboardActive = true, canBackspace = true, canEscape = true),
        )
        assertEquals(
            BackGamepadKeyAction.Consume,
            classify(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.ACTION_DOWN, 1, keyboardActive = true, canBackspace = false, canEscape = true),
        )
        assertEquals(
            BackGamepadKeyAction.Back,
            classify(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.ACTION_DOWN, 0, keyboardActive = true, canBackspace = false),
        )
    }

    private fun classify(
        keyCode: Int,
        action: Int,
        repeatCount: Int,
        hasDismissableLayer: Boolean = true,
        keyboardActive: Boolean = false,
        canBackspace: Boolean = keyboardActive,
        canEscape: Boolean = false,
    ) = classifyBackGamepadKey(
        keyCode = keyCode,
        action = action,
        repeatCount = repeatCount,
        hasDismissableLayer = hasDismissableLayer,
        fromGamepad = false,
        keyboardActive = keyboardActive,
        canBackspace = canBackspace,
        canEscape = canEscape,
    )
}
