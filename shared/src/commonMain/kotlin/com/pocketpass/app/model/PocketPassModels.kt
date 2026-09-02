package com.pocketpass.app.model

import com.pocketpass.app.auth.AuthEvent
import com.pocketpass.app.domain.model.MAX_GROUP_MEMBERS
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.model.isValidProfileName
import com.pocketpass.app.feature.AccountSetupEvent
import com.pocketpass.app.mii.MiiEditorEvent
import kotlinx.serialization.Serializable

@Serializable
enum class PocketPassDestination {
    Messages,
    Friends,
    Home,
    Activities,
    Settings,
}

enum class ActivityVariant {
    Default,
    Shuffled,
}

enum class HomeMood {
    Happy,
    Sad,
    Neutral,
    Party,
    Playful,
    Cool,
}

enum class ThemeMode {
    Light,
    System,
    Dark,
}

enum class RecentInteractionsSort(val key: String) {
    LatestEncounter("latest"),
    OldestEncounter("oldest"),
    NameAZ("name"),
}

enum class MessageComposerAction {
    Image,
    File,
}

enum class FriendsOverlay {
    None,
    AddFriend,
    Notifications,
}

enum class ShopItemStatus {
    Available,
    Unaffordable,
    Purchasing,
    Owned,
    Unlocked,
}

data class ShopUiState(
    val visible: Boolean = false,
    val categories: List<com.pocketpass.app.domain.model.ShopCategory> = emptyList(),
    val tokenBalance: Int = 0,
    val refreshError: String? = null,
    val ownedItemIds: Set<String> = emptySet(),
    val unlockedItemIds: Set<String> = emptySet(),
    val purchasingItemIds: Set<String> = emptySet(),
    val purchaseError: String? = null,
    val buyPromptItemId: String? = null,
) {
    val items: List<com.pocketpass.app.domain.model.ShopItem>
        get() = categories.flatMap { it.items }

    val buyPromptItem: com.pocketpass.app.domain.model.ShopItem?
        get() = buyPromptItemId?.let(::item)

    fun item(id: String): com.pocketpass.app.domain.model.ShopItem? =
        items.firstOrNull { it.id == id }

    fun statusOf(item: com.pocketpass.app.domain.model.ShopItem): ShopItemStatus = when {
        item.id in ownedItemIds -> ShopItemStatus.Owned
        item.id in unlockedItemIds -> ShopItemStatus.Unlocked
        item.id in purchasingItemIds -> ShopItemStatus.Purchasing
        item.priceTokens <= tokenBalance -> ShopItemStatus.Available
        else -> ShopItemStatus.Unaffordable
    }
}

enum class GameTarget {
    PuzzleSwap,
    Bingo,
    WorldTour,
}

data class GamesUiState(
    val visible: Boolean = false,
    val activeGame: GameTarget? = null,
    val bingoGoalIndex: Int? = null,
    val worldTourRegionsVisible: Boolean = false,
)

data class LeaderboardUiState(
    val visible: Boolean = false,
    val settingsVisible: Boolean = false,
    val scope: com.pocketpass.app.domain.model.LeaderboardScope =
        com.pocketpass.app.domain.model.LeaderboardScope.Friends,
    val entries: List<com.pocketpass.app.domain.model.LeaderboardEntry> = emptyList(),
    val refreshError: String? = null,
)

data class AchievementsUiState(
    val visible: Boolean = false,
    val achievements: List<com.pocketpass.app.domain.model.AchievementState> = emptyList(),
    val refreshError: String? = null,
)

data class ConnectedAppsUiState(
    val enabled: Boolean = false,
    val visible: Boolean = false,
    val loading: Boolean = false,
    val apps: List<com.pocketpass.app.domain.model.ConnectedApp> = emptyList(),
    val error: String? = null,
    val revokeClientId: String? = null,
    val revokeInProgress: Boolean = false,
    val revokeError: String? = null,
) {
    val revokeTarget: com.pocketpass.app.domain.model.ConnectedApp?
        get() = revokeClientId?.let { id -> apps.firstOrNull { it.clientId == id } }
}

data class OAuthConsentUiState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val request: com.pocketpass.app.domain.model.OAuthConsentRequest? = null,
    val error: String? = null,
    val deciding: Boolean = false,
)

data class WorldTourUiState(
    val regions: List<com.pocketpass.app.domain.model.WorldTourRegion> = emptyList(),
    val refreshError: String? = null,
)

data class BingoUiState(
    val cells: List<com.pocketpass.app.domain.model.BingoCell> = emptyList(),
    val refreshError: String? = null,
)

const val BIO_MAX_LENGTH = 50

data class BioEditorUiState(
    val visible: Boolean = false,
    val draft: String = "",
    val saving: Boolean = false,
    val error: String? = null,
)

data class NameEditorUiState(
    val visible: Boolean = false,
    val draft: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val errorShakeNonce: Int = 0,
) {
    val valid: Boolean
        get() = isValidProfileName(draft)
}

