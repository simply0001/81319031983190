package com.pocketpass.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

fun PocketPassDatabase.Companion.build(
    context: Context,
    name: String = PocketPassDatabase.DEFAULT_NAME,
): PocketPassDatabase = Room.databaseBuilder(
    context.applicationContext,
    PocketPassDatabase::class.java,
    name,
)
    .addMigrations(
        PocketPassDatabase.Migration1To2,
        PocketPassDatabase.Migration2To3,
        PocketPassDatabase.Migration3To4,
        PocketPassDatabase.Migration4To5,
        PocketPassDatabase.Migration5To6,
        PocketPassDatabase.Migration6To7,
        PocketPassDatabase.Migration7To8,
        PocketPassDatabase.Migration8To9,
        PocketPassDatabase.Migration9To10,
        PocketPassDatabase.Migration10To11,
        PocketPassDatabase.Migration11To12,
        PocketPassDatabase.Migration12To13,
        PocketPassDatabase.Migration13To14,
        PocketPassDatabase.Migration14To15,
        PocketPassDatabase.Migration15To16,
    )
    .build()

val PocketPassDatabase.Companion.Migration1To2: Migration get() = migration1To2
val PocketPassDatabase.Companion.Migration2To3: Migration get() = migration2To3
val PocketPassDatabase.Companion.Migration3To4: Migration get() = migration3To4
val PocketPassDatabase.Companion.Migration4To5: Migration get() = migration4To5
val PocketPassDatabase.Companion.Migration5To6: Migration get() = migration5To6
val PocketPassDatabase.Companion.Migration6To7: Migration get() = migration6To7
val PocketPassDatabase.Companion.Migration7To8: Migration get() = migration7To8
val PocketPassDatabase.Companion.Migration8To9: Migration get() = migration8To9
val PocketPassDatabase.Companion.Migration9To10: Migration get() = migration9To10
val PocketPassDatabase.Companion.Migration10To11: Migration get() = migration10To11
val PocketPassDatabase.Companion.Migration11To12: Migration get() = migration11To12
val PocketPassDatabase.Companion.Migration12To13: Migration get() = migration12To13
val PocketPassDatabase.Companion.Migration13To14: Migration get() = migration13To14
val PocketPassDatabase.Companion.Migration14To15: Migration get() = migration14To15
val PocketPassDatabase.Companion.Migration15To16: Migration get() = migration15To16

private val migration1To2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS activity_snapshots (
                accountId TEXT NOT NULL PRIMARY KEY,
                coinCount INTEGER NOT NULL,
                puzzleCount INTEGER NOT NULL,
                nearbyCount INTEGER NOT NULL,
                locationCount INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

private val migration2To3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS friend_codes (
                accountId TEXT NOT NULL PRIMARY KEY,
                code TEXT NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notifications (
                accountId TEXT NOT NULL,
                notificationId TEXT NOT NULL,
                kind TEXT NOT NULL,
                actorUserId TEXT,
                actorDisplayName TEXT,
                actorAvatarKind TEXT,
                actorAvatarValue TEXT,
                actorUpdatedAtEpochMillis INTEGER,
                friendRequestId TEXT,
                friendRequestStatus TEXT,
                conversationId TEXT,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                eventCount INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                readAtEpochMillis INTEGER,
                deletedAtEpochMillis INTEGER,
                PRIMARY KEY(accountId, notificationId)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_notifications_accountId_updatedAtEpochMillis
            ON notifications(accountId, updatedAtEpochMillis)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_notifications_accountId_readAtEpochMillis
            ON notifications(accountId, readAtEpochMillis)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_notifications_friendRequestId
            ON notifications(friendRequestId)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_notifications_conversationId
            ON notifications(conversationId)
            """.trimIndent(),
        )
    }
}

private val migration3To4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS nearby_encounters (
                accountId TEXT NOT NULL,
                encounterId TEXT NOT NULL,
                remoteUserId TEXT NOT NULL,
                displayName TEXT NOT NULL,
                avatarKind TEXT,
                avatarValue TEXT,
                bio TEXT NOT NULL,
                age INTEGER,
                countryCode TEXT,
                locationLabel TEXT,
                lastSeenAtEpochMillis INTEGER,
                profileUpdatedAtEpochMillis INTEGER NOT NULL,
                occurredAtEpochMillis INTEGER NOT NULL,
                resolvedAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(accountId, encounterId)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_nearby_encounters_accountId_occurredAtEpochMillis
            ON nearby_encounters(accountId, occurredAtEpochMillis)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_nearby_encounters_accountId_remoteUserId
            ON nearby_encounters(accountId, remoteUserId)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS nearby_credentials (
                accountId TEXT NOT NULL,
                tokenHash TEXT NOT NULL,
                secureEntryKey TEXT NOT NULL,
                expiresAtEpochMillis INTEGER NOT NULL,
                claimedAtEpochMillis INTEGER,
                PRIMARY KEY(accountId, tokenHash)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_nearby_credentials_accountId_expiresAtEpochMillis
            ON nearby_credentials(accountId, expiresAtEpochMillis)
            """.trimIndent(),
        )
    }
}

private val migration4To5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN attachmentPath TEXT")
        db.execSQL("ALTER TABLE messages ADD COLUMN attachmentMime TEXT")
        db.execSQL("ALTER TABLE messages ADD COLUMN attachmentLocalPath TEXT")
    }
}

