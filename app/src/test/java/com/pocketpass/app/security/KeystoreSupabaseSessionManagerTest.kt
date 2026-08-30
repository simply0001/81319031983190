package com.pocketpass.app.security

import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeystoreSupabaseSessionManagerTest {
    @Test
    fun sessionRoundTripsThroughSecureStoreBoundary() = runTest {
        val store = FakeSecureStringStore()
        val manager = KeystoreSupabaseSessionManager(store)
        val session = session()

        manager.saveSession(session)

        assertEquals(session, manager.loadSession())
    }

    @Test
    fun malformedSessionIsRemoved() = runTest {
        val store = FakeSecureStringStore()
        store.value = "not-json"
        val manager = KeystoreSupabaseSessionManager(store)

        val error = runCatching { manager.loadSession() }.exceptionOrNull()

        assertTrue(error is SecureStorageException)
        assertNull(store.value)
    }

    @Test
    fun pkceVerifierUsesSameSecureBoundaryAndCanBeDeleted() = runTest {
        val store = FakeSecureStringStore()
        val cache = KeystoreSupabaseCodeVerifierCache(store)

        cache.saveCodeVerifier("pkce-verifier")
        assertEquals("pkce-verifier", cache.loadCodeVerifier())

        cache.deleteCodeVerifier()
        assertNull(cache.loadCodeVerifier())
    }

    private fun session(): UserSession =
        UserSession(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresIn = 3_600,
            tokenType = "bearer",
        )

    private class FakeSecureStringStore : SecureStringStore {
        var value: String? = null

        override suspend fun put(key: String, value: String) {
            this.value = value
        }

        override suspend fun get(key: String): String? = value

        override suspend fun remove(key: String) {
            value = null
        }
    }
}
