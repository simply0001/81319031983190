package com.pocketpass.app.data.supabase.dto

import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthenticatedUserDto(
    @SerialName("user_id")
    val userId: String,
    val email: String?,
    @SerialName("expires_at_epoch_seconds")
    val expiresAtEpochSeconds: Long,
)

fun UserSession.toAuthenticatedUserDto(): AuthenticatedUserDto {
    val info = requireNotNull(user) { "Authenticated Supabase session did not include a user" }
    return AuthenticatedUserDto(
        userId = info.id,
        email = info.email,
        expiresAtEpochSeconds = expiresAt.epochSeconds,
    )
}
