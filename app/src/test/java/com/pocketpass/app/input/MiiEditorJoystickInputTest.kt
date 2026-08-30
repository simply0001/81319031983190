package com.pocketpass.app.input

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiiEditorJoystickInputTest {
    @Test
    fun deflectionInsideTheDeadzoneIsIgnored() {
        assertNull(processJoystickDeflection(x = 0.1f, y = 0.05f))
        assertNull(processJoystickDeflection(x = -0.14f, y = 0f))
        assertNull(processJoystickDeflection(x = 0f, y = 0f))
    }

    @Test
    fun deflectionRampsContinuouslyFromTheDeadzoneEdge() {
        val nearEdge = processJoystickDeflection(x = 0.16f, y = 0f)!!
        assertTrue(abs(nearEdge.x) < 0.01f)
        assertEquals(0f, nearEdge.y, 0f)
    }

    @Test
    fun fullDeflectionReachesFullRange() {
        val full = processJoystickDeflection(x = 1f, y = 0f)!!
        assertEquals(1f, full.x, 0.0001f)
        assertEquals(0f, full.y, 0.0001f)

        val diagonal = processJoystickDeflection(x = 1f, y = 1f)!!
        assertEquals(0.5f, diagonal.x, 0.001f)
        assertEquals(0.5f, diagonal.y, 0.001f)
    }

    @Test
    fun shapingIsMonotonicAndKeepsSign() {
        val half = processJoystickDeflection(x = 0.6f, y = 0f)!!
        val strong = processJoystickDeflection(x = 0.9f, y = 0f)!!
        assertTrue(half.x > 0f)
        assertTrue(strong.x > half.x)

        val left = processJoystickDeflection(x = -0.9f, y = 0f)!!
        assertEquals(-strong.x, left.x, 0.0001f)
        val up = processJoystickDeflection(x = 0f, y = -0.9f)!!
        assertEquals(-strong.x, up.y, 0.0001f)
    }

    @Test
    fun onlyVisibleEditorJoystickMovesAreHandled() {
        assertEquals(
            MiiEditorJoystickAction.PassThrough,
            classifyMiiEditorJoystick(
                isJoystickMove = false,
                editorEnabled = true,
                editorVisible = true,
                x = 1f,
                y = 0f,
                wasDeflected = false,
            ),
        )
        assertEquals(
            MiiEditorJoystickAction.PassThrough,
            classifyMiiEditorJoystick(
                isJoystickMove = true,
                editorEnabled = true,
                editorVisible = false,
                x = 1f,
                y = 0f,
                wasDeflected = false,
            ),
        )
        assertEquals(
            MiiEditorJoystickAction.Deflect(x = 1f, y = 0f),
            classifyMiiEditorJoystick(
                isJoystickMove = true,
                editorEnabled = true,
                editorVisible = true,
                x = 1f,
                y = 0f,
                wasDeflected = false,
            ),
        )
    }

    @Test
    fun releaseIsForwardedExactlyOnce() {
        assertEquals(
            MiiEditorJoystickAction.Release,
            classifyMiiEditorJoystick(
                isJoystickMove = true,
                editorEnabled = true,
                editorVisible = true,
                x = 0f,
                y = 0f,
                wasDeflected = true,
            ),
        )
        assertEquals(
            MiiEditorJoystickAction.PassThrough,
            classifyMiiEditorJoystick(
                isJoystickMove = true,
                editorEnabled = true,
                editorVisible = true,
                x = 0f,
                y = 0f,
                wasDeflected = false,
            ),
        )
    }
}
