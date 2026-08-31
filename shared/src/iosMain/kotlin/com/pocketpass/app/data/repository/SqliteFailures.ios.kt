package com.pocketpass.app.data.repository

import androidx.sqlite.SQLiteException

internal actual fun Throwable.isSqliteConstraintFailure(): Boolean =
    this is SQLiteException && message.orEmpty().contains("constraint", ignoreCase = true)
