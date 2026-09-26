package com.pocketpass.app.audio

import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.PocketPassDestination

enum class BackgroundMusicTrack {
    Home,
    MiiMaker,
    Boards,
}

fun backgroundMusicTrack(state: PocketPassUiState): BackgroundMusicTrack? {
    val signedIn = state.sessionState is SessionState.Authenticated ||
        state.sessionState is SessionState.OfflineWithCachedSession
    val allowed = signedIn &&
        state.accountSetup.resolved &&
        !state.accountSetup.required &&
        !state.nearbyPermissionUi.visible
    if (!allowed) return null
    return when {
        state.miiEditor.isEditorVisible -> BackgroundMusicTrack.MiiMaker
        state.rootDestination == PocketPassDestination.Messages -> BackgroundMusicTrack.Boards
        else -> BackgroundMusicTrack.Home
    }
}
