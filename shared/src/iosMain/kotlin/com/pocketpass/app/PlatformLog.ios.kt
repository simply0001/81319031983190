package com.pocketpass.app

internal actual fun logPlatformWarning(tag: String, message: String) {
    println("W/$tag: $message")
}
