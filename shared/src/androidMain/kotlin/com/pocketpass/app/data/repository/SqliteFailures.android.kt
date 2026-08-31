package com.pocketpass.app.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.SQLiteException

internal actual fun Throwable.isSqliteConstraintFailure(): Boolean =
    this is SQLiteConstraintException ||
        (this is SQLiteException && message.orEmpty().contains("constraint", ignoreCase = true))
