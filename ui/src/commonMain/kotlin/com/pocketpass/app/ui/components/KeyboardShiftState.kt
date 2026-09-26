package com.pocketpass.app.ui.components

internal enum class KeyboardShiftMode { Off, Shift, CapsLock }

internal data class KeyboardShiftState(
    val mode: KeyboardShiftMode = KeyboardShiftMode.Off,
    private val lastTapMillis: Long? = null,
) {
    val uppercase: Boolean get() = mode != KeyboardShiftMode.Off

    fun tap(nowMillis: Long, doubleTapTimeoutMillis: Long): KeyboardShiftState = when {
        mode == KeyboardShiftMode.CapsLock -> KeyboardShiftState()
        mode == KeyboardShiftMode.Shift && lastTapMillis != null &&
            nowMillis - lastTapMillis in 0..doubleTapTimeoutMillis ->
            KeyboardShiftState(KeyboardShiftMode.CapsLock)
        mode == KeyboardShiftMode.Shift -> KeyboardShiftState()
        else -> KeyboardShiftState(KeyboardShiftMode.Shift, nowMillis)
    }

    fun afterKey(key: PocketKey): KeyboardShiftState = copy(
        mode = if (mode == KeyboardShiftMode.Shift && key is PocketKey.Character &&
            key.value.any(Char::isLetter)) KeyboardShiftMode.Off else mode,
        lastTapMillis = null,
    )

    fun cancelDoubleTap(): KeyboardShiftState = copy(lastTapMillis = null)
}
