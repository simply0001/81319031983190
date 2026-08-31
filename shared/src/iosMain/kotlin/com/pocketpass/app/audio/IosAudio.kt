@file:OptIn(ExperimentalForeignApi::class)

package com.pocketpass.app.audio

import kotlin.time.TimeSource
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

private fun bundledAudioUrl(name: String): NSURL? =
    NSBundle.mainBundle
        .pathForResource(name, ofType = "m4a", inDirectory = "audio")
        ?.let { NSURL.fileURLWithPath(it) }

private val SoundEffect.fileName: String
    get() = when (this) {
        SoundEffect.Keyboard -> "sfx_keyboard"
        SoundEffect.KeyboardBackspace -> "sfx_keyboard_backspace"
        SoundEffect.Cancel -> "sfx_cancel"
        SoundEffect.TabLeft -> "sfx_tab_left"
        SoundEffect.TabRight -> "sfx_tab_right"
        SoundEffect.Navigation -> "sfx_navigation"
        SoundEffect.Notification -> "sfx_notification"
        SoundEffect.MessageSent -> "sfx_message_sent"
        SoundEffect.MessageReceived -> "sfx_message_received"
        SoundEffect.Confirm -> "sfx_confirm"
    }

/** AVAudioPlayer-backed counterpart of the Android SoundPool player. */
class IosSoundEffectPlayer : SoundEffectSink {
    var volume: Float = 0f

    private val origin = TimeSource.Monotonic.markNow()
    private val players = mutableMapOf<SoundEffect, AVAudioPlayer?>()
    private val lastStartedAt = mutableMapOf<SoundEffect, Long>()

    private fun nowMillis(): Long = origin.elapsedNow().inWholeMilliseconds

    private fun player(effect: SoundEffect): AVAudioPlayer? =
        players.getOrPut(effect) {
            bundledAudioUrl(effect.fileName)
                ?.let { url -> AVAudioPlayer(contentsOfURL = url, error = null) }
                ?.also { it.prepareToPlay() }
        }

    override fun play(effect: SoundEffect) {
        val level = volume.coerceIn(0f, 1f)
        if (level <= 0f) return
        val now = nowMillis()
        val last = lastStartedAt[effect]
        if (last != null && now - last < MIN_REPEAT_INTERVAL_MILLIS) return
        val player = player(effect) ?: return
        lastStartedAt[effect] = now
        player.volume = level
        player.currentTime = 0.0
        player.play()
    }

    private companion object {
        const val MIN_REPEAT_INTERVAL_MILLIS = 60L
    }
}

private val BackgroundMusicTrack.fileName: String
    get() = when (this) {
        BackgroundMusicTrack.Home -> "bgm_main"
        BackgroundMusicTrack.MiiMaker -> "bgm_mii"
    }

/** Looping background music, mirroring the Android MediaPlayer behaviour. */
class IosBackgroundMusicPlayer {
    private var player: AVAudioPlayer? = null
    private var activeTrack: BackgroundMusicTrack? = null

    fun update(track: BackgroundMusicTrack?, volume: Float) {
        if (track == null) {
            player?.takeIf { it.playing }?.pause()
            return
        }
        if (track != activeTrack) {
            player?.stop()
            player = null
            activeTrack = track
        }
        val active = player
            ?: bundledAudioUrl(track.fileName)
                ?.let { url -> AVAudioPlayer(contentsOfURL = url, error = null) }
                ?.apply { numberOfLoops = -1 }
                ?.also { player = it }
            ?: return
        active.volume = volume.coerceIn(0f, 1f)
        if (!active.playing) active.play()
    }

    fun release() {
        player?.stop()
        player = null
        activeTrack = null
    }
}
