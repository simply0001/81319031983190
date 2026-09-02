package com.pocketpass.app.state

import com.pocketpass.app.audio.SoundEffect
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
    PocketPassEvent.CloseThemePicker,
    PocketPassEvent.CloseSortMenu,
    PocketPassEvent.CloseDeleteMiiSlot,
    PocketPassEvent.CloseMessageActions,
    PocketPassEvent.CancelMessageEdit,
    PocketPassEvent.CloseShop,
    PocketPassEvent.CloseBuyShopItem,
    PocketPassEvent.CloseGames,
    PocketPassEvent.CloseBingoSquare,
    PocketPassEvent.CloseWorldTourRegions,
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
    PocketPassEvent.CloseNewGroup,
    PocketPassEvent.CloseGroupInfo,
    PocketPassEvent.DismissConversationNotice,
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
    is PocketPassEvent.WearShopItem,
    is PocketPassEvent.SetActiveMiiSlot,
    PocketPassEvent.SaveBio,
    PocketPassEvent.SaveName,
    PocketPassEvent.ConfirmDeleteAccount,
    PocketPassEvent.SendProfileFriendRequest,
    PocketPassEvent.SubmitFriendCode,
    -> SoundEffect.Confirm

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

    PocketPassEvent.OpenMiiEditor,
    PocketPassEvent.OpenMiiSlots,
    PocketPassEvent.OpenConnectedApps,
    is PocketPassEvent.OpenRevokeConnectedApp,
    PocketPassEvent.OpenThemePicker,
    is PocketPassEvent.OpenDeleteMiiSlot,
    is PocketPassEvent.OpenMessage,
    is PocketPassEvent.OpenMessageActions,
    PocketPassEvent.OpenShop,
    is PocketPassEvent.OpenBuyShopItem,
    PocketPassEvent.OpenGames,
    is PocketPassEvent.OpenGame,
    PocketPassEvent.OpenWorldTourRegions,
    PocketPassEvent.OpenLeaderboard,
    PocketPassEvent.OpenLeaderboardSettings,
    PocketPassEvent.OpenAchievements,
    PocketPassEvent.OpenBioEditor,
    PocketPassEvent.OpenNameEditor,
    PocketPassEvent.OpenAccessibility,
    PocketPassEvent.OpenSocial,
    PocketPassEvent.OpenContributors,
    PocketPassEvent.OpenNotificationSettings,
    PocketPassEvent.OpenAppUpdate,
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
