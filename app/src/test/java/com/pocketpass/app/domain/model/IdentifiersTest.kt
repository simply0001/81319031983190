package com.pocketpass.app.domain.model

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IdentifiersTest {
    @Test
    fun typedIdsRejectBlankValues() {
        assertThrows(IllegalArgumentException::class.java) {
            UserId(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ConversationId("")
        }
    }

    @Test
    fun generatedClientOperationIdsAreUnique() {
        assertNotEquals(ClientOperationId.new(), ClientOperationId.new())
    }

    @Test
    fun friendCodesPreserveLeadingZeroesAndFormatInTwoGroups() {
        val code = FriendCode("00123456")

        assertEquals("00123456", code.value)
        assertEquals("0012 3456", code.formatted)
        assertEquals(code, FriendCode.parseOrNull("00123456"))
    }

    @Test
    fun friendCodesRejectNonDigitsAndIncorrectLengths() {
        assertThrows(IllegalArgumentException::class.java) {
            FriendCode("1234567")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FriendCode("1234 5678")
        }
        assertEquals(null, FriendCode.parseOrNull("abcdefgh"))
    }
}
