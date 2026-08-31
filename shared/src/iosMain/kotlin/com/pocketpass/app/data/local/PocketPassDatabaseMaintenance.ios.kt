package com.pocketpass.app.data.local

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

// Room's clearAllTables is Android-only; this walks sqlite_master instead,
// with foreign keys deferred the same way clearAllTables defers them.
suspend fun PocketPassDatabase.clearAllPocketPassTables() {
    useWriterConnection { transactor ->
        transactor.immediateTransaction {
            usePrepared("PRAGMA defer_foreign_keys = TRUE") { it.step() }
            val tables = mutableListOf<String>()
            usePrepared(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'",
            ) { statement ->
                while (statement.step()) {
                    tables += statement.getText(0)
                }
            }
            tables.forEach { table ->
                usePrepared("DELETE FROM \"$table\"") { it.step() }
            }
        }
    }
}
