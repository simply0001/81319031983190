package com.pocketpass.app.domain.repository

import com.pocketpass.app.domain.model.AccountSetupCommand
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.RemoveFriendCommand
import com.pocketpass.app.domain.model.RenameProfileCommand
import com.pocketpass.app.domain.model.RespondToFriendRequestCommand
import com.pocketpass.app.domain.model.SendFriendRequestCommand
import com.pocketpass.app.domain.model.SetProfileAgeCommand
import com.pocketpass.app.domain.model.SetProfileCountryCommand
import com.pocketpass.app.domain.model.SetUserBlockCommand
import com.pocketpass.app.domain.model.UpdateProfileCommand
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.state.RepositoryResult

interface MutableProfileRepository : ProfileRepository {
    suspend fun setInvitesPrivacy(command: com.pocketpass.app.domain.model.SetInvitesPrivacyCommand): RepositoryResult<UserProfile> =
        RepositoryResult.Failure(com.pocketpass.app.domain.state.RepositoryFailure(
            com.pocketpass.app.domain.state.RepositoryFailureKind.NotFound, "Invitation privacy is unavailable", retryable = false))
    fun observeBioDraft(accountId: com.pocketpass.app.domain.model.UserId): kotlinx.coroutines.flow.Flow<com.pocketpass.app.domain.model.BioSaveDraft?> = kotlinx.coroutines.flow.flowOf(null)
    suspend fun setMessagePrivacy(command: com.pocketpass.app.domain.model.SetMessagePrivacyCommand): RepositoryResult<UserProfile> =
        RepositoryResult.Failure(com.pocketpass.app.domain.state.RepositoryFailure(
            com.pocketpass.app.domain.state.RepositoryFailureKind.NotFound, "Message privacy is unavailable", retryable = false))

    suspend fun setChatBubbleColour(command: com.pocketpass.app.domain.model.SetChatBubbleColourCommand): RepositoryResult<Unit> =
        RepositoryResult.Failure(com.pocketpass.app.domain.state.RepositoryFailure(
            kind = com.pocketpass.app.domain.state.RepositoryFailureKind.NotFound,
            message = "Chat colours are unavailable", retryable = false,
        ))

    suspend fun updateProfile(
        command: UpdateProfileCommand,
    ): RepositoryResult<UserProfile>

    suspend fun completeAccountSetup(
        command: AccountSetupCommand,
    ): RepositoryResult<UserProfile>

    suspend fun renameProfile(
        command: RenameProfileCommand,
    ): RepositoryResult<UserProfile>

    suspend fun setProfileAge(command: SetProfileAgeCommand): RepositoryResult<UserProfile> =
        RepositoryResult.Failure(com.pocketpass.app.domain.state.RepositoryFailure(
            com.pocketpass.app.domain.state.RepositoryFailureKind.NotFound, "Profile details are unavailable", retryable = false))

    suspend fun setProfileCountry(command: SetProfileCountryCommand): RepositoryResult<UserProfile> =
        RepositoryResult.Failure(com.pocketpass.app.domain.state.RepositoryFailure(
            com.pocketpass.app.domain.state.RepositoryFailureKind.NotFound, "Profile details are unavailable", retryable = false))
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
