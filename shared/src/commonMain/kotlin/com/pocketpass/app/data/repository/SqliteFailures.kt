package com.pocketpass.app.data.repository

internal expect fun Throwable.isSqliteConstraintFailure(): Boolean
