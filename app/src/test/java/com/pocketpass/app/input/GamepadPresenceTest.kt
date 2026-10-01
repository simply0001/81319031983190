package com.pocketpass.app.input

import android.view.InputDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadPresenceTest {
    @Test
    fun builtInControllersCountAsGamepads() {
        assertTrue(isGamepadDevice(InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_DPAD))
        assertTrue(isGamepadDevice(InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD))
        assertTrue(isGamepadDevice(InputDevice.SOURCE_JOYSTICK))
    }

    @Test
    fun keyboardsTouchscreensAndRemotesAreNotGamepads() {
        assertFalse(isGamepadDevice(InputDevice.SOURCE_KEYBOARD))
        assertFalse(isGamepadDevice(InputDevice.SOURCE_TOUCHSCREEN))
        assertFalse(isGamepadDevice(InputDevice.SOURCE_DPAD))
        assertFalse(isGamepadDevice(InputDevice.SOURCE_MOUSE))
    }

    @Test
    fun dpadAndGamepadKeysTurnTheHighlightOnButTypingDoesNot() {
        assertTrue(isControllerKeySource(InputDevice.SOURCE_GAMEPAD))
        assertTrue(isControllerKeySource(InputDevice.SOURCE_DPAD))
        assertTrue(isControllerKeySource(InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD))
        assertFalse(isControllerKeySource(InputDevice.SOURCE_KEYBOARD))
        assertFalse(isControllerKeySource(InputDevice.SOURCE_TOUCHSCREEN))
    }
}
