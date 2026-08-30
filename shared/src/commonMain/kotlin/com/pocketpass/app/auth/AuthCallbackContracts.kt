package com.pocketpass.app.auth

import io.github.jan.supabase.auth.user.UserSession

sealed interface AuthCallbackResult {
    data class Authenticated(val session: UserSession) : AuthCallbackResult

    data class Failed(val error: Throwable) : AuthCallbackResult
}

sealed interface AuthCallbackDispatch {
    data object Dispatched : AuthCallbackDispatch

    data class Ignored(
        val reason: AuthCallbackDecision.Ignored.Reason,
    ) : AuthCallbackDispatch
}

class AuthProviderCallbackException(
    val providerError: String,
    val providerDescription: String?,
) : IllegalStateException(
    buildString {
        append("Authentication provider returned ")
        append(providerError)
        if (!providerDescription.isNullOrBlank()) {
            append(": ")
            append(providerDescription)
        }
    },
)
