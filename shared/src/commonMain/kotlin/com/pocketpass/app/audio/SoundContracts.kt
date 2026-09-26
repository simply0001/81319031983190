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

    // Gain on top of the user's sfx volume. The clips are mixed quiet, the
    // tab flicks and the back flick more so, and the notification chime
    // has to carry from a pocket; the player clamps the result at full
    // volume.
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
