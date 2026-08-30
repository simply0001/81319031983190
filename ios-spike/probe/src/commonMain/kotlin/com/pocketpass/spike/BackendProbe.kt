package com.pocketpass.spike

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse

const val POCKETPASS_URL = "https://api.pocketpass.xyz"

fun buildClient(publishableKey: String): SupabaseClient =
    createSupabaseClient(supabaseUrl = POCKETPASS_URL, supabaseKey = publishableKey) {
        install(Auth) {
            enableLifecycleCallbacks = false
        }
        install(Postgrest)
        install(Realtime)
        install(Storage)
    }

suspend fun probeAuthSettings(): Int {
    val client = HttpClient()
    return try {
        val response: HttpResponse = client.get("$POCKETPASS_URL/auth/v1/settings")
        response.status.value
    } finally {
        client.close()
    }
}
