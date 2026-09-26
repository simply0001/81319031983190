package com.pocketpass.app.ui.controller

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RingDisplayTransitionTest {
    private val target = FocusEntry(
        id = "home_edit",
        layer = 0,
        display = FocusDisplay.Top,
        bounds = Rect(600f, 300f, 700f, 400f),
        onActivate = {},
    )

    private fun TestScope.frameClock() = object : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(16)
            return onFrame(testScheduler.currentTime * 1_000_000L)
        }
    }

    @Test
    fun arrivingOnAnotherScreenFadesInAndSettlesAtFullSize() = runTest {
        val transition = RingDisplayTransition()
        launch(frameClock()) { transition.update(target, swapped = true, animate = true) }
        runCurrent()
        assertEquals(target, transition.target)
        assertEquals(0f, transition.alpha)
        assertEquals(1.08f, transition.scale)
        advanceTimeBy(112)
        runCurrent()
        assertTrue(transition.alpha > 0f && transition.alpha < 1f)
        assertTrue(transition.scale > 1f && transition.scale < 1.08f)
        advanceUntilIdle()
        assertEquals(1f, transition.alpha)
        assertEquals(1f, transition.scale)
    }

    @Test
    fun departingScreenKeepsItsRingUntilTheFadeFinishes() = runTest {
        val transition = RingDisplayTransition()
        transition.update(target, swapped = false, animate = true)
        launch(frameClock()) { transition.update(null, swapped = true, animate = true) }
        runCurrent()
        assertEquals(target, transition.target)
        assertEquals(1f, transition.alpha)
        advanceTimeBy(64)
        runCurrent()
        assertTrue(transition.alpha > 0f && transition.alpha < 1f)
        assertEquals(target, transition.target)
        advanceUntilIdle()
        assertEquals(0f, transition.alpha)
        assertNull(transition.target)
    }

    @Test
    fun rapidSwapBackReversesTheFadeWithoutDisappearing() = runTest {
        val transition = RingDisplayTransition()
        transition.update(target, swapped = false, animate = true)
        launch(frameClock()) { transition.update(null, swapped = true, animate = true) }
        advanceTimeBy(64)
        runCurrent()
        val intermediate = transition.alpha
        launch(frameClock()) { transition.update(target, swapped = true, animate = true) }
        runCurrent()
        assertEquals(intermediate, transition.alpha, 0.001f)
        advanceUntilIdle()
        assertEquals(target, transition.target)
        assertEquals(1f, transition.alpha)
    }

    @Test
    fun layoutUpdatesDoNotInterruptTheEntrance() = runTest {
        val transition = RingDisplayTransition()
        launch(frameClock()) { transition.update(target, swapped = true, animate = true) }
        advanceTimeBy(64)
        runCurrent()
        val intermediate = transition.alpha
        val moved = target.copy(bounds = target.bounds.translate(10f, 0f))
        transition.update(moved, swapped = false, animate = true)
        assertEquals(intermediate, transition.alpha)
        assertEquals(moved, transition.target)
        advanceUntilIdle()
        assertEquals(1f, transition.alpha)
    }

    @Test
    fun ordinaryFocusChangesStayFullyVisibleAndTouchClearsImmediately() = runTest {
        val transition = RingDisplayTransition()
        transition.update(target, swapped = false, animate = true)
        assertEquals(1f, transition.alpha)
        transition.update(target.copy(id = "home_mood"), swapped = false, animate = true)
        assertEquals(1f, transition.alpha)
        transition.update(null, swapped = false, animate = false)
        assertEquals(0f, transition.alpha)
        assertNull(transition.target)
    }

    @Test
    fun reducedMotionSnapsAndCancelsAnyRunningFade() = runTest {
        val transition = RingDisplayTransition()
        launch(frameClock()) { transition.update(target, swapped = true, animate = true) }
        advanceTimeBy(64)
        runCurrent()
        transition.update(null, swapped = true, animate = false)
        advanceUntilIdle()
        assertEquals(0f, transition.alpha)
        assertNull(transition.target)
        transition.update(target, swapped = true, animate = false)
        assertEquals(1f, transition.alpha)
        assertEquals(1f, transition.scale)
    }
}