data class GroupComposerState(
    val title: String = "",
    val selectedMemberIds: Set<UserId> = emptySet(),
    val submitting: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = title.isNotBlank() && selectedMemberIds.isNotEmpty() && !submitting

    val remainingSlots: Int
        get() = (MAX_GROUP_MEMBERS - 1 - selectedMemberIds.size).coerceAtLeast(0)
}

enum class ProfileViewerSource {
    RecentInteraction,
    Friend,
}

enum class ProfileFriendRequestState {
    Hidden,
    Available,
    Sending,
    Pending,
    Friends,
    Unavailable,
    Failed,
}

data class ProfileViewerUiState(
    val selectedUserId: String? = null,
    val source: ProfileViewerSource? = null,
    val profile: UserProfile? = null,
    val isOnline: Boolean = false,
    val unavailable: Boolean = false,
    val friendRequestState: ProfileFriendRequestState =
        ProfileFriendRequestState.Hidden,
    val friendRequestError: String? = null,
    val stats: com.pocketpass.app.domain.model.FriendProfileStats? = null,
    val statsPending: Boolean = false,
    val actionInProgress: Boolean = false,
    val actionError: String? = null,
) {
    val visible: Boolean
        get() = selectedUserId != null && source != null
}

data class StatusInfo(
    val time: String = "12:46",
    val batteryPercent: Int = 99,
    val batteryCharging: Boolean = false,
    val wifiConnected: Boolean = true,
    val wifiSignalLevel: Int = 2,
)

