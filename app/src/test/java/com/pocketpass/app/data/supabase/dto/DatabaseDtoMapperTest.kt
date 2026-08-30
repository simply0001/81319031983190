package com.pocketpass.app.data.supabase.dto

import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.ConversationMemberRole
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

class DatabaseDtoMapperTest {
    @Test
    fun profileMapsPublicFieldsWithoutLeakingStorageImplementation() {
        val dto = ProfileDto(
            userId = USER_ID,
            username = "pocket_user",
            displayName = "Pocket User",
            bio = "Hello!",
            avatarPath = "$USER_ID/avatar.png",
            age = 22,
            countryCode = "SE",
            lastSeenAt = "2026-07-27T00:30:00Z",
            createdAt = "2026-07-26T00:00:00Z",
            updatedAt = "2026-07-27T00:00:00Z",
        )

        val profile = dto.toDomain { path -> "https://signed.example/$path" }

        assertEquals(UserId(USER_ID), profile.userId)
        assertEquals("pocket_user", profile.username)
        assertEquals("Pocket User", profile.displayName)
        assertEquals("Hello!", profile.bio)
        assertEquals(22, profile.age)
        assertEquals("SE", profile.countryCode)
        assertEquals(Instant.parse("2026-07-27T00:30:00Z"), profile.lastSeenAt)
        assertEquals(
            AvatarReference.Remote("https://signed.example/$USER_ID/avatar.png"),
            profile.avatar,
        )
    }

    @Test
    fun conversationMemberMapsRoleJoinedAtAndProfile() {
        val dto = ConversationMemberDto(
            conversationId = "0f660d77-2c61-4615-ac04-bce9c20620dd",
            userId = USER_ID,
            role = "owner",
            joinedAt = "2026-08-29T10:00:00Z",
        )
        val profile = UserProfile(
            userId = UserId(USER_ID),
            displayName = "Pocket User",
            avatar = AvatarReference.Remote("https://signed.example/$USER_ID/avatar.png"),
            updatedAt = Instant.parse("2026-07-27T00:00:00Z"),
        )

        val member = dto.toDomain(profile)
        val unknown = dto.copy(role = "member").toDomain(null)

        assertEquals(UserId(USER_ID), member.userId)
        assertEquals(ConversationMemberRole.Owner, member.role)
        assertEquals("Pocket User", member.displayName)
        assertEquals(profile.avatar, member.avatar)
        assertEquals(Instant.parse("2026-08-29T10:00:00Z"), member.joinedAt)
        assertEquals(ConversationMemberRole.Member, unknown.role)
        assertEquals("Member", unknown.displayName)
        assertEquals(null, unknown.avatar)
    }

    companion object {
        private const val USER_ID = "2de26930-cf7b-4a09-b85e-19df68d42f93"
    }
}
