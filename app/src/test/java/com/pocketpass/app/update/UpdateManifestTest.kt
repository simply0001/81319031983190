package com.pocketpass.app.update

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun releaseNotesKeepEveryLineAndReadAsBullets() {
        val lines = releaseNoteLines(
            "## 0.0.4\r\n\r\n- Dark mode\n* Settings pops\n\n  + Realtime stats  \nPlain line\n",
        )

        assertEquals(
            listOf("0.0.4", "• Dark mode", "• Settings pops", "• Realtime stats", "Plain line"),
            lines,
        )
    }

    @Test
    fun onlyVersionCodeDecidesNewness() {
        val manifest = UpdateManifest(versionCode = 2, versionName = "0.0.1")

        assertTrue(manifest.isNewerThan(1))
        assertFalse(manifest.isNewerThan(2))
        assertFalse(manifest.isNewerThan(3))
        assertFalse(
            UpdateManifest(versionCode = 1, versionName = "9.9.9").isNewerThan(1),
        )
    }

    @Test
    fun sentinelIsNeverAnUpdate() {
        val sentinel = json.decodeFromString<UpdateManifest>(
            """{"schemaVersion":1,"versionCode":0}""",
        )

        assertFalse(sentinel.isNewerThan(1))
        assertFalse(sentinel.forcesUpdateOf(1))
        assertFalse(sentinel.isInstallable())
    }

    @Test
    fun unknownKeysAreTolerated() {
        val manifest = json.decodeFromString<UpdateManifest>(
            """
            {
              "schemaVersion": 1,
              "versionCode": 3,
              "versionName": "0.0.3",
              "futureField": {"nested": true}
            }
            """.trimIndent(),
        )

        assertEquals(3, manifest.versionCode)
    }

    @Test(expected = SerializationException::class)
    fun malformedManifestThrows() {
        json.decodeFromString<UpdateManifest>("""{"versionName":"0.0.3"}""")
    }

    @Test
    fun forceGateComparesAgainstMinSupportedVersion() {
        val manifest = UpdateManifest(versionCode = 5, minSupportedVersionCode = 3)

        assertTrue(manifest.forcesUpdateOf(2))
        assertFalse(manifest.forcesUpdateOf(3))
        assertFalse(UpdateManifest(versionCode = 5).forcesUpdateOf(1))
    }

    @Test
    fun installabilityRequiresUrlAndFullSha() {
        val sha = "a".repeat(64)

        assertTrue(
            UpdateManifest(versionCode = 2, apkUrl = "https://x/y.apk", apkSha256 = sha)
                .isInstallable(),
        )
        assertFalse(UpdateManifest(versionCode = 2, apkSha256 = sha).isInstallable())
        assertFalse(
            UpdateManifest(versionCode = 2, apkUrl = "https://x/y.apk", apkSha256 = "abc")
                .isInstallable(),
        )
        assertFalse(
            UpdateManifest(
                versionCode = 2,
                apkUrl = "https://x/y.apk",
                apkSha256 = "z".repeat(64),
            ).isInstallable(),
        )
    }
}
