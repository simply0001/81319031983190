package com.pocketpass.app.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PocketPassDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "pocketpass-migration-${System.nanoTime()}.db"
    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun cleanUp() {
        helper?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationOneToTwoCreatesUsableActivitySnapshotTable() {
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )
        val database = requireNotNull(helper).writableDatabase

        PocketPassDatabase.Migration1To2.migrate(database)
        database.execSQL(
            """
            INSERT INTO activity_snapshots (
                accountId,
                coinCount,
                puzzleCount,
                nearbyCount,
                locationCount,
                updatedAtEpochMillis
            ) VALUES ('account-one', 22, 3, 12, 3, 1785100000000)
            """.trimIndent(),
        )

        database.query(
            "SELECT accountId, coinCount, puzzleCount, nearbyCount, locationCount " +
                "FROM activity_snapshots WHERE accountId = 'account-one'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("account-one", cursor.getString(0))
            assertEquals(22, cursor.getInt(1))
            assertEquals(3, cursor.getInt(2))
            assertEquals(12, cursor.getInt(3))
            assertEquals(3, cursor.getInt(4))
        }
    }

    @Test
    fun migrationTwoToThreeCreatesFriendCodeAndNotificationTables() {
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(2) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )
        val database = requireNotNull(helper).writableDatabase

        PocketPassDatabase.Migration2To3.migrate(database)
        database.execSQL(
            """
            INSERT INTO friend_codes (accountId, code, updatedAtEpochMillis)
            VALUES ('account-one', '00123456', 1785100000000)
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO notifications (
                accountId,
                notificationId,
                kind,
                actorUserId,
                actorDisplayName,
                actorAvatarKind,
                actorAvatarValue,
                actorUpdatedAtEpochMillis,
                friendRequestId,
                friendRequestStatus,
                conversationId,
                title,
                body,
                eventCount,
                createdAtEpochMillis,
                updatedAtEpochMillis,
                readAtEpochMillis,
                deletedAtEpochMillis
            ) VALUES (
                'account-one',
                'notification-one',
                'System',
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                'PocketPass',
                'Inbox ready',
                1,
                1785100000000,
                1785100000000,
                NULL,
                NULL
            )
            """.trimIndent(),
        )

        database.query(
            "SELECT code FROM friend_codes WHERE accountId = 'account-one'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("00123456", cursor.getString(0))
        }
        database.query(
            "SELECT title, eventCount FROM notifications " +
                "WHERE notificationId = 'notification-one'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("PocketPass", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }
    }

    @Test
    fun migrationThirteenToFourteenAddsHatTypesAndOwnedItems() {
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(13) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )
        val database = requireNotNull(helper).writableDatabase
        database.execSQL(
            """
            CREATE TABLE shop_items (
                itemId TEXT NOT NULL,
                categoryId TEXT NOT NULL,
                slug TEXT NOT NULL,
                name TEXT NOT NULL,
                priceTokens INTEGER NOT NULL,
                imageKey TEXT NOT NULL,
                sortOrder INTEGER NOT NULL,
                PRIMARY KEY(itemId)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "INSERT INTO shop_items VALUES " +
                "('item-cap', 'hats', 'baseball_cap', 'Baseball Cap', 20, 'shop_item_baseball_cap', 0)",
        )

        PocketPassDatabase.Migration13To14.migrate(database)
        database.execSQL("UPDATE shop_items SET miiHatType = 0 WHERE itemId = 'item-cap'")
        database.execSQL(
            "INSERT INTO owned_shop_items " +
                "(accountId, itemId, pricePaid, purchasedAtEpochMillis, pendingOperationId) " +
                "VALUES ('account-one', 'item-cap', 20, 1, NULL)",
        )

        database.query("SELECT miiHatType FROM shop_items WHERE itemId = 'item-cap'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        database.query(
            "SELECT pricePaid FROM owned_shop_items WHERE accountId = 'account-one'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(20, cursor.getInt(0))
        }
    }

    @Test
    fun migrationFourteenToFifteenCreatesSupporterStatusTable() {
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(14) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )
        val database = requireNotNull(helper).writableDatabase

        PocketPassDatabase.Migration14To15.migrate(database)
        database.execSQL(
            "INSERT INTO supporter_status (accountId, activeUntilEpochMillis) " +
                "VALUES ('account-one', 1785100000000)",
        )
        database.execSQL(
            "INSERT OR REPLACE INTO supporter_status (accountId, activeUntilEpochMillis) " +
                "VALUES ('account-one', 1785200000000)",
        )

        database.query(
            "SELECT activeUntilEpochMillis FROM supporter_status WHERE accountId = 'account-one'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1785200000000L, cursor.getLong(0))
            assertEquals(1, cursor.count)
        }
    }

    @Test
    fun migrationFifteenToSixteenAddsConversationKindAndMembersTable() {
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(15) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )
        val database = requireNotNull(helper).writableDatabase
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS conversations (
                accountId TEXT NOT NULL,
                conversationId TEXT NOT NULL,
                title TEXT NOT NULL,
                avatarKind TEXT,
                avatarValue TEXT,
                latestMessagePreview TEXT NOT NULL,
                latestMessageAtEpochMillis INTEGER,
                unreadCount INTEGER NOT NULL,
                PRIMARY KEY(accountId, conversationId)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "INSERT INTO conversations (accountId, conversationId, title, latestMessagePreview, unreadCount) " +
                "VALUES ('account-one', 'spob', 'spob', 'hi', 0)",
        )

        PocketPassDatabase.Migration15To16.migrate(database)

        database.query("SELECT kind FROM conversations WHERE conversationId = 'spob'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Direct", cursor.getString(0))
        }
        database.execSQL(
            "INSERT INTO conversation_members (accountId, conversationId, userId, displayName, role, joinedAtEpochMillis) " +
                "VALUES ('account-one', 'crew', 'spob', 'spob', 'Member', 1785100000000)",
        )
        database.execSQL(
            "INSERT OR REPLACE INTO conversation_members (accountId, conversationId, userId, displayName, role, joinedAtEpochMillis) " +
                "VALUES ('account-one', 'crew', 'spob', 'spob', 'Owner', 1785200000000)",
        )
        database.query(
            "SELECT role FROM conversation_members WHERE accountId = 'account-one' AND conversationId = 'crew'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Owner", cursor.getString(0))
            assertEquals(1, cursor.count)
        }
        database.query("PRAGMA index_list('conversation_members')").use { cursor ->
            val names = generateSequence { if (cursor.moveToNext()) cursor.getString(1) else null }.toList()
            assertTrue(names.contains("index_conversation_members_accountId_conversationId"))
        }
    }
}
