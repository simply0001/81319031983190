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
    ;

    fun gain(): Float = when (this) {
        Notification -> 2f
        TabLeft, TabRight -> 1.87f
        Cancel -> 1.6f
        else -> 1.2f
    }
}

interface SoundEffectSink {
    fun play(effect: SoundEffect)
}

object NoSoundEffects : SoundEffectSink {
    override fun play(effect: SoundEffect) = Unit
}
