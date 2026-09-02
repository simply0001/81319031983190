package com.pocketpass.app.state

import com.pocketpass.app.audio.SoundEffect
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventSoundsTest {
    @Test
    fun backAndCloseEventsCancel() {
        assertEquals(SoundEffect.Cancel, soundEffectFor(PocketPassEvent.Back, PocketPassDestination.Home))
        assertEquals(SoundEffect.Cancel, soundEffectFor(PocketPassEvent.CloseShop, PocketPassDestination.Activities))
        assertEquals(SoundEffect.Cancel, soundEffectFor(PocketPassEvent.DismissOAuthConsent, PocketPassDestination.Settings))
    }

    @Test
    fun tabSwitchesFollowTheTabOrder() {
        assertEquals(
            SoundEffect.TabRight,
            soundEffectFor(PocketPassEvent.SelectDestination(PocketPassDestination.Settings), PocketPassDestination.Home),
        )
        assertEquals(
            SoundEffect.TabLeft,
            soundEffectFor(PocketPassEvent.SelectDestination(PocketPassDestination.Messages), PocketPassDestination.Home),
        )
        assertNull(soundEffectFor(PocketPassEvent.SelectDestination(PocketPassDestination.Home), PocketPassDestination.Home))
    }

    @Test
    fun openingSomethingNavigates() {
        assertEquals(SoundEffect.Navigation, soundEffectFor(PocketPassEvent.OpenSocial, PocketPassDestination.Settings))
        assertEquals(SoundEffect.Navigation, soundEffectFor(PocketPassEvent.OpenContributors, PocketPassDestination.Settings))
        assertEquals(SoundEffect.Navigation, soundEffectFor(PocketPassEvent.OpenMessage("c1"), PocketPassDestination.Messages))
        assertEquals(SoundEffect.Navigation, soundEffectFor(PocketPassEvent.OpenNotification("n1"), PocketPassDestination.Home))
        assertEquals(SoundEffect.Navigation, soundEffectFor(PocketPassEvent.OpenNewGroup, PocketPassDestination.Messages))
        assertEquals(SoundEffect.Navigation, soundEffectFor(PocketPassEvent.OpenGroupInfo, PocketPassDestination.Messages))
        assertEquals(SoundEffect.Cancel, soundEffectFor(PocketPassEvent.CloseGroupInfo, PocketPassDestination.Messages))
        assertEquals(SoundEffect.Confirm, soundEffectFor(PocketPassEvent.CreateGroup, PocketPassDestination.Messages))
        assertEquals(SoundEffect.Confirm, soundEffectFor(PocketPassEvent.LeaveGroup, PocketPassDestination.Messages))
    }

    @Test
    fun confirmingSomethingConfirms() {
        assertEquals(SoundEffect.Confirm, soundEffectFor(PocketPassEvent.ConfirmBuyShopItem, PocketPassDestination.Activities))
        assertEquals(SoundEffect.Confirm, soundEffectFor(PocketPassEvent.SaveName, PocketPassDestination.Settings))
        assertEquals(
            SoundEffect.Confirm,
            soundEffectFor(PocketPassEvent.RespondToNotificationFriendRequest("n1", accept = true), PocketPassDestination.Home),
        )
        assertEquals(
            SoundEffect.Cancel,
            soundEffectFor(PocketPassEvent.RespondToNotificationFriendRequest("n1", accept = false), PocketPassDestination.Home),
        )
        assertEquals(
            SoundEffect.Confirm,
            soundEffectFor(PocketPassEvent.Mii(com.pocketpass.app.mii.MiiEditorEvent.Save), PocketPassDestination.Settings),
        )
        assertEquals(
            SoundEffect.Confirm,
            soundEffectFor(
                PocketPassEvent.Mii(com.pocketpass.app.mii.MiiEditorEvent.ConfirmPretendoImport),
                PocketPassDestination.Settings,
            ),
        )
        assertEquals(
            SoundEffect.Cancel,
            soundEffectFor(
                PocketPassEvent.Mii(com.pocketpass.app.mii.MiiEditorEvent.ClosePretendoImport),
                PocketPassDestination.Settings,
            ),
        )
        assertEquals(
            SoundEffect.Navigation,
            soundEffectFor(
                PocketPassEvent.Mii(com.pocketpass.app.mii.MiiEditorEvent.OpenPretendoImport),
                PocketPassDestination.Settings,
            ),
        )
        assertNull(
            soundEffectFor(
                PocketPassEvent.Mii(com.pocketpass.app.mii.MiiEditorEvent.SetPretendoId("jon")),
                PocketPassDestination.Settings,
            ),
        )
    }

    @Test
    fun settingsChangesAreSilent() {
        assertNull(soundEffectFor(PocketPassEvent.SetSoundLevel(0.5f), PocketPassDestination.Settings))
        assertNull(soundEffectFor(PocketPassEvent.SetSfxLevel(0.5f), PocketPassDestination.Settings))
        assertNull(soundEffectFor(PocketPassEvent.SetNearby(true), PocketPassDestination.Settings))
    }
}
