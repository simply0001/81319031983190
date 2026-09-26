package com.pocketpass.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pocketpass.app.data.local.PocketPassDatabase
import com.pocketpass.app.data.local.toEntity
import com.pocketpass.app.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Instant

class BioDraftPersistenceTest {
    @Test fun rejectedBioRetainsAcceptedProfileAndRecoverableAttemptWithoutOverwritingANewerEdit() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val name="bio-draft-${java.util.UUID.randomUUID()}.db"
        var db=Room.databaseBuilder(context,PocketPassDatabase::class.java,name).build()
        val user=UserId("bio-owner");val now=Instant.fromEpochMilliseconds(1000)
        val profile=UserProfile(user,"Owner",null,bio="Last accepted bio",updatedAt=now)
        val rejected=UpdateProfileCommand(user,profile.copy(bio="Attempted phrase"),changedAt=now)
        try {
            db.profileDao().upsert(profile.toEntity())
            ProductionMutationStore(db).enqueueProfileUpdate(rejected)
            assertEquals(profile.bio,db.profileDao().get(user.value)!!.bio)
            assertEquals("Attempted phrase",db.profileDao().bioDraft(user.value)!!.draft)
            RoomRepositoryReconciler(db).reconcileBioResult(rejected,profile.bio,"Choose another phrase")
            db.close();db=Room.databaseBuilder(context,PocketPassDatabase::class.java,name).build()
            assertEquals(profile.bio,db.profileDao().get(user.value)!!.bio)
            assertEquals("Choose another phrase",db.profileDao().bioDraft(user.value)!!.error)
            val newer=rejected.copy(profile=profile.copy(bio="A new attempt"),clientOperationId=ClientOperationId.new())
            ProductionMutationStore(db).enqueueProfileUpdate(newer)
            RoomRepositoryReconciler(db).reconcileBioResult(rejected,profile.bio,"Old rejection")
            assertEquals("A new attempt",db.profileDao().bioDraft(user.value)!!.draft)
            assertNull(db.profileDao().bioDraft(user.value)!!.error)
            RoomRepositoryReconciler(db).reconcileBioResult(rejected,profile.bio)
            assertNotNull(db.profileDao().bioDraft(user.value))
            RoomRepositoryReconciler(db).reconcileBioResult(newer,newer.profile.bio)
            assertNull(db.profileDao().bioDraft(user.value))
        } finally { db.close();context.deleteDatabase(name) }
    }
}
