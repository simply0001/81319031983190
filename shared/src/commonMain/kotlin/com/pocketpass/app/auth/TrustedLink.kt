package com.pocketpass.app.auth

import com.pocketpass.app.data.supabase.SupabaseBackendConfig
import io.ktor.http.Url
import io.ktor.http.decodeURLQueryComponent

internal const val MAX_LINK_URI_LENGTH = 4_096

internal sealed interface TrustedLink {
    data class Accepted(val path: String, val rawQuery: String) : TrustedLink

    data object Malformed : TrustedLink

    data object UntrustedOrigin : TrustedLink
}

// Ktor's Url never rejects input: it invents a scheme and a host ("https:///a" parses with host "a"),
// so every structural guard runs on the raw string before the parse is allowed to mean anything. The
// query is split off by hand and never handed to Ktor, which rejects the whole URL over one bad escape.
internal fun trustedLinkOrigin(rawUri: String): TrustedLink {
    if (rawUri.length > MAX_LINK_URI_LENGTH || rawUri.hasUnsafeCharacters()) {
        return TrustedLink.Malformed
    }
    if (!rawUri.startsWith(HTTPS_PREFIX, ignoreCase = true)) return TrustedLink.UntrustedOrigin

    val withoutFragment = rawUri.substringBefore('#')
    val origin = withoutFragment.substringBefore('?')
    val rawQuery = withoutFragment.substringAfter('?', "")

    val authority = origin.substring(HTTPS_PREFIX.length)
    if (authority.isEmpty() || authority.startsWith('/')) return TrustedLink.UntrustedOrigin

    val url = runCatching { Url(origin) }.getOrElse { return TrustedLink.Malformed }
    if (
        !url.protocol.name.equals("https", ignoreCase = true) ||
        !url.host.equals(SupabaseBackendConfig.AUTH_CALLBACK_HOST, ignoreCase = true) ||
        url.user != null ||
        url.specifiedPort !in ACCEPTED_PORTS
    ) {
        return TrustedLink.UntrustedOrigin
    }
    return TrustedLink.Accepted(path = url.encodedPath, rawQuery = rawQuery)
}

internal fun parseQueryParameters(rawQuery: String): Map<String, List<String>> {
    if (rawQuery.isBlank()) return emptyMap()
    return rawQuery
        .split('&')
        .mapNotNull { parameter ->
            val separator = parameter.indexOf('=')
            val rawName = if (separator >= 0) parameter.substring(0, separator) else parameter
            if (rawName.isBlank()) return@mapNotNull null
            val rawValue = if (separator >= 0) parameter.substring(separator + 1) else ""
            runCatching { decodeComponent(rawName) to decodeComponent(rawValue) }.getOrNull()
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
}

private fun decodeComponent(value: String): String = value.decodeURLQueryComponent(plusIsSpace = true)

private fun String.hasUnsafeCharacters(): Boolean =
    any { it.isWhitespace() || it.code < 0x20 || it.code == 0x7F || it == '\\' }

private const val HTTPS_PREFIX = "https://"
private const val UNSPECIFIED_PORT = 0
private val ACCEPTED_PORTS = setOf(UNSPECIFIED_PORT, 443)
