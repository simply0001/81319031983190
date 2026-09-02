package com.pocketpass.app.steps

import com.pocketpass.app.data.LocalSettings
import com.pocketpass.app.data.SettingsRepository
import com.pocketpass.app.data.repository.remote.StepRewardsRemoteDataSource
import com.pocketpass.app.domain.model.LeaderboardScope
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.model.HomeMood
import com.pocketpass.app.model.RecentInteractionsSort
import com.pocketpass.app.model.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class StepRewardsTrackerTest {
    private class FakeClock(var nowMillis: Long = 1_800_000_000_000L) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(nowMillis)
    }

    private class FakeSource(
        override val supported: Boolean = true,
        initialPermission: StepPermission = StepPermission.Granted,
    ) : StepSource {
        override val permission = MutableStateFlow(initialPermission)
        val emitted = MutableSharedFlow<StepSample>()
        override val samples: Flow<StepSample> = emitted
        var next: StepSample? = null
        var requests = 0
        var liveActive = false

        override suspend fun sample(): StepSample? = next
        override fun setLive(active: Boolean) {
            liveActive = active
        }
        override fun requestPermission() {
            requests += 1
        }
        override fun refreshPermission() = Unit
    }

    private class RecordingRemote : StepRewardsRemoteDataSource {
        val reports = mutableListOf<Int>()
        var failure: RepositoryFailure? = null

        override suspend fun reportDailySteps(
            accountId: UserId,
            localDay: String,
            steps: Int,
            utcOffsetMinutes: Int,
        ): RepositoryResult<DailyStepReward> {
            reports += steps
            failure?.let { return RepositoryResult.Failure(it) }
            val awarded = tokensForSteps(steps)
            return RepositoryResult.Success(
                DailyStepReward(localDay, steps, awarded, awarded, awarded),
            )
        }
    }

    private class FakeSettingsRepository(initial: LocalSettings) : SettingsRepository {
        val store = MutableStateFlow(initial)
        override val settings: Flow<LocalSettings> = store
        override suspend fun setNearby(enabled: Boolean) = Unit
        override suspend fun setNearbyOnboardingCompleted(completed: Boolean) = Unit
        override suspend fun setSoundLevel(level: Float) = Unit
        override suspend fun setSfxLevel(level: Float) = Unit
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override suspend fun setMoodEmojisEnabled(enabled: Boolean) = Unit
        override suspend fun setHomeMood(mood: HomeMood?) = Unit
        override suspend fun setEncounterLedEnabled(enabled: Boolean) = Unit
        override suspend fun setEncounterAlertsEnabled(enabled: Boolean) = Unit
        override suspend fun setNearbyRepairAlertsEnabled(enabled: Boolean) = Unit
        override suspend fun setUpdateAlertsEnabled(enabled: Boolean) = Unit
        override suspend fun setStepRewardsEnabled(enabled: Boolean) {
            store.update { it.copy(stepRewardsEnabled = enabled) }
        }
        override suspend fun setLastNotifiedUpdateVersionCode(versionCode: Int) = Unit
        override suspend fun setLastSeenMinSupportedVersionCode(versionCode: Int) = Unit
        override suspend fun setNearbyAlertsSeenThrough(epochMillis: Long) = Unit
        override suspend fun setLeaderboardScope(scope: LeaderboardScope) = Unit
        override suspend fun setRecentInteractionsSort(sort: RecentInteractionsSort) = Unit
        override suspend fun setFriendsSort(sort: RecentInteractionsSort) = Unit
        override suspend fun resetSettings() = Unit
    }

    private fun sample(steps: Int, day: String = "2026-09-02") =
        StepSample(localDay = day, utcOffsetMinutes = 120, stepsToday = steps, sampledAtEpochMillis = 0L)

    private fun TestScope.tracker(
        source: FakeSource = FakeSource(),
        remote: RecordingRemote = RecordingRemote(),
        enabled: Boolean = true,
        signedIn: Boolean = true,
        clock: FakeClock = FakeClock(),
    ): StepRewardsTracker {
        val repository = FakeSettingsRepository(LocalSettings(stepRewardsEnabled = enabled))
        val account = MutableStateFlow(if (signedIn) UserId("walker") else null)
        return StepRewardsTracker(
            settingsRepository = repository,
            settings = repository.store,
            activeAccountId = account,
            source = source,
            remote = remote,
            scope = backgroundScope,
            clock = clock,
        )
    }

    @Test
    fun disabledTrackerNeverReports() = runTest {
        val source = FakeSource()
        val remote = RecordingRemote()
        val tracker = tracker(source, remote, enabled = false)
        runCurrent()
        source.next = sample(4_000)

        assertTrue(tracker.sampleAndClaim())

        assertTrue(remote.reports.isEmpty())
        assertEquals(StepRewardsStatus.Disabled, tracker.state.value.status)
    }

    @Test
    fun unsupportedDeviceHidesTheFeature() = runTest {
        val tracker = tracker(FakeSource(supported = false), enabled = true)
        runCurrent()

        assertEquals(StepRewardsStatus.Unsupported, tracker.state.value.status)
        assertFalse(tracker.state.value.supported)
    }

    @Test
    fun enablingWithoutPermissionAsksForIt() = runTest {
        val source = FakeSource(initialPermission = StepPermission.NotDetermined)
        val tracker = tracker(source, enabled = false)
        runCurrent()

        tracker.onPreferenceChanged(true)
        runCurrent()

        assertEquals(1, source.requests)
        assertEquals(StepRewardsStatus.NeedsPermission, tracker.state.value.status)
        assertTrue(tracker.state.value.visible)

        source.permission.value = StepPermission.Granted
        runCurrent()

        assertEquals(StepRewardsStatus.Tracking, tracker.state.value.status)
    }

    @Test
    fun reportsOnlyWhenTheCountIsWorthMore() = runTest {
        val source = FakeSource()
        val remote = RecordingRemote()
        val tracker = tracker(source, remote)
        runCurrent()

        source.emitted.emit(sample(3_999))
        runCurrent()
        assertEquals(listOf(3_999), remote.reports)
        assertEquals(9, tracker.state.value.tokensToday)
        assertEquals(3_999, tracker.state.value.stepsToday)

        source.emitted.emit(sample(4_000))
        runCurrent()
        assertEquals(listOf(3_999, 4_000), remote.reports)
        assertEquals(10, tracker.state.value.tokensToday)

        source.emitted.emit(sample(4_100))
        runCurrent()
        assertEquals(2, remote.reports.size)
        assertEquals(4_100, tracker.state.value.stepsToday)
    }

    @Test
    fun retryableFailureWaitsBeforeTryingAgain() = runTest {
        val source = FakeSource()
        val remote = RecordingRemote()
        val clock = FakeClock()
        val tracker = tracker(source, remote, clock = clock)
        runCurrent()
        remote.failure = RepositoryFailure(kind = RepositoryFailureKind.Unavailable)

        source.emitted.emit(sample(800))
        runCurrent()
        assertEquals(1, remote.reports.size)
        assertNotNull(tracker.state.value.claimError)

        source.emitted.emit(sample(900))
        runCurrent()
        assertEquals(1, remote.reports.size)

        clock.nowMillis += 31_000
        source.emitted.emit(sample(900))
        runCurrent()
        assertEquals(2, remote.reports.size)

        remote.failure = null
        clock.nowMillis += 31_000
        source.emitted.emit(sample(1_000))
        runCurrent()
        assertEquals(3, remote.reports.size)
        assertEquals(2, tracker.state.value.tokensToday)
        assertNull(tracker.state.value.claimError)
    }

    @Test
    fun rejectedDayStopsUntilTheCountGrows() = runTest {
        val source = FakeSource()
        val remote = RecordingRemote()
        val clock = FakeClock()
        val tracker = tracker(source, remote, clock = clock)
        runCurrent()
        remote.failure = RepositoryFailure(kind = RepositoryFailureKind.Validation)

        source.emitted.emit(sample(800))
        runCurrent()
        clock.nowMillis += 31_000
        source.emitted.emit(sample(900))
        runCurrent()
        assertEquals(1, remote.reports.size)

        source.emitted.emit(sample(1_200))
        runCurrent()
        assertEquals(2, remote.reports.size)
    }

    @Test
    fun aNewDayResetsProgress() = runTest {
        val source = FakeSource()
        val remote = RecordingRemote()
        val tracker = tracker(source, remote)
        runCurrent()

        source.emitted.emit(sample(4_000, "2026-09-02"))
        runCurrent()
        assertEquals(10, tracker.state.value.tokensToday)

        source.emitted.emit(sample(400, "2026-09-03"))
        runCurrent()
        assertEquals(listOf(4_000, 400), remote.reports)
        assertEquals(1, tracker.state.value.tokensToday)
        assertEquals("2026-09-03", tracker.state.value.localDay)
    }

    @Test
    fun sampleAndClaimReportsTheOutcome() = runTest {
        val source = FakeSource()
        val remote = RecordingRemote()
        val clock = FakeClock()
        val tracker = tracker(source, remote, clock = clock)
        runCurrent()
        source.next = sample(800)
        remote.failure = RepositoryFailure(kind = RepositoryFailureKind.Unavailable)

        assertFalse(tracker.sampleAndClaim())

        remote.failure = null
        clock.nowMillis += 31_000
        assertTrue(tracker.sampleAndClaim())
        assertEquals(2, tracker.state.value.tokensToday)
    }

    @Test
    fun foregroundTurnsLiveReadingsOnWhileTracking() = runTest {
        val source = FakeSource()
        val tracker = tracker(source)
        runCurrent()

        tracker.setForeground(true)
        runCurrent()
        assertTrue(source.liveActive)

        tracker.setForeground(false)
        runCurrent()
        assertFalse(source.liveActive)
    }
}
