package com.pocketpass.app.ui.phone

import com.pocketpass.app.ui.PocketAsset
import com.pocketpass.app.ui.platformAnimationsEnabled
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pocketpass.app.model.HomeMood
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassExtensions
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.ProfileViewerSource
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.screens.HOME_EMOJI_ALPHA
import com.pocketpass.app.ui.screens.HOME_EMOJI_BURST_COUNT
import com.pocketpass.app.ui.screens.HOME_EMOJI_SIZE
import com.pocketpass.app.ui.screens.HOME_MOOD_CONFIRMATION_MILLIS
import com.pocketpass.app.ui.screens.HOME_MOOD_HALO_REVEAL_PROGRESS
import com.pocketpass.app.ui.screens.HOME_MOOD_SELECTED_COLOR
import com.pocketpass.app.ui.screens.HOME_MOOD_SELECTED_OUTLINE_COLOR
import com.pocketpass.app.ui.screens.HOME_MOOD_SELECTED_OUTLINE_WIDTH
import com.pocketpass.app.ui.screens.HOME_MOOD_SWAP_COMPRESS_MILLIS
import com.pocketpass.app.ui.screens.HOME_MOOD_SWAP_SCALE
import com.pocketpass.app.ui.screens.HomeMoodButton
import com.pocketpass.app.ui.screens.RisingEmoji
import com.pocketpass.app.ui.screens.assetResource
import com.pocketpass.app.ui.screens.countryLabel
import com.pocketpass.app.ui.screens.emojiResource
import com.pocketpass.app.ui.screens.rememberHomeMoodOptionProgress
import com.pocketpass.app.ui.theme.pocketPalette
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

private const val PHONE_MOOD_BUTTON = 84f
private const val PHONE_MOOD_HALO = 96f
private const val PHONE_MOOD_GAP = 12f
private const val PHONE_MOOD_TRIGGER_X = PHONE_MOOD_BUTTON + 20f
private const val PHONE_MOOD_FIRST_OPTION_X = PHONE_MOOD_TRIGGER_X + PHONE_MOOD_BUTTON + 20f

@Composable
fun PhoneHomeTab(
    metrics: DesignMetrics,
    panes: WidePanes?,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    extensions: PocketPassExtensions,
) {
    val people = remember(state.recentInteractions, state.recentInteractionsSort) {
        state.recentInteractions.toPhonePeople(state.recentInteractionsSort)
    }
    if (panes == null) {
        Box(Modifier.fillMaxSize()) {
            PhoneRisingEmojis(metrics, state)
            PhonePeopleGrid(
                metrics = metrics,
                people = people,
                colors = homeCardColors(),
                topInset = LocalPhoneInsets.current.top,
                header = { PhoneRecentsHeader(metrics, state, dispatch) },
                empty = { PhoneRecentsEmpty(metrics) },
                onPerson = { dispatch(PocketPassEvent.OpenUserProfile(it, ProfileViewerSource.RecentInteraction)) },
                hero = { PhoneOwnHero(metrics, state, dispatch, vertical = false) },
            )
            PhoneSortMenuScrim(state.sortMenuOpen, "recent_sort_scrim") { dispatch(PocketPassEvent.CloseSortMenu) }
        }
    } else {
        PhonePanes(
            metrics = metrics,
            panes = panes,
            stage = {
                if (state.profileViewer.visible) {
                    PhoneProfilePage(metrics, state, dispatch, inline = true)
                } else {
                    PhoneRisingEmojis(metrics, state)
                    PhoneStageScroll(metrics) {
                        PhoneOwnHero(
                            metrics = metrics,
                            state = state,
                            dispatch = dispatch,
                            vertical = true,
                            modifier = Modifier.padding(horizontal = metrics.dp(PHONE_CONTENT_MARGIN)),
                        )
                    }
                }
            },
            deck = {
                PhonePeopleGrid(
                    metrics = metrics,
                    people = people,
                    colors = homeCardColors(),
                    topInset = LocalPhoneInsets.current.top,
                    header = { PhoneRecentsHeader(metrics, state, dispatch) },
                    empty = { PhoneRecentsEmpty(metrics) },
                    onPerson = { dispatch(PocketPassEvent.OpenUserProfile(it, ProfileViewerSource.RecentInteraction)) },
                )
                PhoneSortMenuScrim(state.sortMenuOpen, "recent_sort_scrim") { dispatch(PocketPassEvent.CloseSortMenu) }
            },
        )
    }
}

