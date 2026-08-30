package com.pocketpass.app.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class MiiEditorGamepadInputTest {
    @Test
    fun freshYDownContinuesOnlyInsideTheVisibleEditor() {
        assertEquals(
            MiiEditorGamepadKeyAction.Continue,
            classifyMiiEditorGamepadKey(
                keyCode = KeyEvent.KEYCODE_BUTTON_Y,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                editorEnabled = true,
                editorVisible = true,
            ),
        )
        assertEquals(
            MiiEditorGamepadKeyAction.PassThrough,
            classifyMiiEditorGamepadKey(
                keyCode = KeyEvent.KEYCODE_BUTTON_Y,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                editorEnabled = true,
                editorVisible = false,
            ),
        )
    }

    @Test
    fun heldYAndYReleaseAreConsumedWithoutAdvancingAgain() {
        assertEquals(
            MiiEditorGamepadKeyAction.Consume,
            classifyMiiEditorGamepadKey(
                keyCode = KeyEvent.KEYCODE_BUTTON_Y,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                editorEnabled = true,
                editorVisible = true,
            ),
        )
        assertEquals(
            MiiEditorGamepadKeyAction.Consume,
            classifyMiiEditorGamepadKey(
                keyCode = KeyEvent.KEYCODE_BUTTON_Y,
                action = KeyEvent.ACTION_UP,
                repeatCount = 0,
                editorEnabled = true,
                editorVisible = true,
            ),
        )
    }

    @Test
    fun unrelatedButtonsPassThrough() {
        assertEquals(
            MiiEditorGamepadKeyAction.PassThrough,
            classifyMiiEditorGamepadKey(
                keyCode = KeyEvent.KEYCODE_BUTTON_A,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                editorEnabled = true,
                editorVisible = true,
            ),
        )
    }

    @Test
    fun verticalSlidersUseDpadUpAndDownAndIgnoreLeftRight() {
        assertEquals(
            MiiEditorGamepadKeyAction.Adjust(-1),
            classifyMiiEditorGamepadKey(
                keyCode = KeyEvent.KEYCODE_DPAD_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                editorEnabled = true,
                editorVisible = true,
                adjustmentOpen = true,
                verticalUpDelta = -1,
            ),
        )
        assertEquals(
            MiiEditorGamepadKeyAction.Adjust(1),
            classifyMiiEditorGamepadKey(
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 2,
                editorEnabled = true,
                editorVisible = true,
                adjustmentOpen = true,
                verticalUpDelta = -1,
            ),
        )
        assertEquals(
            MiiEditorGamepadKeyAction.Adjust(1),
            classifyMiiEditorGamepadKey(
                keyCode = KeyEvent.KEYCODE_DPAD_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                editorEnabled = true,
                editorVisible = true,
                adjustmentOpen = true,
                verticalUpDelta = 1,
            ),
        )
        assertEquals(
            MiiEditorGamepadKeyAction.Consume,
            classifyMiiEditorGamepadKey(
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                editorEnabled = true,
                editorVisible = true,
                adjustmentOpen = true,
                verticalUpDelta = -1,
            ),
        )
    }

    @Test
    fun dpadOnlyDrivesTheSliderWhileAnAdjustmentIsOpen() {
        assertEquals(
            MiiEditorGamepadKeyAction.PassThrough,
            classifyMiiEditorGamepadKey(
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                editorEnabled = true,
                editorVisible = true,
            ),
        )
        assertEquals(
            MiiEditorGamepadKeyAction.Adjust(1),
            classifyMiiEditorGamepadKey(
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 3,
                editorEnabled = true,
                editorVisible = true,
                adjustmentOpen = true,
            ),
        )
        assertEquals(
            MiiEditorGamepadKeyAction.Adjust(-1),
            classifyMiiEditorGamepadKey(
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                editorEnabled = true,
                editorVisible = true,
                adjustmentOpen = true,
            ),
        )
        assertEquals(
            MiiEditorGamepadKeyAction.Consume,
            classifyMiiEditorGamepadKey(
                keyCode = KeyEvent.KEYCODE_DPAD_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                editorEnabled = true,
                editorVisible = true,
                adjustmentOpen = true,
            ),
        )
        assertEquals(
            MiiEditorGamepadKeyAction.CloseAdjustment,
            classifyMiiEditorGamepadKey(
                keyCode = KeyEvent.KEYCODE_BUTTON_A,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                editorEnabled = true,
                editorVisible = true,
                adjustmentOpen = true,
            ),
        )
    }
}
