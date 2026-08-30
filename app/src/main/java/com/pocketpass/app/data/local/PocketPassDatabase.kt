package com.pocketpass.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pocketpass.app.data.local.dao.AchievementDao
import com.pocketpass.app.data.local.dao.BingoDao
import com.pocketpass.app.data.local.dao.ConversationDao
import com.pocketpass.app.data.local.dao.ConversationMemberDao
import com.pocketpass.app.data.local.dao.FriendDao
import com.pocketpass.app.data.local.dao.FriendCodeDao
import com.pocketpass.app.data.local.dao.MessageDao
import com.pocketpass.app.data.local.dao.NotificationDao
import com.pocketpass.app.data.local.dao.NearbyEncounterDao
import com.pocketpass.app.data.local.dao.OutboxDao
import com.pocketpass.app.data.local.dao.ProfileDao
import com.pocketpass.app.data.local.dao.LeaderboardDao
import com.pocketpass.app.data.local.dao.ShopDao
import com.pocketpass.app.data.local.dao.WorldTourDao
import com.pocketpass.app.data.local.dao.SyncCursorDao
import com.pocketpass.app.data.local.entity.AchievementStateEntity
import com.pocketpass.app.data.local.entity.BingoCellEntity
import com.pocketpass.app.data.local.entity.ConversationEntity
import com.pocketpass.app.data.local.entity.ConversationMemberEntity
import com.pocketpass.app.data.local.entity.FriendEntity
import com.pocketpass.app.data.local.entity.FriendCodeEntity
import com.pocketpass.app.data.local.entity.LeaderboardEntryEntity
import com.pocketpass.app.data.local.entity.MessageEntity
import com.pocketpass.app.data.local.entity.NotificationEntity
import com.pocketpass.app.data.local.entity.NearbyCredentialEntity
import com.pocketpass.app.data.local.entity.NearbyEncounterEntity
import com.pocketpass.app.data.local.entity.OwnedShopItemEntity
import com.pocketpass.app.data.local.entity.PendingOperationEntity
import com.pocketpass.app.data.local.entity.ProfileEntity
import com.pocketpass.app.data.local.entity.ShopCategoryEntity
import com.pocketpass.app.data.local.entity.ShopItemEntity
import com.pocketpass.app.data.local.entity.SupporterStatusEntity
import com.pocketpass.app.data.local.entity.SyncCursorEntity
import com.pocketpass.app.data.local.entity.TokenBalanceEntity
import com.pocketpass.app.data.local.entity.WorldTourRegionEntity

@Database(
    entities = [
        ProfileEntity::class,
        FriendEntity::class,
        FriendCodeEntity::class,
        NotificationEntity::class,
        ConversationEntity::class,
        ConversationMemberEntity::class,
        MessageEntity::class,
        NearbyEncounterEntity::class,
        NearbyCredentialEntity::class,
        PendingOperationEntity::class,
        SyncCursorEntity::class,
        ShopCategoryEntity::class,
        ShopItemEntity::class,
        TokenBalanceEntity::class,
        OwnedShopItemEntity::class,
        SupporterStatusEntity::class,
        LeaderboardEntryEntity::class,
        AchievementStateEntity::class,
        WorldTourRegionEntity::class,
        BingoCellEntity::class,
    ],
    version = 16,
    exportSchema = true,
)
abstract class PocketPassDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun friendDao(): FriendDao
    abstract fun friendCodeDao(): FriendCodeDao
    abstract fun notificationDao(): NotificationDao
    abstract fun conversationDao(): ConversationDao
    abstract fun conversationMemberDao(): ConversationMemberDao
    abstract fun messageDao(): MessageDao
    abstract fun shopDao(): ShopDao
    abstract fun leaderboardDao(): LeaderboardDao
    abstract fun achievementDao(): AchievementDao
    abstract fun worldTourDao(): WorldTourDao
    abstract fun bingoDao(): BingoDao
    abstract fun nearbyEncounterDao(): NearbyEncounterDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncCursorDao(): SyncCursorDao

    companion object {
        const val DEFAULT_NAME = "pocketpass.db"

        fun build(
            context: Context,
            name: String = DEFAULT_NAME,
        ): PocketPassDatabase = Room.databaseBuilder(
            context.applicationContext,
            PocketPassDatabase::class.java,
            name,
        )
            .addMigrations(
                Migration1To2,
                Migration2To3,
                Migration3To4,
                Migration4To5,
                Migration5To6,
                Migration6To7,
                Migration7To8,
                Migration8To9,
                Migration9To10,
                Migration10To11,
                Migration11To12,
                Migration12To13,
                Migration13To14,
                Migration14To15,
                Migration15To16,
            )
            .build()

        val Migration1To2: Migration = object : Migration(1, 2) {
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

        val Migration2To3: Migration = object : Migration(2, 3) {
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

        val Migration3To4: Migration = object : Migration(3, 4) {
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

        val Migration4To5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN attachmentPath TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN attachmentMime TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN attachmentLocalPath TEXT")
            }
        }

        val Migration5To6: Migration = object : Migration(5, 6) {
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

        val Migration6To7: Migration = object : Migration(6, 7) {
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

        val Migration7To8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE profiles ADD COLUMN username TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        val Migration8To9: Migration = object : Migration(8, 9) {
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

        val Migration9To10: Migration = object : Migration(9, 10) {
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

        val Migration10To11: Migration = object : Migration(10, 11) {
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

        val Migration11To12: Migration = object : Migration(11, 12) {
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

        val Migration12To13: Migration = object : Migration(12, 13) {
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

        val Migration13To14: Migration = object : Migration(13, 14) {
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

        val Migration14To15: Migration = object : Migration(14, 15) {
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

        val Migration15To16: Migration = object : Migration(15, 16) {
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
    }
}
