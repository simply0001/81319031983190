package com.pocketpass.app.auth

import com.pocketpass.app.domain.state.RepositoryFailureKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupabaseFailureMapperTest {
    @Test
    fun oauthCallbackBanDescriptionMapsToTheSignUpBan() {
        val failure = AuthProviderCallbackException(
            providerError = "access_denied",
            providerDescription = "This sign-up is blocked because of a ban.",
        ).toRepositoryFailure()

        assertTrue(failure.isSignUpBan())
        assertEquals(RepositoryFailureKind.Forbidden, failure.kind)
        assertEquals(SIGN_UP_BANNED_MESSAGE, failure.message)
        assertFalse(failure.retryable)
    }

    @Test
    fun otherCallbackErrorsStayUnauthorized() {
        val failure = AuthProviderCallbackException(
            providerError = "access_denied",
            providerDescription = "The user denied the request.",
        ).toRepositoryFailure()

        assertEquals(RepositoryFailureKind.Unauthorized, failure.kind)
        assertFalse(failure.isSignUpBan())
    }

    @Test
    fun banTextMatchesWithoutCase() {
        assertTrue(isSignUpBanText("THIS SIGN-UP IS BLOCKED BECAUSE OF A BAN."))
        assertTrue(isSignUpBanText("403: This sign-up is blocked because of a ban."))
        assertFalse(isSignUpBanText("Signups not allowed for this instance"))
        assertFalse(isSignUpBanText(null))
    }
}
