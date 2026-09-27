package com.pocketpass.app.auth

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
