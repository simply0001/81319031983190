package com.pocketpass.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileNameTest {
    @Test
    fun inputFilterLowercasesStripsAndCaps() {
        assertEquals("newname", filterProfileNameInput("New Name!"))
        assertEquals("petah.g", filterProfileNameInput("Petah.G"))
        assertEquals(
            "a".repeat(PROFILE_NAME_MAX_LENGTH),
            filterProfileNameInput("a".repeat(PROFILE_NAME_MAX_LENGTH + 5)),
        )
        assertEquals("", filterProfileNameInput("   ---___   "))
    }

    @Test
    fun validityFollowsTheSetupRule() {
        assertFalse(isValidProfileName("ab"))
        assertTrue(isValidProfileName("abc"))
        assertFalse(isValidProfileName(".abc"))
        assertTrue(isValidProfileName("a.b.c"))
        assertTrue(isValidProfileName("a".repeat(PROFILE_NAME_MAX_LENGTH)))
        assertFalse(isValidProfileName("a".repeat(PROFILE_NAME_MAX_LENGTH + 1)))
        assertFalse(isValidProfileName("Abc"))
        assertFalse(isValidProfileName(""))
    }
}