private val migration5To6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shop_categories (
                categoryId TEXT NOT NULL PRIMARY KEY,
                slug TEXT NOT NULL,
                title TEXT NOT NULL,
                subtitle TEXT NOT NULL,
                iconKey TEXT NOT NULL,
                sortOrder INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shop_items (
                itemId TEXT NOT NULL PRIMARY KEY,
                categoryId TEXT NOT NULL,
                slug TEXT NOT NULL,
                name TEXT NOT NULL,
                priceTokens INTEGER NOT NULL,
                imageKey TEXT NOT NULL,
                sortOrder INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_shop_items_categoryId_sortOrder
            ON shop_items (categoryId, sortOrder)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS token_balances (
                accountId TEXT NOT NULL PRIMARY KEY,
                balance INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

private val migration6To7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS leaderboard_entries (
                accountId TEXT NOT NULL,
                userId TEXT NOT NULL,
                displayName TEXT NOT NULL,
                avatarKind TEXT,
                avatarValue TEXT,
                trophyCount INTEGER NOT NULL,
                encounterCount INTEGER NOT NULL,
                position INTEGER NOT NULL,
                PRIMARY KEY(accountId, userId)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_leaderboard_entries_accountId_position
            ON leaderboard_entries (accountId, position)
            """.trimIndent(),
        )
    }
}

private val migration7To8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE profiles ADD COLUMN username TEXT NOT NULL DEFAULT ''",
        )
    }
}

private val migration8To9: Migration = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS achievement_states (
                accountId TEXT NOT NULL,
                achievementKey TEXT NOT NULL,
                unlocked INTEGER NOT NULL,
                unlockedAtEpochMillis INTEGER,
                progressPercent INTEGER NOT NULL,
                position INTEGER NOT NULL,
                PRIMARY KEY(accountId, achievementKey)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_achievement_states_accountId_position
            ON achievement_states (accountId, position)
            """.trimIndent(),
        )
    }
}

private val migration9To10: Migration = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS world_tour_regions (
                accountId TEXT NOT NULL,
                countryCode TEXT NOT NULL,
                firstMetAtEpochMillis INTEGER NOT NULL,
                position INTEGER NOT NULL,
                PRIMARY KEY(accountId, countryCode)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_world_tour_regions_accountId_position
            ON world_tour_regions (accountId, position)
            """.trimIndent(),
        )
    }
}

private val migration10To11: Migration = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bingo_cells (
                accountId TEXT NOT NULL,
                position INTEGER NOT NULL,
                goalText TEXT NOT NULL,
                shortLabel TEXT NOT NULL,
                PRIMARY KEY(accountId, position)
            )
            """.trimIndent(),
        )
    }
}

private val migration11To12: Migration = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS bingo_cells")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bingo_cells (
                accountId TEXT NOT NULL,
                position INTEGER NOT NULL,
                slug TEXT NOT NULL,
                goalText TEXT NOT NULL,
                shortLabel TEXT NOT NULL,
                completed INTEGER NOT NULL,
                progressCurrent INTEGER NOT NULL,
                progressTarget INTEGER NOT NULL,
                PRIMARY KEY(accountId, position)
            )
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE IF EXISTS activity_snapshots")
    }
}

private val migration12To13: Migration = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS leaderboard_entries")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS leaderboard_entries (
                accountId TEXT NOT NULL,
                scope TEXT NOT NULL,
                userId TEXT NOT NULL,
                displayName TEXT NOT NULL,
                avatarKind TEXT,
                avatarValue TEXT,
                trophyCount INTEGER NOT NULL,
                encounterCount INTEGER NOT NULL,
                position INTEGER NOT NULL,
                PRIMARY KEY(accountId, scope, userId)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_leaderboard_entries_accountId_scope_position
            ON leaderboard_entries (accountId, scope, position)
            """.trimIndent(),
        )
    }
}

private val migration13To14: Migration = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shop_items ADD COLUMN miiHatType INTEGER")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS owned_shop_items (
                accountId TEXT NOT NULL,
                itemId TEXT NOT NULL,
                pricePaid INTEGER NOT NULL,
                purchasedAtEpochMillis INTEGER NOT NULL,
                pendingOperationId TEXT,
                PRIMARY KEY(accountId, itemId)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_owned_shop_items_accountId_pendingOperationId
            ON owned_shop_items (accountId, pendingOperationId)
            """.trimIndent(),
        )
    }
}

private val migration14To15: Migration = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS supporter_status (
                accountId TEXT NOT NULL,
                activeUntilEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(accountId)
            )
            """.trimIndent(),
        )
    }
}

private val migration15To16: Migration = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE conversations ADD COLUMN kind TEXT NOT NULL DEFAULT 'Direct'",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS conversation_members (
                accountId TEXT NOT NULL,
                conversationId TEXT NOT NULL,
                userId TEXT NOT NULL,
                displayName TEXT NOT NULL,
                avatarKind TEXT,
                avatarValue TEXT,
                role TEXT NOT NULL,
                joinedAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(accountId, conversationId, userId)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_conversation_members_accountId_conversationId
            ON conversation_members (accountId, conversationId)
            """.trimIndent(),
        )
    }
}
