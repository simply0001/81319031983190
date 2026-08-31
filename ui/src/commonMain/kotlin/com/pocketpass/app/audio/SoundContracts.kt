package com.pocketpass.app.audio

import androidx.compose.runtime.staticCompositionLocalOf

val LocalSoundEffects = staticCompositionLocalOf<SoundEffectSink> { NoSoundEffects }
