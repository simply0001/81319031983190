package com.pocketpass.app.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
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
@ConstructedBy(PocketPassDatabaseConstructor::class)
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
    }
}

@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object PocketPassDatabaseConstructor : RoomDatabaseConstructor<PocketPassDatabase> {
    override fun initialize(): PocketPassDatabase
}
