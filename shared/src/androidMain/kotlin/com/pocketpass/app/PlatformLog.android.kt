package com.pocketpass.app

import android.util.Log

internal actual fun logPlatformWarning(tag: String, message: String) {
    Log.w(tag, message)
}

internal actual fun logPlatformInfo(tag: String, message: String) {
    Log.i(tag, message)
}

internal actual fun logPlatformDebug(tag: String, message: String) {
    Log.d(tag, message)
}