@Composable
internal fun PhoneStageScroll(
    metrics: DesignMetrics,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val insets = LocalPhoneInsets.current
    Box(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = metrics.dp(insets.top + 60f), bottom = metrics.dp(insets.bottom + 60f)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
internal fun PhonePeopleGrid(
    metrics: DesignMetrics,
    people: List<PhonePerson>,
    colors: PhoneCardColors,
    topInset: Float,
    header: @Composable () -> Unit,
    empty: @Composable () -> Unit,
    onPerson: (String) -> Unit,
    hero: (@Composable () -> Unit)? = null,
    footer: (LazyGridScope.() -> Unit)? = null,
    selectedIds: Set<String> = emptySet(),
    disabledIds: Set<String> = emptySet(),
    tagPrefix: String = "card",
) {
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = metrics.dp(PHONE_CONTENT_MARGIN),
            end = metrics.dp(PHONE_CONTENT_MARGIN),
            top = metrics.dp(topInset + if (hero != null) 32f else 40f),
            bottom = metrics.dp(60f),
        ),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(metrics.dp(40f)),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(metrics.dp(40f)),
    ) {
        if (hero != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "hero") {
                hero()
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Box(Modifier.zIndex(1f)) { header() }
        }
        if (people.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") { empty() }
        } else {
            items(people, key = { it.key }) { person ->
                PhonePersonCard(
                    metrics = metrics,
                    person = person,
                    colors = colors,
                    selected = person.id in selectedIds,
                    enabled = person.id !in disabledIds,
                    tag = "${tagPrefix}_${person.id}",
                ) { onPerson(person.id) }
            }
        }
        footer?.invoke(this)
    }
}

@Composable
private fun PhoneRecentsHeader(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    Box(Modifier.fillMaxWidth()) {
        PhoneSectionHeader(
            metrics = metrics,
            title = "Recent Interactions",
            color = palette.teal,
            horizontalPadding = 0f,
        ) {
            PhoneRoundAction(
                metrics = metrics,
                borderColor = palette.tealBorder,
                tint = palette.tint(Color(0xFFBDF8CB)),
                tag = "notifications",
                badge = state.unreadNotificationCount,
                onClick = { dispatch(PocketPassEvent.ToggleNotifications) },
            ) { BellGlyph(metrics, palette.teal) }
            PhoneRoundAction(
                metrics = metrics,
                borderColor = palette.tealBorder,
                tint = palette.tint(Color(0xFFBDF8CB)),
                tag = "section_filter",
                onClick = { dispatch(PocketPassEvent.ToggleSortMenu) },
            ) { FigmaAsset(resource = Assets.Filter, modifier = Modifier.requiredSize(metrics.dp(42f))) }
        }
        PhoneSortMenuPanel(
            metrics = metrics,
            open = state.sortMenuOpen,
            selected = state.recentInteractionsSort,
            borderColor = palette.tealBorder,
            textColor = palette.teal,
            tagPrefix = "recent_sort",
            onSelect = { sort ->
                dispatch(PocketPassEvent.SetRecentInteractionsSort(sort))
                dispatch(PocketPassEvent.CloseSortMenu)
            },
        )
    }
}

@Composable
private fun PhoneRecentsEmpty(metrics: DesignMetrics) {
    PhoneEmptyRow(
        metrics = metrics,
        icon = Assets.NavHome,
        title = "No recent interactions yet",
        subtitle = "Nearby PocketPass users you meet appear here",
        tag = "recent_interactions_empty",
    )
}

@Composable
internal fun PhoneOwnHero(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = pocketPalette
    val profile = state.profile
    val bio = if (state.bioEditor.visible) {
        state.bioEditor.draft.trim().ifBlank { "Hello! Nice to meet you!" }
    } else {
        profile?.bio?.ifBlank { null } ?: "Hello! Nice to meet you!"
    }
    val country = profile?.locationLabel?.ifBlank { null } ?: profile?.countryCode?.let(::countryLabel)
    PhoneProfileHero(
        metrics = metrics,
        name = profile?.displayName?.ifBlank { null }.orEmpty(),
        bio = bio.trimEnd(),
        age = profile?.age,
        country = country,
        avatar = profile?.avatar,
        localPortraitPath = state.miiEditor.activePortraitFilePath,
        fallback = Assets.HomeAvatarPetah,
        border = palette.tealBorder,
        surface = palette.surface,
        nameColor = palette.teal,
        bodyColor = palette.tealSoft,
        accentColor = palette.ink(Color(0xFF1FC1B3)),
        online = false,
        vertical = vertical,
        modifier = modifier,
    ) {
        PhoneMoodCluster(
            metrics = metrics,
            state = state,
            dispatch = dispatch,
            centreOpenFan = vertical,
            onEdit = {
                dispatch(PocketPassEvent.CloseHomeMoodPicker)
                dispatch(PocketPassEvent.OpenBioEditor)
            },
        )
    }
}

