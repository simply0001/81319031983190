package com.pocketpass.app.push

import com.pocketpass.app.data.SettingsRepository
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.domain.state.accountIdOrNull
import com.pocketpass.app.sync.RealtimeNetworkState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID

/** Swift owns Firebase and permission UI; Kotlin owns the authenticated device binding. */
object IosMessagePushBridge {
    var handler: ((String) -> Unit)? = null
    internal val state = MutableStateFlow(IosPushToken())
    fun update(token: String, allowed: Boolean) {
        state.value = IosPushToken(token, allowed)
    }
}

internal data class IosPushToken(val token: String = "", val allowed: Boolean? = null)

class IosMessagePushManager(
    client: SupabaseClient,
    private val settings: SettingsRepository,
    private val session: Flow<SessionState>,
    private val foreground: StateFlow<Boolean>,
    private val network: StateFlow<RealtimeNetworkState>,
    private val scope: CoroutineScope,
    private val openConversation: (String) -> Unit,
) {
    private val registration = MessagePushRegistration(object : MessagePushRegistrationApi {
        override suspend fun register(installation: String, token: String): String =
            client.postgrest.rpc("register_ios_message_push_device", buildJsonObject {
                put("p_installation_id", installation)
                put("p_token", token)
            }).decodeAs<String>()

        override suspend fun unregister(installation: String) {
            client.postgrest.rpc("unregister_message_push_device", buildJsonObject {
                put("p_installation_id", installation)
            })
        }
    }, UserDefaultsPushBindingStore())
    private val signingOut = MutableStateFlow(false)
    private var authReady = false
    private var pendingTap: Map<String, String>? = null

    fun start() {
        scope.launch {
            val accountAndPreference = combine(
                session.filter { it !is SessionState.Initializing }
                    .map { it.accountIdOrNull()?.value }.distinctUntilChanged(),
                settings.settings.map { it.messageAlertsEnabled }.distinctUntilChanged(),
                signingOut,
            ) { account, enabled, leaving -> (account.takeUnless { leaving }) to enabled }
            combine(accountAndPreference, IosMessagePushBridge.state, foreground, network) {
                account, token, visible, connection ->
                PushState(account.first, account.second, token, visible, connection.available, connection.generation)
            }.distinctUntilChanged().collectLatest { state ->
                authReady = true
                registration.updateAccount(state.account, state.enabled)
                consumeTap()
                val command = when {
                    state.account == null -> "reset"
                    !state.enabled -> "disable"
                    else -> "enable"
                }
                IosMessagePushBridge.handler?.invoke(command)
                if (state.visible || !state.enabled) IosMessagePushBridge.handler?.invoke("clear")
                if (state.account == null || !state.connected) return@collectLatest
                // A cold notification tap can use the saved binding before permission/token refresh.
                val allowed = state.token.allowed ?: return@collectLatest
                while (true) {
                    val success = try {
                        withTimeout(15_000) { registration.synchronize(state.token.token, allowed) }
                        true
                    } catch (cancelled: CancellationException) {
                        // A timeout is retryable; cancellation from an account/token change is not.
                        if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                        false
                    } catch (_: Exception) {
                        false
                    }
                    if (!state.visible) break
                    delay(if (success) 12 * 60 * 60 * 1000L else 30_000L)
                }
            }
        }
    }

    fun tapped(data: Map<String, String>) {
        pendingTap = data
        if (authReady) consumeTap()
    }

    private fun consumeTap() {
        val data = pendingTap ?: return
        pendingTap = null
        registration.conversationFor(data)?.let(openConversation)
    }

    suspend fun beforeSignOut() {
        signingOut.value = true
        pendingTap = null
        registration.updateAccount(null, false)
        IosMessagePushBridge.handler?.invoke("reset")
        try {
            withTimeoutOrNull(5_000) { registration.signOut() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Local binding is already gone. Session revocation also cascades server registrations.
        }
    }

    fun afterSignOut() { signingOut.value = false }

    private data class PushState(
        val account: String?, val enabled: Boolean, val token: IosPushToken,
        val visible: Boolean, val connected: Boolean, val networkGeneration: Long,
    )
}

private class UserDefaultsPushBindingStore : MessagePushBindingStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override val installation: String = defaults.stringForKey("pocketpass.push.installation")
        ?: NSUUID().UUIDString.also { defaults.setObject(it, forKey = "pocketpass.push.installation") }
    override var account: String?
        get() = defaults.stringForKey("pocketpass.push.account")
        set(value) { defaults.setObject(value, forKey = "pocketpass.push.account") }
    override var binding: String?
        get() = defaults.stringForKey("pocketpass.push.binding")
        set(value) { defaults.setObject(value, forKey = "pocketpass.push.binding") }
}
