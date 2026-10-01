package com.pocketpass.app.input

import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal fun hasSource(sources: Int, source: Int): Boolean = sources and source == source

internal fun isGamepadDevice(sources: Int): Boolean =
    hasSource(sources, InputDevice.SOURCE_GAMEPAD) || hasSource(sources, InputDevice.SOURCE_JOYSTICK)

internal fun isControllerKeySource(source: Int): Boolean =
    isGamepadDevice(source) || hasSource(source, InputDevice.SOURCE_DPAD)

class GamepadPresence(private val inputManager: InputManager) : InputManager.InputDeviceListener {
    var active by mutableStateOf(inputManager.inputDeviceIds.any(::isConnectedGamepad))
        private set

    fun start() {
        inputManager.registerInputDeviceListener(this, null)
        if (!active && inputManager.inputDeviceIds.any(::isConnectedGamepad)) active = true
    }

    fun stop() {
        inputManager.unregisterInputDeviceListener(this)
    }

    fun activateFrom(event: KeyEvent): Boolean {
        if (active || !isControllerKeySource(event.source)) return false
        active = true
        return true
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        if (!active && isConnectedGamepad(deviceId)) active = true
    }

    override fun onInputDeviceRemoved(deviceId: Int) = Unit

    override fun onInputDeviceChanged(deviceId: Int) = onInputDeviceAdded(deviceId)

    private fun isConnectedGamepad(deviceId: Int): Boolean {
        val device = inputManager.getInputDevice(deviceId) ?: return false
        return !device.isVirtual && isGamepadDevice(device.sources)
    }
}
