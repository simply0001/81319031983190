package com.pocketpass.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import com.pocketpass.app.boards.*
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.theme.pocketPalette
import com.pocketpass.app.ui.controller.FocusDirection
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.domain.model.AvatarReference
import kotlinx.serialization.json.*

@Composable
internal fun BoardManagement(m: DesignMetrics, state: PocketPassUiState, send: (BoardAction)->Unit,
    openProfile: (String) -> Unit) {
    val s = state.boards; val b = s.board ?: return
    fun action(operation: String, vararg args: Pair<String, JsonElement?>) = send(BoardAction.Mutate(operation, boardArgs("board_id" to b.id.boardValue(), *args)))
    BoardCard(m) {
        BoardLabel(m, "Notifications", 52f, true)
        BoardToggle(m, "Activity alerts", "Updates inside PocketPass", !b.muted, "board_mute", !s.busy) { action("preferences", "muted" to (!b.muted).boardValue()) }
        BoardToggle(m, "Push notifications", "Alerts when you are away", b.pushEnabled, "board_push", !s.busy) { action("preferences", "push_enabled" to (!b.pushEnabled).boardValue()) }
    }
    if(b.transferPending == true) BoardButton(m, "Accept ownership", "board_accept_owner", true, !s.busy) { action("accept_transfer") }
    val inviteFriends = if(s.inviteMembersLoaded) state.friends.filterNot { it.profile.userId.value in s.inviteMemberIds } else emptyList()
    if(b.canInvite) BoardDisclosure(m, "Invite someone", "board_section_invite",
        firstTarget = inviteFriends.firstOrNull()?.let { "board_invite_${it.profile.userId.value}" } ?: "board_invite_friend_code") {
        val status = s.inviteStatus?.takeIf { it.boardId == b.id }
        if(!s.inviteMembersLoaded) BoardLabel(m, "Checking who is already in this board…", 32f)
        else if(inviteFriends.isEmpty()) BoardLabel(m,
            if(state.friends.isEmpty()) "No friends to invite yet. Use a Friend Code below."
            else "All your friends are already in this board.", 32f)
        inviteFriends.forEachIndexed { index, friend ->
            val feedback = status?.takeIf { it.userId == friend.profile.userId.value }
            val subtitle = when {
                feedback == null -> "Send an invitation"
                feedback.sending -> "Sending invitation…"
                feedback.error != null -> "${feedback.error} Tap to retry."
                else -> "Invitation sent"
            }
            val invite = boardConfirmAction({ if(!s.busy) action("invite", "user_id" to friend.profile.userId.value.boardValue()) })
            BoardNavigationRow(m, friend.profile.displayName, subtitle, "board_invite_${friend.profile.userId.value}", leading = {
                BoardMemberAvatar(m, friend.profile.displayName, friend.profile.avatar, "board_invite_avatar_${friend.profile.userId.value}")
            }, neighbors = mapOf(
                FocusDirection.Up to (inviteFriends.getOrNull(index - 1)?.let { "board_invite_${it.profile.userId.value}" } ?: "board_section_invite"),
                FocusDirection.Down to (inviteFriends.getOrNull(index + 1)?.let { "board_invite_${it.profile.userId.value}" } ?: "board_invite_friend_code")),
                onClick = invite)
        }
        var friendCode by remember(b.id) { mutableStateOf("") }
        val canSend = friendCode.length == 8 && !s.busy
        BoardField(m, friendCode, { friendCode = it.filter(Char::isDigit).take(8) }, "Friend Code (8 digits)", tag = "board_invite_friend_code",
            neighbors = mapOf(FocusDirection.Down to if(canSend) "board_invite_friend_code_send" else "board_create_code",
                FocusDirection.Up to (inviteFriends.lastOrNull()?.let { "board_invite_${it.profile.userId.value}" } ?: "board_section_invite")))
        BoardButton(m, "Send invitation", "board_invite_friend_code_send", enabled = canSend, confirmSound = true,
            neighbors = mapOf(FocusDirection.Up to "board_invite_friend_code", FocusDirection.Down to "board_create_code")) { action("invite", "friend_code" to friendCode.boardValue()) }
        status?.takeIf { it.userId == friendCode }?.let {
            BoardLabel(m, if(it.sending) "Sending invitation…" else it.error ?: "Invitation sent")
        }
        BoardButton(m, "Create invitation code", "board_create_code", enabled = !s.busy, confirmSound = true,
            neighbors = mapOf(FocusDirection.Up to if(canSend) "board_invite_friend_code_send" else "board_invite_friend_code")) { action("create_code") }
        s.inviteCode?.let { code -> BoardLabel(m, code, 44f, true); BoardLabel(m, "Share this code with the people you want to invite. You can revoke it below.") }
    }
    if(b.role == "owner") BoardOwnerSettings(m, b, s.busy, send)
    if(b.role == "owner") BoardDisclosure(m, "Board artwork", "board_section_artwork") {
        BoardBranding(m, b, s.assets, true)
        listOf("icon", "cover").forEach { kind ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(m.dp(18f)), verticalArrangement = Arrangement.spacedBy(m.dp(14f))) {
                BoardButton(m, "Draw $kind", "draw_board_$kind", enabled = !s.busy) { send(BoardAction.DrawBranding(kind)) }
                BoardButton(m, "Import $kind", "import_board_$kind", enabled = !s.busy) { send(BoardAction.ImportBranding(kind)) }
            }
        }
    }
    if(b.role != null) {
        BoardDisclosure(m, "Active invitations", "board_section_invitations") {
            if(s.management?.get("invitations")?.jsonArray.orEmpty().isEmpty()) BoardLabel(m, "No active invitations.")
            s.management?.get("invitations")?.jsonArray.orEmpty().forEach { element ->
                val invite = element.jsonObject; val id = invite.text("id") ?: return@forEach
                val recipientId = invite.text("recipient_id")
                val friend = state.friends.firstOrNull { it.profile.userId.value == recipientId }?.profile
                val member = s.members.firstOrNull { it.userId == recipientId }
                val name = friend?.displayName ?: member?.displayName?.takeIf { it.isNotBlank() }
                    ?: invite.text("recipient_name")
                val avatar = friend?.avatar ?: member?.avatarPath?.takeIf { it.startsWith("https://") }?.let { AvatarReference.Remote(it) }
                val expired = invite.text("expires_at")?.let {
                    runCatching { kotlin.time.Instant.parse(it) <= kotlin.time.Clock.System.now() }.getOrDefault(false)
                } ?: false
                val inviterId = invite.text("inviter_id")
                val inviterName = if(inviterId == state.profile?.userId?.value) "you" else
                    state.friends.firstOrNull { it.profile.userId.value == inviterId }?.profile?.displayName
                        ?: s.members.firstOrNull { it.userId == inviterId }?.displayName
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(m.dp(14f))) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(22f))) {
                        if(recipientId != null) BoardMemberAvatar(m, name ?: "?", avatar, "invitation_avatar_$id")
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(m.dp(8f))) {
                            BoardLabel(m, if(recipientId == null) "Invitation code" else name ?: "Invited account", 42f, true)
                            if(recipientId != null && name == null) BoardLabel(m, recipientId, 28f)
                            BoardLabel(m, when {
                                expired -> "Expired"
                                recipientId != null -> "Awaiting acceptance"
                                b.codePolicy == "approval" -> "Code joins require approval"
                                else -> "Code grants entry immediately"
                            }, 30f)
                            inviterName?.let { BoardLabel(m, "Invited by $it", 28f, color = pocketPalette.textSecondary) }
                            invite.text("created_at")?.let { BoardLabel(m, "Created ${it.take(10)}", 28f, color = pocketPalette.textSecondary) }
                            invite.text("expires_at")?.let { BoardLabel(m, "Expires ${it.take(10)}", 28f, color = pocketPalette.textSecondary) }
                        }
                    }
                    BoardButton(m, "Revoke", "revoke_$id", enabled = !s.busy, confirmSound = true) { action("revoke_invitation", "id" to id.boardValue()) }
                    BoardDivider(m)
                }
            }
        }
    }
    if(b.canModerate) {
        BoardDisclosure(m, "Membership requests", "board_section_requests") {
            val requests = s.management?.get("requests")?.jsonArray.orEmpty()
            if(requests.isEmpty()) BoardLabel(m, "No pending requests.")
            requests.forEach { element ->
                val request = element.jsonObject; val user = request.text("user_id") ?: return@forEach
                BoardLabel(m, request.text("display_name") ?: "PocketPass member")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(m.dp(18f)), verticalArrangement = Arrangement.spacedBy(m.dp(14f))) {
                    BoardButton(m, "Accept", "join_accept_$user", true, !s.busy) { action("decide_join", "user_id" to user.boardValue(), "approve" to true.boardValue()) }
                    BoardButton(m, "Decline", "join_decline_$user", enabled = !s.busy) { action("decide_join", "user_id" to user.boardValue(), "approve" to false.boardValue()) }
                }
            }
        }
        BoardDisclosure(m, "Reports", "board_section_reports", initiallyOpen = s.openReportsFromInbox) {
            val reports = s.management?.get("reports")?.jsonArray.orEmpty().map { it.boardDecode<BoardReport>() }.filter { it.status == "open" }
            if(reports.isEmpty()) BoardLabel(m, "No open reports.")
            reports.forEach { report ->
                BoardLabel(m, report.reason)
                report.postId?.let { postId ->
                    BoardButton(m, "Review note", "review_$postId") { send(BoardAction.OpenDestination(b.id, postId, review = true)) }
                }
                BoardResolution(m, report.id, s.busy) { resolution, dismiss -> action("resolve_report", "id" to report.id.boardValue(), "reason" to resolution.boardValue(), "status" to (if(dismiss) "dismissed" else "resolved").boardValue()) }
            }
        }
        BoardDisclosure(m, "Appeals", "board_section_appeals") {
            val appeals = s.management?.get("appeals")?.jsonArray.orEmpty().map { it.boardDecode<BoardAppeal>() }.filter { it.status == "open" }
            if(appeals.isEmpty()) BoardLabel(m, "No open appeals.")
            appeals.forEach { appeal ->
                BoardLabel(m, "Case ${appeal.caseId}", 30f)
                BoardLabel(m, appeal.body)
                BoardResolution(m, appeal.id, s.busy) { resolution, dismiss -> action("resolve_appeal", "id" to appeal.id.boardValue(), "reason" to resolution.boardValue(), "status" to (if(dismiss) "dismissed" else "resolved").boardValue()) }
            }
        }
        BoardDisclosure(m, "Members", "board_section_members") {
            s.members.forEach { member ->
                var expanded by remember(member.userId) { mutableStateOf(false) }
                var reason by remember(member.userId) { mutableStateOf("") }
                BoardInlineBackHandler(expanded) { expanded = false }
                val focus = LocalControllerFocus.current
                val memberTag = "manage_member_${member.userId}"
                val profileTag = "member_profile_${member.userId}"
                LaunchedEffect(expanded) {
                    if(expanded && focus?.focusId == memberTag) focus.focus(profileTag)
                }
                val avatar = member.avatarPath?.takeIf { it.startsWith("https://") }?.let { AvatarReference.Remote(it) }
                    ?: state.profile?.takeIf { it.userId.value == member.userId }?.avatar
                    ?: state.friends.firstOrNull { it.profile.userId.value == member.userId }?.profile?.avatar
                BoardNavigationRow(m, member.displayName, member.role.replaceFirstChar { it.uppercase() }, memberTag, leading = {
                    BoardMemberAvatar(m, member.displayName, avatar, "board_member_avatar_${member.userId}")
                }, neighbors = if(expanded) mapOf(FocusDirection.Down to profileTag) else emptyMap()) { expanded = !expanded }
                if(expanded) BoardButton(m, "View profile", profileTag, confirmSound = true,
                    neighbors = mapOf(FocusDirection.Up to memberTag)) {
                    openProfile(member.userId)
                }
                if(expanded && member.role != "owner") {
                    if(b.role == "owner") {
                        BoardButton(m, if(member.role == "moderator") "Remove moderator" else "Make moderator", "moderator_${member.userId}", enabled = !s.busy) { action("set_moderator", "user_id" to member.userId.boardValue(), "moderator" to (member.role != "moderator").boardValue()) }
                        BoardButton(m, "Offer ownership", "transfer_${member.userId}", enabled = !s.busy) { action("transfer", "user_id" to member.userId.boardValue()) }
                    }
                    if(b.role == "owner" || member.role != "moderator") {
                        BoardField(m, reason, { reason = it.take(1000) }, "Reason shown to this member", true)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(m.dp(18f)), verticalArrangement = Arrangement.spacedBy(m.dp(14f))) {
                            BoardButton(m, "Mute participation", "mute_${member.userId}", enabled = reason.isNotBlank() && !s.busy) { action("restrict", "user_id" to member.userId.boardValue(), "kind" to "mute".boardValue(), "reason" to reason.boardValue()) }
                            BoardButton(m, "Ban from board", "ban_${member.userId}", enabled = reason.isNotBlank() && !s.busy) { action("restrict", "user_id" to member.userId.boardValue(), "kind" to "ban".boardValue(), "reason" to reason.boardValue()) }
                        }
                    }
                }
            }
        }
        BoardDisclosure(m, "Restrictions", "board_section_restrictions") {
            if(s.management?.get("restrictions")?.jsonArray.orEmpty().isEmpty()) BoardLabel(m, "No restrictions.")
            s.management?.get("restrictions")?.jsonArray.orEmpty().forEach { element ->
                val restriction = element.boardDecode<BoardRestriction>()
                BoardLabel(m, "${restriction.kind}: ${restriction.reason}")
                BoardButton(m, "Revoke restriction", "revoke_restriction_${restriction.id}", enabled = !s.busy) { action("revoke_restriction", "id" to restriction.id.boardValue()) }
            }
        }
    }
    if(s.cursor != null) BoardButton(m, "Load more members and requests", "boards_more", enabled = !s.loading) { send(BoardAction.More) }
    BoardDisclosure(m, "Report this board", "board_section_report_branding") {
        var reason by remember(b.id) { mutableStateOf("") }
        BoardLabel(m, "Report a problem with this board’s name, rules, or artwork.")
        BoardField(m, reason, { reason = it.take(1000) }, "What should staff know?", true)
        BoardButton(m, "Send report", "report_branding", enabled = reason.isNotBlank() && !s.busy) { action("report", "reason" to reason.boardValue()) }
    }
    var confirmLeave by remember(b.id) { mutableStateOf(false) }
    BoardButton(m, "Leave board", "leave_board") { confirmLeave = true }
    if(confirmLeave) BoardCard(m) {
        BoardLabel(m, if(b.role == "owner" && !b.archived) "Transfer ownership and wait for acceptance, or archive the board before leaving." else "Leave ${b.name}? You'll need to join again to participate.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(m.dp(18f)), verticalArrangement = Arrangement.spacedBy(m.dp(14f))) {
            BoardButton(m, "Leave", "confirm_leave", enabled = (b.role != "owner" || b.archived) && !s.busy) { action("leave") }
            BoardButton(m, "Stay", "cancel_leave") { confirmLeave = false }
        }
    }
}

