package com.pocketpass.app.ui.phone

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import com.pocketpass.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.ConversationMember
import com.pocketpass.app.domain.model.ConversationMemberRole
import com.pocketpass.app.domain.model.GROUP_TITLE_MAX_LENGTH
import com.pocketpass.app.domain.model.MAX_GROUP_MEMBERS
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.model.GroupComposerState
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.components.AvatarCollage
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.screens.DynamicAvatar
import com.pocketpass.app.ui.screens.PickerMember
import com.pocketpass.app.ui.screens.cancelButtonBrush
import com.pocketpass.app.ui.screens.groupMemberTag
import com.pocketpass.app.ui.screens.redButtonBrush
import com.pocketpass.app.ui.screens.toPickerMembers
import com.pocketpass.app.ui.theme.pocketPalette

private const val CREATE_BUTTON_HEIGHT = 150f
private const val MEMBER_AVATAR = 96f
private val GroupInk = Color(0xFF386F7F)
private val GroupInkSoft = Color(0xFF5591A4)
private val GroupTile = Color(0xFFD1EDFB)
private val GroupDivider = Color(0xFF76B3C1)
private val GroupError = Color(0xFF9D3131)
private val GroupSheetBorder = listOf(Color(0xFF76B3C1), Color(0xFF5A96A9), Color(0xFF22677C))
private val CheckFill = Color(0xFF3CBC29)
private val CheckBorder = Color(0xFF2F9A20)

@Composable
fun PhoneNewGroupPage(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val insets = LocalPhoneInsets.current
    val composer = state.groupComposer ?: GroupComposerState()
    val people = remember(state.friends, state.friendsSort) { state.friends.toPhonePeople(state.friendsSort) }
    val selectedIds = remember(composer.selectedMemberIds) { composer.selectedMemberIds.map { it.value }.toSet() }
    val disabledIds = if (composer.remainingSlots == 0) {
        people.map { it.id }.filterNot { it in selectedIds }.toSet()
    } else {
        emptySet()
    }
    val bottomClear = maxOf(insets.bottom, insets.ime) + 24f
    Box(
        Modifier
            .fillMaxSize()
            .testTag("new_group_page"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .widthIn(max = metrics.dp(PHONE_DECK_WIDTH))
                .fillMaxHeight(),
        ) {
            PhonePeopleGrid(
                metrics = metrics,
                people = people,
                colors = homeCardColors(),
                topInset = insets.top,
                header = { PhoneNewGroupHeader(metrics, composer, dispatch) },
                empty = {
                    PhoneEmptyRow(
                        metrics = metrics,
                        icon = Assets.NavFriends,
                        title = "No friends yet",
                        subtitle = "Add friends before starting a group",
                        tag = "group_members_empty",
                    )
                },
                onPerson = { dispatch(PocketPassEvent.ToggleGroupMember(it)) },
                selectedIds = selectedIds,
                disabledIds = disabledIds,
                tagPrefix = "group_member",
                footer = {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "create_clearance") {
                        Spacer(Modifier.height(metrics.dp(bottomClear + CREATE_BUTTON_HEIGHT)))
                    }
                },
            )
            PhoneButton(
                metrics = metrics,
                label = if (composer.submitting) "Creating…" else "Create Group",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = metrics.dp(PHONE_CONTENT_MARGIN),
                        end = metrics.dp(PHONE_CONTENT_MARGIN),
                        bottom = metrics.dp(bottomClear),
                    )
                    .fillMaxWidth(),
                enabled = composer.canSubmit,
                height = CREATE_BUTTON_HEIGHT,
                tag = "group_create",
                onClick = { dispatch(PocketPassEvent.CreateGroup) },
            )
        }
    }
}

