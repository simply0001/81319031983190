package com.pocketpass.app.audio

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
