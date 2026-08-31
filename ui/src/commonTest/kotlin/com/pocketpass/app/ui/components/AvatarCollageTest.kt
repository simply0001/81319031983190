package com.pocketpass.app.ui.components

import kotlin.test.assertEquals
import kotlin.test.Test

class AvatarCollageTest {
    @Test
    fun oneOrNoMembersFillTheWholeCircle() {
        assertEquals(listOf(CollageSlot(0f, 0f, 1f, 1f)), collageSlots(0))
        assertEquals(listOf(CollageSlot(0f, 0f, 1f, 1f)), collageSlots(1))
    }

    @Test
    fun twoMembersSplitIntoHalves() {
        assertEquals(
            listOf(CollageSlot(0f, 0f, 0.5f, 1f), CollageSlot(0.5f, 0f, 0.5f, 1f)),
            collageSlots(2),
        )
    }

    @Test
    fun threeMembersKeepAHalfAndStackTwoQuadrants() {
        val slots = collageSlots(3)
        assertEquals(3, slots.size)
        assertEquals(CollageSlot(0f, 0f, 0.5f, 1f), slots[0])
        assertEquals(listOf(true, true), slots.drop(1).map { it.isQuadrant })
    }

    @Test
    fun fourOrMoreMembersUseAGridOfQuadrants() {
        assertEquals(4, collageSlots(4).size)
        assertEquals(collageSlots(4), collageSlots(9))
        assertEquals(listOf(true, true, true, true), collageSlots(4).map { it.isQuadrant })
    }
}
