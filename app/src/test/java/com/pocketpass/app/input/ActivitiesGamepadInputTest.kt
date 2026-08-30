package com.pocketpass.app.input

import android.view.KeyEvent
import com.pocketpass.app.model.PocketPassDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivitiesGamepadInputTest {
    @Test
    fun freshYDownTogglesOnlyOnActivities() {
        assertEquals(
            ActivitiesGamepadKeyAction.Toggle,
            classifyActivitiesGamepadKey(
                keyCode = KeyEvent.KEYCODE_BUTTON_Y,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                destination = PocketPassDestination.Activities,
            ),
        )
        assertEquals(
            ActivitiesGamepadKeyAction.PassThrough,
            classifyActivitiesGamepadKey(
                keyCode = KeyEvent.KEYCODE_BUTTON_Y,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                destination = PocketPassDestination.Home,
            ),
        )
    }

    @Test
    fun YRepeatAndReleaseAreConsumedWithoutTogglingAgain() {
        assertEquals(
            ActivitiesGamepadKeyAction.Consume,
            classifyActivitiesGamepadKey(
                keyCode = KeyEvent.KEYCODE_BUTTON_Y,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                destination = PocketPassDestination.Activities,
            ),
        )
        assertEquals(
            ActivitiesGamepadKeyAction.Consume,
            classifyActivitiesGamepadKey(
                keyCode = KeyEvent.KEYCODE_BUTTON_Y,
                action = KeyEvent.ACTION_UP,
                repeatCount = 0,
                destination = PocketPassDestination.Activities,
            ),
        )
    }

    @Test
    fun unrelatedButtonsPassThrough() {
        assertEquals(
            ActivitiesGamepadKeyAction.PassThrough,
            classifyActivitiesGamepadKey(
                keyCode = KeyEvent.KEYCODE_BUTTON_A,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                destination = PocketPassDestination.Activities,
            ),
        )
    }
}
