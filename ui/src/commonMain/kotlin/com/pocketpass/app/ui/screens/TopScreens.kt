package com.pocketpass.app.ui.screens

import com.pocketpass.app.ui.PocketAsset
import com.pocketpass.app.ui.platformAnimationsEnabled
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import com.pocketpass.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.pocketpass.app.ui.setup.CountryCatalog
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.pocketpass.app.ui.components.rememberPocketAssetBytes
import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.model.ActivityVariant
import com.pocketpass.app.model.HomeMood
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassExtensions
import com.pocketpass.app.model.PocketPassExtensionTarget
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.ProfileFriendRequestState
import com.pocketpass.app.model.ProfileViewerSource
import com.pocketpass.app.model.ProfileViewerUiState
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.LocalDesignMetrics
import com.pocketpass.app.ui.TOP_DESIGN_HEIGHT
import com.pocketpass.app.ui.TOP_DESIGN_WIDTH
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.components.EntranceMotion
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.PatternBackground
import com.pocketpass.app.ui.components.pocketBorder
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.components.pocketShadow
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.components.IdleMotion
import com.pocketpass.app.ui.components.MotionLayer
import com.pocketpass.app.ui.components.rememberActivitiesSwapProgress
import com.pocketpass.app.ui.components.rememberGearRotation
import com.pocketpass.app.ui.DesignAnchor
import com.pocketpass.app.ui.anchoredBounds
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.theme.pocketPalette
import com.pocketpass.app.ui.displayCountryName
import com.pocketpass.app.ui.fileExists
import okio.Path.Companion.toPath
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

@Composable
fun TopScreen(
    destination: PocketPassDestination = state.rootDestination,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    extensions: PocketPassExtensions,
    profileViewerPresenting: Boolean = false,
    threadPresenting: Boolean = false,
) {
    when (destination) {
        PocketPassDestination.Home ->
            HomeTop(state, dispatch, extensions, profileViewerPresenting)
        PocketPassDestination.Activities -> ActivitiesTop(state, dispatch)
        PocketPassDestination.Messages -> MessagesTop(state, threadPresenting)
        PocketPassDestination.Friends -> FriendsTop(state, profileViewerPresenting)
        PocketPassDestination.Settings -> SettingsTop(state)
    }
}

@Composable
fun TopProfileViewer(
    metrics: DesignMetrics,
    state: ProfileViewerUiState,
    dispatch: (PocketPassEvent) -> Unit,
    onPresentingChanged: (Boolean) -> Unit = {},
) {
    var retainedState by remember { mutableStateOf<ProfileViewerUiState?>(null) }
    val translation = remember { Animatable(PROFILE_VIEWER_CLOSED_OFFSET) }
    SideEffect {
        if (state.visible) retainedState = state
    }
    LaunchedEffect(state.visible, state.selectedUserId) {
        if (!platformAnimationsEnabled()) {
            translation.snapTo(
                if (state.visible) 0f else PROFILE_VIEWER_CLOSED_OFFSET,
            )
            if (!state.visible) retainedState = null
            return@LaunchedEffect
        }
        if (state.visible) {
            translation.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 220,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else if (retainedState != null) {
            translation.animateTo(
                targetValue = PROFILE_VIEWER_CLOSED_OFFSET,
                animationSpec = tween(
                    durationMillis = 220,
                    easing = FastOutSlowInEasing,
                ),
            )
            retainedState = null
        }
    }
    val presenting = state.visible || retainedState != null
    LaunchedEffect(presenting) { onPresentingChanged(presenting) }
    val content = if (state.visible) state else retainedState ?: return
    val palette = content.source.profilePalette(pocketPalette)
    val blockerInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_viewer")
            .clickable(
                interactionSource = blockerInteraction,
                indication = null,
                onClick = {},
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val settled = 1f - (
                        translation.value / PROFILE_VIEWER_CLOSED_OFFSET
                        ).coerceIn(0f, 1f)
                    translationY = translation.value
                    alpha = settled
                    scaleX = 0.985f + (0.015f * settled)
                    scaleY = 0.985f + (0.015f * settled)
                },
        ) {
            FriendProfileHero(
                metrics = metrics,
                state = content,
                palette = palette,
            )
        }
    }
}

private const val SHOP_CLOSED_OFFSET = 42f

private fun Modifier.softLight(): Modifier = drawWithContent {
    val paint = Paint().apply { blendMode = BlendMode.Softlight }
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), paint)
    drawContent()
    drawContext.canvas.restore()
}

