package com.pocketpass.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.ProfileFriendRequestState
import com.pocketpass.app.model.ProfileViewerSource
import com.pocketpass.app.model.ProfileViewerUiState
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.PocketAsset
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.theme.pocketPalette

@Composable
internal fun BoardProfileTop(
    metrics: DesignMetrics,
    state: ProfileViewerUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    BoardBackdrop(metrics)
    val focus = LocalControllerFocus.current
    val isFriend = state.source == ProfileViewerSource.Friend ||
        state.friendRequestState == ProfileFriendRequestState.Friends
    LaunchedEffect(state.selectedUserId, state.friendRequestState, state.source) {
        focus?.focus(
            when {
                isFriend -> "profile_message"
                state.friendRequestState == ProfileFriendRequestState.Available ||
                    state.friendRequestState == ProfileFriendRequestState.Failed -> "profile_friend_request"
                else -> "profile_viewer_close"
            },
            reveal = false,
        )
    }
    CompositionLocalProvider(LocalBoardFocusLayer provides 15) {
        Column(
            Modifier.designBounds(metrics, 142f, 145f, 1636f, 805f),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(22f)),
        ) {
            Row(
                Modifier.fillMaxWidth().height(metrics.dp(104f)).padding(start = metrics.dp(230f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.dp(28f)),
            ) {
                BoardRoundAction(metrics, "Close profile", "profile_viewer_close", { dispatch(PocketPassEvent.CloseUserProfile) }) {
                    FigmaAsset(
                        Assets.SettingsArrow,
                        Modifier.align(Alignment.Center).width(metrics.dp(25f)).height(metrics.dp(43f))
                            .graphicsLayer { scaleX = -1f },
                    )
                }
                BoardLabel(metrics, "Profile", 64f, true, color = pocketPalette.teal)
            }
            BoardCard(metrics, Modifier.weight(1f).testTag("board_profile_card"), contentPadding = 42f) {
                val profile = state.profile
                if (profile == null) {
                    BoardLabel(metrics, if (state.unavailable) "This profile is unavailable." else "Loading profile…", 56f, true)
                    return@BoardCard
                }
                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(metrics.dp(52f)),
                ) {
                    Column(
                        Modifier.width(metrics.dp(448f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(metrics.dp(20f)),
                    ) {
                        val shape = RoundedCornerShape(metrics.dp(42f))
                        Box(
                            Modifier.size(metrics.dp(360f))
                                .clip(shape)
                                .pocketFrame(SolidColor(pocketPalette.surfaceLow), metrics.dp(6f), pocketPalette.borderSoft, shape)
                                .testTag("board_profile_avatar"),
                        ) {
                            DynamicTopAvatar(
                                avatar = profile.avatar,
                                fallbackResource = Assets.SettingsSocial,
                                modifier = Modifier.fillMaxSize().clip(shape),
                            )
                        }
                        if (state.isOnline) BoardLabel(metrics, "Online now", 32f, color = pocketPalette.teal)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(metrics.dp(16f))) {
                            BoardProfileStat(metrics, Assets.FriendTrophy, "Trophies", state.stats?.trophyCount,
                                state.statsPending, Modifier.weight(1f).testTag("profile_trophy_count"))
                            BoardProfileStat(metrics, Assets.FriendWave, "Encounters", state.stats?.encounterCount,
                                state.statsPending, Modifier.weight(1f).testTag("profile_encounter_count"))
                        }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(metrics.dp(18f)),
                    ) {
                        val context = when (state.source) {
                            ProfileViewerSource.Board -> "BOARD MEMBER"
                            ProfileViewerSource.Friend -> "FRIEND"
                            else -> "PROFILE"
                        }
                        BoardLabel(metrics, context, 30f, true, color = pocketPalette.teal)
                        BoardLabel(metrics, profile.displayName.trim().ifBlank { "User" }, 68f, true, maxLines = 1)
                        val details = listOfNotNull(
                            profile.age?.let { "$it years" },
                            profile.locationLabel?.takeIf { it.isNotBlank() }
                                ?: profile.countryCode?.let(::countryLabel),
                        ).joinToString("  ·  ")
                        if (details.isNotEmpty()) BoardLabel(metrics, details, 34f, color = pocketPalette.textSecondary, maxLines = 1)
                        profile.bio.trim().takeIf { it.isNotEmpty() }?.let {
                            BoardLabel(metrics, it, 40f, maxLines = 2)
                        }
                        Spacer(Modifier.weight(1f))
                        if (isFriend) {
                            BoardProfileFriendActions(metrics, state, dispatch)
                        } else {
                            BoardProfileFriendRequestPanel(
                                metrics = metrics,
                                state = state,
                                modifier = Modifier.testTag("board_profile_request_panel"),
                                compact = true,
                                onSend = { dispatch(PocketPassEvent.SendProfileFriendRequest) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardProfileStat(
    metrics: DesignMetrics,
    icon: PocketAsset,
    label: String,
    value: Int?,
    pending: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(metrics.dp(22f))
    Row(
        modifier.height(metrics.dp(92f))
            .pocketFrame(SolidColor(pocketPalette.surfaceLow), metrics.dp(3f), pocketPalette.borderSoft, shape)
            .padding(horizontal = metrics.dp(12f)),
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(10f), Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FigmaAsset(icon, Modifier.size(metrics.dp(58f)), contentScale = ContentScale.Fit, description = label)
        BoardLabel(metrics, value?.toString() ?: if (pending) "" else "–", 48f, true, maxLines = 1)
    }
}

@Composable
private fun BoardProfileFriendActions(
    metrics: DesignMetrics,
    state: ProfileViewerUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    BoardCard(metrics, contentPadding = 18f) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(metrics.dp(20f))) {
            BoardButton(metrics, "Remove Friend", "profile_remove_friend", enabled = !state.actionInProgress,
                modifier = Modifier.weight(1f)) { dispatch(PocketPassEvent.OpenRemoveFriend) }
            BoardButton(metrics, "Message", "profile_message", selected = true, enabled = !state.actionInProgress,
                modifier = Modifier.weight(1f)) { dispatch(PocketPassEvent.MessageProfileFriend) }
        }
        state.actionError?.let {
            BoardLabel(metrics, it, 30f, maxLines = 1, color = pocketPalette.ink(Color(0xFFB31E3A)))
        }
    }
}

@Composable
internal fun BoardProfileFriendRequestPanel(
    metrics: DesignMetrics,
    state: ProfileViewerUiState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onSend: () -> Unit,
) {
    val request = state.friendRequestState
    if (request == ProfileFriendRequestState.Hidden || request == ProfileFriendRequestState.Friends) return
    BoardCard(metrics, modifier, contentPadding = if (compact) 18f else 24f) {
        BoardLabel(metrics, "Friend request", 38f, true, color = pocketPalette.teal)
        when (request) {
            ProfileFriendRequestState.Available, ProfileFriendRequestState.Failed -> {
                if (!compact) {
                    BoardLabel(
                        metrics,
                        if (request == ProfileFriendRequestState.Failed) "The request didn't go through. Try again."
                        else "Send a friend request to stay in touch.",
                        32f,
                        color = pocketPalette.textSecondary,
                    )
                }
                BoardButton(
                    metrics,
                    if (request == ProfileFriendRequestState.Failed) "Try Again" else "Add Friend",
                    "profile_friend_request",
                    selected = true,
                    enabled = !state.actionInProgress,
                    icon = Assets.MessageActionAdd,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSend,
                )
            }
            ProfileFriendRequestState.Sending -> BoardLabel(metrics, "Sending your friend request…", 34f)
            ProfileFriendRequestState.Pending -> BoardLabel(metrics, "Friend request sent", 34f)
            ProfileFriendRequestState.Unavailable -> BoardLabel(
                metrics,
                if (state.profile?.blockInvites == true) "This person isn't accepting friend requests."
                else "Friend requests aren't available for this profile.",
                34f,
            )
            ProfileFriendRequestState.Hidden, ProfileFriendRequestState.Friends -> Unit
        }
        state.friendRequestError?.let { BoardLabel(metrics, it, 30f, maxLines = if (compact) 1 else 2, color = pocketPalette.ink(Color(0xFFB31E3A))) }
    }
}
