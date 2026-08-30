package com.pocketpass.app.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiiEditorRightStickInputTest {
    @Test
    fun centredOrLightlyTiltedStickHasNoDirection() {
        assertNull(resolveRightStickDirection(x = 0f, y = 0f, current = null))
        assertNull(resolveRightStickDirection(x = 0.49f, y = -0.49f, current = null))
    }

    @Test
    fun dominantAxisPicksTheDirection() {
        assertEquals(RightStickDirection.Right, resolveRightStickDirection(x = 0.8f, y = 0.3f, current = null))
        assertEquals(RightStickDirection.Left, resolveRightStickDirection(x = -0.8f, y = 0.3f, current = null))
        assertEquals(RightStickDirection.Up, resolveRightStickDirection(x = 0.2f, y = -0.9f, current = null))
        assertEquals(RightStickDirection.Down, resolveRightStickDirection(x = 0.2f, y = 0.9f, current = null))
    }

    @Test
    fun heldDirectionSticksUntilTheReleaseThreshold() {
        assertEquals(
            RightStickDirection.Right,
            resolveRightStickDirection(x = 0.35f, y = 0f, current = RightStickDirection.Right),
        )
        assertEquals(
            RightStickDirection.Right,
            resolveRightStickDirection(x = 0.4f, y = -0.95f, current = RightStickDirection.Right),
        )
        assertNull(resolveRightStickDirection(x = 0.25f, y = 0f, current = RightStickDirection.Right))
    }

    @Test
    fun rollingPastTheReleaseThresholdSwitchesDirection() {
        assertEquals(
            RightStickDirection.Up,
            resolveRightStickDirection(x = 0.2f, y = -0.9f, current = RightStickDirection.Right),
        )
        assertEquals(
            RightStickDirection.Left,
            resolveRightStickDirection(x = -0.9f, y = 0f, current = RightStickDirection.Right),
        )
        assertEquals(
            RightStickDirection.Down,
            resolveRightStickDirection(x = 0f, y = 0.6f, current = RightStickDirection.Up),
        )
    }
}
