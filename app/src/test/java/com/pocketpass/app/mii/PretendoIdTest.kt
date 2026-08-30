package com.pocketpass.app.mii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PretendoIdTest {
    @Test
    fun acceptsPretendoUsernamesAndLowercasesThem() {
        assertEquals("jonbarrow", PretendoId.normalize("jonbarrow"))
        assertEquals("some.name-1", PretendoId.normalize("  Some.Name-1 "))
        assertEquals("a_b_c_d", PretendoId.normalize("A_B_C_D"))
        assertEquals("abcdef", PretendoId.normalize("abcdef"))
        assertEquals("abcdefghijklmnop", PretendoId.normalize("abcdefghijklmnop"))
    }

    @Test
    fun rejectsIdsOutsidePretendoRules() {
        assertNull(PretendoId.normalize("abcde"))
        assertNull(PretendoId.normalize("abcdefghijklmnopq"))
        assertNull(PretendoId.normalize("-abcdef"))
        assertNull(PretendoId.normalize("abcdef."))
        assertNull(PretendoId.normalize("abc..def"))
        assertNull(PretendoId.normalize("abc_-def"))
        assertNull(PretendoId.normalize("abc def"))
        assertNull(PretendoId.normalize("héllo-there"))
        assertNull(PretendoId.normalize(""))
    }

    @Test
    fun onlyPretendoCharactersAreTypeable() {
        assertTrue("azAZ09-_.".all(PretendoId::isAllowedCharacter))
        assertFalse(" @!/é".any(PretendoId::isAllowedCharacter))
    }
}
