package com.pocketpass.app.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

fun buildPocketPassDatabase(path: String): PocketPassDatabase =
    Room.databaseBuilder<PocketPassDatabase>(name = path)
        .addMigrations(ChatColourMigration18To19, MessagePrivacyMigration19To20, BoardsMigration20To21, InvitesPrivacyMigration21To22)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
