package com.pocketpass.app.feature

import com.pocketpass.app.domain.model.ConnectedApp
import com.pocketpass.app.domain.model.OAuthConsentRequest
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.repository.ConnectedAppsSource
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OAuthConsentFeatureState(
    val authorizationId: String? = null,
    val loading: Boolean = false,
    val request: OAuthConsentRequest? = null,
    val error: String? = null,
    val deciding: Boolean = false,
) {
    val visible: Boolean
        get() = authorizationId != null
}

data class ConnectedAppsFeatureState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val apps: List<ConnectedApp> = emptyList(),
    val error: String? = null,
    val revokeClientId: String? = null,
    val revokeInProgress: Boolean = false,
    val revokeError: String? = null,
    val consent: OAuthConsentFeatureState = OAuthConsentFeatureState(),
)

class ConnectedAppsStateHolder(
    private val accountId: Flow<UserId?>,
    private val source: ConnectedAppsSource?,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(ConnectedAppsFeatureState())
    val state: StateFlow<ConnectedAppsFeatureState> = mutableState.asStateFlow()

    private val mutableRedirects = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val consentRedirects: SharedFlow<String> = mutableRedirects.asSharedFlow()

    val enabled: Boolean
        get() = source != null

    init {
        scope.launch {
            accountId.collect { id ->
                if (id == null) {
                    mutableState.update {
                        it.copy(
                            visible = false,
                            loading = false,
                            apps = emptyList(),
                            error = null,
                            revokeClientId = null,
                            revokeInProgress = false,
                            revokeError = null,
                        )
                    }
                }
            }
        }
    }

    fun open() {
        if (source == null) return
        mutableState.update { it.copy(visible = true, revokeClientId = null, revokeError = null) }
        refresh()
    }

    fun close(): Boolean {
        if (!mutableState.value.visible) return false
        mutableState.update {
            it.copy(visible = false, revokeClientId = null, revokeInProgress = false, revokeError = null)
        }
        return true
    }

    fun refresh() {
        val remote = source ?: return
        mutableState.update { it.copy(loading = true, error = null) }
        scope.launch {
            when (val result = remote.fetchConnectedApps()) {
                is RepositoryResult.Success -> mutableState.update {
                    it.copy(loading = false, apps = result.value, error = null)
                }

                is RepositoryResult.Failure -> mutableState.update {
                    it.copy(loading = false, error = result.error.message)
                }
            }
        }
    }

    fun openRevoke(clientId: String) {
        if (mutableState.value.apps.none { it.clientId == clientId }) return
        mutableState.update { it.copy(revokeClientId = clientId, revokeError = null) }
    }

    fun closeRevoke(): Boolean {
        val current = mutableState.value
        if (current.revokeClientId == null || current.revokeInProgress) return false
        mutableState.update { it.copy(revokeClientId = null, revokeError = null) }
        return true
    }

    fun confirmRevoke() {
        val remote = source ?: return
        val clientId = mutableState.value.revokeClientId ?: return
        if (mutableState.value.revokeInProgress) return
        mutableState.update { it.copy(revokeInProgress = true, revokeError = null) }
        scope.launch {
            when (remote.revokeConnectedApp(clientId)) {
                is RepositoryResult.Success -> {
                    mutableState.update {
                        it.copy(
                            revokeInProgress = false,
                            revokeClientId = null,
                            apps = it.apps.filterNot { app -> app.clientId == clientId },
                        )
                    }
                    refresh()
                }

                is RepositoryResult.Failure -> mutableState.update {
                    it.copy(
                        revokeInProgress = false,
                        revokeError = "The app could not be disconnected. Try again.",
                    )
                }
            }
        }
    }

    fun handleConsentLink(authorizationId: String) {
        val remote = source ?: return
        mutableState.update {
            it.copy(
                consent = OAuthConsentFeatureState(authorizationId = authorizationId, loading = true),
            )
        }
        scope.launch {
            accountId.filterNotNull().first()
            if (mutableState.value.consent.authorizationId != authorizationId) return@launch
            when (val result = remote.fetchOAuthConsent(authorizationId)) {
                is RepositoryResult.Success -> {
                    val request = result.value
                    val redirectUrl = request.redirectUrl
                    if (redirectUrl != null) {
                        returnToApp(redirectUrl)
                    } else {
                        mutableState.update {
                            it.copy(consent = it.consent.copy(loading = false, request = request))
                        }
                    }
                }

                is RepositoryResult.Failure -> mutableState.update {
                    it.copy(
                        consent = it.consent.copy(loading = false, error = result.error.consentMessage()),
                    )
                }
            }
        }
    }

    fun dismissConsent(): Boolean {
        val consent = mutableState.value.consent
        if (!consent.visible || consent.deciding) return false
        mutableState.update { it.copy(consent = OAuthConsentFeatureState()) }
        return true
    }

    fun decideConsent(approve: Boolean) {
        val remote = source ?: return
        val consent = mutableState.value.consent
        val authorizationId = consent.authorizationId ?: return
        if (consent.deciding || consent.loading) return
        if (approve && consent.request?.allowable != true) return
        mutableState.update { it.copy(consent = it.consent.copy(deciding = true, error = null)) }
        scope.launch {
            when (val result = remote.decideOAuthConsent(authorizationId, approve)) {
                is RepositoryResult.Success -> returnToApp(result.value)
                is RepositoryResult.Failure -> mutableState.update {
                    it.copy(
                        consent = it.consent.copy(deciding = false, error = result.error.consentMessage()),
                    )
                }
            }
        }
    }

    private fun returnToApp(redirectUrl: String) {
        val target = safeRedirect(redirectUrl)
        if (target == null) {
            mutableState.update {
                it.copy(
                    consent = it.consent.copy(
                        loading = false,
                        deciding = false,
                        error = "The app asked to send you to an address PocketPass does not allow. Nothing was shared.",
                    ),
                )
            }
            return
        }
        mutableState.update { it.copy(consent = OAuthConsentFeatureState()) }
        mutableRedirects.tryEmit(target)
    }

    private fun RepositoryFailure.consentMessage(): String = when (kind) {
        RepositoryFailureKind.NotFound,
        RepositoryFailureKind.Validation,
        RepositoryFailureKind.Conflict,
        -> "This request has expired or was already used. Go back to the app and try again."

        RepositoryFailureKind.Offline -> "PocketPass is offline. Connect to the internet and open the link again."
        RepositoryFailureKind.Unauthorized -> "Sign in again, then open the link from the app once more."
        else -> "This request cannot be completed right now. Go back to the app and try again."
    }

    companion object {
        private val CUSTOM_SCHEME = Regex("^[a-z][a-z0-9+.-]*$")
        private val BLOCKED_SCHEMES = setOf("javascript", "data", "blob", "vbscript", "file", "about", "http", "intent", "content", "android-app")
        private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "[::1]", "::1")

        fun safeRedirect(value: String): String? {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.any { it.isWhitespace() || it.code < 0x20 || it.code == 0x7F }) return null
            val separator = trimmed.indexOf(':')
            if (separator <= 0) return null
            val scheme = trimmed.substring(0, separator).lowercase()
            if (!CUSTOM_SCHEME.matches(scheme)) return null
            if (scheme == "http") {
                val url = runCatching { Url(trimmed) }.getOrNull() ?: return null
                return if (url.host.lowercase() in LOOPBACK_HOSTS) trimmed else null
            }
            if (scheme in BLOCKED_SCHEMES) return null
            return trimmed
        }
    }
}
