package com.pocketpass.app.audio

import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.feature.AccountSetupUiState
import com.pocketpass.app.mii.MiiEditorMode
import com.pocketpass.app.mii.MiiEditorUiState
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.nearby.NearbyPermissionUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundMusicTest {
    private val signedIn = PocketPassUiState(
        sessionState = SessionState.Authenticated(FixtureData.CurrentUserId),
        accountSetup = AccountSetupUiState(resolved = true),
    )

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
