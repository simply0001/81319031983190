package com.pocketpass.app.data.supabase.dto

import com.pocketpass.app.data.supabase.parseSupabaseInstant
import com.pocketpass.app.domain.model.ConnectedApp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConnectedAppsDto(
    val items: List<ConnectedAppDto> = emptyList(),
)

@Serializable
data class ConnectedAppDto(
    @SerialName("client_id")
    val clientId: String,
    val name: String = "Unknown app",
    val website: String = "",
    @SerialName("logo_url")
    val logoUrl: String = "",
    val scopes: List<String> = emptyList(),
    @SerialName("granted_at")
    val grantedAt: String,
) {
    fun toDomain(): ConnectedApp = ConnectedApp(
        clientId = clientId,
        name = name,
        website = website,
        scopes = scopes,
        grantedAt = parseSupabaseInstant(grantedAt),
    )
}

@Serializable
data class RevokeAppRpc(
    @SerialName("p_client_id")
    val clientId: String,
)

@Serializable
data class RevokeAppResultDto(
    val revoked: Boolean = false,
)

@Serializable
data class AppInfoRpc(
    @SerialName("p_client_id")
    val clientId: String,
)

@Serializable
data class AppInfoScopeDto(
    val key: String,
    val description: String = "",
)

@Serializable
data class AppInfoDto(
    @SerialName("client_id")
    val clientId: String? = null,
    val name: String? = null,
    val description: String? = null,
    val website: String? = null,
    @SerialName("logo_url")
    val logoUrl: String? = null,
    @SerialName("owner_display_name")
    val ownerDisplayName: String? = null,
    val scopes: List<AppInfoScopeDto> = emptyList(),
    val status: String? = null,
    @SerialName("oidc_scope_descriptions")
    val oidcScopeDescriptions: Map<String, String> = emptyMap(),
)

@Serializable
data class OAuthClientDto(
    val id: String? = null,
    val name: String? = null,
    val uri: String? = null,
    @SerialName("logo_uri")
    val logoUri: String? = null,
)

@Serializable
data class OAuthAuthorizationDetailsDto(
    val client: OAuthClientDto? = null,
    val scope: String? = null,
    @SerialName("redirect_uri")
    val redirectUri: String? = null,
    @SerialName("redirect_url")
    val redirectUrl: String? = null,
)

@Serializable
data class OAuthConsentDecisionDto(
    val action: String,
)

@Serializable
data class OAuthConsentResultDto(
    @SerialName("redirect_url")
    val redirectUrl: String? = null,
)
