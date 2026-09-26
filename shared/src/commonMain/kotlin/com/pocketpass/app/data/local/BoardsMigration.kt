package com.pocketpass.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val boardsMigrationStatements = listOf(
    "CREATE TABLE IF NOT EXISTS board_records (accountId TEXT NOT NULL, kind TEXT NOT NULL, recordId TEXT NOT NULL, boardId TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(accountId, kind, recordId))",
    "CREATE INDEX IF NOT EXISTS index_board_records_accountId_boardId ON board_records(accountId, boardId)",
    "CREATE TABLE IF NOT EXISTS board_drafts (accountId TEXT NOT NULL, draftId TEXT NOT NULL, boardId TEXT NOT NULL, payload TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(accountId, draftId))",
    "CREATE TABLE IF NOT EXISTS profile_bio_drafts (accountId TEXT NOT NULL PRIMARY KEY, draft TEXT NOT NULL, acceptedBio TEXT NOT NULL, operationId TEXT NOT NULL, error TEXT)",
)
val BoardsMigration20To21: Migration = object : Migration(20, 21) {
    override fun migrate(connection: SQLiteConnection) { boardsMigrationStatements.forEach(connection::execSQL) }
}
