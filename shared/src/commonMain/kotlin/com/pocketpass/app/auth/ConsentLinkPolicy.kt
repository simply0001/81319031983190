package com.pocketpass.app.auth

sealed interface ConsentLinkDecision {
    data class Consent(val authorizationId: String) : ConsentLinkDecision

    data object Ignored : ConsentLinkDecision
}

object ConsentLinkPolicy {
    const val CONSENT_PATH = "/oauth/consent"

    fun evaluate(rawUri: String?): ConsentLinkDecision {
        if (rawUri.isNullOrBlank()) return ConsentLinkDecision.Ignored

        val link = trustedLinkOrigin(rawUri) as? TrustedLink.Accepted
            ?: return ConsentLinkDecision.Ignored
        if (link.path != CONSENT_PATH) return ConsentLinkDecision.Ignored

        val authorizationId = parseQueryParameters(link.rawQuery)["authorization_id"]
            ?.firstOrNull()
            ?: return ConsentLinkDecision.Ignored
        if (!AUTHORIZATION_ID_PATTERN.matches(authorizationId)) return ConsentLinkDecision.Ignored
        return ConsentLinkDecision.Consent(authorizationId)
    }

    private val AUTHORIZATION_ID_PATTERN = Regex("^[A-Za-z0-9._~-]{8,128}$")
}
