package com.pocketpass.app.state

import com.pocketpass.app.audio.SoundEffect
import com.pocketpass.app.feature.AccountSecurityEvent
import com.pocketpass.app.mii.MiiEditorEvent
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassEvent

fun soundEffectFor(
    event: PocketPassEvent,
    current: PocketPassDestination,
): SoundEffect? = when (event) {
    PocketPassEvent.Back,
    PocketPassEvent.CloseMiiSlots,
    PocketPassEvent.CloseConnectedApps,
    PocketPassEvent.CloseRevokeConnectedApp,
    PocketPassEvent.DismissOAuthConsent,
    PocketPassEvent.CloseSortMenu,
    PocketPassEvent.CloseDeleteMiiSlot,
    PocketPassEvent.CloseMessageActions,
    PocketPassEvent.CancelMessageEdit,
    PocketPassEvent.CloseShop,
    PocketPassEvent.CloseShopCategory,
    PocketPassEvent.CloseBuyShopItem,
    PocketPassEvent.CloseGames,
    PocketPassEvent.CloseBingoSquare,
    PocketPassEvent.CloseWorldTourRegions,
    PocketPassEvent.CloseBuyPuzzlePiece,
    PocketPassEvent.DismissPuzzleNotice,
    PocketPassEvent.ClosePuzzleInfo,
    PocketPassEvent.CloseLeaderboard,
    PocketPassEvent.CloseLeaderboardSettings,
    PocketPassEvent.CloseAchievements,
    PocketPassEvent.CloseHomeMoodPicker,
    PocketPassEvent.CloseBioEditor,
    PocketPassEvent.CloseNameEditor,
    PocketPassEvent.CloseDeleteAccount,
    PocketPassEvent.CloseUserProfile,
    PocketPassEvent.CloseRemoveFriend,
    PocketPassEvent.CloseFriendsOverlay,
    PocketPassEvent.CloseGroupInfo,
    PocketPassEvent.DismissConversationNotice,
    PocketPassEvent.CloseWidgetBlockPicker,
    PocketPassEvent.CloseWidgetDeletePrompt,
    PocketPassEvent.CloseWidgetRename,
    PocketPassEvent.DismissWidgetMessage,
    -> SoundEffect.Cancel

    PocketPassEvent.CreateGroup,
    is PocketPassEvent.RenameGroup,
    is PocketPassEvent.AddGroupMembers,
    is PocketPassEvent.RemoveGroupMember,
    PocketPassEvent.LeaveGroup,
    PocketPassEvent.ConfirmRevokeConnectedApp,
    PocketPassEvent.ApproveOAuthConsent,
    PocketPassEvent.ConfirmDeleteMiiSlot,
    PocketPassEvent.ConfirmBuyShopItem,
    PocketPassEvent.ConfirmBuyPuzzlePiece,
    is PocketPassEvent.WearShopItem,
    is PocketPassEvent.SetActiveMiiSlot,
    PocketPassEvent.SaveBio,
    PocketPassEvent.SaveName,
    PocketPassEvent.ConfirmDeleteAccount,
    PocketPassEvent.SendProfileFriendRequest,
    PocketPassEvent.SubmitFriendCode,
    PocketPassEvent.CreateWidgetDesign,
    is PocketPassEvent.PickWidgetBlock,
    PocketPassEvent.SaveWidgetName,
    is PocketPassEvent.PinWidgetDesign,
    is PocketPassEvent.AssignWidgetDesign,
    is PocketPassEvent.DeleteWidgetDesign,
    PocketPassEvent.ToggleSortMenu,
    PocketPassEvent.ToggleHomeMoodPicker,
    PocketPassEvent.ToggleNotifications,
    PocketPassEvent.EditSelectedMessage,
    PocketPassEvent.DeleteSelectedMessage,
    PocketPassEvent.PickMessageImage,
    is PocketPassEvent.ToggleGroupMember,
    is PocketPassEvent.AddGroupMemberFriend,
    is PocketPassEvent.EditMiiSlot,
    is PocketPassEvent.SelectBingoSquare,
    PocketPassEvent.ShuffleActivities,
    is PocketPassEvent.SelectHomeMood,
    is PocketPassEvent.SetGlobalLeaderboardLimit,
    is PocketPassEvent.SetLeaderboardScope,
    is PocketPassEvent.SetNearby,
    is PocketPassEvent.SetThemeMode,
    is PocketPassEvent.SetRecentInteractionsSort,
    is PocketPassEvent.SetFriendsSort,
    is PocketPassEvent.SetMessagePrivacy,
    is PocketPassEvent.SetInvitesPrivacy,
    is PocketPassEvent.SetBoardsVisible,
    is PocketPassEvent.SaveChatColour,
    is PocketPassEvent.SetMoodEmojisEnabled,
    is PocketPassEvent.SetEncounterLedEnabled,
    is PocketPassEvent.SetEncounterAlertsEnabled,
    is PocketPassEvent.SetNearbyRepairAlertsEnabled,
    is PocketPassEvent.SetUpdateAlertsEnabled,
    is PocketPassEvent.SetMessageAlertsEnabled,
    is PocketPassEvent.SetStepRewardsEnabled,
    PocketPassEvent.OpenChatColours,
    PocketPassEvent.OpenSocial,
    PocketPassEvent.OpenAppUpdate,
    PocketPassEvent.OpenAccountSecurity,
    PocketPassEvent.OpenAppSettings,
    PocketPassEvent.DownloadAppUpdate,
    PocketPassEvent.InstallAppUpdate,
    PocketPassEvent.SignOut,
    PocketPassEvent.RemoveProfileFriend,
    PocketPassEvent.MessageProfileFriend,
    PocketPassEvent.ClearAllNotifications,
    -> SoundEffect.Confirm

    is PocketPassEvent.AccountSecurity -> when (event.event) {
        AccountSecurityEvent.OpenLinkEmail,
        AccountSecurityEvent.SubmitEmail,
        AccountSecurityEvent.ContinueWithCode,
        AccountSecurityEvent.ResendCode,
        AccountSecurityEvent.OpenChangePassword,
        AccountSecurityEvent.TogglePasswordVisibility,
        AccountSecurityEvent.SubmitNewPassword,
        AccountSecurityEvent.DismissNotice,
        -> SoundEffect.Confirm
        AccountSecurityEvent.Back -> SoundEffect.Cancel
        else -> null
    }

    is PocketPassEvent.RespondToNotificationFriendRequest ->
        if (event.accept) SoundEffect.Confirm else SoundEffect.Cancel

    is PocketPassEvent.Mii -> when (event.event) {
        MiiEditorEvent.Save,
        MiiEditorEvent.LookupPretendoMii,
        MiiEditorEvent.ConfirmPretendoImport,
        -> SoundEffect.Confirm
        MiiEditorEvent.Cancel,
        MiiEditorEvent.RequestCancel,
        MiiEditorEvent.DismissDiscardPrompt,
        MiiEditorEvent.ClosePretendoImport,
        -> SoundEffect.Cancel
        MiiEditorEvent.OpenPretendoImport,
        is MiiEditorEvent.SelectPretendoImportSlot,
        -> SoundEffect.Navigation
        else -> null
    }

    is PocketPassEvent.SelectDestination -> {
        val entries = PocketPassDestination.entries
        val step = entries.indexOf(event.destination) - entries.indexOf(current)
        when {
            step > 0 -> SoundEffect.TabRight
            step < 0 -> SoundEffect.TabLeft
            else -> null
        }
    }

    PocketPassEvent.OpenMiiSlots,
    PocketPassEvent.OpenConnectedApps,
    is PocketPassEvent.OpenRevokeConnectedApp,
    PocketPassEvent.OpenThemePicker,
    is PocketPassEvent.OpenDeleteMiiSlot,
    is PocketPassEvent.OpenMessage,
    is PocketPassEvent.OpenMessageActions,
    PocketPassEvent.OpenShop,
    is PocketPassEvent.OpenShopCategory,
    is PocketPassEvent.OpenBuyShopItem,
    PocketPassEvent.OpenGames,
    is PocketPassEvent.OpenGame,
    PocketPassEvent.OpenWorldTourRegions,
    PocketPassEvent.OpenBuyPuzzlePiece,
    PocketPassEvent.PreviousPuzzle,
    PocketPassEvent.NextPuzzle,
    PocketPassEvent.OpenPuzzleInfo,
    PocketPassEvent.OpenLeaderboard,
    PocketPassEvent.OpenLeaderboardSettings,
    PocketPassEvent.OpenAchievements,
    PocketPassEvent.OpenBioEditor,
    PocketPassEvent.OpenNameEditor,
    PocketPassEvent.OpenAccessibility,
    PocketPassEvent.OpenContributors,
    PocketPassEvent.OpenNotificationSettings,
    PocketPassEvent.OpenWidgetMaker,
    is PocketPassEvent.OpenWidgetEditor,
    is PocketPassEvent.OpenWidgetBlockPicker,
    PocketPassEvent.OpenWidgetRename,
    PocketPassEvent.OpenWidgetDeletePrompt,
    PocketPassEvent.OpenDeleteAccount,
    PocketPassEvent.OpenAddFriend,
    is PocketPassEvent.OpenUserProfile,
    PocketPassEvent.OpenRemoveFriend,
    is PocketPassEvent.OpenNotification,
    PocketPassEvent.OpenNewGroup,
    PocketPassEvent.OpenGroupInfo,
    -> SoundEffect.Navigation

    else -> null
}
