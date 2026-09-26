package com.pocketpass.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class KeyboardShiftStateTest {
    @Test fun singleTapOnlyCapitalizesTheNextLetter() {
        val shift = KeyboardShiftState().tap(100, 300)
        assertEquals(KeyboardShiftMode.Shift, shift.mode)
        assertEquals(KeyboardShiftMode.Off, shift.afterKey(PocketKey.Character("A")).mode)
    }

    @Test fun doubleTapLocksUntilTappedAgain() {
        var shift = KeyboardShiftState().tap(100, 300).tap(250, 300)
        for (key in listOf(PocketKey.Character("A"), PocketKey.Space, PocketKey.Character("!"),
            PocketKey.Backspace, PocketKey.Emoji, PocketKey.Alphabet, PocketKey.Submit)) {
            shift = shift.afterKey(key)
            assertEquals(KeyboardShiftMode.CapsLock, shift.mode)
        }
        assertEquals(KeyboardShiftMode.Off, shift.tap(2_000, 300).mode)
    }

    @Test fun slowSecondTapTurnsShiftOff() {
        assertEquals(KeyboardShiftMode.Off, KeyboardShiftState().tap(100, 300).tap(401, 300).mode)
    }

    @Test fun anotherKeyOrLayoutChangeBreaksTheDoubleTapSequence() {
        val shift = KeyboardShiftState().tap(100, 300)
        assertEquals(KeyboardShiftMode.Off, shift.afterKey(PocketKey.Space).tap(200, 300).mode)
        assertEquals(KeyboardShiftMode.Off, shift.cancelDoubleTap().tap(200, 300).mode)
    }
}
