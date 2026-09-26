package com.pocketpass.app.data.supabase

import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.data.supabase.dto.FriendRequestDto
import com.pocketpass.app.data.supabase.dto.FriendshipDto
import com.pocketpass.app.domain.model.FriendshipStatus
import com.pocketpass.app.domain.model.UserId
import kotlin.test.Test
import kotlin.test.assertEquals

class FriendSnapshotTest {
    private val me = UserId("00000000-0000-4000-8000-00000000000a")
    private val friend = FixtureData.spobProfile.copy(userId = UserId("00000000-0000-4000-8000-00000000000b"))
    private val invited = FixtureData.spobProfile.copy(userId = UserId("00000000-0000-4000-8000-00000000000c"))
    private val inviter = FixtureData.spobProfile.copy(userId = UserId("00000000-0000-4000-8000-00000000000d"))
    private val profiles = listOf(friend, invited, inviter).associateBy { it.userId.value }

    private val friendships = listOf(
        FriendshipDto(userLow = me.value, userHigh = friend.userId.value, createdBy = me.value, createdAt = "2026-09-01T10:00:00+00:00"),
    )
    private val requests = listOf(
        FriendRequestDto("r1", requesterId = me.value, addresseeId = invited.userId.value, status = "pending", clientOperationId = "op1", createdAt = "2026-09-03T17:46:31+00:00"),
        FriendRequestDto("r2", requesterId = inviter.userId.value, addresseeId = me.value, status = "pending", clientOperationId = "op2", createdAt = "2026-09-03T18:00:00+00:00"),
        FriendRequestDto("r3", requesterId = me.value, addresseeId = friend.userId.value, status = "accepted", clientOperationId = "op3", createdAt = "2026-08-30T09:00:00+00:00", respondedAt = "2026-09-01T10:00:00+00:00"),
        FriendRequestDto("r4", requesterId = "someone-else", addresseeId = "another", status = "pending", clientOperationId = "op4", createdAt = "2026-09-03T19:00:00+00:00"),
    )

    @Test
    fun pendingRequestsJoinTheSnapshotWithTheirDirection() {
        val snapshot = buildFriendSnapshot(me, friendships, requests, profiles)

        assertEquals(
            mapOf(
                friend.userId to FriendshipStatus.Accepted,
                invited.userId to FriendshipStatus.PendingOutgoing,
                inviter.userId to FriendshipStatus.PendingIncoming,
            ),
            snapshot.associate { it.profile.userId to it.status },
        )
    }

    @Test
    fun anAcceptedFriendshipWinsOverAStaleRequest() {
        val stale = FriendRequestDto("r5", requesterId = friend.userId.value, addresseeId = me.value, status = "pending", clientOperationId = "op5", createdAt = "2026-09-02T09:00:00+00:00")
        val snapshot = buildFriendSnapshot(me, friendships, requests + stale, profiles)

        assertEquals(1, snapshot.count { it.profile.userId == friend.userId })
        assertEquals(FriendshipStatus.Accepted, snapshot.single { it.profile.userId == friend.userId }.status)
    }

    @Test
    fun peerIdsCoverFriendsAndPendingRequestsOnly() {
        assertEquals(
            setOf(friend.userId.value, invited.userId.value, inviter.userId.value),
            friendSnapshotPeerIds(me, friendships, requests),
        )
    }
}
