package com.pocketpass.app.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class BackGamepadInputTest {
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

    private fun classify(
        keyCode: Int,
        action: Int,
        repeatCount: Int,
        hasDismissableLayer: Boolean = true,
        keyboardActive: Boolean = false,
        canBackspace: Boolean = keyboardActive,
    ) = classifyBackGamepadKey(
        keyCode = keyCode,
        action = action,
        repeatCount = repeatCount,
        hasDismissableLayer = hasDismissableLayer,
        fromGamepad = false,
        keyboardActive = keyboardActive,
        canBackspace = canBackspace,
    )
}
