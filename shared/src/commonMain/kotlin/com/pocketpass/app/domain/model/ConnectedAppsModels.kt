package com.pocketpass.app.domain.model

import kotlin.time.Instant

data class ConnectedApp(
    val clientId: String,
    val name: String,
    val website: String,
    val scopes: List<String>,
    val grantedAt: Instant,
)

data class OAuthConsentScope(
    val key: String,
    val description: String,
)

data class OAuthConsentRequest(
    val authorizationId: String,
    val appName: String,
    val website: String?,
    val ownerDisplayName: String?,
    val scopes: List<OAuthConsentScope>,
    val extraClaims: List<String>,
    val unknownScopes: List<String>,
    val returnHost: String,
    val suspended: Boolean,
    val infoError: String?,
    val redirectUrl: String?,
) {
    val allowable: Boolean
        get() = redirectUrl == null && !suspended && infoError == null && unknownScopes.isEmpty()
}
