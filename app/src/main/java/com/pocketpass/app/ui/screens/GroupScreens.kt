package com.pocketpass.app.ui.screens

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.ConversationMember
import com.pocketpass.app.domain.model.ConversationMemberRole
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.FORMER_MEMBER_LABEL
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.GROUP_TITLE_MAX_LENGTH
import com.pocketpass.app.domain.model.MAX_GROUP_MEMBERS
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.model.GroupComposerState
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.BOTTOM_DESIGN_HEIGHT
import com.pocketpass.app.ui.BOTTOM_DESIGN_WIDTH
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.TOP_DESIGN_HEIGHT
import com.pocketpass.app.ui.TOP_DESIGN_WIDTH
import com.pocketpass.app.ui.components.AvatarCollage
import com.pocketpass.app.ui.components.EntranceMotion
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.POCKET_KEYBOARD_HEIGHT
import com.pocketpass.app.ui.components.PatternBackground
import com.pocketpass.app.ui.components.PocketKey
import com.pocketpass.app.ui.components.PocketKeyboard
import com.pocketpass.app.ui.components.PocketKeyboardLayout
import com.pocketpass.app.ui.components.PocketKeyboardPalette
import com.pocketpass.app.ui.components.TYPING_CARET_INLINE_ID
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.components.pocketShadow
import com.pocketpass.app.ui.components.typingCaretInline
import com.pocketpass.app.ui.controller.ControllerFocusViewport
import com.pocketpass.app.ui.controller.FocusDirection
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.LocalControllerFocusViewport
import com.pocketpass.app.ui.controller.controllerFocusBarrier
import com.pocketpass.app.ui.controller.controllerFocusViewport
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.theme.pocketPalette
import kotlin.time.Instant

internal data class SenderLabel(
    val name: String,
    val avatar: AvatarReference?,
)

internal fun senderLabelFor(
    conversation: ConversationSummary?,
    previous: Message?,
    message: Message,
    selfId: UserId?,
): SenderLabel? {
    if (conversation?.isGroup != true || message.senderId == selfId) return null
    if (previous?.senderId == message.senderId) return null
    val member = conversation.member(message.senderId)
    return SenderLabel(
        name = member?.displayName?.trim()?.ifEmpty { null } ?: FORMER_MEMBER_LABEL,
        avatar = member?.avatar,
    )
}

internal fun typingNames(
    conversation: ConversationSummary,
    typingUserIds: Set<UserId>,
): String? = conversation.members
    .filter { it.userId in typingUserIds }
    .map { it.displayName }
    .takeIf { it.isNotEmpty() }
    ?.joinToString(", ")

internal fun groupSubtitle(
    conversation: ConversationSummary,
    typingUserIds: Set<UserId>,
): String = typingNames(conversation, typingUserIds)?.let { "$it typing…" }
    ?: "${conversation.memberCount} members"

internal data class PickerMember(
    val id: UserId,
    val name: String,
    val avatar: AvatarReference?,
    val online: Boolean,
    val detail: String,
)

internal fun Friend.toPickerMember(): PickerMember {
    val name = profile.displayName.trim().ifBlank { "PocketPass User" }
    return PickerMember(
        id = profile.userId,
        name = name,
        avatar = profile.avatar,
        online = isOnline,
        detail = if (isOnline) "Now" else profile.lastSeenAt?.let(::relativeTime) ?: "Offline",
    )
}

internal fun List<Friend>.toPickerMembers(): List<PickerMember> =
    sortedBy { it.profile.displayName.lowercase() }.map { it.toPickerMember() }

internal fun groupMemberTag(userId: UserId): String = "group_member_${userId.value}"

