package com.pocketpass.app.security

import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KeystoreSupabaseSessionManager(
    private val secureStore: SecureStringStore,
    private val json: Json = SESSION_JSON,
) : SessionManager {
    override suspend fun saveSession(session: UserSession) {
        secureStore.put(SESSION_ENTRY, json.encodeToString(session))
    }

    override suspend fun loadSession(): UserSession {
        val encoded = secureStore.get(SESSION_ENTRY) ?: error("No session stored")
        return try {
            json.decodeFromString(encoded)
        } catch (error: SerializationException) {
            secureStore.remove(SESSION_ENTRY)
            throw SecureStorageException("Stored Supabase session was invalid and has been cleared", error)
        } catch (error: IllegalArgumentException) {
            secureStore.remove(SESSION_ENTRY)
            throw SecureStorageException("Stored Supabase session was invalid and has been cleared", error)
        }
    }

    override suspend fun deleteSession() {
        secureStore.remove(SESSION_ENTRY)
    }

    companion object {
        private const val SESSION_ENTRY = "supabase_session_v1"

        private val SESSION_JSON = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
        }
    }
}

class KeystoreSupabaseCodeVerifierCache(
    private val secureStore: SecureStringStore,
) : CodeVerifierCache {
    override suspend fun saveCodeVerifier(codeVerifier: String) {
        require(codeVerifier.isNotBlank()) { "PKCE code verifier must not be blank" }
        secureStore.put(CODE_VERIFIER_ENTRY, codeVerifier)
    }

    override suspend fun loadCodeVerifier(): String? =
        secureStore.get(CODE_VERIFIER_ENTRY)

    override suspend fun deleteCodeVerifier() {
        secureStore.remove(CODE_VERIFIER_ENTRY)
    }

    companion object {
        private const val CODE_VERIFIER_ENTRY = "supabase_pkce_verifier_v1"
    }
}
