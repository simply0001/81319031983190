package com.pocketpass.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val invitesPrivacyMigrationSql = listOf(
    "ALTER TABLE profiles ADD COLUMN blockInvites INTEGER NOT NULL DEFAULT 0",
    "UPDATE profiles SET blockInvites = blockMessages WHERE blockMessages = 1",
)

val InvitesPrivacyMigration21To22: Migration = object : Migration(21, 22) {
    override fun migrate(connection: SQLiteConnection) { invitesPrivacyMigrationSql.forEach(connection::execSQL) }
}