@Composable
private fun PhoneMoodCluster(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    onEdit: () -> Unit,
    centreOpenFan: Boolean,
) {
    var confirmationMood by remember { mutableStateOf<HomeMood?>(null) }
    var confirmationSequence by remember { mutableStateOf(0) }
    val confirmationScale = remember { Animatable(1f) }
    LaunchedEffect(confirmationSequence) {
        if (confirmationSequence == 0) return@LaunchedEffect
        confirmationScale.snapTo(1f)
        delay(HOME_MOOD_CONFIRMATION_MILLIS)
        if (!platformAnimationsEnabled()) {
            confirmationMood = null
            return@LaunchedEffect
        }
        confirmationScale.animateTo(HOME_MOOD_SWAP_SCALE, tween(HOME_MOOD_SWAP_COMPRESS_MILLIS, easing = FastOutSlowInEasing))
        confirmationMood = null
        confirmationScale.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 720f, visibilityThreshold = 0.001f))
    }
    val width = PHONE_MOOD_FIRST_OPTION_X + HomeMood.entries.size * (PHONE_MOOD_BUTTON + PHONE_MOOD_GAP) - PHONE_MOOD_GAP
    val pairCentre = (PHONE_MOOD_TRIGGER_X + PHONE_MOOD_BUTTON) / 2f
    val open by animateFloatAsState(if (state.homeMoodPickerExpanded && centreOpenFan) 1f else 0f, label = "moodFanSlide")
    Box(
        Modifier
            .offset(x = metrics.dp((width / 2f - pairCentre) * (1f - open)))
            .width(metrics.dp(width))
            .height(metrics.dp(PHONE_MOOD_HALO)),
    ) {
        val haloInset = (PHONE_MOOD_HALO - PHONE_MOOD_BUTTON) / 2f
        HomeMood.entries.forEachIndexed { index, mood ->
            val targetX = PHONE_MOOD_FIRST_OPTION_X + index * (PHONE_MOOD_BUTTON + PHONE_MOOD_GAP)
            val progress = rememberHomeMoodOptionProgress(state.homeMoodPickerExpanded, index, HomeMood.entries.size)
            val selected = mood == state.homeMood
            val haloVisible by remember(selected, progress) {
                derivedStateOf { selected && progress.value > HOME_MOOD_HALO_REVEAL_PROGRESS }
            }
            Box(
                modifier = Modifier
                    .offset(x = metrics.dp(targetX - haloInset), y = metrics.dp(0f))
                    .requiredSize(metrics.dp(PHONE_MOOD_HALO))
                    .graphicsLayer { translationX = (PHONE_MOOD_TRIGGER_X - targetX) * (1f - progress.value) }
                    .clip(CircleShape)
                    .pocketFrame(
                        if (haloVisible) HOME_MOOD_SELECTED_COLOR else Color.Transparent,
                        metrics.dp(HOME_MOOD_SELECTED_OUTLINE_WIDTH),
                        if (haloVisible) HOME_MOOD_SELECTED_OUTLINE_COLOR else Color.Transparent,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                HomeMoodButton(
                    metrics = metrics,
                    resource = mood.assetResource(),
                    description = mood.name,
                    size = PHONE_MOOD_BUTTON,
                    enabled = state.homeMoodPickerExpanded,
                    modifier = Modifier.testTag("home_mood_${mood.name.lowercase()}"),
                    onClick = {
                        confirmationMood = mood
                        confirmationSequence += 1
                        dispatch(PocketPassEvent.SelectHomeMood(mood))
                    },
                )
            }
        }
        FigmaAsset(
            resource = Assets.HomeMore,
            modifier = Modifier
                .offset(x = metrics.dp(0f), y = metrics.dp(haloInset))
                .requiredSize(metrics.dp(PHONE_MOOD_BUTTON))
                .testTag("home_edit")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onEdit,
                ),
        )
        HomeMoodButton(
            metrics = metrics,
            resource = confirmationMood?.assetResource() ?: Assets.HomeMoodTrigger,
            description = confirmationMood?.name ?: "Choose mood",
            size = PHONE_MOOD_BUTTON,
            transitionScale = { confirmationScale.value },
            modifier = Modifier
                .offset(x = metrics.dp(PHONE_MOOD_TRIGGER_X), y = metrics.dp(haloInset))
                .testTag("home_mood_trigger"),
            onClick = { dispatch(PocketPassEvent.ToggleHomeMoodPicker) },
        )
    }
}