@Composable
private fun BoardMemberAvatar(m: DesignMetrics, name: String, avatar: AvatarReference?, tag: String) {
    val shape = RoundedCornerShape(m.dp(22f))
    Box(Modifier.size(m.dp(100f)).clip(shape).background(pocketPalette.surfaceLow)
        .border(m.dp(3f), pocketPalette.borderSoft, shape).testTag(tag), contentAlignment = Alignment.Center) {
        BoardLabel(m, name.take(1).uppercase(), 44f, true, color = pocketPalette.teal)
        DynamicAvatar(avatar, null, Modifier.fillMaxSize().padding(m.dp(4f)), ContentScale.Crop)
    }
}

@Composable
private fun BoardOwnerSettings(m: DesignMetrics, b: Board, busy: Boolean, send: (BoardAction)->Unit) {
    var name by remember(b.id) { mutableStateOf(b.name) }; var description by remember(b.id) { mutableStateOf(b.description) }; var rules by remember(b.id) { mutableStateOf(b.rules) }
    var makePrivate by remember(b.id) { mutableStateOf(false) }
    fun action(operation: String, vararg args: Pair<String, JsonElement?>) = send(BoardAction.Mutate(operation, boardArgs("board_id" to b.id.boardValue(), *args)))
    BoardDisclosure(m, "About this board", "board_section_details") {
        BoardField(m, name, { name = it.take(60) }, "Name")
        BoardField(m, description, { description = it.take(1000) }, "Description", true)
        BoardField(m, rules, { rules = it.take(4000) }, "Rules", true)
        BoardButton(m, "Save details", "board_save_details", true, !busy && name.isNotBlank()) { action("update_board", "name" to name.boardValue(), "description" to description.boardValue(), "rules" to rules.boardValue()) }
        BoardLabel(m, "Accent colour", 40f, true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(m.dp(14f)), verticalArrangement = Arrangement.spacedBy(m.dp(14f))) {
            listOf("blue", "green", "pink", "purple", "orange", "teal").forEach { accent -> BoardButton(m, accent.replaceFirstChar { it.uppercase() }, "accent_$accent", b.accent == accent, !busy) { action("update_board", "accent" to accent.boardValue()) } }
        }
    }
    BoardDisclosure(m, "Joining and invitations", "board_section_joining",
        firstTarget = if(b.visibility == "public") "join_policy" else "code_policy") {
        if(b.visibility == "public") BoardToggle(m, "Approve new members", "Review requests before people join", b.joinPolicy == "approval", "join_policy", !busy,
            neighbors = mapOf(FocusDirection.Up to "board_section_joining", FocusDirection.Down to "code_policy")) { action("update_board", "join_policy" to (if(b.joinPolicy == "open") "approval" else "open").boardValue()) }
        BoardToggle(m, "Approve code joins", "Review people joining with a code", b.codePolicy == "approval", "code_policy", !busy,
            neighbors = mapOf(FocusDirection.Up to if(b.visibility == "public") "join_policy" else "board_section_joining", FocusDirection.Down to "members_invite")) { action("update_board", "code_policy" to (if(b.codePolicy == "open") "approval" else "open").boardValue()) }
        BoardToggle(m, "Members can invite", "Allow members to share invitations", b.membersCanInvite, "members_invite", !busy,
            neighbors = mapOf(FocusDirection.Up to "code_policy", FocusDirection.Down to if(b.visibility == "public") "board_make_private" else "board_archive")) { action("update_board", "members_can_invite" to (!b.membersCanInvite).boardValue()) }
        if(b.visibility == "public") BoardButton(m, "Make board private", "board_make_private", enabled = !busy,
            neighbors = mapOf(FocusDirection.Up to "members_invite", FocusDirection.Down to if(makePrivate) "board_confirm_private" else "board_archive")) { makePrivate = true }
        if(makePrivate) {
            BoardLabel(m, "Only members will be able to read this board. This change cannot be reversed.")
            BoardButton(m, "Confirm private board", "board_confirm_private", enabled = !busy,
                neighbors = mapOf(FocusDirection.Up to "board_make_private", FocusDirection.Down to "board_archive")) { action("update_board", "visibility" to "private".boardValue()); makePrivate = false }
        }
        BoardButton(m, if(b.archived) "Reopen board" else "Archive board", "board_archive", enabled = !busy,
            neighbors = mapOf(FocusDirection.Up to when {
                makePrivate -> "board_confirm_private"
                b.visibility == "public" -> "board_make_private"
                else -> "members_invite"
            })) { action("archive", "archived" to (!b.archived).boardValue()) }
        BoardLabel(m, "Archived boards stay readable, but notes, replies, reactions, and invitations stop.", 32f)
    }
}

