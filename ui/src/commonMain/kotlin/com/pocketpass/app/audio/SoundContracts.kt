package com.pocketpass.app.audio

import androidx.compose.runtime.staticCompositionLocalOf

enum class SoundEffect {
    Keyboard,
    KeyboardBackspace,
    Cancel,
    TabLeft,
    TabRight,
    Navigation,
    Notification,
    MessageSent,
    MessageReceived,
    Confirm,
}

interface SoundEffectSink {
    fun play(effect: SoundEffect)
}

object NoSoundEffects : SoundEffectSink {
    override fun play(effect: SoundEffect) = Unit
}

val LocalSoundEffects = staticCompositionLocalOf<SoundEffectSink> { NoSoundEffects }