@Composable
internal fun PhoneRisingEmojis(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    modifier: Modifier = Modifier,
) {
    if (!state.moodEmojisEnabled || !platformAnimationsEnabled()) return
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val particles = remember { mutableStateListOf<RisingEmoji>() }
        var nextId by remember { mutableStateOf(0L) }
        val mood by rememberUpdatedState(state.homeMood)
        val selectionCount = state.homeMoodSelectionCount
        var lastBurst by remember { mutableStateOf(selectionCount) }
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner, selectionCount) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (selectionCount == lastBurst) return@repeatOnLifecycle
                lastBurst = selectionCount
                val resource = mood.emojiResource()
                repeat(HOME_EMOJI_BURST_COUNT) {
                    particles += phoneRisingEmoji(nextId++, resource, width)
                    delay(Random.nextLong(30L, 85L))
                }
            }
        }
        LaunchedEffect(lifecycleOwner, state.homeMoodActive) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (!state.homeMoodActive) return@repeatOnLifecycle
                while (true) {
                    delay(Random.nextLong(3_200L, 7_800L))
                    particles += phoneRisingEmoji(nextId++, mood.emojiResource(), width)
                    if (Random.nextFloat() < 0.35f) {
                        delay(Random.nextLong(250L, 700L))
                        particles += phoneRisingEmoji(nextId++, mood.emojiResource(), width)
                    }
                }
            }
        }
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.lifecycle.currentStateFlow.collect { lifecycleState ->
                if (!lifecycleState.isAtLeast(Lifecycle.State.STARTED)) particles.clear()
            }
        }
        particles.sortedBy(RisingEmoji::depth).forEach { particle ->
            key(particle.id) {
                PhoneRisingEmojiParticle(metrics, particle, height) { finished ->
                    particles.removeAll { it.id == finished }
                }
            }
        }
    }
}

private fun phoneRisingEmoji(id: Long, resource: PocketAsset, width: Float): RisingEmoji {
    val depth = Random.nextFloat()
    val size = HOME_EMOJI_SIZE * (0.5f + 0.75f * depth)
    return RisingEmoji(
        id = id,
        resource = resource,
        x = Random.nextFloat() * (width - size).coerceAtLeast(0f),
        depth = depth,
        durationMillis = (5_500 - 2_700 * depth).toInt() + Random.nextInt(-350, 350),
        swayAmplitude = 12f + 58f * depth + Random.nextFloat() * 22f,
        swayPhase = Random.nextFloat() * 6.2831853f,
    )
}

@Composable
private fun PhoneRisingEmojiParticle(
    metrics: DesignMetrics,
    particle: RisingEmoji,
    height: Float,
    onFinished: (Long) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(particle.id) {
        progress.animateTo(1f, tween(particle.durationMillis, easing = LinearEasing))
        onFinished(particle.id)
    }
    FigmaAsset(
        resource = particle.resource,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .offset(x = metrics.dp(particle.x), y = metrics.dp(height))
            .requiredSize(metrics.dp(particle.size))
            .graphicsLayer {
                val value = progress.value
                translationY = -value * (height + particle.size)
                translationX = sin(value * 5f + particle.swayPhase) * particle.swayAmplitude
                rotationZ = sin(value * 4f + particle.swayPhase) * 9f
                alpha = HOME_EMOJI_ALPHA * particle.alphaScale *
                    (value * 9f).coerceAtMost(1f) *
                    ((1f - value) * 5f).coerceAtMost(1f)
            }
            .blur(metrics.dp(particle.blurRadius)),
    )
}
