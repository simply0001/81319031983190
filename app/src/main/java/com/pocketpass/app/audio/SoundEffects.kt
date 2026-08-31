package com.pocketpass.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.annotation.RawRes
import androidx.compose.runtime.staticCompositionLocalOf
import com.pocketpass.app.R
import java.util.concurrent.ConcurrentHashMap

@get:RawRes
private val SoundEffect.resource: Int
    get() = when (this) {
        SoundEffect.Keyboard -> R.raw.sfx_keyboard
        SoundEffect.KeyboardBackspace -> R.raw.sfx_keyboard_backspace
        SoundEffect.Cancel -> R.raw.sfx_cancel
        SoundEffect.TabLeft -> R.raw.sfx_tab_left
        SoundEffect.TabRight -> R.raw.sfx_tab_right
        SoundEffect.Navigation -> R.raw.sfx_navigation
        SoundEffect.Notification -> R.raw.sfx_notification
        SoundEffect.MessageSent -> R.raw.sfx_message_sent
        SoundEffect.MessageReceived -> R.raw.sfx_message_received
        SoundEffect.Confirm -> R.raw.sfx_confirm
    }

class SoundEffectPlayer(context: Context) : SoundEffectSink {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val loaded: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val lastStartedAt = ConcurrentHashMap<SoundEffect, Long>()
    private val samples: Map<SoundEffect, Int>

    @Volatile
    var volume: Float = 0f

    @Volatile
    var foreground: Boolean = false

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded.add(sampleId)
        }
        samples = SoundEffect.entries.associateWith { effect ->
            pool.load(context, effect.resource, 1)
        }
    }

    override fun play(effect: SoundEffect) {
        val level = volume.coerceIn(0f, 1f)
        if (!foreground || level <= 0f) return
        val sample = samples[effect] ?: return
        if (sample !in loaded) return
        val now = SystemClock.elapsedRealtime()
        val last = lastStartedAt[effect]
        if (last != null && now - last < MIN_REPEAT_INTERVAL_MILLIS) return
        lastStartedAt[effect] = now
        pool.play(sample, level, level, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }

    private companion object {
        const val MIN_REPEAT_INTERVAL_MILLIS = 60L
    }
}
