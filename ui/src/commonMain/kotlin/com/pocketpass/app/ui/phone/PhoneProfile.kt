package com.pocketpass.app.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.pocketpass.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.ProfileFriendRequestState
import com.pocketpass.app.model.ProfileViewerSource
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.screens.FriendStat
import com.pocketpass.app.ui.screens.ProfileViewerPalette
import com.pocketpass.app.ui.screens.countryLabel
import com.pocketpass.app.ui.screens.greyPanelBrush
import com.pocketpass.app.ui.screens.profilePalette
import com.pocketpass.app.ui.theme.pocketPalette

@Composable
fun PhoneProfilePage(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    inline: Boolean = false,
) {
    val viewer = state.profileViewer
    val palette = viewer.source.profilePalette(pocketPalette)
    val profile = viewer.profile
    val insets = LocalPhoneInsets.current
    val busy = viewer.actionInProgress
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("profile_viewer")
                .verticalScroll(rememberScrollState())
                .padding(
                    top = metrics.dp(insets.top + 24f),
                    bottom = metrics.dp(insets.bottom + 60f),
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (inline) Arrangement.Center else Arrangement.Top,
        ) {
            ProfileBody(metrics, state, dispatch, inline, palette, busy)
        }
        if (inline) {
            PhoneRoundAction(
                metrics = metrics,
                borderColor = palette.border,
                tint = palette.surfaceBottom,
                tag = "profile_viewer_close",
                onClick = { dispatch(PocketPassEvent.CloseUserProfile) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = metrics.dp(insets.top + 24f), end = metrics.dp(PHONE_CONTENT_MARGIN)),
            ) { CloseGlyph(metrics, palette.primaryText) }
        }
    }
}

@Composable
private fun ProfileBody(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    inline: Boolean,
    palette: ProfileViewerPalette,
    busy: Boolean,
) {
    val viewer = state.profileViewer
    val profile = viewer.profile
    if (!inline) {
        PhonePageHeader(
            metrics = metrics,
            title = when (viewer.source) {
                ProfileViewerSource.Friend -> "Friend"
                else -> "Recent Interaction"
            },
            subtitle = null,
            backTag = "profile_viewer_close",
            onBack = { dispatch(PocketPassEvent.CloseUserProfile) },
        )
        Spacer(Modifier.height(metrics.dp(40f)))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (profile == null) {
            Text(
                text = if (viewer.unavailable) "This profile is unavailable." else "Loading profile…",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = metrics.dp(PHONE_CONTENT_MARGIN), vertical = metrics.dp(120f)),
                color = palette.primaryText,
                fontFamily = Rubik,
                fontWeight = FontWeight.ExtraBold,
                fontSize = metrics.sp(72f),
                textAlign = TextAlign.Center,
            )
            return@Column
        }
        val country = profile.locationLabel?.ifBlank { null } ?: profile.countryCode?.let(::countryLabel)
        PhoneProfileHero(
            metrics = metrics,
            name = profile.displayName.trim().ifBlank { "PocketPass User" },
            bio = profile.bio.trim(),
            age = profile.age,
            country = country,
            avatar = profile.avatar,
            localPortraitPath = null,
            fallback = null,
            border = palette.border,
            surface = palette.surfaceBottom,
            nameColor = palette.primaryText,
            bodyColor = palette.primaryText,
            accentColor = palette.accentText,
            online = viewer.isOnline,
            vertical = true,
            modifier = Modifier.padding(horizontal = metrics.dp(PHONE_CONTENT_MARGIN)),
            avatarSize = if (inline) 340f else 449f,
        )
        Spacer(Modifier.height(metrics.dp(if (inline) 36f else 56f)))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.dp(180f), Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FriendStat(
                metrics = metrics,
                modifier = Modifier,
                icon = Assets.FriendTrophy,
                iconWidth = 108.878f,
                iconHeight = 97.797f,
                value = viewer.stats?.trophyCount,
                pending = viewer.statsPending,
            )
            FriendStat(
                metrics = metrics,
                modifier = Modifier,
                icon = Assets.FriendWave,
                iconWidth = 97.795f,
                iconHeight = 97.795f,
                value = viewer.stats?.encounterCount,
                pending = viewer.statsPending,
            )
        }
        Spacer(Modifier.height(metrics.dp(if (inline) 36f else 52f)))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.dp(PHONE_CONTENT_MARGIN)),
            horizontalArrangement = Arrangement.spacedBy(metrics.dp(30f)),
        ) {
            val request = viewer.friendRequestState
            val isFriend = viewer.source == ProfileViewerSource.Friend || request == ProfileFriendRequestState.Friends
            when {
                request == ProfileFriendRequestState.Available ||
                    request == ProfileFriendRequestState.Sending ||
                    request == ProfileFriendRequestState.Pending ||
                    request == ProfileFriendRequestState.Failed -> {
                    PhoneButton(
                        metrics = metrics,
                        label = when (request) {
                            ProfileFriendRequestState.Sending -> "Sending…"
                            ProfileFriendRequestState.Pending -> "Request Sent"
                            ProfileFriendRequestState.Failed -> "Try Again"
                            else -> "Add Friend"
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !busy && request != ProfileFriendRequestState.Sending && request != ProfileFriendRequestState.Pending,
                        height = 165f,
                        fontSize = 42f,
                        tag = "profile_friend_request",
                        onClick = { dispatch(PocketPassEvent.SendProfileFriendRequest) },
                    )
                }
                isFriend -> {
                    PhoneButton(
                        metrics = metrics,
                        label = "Remove Friend",
                        modifier = Modifier.weight(1f),
                        fill = com.pocketpass.app.ui.screens.redButtonBrush(),
                        borderColor = PhoneRedBorder,
                        enabled = !busy,
                        height = 165f,
                        fontSize = 42f,
                        tag = "profile_remove_friend",
                        onClick = { dispatch(PocketPassEvent.OpenRemoveFriend) },
                    )
                }
                else -> Unit
            }
            // Direct messages are friend-only, so strangers see no dead button.
            if (isFriend) {
                PhoneButton(
                    metrics = metrics,
                    label = "Message",
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    height = 165f,
                    fontSize = 42f,
                    tag = "profile_message",
                    onClick = { dispatch(PocketPassEvent.MessageProfileFriend) },
                )
            }
        }
        val error = viewer.actionError ?: viewer.friendRequestError
        if (error != null) {
            Spacer(Modifier.height(metrics.dp(24f)))
            Text(
                text = error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = metrics.dp(PHONE_CONTENT_MARGIN)),
                color = pocketPalette.ink(Color(0xFFB31E3A)),
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(30f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
