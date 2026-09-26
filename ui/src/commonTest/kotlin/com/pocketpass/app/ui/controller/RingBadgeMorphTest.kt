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
class RingBadgeMorphTest {
    private val badge = Rect(0.625f, -0.3125f, 1.3125f, 0.375f)

    private fun assertSameShape(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 0.001f)
        assertEquals(expected.top, actual.top, 0.001f)
        assertEquals(expected.right, actual.right, 0.001f)
        assertEquals(expected.bottom, actual.bottom, 0.001f)
    }

    private fun TestScope.frameClock() = object : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(16)
            return onFrame(testScheduler.currentTime * 1_000_000L)
        }
    }

    @Test
    fun badgeGrowsAndRetractsOverTimeInsteadOfSwitchingImmediately() = runTest {
        val morph = RingBadgeMorph()
        launch(frameClock()) { morph.moveTo(badge, animate = true) }
        runCurrent()
        assertEquals(CollapsedRingBadge, morph.value)
        advanceTimeBy(160)
        runCurrent()
        assertTrue(morph.value.top in badge.top..0.49f)
        assertTrue(morph.value.right in 0.51f..badge.right)
        advanceUntilIdle()
        assertEquals(badge, morph.value)

        launch(frameClock()) { morph.moveTo(CollapsedRingBadge, animate = true) }
        runCurrent()
        assertEquals(badge, morph.value)
        advanceTimeBy(160)
        runCurrent()
        assertTrue(morph.value.top > badge.top && morph.value.top < 0.5f)
        advanceUntilIdle()
        assertEquals(CollapsedRingBadge, morph.value)
    }

    @Test
    fun reversingMidTransitionStartsFromTheCurrentShape() = runTest {
        val morph = RingBadgeMorph()
        launch(frameClock()) { morph.moveTo(badge, animate = true) }
        advanceTimeBy(96)
        runCurrent()
        val intermediate = morph.value
        launch(frameClock()) { morph.moveTo(CollapsedRingBadge, animate = true) }
        runCurrent()
        assertSameShape(intermediate, morph.value)
        advanceTimeBy(32)
        runCurrent()
        val reversing = morph.value
        launch(frameClock()) { morph.moveTo(badge, animate = true) }
        runCurrent()
        assertSameShape(reversing, morph.value)
        advanceUntilIdle()
        assertEquals(badge, morph.value)
    }

    @Test
    fun disabledAnimationsSnapEvenDuringAMorph() = runTest {
        val morph = RingBadgeMorph()
        launch(frameClock()) { morph.moveTo(badge, animate = true) }
        advanceTimeBy(96)
        runCurrent()
        morph.moveTo(CollapsedRingBadge, animate = false)
        advanceUntilIdle()
        assertEquals(CollapsedRingBadge, morph.value)
        morph.moveTo(badge, animate = false)
        assertEquals(badge, morph.value)
    }

    @Test
    fun badgeTracksTheMovingRingBoundsAndDisplayScale() {
        val target = FocusEntry(
            id = "notifications",
            layer = 0,
            display = FocusDisplay.Bottom,
            bounds = Rect(2016f, 594f, 2176f, 754f),
            scale = 2f,
            badgeBounds = Rect(50f, -25f, 105f, 30f),
            onActivate = {},
        )
        assertEquals(badge, target.normalizedRingBadge())
        assertEquals(target.badgeBounds, badge.localRingBadge(target.bounds, target.scale))
        assertEquals(
            Rect(75f, -25f, 157.5f, 30f),
            badge.localRingBadge(Rect(100f, 200f, 340f, 360f), 2f),
        )
        assertEquals(CollapsedRingBadge, target.copy(badgeBounds = null).normalizedRingBadge())
        assertNull(CollapsedRingBadge.localRingBadge(target.bounds, target.scale))
    }
}
