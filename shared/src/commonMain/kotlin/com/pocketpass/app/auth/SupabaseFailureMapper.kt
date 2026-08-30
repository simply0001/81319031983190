package com.pocketpass.app.auth

import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.security.SecureStorageException
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.io.IOException

fun Throwable.toRepositoryFailure(): RepositoryFailure {
    if (this is AuthRestException) {
        val kind = when (errorCode) {
            AuthErrorCode.SessionNotFound,
            AuthErrorCode.SessionExpired,
            AuthErrorCode.RefreshTokenNotFound,
            AuthErrorCode.RefreshTokenAlreadyUsed,
            AuthErrorCode.InvalidCredentials,
            AuthErrorCode.OtpExpired,
            AuthErrorCode.BadCodeVerifier,
            AuthErrorCode.BadOauthState,
            AuthErrorCode.FlowStateNotFound,
            AuthErrorCode.FlowStateExpired,
            -> RepositoryFailureKind.Unauthorized

            AuthErrorCode.ProviderDisabled,
            AuthErrorCode.EmailProviderDisabled,
            AuthErrorCode.OauthProviderNotSupported,
            AuthErrorCode.OtpDisabled,
            AuthErrorCode.SignupDisabled,
            -> RepositoryFailureKind.Misconfigured

            AuthErrorCode.Conflict,
            AuthErrorCode.EmailExists,
            AuthErrorCode.UserAlreadyExists,
            -> RepositoryFailureKind.Conflict

            AuthErrorCode.RequestTimeout,
            AuthErrorCode.HookTimeout,
            AuthErrorCode.HookTimeoutAfterRetry,
            -> RepositoryFailureKind.Unavailable

            AuthErrorCode.OverRequestRateLimit,
            AuthErrorCode.OverEmailSendRateLimit,
            -> RepositoryFailureKind.RateLimited

            else -> statusCode.toFailureKind()
        }
        return RepositoryFailure(
            kind = kind,
            message = kind.safeMessage(),
            retryable = kind == RepositoryFailureKind.Offline ||
                kind == RepositoryFailureKind.RateLimited ||
                kind == RepositoryFailureKind.Unavailable,
        )
    }

    if (this is RestException) {
        val kind = statusCode.toFailureKind()
        return RepositoryFailure(
            kind = kind,
            message = kind.safeMessage(),
            retryable = kind == RepositoryFailureKind.RateLimited ||
                kind == RepositoryFailureKind.Unavailable,
        )
    }

    val kind = when (this) {
        is IOException -> RepositoryFailureKind.Offline
        is SecureStorageException -> RepositoryFailureKind.Unauthorized
        is AuthProviderCallbackException -> RepositoryFailureKind.Unauthorized
        is IllegalArgumentException -> RepositoryFailureKind.Validation
        else -> RepositoryFailureKind.Unknown
    }
    return RepositoryFailure(
        kind = kind,
        message = kind.safeMessage(),
        retryable = kind == RepositoryFailureKind.Offline,
    )
}

private fun Int.toFailureKind(): RepositoryFailureKind =
    when (this) {
        400, 422 -> RepositoryFailureKind.Validation
        401 -> RepositoryFailureKind.Unauthorized
        403 -> RepositoryFailureKind.Forbidden
        404 -> RepositoryFailureKind.NotFound
        409 -> RepositoryFailureKind.Conflict
        429 -> RepositoryFailureKind.RateLimited
        408, 425, in 500..599 -> RepositoryFailureKind.Unavailable
        else -> RepositoryFailureKind.Unknown
    }

private fun RepositoryFailureKind.safeMessage(): String =
    when (this) {
        RepositoryFailureKind.Offline -> "PocketPass is offline"
        RepositoryFailureKind.Unauthorized -> "Authentication is required"
        RepositoryFailureKind.Forbidden -> "This account cannot perform that action"
        RepositoryFailureKind.NotFound -> "The requested account resource was not found"
        RepositoryFailureKind.Conflict -> "The account operation conflicts with current data"
        RepositoryFailureKind.Validation -> "The authentication response was invalid"
        RepositoryFailureKind.RateLimited -> "Too many authentication requests"
        RepositoryFailureKind.Unavailable -> "The PocketPass service is temporarily unavailable"
        RepositoryFailureKind.Misconfigured -> "PocketPass authentication is not configured"
        RepositoryFailureKind.Unknown -> "An unexpected authentication error occurred"
    }
