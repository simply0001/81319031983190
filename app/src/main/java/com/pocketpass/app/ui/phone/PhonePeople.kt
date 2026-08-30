package com.pocketpass.app.ui.phone

import androidx.annotation.AnyRes
import androidx.annotation.RawRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.NearbyEncounter
import com.pocketpass.app.model.RecentInteractionsSort
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.pocketBorder
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.components.pocketShadow
import com.pocketpass.app.ui.screens.CARD_PORTRAIT_ZOOM
import com.pocketpass.app.ui.screens.DynamicAvatar
import com.pocketpass.app.ui.screens.DynamicTopAvatar
import com.pocketpass.app.ui.screens.SortMenuRow
import com.pocketpass.app.ui.screens.chevronTint
import com.pocketpass.app.ui.screens.relativeTime
import com.pocketpass.app.ui.theme.pocketPalette
import kotlin.time.Instant

internal data class PhonePerson(
    val id: String,
    val name: String,
    val avatar: AvatarReference?,
    val initial: String,
    val isOnline: Boolean,
    val detail: String,
    val key: String = id,
)

@JvmName("encountersToPhonePeople")
internal fun List<NearbyEncounter>.toPhonePeople(sort: RecentInteractionsSort): List<PhonePerson> {
    val sorted = when (sort) {
        RecentInteractionsSort.LatestEncounter -> sortedByDescending { it.occurredAt }
        RecentInteractionsSort.OldestEncounter -> sortedBy { it.occurredAt }
        RecentInteractionsSort.NameAZ -> sortedBy { it.profile.displayName.lowercase() }
    }
    return sorted.map { encounter ->
        val name = encounter.profile.displayName.trim().ifBlank { "PocketPass User" }
        PhonePerson(
            id = encounter.profile.userId.value,
            name = name,
            avatar = encounter.profile.avatar,
            initial = name.firstOrNull()?.uppercase() ?: "?",
            isOnline = false,
            detail = relativeTime(encounter.occurredAt),
            key = encounter.id.value,
        )
    }
}

@JvmName("friendsToPhonePeople")
internal fun List<Friend>.toPhonePeople(sort: RecentInteractionsSort): List<PhonePerson> {
    val sorted = when (sort) {
        RecentInteractionsSort.LatestEncounter ->
            sortedByDescending { it.lastInteractionAt ?: Instant.fromEpochSeconds(0) }
        RecentInteractionsSort.OldestEncounter ->
            sortedWith(compareBy(nullsLast()) { it.lastInteractionAt })
        RecentInteractionsSort.NameAZ -> sortedBy { it.profile.displayName.lowercase() }
    }
    return sorted.map { friend ->
        val name = friend.profile.displayName.trim().ifBlank { "PocketPass User" }
        PhonePerson(
            id = friend.profile.userId.value,
            name = name,
            avatar = friend.profile.avatar,
            initial = name.firstOrNull()?.uppercase() ?: "?",
            isOnline = friend.isOnline,
            detail = if (friend.isOnline) "Now" else friend.profile.lastSeenAt?.let(::relativeTime) ?: "Offline",
        )
    }
}

private val SelectedCheckFill = Color(0xFF3CBC29)
private val SelectedCheckBorder = Color(0xFF2F9A20)
private val SelectedCardBorder = Brush.verticalGradient(listOf(Color(0xFF73E881), Color(0xFF3CBC29)))

internal data class PhoneCardColors(
    val border: Color,
    val borderBrush: Brush,
    val bottom: Color,
    val name: Color,
    val detail: Color,
)

@Composable
internal fun homeCardColors(): PhoneCardColors {
    val palette = pocketPalette
    return PhoneCardColors(
        border = palette.tealBorder,
        borderBrush = Brush.verticalGradient(listOf(Color(0xFF76B3C1), Color(0xFF5E9AAC), Color(0xFF22677C))),
        bottom = palette.tint(Color(0xFFBDF8CB)),
        name = palette.teal,
        detail = palette.ink(Color(0xFF2F948C)),
    )
}

