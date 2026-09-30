package com.pocketpass.app.domain.model

import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind

data class AccountBanNotice(
    val reason: String,
    val endsAtEpochMillis: Long?,
)

const val ACCOUNT_BANNED_HINT = "ACCOUNT_BANNED"
const val ACCOUNT_BANNED_MESSAGE = "This account is banned."

fun accountBannedFailure(): RepositoryFailure = RepositoryFailure(
    kind = RepositoryFailureKind.Forbidden,
    message = ACCOUNT_BANNED_MESSAGE,
    retryable = false,
)

fun RepositoryFailure.isAccountBanned(): Boolean =
    kind == RepositoryFailureKind.Forbidden && message == ACCOUNT_BANNED_MESSAGE
