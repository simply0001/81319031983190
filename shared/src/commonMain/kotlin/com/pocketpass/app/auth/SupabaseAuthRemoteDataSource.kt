package com.pocketpass.app.auth

import com.pocketpass.app.data.supabase.SupabaseBackendConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.event.AuthEvent
import io.github.jan.supabase.auth.providers.Discord
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge

sealed interface RemoteAuthStatus {
    data object Initializing : RemoteAuthStatus
    data object SignedOut : RemoteAuthStatus
    data class Authenticated(val userId: String) : RemoteAuthStatus

    data class RefreshFailed(
        val error: Throwable,
        val isNetworkFailure: Boolean,
        val cachedUserId: String? = null,
    ) : RemoteAuthStatus
}

interface AuthRemoteDataSource {
    val status: Flow<RemoteAuthStatus>

    suspend fun initialize(): RemoteAuthStatus

    suspend fun handleAuthCallback(callbackUri: String): RemoteAuthStatus.Authenticated

    suspend fun signInWithDiscord()

    suspend fun requestEmailOtp(
        email: String,
        createUser: Boolean,
    )

    suspend fun verifyEmailOtp(email: String, code: String): RemoteAuthStatus.Authenticated

    suspend fun signOut()
}

class SupabaseAuthRemoteDataSource(
    private val client: SupabaseClient,
    private val config: SupabaseBackendConfig,
) : AuthRemoteDataSource {
    override val status: Flow<RemoteAuthStatus> =
        client.auth.sessionStatus.map { toRemoteStatus(it) }

    fun currentSessionOrNull(): UserSession? =
        client.auth.currentSessionOrNull()

    @OptIn(SupabaseExperimental::class)
    override suspend fun initialize(): RemoteAuthStatus {
        val current = client.auth.sessionStatus.value
        if (current !is SessionStatus.Initializing) return toRemoteStatus(current)
        return merge(
            client.auth.sessionStatus
                .filter { it !is SessionStatus.Initializing }
                .map { toRemoteStatus(it) },
            client.auth.events
                .filterIsInstance<AuthEvent.RefreshFailure>()
                .mapNotNull { event -> storedUserId()?.let { refreshFailed(event.cause, it) } },
        ).first()
    }

    override suspend fun handleAuthCallback(
        callbackUri: String,
    ): RemoteAuthStatus.Authenticated {
        return when (val decision = AuthCallbackPolicy.evaluate(callbackUri)) {
            is AuthCallbackDecision.AuthorizationCode -> {
                val session = client.auth.exchangeCodeForSession(decision.code)
                RemoteAuthStatus.Authenticated(session.requireUserId())
            }

            is AuthCallbackDecision.ProviderError -> throw AuthProviderCallbackException(
                providerError = decision.code,
                providerDescription = decision.description,
            )

            is AuthCallbackDecision.Ignored -> throw IllegalArgumentException(
                "URI is not a valid PocketPass authentication callback: ${decision.reason}",
            )
        }
    }

    override suspend fun signInWithDiscord() {
        client.auth.signInWith(
            provider = Discord,
            redirectUrl = config.authCallbackUrl,
        )
    }

    override suspend fun requestEmailOtp(
        email: String,
        createUser: Boolean,
    ) {
        val normalizedEmail = normalizeEmail(email)
        client.auth.signInWith(
            provider = OTP,
            redirectUrl = config.authCallbackUrl,
        ) {
            this.email = normalizedEmail
            this.createUser = createUser
        }
    }

    override suspend fun verifyEmailOtp(
        email: String,
        code: String,
    ): RemoteAuthStatus.Authenticated {
        val normalizedEmail = normalizeEmail(email)
        val normalizedCode = code.trim()
        require(OTP_PATTERN.matches(normalizedCode)) { "OTP must contain exactly six digits" }
        client.auth.verifyEmailOtp(
            type = OtpType.Email.EMAIL,
            email = normalizedEmail,
            token = normalizedCode,
        )
        val session = client.auth.currentSessionOrNull()
            ?: error("OTP verification completed without an authenticated session")
        return RemoteAuthStatus.Authenticated(session.requireUserId())
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }

    private suspend fun toRemoteStatus(status: SessionStatus): RemoteAuthStatus =
        when (status) {
            SessionStatus.Initializing -> RemoteAuthStatus.Initializing
            is SessionStatus.NotAuthenticated -> RemoteAuthStatus.SignedOut
            is SessionStatus.Authenticated -> RemoteAuthStatus.Authenticated(
                status.session.requireUserId(),
            )

            is SessionStatus.RefreshFailure -> refreshFailed(status.cause, storedUserId())
        }

    @Suppress("DEPRECATION")
    private fun refreshFailed(
        cause: RefreshFailureCause,
        cachedUserId: String?,
    ): RemoteAuthStatus.RefreshFailed =
        when (cause) {
            is RefreshFailureCause.NetworkError -> RemoteAuthStatus.RefreshFailed(
                error = cause.exception,
                isNetworkFailure = true,
                cachedUserId = cachedUserId,
            )

            is RefreshFailureCause.InternalServerError -> RemoteAuthStatus.RefreshFailed(
                error = cause.exception,
                isNetworkFailure = false,
                cachedUserId = cachedUserId,
            )
        }

    private suspend fun storedUserId(): String? =
        client.auth.sessionManager.loadSessionOrNull()?.user?.id

    private fun UserSession.requireUserId(): String =
        requireNotNull(user?.id) { "Authenticated Supabase session did not contain a user id" }

    private fun normalizeEmail(email: String): String {
        val normalized = email.trim().lowercase()
        require(normalized.length in 3..MAX_EMAIL_LENGTH) { "Email address has an invalid length" }
        require(EMAIL_PATTERN.matches(normalized)) { "Email address is invalid" }
        return normalized
    }

    companion object {
        private const val MAX_EMAIL_LENGTH = 254
        private val OTP_PATTERN = Regex("""\d{6}""")
        private val EMAIL_PATTERN = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
    }
}
