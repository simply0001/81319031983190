package com.pocketpass.app.data.supabase

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl

internal class AuthenticatedAvatarInterceptor(
    supabaseBaseUrl: String,
    private val publishableKey: String,
    private val accessTokenProvider: () -> String?,
) : Interceptor {
    private val supabaseOrigin: HttpUrl? =
        supabaseBaseUrl
            .takeIf(String::isNotBlank)
            ?.let { runCatching { it.toHttpUrl() }.getOrNull() }

    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(authorize(chain.request()))
    }

    internal fun authorize(request: Request): Request {
        val builder = request.newBuilder()
            .removeHeader(AUTHORIZATION_HEADER)
            .removeHeader(API_KEY_HEADER)

        if (isPrivateAvatarRequest(request.url)) {
            accessTokenProvider()
                ?.takeIf(String::isNotBlank)
                ?.let { token ->
                    builder.header(AUTHORIZATION_HEADER, "Bearer $token")
                    publishableKey
                        .takeIf(String::isNotBlank)
                        ?.let { key -> builder.header(API_KEY_HEADER, key) }
                }
        }
        return builder.build()
    }

    internal fun isPrivateAvatarRequest(url: HttpUrl): Boolean {
        val origin = supabaseOrigin ?: return false
        if (
            url.scheme != origin.scheme ||
            url.host != origin.host ||
            url.port != origin.port
        ) {
            return false
        }
        return PRIVATE_BUCKET_PATH_PREFIXES.any(url.encodedPath::startsWith)
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val API_KEY_HEADER = "apikey"

        val PRIVATE_BUCKET_PATH_PREFIXES = listOf(
            "/storage/v1/object/authenticated/avatars/",
            "/storage/v1/object/authenticated/message-media/",
        )
    }
}