@Composable
private fun BoardResolution(m: DesignMetrics, id: String, busy: Boolean, submit: (String, Boolean)->Unit) {
    var reason by remember(id) { mutableStateOf("") }
    BoardField(m, reason, { reason = it.take(1000) }, "Resolution", true)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(m.dp(18f)), verticalArrangement = Arrangement.spacedBy(m.dp(14f))) {
        BoardButton(m, "Resolve", "resolve_$id", true, !busy && reason.isNotBlank()) { submit(reason, false) }
        BoardButton(m, "Dismiss", "dismiss_$id", enabled = !busy && reason.isNotBlank()) { submit(reason, true) }
    }
}

@Composable
internal fun BoardModerationNotices(m: DesignMetrics, s: BoardsUiState, send: (BoardAction)->Unit) {
    val uri = LocalUriHandler.current
    val data = s.moderationNotices ?: return
    val restrictions = data["restrictions"]?.jsonArray.orEmpty().map { it.boardDecode<BoardRestriction>() }
    val actions = data["actions"]?.jsonArray.orEmpty().map { it.boardDecode<BoardModerationAction>() }
    if(restrictions.isEmpty() && actions.isEmpty()) BoardLabel(m, "You have no moderation notices.")
    restrictions.forEach { restriction -> BoardCard(m) {
        BoardLabel(m, restriction.kind.replaceFirstChar { it.uppercase() }, 48f, true)
        BoardLabel(m, restriction.reason)
        BoardLabel(m, "Case ${restriction.id}", 30f)
        if(restriction.revokedAt != null) BoardLabel(m, "This restriction was revoked.")
        if(restriction.boardId == null || restriction.central) {
            BoardLabel(m, "To appeal a PocketPass staff action, contact us on Discord and include the case reference.")
            BoardButton(m, "Open Discord", "appeal_discord_${restriction.id}") { uri.openUri("https://pocketpass.xyz/#community") }
        } else BoardAppealForm(m, requireNotNull(restriction.boardId), restriction.id, s.busy, send)
    } }
    actions.filter { it.reason.isNotBlank() }.forEach { action -> BoardCard(m) {
        BoardLabel(m, action.reason)
        BoardLabel(m, "Case ${action.id}", 30f)
        if(action.central || action.boardId == null) BoardButton(m, "Appeal through Discord", "action_discord_${action.id}") { uri.openUri("https://pocketpass.xyz/#community") }
        else BoardAppealForm(m, requireNotNull(action.boardId), action.id, s.busy, send)
    } }
    data["appeals"]?.jsonArray.orEmpty().map { it.boardDecode<BoardAppeal>() }.forEach { appeal -> BoardCard(m) {
        BoardLabel(m, "Appeal · ${appeal.status}", 44f, true)
        BoardLabel(m, appeal.resolution.ifBlank { "The board moderators will review your appeal." })
    } }
}
@Composable
private fun BoardAppealForm(m: DesignMetrics, board: String, case: String, busy: Boolean, send: (BoardAction)->Unit) {
    var text by remember(case) { mutableStateOf("") }
    BoardField(m, text, { text = it.take(1000) }, "Write an appeal to the board moderators", true)
    BoardButton(m, "Send appeal", "appeal_$case", enabled = text.isNotBlank() && !busy) { send(BoardAction.Mutate("appeal", boardArgs("board_id" to board.boardValue(), "case_id" to case.boardValue(), "reason" to text.boardValue()))) }
}
