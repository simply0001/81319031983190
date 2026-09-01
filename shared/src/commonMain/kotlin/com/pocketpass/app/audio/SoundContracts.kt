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

    // Gain on top of the user's sfx volume; the tab flicks are mixed
    // noticeably quieter than the rest of the set.
    fun gain(): Float = when (this) {
        TabLeft, TabRight -> 1.3f
        else -> 1f
    }
}

interface SoundEffectSink {
    fun play(effect: SoundEffect)
}

object NoSoundEffects : SoundEffectSink {
    override fun play(effect: SoundEffect) = Unit
}