@Composable
private fun PhoneNewGroupHeader(
    metrics: DesignMetrics,
    composer: GroupComposerState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        PhonePageHeader(
            metrics = metrics,
            title = "New Group",
            subtitle = "Pick friends and name it",
            backTag = "new_group_back",
            onBack = { dispatch(PocketPassEvent.Back) },
            horizontalPadding = 0f,
        )
        Spacer(Modifier.height(metrics.dp(32f)))
        PhoneTextField(
            metrics = metrics,
            value = composer.title,
            onValueChange = { dispatch(PocketPassEvent.UpdateGroupTitle(it.take(GROUP_TITLE_MAX_LENGTH))) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Group name",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            tag = "group_title_field",
        )
        Spacer(Modifier.height(metrics.dp(20f)))
        val error = composer.error
        Text(
            text = error ?: "${composer.selectedMemberIds.size}/$MAX_GROUP_MEMBERS selected",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.dp(30f))
                .testTag(if (error == null) "group_selection_count" else "group_composer_error"),
            color = if (error == null) pocketPalette.tealSoft else pocketPalette.ink(GroupError),
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PhoneGroupInfoSheet(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val current = state.selectedConversation?.takeIf { it.isGroup }
    val retained = remember { mutableStateOf(current) }
    if (current != null) retained.value = current
    val conversation = retained.value ?: return
    val visible = state.routes.lastOrNull() is PocketPassRoute.MessageDetail &&
        state.groupInfoOpen &&
        state.selectedConversation?.isGroup == true
    val insets = LocalPhoneInsets.current
    val palette = pocketPalette
    val selfId = state.profile?.userId
    val busy = state.groupOperationInProgress
    val error = state.groupOperationError
    val isOwner = state.isGroupOwner
    val candidates = remember(state.friends, conversation.members) {
        state.friends.filter { conversation.member(it.profile.userId) == null }.toPickerMembers()
    }
    var draft by remember(conversation.id) { mutableStateOf(conversation.title) }
    var pendingRemove by remember(conversation.id) { mutableStateOf<ConversationMember?>(null) }
    var pickerOpen by remember(conversation.id) { mutableStateOf(false) }
    var leaveOpen by remember(conversation.id) { mutableStateOf(false) }
    val retainedRemove = remember { mutableStateOf(pendingRemove) }
    if (pendingRemove != null) retainedRemove.value = pendingRemove
    LaunchedEffect(busy) {
        if (!busy && error == null) {
            pendingRemove = null
            pickerOpen = false
            leaveOpen = false
        }
    }
    LaunchedEffect(visible) {
        if (!visible) {
            pendingRemove = null
            pickerOpen = false
            leaveOpen = false
        }
    }
    val trimmedDraft = draft.trim()
    val canSave = trimmedDraft.isNotEmpty() && trimmedDraft != conversation.title && !busy
    val subtitle = "${conversation.memberCount} members" + if (isOwner) " · You're the owner" else ""
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(palette.scrim)
                    .testTag("group_info_scrim")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { dispatch(PocketPassEvent.CloseGroupInfo) },
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 360f)) { it } +
                fadeIn(tween(160)),
            exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it } +
                fadeOut(tween(140)),
        ) {
            val shape = RoundedCornerShape(
                topStart = metrics.dp(96f),
                topEnd = metrics.dp(96f),
                bottomEnd = 0.dp,
                bottomStart = 0.dp,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .pocketFrame(
                        Brush.verticalGradient(listOf(palette.surface, palette.tint(GroupTile))),
                        metrics.dp(15f),
                        Brush.verticalGradient(GroupSheetBorder),
                        shape,
                    )
                    .testTag("group_info_panel")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(
                        start = metrics.dp(insets.start + 60f),
                        end = metrics.dp(insets.end + 60f),
                        top = metrics.dp(26f),
                        bottom = metrics.dp(maxOf(insets.bottom, insets.ime) + 48f),
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .width(metrics.dp(120f))
                        .height(metrics.dp(12f))
                        .clip(RoundedCornerShape(metrics.dp(6f)))
                        .background(GroupInkSoft.copy(alpha = 0.55f)),
                )
                Spacer(Modifier.height(metrics.dp(30f)))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .requiredSize(metrics.dp(110f))
                            .clip(CircleShape)
                            .pocketFrame(palette.tint(GroupTile), metrics.dp(7f), GroupDivider, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        AvatarCollage(
                            metrics = metrics,
                            members = conversation.othersThan(selfId),
                            size = 110f,
                            initialColor = palette.ink(GroupInk),
                            tileFill = palette.tint(GroupTile),
                            divider = GroupDivider,
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = metrics.dp(28f)),
                    ) {
                        Text(
                            text = conversation.title,
                            color = palette.teal,
                            fontFamily = Rubik,
                            fontWeight = FontWeight.Bold,
                            fontSize = metrics.sp(56f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = subtitle,
                            color = palette.ink(GroupInkSoft),
                            fontFamily = Rubik,
                            fontWeight = FontWeight.Medium,
                            fontSize = metrics.sp(32f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    PhoneRoundAction(
                        metrics = metrics,
                        borderColor = palette.tealBorder,
                        tint = palette.tint(GroupTile),
                        tag = "close_group_info",
                        onClick = { dispatch(PocketPassEvent.CloseGroupInfo) },
                    ) { CloseGlyph(metrics, palette.teal) }
                }
                if (isOwner) {
                    Spacer(Modifier.height(metrics.dp(28f)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PhoneTextField(
                            metrics = metrics,
                            value = draft,
                            onValueChange = { draft = it.take(GROUP_TITLE_MAX_LENGTH) },
                            modifier = Modifier.weight(1f),
                            placeholder = "Group name",
                            fontSize = 44f,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done,
                            ),
                            minHeight = 130f,
                            radius = 65f,
                            borderWidth = 14f,
                            horizontalPadding = 40f,
                            verticalPadding = 24f,
                            tag = "group_rename_field",
                            enabled = !busy,
                        )
                        PhoneTextAction(
                            metrics = metrics,
                            label = "Save",
                            tag = "group_rename",
                            enabled = canSave,
                            color = palette.teal,
                            onClick = { dispatch(PocketPassEvent.RenameGroup(trimmedDraft)) },
                        )
                    }
                }
                Spacer(Modifier.height(metrics.dp(28f)))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = metrics.dp(metrics.designHeight * 0.45f))
                        .testTag("group_member_list"),
                    verticalArrangement = Arrangement.spacedBy(metrics.dp(8f)),
                ) {
                    items(conversation.members, key = { it.userId.value }) { member ->
                        val isSelf = member.userId == selfId
                        PhoneGroupMemberRow(
                            metrics = metrics,
                            member = member,
                            isSelf = isSelf,
                            removable = isOwner && !isSelf,
                            busy = busy,
                            onRemove = { pendingRemove = member },
                        )
                    }
                }
                Spacer(Modifier.height(metrics.dp(30f)))
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(20f))) {
                    PhoneButton(
                        metrics = metrics,
                        label = "Add Members",
                        modifier = Modifier.weight(1f),
                        enabled = !busy && state.canAddGroupMembers && candidates.isNotEmpty(),
                        fontSize = 42f,
                        tag = "group_add_members",
                        onClick = { pickerOpen = true },
                    )
                    PhoneButton(
                        metrics = metrics,
                        label = "Leave Group",
                        modifier = Modifier.weight(1f),
                        fill = redButtonBrush(),
                        borderColor = PhoneRedBorder,
                        enabled = !busy,
                        fontSize = 42f,
                        tag = "group_leave",
                        onClick = { leaveOpen = true },
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(metrics.dp(18f)))
                    Text(
                        text = error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_operation_error"),
                        color = palette.ink(GroupError),
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
        val removeTarget = retainedRemove.value
        PhoneConfirmDialog(
            metrics = metrics,
            visible = visible && pendingRemove != null,
            tag = "group_remove",
            title = "Remove ${removeTarget?.displayName?.trim().orEmpty().ifEmpty { "this member" }}?",
            body = "They'll be removed from ${conversation.title} right away.",
            confirmLabel = if (busy) "Removing…" else "Remove",
            confirmEnabled = !busy,
            error = error,
            onCancel = { pendingRemove = null },
            onConfirm = { pendingRemove?.let { dispatch(PocketPassEvent.RemoveGroupMember(it.userId.value)) } },
        )
        PhoneMemberPickerDialog(
            metrics = metrics,
            visible = visible && pickerOpen,
            candidates = candidates,
            slots = (MAX_GROUP_MEMBERS - conversation.memberCount).coerceAtLeast(0),
            busy = busy,
            error = error,
            onDismiss = { pickerOpen = false },
            onAdd = { ids -> dispatch(PocketPassEvent.AddGroupMembers(ids.map { it.value })) },
        )
        PhoneConfirmDialog(
            metrics = metrics,
            visible = visible && leaveOpen,
            tag = "group_leave",
            title = "Leave this group?",
            body = "You'll stop receiving messages from ${conversation.title}. A member can add you back.",
            confirmLabel = if (busy) "Leaving…" else "Leave",
            confirmEnabled = !busy,
            error = error,
            onCancel = { leaveOpen = false },
            onConfirm = { dispatch(PocketPassEvent.LeaveGroup) },
        )
    }
}

@Composable
private fun PhoneGroupMemberRow(
    metrics: DesignMetrics,
    member: ConversationMember,
    isSelf: Boolean,
    removable: Boolean,
    busy: Boolean,
    onRemove: () -> Unit,
) {
    val palette = pocketPalette
    val name = member.displayName.trim().ifEmpty { "PocketPass User" }
    val chip = when {
        member.role == ConversationMemberRole.Owner && isSelf -> "Owner · You"
        member.role == ConversationMemberRole.Owner -> "Owner"
        isSelf -> "You"
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("group_info_member_${member.userId.value}")
            .padding(vertical = metrics.dp(8f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhoneMemberAvatar(metrics, MEMBER_AVATAR, name, member.avatar)
        Text(
            text = name,
            modifier = Modifier
                .weight(1f)
                .padding(start = metrics.dp(24f)),
            color = palette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(40f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (chip != null) {
            Box(
                modifier = Modifier
                    .padding(start = metrics.dp(16f))
                    .clip(RoundedCornerShape(metrics.dp(26f)))
                    .background(palette.ink(GroupInk).copy(alpha = 0.14f))
                    .padding(horizontal = metrics.dp(22f), vertical = metrics.dp(8f)),
            ) {
                Text(
                    text = chip,
                    color = palette.ink(GroupInk),
                    fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = metrics.sp(26f),
                    maxLines = 1,
                )
            }
        }
        if (removable) {
            PhoneTextAction(
                metrics = metrics,
                label = "Remove",
                tag = "group_remove_${member.userId.value}",
                enabled = !busy,
                fontSize = 34f,
                color = palette.ink(GroupError),
                modifier = Modifier.padding(start = metrics.dp(4f)),
                onClick = onRemove,
            )
        }
    }
}

@Composable
private fun PhoneMemberAvatar(
    metrics: DesignMetrics,
    size: Float,
    name: String,
    avatar: AvatarReference?,
) {
    val palette = pocketPalette
    Box(
        modifier = Modifier
            .requiredSize(metrics.dp(size))
            .clip(CircleShape)
            .pocketFrame(palette.tint(GroupTile), metrics.dp(6f), GroupDivider, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            color = palette.ink(GroupInk),
            fontFamily = Rubik,
            fontWeight = FontWeight.Black,
            fontSize = metrics.sp(size * 0.46f),
            maxLines = 1,
        )
        DynamicAvatar(
            avatar = avatar,
            fallbackResource = null,
            modifier = Modifier
                .requiredSize(metrics.dp(size))
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun PhoneMemberPickerDialog(
    metrics: DesignMetrics,
    visible: Boolean,
    candidates: List<PickerMember>,
    slots: Int,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onAdd: (List<UserId>) -> Unit,
) {
    val palette = pocketPalette
    var selection by remember(visible) { mutableStateOf<Set<UserId>>(emptySet()) }
    val chosen = candidates.filter { it.id in selection }
    PhoneDialog(metrics, visible, tag = "group_picker", onDismiss = onDismiss) {
        Text(
            text = "Add Members",
            modifier = Modifier.fillMaxWidth(),
            color = palette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(70f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(metrics.dp(12f)))
        Text(
            text = when {
                slots <= 0 -> "This group is full"
                slots == 1 -> "Room for one more"
                else -> "Room for $slots more"
            },
            modifier = Modifier.fillMaxWidth(),
            color = palette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(metrics.dp(32f)))
        candidates.forEach { member ->
            val selected = member.id in selection
            PhonePickerRow(
                metrics = metrics,
                member = member,
                selected = selected,
                enabled = !busy && (selected || selection.size < slots),
                onToggle = {
                    selection = if (selected) selection - member.id else selection + member.id
                },
            )
        }
        if (error != null) {
            Spacer(Modifier.height(metrics.dp(20f)))
            Text(
                text = error,
                modifier = Modifier.fillMaxWidth(),
                color = palette.ink(GroupError),
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(30f),
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(metrics.dp(44f)))
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(20f))) {
            PhoneButton(
                metrics = metrics,
                label = "Cancel",
                modifier = Modifier.weight(1f),
                fill = cancelButtonBrush(),
                borderColor = PhoneGreyBorder,
                fontSize = 44f,
                tag = "group_picker_cancel",
                onClick = onDismiss,
            )
            PhoneButton(
                metrics = metrics,
                label = if (busy) "Adding…" else "Add ${chosen.size}",
                modifier = Modifier.weight(1f),
                enabled = chosen.isNotEmpty() && !busy,
                fontSize = 44f,
                tag = "group_add_confirm",
                onClick = { onAdd(chosen.map { it.id }) },
            )
        }
    }
}

@Composable
private fun PhonePickerRow(
    metrics: DesignMetrics,
    member: PickerMember,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val palette = pocketPalette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.dp(40f)))
            .testTag(groupMemberTag(member.id))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = metrics.dp(12f), vertical = metrics.dp(12f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            PhoneMemberAvatar(metrics, MEMBER_AVATAR, member.name, member.avatar)
            if (member.online) {
                FigmaAsset(
                    resource = Assets.OnlineDot,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .requiredSize(metrics.dp(40f)),
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = metrics.dp(24f)),
        ) {
            Text(
                text = member.name,
                color = palette.textPrimary,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(40f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = member.detail,
                color = palette.textSecondary,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(28f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .requiredSize(metrics.dp(60f))
                .clip(CircleShape)
                .pocketFrame(
                    if (selected) CheckFill else Color.Transparent,
                    metrics.dp(7f),
                    if (selected) CheckBorder else palette.borderGrey,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text(
                    text = "✓",
                    color = Color.White,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Bold,
                    fontSize = metrics.sp(34f),
                    maxLines = 1,
                )
            }
        }
    }
}
