package com.pocketpass.app

import android.util.Log

internal actual fun logPlatformWarning(tag: String, message: String) {
    Log.w(tag, message)
}
