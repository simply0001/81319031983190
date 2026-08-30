package com.pocketpass.app.data.supabase

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedAvatarInterceptorTest {
    private val interceptor = AuthenticatedAvatarInterceptor(
        supabaseBaseUrl = "https://api.pocketpass.xyz",
        publishableKey = "public-key",
        accessTokenProvider = { "session-token" },
    )

    @Test
    fun `adds current credentials to the exact private avatar endpoint`() {
        val request = Request.Builder()
            .url(
                "https://api.pocketpass.xyz/storage/v1/object/authenticated/avatars/" +
                    "00000000-0000-0000-0000-000000000000/mii.png",
            )
            .build()

        val authorized = interceptor.authorize(request)

        assertEquals("Bearer session-token", authorized.header("Authorization"))
        assertEquals("public-key", authorized.header("apikey"))
    }

    @Test
    fun `does not send credentials to another path or origin`() {
        val untrustedUrls = listOf(
            "https://api.pocketpass.xyz/auth/v1/user",
            "https://api.pocketpass.xyz.evil.example/" +
                "storage/v1/object/authenticated/avatars/user/avatar.png",
            "https://images.example/avatar.png",
            "http://api.pocketpass.xyz/" +
                "storage/v1/object/authenticated/avatars/user/avatar.png",
        )

        untrustedUrls.forEach { url ->
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer stale-token")
                .header("apikey", "stale-key")
                .build()
            val sanitized = interceptor.authorize(request)

            assertNull(url, sanitized.header("Authorization"))
            assertNull(url, sanitized.header("apikey"))
        }
    }

    @Test
    fun `leaves private request unauthenticated when no session exists`() {
        val signedOutInterceptor = AuthenticatedAvatarInterceptor(
            supabaseBaseUrl = "https://api.pocketpass.xyz",
            publishableKey = "public-key",
            accessTokenProvider = { null },
        )
        val request = Request.Builder()
            .url(
                "https://api.pocketpass.xyz/storage/v1/object/authenticated/avatars/" +
                    "user/avatar.png",
            )
            .build()

        val authorized = signedOutInterceptor.authorize(request)

        assertNull(authorized.header("Authorization"))
        assertNull(authorized.header("apikey"))
        assertTrue(signedOutInterceptor.isPrivateAvatarRequest(request.url))
    }
}
