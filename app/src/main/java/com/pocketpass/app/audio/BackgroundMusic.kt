package com.pocketpass.app.audio

import android.content.Context
import android.media.MediaPlayer
import androidx.annotation.RawRes
import com.pocketpass.app.R
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.model.PocketPassUiState

enum class BackgroundMusicTrack(@param:RawRes val resource: Int) {
    Home(R.raw.bgm_main),
    MiiMaker(R.raw.bgm_mii),
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

class BackgroundMusicPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var activeTrack: BackgroundMusicTrack? = null

    fun update(track: BackgroundMusicTrack?, volume: Float) {
        if (track == null) {
            player?.takeIf { it.isPlaying }?.pause()
            return
        }
        if (track != activeTrack) {
            player?.release()
            player = null
            activeTrack = track
        }
        val active = player
            ?: MediaPlayer.create(context, track.resource)
                ?.apply { isLooping = true }
                ?.also { player = it }
            ?: return
        active.setVolume(volume, volume)
        if (!active.isPlaying) active.start()
    }

    fun release() {
        player?.release()
        player = null
        activeTrack = null
    }
}
