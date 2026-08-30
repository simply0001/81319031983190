package com.pocketpass.app.ui

import kotlin.time.Instant

internal fun Instant.toJavaInstant(): java.time.Instant =
    java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())
