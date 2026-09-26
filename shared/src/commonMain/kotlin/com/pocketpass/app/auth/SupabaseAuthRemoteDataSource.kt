package com.pocketpass.app.auth

import com.pocketpass.app.data.supabase.SupabaseBackendConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.event.AuthEvent
import io.github.jan.supabase.auth.providers.Discord
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

    fun currentAccountEmail(): String? = null

    suspend fun isUsernameAvailable(username: String): Boolean = passwordAccountsUnsupported()

    suspend fun signUpWithPassword(
        loginEmail: String,
        password: String,
        username: String,
    ): RemoteAuthStatus.Authenticated = passwordAccountsUnsupported()

    suspend fun signInWithPassword(
        loginEmail: String,
        password: String,
    ): RemoteAuthStatus.Authenticated = passwordAccountsUnsupported()

    suspend fun requestEmailLink(email: String): Unit = passwordAccountsUnsupported()

    suspend fun verifyEmailLink(email: String, code: String): Unit = passwordAccountsUnsupported()

    suspend fun requestReauthentication(): Unit = passwordAccountsUnsupported()

    suspend fun changePassword(newPassword: String, nonce: String): Unit = passwordAccountsUnsupported()
}

private fun passwordAccountsUnsupported(): Nothing =
    throw UnsupportedOperationException("Password accounts are not supported by this session backend")

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
        val normalizedCode = normalizeCode(code)
        client.auth.verifyEmailOtp(
            type = OtpType.Email.EMAIL,
            email = normalizedEmail,
            token = normalizedCode,
        )
        return authenticatedSession("OTP verification")
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }

    override fun currentAccountEmail(): String? =
        client.auth.currentUserOrNull()?.email?.takeIf { it.isNotBlank() }

    override suspend fun isUsernameAvailable(username: String): Boolean =
        client.postgrest.rpc(
            function = "username_available",
            parameters = buildJsonObject { put("p_username", username) },
        ).decodeAs<Boolean>()

    override suspend fun signUpWithPassword(
        loginEmail: String,
        password: String,
        username: String,
    ): RemoteAuthStatus.Authenticated {
        val normalizedEmail = normalizeEmail(loginEmail)
        require(isPocketPassPasswordValid(password)) { "Password length is outside the allowed range" }
        client.auth.signUpWith(provider = Email) {
            this.email = normalizedEmail
            this.password = password
            data = buildJsonObject { put("username", username) }
        }
        return authenticatedSession("Sign-up")
    }

    override suspend fun signInWithPassword(
        loginEmail: String,
        password: String,
    ): RemoteAuthStatus.Authenticated {
        val normalizedEmail = normalizeEmail(loginEmail)
        require(password.isNotEmpty()) { "Password must not be empty" }
        client.auth.signInWith(provider = Email) {
            this.email = normalizedEmail
            this.password = password
        }
        return authenticatedSession("Password sign-in")
    }

    override suspend fun requestEmailLink(email: String) {
        val normalizedEmail = normalizeEmail(email)
        client.auth.updateUser {
            this.email = normalizedEmail
        }
    }

    override suspend fun verifyEmailLink(email: String, code: String) {
        val normalizedEmail = normalizeEmail(email)
        val normalizedCode = normalizeCode(code)
        client.auth.verifyEmailOtp(
            type = OtpType.Email.EMAIL_CHANGE,
            email = normalizedEmail,
            token = normalizedCode,
        )
        client.auth.retrieveUserForCurrentSession(updateSession = true)
    }

    override suspend fun requestReauthentication() {
        client.auth.reauthenticate()
    }

    override suspend fun changePassword(newPassword: String, nonce: String) {
        require(isPocketPassPasswordValid(newPassword)) { "Password length is outside the allowed range" }
        val normalizedNonce = normalizeCode(nonce)
        client.auth.updateUser {
            password = newPassword
            this.nonce = normalizedNonce
        }
    }

    private fun authenticatedSession(operation: String): RemoteAuthStatus.Authenticated {
        val session = client.auth.currentSessionOrNull()
            ?: error("$operation completed without an authenticated session")
        return RemoteAuthStatus.Authenticated(session.requireUserId())
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

    private fun normalizeCode(code: String): String {
        val normalized = code.trim()
        require(OTP_PATTERN.matches(normalized)) { "OTP must contain exactly six digits" }
        return normalized
    }

    companion object {
        private const val MAX_EMAIL_LENGTH = 254
        private val OTP_PATTERN = Regex("""\d{6}""")
        private val EMAIL_PATTERN = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
    }
}
