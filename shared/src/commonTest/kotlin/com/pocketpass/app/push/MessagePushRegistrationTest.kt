@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.pocketpass.app.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class MessagePushRegistrationTest {
    private val account = "10000000-0000-4000-8000-000000000001"
    private val binding = "10000000-0000-4000-8000-000000000002"
    private val conversation = "10000000-0000-4000-8000-000000000003"
    private val payload get() = mapOf(
        "version" to "1", "type" to "message", "recipient_id" to account,
        "binding_id" to binding, "conversation_id" to conversation,
        "notification_id" to "10000000-0000-4000-8000-000000000004",
        "event_count" to "1", "title" to "PocketPass", "body" to "You have a new message.",
    )

    private inner class Store : MessagePushBindingStore {
        override val installation = "installation"
        override var account: String? = this@MessagePushRegistrationTest.account
        override var binding: String? = this@MessagePushRegistrationTest.binding
    }
    private inner class Api : MessagePushRegistrationApi {
        val calls = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        var failRemoval = false
        override suspend fun register(installation: String, token: String): String {
            calls += "register:$token"
            gate?.await()
            return binding
        }
        override suspend fun unregister(installation: String) {
            calls += "unregister"
            if (failRemoval) error("offline")
        }
    }

    @Test fun coldTapWaitsForAuthenticatedAccountAndUsesSavedBinding() {
        val registration = MessagePushRegistration(Api(), Store())
        assertNull(registration.conversationFor(payload))
        registration.updateAccount(account, true)
        assertEquals(conversation, registration.conversationFor(payload))
        assertNull(registration.conversationFor(payload + ("recipient_id" to conversation)))
        assertNull(registration.conversationFor(payload + ("binding_id" to conversation)))
        assertNull(registration.conversationFor(payload + ("conversation_id" to "invalid")))
        assertNull(registration.conversationFor(payload + ("event_count" to "0")))
    }

    @Test fun disablingAlertsInvalidatesTapsAndRemovesRegistration() = runTest {
        val api = Api()
        val registration = MessagePushRegistration(api, Store())
        registration.updateAccount(account, false)
        assertNull(registration.conversationFor(payload))
        registration.synchronize("token", true)
        assertEquals(listOf("unregister"), api.calls)
    }

    @Test fun deniedPermissionRemovesRegistrationWithoutRequestingAToken() = runTest {
        val api = Api()
        val store = Store()
        val registration = MessagePushRegistration(api, store)
        registration.updateAccount(account, true)
        registration.synchronize("", false)
        assertNull(store.binding)
        assertEquals(listOf("unregister"), api.calls)
    }

    @Test fun responseFromPreviousAccountCannotRestoreItsBinding() = runTest {
        val api = Api().apply { gate = CompletableDeferred() }
        val store = Store()
        val registration = MessagePushRegistration(api, store)
        registration.updateAccount(account, true)
        val request = async { registration.synchronize("old-token", true) }
        runCurrent()
        registration.updateAccount(conversation, true)
        api.gate!!.complete(Unit)
        request.await()
        assertNull(store.binding)
        assertNull(registration.conversationFor(payload))
    }

    @Test fun logoutInvalidatesImmediatelyAndUnregistersAfterAnInFlightRegistration() = runTest {
        val api = Api().apply { gate = CompletableDeferred() }
        val store = Store()
        val registration = MessagePushRegistration(api, store)
        registration.updateAccount(account, true)
        val request = async { registration.synchronize("token", true) }
        runCurrent()
        val logout = async { registration.signOut() }
        runCurrent()
        assertNull(registration.conversationFor(payload))
        assertNull(store.account)
        api.gate!!.complete(Unit)
        request.await()
        logout.await()
        assertEquals(listOf("register:token", "unregister"), api.calls)
        assertNull(store.binding)
    }

    @Test fun offlineLogoutStillClearsLocalBinding() = runTest {
        val store = Store()
        val registration = MessagePushRegistration(Api().apply { failRemoval = true }, store)
        registration.updateAccount(account, true)
        assertFailsWith<IllegalStateException> { registration.signOut() }
        assertNull(store.binding)
        assertNull(store.account)
        assertNull(registration.conversationFor(payload))
    }

    @Test fun emptyTokenDoesNotReplaceAValidSavedBinding() = runTest {
        val api = Api()
        val store = Store()
        val registration = MessagePushRegistration(api, store)
        registration.updateAccount(account, true)
        registration.synchronize("", true)
        assertEquals(emptyList(), api.calls)
        assertEquals(binding, store.binding)
    }
}
