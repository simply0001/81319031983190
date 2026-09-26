package com.pocketpass.app.ui.screens

import com.pocketpass.app.audio.LocalSoundEffects
import com.pocketpass.app.audio.SoundEffect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pocketpass.app.domain.model.GROUP_MESSAGES_BLOCKED
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.components.PocketPanel
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.theme.pocketPalette

internal const val MESSAGE_PRIVACY_PANEL_HEIGHT = 430f

@Composable
internal fun MessagePrivacyPanel(metrics: DesignMetrics, y: Float, state: PocketPassUiState, dispatch: (PocketPassEvent) -> Unit) {
    val blocked = state.profile?.blockMessages == true
    val canChange = state.profile != null && !state.messagePrivacySaving
    PocketPanel(metrics, x = 50f, y = y, width = 1140f, height = MESSAGE_PRIVACY_PANEL_HEIGHT,
        borderColor = pocketPalette.borderGrey, borderWidth = 20.152f, radius = 110f,
        fillBrush = greyPanelBrush(), tag = "block_messages_toggle",
        modifier = Modifier.semantics { toggleableState = ToggleableState(blocked) },
        onClick = if (canChange) ({ dispatch(PocketPassEvent.SetMessagePrivacy(!blocked)) }) else null) {
        SettingsHeading(metrics, Assets.SettingsMessagePrivacy, "Block Messages", if (state.messagePrivacySaving) "Saving…" else if (blocked) "On" else "Off")
        NearbyToggle(metrics, blocked)
        Text("Block new direct messages, including from friends.\nExisting group chats stay available.",
            color = pocketPalette.textSecondary, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = metrics.sp(40f),
            modifier = Modifier.designBounds(metrics, 55f, 210f, 1020f, 110f))
        val status = state.messagePrivacyError ?: if (state.profile == null) "Loading your settings…" else "Invitations have a separate setting below."
        Text(status, color = pocketPalette.textSecondary, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = metrics.sp(40f),
            modifier = Modifier.designBounds(metrics, 55f, 325f, 1020f, 95f).testTag("message_privacy_status"))
    }
}

internal const val SOCIAL_PRIVACY_PANEL_HEIGHT = 370f

@Composable
internal fun InvitesPrivacyPanel(metrics: DesignMetrics, y: Float, state: PocketPassUiState, dispatch: (PocketPassEvent) -> Unit) {
    val blocked = state.profile?.blockInvites == true
    PocketPanel(metrics, x = 50f, y = y, width = 1140f, height = SOCIAL_PRIVACY_PANEL_HEIGHT,
        borderColor = pocketPalette.borderGrey, borderWidth = 20.152f, radius = 110f,
        fillBrush = greyPanelBrush(), tag = "block_invites_toggle",
        modifier = Modifier.semantics { toggleableState = ToggleableState(blocked) },
        onClick = if (state.profile != null && !state.invitesPrivacySaving)
            ({ dispatch(PocketPassEvent.SetInvitesPrivacy(!blocked)) }) else null) {
        SettingsHeading(metrics, Assets.SettingsMessagePrivacy, "Block Invites",
            if (state.invitesPrivacySaving) "Saving…" else if (blocked) "On" else "Off")
        NearbyToggle(metrics, blocked)
        Text("Stop new group and Board invitations, and new friend requests.",
            color = pocketPalette.textSecondary, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = metrics.sp(40f),
            modifier = Modifier.designBounds(metrics, 55f, 205f, 1020f, 110f))
        state.invitesPrivacyError?.let { error -> Text(error, color = pocketPalette.textSecondary,
            fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = metrics.sp(35f),
            modifier = Modifier.designBounds(metrics, 55f, 315f, 1020f, 45f).testTag("invites_privacy_status")) }
    }
}

@Composable
internal fun BoardsVisibilityPanel(metrics: DesignMetrics, y: Float, state: PocketPassUiState, dispatch: (PocketPassEvent) -> Unit) {
    PocketPanel(metrics, x = 50f, y = y, width = 1140f, height = SOCIAL_PRIVACY_PANEL_HEIGHT,
        borderColor = pocketPalette.borderGrey, borderWidth = 20.152f, radius = 110f,
        fillBrush = greyPanelBrush(), tag = "boards_visibility_toggle",
        modifier = Modifier.semantics { toggleableState = ToggleableState(state.boardsVisible) },
        onClick = { dispatch(PocketPassEvent.SetBoardsVisible(!state.boardsVisible)) }) {
        SettingsHeading(metrics, Assets.SettingsSocial, "Show Boards", if (state.boardsVisible) "On" else "Off")
        NearbyToggle(metrics, state.boardsVisible)
        Text("When off, Messages shows only your conversations. Boards can be shown again here.",
            color = pocketPalette.textSecondary, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = metrics.sp(40f),
            modifier = Modifier.designBounds(metrics, 55f, 205f, 1020f, 115f))
    }
}

@Composable
internal fun MessagePrivacyBlockedDialog(error: String?) {
    var dismissed by remember(error) { mutableStateOf(false) }
    if (error != GROUP_MESSAGES_BLOCKED || dismissed) return
    val dismiss = { dismissed = true }
    val sounds = LocalSoundEffects.current
    val confirmDismiss = { sounds.play(SoundEffect.Confirm); dismiss() }
    Dialog(onDismissRequest = dismiss) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()
            .background(pocketPalette.surface, RoundedCornerShape(24.dp))
            .testTag("group_messages_blocked_dialog").padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Can't add this person", fontFamily = Rubik, fontWeight = FontWeight.Bold,
                color = pocketPalette.textPrimary, fontSize = 22.sp)
            Text(GROUP_MESSAGES_BLOCKED, fontFamily = Rubik, color = pocketPalette.textSecondary, fontSize = 17.sp)
            Box(Modifier.align(Alignment.End).testTag("group_messages_blocked_ok")
                .controllerTarget("group_messages_blocked_ok", layer = 100, onActivate = confirmDismiss)
                .clickable(role = Role.Button, onClick = confirmDismiss).padding(horizontal = 24.dp, vertical = 14.dp)) {
                Text("OK", fontFamily = Rubik, fontWeight = FontWeight.Bold, color = pocketPalette.teal, fontSize = 18.sp)
            }
        }
    }
}
