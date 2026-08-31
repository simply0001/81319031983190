package com.pocketpass.app.audio

import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.model.PocketPassUiState

enum class BackgroundMusicTrack {
    Home,
    MiiMaker,
}

fun backgroundMusicTrack(state: PocketPassUiState): BackgroundMusicTrack? {
    val signedIn = state.sessionState is SessionState.Authenticated ||
        state.sessionState is SessionState.OfflineWithCachedSession
    val allowed = signedIn &&
        state.accountSetup.resolved &&
        !state.accountSetup.required &&
        !state.nearbyPermissionUi.visible
    if (!allowed) return null
    return if (state.miiEditor.isEditorVisible) {
        BackgroundMusicTrack.MiiMaker
    } else {
        BackgroundMusicTrack.Home
    }
}
