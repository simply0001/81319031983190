package com.pocketpass.app

internal expect fun logPlatformWarning(tag: String, message: String)

internal expect fun logPlatformInfo(tag: String, message: String)

internal expect fun logPlatformDebug(tag: String, message: String)
