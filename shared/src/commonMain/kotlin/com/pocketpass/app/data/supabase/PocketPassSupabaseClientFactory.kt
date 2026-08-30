package com.pocketpass.app.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

class PocketPassSupabaseClientFactory(
    private val config: SupabaseBackendConfig,
    private val sessionManager: SessionManager,
    private val codeVerifierCache: CodeVerifierCache,
) {
    private val client: SupabaseClient by lazy { createClient() }

    fun get(): SupabaseClient = client

    private fun createClient(): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = config.normalizedBaseUrl,
            supabaseKey = config.publishableKey,
        ) {
            install(Auth) {
                flowType = FlowType.PKCE
                defaultRedirectUrl = config.authCallbackUrl
                scheme = AUTH_CALLBACK_SCHEME
                host = SupabaseBackendConfig.AUTH_CALLBACK_HOST
                this.sessionManager = this@PocketPassSupabaseClientFactory.sessionManager
                this.codeVerifierCache = this@PocketPassSupabaseClientFactory.codeVerifierCache
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
                autoSaveToStorage = true
                enableLifecycleCallbacks = false
            }
            install(Postgrest)
            install(Realtime) {
                disconnectOnSessionLoss = true
                connectOnSubscribe = true
                disconnectOnNoSubscriptions = true
            }
            install(Storage)
        }

    companion object {
        private const val AUTH_CALLBACK_SCHEME = "https"
    }
}