@Composable
internal fun friendCardColors(): PhoneCardColors {
    val palette = pocketPalette
    return PhoneCardColors(
        border = Color(0xFFCB4AC0),
        borderBrush = Brush.verticalGradient(listOf(Color(0xFFD75CD0), Color(0xFFCB4AC0), Color(0xFF6E217D))),
        bottom = palette.tint(Color(0xFFFED3FF)),
        name = palette.ink(Color(0xFF511D6B)),
        detail = palette.ink(Color(0xFF820A79)),
    )
}

@Composable
internal fun PhonePersonCard(
    metrics: DesignMetrics,
    person: PhonePerson,
    colors: PhoneCardColors,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    tag: String = "card_${person.id}",
    onClick: () -> Unit,
) {
    val palette = pocketPalette
    val shape = RoundedCornerShape(metrics.dp(104f))
    val avatarShape = RoundedCornerShape(metrics.dp(56f))
    Column(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .phoneShadow(metrics, 104f)
            .clip(shape)
            .pocketFrame(
                Brush.verticalGradient(
                    colorStops = arrayOf(0f to palette.surface, 0.626f to palette.surface, 1f to colors.bottom),
                ),
                metrics.dp(PHONE_PANEL_BORDER),
                if (selected) SelectedCardBorder else colors.borderBrush,
                shape,
            )
            .testTag(tag)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(start = metrics.dp(50f), end = metrics.dp(50f), top = metrics.dp(50f), bottom = metrics.dp(40f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.12f)
                    .clip(avatarShape)
                    .background(Brush.verticalGradient(listOf(palette.surface, colors.bottom.copy(alpha = 0.72f))))
                    .pocketBorder(metrics.dp(16.793f), colors.border.copy(alpha = 0.2f), avatarShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = person.initial,
                    color = colors.name,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Black,
                    fontSize = metrics.sp(150f),
                    maxLines = 1,
                )
                if (person.avatar != null) {
                    DynamicAvatar(
                        avatar = person.avatar,
                        fallbackResource = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .graphicsLayer {
                                scaleX = CARD_PORTRAIT_ZOOM
                                scaleY = CARD_PORTRAIT_ZOOM
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            },
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            if (person.isOnline) {
                FigmaAsset(
                    resource = Assets.OnlineDot,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = metrics.dp(36f), y = metrics.dp(-36f))
                        .requiredSize(metrics.dp(158f)),
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = metrics.dp(-22f), y = metrics.dp(-22f))
                        .requiredSize(metrics.dp(72f))
                        .clip(CircleShape)
                        .pocketFrame(SelectedCheckFill, metrics.dp(6f), SelectedCheckBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        color = Color.White,
                        fontFamily = Rubik,
                        fontWeight = FontWeight.Bold,
                        fontSize = metrics.sp(40f),
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(metrics.dp(18f)))
        Text(
            text = person.name,
            modifier = Modifier.fillMaxWidth(),
            color = colors.name,
            fontFamily = Rubik,
            fontWeight = FontWeight.Black,
            fontSize = metrics.sp(80f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = person.detail,
            modifier = Modifier.fillMaxWidth(),
            color = colors.detail,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(52f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PhoneEmptyRow(
    metrics: DesignMetrics,
    @RawRes icon: Int,
    title: String,
    subtitle: String,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .phonePanel(metrics, radius = 110f)
            .testTag(tag)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                },
            )
            .padding(start = metrics.dp(43f), end = metrics.dp(43f), top = metrics.dp(38f), bottom = metrics.dp(38f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .requiredSize(metrics.dp(142.65f))
                .clip(CircleShape)
                .pocketFrame(pocketPalette.surface, metrics.dp(9f), Color(0xFF517F92), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FigmaAsset(
                resource = icon,
                modifier = Modifier.requiredSize(metrics.dp(74f)),
                colorFilter = ColorFilter.tint(Color(0xFF3F7D90)),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = metrics.dp(24f)),
        ) {
            Text(
                text = title,
                color = pocketPalette.textPrimary,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(60f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = pocketPalette.textSecondary,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(40f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            FigmaAsset(
                resource = Assets.SettingsArrow,
                modifier = Modifier
                    .padding(start = metrics.dp(20f))
                    .requiredSize(metrics.dp(40.372f), metrics.dp(68.725f)),
                colorFilter = chevronTint(),
            )
        }
    }
}

@Composable
internal fun BoxScope.PhoneSortMenuPanel(
    metrics: DesignMetrics,
    open: Boolean,
    selected: RecentInteractionsSort,
    borderColor: Color,
    textColor: Color,
    tagPrefix: String,
    onSelect: (RecentInteractionsSort) -> Unit,
) {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .offset(y = metrics.dp(102f))
            .width(metrics.dp(558f))
            .height(metrics.dp(0f))
            .wrapContentHeight(Alignment.Top, unbounded = true),
    ) {
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(tween(160)) + scaleIn(tween(220), initialScale = 0.9f, transformOrigin = TransformOrigin(0.93f, 0f)),
        exit = fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.94f, transformOrigin = TransformOrigin(0.93f, 0f)),
    ) {
        val shape = RoundedCornerShape(metrics.dp(48f))
        Column(
            modifier = Modifier
                .width(metrics.dp(558f))
                .phoneShadow(metrics, 48f, 12f)
                .clip(shape)
                .pocketFrame(pocketPalette.surface, metrics.dp(8f), borderColor, shape)
                .testTag("${tagPrefix}_menu")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(vertical = metrics.dp(22f)),
        ) {
            listOf(
                RecentInteractionsSort.LatestEncounter to "Latest Encounter",
                RecentInteractionsSort.OldestEncounter to "Oldest Encounter",
                RecentInteractionsSort.NameAZ to "Name A-Z",
            ).forEach { (sort, label) ->
                SortMenuRow(
                    metrics = metrics,
                    label = label,
                    selected = sort == selected,
                    textColor = textColor,
                    tag = "${tagPrefix}_${sort.key}",
                    focusable = false,
                    onClick = { onSelect(sort) },
                )
            }
        }
    }
    }
}

@Composable
internal fun PhoneSortMenuScrim(open: Boolean, tag: String, onDismiss: () -> Unit) {
    if (!open) return
    Box(
        Modifier
            .fillMaxSize()
            .testTag(tag)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    )
}

@Composable
internal fun PhoneAvatarFrame(
    metrics: DesignMetrics,
    size: Float,
    avatar: AvatarReference?,
    localPortraitPath: String?,
    @AnyRes fallback: Int?,
    border: Color,
    surface: Color,
    online: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.requiredSize(metrics.dp(size))) {
        Box(
            Modifier
                .fillMaxSize()
                .offset(y = metrics.dp(14f))
                .pocketShadow(metrics, size / 2f),
        )
        DynamicTopAvatar(
            avatar = avatar,
            localPortraitFilePath = localPortraitPath,
            fallbackResource = fallback,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .pocketFrame(surface, metrics.dp(22f * size / 449f), border, CircleShape)
                .padding(metrics.dp(3f))
                .clip(CircleShape)
                .background(Color.White)
                .graphicsLayer {
                    scaleX = CARD_PORTRAIT_ZOOM
                    scaleY = CARD_PORTRAIT_ZOOM
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
        )
        if (online) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = metrics.dp(-size * 0.04f), y = metrics.dp(-size * 0.04f))
                    .requiredSize(metrics.dp(size * 0.2f))
                    .clip(CircleShape)
                    .pocketFrame(Color(0xFF51FF85), metrics.dp(size * 0.028f), Color.White, CircleShape),
            )
        }
    }
}

@Composable
internal fun PhoneProfileHero(
    metrics: DesignMetrics,
    name: String,
    bio: String,
    age: Int?,
    country: String?,
    avatar: AvatarReference?,
    localPortraitPath: String?,
    @AnyRes fallback: Int?,
    border: Color,
    surface: Color,
    nameColor: Color,
    bodyColor: Color,
    accentColor: Color,
    online: Boolean,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    avatarSize: Float = if (vertical) 449f else 520f,
    actions: (@Composable () -> Unit)? = null,
) {
    val nameAutoSize = remember(metrics, vertical) {
        TextAutoSize.StepBased(
            minFontSize = metrics.sp(if (vertical) 64f else 48f),
            maxFontSize = metrics.sp(if (vertical) 133.411f else 120f),
            stepSize = metrics.sp(1f),
        )
    }
    val nameStyle = TextStyle(
        fontFamily = Rubik,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = metrics.sp(0.7f),
        color = nameColor,
        textAlign = if (vertical) TextAlign.Center else TextAlign.Start,
        shadow = Shadow(color = Color.Black.copy(alpha = 0.16f), offset = Offset(5f, 7f), blurRadius = 14f),
    )
    val detailShadow = Shadow(color = Color.Black.copy(alpha = 0.12f), offset = Offset(3f, 4f), blurRadius = 10f)
    val bioStyle = TextStyle(
        color = bodyColor,
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = metrics.sp(if (vertical) 48f else 50f),
        shadow = detailShadow,
        lineBreak = LineBreak.Heading,
        textAlign = if (vertical) TextAlign.Center else TextAlign.Start,
    )
    val details = if (age == null && country == null) {
        null
    } else {
        buildAnnotatedString {
            withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold)) { if (age != null) append(age.toString()) }
            if (age != null && country != null) {
                withStyle(SpanStyle(color = bodyColor, fontWeight = FontWeight.SemiBold)) { append("  ·  ") }
            }
            withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold)) { if (country != null) append(country) }
        }
    }
    val detailStyle = TextStyle(
        fontFamily = Rubik,
        fontSize = metrics.sp(if (vertical) 52f else 56f),
        shadow = detailShadow,
        textAlign = if (vertical) TextAlign.Center else TextAlign.Start,
    )
    val avatarFrame: @Composable () -> Unit = {
        PhoneAvatarFrame(metrics, avatarSize, avatar, localPortraitPath, fallback, border, surface, online)
    }
    val texts: @Composable (Modifier) -> Unit = { textModifier ->
        Column(textModifier, horizontalAlignment = if (vertical) Alignment.CenterHorizontally else Alignment.Start) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.dp(if (vertical) 160f else 150f)),
                contentAlignment = if (vertical) Alignment.Center else Alignment.CenterStart,
            ) {
                BasicText(text = name, autoSize = nameAutoSize, style = nameStyle, maxLines = 1)
            }
            if (bio.isNotEmpty()) {
                Text(text = bio, modifier = Modifier.fillMaxWidth(), style = bioStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (details != null) {
                Spacer(Modifier.height(metrics.dp(10f)))
                Text(text = details, modifier = Modifier.fillMaxWidth(), style = detailStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (vertical) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            avatarFrame()
            Spacer(Modifier.height(metrics.dp(36f)))
            texts(Modifier.fillMaxWidth())
            if (actions != null) {
                Spacer(Modifier.height(metrics.dp(40f)))
                actions()
            }
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.Top) {
            Column(Modifier.width(metrics.dp(avatarSize)), horizontalAlignment = Alignment.CenterHorizontally) {
                avatarFrame()
                if (actions != null) {
                    Spacer(Modifier.height(metrics.dp(16f)))
                    Box(Modifier.fillMaxWidth().wrapContentWidth(unbounded = true)) { actions() }
                }
            }
            Spacer(Modifier.width(metrics.dp(72f)))
            Box(Modifier.weight(1f).height(metrics.dp(avatarSize)), contentAlignment = Alignment.CenterStart) {
                texts(Modifier.fillMaxWidth())
            }
        }
    }
}
