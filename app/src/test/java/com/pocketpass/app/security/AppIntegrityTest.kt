package com.pocketpass.app.security

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIntegrityTest {
    private val releaseDigest =
        "3a1f9c0b7d2e4f5a6b8c9d0e1f2a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c"

    @Test
    fun theCheckStaysOutOfTheWayUntilItIsConfigured() {
        assertEquals(
            AppIntegrityStatus.Unverified,
            decide(expected = releaseDigest, isDebugBuild = true),
        )
        assertEquals(
            AppIntegrityStatus.Unverified,
            decide(expected = "   ", isDebugBuild = false),
        )
    }

    @Test
    fun theReleaseCertificateVerifiesRegardlessOfKeytoolFormatting() {
        assertEquals(
            AppIntegrityStatus.Verified,
            decide(
                expected = releaseDigest.chunked(2).joinToString(":").uppercase(),
                digests = listOf(releaseDigest),
            ),
        )
        assertEquals(
            AppIntegrityStatus.Verified,
            decide(expected = releaseDigest, digests = listOf("00ff", releaseDigest)),
        )
    }

    @Test
    fun aResignedOrDebuggableBuildIsRejected() {
        assertEquals(
            AppIntegrityStatus.Compromised,
            decide(expected = releaseDigest, digests = listOf("00ff")),
        )
        assertEquals(
            AppIntegrityStatus.Compromised,
            decide(expected = releaseDigest, digests = emptyList()),
        )
        assertEquals(
            AppIntegrityStatus.Compromised,
            decide(expected = releaseDigest, digests = null),
        )
        assertEquals(
            AppIntegrityStatus.Compromised,
            decide(expected = releaseDigest, debuggable = true),
        )
    }

    private fun decide(
        expected: String,
        isDebugBuild: Boolean = false,
        debuggable: Boolean = false,
        digests: List<String>? = listOf(releaseDigest),
    ): AppIntegrityStatus = AppIntegrity.decide(
        expectedCertificateSha256 = expected,
        isDebugBuild = isDebugBuild,
        debuggable = { debuggable },
        certificateDigests = { digests },
    )
}
