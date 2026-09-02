package com.pocketpass.app.update

import com.pocketpass.app.data.LocalSettings
import com.pocketpass.app.data.SettingsRepository
import com.pocketpass.app.model.ThemeMode
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppUpdateStateHolderTest {
    private class InMemorySettingsRepository : SettingsRepository {
        val store = MutableStateFlow(LocalSettings())
        override val settings: Flow<LocalSettings> = store

        override suspend fun setNearby(enabled: Boolean) = Unit
        override suspend fun setNearbyOnboardingCompleted(completed: Boolean) = Unit
        override suspend fun setSoundLevel(level: Float) = Unit
        override suspend fun setSfxLevel(level: Float) = Unit
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override suspend fun setMoodEmojisEnabled(enabled: Boolean) = Unit
        override suspend fun setHomeMood(mood: com.pocketpass.app.model.HomeMood?) = Unit
        override suspend fun setEncounterLedEnabled(enabled: Boolean) = Unit
        override suspend fun setEncounterAlertsEnabled(enabled: Boolean) = Unit
        override suspend fun setNearbyRepairAlertsEnabled(enabled: Boolean) = Unit
        override suspend fun setUpdateAlertsEnabled(enabled: Boolean) = Unit
        override suspend fun setStepRewardsEnabled(enabled: Boolean) = Unit

        override suspend fun setLastNotifiedUpdateVersionCode(versionCode: Int) {
            store.value = store.value.copy(lastNotifiedUpdateVersionCode = versionCode)
        }

        override suspend fun setLeaderboardScope(
            scope: com.pocketpass.app.domain.model.LeaderboardScope,
        ) = Unit

        override suspend fun setRecentInteractionsSort(
            sort: com.pocketpass.app.model.RecentInteractionsSort,
        ) = Unit

        override suspend fun setFriendsSort(
            sort: com.pocketpass.app.model.RecentInteractionsSort,
        ) = Unit

        override suspend fun setLastSeenMinSupportedVersionCode(versionCode: Int) {
            store.value = store.value.copy(lastSeenMinSupportedVersionCode = versionCode)
        }

        override suspend fun setNearbyAlertsSeenThrough(epochMillis: Long) = Unit

        override suspend fun resetSettings() = Unit
    }

    private fun shaOf(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun manifestJson(
        versionCode: Int,
        sha: String = shaOf(APK_BYTES),
        minSupported: Int? = null,
    ): String {
        val min = minSupported?.let { ""","minSupportedVersionCode":$it""" } ?: ""
        return """
            {"schemaVersion":1,"versionCode":$versionCode,"versionName":"0.0.$versionCode",
             "apkUrl":"https://example.test/PocketPass.apk","apkSha256":"$sha",
             "apkSizeBytes":${APK_BYTES.size}$min}
        """.trimIndent()
    }

    private fun tempDir(): File =
        Files.createTempDirectory("pocketpass-update-test").toFile()

    private fun holder(
        scope: kotlinx.coroutines.CoroutineScope,
        settings: SettingsRepository = InMemorySettingsRepository(),
        dir: File = tempDir(),
        enabled: Boolean = true,
        fetch: suspend (String) -> String = { manifestJson(2) },
        downloadBytes: ByteArray = APK_BYTES,
        gate: () -> Boolean = { true },
        installer: suspend (File, UpdateManifest) -> Unit = { _, _ -> },
        notifier: (UpdateManifest) -> Boolean = { true },
        now: () -> Long = { 0L },
    ) = AppUpdateStateHolder(
        settingsRepository = settings,
        scope = scope,
        installedVersionCode = 1,
        enabled = enabled,
        manifestUrl = "https://example.test/latest.json",
        downloadDirProvider = { dir },
        manifestFetcher = { url -> fetch(url) },
        apkDownloader = { _, target, _, onProgress ->
            target.writeBytes(downloadBytes)
            onProgress(1f)
        },
        installGate = gate,
        installer = installer,
        notifier = notifier,
        now = now,
    )

    @Test
    fun checkFindsNewerVersion() = runTest {
        val holder = holder(this)

        holder.check()
        val state = holder.state.first { it.phase !is AppUpdatePhase.Idle && it.phase !is AppUpdatePhase.Checking }

        assertEquals(AppUpdatePhase.UpdateAvailable, state.phase)
        assertEquals(2, state.manifest?.versionCode)
        assertTrue(state.updateAvailable)
    }

    @Test
    fun sentinelMeansUpToDate() = runTest {
        val holder = holder(this, fetch = { """{"schemaVersion":1,"versionCode":0}""" })

        holder.check()
        val state = holder.state.first { it.phase !is AppUpdatePhase.Idle && it.phase !is AppUpdatePhase.Checking }

        assertEquals(AppUpdatePhase.UpToDate, state.phase)
        assertFalse(state.updateAvailable)
    }

    @Test
    fun failedCheckReportsCheckStage() = runTest {
        val holder = holder(this, fetch = { error("boom") })

        holder.check()
        val state = holder.state.first { it.phase is AppUpdatePhase.Failed }

        assertEquals(
            AppUpdateFailureStage.Check,
            (state.phase as AppUpdatePhase.Failed).stage,
        )
    }

    @Test
    fun downloadVerifiesAndBecomesReady() = runTest {
        val dir = tempDir()
        val holder = holder(this, dir = dir)

        holder.check()
        holder.state.first { it.phase is AppUpdatePhase.UpdateAvailable }
        holder.download()
        val state = holder.state.first {
            it.phase is AppUpdatePhase.ReadyToInstall || it.phase is AppUpdatePhase.Failed
        }

        assertEquals(AppUpdatePhase.ReadyToInstall, state.phase)
        assertTrue(File(dir, "PocketPass-2.apk").isFile)
        assertFalse(File(dir, "PocketPass-2.apk.part").exists())
    }

    @Test
    fun shaMismatchFailsDownloadAndCleansUp() = runTest {
        val dir = tempDir()
        val holder = holder(this, dir = dir, downloadBytes = "tampered".toByteArray())

        holder.check()
        holder.state.first { it.phase is AppUpdatePhase.UpdateAvailable }
        holder.download()
        val state = holder.state.first {
            it.phase is AppUpdatePhase.ReadyToInstall || it.phase is AppUpdatePhase.Failed
        }

        val failed = state.phase as AppUpdatePhase.Failed
        assertEquals(AppUpdateFailureStage.Download, failed.stage)
        assertFalse(File(dir, "PocketPass-2.apk").exists())
        assertFalse(File(dir, "PocketPass-2.apk.part").exists())
    }

    @Test
    fun installWithoutPermissionAsksAndStaysReady() = runTest {
        var installs = 0
        val holder = holder(
            this,
            gate = { false },
            installer = { _, _ -> installs++ },
        )

        holder.check()
        holder.state.first { it.phase is AppUpdatePhase.UpdateAvailable }
        holder.download()
        holder.state.first { it.phase is AppUpdatePhase.ReadyToInstall }
        holder.install()
        advanceUntilIdle()

        assertEquals(0, installs)
        assertEquals(AppUpdatePhase.ReadyToInstall, holder.state.value.phase)
    }

    @Test
    fun abortedInstallReturnsToReady() = runTest {
        val holder = holder(this)

        holder.check()
        holder.state.first { it.phase is AppUpdatePhase.UpdateAvailable }
        holder.download()
        holder.state.first { it.phase is AppUpdatePhase.ReadyToInstall }
        holder.onInstallFailed("user said no", aborted = true)

        assertEquals(AppUpdatePhase.ReadyToInstall, holder.state.value.phase)

        holder.onInstallFailed("real failure", aborted = false)
        val failed = holder.state.value.phase as AppUpdatePhase.Failed
        assertEquals(AppUpdateFailureStage.Install, failed.stage)
    }

    @Test
    fun disabledHolderNoOps() = runTest {
        var fetches = 0
        val holder = holder(this, enabled = false, fetch = { fetches++; manifestJson(2) })

        holder.check()
        holder.checkOnLaunch()
        holder.download()
        holder.install()
        advanceUntilIdle()

        assertEquals(0, fetches)
        assertEquals(AppUpdatePhase.Idle, holder.state.value.phase)
    }

    @Test
    fun foregroundRechecksOnlyWhenStaleAndThenPeriodically() = runTest {
        var fetches = 0
        var clock = 0L
        val holder = holder(
            this,
            fetch = { fetches++; manifestJson(2) },
            now = { clock },
        )

        holder.checkOnLaunch()
        advanceUntilIdle()
        assertEquals(1, fetches)

        holder.setForeground(true)
        runCurrent()
        assertEquals(1, fetches)

        holder.setForeground(false)
        clock = 11L * 60L * 1_000L
        holder.setForeground(true)
        runCurrent()
        assertEquals(2, fetches)

        advanceTimeBy(15L * 60L * 1_000L + 1L)
        runCurrent()
        assertEquals(3, fetches)

        holder.setForeground(false)
        advanceTimeBy(60L * 60L * 1_000L)
        runCurrent()
        assertEquals(3, fetches)
    }

    @Test
    fun remoteManifestSignalRefreshesAndRaisesTheForcedUpdateFloor() = runTest {
        var minSupported: Int? = null
        val holder = holder(this, fetch = { manifestJson(2, minSupported = minSupported) })

        holder.checkOnLaunch()
        advanceUntilIdle()
        assertFalse(holder.state.value.updateRequired)

        minSupported = 2
        holder.onRemoteManifestChanged()
        advanceUntilIdle()

        assertTrue(holder.state.value.updateRequired)
        assertEquals(AppUpdatePhase.UpdateAvailable, holder.state.value.phase)
    }

    @Test
    fun launchCheckNotifiesOncePerVersion() = runTest {
        var notified = 0
        val settings = InMemorySettingsRepository()
        val holder = holder(this, settings = settings, notifier = { notified++; true })

        holder.checkOnLaunch()
        holder.state.first { it.phase is AppUpdatePhase.UpdateAvailable }
        advanceUntilIdle()
        holder.checkOnLaunch()
        advanceUntilIdle()

        assertEquals(1, notified)
        assertEquals(2, settings.store.value.lastNotifiedUpdateVersionCode)
    }

    @Test
    fun unpostedNotificationRetriesNextLaunch() = runTest {
        var attempts = 0
        val settings = InMemorySettingsRepository()
        val holder = holder(this, settings = settings, notifier = { attempts++; false })

        holder.checkOnLaunch()
        holder.state.first { it.phase is AppUpdatePhase.UpdateAvailable }
        advanceUntilIdle()
        holder.checkOnLaunch()
        advanceUntilIdle()

        assertEquals(2, attempts)
        assertEquals(0, settings.store.value.lastNotifiedUpdateVersionCode)
    }

    @Test
    fun forceGateFollowsManifestAndPersists() = runTest {
        val settings = InMemorySettingsRepository()
        val holder = holder(
            this,
            settings = settings,
            fetch = { manifestJson(3, minSupported = 3) },
        )

        holder.check()
        val state = holder.state.first { it.updateRequired }

        assertTrue(state.updateRequired)
        advanceUntilIdle()
        assertEquals(3, settings.store.value.lastSeenMinSupportedVersionCode)
    }

    @Test
    fun persistedForceGateSeedsBeforeFirstFetch() = runTest {
        val settings = InMemorySettingsRepository()
        settings.store.value = LocalSettings(lastSeenMinSupportedVersionCode = 5)
        val holder = holder(this, settings = settings, fetch = { error("offline") })

        holder.checkOnLaunch()
        val state = holder.state.first { it.updateRequired }

        assertTrue(state.updateRequired)
    }

    private companion object {
        val APK_BYTES = "pretend this is an apk".toByteArray()
    }
}