@Composable
internal fun NewGroupBottom(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    BottomPage(entrance = EntranceMotion.None) { metrics ->
        val composer = state.groupComposer ?: GroupComposerState()
        val focus = LocalControllerFocus.current
        val candidates = remember(state.friends) { state.friends.toPickerMembers() }
        var keyboardVisible by remember { mutableStateOf(true) }
        val keyboardProgress by animateFloatAsState(
            targetValue = if (keyboardVisible) 1f else 0f,
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            label = "group keyboard",
        )
        LaunchedEffect(Unit) { focus?.focus(GROUP_TITLE_FIELD_TAG, reveal = false) }

        PatternBackground(
            metrics = metrics,
            pattern = Assets.MessagesDetailPattern,
            topColor = pocketPalette.tint(Color(0xFFE9F1F6)),
            bottomColor = pocketPalette.tint(Color(0xFFD1EDFB)),
            holdFraction = 0.4375f,
            designWidth = BOTTOM_DESIGN_WIDTH,
            designHeight = BOTTOM_DESIGN_HEIGHT,
        )
        SubpageHeader(
            metrics = metrics,
            title = "New Group",
            subtitle = "Pick friends and name it",
            backTag = "new_group_back",
            onBack = { dispatch(PocketPassEvent.Back) },
        )
        Text(
            text = "${composer.selectedMemberIds.size}/$MAX_GROUP_MEMBERS selected",
            modifier = Modifier.designBounds(metrics, 50f, 404f, 560f, 40f),
            color = pocketPalette.tealSoft,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            maxLines = 1,
        )
        composer.error?.let { error ->
            Text(
                text = error,
                modifier = Modifier.designBounds(metrics, 610f, 404f, 580f, 40f),
                color = pocketPalette.ink(Color(0xFFB31E3A)),
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(30f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (candidates.isEmpty()) {
            GroupNotice(
                metrics = metrics,
                x = 50f,
                y = 456f,
                width = 1140f,
                height = 200f,
                title = "No friends yet",
                subtitle = "Add friends before starting a group",
                tag = "group_members_empty",
            )
        } else {
            MemberPickerPanel(
                metrics = metrics,
                x = 50f,
                y = 456f,
                width = 1140f,
                height = 424f,
                members = candidates,
                selected = composer.selectedMemberIds,
                remainingSlots = composer.remainingSlots,
                layer = 0,
                onToggle = { dispatch(PocketPassEvent.ToggleGroupMember(it.value)) },
            )
        }
        GroupActionButton(
            metrics = metrics,
            x = 320f,
            y = 900f,
            width = 600f,
            height = 130f,
            label = if (composer.submitting) "Creating…" else "Create Group",
            textColor = Color.White,
            fill = greenButtonBrush(),
            borderColor = Color(0xFF4FC24B),
            enabled = composer.canSubmit,
            tag = "group_create",
        ) { dispatch(PocketPassEvent.CreateGroup) }

        if (keyboardVisible) {
            Box(
                Modifier
                    .designBounds(
                        metrics,
                        0f,
                        KEYBOARD_BARRIER_TOP,
                        BOTTOM_DESIGN_WIDTH,
                        BOTTOM_DESIGN_HEIGHT - KEYBOARD_BARRIER_TOP,
                    )
                    .controllerFocusBarrier("group_title_keyboard_barrier", layer = GROUP_KEYBOARD_LAYER)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { keyboardVisible = false },
            )
        }
        GroupTitleField(
            metrics = metrics,
            x = 50f,
            y = 270f,
            width = 1140f,
            height = 120f,
            value = composer.title,
            placeholder = "Group name",
            caret = keyboardVisible,
            tag = GROUP_TITLE_FIELD_TAG,
            layer = if (keyboardVisible) GROUP_KEYBOARD_LAYER else 0,
        ) { keyboardVisible = true }
        if (keyboardProgress > 0.001f) {
            PocketKeyboard(
                metrics = metrics,
                layout = PocketKeyboardLayout.Text,
                submitLabel = "Done",
                submitEnabled = true,
                canBackspace = composer.title.isNotEmpty(),
                onKey = { key ->
                    when (key) {
                        is PocketKey.Character -> dispatch(
                            PocketPassEvent.UpdateGroupTitle(composer.title + key.value),
                        )

                        PocketKey.Space -> dispatch(
                            PocketPassEvent.UpdateGroupTitle("${composer.title} "),
                        )

                        PocketKey.Backspace -> dispatch(
                            PocketPassEvent.UpdateGroupTitle(composer.title.dropLast(1)),
                        )

                        PocketKey.Submit -> {
                            keyboardVisible = false
                            focus?.focus(
                                candidates.firstOrNull()?.let { groupMemberTag(it.id) } ?: "group_create",
                                reveal = false,
                            )
                        }

                        PocketKey.Alphabet -> Unit
                    }
                },
                modifier = Modifier.graphicsLayer {
                    translationY = (1f - keyboardProgress) * POCKET_KEYBOARD_HEIGHT
                },
                palette = PocketKeyboardPalette.Messages,
                focusLayer = GROUP_KEYBOARD_LAYER,
                focusReturnTag = GROUP_TITLE_FIELD_TAG,
                topRowUpTarget = { GROUP_TITLE_FIELD_TAG },
            )
        }
    }
}

@Composable
private fun GroupTitleField(
    metrics: DesignMetrics,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    value: String,
    placeholder: String,
    caret: Boolean,
    tag: String,
    layer: Int,
    onClick: () -> Unit,
) {
    val palette = pocketPalette
    val shape = RoundedCornerShape(metrics.dp(height / 2f))
    Box(
        modifier = Modifier
            .designBounds(metrics, x, y, width, height)
            .clip(shape)
            .pocketFrame(palette.surfaceSunken, metrics.dp(8f), palette.tealBorder, shape)
            .testTag(tag)
            .controllerTarget(tag, layer = layer, cornerRadius = height / 2f) { onClick() }
            .clickable(
                interactionSource = remember(tag) { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        val caretInline = typingCaretInline(metrics, palette.teal, 52f)
        Text(
            text = buildAnnotatedString {
                if (value.isEmpty()) {
                    if (caret) appendInlineContent(TYPING_CARET_INLINE_ID, "|")
                    append(placeholder)
                } else {
                    append(value)
                    if (caret) appendInlineContent(TYPING_CARET_INLINE_ID, "|")
                }
            },
            inlineContent = caretInline,
            modifier = Modifier.padding(start = metrics.dp(48f), end = metrics.dp(200f)),
            color = if (value.isEmpty()) palette.ink(Color(0xFF8FB9C6)) else palette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.Medium,
            fontSize = metrics.sp(52f),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = "${value.length}/$GROUP_TITLE_MAX_LENGTH",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = metrics.dp(44f)),
            color = palette.tealBorder,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(30f),
            maxLines = 1,
        )
    }
}

@Composable
private fun GroupNotice(
    metrics: DesignMetrics,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    title: String,
    subtitle: String,
    tag: String,
) {
    val shape = RoundedCornerShape(metrics.dp(60f))
    Box(
        modifier = Modifier
            .designBounds(metrics, x, y, width, height)
            .clip(shape)
            .pocketFrame(greyPanelBrush(), metrics.dp(16f), pocketPalette.borderGrey, shape)
            .testTag(tag),
    ) {
        Text(
            text = title,
            modifier = Modifier.designBounds(metrics, 60f, 42f, width - 120f, 70f),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(56f),
            maxLines = 1,
        )
        Text(
            text = subtitle,
            modifier = Modifier.designBounds(metrics, 60f, 112f, width - 120f, 50f),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(38f),
            maxLines = 1,
        )
    }
}

@Composable
private fun MemberPickerPanel(
    metrics: DesignMetrics,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    members: List<PickerMember>,
    selected: Set<UserId>,
    remainingSlots: Int,
    layer: Int,
    onToggle: (UserId) -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(60f))
    val viewport = remember(shape) { ControllerFocusViewport(shape = shape) }
    val scroll = rememberScrollState()
    Box(
        Modifier
            .designBounds(metrics, x, y + 14f, width, height)
            .pocketShadow(metrics, 60f),
    )
    Box(
        Modifier
            .designBounds(metrics, x, y, width, height)
            .clip(shape)
            .pocketFrame(
                pocketPalette.surface,
                metrics.dp(16f),
                Brush.verticalGradient(
                    listOf(Color(0xFF76B3C1), Color(0xFF5E9AAC), Color(0xFF22677C)),
                ),
                shape,
            )
            .controllerFocusViewport(viewport),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
        ) {
            val contentHeight = (members.size * MEMBER_ROW_HEIGHT + MEMBER_ROW_INSET * 2f)
                .coerceAtLeast(height)
            Box(
                Modifier
                    .requiredWidth(metrics.dp(width))
                    .requiredHeight(metrics.dp(contentHeight)),
            ) {
                CompositionLocalProvider(LocalControllerFocusViewport provides viewport) {
                    members.forEachIndexed { index, member ->
                        val isSelected = member.id in selected
                        MemberPickerRow(
                            metrics = metrics,
                            x = MEMBER_ROW_MARGIN,
                            y = MEMBER_ROW_INSET + index * MEMBER_ROW_HEIGHT,
                            width = width - MEMBER_ROW_MARGIN * 2f,
                            member = member,
                            selected = isSelected,
                            enabled = isSelected || remainingSlots > 0,
                            layer = layer,
                            onToggle = { onToggle(member.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MemberPickerRow(
    metrics: DesignMetrics,
    x: Float,
    y: Float,
    width: Float,
    member: PickerMember,
    selected: Boolean,
    enabled: Boolean,
    layer: Int,
    onToggle: () -> Unit,
) {
    val palette = pocketPalette
    val tag = groupMemberTag(member.id)
    val active = enabled || selected
    Box(
        Modifier
            .designBounds(metrics, x, y, width, MEMBER_ROW_HEIGHT)
            .graphicsLayer { alpha = if (active) 1f else 0.5f }
            .testTag(tag)
            .controllerTarget(tag, layer = layer, cornerRadius = 40f) { if (active) onToggle() }
            .clickable(
                interactionSource = remember(tag) { MutableInteractionSource() },
                indication = null,
                enabled = active,
                onClick = onToggle,
            ),
    ) {
        Box(
            modifier = Modifier
                .designBounds(metrics, 40f, 20f, 100f, 100f)
                .clip(CircleShape)
                .pocketFrame(palette.tint(Color(0xFFD1EDFB)), metrics.dp(6f), Color(0xFF76B3C1), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = member.name.firstOrNull()?.uppercase() ?: "?",
                color = palette.ink(Color(0xFF386F7F)),
                fontFamily = Rubik,
                fontWeight = FontWeight.Black,
                fontSize = metrics.sp(46f),
                maxLines = 1,
            )
            DynamicAvatar(
                avatar = member.avatar,
                fallbackResource = null,
                modifier = Modifier
                    .requiredSize(metrics.dp(100f))
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
        if (member.online) {
            FigmaAsset(
                resource = Assets.OnlineDot,
                modifier = Modifier.designBounds(metrics, 112f, 8f, 46f, 46f),
            )
        }
        Text(
            text = member.name,
            modifier = Modifier.designBounds(metrics, 170f, 26f, width - 300f, 60f),
            color = palette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(46f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = member.detail,
            modifier = Modifier.designBounds(metrics, 170f, 86f, width - 300f, 40f),
            color = palette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(30f),
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .designBounds(metrics, width - 104f, 40f, 60f, 60f)
                .clip(CircleShape)
                .pocketFrame(
                    if (selected) Color(0xFF3CBC29) else Color.Transparent,
                    metrics.dp(7f),
                    if (selected) Color(0xFF2F9A20) else palette.line(Color(0xFF9FB6C1)),
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

@Composable
internal fun GroupActionButton(
    metrics: DesignMetrics,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    label: String,
    textColor: Color,
    fill: Brush,
    borderColor: Color,
    enabled: Boolean,
    tag: String,
    layer: Int = 0,
    fontSize: Float = 44f,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(height / 2f))
    Box(
        Modifier
            .designBounds(metrics, x, y + 10f, width, height)
            .pocketShadow(metrics, height / 2f, 0.11f, 6f),
    )
    Box(
        modifier = Modifier
            .designBounds(metrics, x, y, width, height)
            .graphicsLayer { alpha = if (enabled) 1f else 0.6f }
            .clip(shape)
            .pocketFrame(fill, metrics.dp(16f), borderColor, shape)
            .testTag(tag)
            .controllerTarget(tag, layer = layer, cornerRadius = height / 2f) {
                if (enabled) onClick()
            }
            .clickable(
                interactionSource = remember(tag) { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(fontSize),
            maxLines = 1,
        )
    }
}

@Composable
fun TopGroupComposer(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    onPresentingChanged: (Boolean) -> Unit = {},
) {
    val composer = state.groupComposer
    var retained by remember { mutableStateOf<GroupComposerState?>(null) }
    val translation = remember { Animatable(TOP_COMPOSER_CLOSED_OFFSET) }
    SideEffect {
        if (composer != null) retained = composer
    }
    LaunchedEffect(composer != null) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            translation.snapTo(if (composer != null) 0f else TOP_COMPOSER_CLOSED_OFFSET)
            if (composer == null) retained = null
            return@LaunchedEffect
        }
        if (composer != null) {
            translation.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        } else if (retained != null) {
            translation.animateTo(TOP_COMPOSER_CLOSED_OFFSET, tween(220, easing = FastOutSlowInEasing))
            retained = null
        }
    }
    val presenting = composer != null || retained != null
    LaunchedEffect(presenting) { onPresentingChanged(presenting) }
    if (!presenting) return
    val shown = composer ?: retained ?: return
    val members = state.friends
        .filter { it.profile.userId in shown.selectedMemberIds }
        .map { friend ->
            ConversationMember(
                userId = friend.profile.userId,
                displayName = friend.profile.displayName,
                avatar = friend.profile.avatar,
                role = ConversationMemberRole.Member,
                joinedAt = Instant.fromEpochSeconds(0),
            )
        }
    val settled = { 1f - (translation.value / TOP_COMPOSER_CLOSED_OFFSET).coerceIn(0f, 1f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = translation.value
                alpha = settled()
            }
            .testTag("top_group_composer"),
    ) {
        PatternBackground(
            metrics = metrics,
            pattern = Assets.PatternMessagesTop,
            topColor = pocketPalette.tint(Color(0xFFDDF0F9)),
            bottomColor = pocketPalette.tint(Color(0xFFE9F1F6)),
            holdFraction = 0f,
            designWidth = TOP_DESIGN_WIDTH,
            designHeight = TOP_DESIGN_HEIGHT,
            alpha = settled,
        )
        val cardShape = RoundedCornerShape(metrics.dp(104f))
        Box(
            Modifier
                .designBounds(metrics, 460f, 166f, 1000f, 780f)
                .pocketShadow(metrics, 104f),
        )
        Box(
            Modifier
                .designBounds(metrics, 460f, 150f, 1000f, 780f)
                .clip(cardShape)
                .pocketFrame(
                    pocketPalette.surface,
                    metrics.dp(20.152f),
                    Brush.verticalGradient(
                        listOf(Color(0xFF76B3C1), Color(0xFF5E9AAC), Color(0xFF22677C)),
                    ),
                    cardShape,
                ),
        ) {
            Box(
                modifier = Modifier
                    .designBounds(metrics, 370f, 60f, 260f, 260f)
                    .clip(CircleShape)
                    .pocketFrame(
                        pocketPalette.tint(Color(0xFFD1EDFB)),
                        metrics.dp(12f),
                        Color(0xFF76B3C1),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AvatarCollage(
                    metrics = metrics,
                    members = members,
                    size = 260f,
                    initialColor = pocketPalette.ink(Color(0xFF386F7F)),
                    tileFill = pocketPalette.tint(Color(0xFFD1EDFB)),
                    divider = Color(0xFF76B3C1),
                )
            }
            Text(
                text = shown.title.ifBlank { "New Group" },
                modifier = Modifier.designBounds(metrics, 60f, 360f, 880f, 110f),
                color = pocketPalette.teal,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(96f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${members.size + 1} of $MAX_GROUP_MEMBERS members",
                modifier = Modifier.designBounds(metrics, 60f, 478f, 880f, 60f),
                color = pocketPalette.tealSoft,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(52f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = members.joinToString(", ") { it.displayName }
                    .ifBlank { "Pick friends on the touch screen" },
                modifier = Modifier.designBounds(metrics, 100f, 566f, 800f, 170f),
                color = pocketPalette.ink(Color(0xFF5591A4)),
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(44f),
                lineHeight = metrics.sp(56f),
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun GroupThreadHeader(
    metrics: DesignMetrics,
    conversation: ConversationSummary,
    subtitle: String,
    selfId: UserId?,
    onBack: () -> Unit,
    onInfo: () -> Unit,
) {
    val ink = pocketPalette.ink(Color(0xFF386F7F))
    Box(
        modifier = Modifier
            .designBounds(metrics, 38f, 50f, 130f, 200f)
            .testTag("message_back")
            .controllerTarget("message_back", cornerRadius = 40f) { onBack() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBack,
            ),
    ) {
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = ColorFilter.tint(ink),
            modifier = Modifier
                .designBounds(metrics, 30f, 66f, 40.372f, 68.725f)
                .graphicsLayer { scaleX = -1f },
        )
    }
    Box(
        modifier = Modifier
            .designBounds(metrics, 170f, 50f, 950f, 200f)
            .testTag("message_group_info")
            .controllerTarget(
                "message_group_info",
                cornerRadius = 100f,
                neighbors = mapOf(FocusDirection.Down to "message_field"),
            ) { onInfo() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onInfo,
            ),
    ) {
        Box(
            modifier = Modifier
                .designBounds(metrics, 12f, 15f, 170f, 170f)
                .clip(CircleShape)
                .pocketFrame(
                    pocketPalette.tint(Color(0xFFD1EDFB)),
                    metrics.dp(10f),
                    Color(0xFF76B3C1),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AvatarCollage(
                metrics = metrics,
                members = conversation.othersThan(selfId),
                size = 170f,
                initialColor = ink,
                tileFill = pocketPalette.tint(Color(0xFFD1EDFB)),
                divider = Color(0xFF76B3C1),
                tag = "avatar_collage_${conversation.id.value}",
            )
        }
        Box(
            modifier = Modifier.designBounds(metrics, 220f, 8f, 720f, 114f),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = conversation.title,
                color = ink,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(96f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier.designBounds(metrics, 224f, 128f, 700f, 72f),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = subtitle,
                color = pocketPalette.ink(Color(0xFF5591A4)),
                fontFamily = Rubik,
                fontWeight = FontWeight.Medium,
                fontSize = metrics.sp(64f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private sealed interface GroupPrompt {
    data object Leave : GroupPrompt

    data class Remove(val member: ConversationMember) : GroupPrompt
}

@Composable
internal fun GroupInfoBottomOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val conversation = state.selectedConversation?.takeIf { it.isGroup } ?: return
    val palette = pocketPalette
    val selfId = state.profile?.userId
    val focus = LocalControllerFocus.current
    val busy = state.groupOperationInProgress
    val isOwner = state.isGroupOwner
    var renameDraft by remember(conversation.id) { mutableStateOf<String?>(null) }
    var prompt by remember(conversation.id) { mutableStateOf<GroupPrompt?>(null) }
    var pickerOpen by remember(conversation.id) { mutableStateOf(false) }
    LaunchedEffect(busy) {
        if (!busy && state.groupOperationError == null) {
            renameDraft = null
            prompt = null
            pickerOpen = false
        }
    }
    LaunchedEffect(Unit) { focus?.focus("close_group_info", reveal = false) }
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
    }
    val candidates = remember(state.friends, conversation.members) {
        state.friends
            .filter { friend -> conversation.member(friend.profile.userId) == null }
            .toPickerMembers()
    }
    val members = conversation.members

    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, BOTTOM_DESIGN_WIDTH, BOTTOM_DESIGN_HEIGHT)
            .background(palette.scrim)
            .testTag("group_info_overlay")
            .controllerFocusBarrier("group_info_overlay", layer = GROUP_INFO_FOCUS_LAYER)
            .clickable(
                enabled = !busy,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { dispatch(PocketPassEvent.CloseGroupInfo) },
    )
    Box(
        Modifier
            .designBounds(metrics, 80f, 74f, 1080f, 960f)
            .graphicsLayer { translationY = entrance.value }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 60f, 1080f, 960f)
            .graphicsLayer { translationY = entrance.value }
            .clip(panelShape)
            .pocketFrame(
                Brush.verticalGradient(
                    listOf(palette.surface, palette.tint(Color(0xFFD1EDFB))),
                ),
                metrics.dp(15f),
                palette.tealBorder,
                panelShape,
            )
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("group_info_panel"),
    ) {
        Box(
            modifier = Modifier
                .designBounds(metrics, 58f, 46f, 110f, 110f)
                .clip(CircleShape)
                .pocketFrame(palette.tint(Color(0xFFD1EDFB)), metrics.dp(7f), Color(0xFF76B3C1), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AvatarCollage(
                metrics = metrics,
                members = conversation.othersThan(selfId),
                size = 110f,
                initialColor = palette.ink(Color(0xFF386F7F)),
                tileFill = palette.tint(Color(0xFFD1EDFB)),
                divider = Color(0xFF76B3C1),
            )
        }
        Text(
            text = conversation.title,
            modifier = Modifier.designBounds(metrics, 190f, 46f, 700f, 94f),
            color = palette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        CloseGlyphButton(
            metrics = metrics,
            x = 945f,
            y = 55f,
            tag = "close_group_info",
            layer = GROUP_INFO_FOCUS_LAYER,
        ) { dispatch(PocketPassEvent.CloseGroupInfo) }
        Text(
            text = buildString {
                append("${members.size} members")
                if (isOwner) append(" · You're the owner")
            },
            modifier = Modifier.designBounds(metrics, 190f, 142f, 760f, 44f),
            color = palette.tealSoft,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            maxLines = 1,
        )
        if (isOwner) {
            val draft = renameDraft
            if (draft == null) {
                GroupActionButton(
                    metrics = metrics,
                    x = 58f,
                    y = 204f,
                    width = 320f,
                    height = 96f,
                    label = "Rename",
                    textColor = palette.textPrimary,
                    fill = greyPanelBrush(),
                    borderColor = palette.borderGrey,
                    enabled = !busy,
                    tag = "group_rename",
                    layer = GROUP_INFO_FOCUS_LAYER,
                    fontSize = 38f,
                ) { renameDraft = conversation.title }
            } else {
                GroupTitleField(
                    metrics = metrics,
                    x = 58f,
                    y = 204f,
                    width = 964f,
                    height = 96f,
                    value = draft,
                    placeholder = "Group name",
                    caret = true,
                    tag = "group_rename_field",
                    layer = GROUP_PROMPT_FOCUS_LAYER,
                ) {}
            }
        }
        GroupMemberList(
            metrics = metrics,
            x = 58f,
            y = 320f,
            width = 964f,
            height = 440f,
            members = members,
            selfId = selfId,
            canRemove = isOwner && !busy,
            onRemove = { prompt = GroupPrompt.Remove(it) },
        )
        GroupActionButton(
            metrics = metrics,
            x = 58f,
            y = 776f,
            width = 460f,
            height = 130f,
            label = "Add Members",
            textColor = palette.textPrimary,
            fill = greyPanelBrush(),
            borderColor = palette.borderGrey,
            enabled = !busy && state.canAddGroupMembers && candidates.isNotEmpty(),
            tag = "group_add_members",
            layer = GROUP_INFO_FOCUS_LAYER,
        ) { pickerOpen = true }
        GroupActionButton(
            metrics = metrics,
            x = 562f,
            y = 776f,
            width = 460f,
            height = 130f,
            label = if (busy) "Working…" else "Leave Group",
            textColor = Color.White,
            fill = redButtonBrush(),
            borderColor = Color(0xFFC24B4B),
            enabled = !busy,
            tag = "group_leave",
            layer = GROUP_INFO_FOCUS_LAYER,
        ) { prompt = GroupPrompt.Leave }
        state.groupOperationError?.let { error ->
            Text(
                text = error,
                modifier = Modifier.designBounds(metrics, 58f, 918f, 964f, 36f),
                color = palette.ink(Color(0xFFB31E3A)),
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(30f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    renameDraft?.let { draft ->
        Box(
            Modifier
                .designBounds(metrics, 0f, 0f, BOTTOM_DESIGN_WIDTH, BOTTOM_DESIGN_HEIGHT)
                .controllerFocusBarrier("group_rename_barrier", layer = GROUP_PROMPT_FOCUS_LAYER)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { renameDraft = null },
        )
        val trimmed = draft.trim()
        PocketKeyboard(
            metrics = metrics,
            layout = PocketKeyboardLayout.Text,
            submitLabel = if (busy) "Saving…" else "Save",
            submitEnabled = trimmed.isNotEmpty() && trimmed != conversation.title && !busy,
            canBackspace = draft.isNotEmpty(),
            onKey = { key ->
                when (key) {
                    is PocketKey.Character ->
                        renameDraft = (draft + key.value).take(GROUP_TITLE_MAX_LENGTH)

                    PocketKey.Space -> renameDraft = "$draft ".take(GROUP_TITLE_MAX_LENGTH)
                    PocketKey.Backspace -> renameDraft = draft.dropLast(1)
                    PocketKey.Submit -> dispatch(PocketPassEvent.RenameGroup(trimmed))
                    PocketKey.Alphabet -> Unit
                }
            },
            palette = PocketKeyboardPalette.Messages,
            focusLayer = GROUP_PROMPT_FOCUS_LAYER,
            focusReturnTag = "group_rename",
            topRowUpTarget = { "group_rename_field" },
        )
    }

    when (val current = prompt) {
        null -> Unit
        GroupPrompt.Leave -> GroupConfirmDialog(
            metrics = metrics,
            title = "Leave this group?",
            body = "You'll stop receiving messages from ${conversation.title}. A member can add you back.",
            confirmLabel = if (busy) "Leaving…" else "Leave",
            tag = "group_leave",
            busy = busy,
            onCancel = { prompt = null },
            onConfirm = { dispatch(PocketPassEvent.LeaveGroup) },
        )

        is GroupPrompt.Remove -> GroupConfirmDialog(
            metrics = metrics,
            title = "Remove ${current.member.displayName}?",
            body = "They'll be removed from ${conversation.title} right away.",
            confirmLabel = if (busy) "Removing…" else "Remove",
            tag = "group_remove",
            busy = busy,
            onCancel = { prompt = null },
            onConfirm = { dispatch(PocketPassEvent.RemoveGroupMember(current.member.userId.value)) },
        )
    }

    if (pickerOpen) {
        MemberPickerOverlay(
            metrics = metrics,
            candidates = candidates,
            cap = (MAX_GROUP_MEMBERS - members.size).coerceAtLeast(0),
            busy = busy,
            onConfirm = { ids -> dispatch(PocketPassEvent.AddGroupMembers(ids.map { it.value })) },
            onClose = { pickerOpen = false },
        )
    }
}

@Composable
private fun GroupMemberList(
    metrics: DesignMetrics,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    members: List<ConversationMember>,
    selfId: UserId?,
    canRemove: Boolean,
    onRemove: (ConversationMember) -> Unit,
) {
    val palette = pocketPalette
    val shape = RoundedCornerShape(metrics.dp(48f))
    val viewport = remember(shape) { ControllerFocusViewport(shape = shape) }
    val scroll = rememberScrollState()
    Box(
        Modifier
            .designBounds(metrics, x, y, width, height)
            .clip(shape)
            .background(palette.surfaceSunken)
            .controllerFocusViewport(viewport),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
        ) {
            val contentHeight = (members.size * INFO_ROW_PITCH + 24f).coerceAtLeast(height)
            Box(
                Modifier
                    .requiredWidth(metrics.dp(width))
                    .requiredHeight(metrics.dp(contentHeight)),
            ) {
                CompositionLocalProvider(LocalControllerFocusViewport provides viewport) {
                    members.forEachIndexed { index, member ->
                        val isSelf = member.userId == selfId
                        val rowTag = "group_info_member_${member.userId.value}"
                        val rowY = 12f + index * INFO_ROW_PITCH
                        Box(
                            Modifier
                                .designBounds(metrics, 16f, rowY, width - 32f, INFO_ROW_HEIGHT)
                                .testTag(rowTag)
                                .controllerTarget(rowTag, layer = GROUP_INFO_FOCUS_LAYER, cornerRadius = 48f) {},
                        ) {
                            Box(
                                modifier = Modifier
                                    .designBounds(metrics, 16f, 14f, 96f, 96f)
                                    .clip(CircleShape)
                                    .pocketFrame(palette.tint(Color(0xFFD1EDFB)), metrics.dp(6f), Color(0xFF76B3C1), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = member.displayName.trim().firstOrNull()?.uppercase() ?: "?",
                                    color = palette.ink(Color(0xFF386F7F)),
                                    fontFamily = Rubik,
                                    fontWeight = FontWeight.Black,
                                    fontSize = metrics.sp(44f),
                                    maxLines = 1,
                                )
                                DynamicAvatar(
                                    avatar = member.avatar,
                                    fallbackResource = null,
                                    modifier = Modifier
                                        .requiredSize(metrics.dp(96f))
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Text(
                                text = member.displayName,
                                modifier = Modifier.designBounds(metrics, 136f, 18f, 560f, 60f),
                                color = palette.textPrimary,
                                fontFamily = Rubik,
                                fontWeight = FontWeight.Bold,
                                fontSize = metrics.sp(46f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val chip = when {
                                member.role == ConversationMemberRole.Owner && isSelf -> "Owner · You"
                                member.role == ConversationMemberRole.Owner -> "Owner"
                                isSelf -> "You"
                                else -> null
                            }
                            if (chip != null) {
                                GroupChip(metrics, x = 136f, y = 80f, label = chip)
                            }
                            if (canRemove && !isSelf) {
                                GroupActionButton(
                                    metrics = metrics,
                                    x = width - 32f - 190f,
                                    y = 26f,
                                    width = 174f,
                                    height = 72f,
                                    label = "Remove",
                                    textColor = Color.White,
                                    fill = redButtonBrush(),
                                    borderColor = Color(0xFFC24B4B),
                                    enabled = true,
                                    tag = "group_remove_${member.userId.value}",
                                    layer = GROUP_INFO_FOCUS_LAYER,
                                    fontSize = 30f,
                                ) { onRemove(member) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupChip(
    metrics: DesignMetrics,
    x: Float,
    y: Float,
    label: String,
) {
    val shape = RoundedCornerShape(metrics.dp(20f))
    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = x
                translationY = y
            }
            .clip(shape)
            .pocketFrame(pocketPalette.tint(Color(0xFFD1EDFB)), metrics.dp(5f), pocketPalette.tealBorder, shape)
            .padding(horizontal = metrics.dp(18f), vertical = metrics.dp(4f)),
    ) {
        Text(
            text = label,
            color = pocketPalette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(26f),
            maxLines = 1,
        )
    }
}

@Composable
private fun CloseGlyphButton(
    metrics: DesignMetrics,
    x: Float,
    y: Float,
    tag: String,
    layer: Int,
    onClick: () -> Unit,
) {
    val ink = pocketPalette.ink(Color(0xFF2F948C))
    Canvas(
        Modifier
            .designBounds(metrics, x, y, 72f, 72f)
            .testTag(tag)
            .controllerTarget(tag, layer = layer) { onClick() }
            .clickable(
                interactionSource = remember(tag) { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        drawLine(
            ink,
            Offset(size.width * 0.2f, size.height * 0.2f),
            Offset(size.width * 0.8f, size.height * 0.8f),
            strokeWidth = 9f,
            cap = StrokeCap.Round,
        )
        drawLine(
            ink,
            Offset(size.width * 0.8f, size.height * 0.2f),
            Offset(size.width * 0.2f, size.height * 0.8f),
            strokeWidth = 9f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun MemberPickerOverlay(
    metrics: DesignMetrics,
    candidates: List<PickerMember>,
    cap: Int,
    busy: Boolean,
    onConfirm: (List<UserId>) -> Unit,
    onClose: () -> Unit,
) {
    val palette = pocketPalette
    val focus = LocalControllerFocus.current
    var selected by remember { mutableStateOf(emptySet<UserId>()) }
    LaunchedEffect(Unit) {
        focus?.focus(
            candidates.firstOrNull()?.let { groupMemberTag(it.id) } ?: "close_group_picker",
            reveal = false,
        )
    }
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, BOTTOM_DESIGN_WIDTH, BOTTOM_DESIGN_HEIGHT)
            .background(palette.scrim)
            .testTag("group_picker_overlay")
            .controllerFocusBarrier("group_picker_overlay", layer = GROUP_PROMPT_FOCUS_LAYER)
            .clickable(
                enabled = !busy,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 80f, 1080f, 920f)
            .clip(panelShape)
            .pocketFrame(
                Brush.verticalGradient(listOf(palette.surface, palette.tint(Color(0xFFD1EDFB)))),
                metrics.dp(15f),
                palette.tealBorder,
                panelShape,
            )
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("group_picker_panel"),
    ) {
        Text(
            text = "Add Members",
            modifier = Modifier.designBounds(metrics, 58f, 46f, 760f, 94f),
            color = palette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(72f),
            maxLines = 1,
        )
        CloseGlyphButton(
            metrics = metrics,
            x = 945f,
            y = 55f,
            tag = "close_group_picker",
            layer = GROUP_PROMPT_FOCUS_LAYER,
            onClick = onClose,
        )
        Text(
            text = "${selected.size}/$cap selected",
            modifier = Modifier.designBounds(metrics, 58f, 150f, 600f, 44f),
            color = palette.tealSoft,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            maxLines = 1,
        )
        MemberPickerPanel(
            metrics = metrics,
            x = 58f,
            y = 210f,
            width = 964f,
            height = 550f,
            members = candidates,
            selected = selected,
            remainingSlots = (cap - selected.size).coerceAtLeast(0),
            layer = GROUP_PROMPT_FOCUS_LAYER,
            onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
        )
        GroupActionButton(
            metrics = metrics,
            x = 263f,
            y = 790f,
            width = 554f,
            height = 116f,
            label = if (busy) "Adding…" else "Add ${selected.size}",
            textColor = Color.White,
            fill = greenButtonBrush(),
            borderColor = Color(0xFF4FC24B),
            enabled = selected.isNotEmpty() && !busy,
            tag = "group_add_confirm",
            layer = GROUP_PROMPT_FOCUS_LAYER,
        ) { onConfirm(selected.toList()) }
    }
}

@Composable
private fun GroupConfirmDialog(
    metrics: DesignMetrics,
    title: String,
    body: String,
    confirmLabel: String,
    tag: String,
    busy: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val palette = pocketPalette
    val focus = LocalControllerFocus.current
    LaunchedEffect(Unit) { focus?.focus("${tag}_cancel", reveal = false) }
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, BOTTOM_DESIGN_WIDTH, BOTTOM_DESIGN_HEIGHT)
            .background(palette.scrim)
            .testTag("${tag}_overlay")
            .controllerFocusBarrier("${tag}_overlay", layer = GROUP_PROMPT_FOCUS_LAYER)
            .clickable(
                enabled = !busy,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel,
            ),
    )
    Box(
        Modifier
            .designBounds(metrics, 80f, 294f, 1080f, 500f)
            .graphicsLayer { translationY = entrance.value }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 280f, 1080f, 500f)
            .graphicsLayer { translationY = entrance.value }
            .clip(panelShape)
            .pocketFrame(greyPanelBrush(), metrics.dp(15f), palette.borderGrey, panelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("${tag}_panel"),
    ) {
        Text(
            text = title,
            modifier = Modifier.designBounds(metrics, 60f, 44f, 960f, 90f),
            color = palette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(70f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = body,
            modifier = Modifier.designBounds(metrics, 90f, 148f, 900f, 130f),
            color = palette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        GroupActionButton(
            metrics = metrics,
            x = 60f,
            y = 300f,
            width = 470f,
            height = 150f,
            label = "Cancel",
            textColor = Color.White,
            fill = cancelButtonBrush(),
            borderColor = Color(0xFF8A8A8A),
            enabled = !busy,
            tag = "${tag}_cancel",
            layer = GROUP_PROMPT_FOCUS_LAYER,
            onClick = onCancel,
        )
        GroupActionButton(
            metrics = metrics,
            x = 550f,
            y = 300f,
            width = 470f,
            height = 150f,
            label = confirmLabel,
            textColor = Color.White,
            fill = redButtonBrush(),
            borderColor = Color(0xFFC24B4B),
            enabled = !busy,
            tag = "${tag}_confirm",
            layer = GROUP_PROMPT_FOCUS_LAYER,
            onClick = onConfirm,
        )
    }
}

internal const val GROUP_TITLE_FIELD_TAG = "group_title_field"
private const val GROUP_INFO_FOCUS_LAYER = 10
private const val GROUP_KEYBOARD_LAYER = 10
private const val GROUP_PROMPT_FOCUS_LAYER = 20
private const val KEYBOARD_BARRIER_TOP = 404f
private const val MEMBER_ROW_HEIGHT = 140f
private const val MEMBER_ROW_INSET = 20f
private const val MEMBER_ROW_MARGIN = 40f
private const val INFO_ROW_HEIGHT = 124f
private const val INFO_ROW_PITCH = 136f
private const val TOP_COMPOSER_CLOSED_OFFSET = 42f
