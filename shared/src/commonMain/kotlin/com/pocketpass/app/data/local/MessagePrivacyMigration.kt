package com.pocketpass.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal const val messagePrivacyMigrationSql = "ALTER TABLE profiles ADD COLUMN blockMessages INTEGER NOT NULL DEFAULT 0"

val MessagePrivacyMigration19To20: Migration = object : Migration(19, 20) {
    override fun migrate(connection: SQLiteConnection) { connection.execSQL(messagePrivacyMigrationSql) }
}
