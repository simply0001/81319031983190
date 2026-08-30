package com.pocketpass.app.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import java.security.MessageDigest
import java.util.Locale

enum class AppIntegrityStatus {
    Unverified,
    Verified,
    Compromised,
}

object AppIntegrity {
    fun evaluate(
        context: Context,
        expectedCertificateSha256: String,
        isDebugBuild: Boolean,
    ): AppIntegrityStatus = decide(
        expectedCertificateSha256 = expectedCertificateSha256,
        isDebugBuild = isDebugBuild,
        debuggable = { isDebuggable(context) },
        certificateDigests = { signingCertificates(context)?.map(::digest) },
    )

    internal fun decide(
        expectedCertificateSha256: String,
        isDebugBuild: Boolean,
        debuggable: () -> Boolean,
        certificateDigests: () -> List<String>?,
    ): AppIntegrityStatus {
        if (isDebugBuild) return AppIntegrityStatus.Unverified
        val expected = normalize(expectedCertificateSha256)
        if (expected.isEmpty()) return AppIntegrityStatus.Unverified
        if (debuggable()) return AppIntegrityStatus.Compromised
        val digests = certificateDigests()?.takeIf { it.isNotEmpty() }
            ?: return AppIntegrityStatus.Compromised
        val matches = digests.any { normalize(it) == expected }
        return if (matches) AppIntegrityStatus.Verified else AppIntegrityStatus.Compromised
    }

    private fun isDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun signingCertificates(context: Context): List<Signature>? = runCatching {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val signingInfo = info.signingInfo ?: return null
        if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners.toList()
        } else {
            signingInfo.signingCertificateHistory.toList()
        }
    }.getOrNull()

    private fun digest(signature: Signature): String =
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun normalize(value: String): String =
        value.filterNot { it == ':' || it.isWhitespace() }.lowercase(Locale.ROOT)
}
