package com.pocketpass.app.data.supabase

import com.pocketpass.app.data.supabase.dto.FriendRequestDto
import com.pocketpass.app.data.supabase.dto.FriendshipDto
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.FriendshipStatus
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile

/**
 * Merges accepted friendships with the account's pending friend requests into
 * the friends snapshot. Without the requests, the next sync after a request
 * was acknowledged reconciled the optimistic "request sent" row away and the
 * profile fell back to "Add Friend" until the request was answered.
 */
internal fun buildFriendSnapshot(
    accountId: UserId,
    friendships: List<FriendshipDto>,
    requests: List<FriendRequestDto>,
    profiles: Map<String, UserProfile>,
): List<Friend> {
    val me = accountId.value
    val accepted = friendships.mapNotNull { friendship ->
        val profile = profiles[friendship.peerOf(me)] ?: return@mapNotNull null
        Friend(
            ownerId = accountId,
            profile = profile,
            status = FriendshipStatus.Accepted,
            lastInteractionAt = parseSupabaseInstant(friendship.createdAt),
            isOnline = false,
        )
    }
    val acceptedIds = accepted.mapTo(hashSetOf()) { it.profile.userId.value }
    val pending = requests
        .filter { it.isPendingFor(me) }
        .mapNotNull { request ->
            val peerId = request.peerOf(me)
            if (peerId in acceptedIds) return@mapNotNull null
            val profile = profiles[peerId] ?: return@mapNotNull null
            Friend(
                ownerId = accountId,
                profile = profile,
                status = if (request.requesterId == me) {
                    FriendshipStatus.PendingOutgoing
                } else {
                    FriendshipStatus.PendingIncoming
                },
                lastInteractionAt = parseSupabaseInstant(request.createdAt),
                isOnline = false,
            )
        }
        .distinctBy { it.profile.userId }
    return (accepted + pending).sortedWith(
        compareByDescending<Friend> { it.isOnline }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.profile.displayName }
            .thenBy { it.profile.userId.value },
    )
}

/** Everyone a friends snapshot needs a profile for. */
internal fun friendSnapshotPeerIds(
    accountId: UserId,
    friendships: List<FriendshipDto>,
    requests: List<FriendRequestDto>,
): Set<String> {
    val me = accountId.value
    val ids = linkedSetOf<String>()
    friendships.mapTo(ids) { it.peerOf(me) }
    requests.filter { it.isPendingFor(me) }.mapTo(ids) { it.peerOf(me) }
    return ids
}

private const val PENDING_STATUS = "pending"

private fun FriendshipDto.peerOf(me: String): String = if (userLow == me) userHigh else userLow

private fun FriendRequestDto.peerOf(me: String): String =
    if (requesterId == me) addresseeId else requesterId

private fun FriendRequestDto.isPendingFor(me: String): Boolean =
    status == PENDING_STATUS && (requesterId == me || addresseeId == me)
