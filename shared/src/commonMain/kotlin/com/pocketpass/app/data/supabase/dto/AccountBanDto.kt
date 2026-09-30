package com.pocketpass.app.data.supabase.dto

import com.pocketpass.app.data.supabase.parseSupabaseInstant
import com.pocketpass.app.domain.model.AccountBanNotice
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json

@Serializable
data class AccountBanDto(
    val reason: String = "",
    @SerialName("ends_at")
    val endsAt: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
)

fun AccountBanDto.toDomain(): AccountBanNotice = AccountBanNotice(
    reason = reason.trim(),
    endsAtEpochMillis = endsAt?.let { parseSupabaseInstant(it).toEpochMilliseconds() },
)

fun decodeAccountBan(body: String): AccountBanNotice? {
    if (body.isBlank()) return null
    return AccountBanJson.decodeFromString(AccountBanDto.serializer().nullable, body)?.toDomain()
}

private val AccountBanJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
