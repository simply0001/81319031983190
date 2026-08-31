package com.pocketpass.app.data.supabase

import io.ktor.http.Url
import kotlin.io.encoding.Base64

data class SupabaseBackendConfig(
    val baseUrl: String,
    val publishableKey: String,
    val authCallbackUrl: String = DEFAULT_AUTH_CALLBACK_URL,
) {
    init {
        validateHttpsUrl("baseUrl", baseUrl, pathMustBeEmpty = true)
        require(publishableKey.isNotBlank()) { "publishableKey must not be blank" }
        require(!publishableKey.contains("service_role", ignoreCase = true)) {
            "A service-role key must never be used by the Android client"
        }
        require(!isServiceRoleJwt(publishableKey)) {
            "A service-role JWT must never be used by the Android client"
        }

        // iOS cannot claim the https link without an associated-domains
        // entitlement, so it redirects through the app's own scheme instead;
        // only this exact literal is allowed past the https rules.
        if (authCallbackUrl != MOBILE_AUTH_CALLBACK_URL) {
            validateHttpsUrl("authCallbackUrl", authCallbackUrl, pathMustBeEmpty = false)
            val callback = Url(authCallbackUrl)
            require(callback.host.equals(AUTH_CALLBACK_HOST, ignoreCase = true)) {
                "authCallbackUrl must use $AUTH_CALLBACK_HOST"
            }
            require(callback.encodedPath == AUTH_CALLBACK_PATH) {
                "authCallbackUrl must use path $AUTH_CALLBACK_PATH"
            }
            require(callback.encodedQuery.isEmpty() && callback.fragment.isEmpty()) {
                "authCallbackUrl must not contain a query or fragment"
            }
        }
    }

    val normalizedBaseUrl: String
        get() = baseUrl.trimEnd('/')

    override fun toString(): String =
        "SupabaseBackendConfig(baseUrl=$baseUrl, publishableKey=<redacted>, " +
            "authCallbackUrl=$authCallbackUrl)"

    companion object {
        const val DEFAULT_BASE_URL = "https://api.pocketpass.xyz"
        const val AUTH_CALLBACK_HOST = "links.pocketpass.xyz"
        const val AUTH_CALLBACK_PATH = "/auth/callback"
        const val DEFAULT_AUTH_CALLBACK_URL =
            "https://$AUTH_CALLBACK_HOST$AUTH_CALLBACK_PATH"

        // Must stay listed in the server's additional_redirect_urls.
        const val MOBILE_AUTH_CALLBACK_URL = "pocketpass://auth/callback"

        private fun validateHttpsUrl(
            name: String,
            value: String,
            pathMustBeEmpty: Boolean,
        ) {
            require(value.startsWith(HTTPS_PREFIX, ignoreCase = true)) {
                "$name must use HTTPS"
            }
            // Ktor's Url accepts an empty authority; java.net.URI did not.
            val authority = value.substring(HTTPS_PREFIX.length)
            require(authority.isNotEmpty() && !authority.startsWith('/')) {
                "$name must include a host"
            }
            val url = runCatching { Url(value) }
                .getOrElse { throw IllegalArgumentException("$name must be a valid URL", it) }
            require(url.protocol.name.equals("https", ignoreCase = true)) {
                "$name must use HTTPS"
            }
            require(url.host.isNotBlank()) { "$name must include a host" }
            require(url.user == null) { "$name must not contain user info" }
            require(url.fragment.isEmpty()) { "$name must not contain a fragment" }
            if (pathMustBeEmpty) {
                require(url.encodedPath.isEmpty() || url.encodedPath == "/") {
                    "$name must not contain a path"
                }
                require(url.encodedQuery.isEmpty()) { "$name must not contain a query" }
            }
        }

        private fun isServiceRoleJwt(key: String): Boolean {
            val payload = key.split('.').getOrNull(1) ?: return false
            val decoded = runCatching {
                URL_SAFE_BASE64.decode(payload).decodeToString()
            }.getOrNull() ?: return false
            return SERVICE_ROLE_CLAIM.containsMatchIn(decoded)
        }

        private const val HTTPS_PREFIX = "https://"

        private val URL_SAFE_BASE64 =
            Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

        private val SERVICE_ROLE_CLAIM =
            Regex(""""role"\s*:\s*"service_role"""", RegexOption.IGNORE_CASE)
    }
}
