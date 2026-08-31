package com.pocketpass.app

internal actual fun logPlatformWarning(tag: String, message: String) {
    println("W/$tag: $message")
}

internal actual fun logPlatformInfo(tag: String, message: String) {
    println("I/$tag: $message")
}

internal actual fun logPlatformDebug(tag: String, message: String) {
    println("D/$tag: $message")
}
