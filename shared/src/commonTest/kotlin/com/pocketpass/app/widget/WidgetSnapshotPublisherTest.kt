package com.pocketpass.app.widget

import com.pocketpass.app.data.LocalSettings
import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.EncounterId
import com.pocketpass.app.domain.model.NearbyEncounter
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.state.LoadState
import com.pocketpass.app.feature.FriendsFeatureState
import com.pocketpass.app.feature.HomeProfileFeatureState
import com.pocketpass.app.feature.NotificationFeatureState
import com.pocketpass.app.mii.MiiEditorUiState
import com.pocketpass.app.model.ThemeMode
import com.pocketpass.app.nearby.NearbyFeatureState
import com.pocketpass.app.nearby.NearbyRuntimeState
import com.pocketpass.app.nearby.NearbyRuntimeStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

class WidgetSnapshotPublisherTest {
    private val account = UserId("90000000-0000-4000-8000-000000000001")
    private val dayStart = 1_700_000_000_000L
    private val now = dayStart + 6 * 60 * 60 * 1000L

    @Test
    fun countsOnlyEncountersSinceLocalMidnightAndKeepsTheLatestTimestamp() {
        val recorded = FakeSink()
        val publisher = publisher(
            sink = recorded,
            homeProfile = MutableStateFlow(
                HomeProfileFeatureState(
                    profile = LoadState.Data(profile(bio = "")),
                    recentInteractions = LoadState.Data(
                        listOf(
                            encounter("a", dayStart - 1),
                            encounter("b", dayStart),
                            encounter("c", dayStart + 60_000),
                        ),
                    ),
                ),
            ),
        )

        val pending = publisher.current()

        assertEquals(2, pending.content.encountersToday)
        assertEquals(dayStart + 60_000, pending.content.lastEncounterEpochMillis)
        assertEquals(WidgetSnapshotPublisher.DEFAULT_BIO, pending.content.bio)
        assertEquals("petah", pending.content.avatarBundledKey)
        assertEquals(true, pending.content.signedIn)
        assertEquals(0L, pending.content.updatedAtEpochMillis)
    }

    @Test
    fun nearbyLastEncounterWinsWhenNewerThanTheList() {
        val publisher = publisher(
            nearby = MutableStateFlow(
                NearbyFeatureState(
                    runtime = NearbyRuntimeState(
                        status = NearbyRuntimeStatus.Running,
                        lastEncounterAt = Instant.fromEpochMilliseconds(now - 1_000),
                    ),
                ),
            ),
        )

        val content = publisher.current().content

        assertEquals(now - 1_000, content.lastEncounterEpochMillis)
        assertEquals("Running", content.nearbyStatus)
    }

    @Test
    fun snapshotSurvivesAJsonRoundTrip() {
        val original = publisher(
            miiEditor = MutableStateFlow(MiiEditorUiState(activePortraitFilePath = "/tmp/p.png")),
            settings = MutableStateFlow(LocalSettings(themeMode = ThemeMode.Dark)),
        ).current().content.copy(updatedAtEpochMillis = now)

        val decoded = WidgetSnapshot.decode(original.encode())

        assertEquals(original, decoded)
        assertEquals(WidgetSnapshot.PORTRAIT_FILE_NAME, decoded?.portraitFileName)
        assertEquals("Dark", decoded?.themeMode)
        assertNull(WidgetSnapshot.decode("not json"))
    }

    @Test
    fun publishesOnceForABurstAndStampsTheTime() = runTest {
        val sink = FakeSink()
        val notifications = MutableStateFlow(NotificationFeatureState())
        val publisher = publisher(
            scope = backgroundScope,
            sink = sink,
            notifications = notifications,
            debounceMillis = 100,
        )

        publisher.start()
        advanceTimeBy(150)
        assertEquals(1, sink.published.size)

        notifications.value = NotificationFeatureState()
        notifications.value = NotificationFeatureState(error = "a")
        notifications.value = NotificationFeatureState(error = "b")
        advanceTimeBy(150)

        // Errors do not change the snapshot content, so nothing new is published.
        assertEquals(1, sink.published.size)
        assertEquals(now, sink.published.single().updatedAtEpochMillis)
        publisher.stop()
    }

    private fun publisher(
        scope: kotlinx.coroutines.CoroutineScope = kotlinx.coroutines.GlobalScope,
        sink: WidgetSnapshotSink = FakeSink(),
        homeProfile: MutableStateFlow<HomeProfileFeatureState> = MutableStateFlow(
            HomeProfileFeatureState(
                profile = LoadState.Data(profile()),
                recentInteractions = LoadState.Data(emptyList()),
            ),
        ),
        notifications: MutableStateFlow<NotificationFeatureState> =
            MutableStateFlow(NotificationFeatureState()),
        nearby: MutableStateFlow<NearbyFeatureState> = MutableStateFlow(NearbyFeatureState()),
        miiEditor: MutableStateFlow<MiiEditorUiState> = MutableStateFlow(MiiEditorUiState()),
        settings: MutableStateFlow<LocalSettings> = MutableStateFlow(LocalSettings()),
        debounceMillis: Long = 500,
    ) = WidgetSnapshotPublisher(
        scope = scope,
        activeAccountId = MutableStateFlow(account),
        homeProfile = homeProfile,
        notifications = notifications,
        friends = MutableStateFlow(FriendsFeatureState()),
        nearby = nearby,
        miiEditor = miiEditor,
        settings = settings,
        sink = sink,
        nowEpochMillis = { now },
        startOfLocalDay = { dayStart },
        debounceMillis = debounceMillis,
    )

    private fun profile(bio: String = "Hi there") = UserProfile(
        userId = account,
        displayName = "Petah",
        avatar = AvatarReference.Bundled("petah"),
        bio = bio,
        updatedAt = Instant.fromEpochMilliseconds(now),
    )

    private fun encounter(id: String, occurredAt: Long) = NearbyEncounter(
        id = EncounterId("encounter-$id"),
        ownerId = account,
        profile = profile(),
        occurredAt = Instant.fromEpochMilliseconds(occurredAt),
        resolvedAt = Instant.fromEpochMilliseconds(occurredAt),
    )

    private class FakeSink : WidgetSnapshotSink {
        val published = mutableListOf<WidgetSnapshot>()

        override suspend fun publish(snapshot: WidgetSnapshot, portraitSourcePath: String?) {
            published += snapshot
        }
    }
}
