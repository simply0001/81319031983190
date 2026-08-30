package com.pocketpass.app.auth

import com.pocketpass.app.data.supabase.SupabaseBackendConfig

sealed interface AuthCallbackDecision {
    data class AuthorizationCode(
        val code: String,
    ) : AuthCallbackDecision {
        override fun toString(): String = "AuthorizationCode(code=<redacted>)"
    }

    data class ProviderError(
        val code: String,
        val description: String?,
    ) : AuthCallbackDecision

    data class Ignored(
        val reason: Reason,
    ) : AuthCallbackDecision {
        enum class Reason {
            MissingUri,
            MalformedUri,
            UnexpectedOrigin,
            UnexpectedPath,
            UnexpectedFragment,
            MissingResult,
        }
    }
}

object AuthCallbackPolicy {
    fun evaluate(rawUri: String?): AuthCallbackDecision {
        if (rawUri.isNullOrBlank()) return ignored(AuthCallbackDecision.Ignored.Reason.MissingUri)

        val link = when (val origin = trustedLinkOrigin(rawUri)) {
            is TrustedLink.Accepted -> origin
            TrustedLink.Malformed -> return ignored(AuthCallbackDecision.Ignored.Reason.MalformedUri)
            TrustedLink.UntrustedOrigin ->
                return ignored(AuthCallbackDecision.Ignored.Reason.UnexpectedOrigin)
        }

        if (link.path != SupabaseBackendConfig.AUTH_CALLBACK_PATH) {
            return ignored(AuthCallbackDecision.Ignored.Reason.UnexpectedPath)
        }
        if (rawUri.contains('#')) {
            return ignored(AuthCallbackDecision.Ignored.Reason.UnexpectedFragment)
        }

        val parameters = parseQueryParameters(link.rawQuery)
        val error = parameters["error"]?.firstOrNull()
        if (!error.isNullOrBlank()) {
            return AuthCallbackDecision.ProviderError(
                code = error.take(MAX_ERROR_LENGTH),
                description = parameters["error_description"]
                    ?.firstOrNull()
                    ?.take(MAX_DESCRIPTION_LENGTH),
            )
        }

        val code = parameters["code"]?.firstOrNull()
        return if (!code.isNullOrBlank()) {
            AuthCallbackDecision.AuthorizationCode(code)
        } else {
            ignored(AuthCallbackDecision.Ignored.Reason.MissingResult)
        }
    }

    private fun ignored(reason: AuthCallbackDecision.Ignored.Reason): AuthCallbackDecision =
        AuthCallbackDecision.Ignored(reason)

    private const val MAX_ERROR_LENGTH = 100
    private const val MAX_DESCRIPTION_LENGTH = 300
}
