package com.pocketpass.app.ui.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.ProfileViewerSource
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.components.EntranceMotion
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.IdleMotion
import com.pocketpass.app.ui.components.MotionLayer
import com.pocketpass.app.ui.theme.pocketPalette

private val FriendsAccent = Color(0xFFCB4AC0)

@Composable
internal fun PhoneFriendsTab(
    metrics: DesignMetrics,
    panes: WidePanes?,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val people = remember(state.friends, state.friendsSort) { state.friends.toPhonePeople(state.friendsSort) }
    val grid: @Composable () -> Unit = {
        PhonePeopleGrid(
            metrics = metrics,
            people = people,
            colors = friendCardColors(),
            topInset = LocalPhoneInsets.current.top,
            header = { PhoneFriendsHeader(metrics, state, dispatch) },
            empty = { PhoneFriendsStatus(metrics, state, dispatch) },
            onPerson = { dispatch(PocketPassEvent.OpenUserProfile(it, ProfileViewerSource.Friend)) },
            footer = {
                val error = state.friendsRefreshError
                if (error != null && people.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "refresh_notice") {
                        PhoneFriendsRefreshNotice(metrics, error, state.friendsRefreshing) {
                            dispatch(PocketPassEvent.RefreshFriends)
                        }
                    }
                }
            },
        )
        PhoneSortMenuScrim(state.sortMenuOpen, "friends_sort_scrim") { dispatch(PocketPassEvent.CloseSortMenu) }
    }
    if (panes == null) {
        Box(Modifier.fillMaxSize()) { grid() }
    } else {
        PhonePanes(
            metrics = metrics,
            panes = panes,
            stage = {
                if (state.profileViewer.visible) {
                    PhoneProfilePage(metrics, state, dispatch, inline = true)
                } else {
                    PhoneStageScroll(metrics) { PhoneFriendsBadge(metrics, state.onlineFriendCount) }
                }
            },
            deck = { grid() },
        )
    }
}

@Composable
private fun PhoneFriendsHeader(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    val titleColor = palette.ink(Color(0xFF820A79))
    Box(Modifier.fillMaxWidth()) {
        PhoneSectionHeader(
            metrics = metrics,
            title = "All Friends (${state.friends.size})",
            color = titleColor,
            subtitle = "${state.onlineFriendCount} online",
            horizontalPadding = 0f,
        ) {
            PhoneRoundAction(
                metrics = metrics,
                borderColor = FriendsAccent,
                tint = palette.tint(Color(0xFFFED3FF)),
                tag = "add_friend",
                onClick = { dispatch(PocketPassEvent.OpenAddFriend) },
            ) { PlusGlyph(metrics, palette.ink(Color(0xFF820A79))) }
            PhoneRoundAction(
                metrics = metrics,
                borderColor = FriendsAccent,
                tint = palette.tint(Color(0xFFFED3FF)),
                tag = "section_filter",
                onClick = { dispatch(PocketPassEvent.ToggleSortMenu) },
            ) { FigmaAsset(resource = Assets.FriendsFilter, modifier = Modifier.requiredSize(metrics.dp(42f))) }
        }
        PhoneSortMenuPanel(
            metrics = metrics,
            open = state.sortMenuOpen,
            selected = state.friendsSort,
            borderColor = FriendsAccent,
            textColor = palette.ink(Color(0xFF511D6B)),
            tagPrefix = "friends_sort",
            onSelect = { sort ->
                dispatch(PocketPassEvent.SetFriendsSort(sort))
                dispatch(PocketPassEvent.CloseSortMenu)
            },
        )
    }
}

@Composable
private fun PhoneFriendsStatus(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val loading = state.friendsLoading || state.friendsRefreshing
    val error = state.friendsRefreshError
    PhoneEmptyRow(
        metrics = metrics,
        icon = Assets.NavFriends,
        title = when {
            loading -> "Loading friends…"
            error != null -> "Friends unavailable"
            else -> "No friends yet"
        },
        subtitle = when {
            loading -> "Checking PocketPass for your friends"
            error != null -> "Tap to try again"
            else -> "Tap to add someone with a friend code"
        },
        tag = if (error == null) "friends_empty_add" else "friends_retry",
        onClick = when {
            loading -> null
            error != null -> ({ dispatch(PocketPassEvent.RefreshFriends) })
            else -> ({ dispatch(PocketPassEvent.OpenAddFriend) })
        },
    )
}

@Composable
private fun PhoneFriendsRefreshNotice(
    metrics: DesignMetrics,
    message: String,
    refreshing: Boolean,
    onRetry: () -> Unit,
) {
    Text(
        text = if (refreshing) "Refreshing friends…" else "$message Tap to retry.",
        modifier = Modifier
            .fillMaxWidth()
            .testTag("friends_refresh_notice")
            .clickable(
                enabled = !refreshing,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onRetry,
            )
            .padding(vertical = metrics.dp(12f)),
        color = pocketPalette.ink(Color(0xFF820A79)),
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = metrics.sp(30f),
        textAlign = TextAlign.Center,
        maxLines = 2,
    )
}

@Composable
internal fun PhoneFriendsBadge(metrics: DesignMetrics, onlineCount: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MotionLayer(entrance = EntranceMotion.FriendSweep, idle = IdleMotion.FriendPulse) {
            FigmaAsset(
                resource = Assets.FriendsBadge,
                modifier = Modifier.requiredSize(metrics.dp(521.464f), metrics.dp(552.073f)),
            )
        }
        Spacer(Modifier.height(metrics.dp(40f)))
        MotionLayer(entrance = EntranceMotion.PanelRise, delayMillis = 120) {
            Text(
                text = "$onlineCount Online",
                color = pocketPalette.ink(Color(0xFF820A79)),
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(128f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
