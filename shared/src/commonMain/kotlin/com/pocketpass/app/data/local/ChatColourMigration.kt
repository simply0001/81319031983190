package com.pocketpass.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val chatColourMigrationStatements = listOf(
    "ALTER TABLE profiles ADD COLUMN chatBubbleColour TEXT NOT NULL DEFAULT 'default'",
    "ALTER TABLE profiles ADD COLUMN chatColourOperationId TEXT",
    "ALTER TABLE profiles ADD COLUMN chatColourError TEXT",
)

val ChatColourMigration18To19: Migration = object : Migration(18, 19) {
    override fun migrate(connection: SQLiteConnection) {
        chatColourMigrationStatements.forEach(connection::execSQL)
    }
}