sealed interface PocketPassEvent {
    data class Auth(val event: AuthEvent) : PocketPassEvent
    data class AccountSetup(val event: AccountSetupEvent) : PocketPassEvent
    data class Mii(val event: MiiEditorEvent) : PocketPassEvent
    data object OpenMiiEditor : PocketPassEvent
    data object OpenMiiSlots : PocketPassEvent
    data object CloseMiiSlots : PocketPassEvent
    data object OpenConnectedApps : PocketPassEvent
    data object CloseConnectedApps : PocketPassEvent
    data class OpenRevokeConnectedApp(val clientId: String) : PocketPassEvent
    data object CloseRevokeConnectedApp : PocketPassEvent
    data object ConfirmRevokeConnectedApp : PocketPassEvent
    data object DismissOAuthConsent : PocketPassEvent
    data object ApproveOAuthConsent : PocketPassEvent
    data object DenyOAuthConsent : PocketPassEvent
    data object OpenThemePicker : PocketPassEvent
    data object CloseThemePicker : PocketPassEvent
    data object ToggleSortMenu : PocketPassEvent
    data object CloseSortMenu : PocketPassEvent
    data class EditMiiSlot(val slot: Int) : PocketPassEvent
    data class OpenDeleteMiiSlot(val slot: Int) : PocketPassEvent
    data object CloseDeleteMiiSlot : PocketPassEvent
    data object ConfirmDeleteMiiSlot : PocketPassEvent
    data class SetActiveMiiSlot(val slot: Int) : PocketPassEvent
    data class SelectDestination(val destination: PocketPassDestination) : PocketPassEvent
    data class OpenMessage(val conversationId: String) : PocketPassEvent
    data class UpdateMessageDraft(val value: String) : PocketPassEvent
    data object SendMessage : PocketPassEvent
    data object ToggleMessageActions : PocketPassEvent
    data class RetryMessage(val messageId: String) : PocketPassEvent
    data class SelectMessageAction(val action: MessageComposerAction) : PocketPassEvent
    data class OpenMessageActions(val messageId: String) : PocketPassEvent
    data object CloseMessageActions : PocketPassEvent
    data object EditSelectedMessage : PocketPassEvent
    data object DeleteSelectedMessage : PocketPassEvent
    data object CancelMessageEdit : PocketPassEvent
    data object OpenNewGroup : PocketPassEvent
    data object CloseNewGroup : PocketPassEvent
    data class ToggleGroupMember(val userId: String) : PocketPassEvent
    data class UpdateGroupTitle(val value: String) : PocketPassEvent
    data object CreateGroup : PocketPassEvent
    data object OpenGroupInfo : PocketPassEvent
    data object CloseGroupInfo : PocketPassEvent
    data class AddGroupMembers(val userIds: List<String>) : PocketPassEvent
    data class RemoveGroupMember(val userId: String) : PocketPassEvent
    data object LeaveGroup : PocketPassEvent
    data class RenameGroup(val title: String) : PocketPassEvent
    data object DismissConversationNotice : PocketPassEvent
    data object Back : PocketPassEvent
    data object OpenShop : PocketPassEvent
    data object CloseShop : PocketPassEvent
    data class OpenBuyShopItem(val itemId: String) : PocketPassEvent
    data object CloseBuyShopItem : PocketPassEvent
    data object ConfirmBuyShopItem : PocketPassEvent
    data class WearShopItem(val itemId: String) : PocketPassEvent
    data object OpenGames : PocketPassEvent
    data object CloseGames : PocketPassEvent
    data class OpenGame(val game: GameTarget) : PocketPassEvent
    data class SelectBingoSquare(val index: Int) : PocketPassEvent
    data object CloseBingoSquare : PocketPassEvent
    data object OpenWorldTourRegions : PocketPassEvent
    data object CloseWorldTourRegions : PocketPassEvent
    data object OpenLeaderboard : PocketPassEvent
    data object CloseLeaderboard : PocketPassEvent
    data object OpenLeaderboardSettings : PocketPassEvent
    data object CloseLeaderboardSettings : PocketPassEvent
    data class SetLeaderboardScope(
        val scope: com.pocketpass.app.domain.model.LeaderboardScope,
    ) : PocketPassEvent
    data object OpenAchievements : PocketPassEvent
    data object CloseAchievements : PocketPassEvent
    data object ShuffleActivities : PocketPassEvent
    data object ToggleHomeMoodPicker : PocketPassEvent
    data class SelectHomeMood(val mood: HomeMood) : PocketPassEvent
    data object CloseHomeMoodPicker : PocketPassEvent
    data object OpenBioEditor : PocketPassEvent
    data class UpdateBioDraft(val value: String) : PocketPassEvent
    data object SaveBio : PocketPassEvent
    data object CloseBioEditor : PocketPassEvent
    data object OpenNameEditor : PocketPassEvent
    data class UpdateNameDraft(val value: String) : PocketPassEvent
    data object SaveName : PocketPassEvent
    data object CloseNameEditor : PocketPassEvent
    data class SetMessageBadgeText(val text: String) : PocketPassEvent
    data class SetNearby(val enabled: Boolean) : PocketPassEvent
    data object RequestNearbyPermissions : PocketPassEvent
    data object SkipNearbyPermissions : PocketPassEvent
    data class SetSoundLevel(val level: Float) : PocketPassEvent
    data class SetSfxLevel(val level: Float) : PocketPassEvent
    data class SetThemeMode(val mode: ThemeMode) : PocketPassEvent
    data class SetRecentInteractionsSort(
        val sort: RecentInteractionsSort,
    ) : PocketPassEvent
    data class SetFriendsSort(
        val sort: RecentInteractionsSort,
    ) : PocketPassEvent
    data object OpenAccessibility : PocketPassEvent
    data object OpenSocial : PocketPassEvent
    data object OpenContributors : PocketPassEvent
    data object OpenNotificationSettings : PocketPassEvent
    data object OpenAppUpdate : PocketPassEvent
    data object CheckForAppUpdate : PocketPassEvent
    data object DownloadAppUpdate : PocketPassEvent
    data object InstallAppUpdate : PocketPassEvent
    data class SetMoodEmojisEnabled(val enabled: Boolean) : PocketPassEvent
    data class SetEncounterLedEnabled(val enabled: Boolean) : PocketPassEvent
    data class SetEncounterAlertsEnabled(val enabled: Boolean) : PocketPassEvent
    data class SetNearbyRepairAlertsEnabled(val enabled: Boolean) : PocketPassEvent
    data class SetUpdateAlertsEnabled(val enabled: Boolean) : PocketPassEvent
    data class SetStepRewardsEnabled(val enabled: Boolean) : PocketPassEvent
    data object RequestStepRewardsPermission : PocketPassEvent
    data object ResetSettings : PocketPassEvent
    data object OpenDeleteAccount : PocketPassEvent
    data object CloseDeleteAccount : PocketPassEvent
    data object ConfirmDeleteAccount : PocketPassEvent
    data object SignOut : PocketPassEvent
    data object OpenAddFriend : PocketPassEvent
    data object RefreshFriends : PocketPassEvent
    data class OpenUserProfile(
        val userId: String,
        val source: ProfileViewerSource,
    ) : PocketPassEvent
    data object CloseUserProfile : PocketPassEvent
    data object SendProfileFriendRequest : PocketPassEvent
    data object RemoveProfileFriend : PocketPassEvent
    data object OpenRemoveFriend : PocketPassEvent
    data object CloseRemoveFriend : PocketPassEvent
    data object MessageProfileFriend : PocketPassEvent
    data object ToggleNotifications : PocketPassEvent
    data object CloseFriendsOverlay : PocketPassEvent
    data class UpdateFriendCode(val value: String) : PocketPassEvent
    data object SubmitFriendCode : PocketPassEvent
    data class OpenNotification(val notificationId: String) : PocketPassEvent
    data class RespondToNotificationFriendRequest(
        val notificationId: String,
        val accept: Boolean,
    ) : PocketPassEvent
    data class DeleteNotification(val notificationId: String) : PocketPassEvent
    data object MarkAllNotificationsRead : PocketPassEvent
    data object ClearAllNotifications : PocketPassEvent
    data class StatusChanged(val status: StatusInfo) : PocketPassEvent
}

sealed interface PocketPassExtensionTarget {
    data object HomeMore : PocketPassExtensionTarget
    data object Shop : PocketPassExtensionTarget
    data object Leaderboard : PocketPassExtensionTarget
    data object Notifications : PocketPassExtensionTarget
    data class MessageComposer(
        val conversationId: String,
        val action: MessageComposerAction,
    ) : PocketPassExtensionTarget
}

fun interface PocketPassExtensions {
    fun open(target: PocketPassExtensionTarget)

    companion object {
        val None = PocketPassExtensions { }
    }
}
