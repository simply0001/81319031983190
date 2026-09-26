package com.pocketpass.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pocketpass.app.data.local.PocketPassDatabase
import com.pocketpass.app.data.local.toEntity
import com.pocketpass.app.data.local.toDomain
import com.pocketpass.app.data.repository.remote.ProfileRemoteDataSource
import com.pocketpass.app.domain.model.*
import com.pocketpass.app.domain.state.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Instant

class MessagePrivacyPersistenceTest {
    private val account = UserId("privacy-owner")
    private val now = Instant.fromEpochMilliseconds(1000)
    private val original = UserProfile(account, "Owner", null, bio = "Local bio", updatedAt = now)
    private class Remote(var profile: UserProfile) : ProfileRemoteDataSource {
        var fail = false
        override suspend fun setMessagePrivacy(command: SetMessagePrivacyCommand): RepositoryResult<UserProfile> {
            if (fail) return RepositoryResult.Failure(RepositoryFailure(RepositoryFailureKind.Offline, "Offline", retryable = true))
            profile = profile.copy(blockMessages = command.blocked, bio = "Server bio")
            return RepositoryResult.Success(profile)
        }
        override suspend fun setInvitesPrivacy(command: SetInvitesPrivacyCommand): RepositoryResult<UserProfile> {
            if (fail) return RepositoryResult.Failure(RepositoryFailure(RepositoryFailureKind.Offline, "Offline", retryable = true))
            profile = profile.copy(blockInvites = command.blocked)
            return RepositoryResult.Success(profile)
        }
        override suspend fun fetchProfile(userId: UserId) = RepositoryResult.Success(profile)
        override suspend fun updateProfile(command: UpdateProfileCommand) = error("Unused")
        override suspend fun completeAccountSetup(command: AccountSetupCommand) = error("Unused")
        override suspend fun renameProfile(command: RenameProfileCommand) = error("Unused")
        override suspend fun touchLastSeen() = error("Unused")
    }
    @Test fun serverConfirmedSettingSurvivesRestartAndFailureKeepsLastConfirmedValue() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "privacy-${java.util.UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, PocketPassDatabase::class.java, name).build()
        try {
            db.profileDao().upsert(original.toEntity())
            db.profileDao().upsert(original.copy(userId = UserId("other")).toEntity())
            val remote = Remote(original)
            fun repository() = RoomProfileRepository(db.profileDao(), remote, ProductionMutationStore(db), RoomRepositoryReconciler(db))
            assertTrue(repository().setMessagePrivacy(SetMessagePrivacyCommand(account, true)) is RepositoryResult.Success)
            assertTrue(repository().setInvitesPrivacy(SetInvitesPrivacyCommand(account, true)) is RepositoryResult.Success)
            assertTrue(db.profileDao().get(account.value)!!.toDomain().blockMessages)
            assertTrue(db.profileDao().get(account.value)!!.toDomain().blockInvites)
            assertEquals("Local bio", db.profileDao().get(account.value)!!.bio)
            assertFalse(db.profileDao().get("other")!!.blockMessages)
            db.close()
            db = Room.databaseBuilder(context, PocketPassDatabase::class.java, name).build()
            assertTrue(db.profileDao().get(account.value)!!.blockMessages)
            assertTrue(db.profileDao().get(account.value)!!.blockInvites)
            remote.fail = true
            assertTrue(repository().setMessagePrivacy(SetMessagePrivacyCommand(account, false)) is RepositoryResult.Failure)
            assertTrue(db.profileDao().get(account.value)!!.blockMessages)
            remote.fail = false
            assertTrue(repository().setMessagePrivacy(SetMessagePrivacyCommand(account, false)) is RepositoryResult.Success)
            assertFalse(db.profileDao().get(account.value)!!.blockMessages)
            assertTrue(db.profileDao().get(account.value)!!.blockInvites)
            assertTrue(repository().setInvitesPrivacy(SetInvitesPrivacyCommand(account, false)) is RepositoryResult.Success)
            assertFalse(db.profileDao().get(account.value)!!.blockInvites)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun privacyRefreshPreservesPendingBioAndColourChanges() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PocketPassDatabase::class.java).build()
        try {
            db.profileDao().upsert(original.toEntity())
            val store = ProductionMutationStore(db)
            store.enqueueProfileUpdate(UpdateProfileCommand(account, original.copy(bio = "Unsynced bio"), changedAt = now))
            store.enqueueChatColour(SetChatBubbleColourCommand(account, ChatBubbleColour.Pink, now))
            RoomRepositoryReconciler(db).reconcileProfile(account, original.copy(blockMessages = true))
            val cached = db.profileDao().get(account.value)!!
            assertTrue(cached.blockMessages)
            assertEquals("Local bio", cached.bio)
            assertEquals("Unsynced bio", db.profileDao().bioDraft(account.value)?.draft)
            assertEquals("pink", cached.chatBubbleColour)
        } finally { db.close() }
    }
}
