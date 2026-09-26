package com.pocketpass.app.audio

import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.feature.AccountSetupUiState
import com.pocketpass.app.mii.MiiEditorMode
import com.pocketpass.app.mii.MiiEditorUiState
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.boards.BoardsScreen
import com.pocketpass.app.nearby.NearbyPermissionUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundMusicTest {
    private val signedIn = PocketPassUiState(
        sessionState = SessionState.Authenticated(FixtureData.CurrentUserId),
        accountSetup = AccountSetupUiState(resolved = true),
    )

    @Test
    fun boardsMusicContinuesAcrossCommunitiesAndPrivateMessages() {
        val boards = signedIn.copy(routes = listOf(PocketPassRoute.Root(PocketPassDestination.Messages)))
        for (screen in BoardsScreen.entries) {
            assertEquals(BackgroundMusicTrack.Boards, backgroundMusicTrack(boards.copy(boards = boards.boards.copy(screen = screen))))
        }
        assertEquals(BackgroundMusicTrack.Boards, backgroundMusicTrack(boards.copy(routes = boards.routes + PocketPassRoute.MessageDetail("conversation"))))
        assertEquals(BackgroundMusicTrack.Home, backgroundMusicTrack(boards.copy(routes = listOf(PocketPassRoute.Root(PocketPassDestination.Home)))))
        assertEquals(BackgroundMusicTrack.MiiMaker, backgroundMusicTrack(boards.copy(miiEditor = MiiEditorUiState(mode = MiiEditorMode.EditExisting))))
        assertEquals(null, backgroundMusicTrack(boards.copy(nearbyPermissionUi = NearbyPermissionUiState(visible = true))))
    }

    @Test
    fun playsOnlyDuringSignedInNavigation() {
        assertEquals(null, backgroundMusicTrack(PocketPassUiState()))
        assertEquals(
            null,
            backgroundMusicTrack(
                PocketPassUiState(sessionState = SessionState.SignedOut),
            ),
        )
        assertEquals(BackgroundMusicTrack.Home, backgroundMusicTrack(signedIn))
    }

    @Test
    fun playsTheMiiMakerTrackInsideTheEditor() {
        assertEquals(
            BackgroundMusicTrack.MiiMaker,
            backgroundMusicTrack(
                signedIn.copy(
                    miiEditor = MiiEditorUiState(mode = MiiEditorMode.RequiredSetup),
                ),
            ),
        )
        assertEquals(
            BackgroundMusicTrack.MiiMaker,
            backgroundMusicTrack(
                signedIn.copy(
                    miiEditor = MiiEditorUiState(mode = MiiEditorMode.EditExisting),
                ),
            ),
        )
        assertEquals(
            BackgroundMusicTrack.Home,
            backgroundMusicTrack(
                signedIn.copy(
                    miiEditor = MiiEditorUiState(mode = MiiEditorMode.Inactive),
                ),
            ),
        )
    }

    @Test
    fun mutesForNearbyPermissionFlow() {
        assertEquals(
            null,
            backgroundMusicTrack(
                signedIn.copy(
                    nearbyPermissionUi = NearbyPermissionUiState(visible = true),
                ),
            ),
        )
    }

    @Test
    fun mutesUntilAccountSetupIsResolvedAndComplete() {
        assertEquals(
            null,
            backgroundMusicTrack(
                signedIn.copy(accountSetup = AccountSetupUiState()),
            ),
        )
        assertEquals(
            null,
            backgroundMusicTrack(
                signedIn.copy(
                    accountSetup = AccountSetupUiState(
                        resolved = true,
                        required = true,
                    ),
                ),
            ),
        )
        assertEquals(
            BackgroundMusicTrack.Home,
            backgroundMusicTrack(
                signedIn.copy(
                    accountSetup = AccountSetupUiState(resolved = true),
                ),
            ),
        )
    }
}
