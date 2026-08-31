package com.pocketpass.app.audio

import android.content.Context
import android.media.MediaPlayer
import androidx.annotation.RawRes
import com.pocketpass.app.R

@get:RawRes
private val BackgroundMusicTrack.resource: Int
    get() = when (this) {
        BackgroundMusicTrack.Home -> R.raw.bgm_main
        BackgroundMusicTrack.MiiMaker -> R.raw.bgm_mii
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
