package com.pocketpass.app.domain.repository

import com.pocketpass.app.domain.model.AccountSetupCommand
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.RemoveFriendCommand
import com.pocketpass.app.domain.model.RenameProfileCommand
import com.pocketpass.app.domain.model.RespondToFriendRequestCommand
import com.pocketpass.app.domain.model.SendFriendRequestCommand
import com.pocketpass.app.domain.model.SetUserBlockCommand
import com.pocketpass.app.domain.model.UpdateProfileCommand
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.state.RepositoryResult

interface MutableProfileRepository : ProfileRepository {
    suspend fun updateProfile(
        command: UpdateProfileCommand,
    ): RepositoryResult<UserProfile>

    suspend fun completeAccountSetup(
        command: AccountSetupCommand,
    ): RepositoryResult<UserProfile>

    suspend fun renameProfile(
        command: RenameProfileCommand,
    ): RepositoryResult<UserProfile>
}

interface MutableFriendsRepository : FriendsRepository {
    suspend fun sendFriendRequest(
        command: SendFriendRequestCommand,
    ): RepositoryResult<Friend>

    suspend fun respondToFriendRequest(
        command: RespondToFriendRequestCommand,
    ): RepositoryResult<Friend?>

    suspend fun removeFriend(
        command: RemoveFriendCommand,
    ): RepositoryResult<Unit>

    suspend fun setUserBlocked(
        command: SetUserBlockCommand,
    ): RepositoryResult<Unit>
}
