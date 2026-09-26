package com.pocketpass.app.push

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.pocketpass.app.BuildConfig
import com.pocketpass.app.data.SettingsRepository
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.domain.state.accountIdOrNull
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MessagePushManager(
    private val context: Context,
    private val client: SupabaseClient?,
    private val settings: SettingsRepository,
    private val session: Flow<SessionState>,
    private val foreground: StateFlow<Boolean>,
    private val scope: CoroutineScope,
    private val requestPermission: () -> Unit,
) {
    private val preferences = context.getSharedPreferences("pocketpass_message_push", Context.MODE_PRIVATE)
    private val lock = Any()
    private var signingOut = false
    val supported = BuildConfig.FIREBASE_CONFIGURED && client != null
    private val messaging: FirebaseMessaging?
        get() = if (supported && FirebaseApp.getApps(context).isNotEmpty()) FirebaseMessaging.getInstance() else null

    fun start() {
        MessageNotifications.createChannel(context)
        BoardNotifications.createChannel(context)
        if (!supported) return
        scope.launch {
            combine(
                session.filter { it !is SessionState.Initializing }.map { it.accountIdOrNull()?.value }.distinctUntilChanged(),
                settings.settings.map { it.messageAlertsEnabled }.distinctUntilChanged(),
                foreground,
            ) { account, enabled, visible -> Triple(account, enabled, visible) }
                .distinctUntilChanged()
                .collect { (account, enabled, visible) ->
                    synchronized(lock) {
                        if (account == null) signingOut = false
                        val allowedAccount = account.takeUnless { signingOut }
                        if (preferences.getString("account", null) != allowedAccount) {
                            preferences.edit().remove("binding").putString("account", allowedAccount).commit()
                            MessageNotifications.cancelAll(context)
                            BoardNotifications.cancelAll(context)
                        }
                        preferences.edit().putBoolean("enabled", enabled && allowedAccount != null).commit()
                    }
                    if (visible) {
                        MessageNotifications.cancelAll(context)
                        BoardNotifications.cancelAll(context)
                        if (account != null && !MessageNotifications.permissionGranted(context) && !preferences.getBoolean("prompted", false)) {
                            preferences.edit().putBoolean("prompted", true).apply()
                            requestPermission()
                        }
                    }
                    MessagePushRegistrationWorker.enqueue(context)
                    MessagePushRegistrationWorker.schedule(context, account != null)
                }
        }
    }

    fun receive(data: Map<String, String>) {
        if (!supported) return
        com.pocketpass.app.boards.BoardPushPayload.parse(data)?.let { board ->
            synchronized(lock) {
                if (signingOut) return
                val key = "board_seen:${board.notificationId}"
                val account = preferences.getString("account", null)
                val binding = preferences.getString("binding", null)
                if (board.recipientId != account || board.bindingId != binding) return
                if (board.shouldShow(account, binding, foreground.value, preferences.getLong(key, 0))) BoardNotifications.post(context, board)
                preferences.edit().putLong(key, maxOf(preferences.getLong(key, 0), board.eventCount)).commit()
            }
            return
        }
        val payload = MessagePushPayload.parse(data) ?: return
        synchronized(lock) {
            val account = preferences.getString("account", null)
            val binding = preferences.getString("binding", null)
            if (account != payload.recipientId || binding != payload.bindingId || signingOut) return
            val key = "seen:${payload.notificationId}"
            val seen = preferences.getLong(key, 0)
            if (payload.shouldShow(account, binding, preferences.getBoolean("enabled", false), foreground.value, seen)) {
                MessageNotifications.post(context, payload)
            }
            if (payload.eventCount > seen) preferences.edit().putLong(key, payload.eventCount).commit()
        }
    }

    fun preferenceChanged(enabled: Boolean) {
        synchronized(lock) {
            preferences.edit().putBoolean("enabled", enabled && preferences.getString("account", null) != null).commit()
            if (!enabled) MessageNotifications.cancelAll(context)
        }
        MessagePushRegistrationWorker.enqueue(context)
    }

    suspend fun synchronizeRegistration(): Boolean {
        val firebase = messaging ?: return true
        val current = withTimeout(15_000) { session.first { it !is SessionState.Initializing } }
        val account = current.accountIdOrNull()?.value
        if (account == null) {
            firebase.isAutoInitEnabled = false
            return true
        }
        if (synchronized(lock) { signingOut }) return true
        val enabled = settings.settings.first().messageAlertsEnabled && MessageNotifications.allowed(context)
        val boardsEnabled = BoardNotifications.allowed(context)
        firebase.isAutoInitEnabled = enabled || boardsEnabled
        if (!enabled && !boardsEnabled && preferences.getString("binding", null) == null) return true
        val token = withTimeout(30_000) { firebase.token.awaitPush() }
        val installation = synchronized(lock) {
            preferences.getString("installation", null) ?: UUID.randomUUID().toString().also {
                preferences.edit().putString("installation", it).commit()
            }
        }
        val binding = requireNotNull(client).postgrest.rpc("register_board_push_device", buildJsonObject {
            put("p_installation_id", installation)
            put("p_token", token)
            put("p_message_enabled", enabled)
            put("p_board_enabled", boardsEnabled)
        }).decodeAs<String>()
        synchronized(lock) {
            if (!signingOut && preferences.getString("account", null) == account) {
                preferences.edit().putString("binding", binding).commit()
            }
        }
        return true
    }

    suspend fun signOut() {
        val installation = preferences.getString("installation", null)
        synchronized(lock) {
            signingOut = true
            preferences.edit().clear().commit()
            MessageNotifications.cancelAll(context)
            BoardNotifications.cancelAll(context)
        }
        val firebase = messaging ?: return
        firebase.isAutoInitEnabled = false
        try {
            if (installation != null) {
                withTimeoutOrNull(3_000) {
                    client?.postgrest?.rpc("unregister_message_push_device", buildJsonObject { put("p_installation_id", installation) })
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w("PocketPassPush", "Push registration could not be removed; local delivery is disabled")
        }
        try {
            withTimeoutOrNull(4_000) { firebase.deleteToken().awaitPush() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w("PocketPassPush", "Firebase token cleanup unavailable; local delivery is disabled")
        }
    }
}

private suspend fun <T> Task<T>.awaitPush(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