@Composable
fun TopShop(
    metrics: DesignMetrics,
    state: PocketPassUiState,
) {
    val visible = state.shop.visible
    var retainedBalance by remember { mutableStateOf(0) }
    var everShown by remember { mutableStateOf(false) }
    val translation = remember { Animatable(SHOP_CLOSED_OFFSET) }
    SideEffect {
        if (visible) {
            retainedBalance = state.shop.tokenBalance
            everShown = true
        }
    }
    LaunchedEffect(visible) {
        if (!platformAnimationsEnabled()) {
            translation.snapTo(if (visible) 0f else SHOP_CLOSED_OFFSET)
            if (!visible) everShown = false
            return@LaunchedEffect
        }
        if (visible) {
            translation.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
        } else if (everShown) {
            translation.animateTo(
                targetValue = SHOP_CLOSED_OFFSET,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
            everShown = false
        }
    }
    if (!visible && !everShown) return
    val balance = if (visible) state.shop.tokenBalance else retainedBalance

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val settled = 1f - (translation.value / SHOP_CLOSED_OFFSET).coerceIn(0f, 1f)
                translationY = translation.value
                alpha = settled
            }
            .testTag("top_shop"),
    ) {
        PatternBackground(
            metrics = metrics,
            pattern = Assets.PatternActivitiesTop,
            topColor = pocketPalette.tint(Color(0xFFFCD1A5)),
            bottomColor = pocketPalette.background(PocketPassDestination.Activities, top = true).bottom,
            holdFraction = 0.5f,
            designWidth = TOP_DESIGN_WIDTH,
            designHeight = TOP_DESIGN_HEIGHT,
            alpha = { 1f - (translation.value / SHOP_CLOSED_OFFSET).coerceIn(0f, 1f) },
        )
        FigmaAsset(
            resource = Assets.ActivitiesCoinDefault,
            modifier = Modifier.designBounds(metrics, 711.96f, 274.32f, 496.082f, 496.082f),
        )
        Column(
            modifier = Modifier.designBounds(metrics, 226.5f, 749f, 1467f, 260f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "$balance Tokens",
                color = pocketPalette.ink(Color(0xFF803427)),
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(133f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.testTag("top_shop_balance"),
            )
            Text(
                text = if (visible && state.shop.purchasingItemIds.isNotEmpty()) {
                    "Purchase pending…"
                } else {
                    "Earn by playing games, walking & interacting!"
                },
                color = pocketPalette.ink(Color(0xFF803427)).copy(alpha = 0.79f),
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(64f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun TopGames(
    metrics: DesignMetrics,
    state: PocketPassUiState,
) {
    val visible = state.games.visible
    var everShown by remember { mutableStateOf(false) }
    val translation = remember { Animatable(SHOP_CLOSED_OFFSET) }
    SideEffect {
        if (visible) everShown = true
    }
    LaunchedEffect(visible) {
        if (!platformAnimationsEnabled()) {
            translation.snapTo(if (visible) 0f else SHOP_CLOSED_OFFSET)
            if (!visible) everShown = false
            return@LaunchedEffect
        }
        if (visible) {
            translation.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
        } else if (everShown) {
            translation.animateTo(
                targetValue = SHOP_CLOSED_OFFSET,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
            everShown = false
        }
    }
    if (!visible && !everShown) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val settled = 1f - (translation.value / SHOP_CLOSED_OFFSET).coerceIn(0f, 1f)
                translationY = translation.value
                alpha = settled
            }
            .testTag("top_games"),
    ) {
        PatternBackground(
            metrics = metrics,
            pattern = Assets.PatternActivitiesTop,
            topColor = pocketPalette.tint(Color(0xFFA5FCAB)),
            bottomColor = pocketPalette.background(PocketPassDestination.Activities, top = true).bottom,
            holdFraction = 0.5f,
            designWidth = TOP_DESIGN_WIDTH,
            designHeight = TOP_DESIGN_HEIGHT,
            alpha = { 1f - (translation.value / SHOP_CLOSED_OFFSET).coerceIn(0f, 1f) },
        )
        FigmaAsset(
            resource = Assets.GamesHero,
            modifier = Modifier.designBounds(metrics, 719.88f, 194.32f, 502.66f, 620.59f),
        )
        Box(
            modifier = Modifier.designBounds(metrics, 226.5f, 809.04f, 1467f, 200f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Games",
                color = pocketPalette.teal,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(133f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun TopLeaderboard(
    metrics: DesignMetrics,
    state: PocketPassUiState,
) {
    val visible = state.leaderboard.visible
    var everShown by remember { mutableStateOf(false) }
    val translation = remember { Animatable(SHOP_CLOSED_OFFSET) }
    SideEffect {
        if (visible) everShown = true
    }
    LaunchedEffect(visible) {
        if (!platformAnimationsEnabled()) {
            translation.snapTo(if (visible) 0f else SHOP_CLOSED_OFFSET)
            if (!visible) everShown = false
            return@LaunchedEffect
        }
        if (visible) {
            translation.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
        } else if (everShown) {
            translation.animateTo(
                targetValue = SHOP_CLOSED_OFFSET,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
            everShown = false
        }
    }
    if (!visible && !everShown) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val settled = 1f - (translation.value / SHOP_CLOSED_OFFSET).coerceIn(0f, 1f)
                translationY = translation.value
                alpha = settled
            }
            .testTag("top_leaderboard"),
    ) {
        PatternBackground(
            metrics = metrics,
            pattern = Assets.PatternActivitiesTop,
            topColor = pocketPalette.tint(Color(0xFFFCF6A5)),
            bottomColor = pocketPalette.background(PocketPassDestination.Activities, top = true).bottom,
            holdFraction = 0.5f,
            designWidth = TOP_DESIGN_WIDTH,
            designHeight = TOP_DESIGN_HEIGHT,
            alpha = { 1f - (translation.value / SHOP_CLOSED_OFFSET).coerceIn(0f, 1f) },
        )
        FigmaAsset(
            resource = Assets.LeaderboardTrophyHero,
            modifier = Modifier.designBounds(metrics, 673.515f, 233.671f, 572.848f, 544.19f),
        )
        FigmaAsset(
            resource = Assets.LeaderboardTrophyGloss,
            modifier = Modifier
                .designBounds(metrics, 698.909f, 227.38f, 553.74f, 532.29f)
                .softLight()
                .blur(metrics.dp(26.6f), BlurredEdgeTreatment.Unbounded),
        )
        Box(
            modifier = Modifier.designBounds(metrics, 226.5f, 786.95f, 1467f, 200f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Leaderboard",
                color = pocketPalette.ink(Color(0xFF6B401D)),
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(133f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun TopMessageThread(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    onPresentingChanged: (Boolean) -> Unit = {},
) {
    val conversation = state.selectedConversation
    var retained by remember { mutableStateOf<ConversationSummary?>(null) }
    var retainedMessages by remember { mutableStateOf<List<Message>>(emptyList()) }
    val translation = remember { Animatable(MESSAGE_THREAD_CLOSED_OFFSET) }
    SideEffect {
        if (conversation != null) {
            retained = conversation
            retainedMessages = state.selectedMessages
        }
    }
    LaunchedEffect(conversation?.id) {
        if (!platformAnimationsEnabled()) {
            translation.snapTo(
                if (conversation != null) 0f else MESSAGE_THREAD_CLOSED_OFFSET,
            )
            if (conversation == null) retained = null
            return@LaunchedEffect
        }
        if (conversation != null) {
            translation.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
        } else if (retained != null) {
            translation.animateTo(
                targetValue = MESSAGE_THREAD_CLOSED_OFFSET,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
            retained = null
            retainedMessages = emptyList()
        }
    }
    val presenting = conversation != null || retained != null
    LaunchedEffect(presenting) { onPresentingChanged(presenting) }
    if (!presenting) return
    val messages = if (conversation != null) state.selectedMessages else retainedMessages
    val currentUserId = when (val session = state.sessionState) {
        is SessionState.Authenticated -> session.userId
        is SessionState.OfflineWithCachedSession -> session.userId
        else -> null
    }
    val partnerTyping = conversation != null &&
        conversation.id.value in state.typingConversationIds
    val threadId = (conversation ?: retained)?.id?.value
    val arrivalTracker = remember(threadId) { MessageArrivalTracker() }
    SideEffect { arrivalTracker.primed = true }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.lastOrNull()?.id?.value, partnerTyping) {
        val lastIndex = messages.lastIndex + if (partnerTyping) 1 else 0
        if (lastIndex >= 0) listState.scrollToItem(lastIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val settled = 1f - (
                    translation.value / MESSAGE_THREAD_CLOSED_OFFSET
                    ).coerceIn(0f, 1f)
                translationY = translation.value
                alpha = settled
                scaleX = 0.985f + (0.015f * settled)
                scaleY = 0.985f + (0.015f * settled)
            }
            .testTag("top_message_thread"),
    ) {
        PatternBackground(
            metrics = metrics,
            pattern = Assets.PatternMessagesTop,
            topColor = pocketPalette.tint(Color(0xFFDDF0F9)),
            bottomColor = pocketPalette.tint(Color(0xFFE9F1F6)),
            holdFraction = 0f,
            designWidth = TOP_DESIGN_WIDTH,
            designHeight = TOP_DESIGN_HEIGHT,
            alpha = { 1f - (translation.value / MESSAGE_THREAD_CLOSED_OFFSET).coerceIn(0f, 1f) },
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .anchoredBounds(
                    metrics,
                    0f,
                    MESSAGE_LIST_TOP,
                    1920f,
                    MESSAGE_LIST_HEIGHT,
                    vertical = DesignAnchor.Stretch,
                )
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                MESSAGE_LIST_FADE / MESSAGE_LIST_HEIGHT to Color.Black,
                            ),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .testTag("message_thread"),
            contentPadding = PaddingValues(
                start = metrics.dp(100f),
                end = metrics.dp(100f),
                top = metrics.dp(24f),
                bottom = metrics.dp(96f),
            ),
            verticalArrangement = Arrangement.spacedBy(
                space = metrics.dp(80f),
                alignment = Alignment.Bottom,
            ),
        ) {
            val shownConversation = conversation ?: retained
            itemsIndexed(items = messages, key = { _, message -> message.id.value }) { index, message ->
                val arrivalPop = remember(message.id.value) {
                    arrivalTracker.markSeen(message.id.value)
                }
                val mine = message.senderId == currentUserId
                MessageBubble(
                    metrics = metrics,
                    message = message,
                    outgoing = mine,
                    onRetry = { dispatch(PocketPassEvent.RetryMessage(message.id.value)) },
                    arrivalPop = arrivalPop,
                    onLongPress = if (conversation != null && mine && message.isEditable()) {
                        { dispatch(PocketPassEvent.OpenMessageActions(message.id.value)) }
                    } else {
                        null
                    },
                    selected = message.id.value == state.messageActionMessageId,
                    senderLabel = senderLabelFor(
                        conversation = shownConversation,
                        previous = messages.getOrNull(index - 1),
                        message = message,
                        selfId = currentUserId,
                    ),
                )
            }
            if (partnerTyping) {
                item(key = "typing_indicator") {
                    TypingIndicatorBubble(
                        metrics = metrics,
                        label = shownConversation
                            ?.takeIf { it.isGroup }
                            ?.let { typingNames(it, state.typingUserIds) },
                    )
                }
            }
        }
    }
}

internal class MessageArrivalTracker {
    private val seen = HashSet<String>()
    var primed = false

    fun markSeen(id: String): Boolean {
        val fresh = primed && id !in seen
        seen.add(id)
        return fresh
    }
}

@Composable
internal fun TypingIndicatorBubble(
    metrics: DesignMetrics,
    label: String? = null,
) {
    val wave = rememberInfiniteTransition(label = "typing dots").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
        ),
        label = "typing wave",
    )
    val shape = RoundedCornerShape(metrics.dp(60f))
    Column(modifier = Modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                text = label,
                modifier = Modifier.padding(start = metrics.dp(23f), bottom = metrics.dp(10f)),
                color = pocketPalette.ink(Color(0xFF8C6D0D)),
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(30f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .requiredSize(metrics.dp(232f), metrics.dp(120f))
                .clip(shape)
                .pocketFrame(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFFEDD85E),
                            0.16477f to Color(0xFFEDD85E),
                            1f to Color(0xFFFF9900),
                        ),
                    ),
                    metrics.dp(16.122f),
                    Color(0xFFC2B04B),
                    shape,
                )
                .drawBehind {
                    val phase = wave.value
                    val dotRadius = size.height * 0.11f
                    val centerY = size.height / 2f
                    val spacing = size.width / 4f
                    repeat(3) { index ->
                        val local = (phase * 2f - index * 0.28f).mod(2f)
                        val lift = if (local < 1f) {
                            kotlin.math.sin(local * kotlin.math.PI).toFloat()
                        } else {
                            0f
                        }
                        drawCircle(
                            color = Color.White,
                            radius = dotRadius,
                            center = Offset(
                                spacing * (index + 1),
                                centerY - lift * dotRadius * 1.2f,
                            ),
                        )
                    }
                },
        )
    }
}

private const val MESSAGE_THREAD_CLOSED_OFFSET = 42f

private const val MESSAGE_LIST_TOP = 0f
private const val MESSAGE_LIST_HEIGHT = 1030f
private const val MESSAGE_LIST_FADE = 210f

@Composable
private fun FriendProfileHero(
    metrics: DesignMetrics,
    state: ProfileViewerUiState,
    palette: ProfileViewerPalette,
) {
    val profile = state.profile
    if (profile == null) {
        ProfileUnavailableHero(metrics, palette)
        return
    }
    val displayName = profile.displayName.trim().ifBlank { "PocketPass User" }
    val bio = profile.bio.trim()
    val age = profile.age
    val country = profile.locationLabel?.ifBlank { null }
        ?: profile.countryCode?.let(::countryLabel)

    val avatarShape = RoundedCornerShape(metrics.dp(224.5f))
    Box(
        Modifier
            .designBounds(metrics, 255.5f, 395.5f, 449f, 449f)
            .pocketShadow(metrics, 224.5f),
    )
    DynamicTopAvatar(
        avatar = profile.avatar,
        localPortraitFilePath = null,
        fallbackResource = null,
        modifier = Modifier
            .designBounds(metrics, 255.5f, 381.5f, 449f, 449f)
            .clip(avatarShape)
            .pocketFrame(palette.surfaceBottom, metrics.dp(22f), palette.border, avatarShape)
            .padding(metrics.dp(3f))
            .clip(avatarShape),
    )
    val nameAutoSize = remember(metrics) {
        TextAutoSize.StepBased(
            minFontSize = metrics.sp(64f),
            maxFontSize = metrics.sp(133.411f),
            stepSize = metrics.sp(1f),
        )
    }
    Box(
        modifier = Modifier.designBounds(metrics, 740.5f, 484f, 924f, 174f),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(
            text = displayName,
            autoSize = nameAutoSize,
            style = TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = metrics.sp(0.7f),
                color = palette.primaryText,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.16f),
                    offset = Offset(5f, 7f),
                    blurRadius = 14f,
                ),
            ),
            maxLines = 1,
        )
    }
    ProfileDetailRows(
        metrics = metrics,
        age = age,
        country = country,
        bio = bio,
        bodyColor = palette.primaryText,
        accentColor = palette.accentText,
    )
    if (state.isOnline) {
        Box(
            Modifier
                .designBounds(metrics, 622f, 748f, 72f, 72f)
                .clip(RoundedCornerShape(metrics.dp(36f)))
                .pocketFrame(
                    Color(0xFF51FF85),
                    metrics.dp(10f),
                    Color.White,
                    RoundedCornerShape(metrics.dp(36f)),
                ),
        )
    }
}

@Composable
private fun ProfileUnavailableHero(
    metrics: DesignMetrics,
    palette: ProfileViewerPalette,
) {
    Text(
        text = "This profile is unavailable.",
        modifier = Modifier.designBounds(metrics, 255.5f, 484f, 1409f, 174f),
        style = TextStyle(
            fontFamily = Rubik,
            fontWeight = FontWeight.ExtraBold,
            fontSize = metrics.sp(96f),
            color = palette.primaryText,
        ),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun ProfileViewerCard(
    metrics: DesignMetrics,
    state: ProfileViewerUiState,
    palette: ProfileViewerPalette,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(82f))
    Box(
        modifier = Modifier
            .designBounds(
                metrics,
                PROFILE_VIEWER_X,
                PROFILE_VIEWER_Y,
                PROFILE_VIEWER_WIDTH,
                PROFILE_VIEWER_HEIGHT,
            )
            .clip(shape)
            .pocketFrame(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to pocketPalette.surface,
                        0.62f to pocketPalette.surface,
                        1f to palette.surfaceBottom,
                    ),
                ),
                metrics.dp(14f),
                Brush.verticalGradient(
                        listOf(
                            palette.borderHighlight,
                            palette.border,
                            palette.borderDark,
                        ),
                    ),
                shape,
            )
            .testTag("profile_viewer_card"),
    ) {
        Box(
            modifier = Modifier
                .designBounds(metrics, 22f, 22f, 1356f, 736f)
                .border(
                    metrics.dp(4f),
                    Color.White.copy(alpha = if (pocketPalette.isDark) 0.12f else 0.72f),
                    RoundedCornerShape(metrics.dp(64f)),
                ),
        )
        ProfileViewerCloseButton(
            metrics = metrics,
            palette = palette,
            onClick = { dispatch(PocketPassEvent.CloseUserProfile) },
        )
        if (state.unavailable || state.profile == null) {
            ProfileUnavailableContent(metrics, palette)
        } else {
            ProfileViewerContent(
                metrics = metrics,
                state = state,
                palette = palette,
                dispatch = dispatch,
            )
        }
    }
}

@Composable
private fun ProfileViewerContent(
    metrics: DesignMetrics,
    state: ProfileViewerUiState,
    palette: ProfileViewerPalette,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val profile = requireNotNull(state.profile)
    val displayName = profile.displayName.trim().ifBlank { "PocketPass User" }
    val location = profile.locationLabel
        ?.trim()
        ?.ifBlank { null }
        ?: profile.countryCode
            ?.trim()
            ?.ifBlank { null }
            ?.let(::countryLabel)
    val lastSeenAt = profile.lastSeenAt
    val status = when {
        state.isOnline -> "Online now"
        lastSeenAt != null -> "Last seen ${lastSeenAt.profileRelativeTime()}"
        else -> "Offline"
    }
    Box(
        modifier = Modifier
            .designBounds(metrics, 70f, 135f, 500f, 500f)
            .clip(RoundedCornerShape(metrics.dp(74f)))
            .background(
                Brush.verticalGradient(
                    listOf(pocketPalette.surface, palette.surfaceBottom.copy(alpha = 0.78f)),
                ),
            )
            .pocketBorder(
                metrics.dp(16f),
                palette.border.copy(alpha = 0.30f),
                RoundedCornerShape(metrics.dp(74f)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayName.firstOrNull()?.uppercase() ?: "?",
            color = palette.primaryText,
            fontFamily = Rubik,
            fontWeight = FontWeight.Black,
            fontSize = metrics.sp(190f),
            maxLines = 1,
        )
        ProfileViewerAvatar(
            avatar = profile.avatar,
            modifier = Modifier.fillMaxSize(),
        )
    }
    if (state.isOnline) {
        Box(
            modifier = Modifier
                .designBounds(metrics, 465f, 118f, 116f, 116f)
                .clip(CircleShape)
                .pocketFrame(pocketPalette.surface, metrics.dp(8f), palette.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(metrics.dp(72f))
                    .clip(CircleShape)
                    .background(Color(0xFF38C96B)),
            )
        }
    }
    val headerNameAutoSize = remember(metrics) {
        TextAutoSize.StepBased(
            minFontSize = metrics.sp(52f),
            maxFontSize = metrics.sp(82f),
            stepSize = metrics.sp(1f),
        )
    }
    Box(
        modifier = Modifier.designBounds(metrics, 630f, 92f, 610f, 116f),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(
            text = displayName,
            autoSize = headerNameAutoSize,
            style = TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.Black,
                color = palette.primaryText,
            ),
            maxLines = 1,
        )
    }
    Text(
        text = status,
        modifier = Modifier.designBounds(metrics, 632f, 205f, 600f, 62f),
        color = if (state.isOnline) pocketPalette.ink(Color(0xFF2B9B57)) else palette.accentText,
        fontFamily = Rubik,
        fontWeight = FontWeight.Bold,
        fontSize = metrics.sp(38f),
        maxLines = 1,
    )
    profile.bio.trim().ifBlank { null }?.let { bio ->
        Text(
            text = bio,
            modifier = Modifier.designBounds(metrics, 630f, 292f, 650f, 170f),
            color = palette.primaryText,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(46f),
            maxLines = 3,
        )
    }
    var metadataX = 630f
    profile.age?.let { age ->
        ProfileMetadataChip(
            metrics = metrics,
            x = metadataX,
            width = 220f,
            label = "$age years",
            palette = palette,
        )
        metadataX += 240f
    }
    location?.let { label ->
        ProfileMetadataChip(
            metrics = metrics,
            x = metadataX,
            width = if (profile.age == null) 650f else 410f,
            label = label,
            palette = palette,
        )
    }
    ProfileFriendRequestButton(
        metrics = metrics,
        state = state,
        palette = palette,
        onClick = { dispatch(PocketPassEvent.SendProfileFriendRequest) },
    )
}

@Composable
private fun ProfileMetadataChip(
    metrics: DesignMetrics,
    x: Float,
    width: Float,
    label: String,
    palette: ProfileViewerPalette,
) {
    val shape = RoundedCornerShape(metrics.dp(38f))
    Box(
        modifier = Modifier
            .designBounds(metrics, x, 490f, width, 76f)
            .clip(shape)
            .background(palette.surfaceBottom.copy(alpha = 0.78f))
            .pocketBorder(metrics.dp(5f), palette.border.copy(alpha = 0.62f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = palette.primaryText,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(34f),
            maxLines = 1,
        )
    }
}

@Composable
private fun ProfileFriendRequestButton(
    metrics: DesignMetrics,
    state: ProfileViewerUiState,
    palette: ProfileViewerPalette,
    onClick: () -> Unit,
) {
    if (
        state.source != ProfileViewerSource.RecentInteraction ||
        state.friendRequestState == ProfileFriendRequestState.Hidden
    ) {
        return
    }
    val label = when (state.friendRequestState) {
        ProfileFriendRequestState.Hidden -> return
        ProfileFriendRequestState.Available -> "Send Friend Request"
        ProfileFriendRequestState.Sending -> "Sending…"
        ProfileFriendRequestState.Pending -> "Request pending"
        ProfileFriendRequestState.Friends -> "Friends"
        ProfileFriendRequestState.Unavailable -> "Unavailable"
        ProfileFriendRequestState.Failed -> "Try Again"
    }
    val enabled =
        state.friendRequestState == ProfileFriendRequestState.Available ||
            state.friendRequestState == ProfileFriendRequestState.Failed
    val interaction = remember(state.selectedUserId) { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 800f),
        label = "Profile request press",
    )
    val shape = RoundedCornerShape(metrics.dp(48f))
    Box(
        modifier = Modifier
            .designBounds(metrics, 630f, 595f, 650f, 104f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.verticalGradient(
                        listOf(
                            palette.borderHighlight,
                            palette.border,
                            palette.borderDark,
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            palette.border.copy(alpha = 0.48f),
                            palette.borderDark.copy(alpha = 0.58f),
                        ),
                    )
                },
            )
            .pocketBorder(metrics.dp(6f), Color.White.copy(alpha = 0.76f), shape)
            .testTag("profile_friend_request")
            .then(
                if (enabled) {
                    Modifier.controllerTarget("profile_friend_request", layer = 10, cornerRadius = 48f) { onClick() }
                } else {
                    Modifier
                },
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontFamily = Rubik,
            fontWeight = FontWeight.Black,
            fontSize = metrics.sp(42f),
            maxLines = 1,
        )
    }
    state.friendRequestError?.let { error ->
        Text(
            text = error,
            modifier = Modifier.designBounds(metrics, 630f, 706f, 650f, 46f),
            color = pocketPalette.ink(Color(0xFF9C2D35)),
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(27f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProfileUnavailableContent(
    metrics: DesignMetrics,
    palette: ProfileViewerPalette,
) {
    Text(
        text = "Profile unavailable",
        modifier = Modifier.designBounds(metrics, 250f, 270f, 900f, 120f),
        color = palette.primaryText,
        fontFamily = Rubik,
        fontWeight = FontWeight.Black,
        fontSize = metrics.sp(88f),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
    Text(
        text = "This PocketPass profile can’t be shown right now.",
        modifier = Modifier.designBounds(metrics, 300f, 415f, 800f, 100f),
        color = palette.accentText,
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = metrics.sp(42f),
        textAlign = TextAlign.Center,
        maxLines = 2,
    )
}

@Composable
private fun ProfileViewerCloseButton(
    metrics: DesignMetrics,
    palette: ProfileViewerPalette,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .designBounds(metrics, 1260f, 48f, 92f, 92f)
            .clip(CircleShape)
            .pocketFrame(pocketPalette.surface, metrics.dp(8f), palette.border, CircleShape)
            .testTag("profile_viewer_close")
            .controllerTarget("profile_viewer_close", layer = 10) { onClick() }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = 27f
            drawLine(
                color = palette.primaryText,
                start = Offset(inset, inset),
                end = Offset(size.width - inset, size.height - inset),
                strokeWidth = 10f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = palette.primaryText,
                start = Offset(size.width - inset, inset),
                end = Offset(inset, size.height - inset),
                strokeWidth = 10f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ProfileViewerAvatar(
    avatar: AvatarReference?,
    modifier: Modifier,
) {
    val model = when (avatar) {
        is AvatarReference.Remote -> avatar.url
        is AvatarReference.Bundled -> when (avatar.key) {
            "home_avatar_petah" -> Assets.HomeAvatarPetah
            "home_avatar_matt" -> Assets.HomeAvatarMatt
            "friends_avatar_matt" -> Assets.FriendsAvatarMatt
            "messages_avatar_spob" -> Assets.MessagesAvatarSpob
            "messages_avatar_sans" -> Assets.MessagesAvatarSans
            else -> null
        }

        null -> null
    }
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

internal data class ProfileViewerPalette(
    val borderHighlight: Color,
    val border: Color,
    val borderDark: Color,
    val surfaceBottom: Color,
    val primaryText: Color,
    val accentText: Color,
)

internal fun ProfileViewerSource?.profilePalette(
    theme: com.pocketpass.app.ui.theme.PocketPalette,
): ProfileViewerPalette =
    if (this == ProfileViewerSource.Friend) {
        ProfileViewerPalette(
            borderHighlight = Color(0xFFD75CD0),
            border = Color(0xFFCB4AC0),
            borderDark = Color(0xFF6E217D),
            surfaceBottom = theme.tint(Color(0xFFFED3FF)),
            primaryText = theme.ink(Color(0xFF511D6B)),
            accentText = theme.ink(Color(0xFF820A79)),
        )
    } else {
        ProfileViewerPalette(
            borderHighlight = Color(0xFF76B3C1),
            border = theme.tealBorder,
            borderDark = Color(0xFF22677C),
            surfaceBottom = theme.tint(Color(0xFFBDF8CB)),
            primaryText = theme.teal,
            accentText = theme.ink(Color(0xFF2F948C)),
        )
    }

private fun Instant.profileRelativeTime(): String {
    val elapsed = (Clock.System.now() - this).coerceAtLeast(Duration.ZERO)
    return when {
        elapsed.inWholeMinutes < 1L -> "just now"
        elapsed.inWholeMinutes < 60L -> "${elapsed.inWholeMinutes}m ago"
        elapsed.inWholeHours < 24L -> "${elapsed.inWholeHours}h ago"
        elapsed.inWholeDays < 30L -> "${elapsed.inWholeDays}d ago"
        else -> "${elapsed.inWholeDays / 30L}mo ago"
    }
}

@Composable
private fun HomeTop(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    extensions: PocketPassExtensions,
    profileViewerPresenting: Boolean,
) {
    val profile = state.profile
    val displayName = profile?.displayName?.ifBlank { null }.orEmpty()
    val bio = if (state.bioEditor.visible) {
        state.bioEditor.draft.trim().ifBlank { "Hello! Nice to meet you!" }
    } else {
        profile?.bio?.ifBlank { null } ?: "Hello! Nice to meet you!"
    }
    val age = profile?.age
    val country = profile?.locationLabel?.ifBlank { null }
        ?: profile?.countryCode?.let(::countryLabel)
    TopPage(entrance = EntranceMotion.HeroRise) { metrics ->
        HomeRisingEmojis(metrics = metrics, state = state)

        val ownProfileReturn by animateFloatAsState(
            targetValue = if (profileViewerPresenting) 0f else 1f,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 430f),
            label = "Own profile swap",
        )
        val ownProfileVisible = remember { derivedStateOf { ownProfileReturn > 0.001f } }
        if (ownProfileVisible.value) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = ownProfileReturn
                    scaleX = 0.96f + (0.04f * ownProfileReturn)
                    scaleY = 0.96f + (0.04f * ownProfileReturn)
                    translationY = (1f - ownProfileReturn) * 24f
                },
        ) {
        val avatarShape = RoundedCornerShape(metrics.dp(224.5f))
        Box(
            Modifier
                .designBounds(metrics, 255.5f, 395.5f, 449f, 449f)
                .pocketShadow(metrics, 224.5f),
        )
        DynamicTopAvatar(
            avatar = profile?.avatar,
            localPortraitFilePath = state.miiEditor.activePortraitFilePath,
            fallbackResource = Assets.HomeAvatarPetah,
            modifier = Modifier
                .designBounds(metrics, 255.5f, 381.5f, 449f, 449f)
                .clip(avatarShape)
                .pocketFrame(
                    pocketPalette.surface,
                    metrics.dp(22f),
                    pocketPalette.tealBorder,
                    avatarShape,
                )
                .padding(metrics.dp(3f))
                .clip(avatarShape)
                .background(Color.White)
                .graphicsLayer {
                    scaleX = CARD_PORTRAIT_ZOOM
                    scaleY = CARD_PORTRAIT_ZOOM
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
        )
        val nameAutoSize = remember(metrics) {
            TextAutoSize.StepBased(
                minFontSize = metrics.sp(64f),
                maxFontSize = metrics.sp(133.411f),
                stepSize = metrics.sp(1f),
            )
        }
        Box(
            modifier = Modifier.designBounds(metrics, 740.5f, 484f, 924f, 174f),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                text = displayName,
                autoSize = nameAutoSize,
                style = TextStyle(
                    fontFamily = Rubik,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = metrics.sp(0.7f),
                    color = pocketPalette.teal,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.16f),
                        offset = Offset(5f, 7f),
                        blurRadius = 14f,
                    ),
                ),
                maxLines = 1,
            )
        }

        ProfileDetailRows(
            metrics = metrics,
            age = age,
            country = country,
            bio = bio.trimEnd(),
            bodyColor = pocketPalette.tealSoft,
            accentColor = pocketPalette.ink(Color(0xFF1FC1B3)),
        )
        }
        }

        if (!profileViewerPresenting) {
            HomeMoodControls(
                metrics = metrics,
                state = state,
                dispatch = dispatch,
                onEdit = {
                    dispatch(PocketPassEvent.CloseHomeMoodPicker)
                    dispatch(PocketPassEvent.OpenBioEditor)
                },
            )
        }
    }
}

@Composable
private fun HomeMoodControls(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    onEdit: () -> Unit,
) {
    var confirmationMood by remember { mutableStateOf<HomeMood?>(null) }
    var confirmationSequence by remember { mutableStateOf(0) }
    val confirmationScale = remember { Animatable(1f) }
    val focus = LocalControllerFocus.current
    var wasExpanded by remember { mutableStateOf(state.homeMoodPickerExpanded) }
    LaunchedEffect(state.homeMoodPickerExpanded) {
        if (state.homeMoodPickerExpanded) {
            focus?.focus(homeMoodTag(state.homeMood), reveal = false)
        } else if (wasExpanded) {
            focus?.focus(HOME_MOOD_TRIGGER_TAG, reveal = false)
        }
        wasExpanded = state.homeMoodPickerExpanded
    }
    val select: (HomeMood) -> Unit = { mood ->
        confirmationMood = mood
        confirmationSequence += 1
        dispatch(PocketPassEvent.SelectHomeMood(mood))
    }
    LaunchedEffect(confirmationSequence) {
        if (confirmationSequence == 0) return@LaunchedEffect

        confirmationScale.snapTo(1f)
        delay(HOME_MOOD_CONFIRMATION_MILLIS)
        if (!platformAnimationsEnabled()) {
            confirmationMood = null
            return@LaunchedEffect
        }
        confirmationScale.animateTo(
            targetValue = HOME_MOOD_SWAP_SCALE,
            animationSpec = tween(
                durationMillis = HOME_MOOD_SWAP_COMPRESS_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        )
        confirmationMood = null
        confirmationScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.86f,
                stiffness = 720f,
                visibilityThreshold = 0.001f,
            ),
        )
    }

    HomeMood.entries.forEachIndexed { index, mood ->
        val targetX = HOME_MOOD_OPTION_X[index]
        val progress = rememberHomeMoodOptionProgress(
            expanded = state.homeMoodPickerExpanded,
            index = index,
            count = HomeMood.entries.size,
        )
        val selected = mood == state.homeMood
        val selectedHaloVisible = remember(selected, progress) {
            derivedStateOf {
                selected && progress.value > HOME_MOOD_HALO_REVEAL_PROGRESS
            }
        }
        Box(
            modifier = Modifier
                .designBounds(
                    metrics = metrics,
                    x = targetX - HOME_MOOD_SELECTED_HALO_INSET,
                    y = HOME_MOOD_BUTTON_Y - HOME_MOOD_SELECTED_HALO_INSET,
                    width = HOME_MOOD_SELECTED_HALO_SIZE,
                    height = HOME_MOOD_SELECTED_HALO_SIZE,
                )
                .graphicsLayer {
                    translationX =
                        (HOME_MOOD_TRIGGER_X - targetX) * (1f - progress.value)
                }
                .clip(CircleShape)
                .pocketFrame(
                    if (selectedHaloVisible.value) HOME_MOOD_SELECTED_COLOR else Color.Transparent,
                    metrics.dp(HOME_MOOD_SELECTED_OUTLINE_WIDTH),
                    if (selectedHaloVisible.value) HOME_MOOD_SELECTED_OUTLINE_COLOR else Color.Transparent,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            HomeMoodButton(
                metrics = metrics,
                resource = mood.assetResource(),
                description = mood.name,
                enabled = state.homeMoodPickerExpanded,
                modifier = Modifier.testTag(homeMoodTag(mood)),
                onClick = { select(mood) },
            )
        }
        if (state.homeMoodPickerExpanded) {
            Box(
                Modifier
                    .designBounds(
                        metrics = metrics,
                        x = targetX - HOME_MOOD_SELECTED_HALO_INSET,
                        y = HOME_MOOD_BUTTON_Y - HOME_MOOD_SELECTED_HALO_INSET,
                        width = HOME_MOOD_SELECTED_HALO_SIZE,
                        height = HOME_MOOD_SELECTED_HALO_SIZE,
                    )
                    .controllerTarget(
                        homeMoodTag(mood),
                        layer = HOME_MOOD_FOCUS_LAYER,
                        cornerRadius = HOME_MOOD_SELECTED_HALO_SIZE / 2f,
                    ) { select(mood) },
            )
        }
    }

    FigmaAsset(
        resource = Assets.HomeMore,
        modifier = Modifier
            .designBounds(metrics, 644f, HOME_MOOD_BUTTON_Y, 104.5f, 104.5f)
            .testTag("home_edit")
            .controllerTarget("home_edit", cornerRadius = HOME_MOOD_BUTTON_SIZE / 2f) { onEdit() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onEdit() },
    )

    HomeMoodButton(
        metrics = metrics,
        resource = confirmationMood?.assetResource() ?: Assets.HomeMoodTrigger,
        description = confirmationMood?.name ?: "Choose mood",
        transitionScale = { confirmationScale.value },
        modifier = Modifier
            .designBounds(
                metrics,
                HOME_MOOD_TRIGGER_X,
                HOME_MOOD_BUTTON_Y,
                HOME_MOOD_BUTTON_SIZE,
                HOME_MOOD_BUTTON_SIZE,
            )
            .testTag(HOME_MOOD_TRIGGER_TAG)
            .controllerTarget(HOME_MOOD_TRIGGER_TAG, cornerRadius = HOME_MOOD_BUTTON_SIZE / 2f) {
                dispatch(PocketPassEvent.ToggleHomeMoodPicker)
            },
        onClick = { dispatch(PocketPassEvent.ToggleHomeMoodPicker) },
    )
}

@Composable
internal fun HomeMoodButton(
    metrics: DesignMetrics,
    resource: PocketAsset,
    description: String,
    modifier: Modifier = Modifier,
    size: Float = HOME_MOOD_BUTTON_SIZE,
    enabled: Boolean = true,
    transitionScale: () -> Float = { 1f },
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = 0.9f,
            stiffness = 800f,
        ),
        label = "Home mood press",
    )
    Box(
        modifier = modifier
            .requiredSize(metrics.dp(size))
            .graphicsLayer {
                val scale = pressScale * transitionScale()
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(metrics.dp(HOME_MOOD_RING_WIDTH / 2f))
                .background(Color.White, CircleShape),
        )
        FigmaAsset(
            resource = resource,
            modifier = Modifier.fillMaxSize(),
            description = description,
        )
        Box(
            Modifier
                .fillMaxSize()
                .pocketBorder(
                    metrics.dp(HOME_MOOD_RING_WIDTH),
                    Brush.verticalGradient(listOf(Color(0xFF5E9AAC), Color(0xFF21677B))),
                    CircleShape,
                ),
        )
    }
}

@Composable
internal fun rememberHomeMoodOptionProgress(
    expanded: Boolean,
    index: Int,
    count: Int,
): Animatable<Float, AnimationVector1D> {
    val target = if (expanded) 1f else 0f
    val progress = remember { Animatable(target) }
    LaunchedEffect(expanded, index, count) {
        if (!platformAnimationsEnabled()) {
            progress.snapTo(target)
            return@LaunchedEffect
        }

        val staggerIndex = if (expanded) index else count - 1 - index
        if (staggerIndex > 0) {
            delay(staggerIndex.toLong() * HOME_MOOD_STAGGER_MILLIS)
        }
        progress.animateTo(
            targetValue = target,
            animationSpec = spring(
                dampingRatio = 0.88f,
                stiffness = 520f,
                visibilityThreshold = 0.001f,
            ),
        )
    }
    return progress
}

internal fun HomeMood.assetResource(): PocketAsset = when (this) {
    HomeMood.Happy -> Assets.HomeMoodHappy
    HomeMood.Sad -> Assets.HomeMoodSad
    HomeMood.Neutral -> Assets.HomeMoodNeutral
    HomeMood.Party -> Assets.HomeMoodParty
    HomeMood.Playful -> Assets.HomeMoodPlayful
    HomeMood.Cool -> Assets.HomeMoodCool
}

internal data class RisingEmoji(
    val id: Long,
    val resource: PocketAsset,
    val x: Float,
    val depth: Float,
    val durationMillis: Int,
    val swayAmplitude: Float,
    val swayPhase: Float,
) {
    val size: Float
        get() = HOME_EMOJI_SIZE * (0.5f + 0.75f * depth)

    val blurRadius: Float
        get() = (1f - depth) * 7f

    val alphaScale: Float
        get() = 0.5f + 0.5f * depth
}

internal fun HomeMood.emojiResource(): PocketAsset = when (this) {
    HomeMood.Happy -> Assets.HomeMoodEmojiHappy
    HomeMood.Sad -> Assets.HomeMoodEmojiSad
    HomeMood.Neutral -> Assets.HomeMoodEmojiNeutral
    HomeMood.Party -> Assets.HomeMoodEmojiParty
    HomeMood.Playful -> Assets.HomeMoodEmojiPlayful
    HomeMood.Cool -> Assets.HomeMoodEmojiCool
}

internal fun risingEmoji(id: Long, resource: PocketAsset): RisingEmoji {
    val depth = Random.nextFloat()
    val size = HOME_EMOJI_SIZE * (0.5f + 0.75f * depth)
    return RisingEmoji(
        id = id,
        resource = resource,
        x = Random.nextFloat() * (1920f - size),
        depth = depth,
        durationMillis = (5_500 - 2_700 * depth).toInt() + Random.nextInt(-350, 350),
        swayAmplitude = 12f + 58f * depth + Random.nextFloat() * 22f,
        swayPhase = Random.nextFloat() * 6.2831853f,
    )
}

@Composable
private fun HomeRisingEmojis(
    metrics: DesignMetrics,
    state: PocketPassUiState,
) {
    if (!state.moodEmojisEnabled || !platformAnimationsEnabled()) return
    val particles = remember { mutableStateListOf<RisingEmoji>() }
    var nextId by remember { mutableStateOf(0L) }
    val selectionCount = state.homeMoodSelectionCount
    val mood by rememberUpdatedState(state.homeMood)

    var lastBurst by remember { mutableStateOf(selectionCount) }
    LaunchedEffect(selectionCount) {
        if (selectionCount == lastBurst) return@LaunchedEffect
        lastBurst = selectionCount
        val resource = mood.emojiResource()
        repeat(HOME_EMOJI_BURST_COUNT) {
            particles += risingEmoji(nextId++, resource)
            delay(Random.nextLong(30L, 85L))
        }
    }

    LaunchedEffect(state.homeMoodActive) {
        if (!state.homeMoodActive) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(3_200L, 7_800L))
            particles += risingEmoji(nextId++, mood.emojiResource())
            if (Random.nextFloat() < 0.35f) {
                delay(Random.nextLong(250L, 700L))
                particles += risingEmoji(nextId++, mood.emojiResource())
            }
        }
    }

    particles.sortedBy(RisingEmoji::depth).forEach { particle ->
        key(particle.id) {
            RisingEmojiParticle(metrics, particle) { finished ->
                particles.removeAll { it.id == finished }
            }
        }
    }
}

