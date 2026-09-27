package com.pocketpass.app.push

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface MessagePushRegistrationApi {
    suspend fun register(installation: String, token: String): String
    suspend fun unregister(installation: String)
}

interface MessagePushBindingStore {
    val installation: String
    var account: String?
    var binding: String?
}

class MessagePushRegistration(
    private val api: MessagePushRegistrationApi,
    private val store: MessagePushBindingStore,
) {
    private val mutex = Mutex()
    private var generation = 0L
    private var account: String? = null
    private var enabled = false

    fun updateAccount(current: String?, alertsEnabled: Boolean) {
        if (account != current || enabled != alertsEnabled) generation++
        account = current
        enabled = current != null && alertsEnabled
        if (store.account != current || !enabled) store.binding = null
        store.account = current
    }

    suspend fun synchronize(token: String, permissionGranted: Boolean) {
        val expectedGeneration = generation
        mutex.withLock {
            if (expectedGeneration != generation || account == null) return
            if (!enabled || !permissionGranted) {
                store.binding = null
                api.unregister(store.installation)
                return
            }
            if (token.isBlank()) return
            val binding = api.register(store.installation, token)
            if (expectedGeneration == generation) store.binding = binding
        }
    }

    suspend fun signOut() {
        updateAccount(null, false)
        mutex.withLock { api.unregister(store.installation) }
    }

    fun conversationFor(data: Map<String, String>): String? {
        val payload = MessagePushPayload.parse(data) ?: return null
        return payload.conversationId.takeIf {
            enabled && payload.recipientId == account && payload.bindingId == store.binding
        }
    }
}
