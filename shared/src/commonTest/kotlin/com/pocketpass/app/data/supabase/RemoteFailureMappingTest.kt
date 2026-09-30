package com.pocketpass.app.data.supabase

import com.pocketpass.app.domain.model.ACCOUNT_BANNED_HINT
import com.pocketpass.app.domain.model.ACCOUNT_BANNED_MESSAGE
import com.pocketpass.app.domain.model.GROUP_MESSAGES_BLOCKED
import com.pocketpass.app.domain.model.isAccountBanned
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteFailureMappingTest {
    @Test
    fun accountBannedHintMapsToADistinctFailure() {
        val failure = requireNotNull(postgrestHintFailure(ACCOUNT_BANNED_HINT, "This account is banned."))

        assertTrue(failure.isAccountBanned())
        assertEquals(RepositoryFailureKind.Forbidden, failure.kind)
        assertEquals(ACCOUNT_BANNED_MESSAGE, failure.message)
        assertFalse(failure.retryable)
    }

    @Test
    fun otherHintsKeepTheirMapping() {
        assertEquals(
            RepositoryFailure(RepositoryFailureKind.Validation, "Rejected", retryable = false),
            postgrestHintFailure("BOARD_TEXT_REJECTED", "Rejected"),
        )
        assertEquals(
            RepositoryFailure(RepositoryFailureKind.Forbidden, GROUP_MESSAGES_BLOCKED, retryable = false),
            postgrestHintFailure("GROUP_MESSAGES_BLOCKED", "Blocked"),
        )
        val directBlock = requireNotNull(postgrestHintFailure("DIRECT_MESSAGES_BLOCKED", "Blocked"))
        assertEquals(RepositoryFailureKind.Forbidden, directBlock.kind)
        assertFalse(directBlock.isAccountBanned())
        assertNull(postgrestHintFailure(null, "Forbidden"))
        assertNull(postgrestHintFailure("APP_SUSPENDED", "Suspended"))
    }

    @Test
    fun onlyTheBannedFailureCountsAsBanned() {
        assertFalse(RepositoryFailure(RepositoryFailureKind.Forbidden, "This account cannot perform that action").isAccountBanned())
        assertFalse(RepositoryFailure(RepositoryFailureKind.Unknown, ACCOUNT_BANNED_MESSAGE).isAccountBanned())
    }
}