@Composable
internal fun RisingEmojiParticle(
    metrics: DesignMetrics,
    particle: RisingEmoji,
    onFinished: (Long) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(particle.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(particle.durationMillis, easing = LinearEasing),
        )
        onFinished(particle.id)
    }
    FigmaAsset(
        resource = particle.resource,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .anchoredBounds(
                metrics,
                particle.x,
                1080f,
                particle.size,
                particle.size,
                vertical = DesignAnchor.End,
            )
            .graphicsLayer {
                val value = progress.value
                translationY = -value * (1080f + particle.size)
                translationX =
                    sin(value * 5f + particle.swayPhase) * particle.swayAmplitude
                rotationZ = sin(value * 4f + particle.swayPhase) * 9f
                alpha = HOME_EMOJI_ALPHA * particle.alphaScale *
                    (value * 9f).coerceAtMost(1f) *
                    ((1f - value) * 5f).coerceAtMost(1f)
            }
            .blur(metrics.dp(particle.blurRadius)),
    )
}

@Composable
private fun ActivitiesTop(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    TopPage(entrance = EntranceMotion.None) { metrics ->
        val shuffled = state.activityVariant == ActivityVariant.Shuffled
        val compact = state.stepRewards.visible
        val swapProgress = rememberActivitiesSwapProgress(shuffled)
        val defaultIdle = remember(swapProgress) {
            derivedStateOf {
                !swapProgress.isRunning &&
                    swapProgress.value <= ACTIVITIES_SETTLED_EPSILON
            }
        }
        val alternateIdle = remember(swapProgress) {
            derivedStateOf {
                !swapProgress.isRunning &&
                    swapProgress.value >= 1f - ACTIVITIES_SETTLED_EPSILON
            }
        }

        ActivitiesVariantLayer(
            metrics = metrics,
            compact = compact,
            alternate = false,
            leftCount = state.activitySnapshot?.coinCount ?: 22,
            rightCount = state.activitySnapshot?.puzzleCount ?: 3,
            idleEnabled = defaultIdle.value,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    translationY = -ACTIVITIES_SWAP_DISTANCE * swapProgress.value
                },
        )
        ActivitiesVariantLayer(
            metrics = metrics,
            compact = compact,
            alternate = true,
            leftCount = state.activitySnapshot?.nearbyCount ?: 12,
            rightCount = state.activitySnapshot?.locationCount ?: 3,
            idleEnabled = alternateIdle.value,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    translationY = ACTIVITIES_SWAP_DISTANCE * (1f - swapProgress.value)
                },
        )

        // One divider between two counters, two between three.
        val dividerBrush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = if (pocketPalette.isDark) 0.28f else 1f), Color.Transparent),
        )
        val dividerXs = if (compact) listOf(630.5f, 1270.5f) else listOf(956.49f)
        dividerXs.forEach { x ->
            Box(
                Modifier
                    .designBounds(metrics, x, 182f, 19f, 848f)
                    .clip(RoundedCornerShape(metrics.dp(10f)))
                    .background(dividerBrush),
            )
        }
        if (compact) {
            Box(Modifier.designBounds(metrics, 1400f, 300f, 400f, 640f)) {
                StepsCounter(
                    metrics = metrics,
                    state = state,
                    dispatch = dispatch,
                    artSize = 400f,
                    numberSize = 100f,
                )
            }
        }

        val interactionSource = remember { MutableInteractionSource() }
        val buttonShape = RoundedCornerShape(
            topStart = metrics.dp(130f),
            topEnd = metrics.dp(130f),
        )
        MotionLayer(
            modifier = Modifier.anchoredBounds(
                metrics,
                640f,
                924f,
                615.64f,
                192.316f,
                vertical = DesignAnchor.End,
            ),
            entrance = EntranceMotion.ActivityButtonRise,
            delayMillis = 220,
            transformOrigin = TransformOrigin(0.5f, 1f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(buttonShape)
                    .pocketFrame(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to pocketPalette.surface,
                                0.44712f to pocketPalette.surface,
                                1f to pocketPalette.tint(Color(0xFFA8FFC7)),
                            ),
                        ),
                        metrics.dp(16f),
                        Color(0xFF73E881),
                        buttonShape,
                    )
                    .testTag("shuffle_activities")
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { dispatch(PocketPassEvent.ShuffleActivities) },
                contentAlignment = Alignment.TopStart,
            ) {
                Box(
                    modifier = Modifier.designBounds(metrics, 98f, 53.438f, 85.44f, 92.44f),
                ) {
                    FigmaAsset(
                        resource = Assets.ActivitiesShuffle,
                        modifier = Modifier.fillMaxSize(),
                    )
                    FigmaAsset(
                        resource = Assets.ActivitiesShuffleYOverlay,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    text = "Shuffle",
                    modifier = Modifier.designBounds(metrics, 209.44f, 40f, 300.2f, 112.316f),
                    style = TextStyle(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF94FF9B), Color(0xFF20AF42)),
                        ),
                        fontFamily = Rubik,
                        fontWeight = FontWeight.Bold,
                        fontSize = metrics.sp(82.814f),
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ActivitiesVariantLayer(
    metrics: DesignMetrics,
    compact: Boolean,
    alternate: Boolean,
    leftCount: Int,
    rightCount: Int,
    idleEnabled: Boolean,
    modifier: Modifier,
) {
    Box(modifier = modifier) {
        MotionLayer(
            modifier = if (compact) {
                Modifier.designBounds(metrics, 120f, 300f, 400f, 400f)
            } else {
                Modifier.designBounds(metrics, 254.404f, 256.959f, 496.082f, 496.082f)
            },
            entrance = EntranceMotion.ActivityCoinSettle,
            idle = if (idleEnabled) IdleMotion.CoinRock else IdleMotion.None,
        ) {
            FigmaAsset(
                resource = if (alternate) {
                    Assets.ActivitiesCoinAlt
                } else {
                    Assets.ActivitiesCoinDefault
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        MotionLayer(
            modifier = if (compact) {
                Modifier.designBounds(metrics, 130f, 740f, 380f, 160f)
            } else {
                Modifier.designBounds(metrics, 315f, 802f, 380f, 160f)
            },
            entrance = EntranceMotion.ActivityCountRise,
            delayMillis = 80,
        ) {
            Text(
                text = leftCount.toString(),
                modifier = Modifier.fillMaxSize(),
                color = pocketPalette.ink(if (alternate) Color(0xFF33398D) else Color(0xFF803427)),
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(if (compact) 100f else 128f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }

        MotionLayer(
            modifier = if (compact) {
                Modifier.designBounds(metrics, 765f, 305f, 390f, 390f)
            } else {
                Modifier.designBounds(metrics, 1181.486f, 262.945f, 484.11f, 484.11f)
            },
            entrance = EntranceMotion.ActivityPuzzleSettle,
            idle = if (idleEnabled) IdleMotion.PuzzleBob else IdleMotion.None,
            delayMillis = 70,
            idlePhaseMillis = 900,
        ) {
            FigmaAsset(
                resource = if (alternate) {
                    Assets.ActivitiesPuzzleAlt
                } else {
                    Assets.ActivitiesPuzzleDefault
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        MotionLayer(
            modifier = if (compact) {
                Modifier.designBounds(metrics, 770f, 740f, 380f, 160f)
            } else {
                Modifier.designBounds(metrics, 1235f, 797f, 380f, 160f)
            },
            entrance = EntranceMotion.ActivityCountRise,
            delayMillis = 150,
        ) {
            Text(
                text = rightCount.toString(),
                modifier = Modifier.fillMaxSize(),
                color = pocketPalette.ink(if (alternate) Color(0xFF851111) else Color(0xFF11851E)),
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(if (compact) 100f else 128f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MessagesTop(state: PocketPassUiState, threadPresenting: Boolean) {
    val badgeAlpha by animateFloatAsState(
        targetValue = if (threadPresenting) 0f else 1f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "Messages badge",
    )
    val badgeGone = remember { derivedStateOf { badgeAlpha <= 0.001f } }
    if (badgeGone.value) return
    TopPage(entrance = EntranceMotion.MessagePop) { metrics ->
        MotionLayer(
            modifier = Modifier
                .designBounds(
                    metrics,
                    643.836f,
                    307.001f,
                    632.327f,
                    597.997f,
                )
                .graphicsLayer { alpha = badgeAlpha },
            idle = IdleMotion.MessageFloat,
        ) {
            Box(
                modifier = Modifier
                    .designBounds(
                        metrics,
                        31.379f,
                        34.064f,
                        569.569f,
                        529.868f,
                    )
                    .graphicsLayer { rotationZ = -7.31f },
            ) {
                FigmaAsset(
                    resource = Assets.MessagesBadge,
                    modifier = Modifier.designBounds(
                        metrics,
                        0f,
                        0f,
                        569.569f,
                        552.167f,
                    ),
                )
            }

            val badgeAutoSize = remember(metrics) {
                TextAutoSize.StepBased(
                    minFontSize = metrics.sp(48f),
                    maxFontSize = metrics.sp(331.378f),
                    stepSize = metrics.sp(1f),
                )
            }
            Box(
                modifier = Modifier
                    .designBounds(
                        metrics,
                        172.86f,
                        56.14f,
                        284.622f,
                        420.847f,
                    )
                    .graphicsLayer { rotationZ = -7.71f },
                contentAlignment = Alignment.Center,
            ) {
                val badgeTextStyle = TextStyle(
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                BasicText(
                    text = state.messageBadgeText,
                    style = badgeTextStyle.copy(
                        color = pocketPalette.ink(Color(0xFF2F6CA5)),
                        drawStyle = Stroke(
                            width = 18f,
                            join = StrokeJoin.Round,
                        ),
                    ),
                    maxLines = 1,
                    autoSize = badgeAutoSize,
                )
                BasicText(
                    text = state.messageBadgeText,
                    style = badgeTextStyle,
                    maxLines = 1,
                    autoSize = badgeAutoSize,
                )
            }
        }
    }
}

@Composable
internal fun DynamicTopAvatar(
    avatar: AvatarReference?,
    localPortraitFilePath: String? = null,
    fallbackResource: PocketAsset?,
    modifier: Modifier,
) {
    val localPortrait = remember(localPortraitFilePath) {
        localPortraitFilePath
            ?.takeIf(::fileExists)
            ?.toPath()
    }
    val bundled = when (avatar) {
        is AvatarReference.Remote -> null
        is AvatarReference.Bundled -> avatarResourceForKey(avatar.key) ?: fallbackResource
        null -> fallbackResource
    }
    val model: Any? = localPortrait
        ?: (avatar as? AvatarReference.Remote)?.url
        ?: bundled?.let { rememberPocketAssetBytes(it) }
    val fallbackPainter = fallbackResource
        ?.let { rememberPocketAssetBytes(it) }
        ?.let { bytes -> rememberAsyncImagePainter(bytes) }
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        fallback = fallbackPainter,
        error = fallbackPainter,
    )
}

internal fun countryLabel(code: String): String? {
    val normalized = code.trim().uppercase()
    if (normalized.length != 2) return null
    val name = displayCountryName(normalized)
    return "$name ${CountryCatalog.flagEmoji(normalized)}"
}

@Composable
internal fun ProfileDetailRows(
    metrics: DesignMetrics,
    age: Int?,
    country: String?,
    bio: String,
    bodyColor: Color,
    accentColor: Color,
) {
    val detailShadow = Shadow(
        color = Color.Black.copy(alpha = 0.12f),
        offset = Offset(3f, 4f),
        blurRadius = 10f,
    )
    val hasBio = bio.isNotEmpty()
    val bioBottom = remember(bio) { mutableFloatStateOf(64f) }
    if (hasBio) {
        Text(
            text = bio,
            modifier = Modifier.designBounds(metrics, 748.5f, 650f, 730.015f, 130f),
            style = TextStyle(
                color = bodyColor,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(48f),
                shadow = detailShadow,
                lineBreak = LineBreak.Heading,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout ->
                bioBottom.floatValue = layout.getLineBottom(layout.lineCount - 1)
            },
        )
    }
    if (age != null || country != null) {
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = accentColor,
                        fontFamily = Rubik,
                        fontWeight = FontWeight.Bold,
                    ),
                ) {
                    if (age != null) append(age.toString())
                }
                if (age != null && country != null) {
                    withStyle(
                        SpanStyle(
                            color = bodyColor,
                            fontFamily = Rubik,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) {
                        append("  ·  ")
                    }
                }
                if (country != null) {
                    withStyle(
                        SpanStyle(
                            color = accentColor,
                            fontFamily = Rubik,
                            fontWeight = FontWeight.Bold,
                        ),
                    ) {
                        append(country)
                    }
                }
            },
            modifier = Modifier
                .graphicsLayer {
                    translationX = 748.5f
                    translationY =
                        if (hasBio) 650f + bioBottom.floatValue + 10f else 650f
                }
                .requiredSize(metrics.dp(730.015f), metrics.dp(64f)),
            style = TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(52f),
                shadow = detailShadow,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal const val HOME_MOOD_BUTTON_SIZE = 104.5f
internal const val HOME_MOOD_RING_WIDTH = 13f
internal const val HOME_MOOD_BUTTON_Y = 337f
internal const val HOME_MOOD_TRIGGER_X = 766.5f
internal const val HOME_MOOD_SELECTED_HALO_INSET = 7f
internal const val HOME_MOOD_SELECTED_HALO_SIZE = 118.5f
private const val HOME_MOOD_FOCUS_LAYER = 5
private const val HOME_MOOD_TRIGGER_TAG = "home_mood_trigger"

private fun homeMoodTag(mood: HomeMood) = "home_mood_${mood.name.lowercase()}"
internal const val HOME_MOOD_SELECTED_OUTLINE_WIDTH = 3f
internal const val HOME_MOOD_HALO_REVEAL_PROGRESS = 0.02f
internal const val HOME_MOOD_STAGGER_MILLIS = 35L
internal const val HOME_MOOD_CONFIRMATION_MILLIS = 2_000L
internal const val HOME_EMOJI_BURST_COUNT = 26
internal const val HOME_EMOJI_SIZE = 165f
internal const val HOME_EMOJI_ALPHA = 0.55f
internal const val HOME_MOOD_SWAP_SCALE = 0.94f
internal const val HOME_MOOD_SWAP_COMPRESS_MILLIS = 110
internal val HOME_MOOD_OPTION_X = floatArrayOf(889f, 1011.5f, 1134f, 1256.5f, 1379f, 1501.5f)
internal val HOME_MOOD_SELECTED_COLOR = Color(0xFFA4F4BA)
internal val HOME_MOOD_SELECTED_OUTLINE_COLOR = Color(0xFF2CA765)
private const val PROFILE_VIEWER_X = 260f
private const val PROFILE_VIEWER_Y = 150f
private const val PROFILE_VIEWER_WIDTH = 1400f
private const val PROFILE_VIEWER_HEIGHT = 780f
private const val PROFILE_VIEWER_CLOSED_OFFSET = 36f

private const val ACTIVITIES_SWAP_DISTANCE = 1080f
private const val ACTIVITIES_SETTLED_EPSILON = 0.0001f

@Composable
private fun FriendsTop(state: PocketPassUiState, profileViewerPresenting: Boolean) {
    TopPage(entrance = EntranceMotion.None) { metrics ->
        val badgeReturn by animateFloatAsState(
            targetValue = if (profileViewerPresenting) 0f else 1f,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 430f),
            label = "Friends badge swap",
        )
        val badgeVisible = remember { derivedStateOf { badgeReturn > 0.001f } }
        if (badgeVisible.value) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = badgeReturn
                    scaleX = 0.96f + (0.04f * badgeReturn)
                    scaleY = 0.96f + (0.04f * badgeReturn)
                    translationY = (1f - badgeReturn) * 24f
                },
        ) {
        MotionLayer(
            modifier = Modifier.designBounds(metrics, 699.268f, 240.5f, 521.464f, 552.073f),
            entrance = EntranceMotion.FriendSweep,
            idle = IdleMotion.FriendPulse,
        ) {
            FigmaAsset(
                resource = Assets.FriendsBadge,
                modifier = Modifier.fillMaxSize(),
            )
        }
        MotionLayer(
            modifier = Modifier.designBounds(metrics, 540f, 802f, 840f, 180f),
            entrance = EntranceMotion.PanelRise,
            delayMillis = 120,
        ) {
            Text(
                text = "${state.onlineFriendCount.coerceAtLeast(0)} Online",
                modifier = Modifier.fillMaxSize(),
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
    }
}

@Composable
private fun SettingsTop(state: PocketPassUiState) {
    val gearRotation = rememberGearRotation()
    val gearOrigin = remember {
        TransformOrigin(
            pivotFractionX = 268.88f / 535.119f,
            pivotFractionY = 280.232f / 584.074f,
        )
    }

    TopPage(entrance = EntranceMotion.None) { metrics ->
        MotionLayer(
            modifier = Modifier.fillMaxSize(),
            entrance = EntranceMotion.SettingsTurn,
        ) {
            FigmaAsset(
                resource = Assets.SettingsGearShadow,
                modifier = Modifier
                    .designBounds(metrics, 707.5f, 233f, 535.119f, 584.074f)
                    .blur(
                        radius = metrics.dp(5f),
                        edgeTreatment = BlurredEdgeTreatment.Unbounded,
                    )
                    .graphicsLayer {
                        rotationZ = gearRotation.value
                        transformOrigin = gearOrigin
                    },
                alpha = 0.52f,
            )
            FigmaAsset(
                resource = Assets.SettingsGear,
                modifier = Modifier
                    .designBounds(metrics, 699.5f, 215f, 535.119f, 584.074f)
                    .graphicsLayer {
                        rotationZ = gearRotation.value
                        transformOrigin = gearOrigin
                    },
            )
        }
        MotionLayer(
            modifier = Modifier.designBounds(metrics, 460f, 828f, 1000f, 180f),
            entrance = EntranceMotion.TextRise,
            delayMillis = 110,
        ) {
            Text(
                text = "Settings",
                modifier = Modifier.fillMaxSize(),
                color = pocketPalette.ink(Color(0xFF4E4E4E)),
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(128f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TopPage(
    entrance: EntranceMotion,
    content: @Composable (DesignMetrics) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val surfaceMetrics = LocalDesignMetrics.current
        val metrics = surfaceMetrics ?: remember(density) { DesignMetrics(density) }
        MotionLayer(
            modifier = Modifier.fillMaxSize(),
            entrance = entrance,
        ) {
            content(metrics)
        }
    }
}
