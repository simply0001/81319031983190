package com.pocketpass.app.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.pocketpass.app.data.local.PocketPassDatabase

internal suspend fun <T> PocketPassDatabase.withWriterTransaction(
    block: suspend () -> T,
): T = useWriterConnection { transactor ->
    transactor.immediateTransaction { block() }
}
