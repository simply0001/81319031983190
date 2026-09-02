package com.pocketpass.app.ui.setup

import com.pocketpass.app.ui.platformAnimationsEnabled
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.pocketpass.app.ui.components.Text
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pocketpass.app.domain.model.PROFILE_NAME_MAX_LENGTH
import com.pocketpass.app.feature.AccountSetupEvent
import com.pocketpass.app.feature.AccountSetupStep
import com.pocketpass.app.feature.AccountSetupUiState
import com.pocketpass.app.model.BIO_MAX_LENGTH
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.BOTTOM_DESIGN_HEIGHT
import com.pocketpass.app.ui.BOTTOM_DESIGN_WIDTH
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.auth.AuthButton
import com.pocketpass.app.ui.auth.AuthTextAction
import com.pocketpass.app.ui.auth.PocketBorder
import com.pocketpass.app.ui.auth.PocketGreenText
import com.pocketpass.app.ui.auth.PocketTeal
import com.pocketpass.app.ui.auth.PocketWhitePanel
import com.pocketpass.app.ui.auth.pocketAuthText
import com.pocketpass.app.ui.components.PatternBackground
import com.pocketpass.app.ui.components.PocketKey
import com.pocketpass.app.ui.components.PocketKeyboard
import com.pocketpass.app.ui.components.PocketKeyboardLayout
import com.pocketpass.app.ui.components.PocketPanel
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.controller.ControllerFocusViewport
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.LocalControllerFocusViewport
import com.pocketpass.app.ui.controller.controllerFocusViewport
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.theme.pocketPalette
import com.pocketpass.app.model.PocketPassDestination

private val SetupErrorRed = Color(0xFF9B3434)
private val SetupSelectedGreen = Color(0xFFBDF8CB)
private val SetupSelectedText = Color(0xFF1D6B25)

@Composable
fun AccountSetupBottomScreen(
    metrics: DesignMetrics,
    state: AccountSetupUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    PatternBackground(
        metrics = metrics,
        pattern = Assets.PatternHomeBottom,
        topColor = pocketPalette.background(PocketPassDestination.Home, top = false).top,
        bottomColor = pocketPalette.background(PocketPassDestination.Home, top = false).bottom,
        holdFraction = 0.4375f,
        designWidth = BOTTOM_DESIGN_WIDTH,
        designHeight = BOTTOM_DESIGN_HEIGHT,
    )
    if (!state.resolved || !state.required) return

    val send: (AccountSetupEvent) -> Unit = remember(dispatch) {
        { dispatch(PocketPassEvent.AccountSetup(it)) }
    }
    SetupProgressDots(metrics, state.step)
    if (state.step != AccountSetupStep.Name) {
        SetupBackChevron(
            metrics = metrics,
            enabled = !state.submitting,
            onClick = { send(AccountSetupEvent.BackStep) },
        )
    }
    when (state.step) {
        AccountSetupStep.Name -> SetupNameStep(metrics, state, send)
        AccountSetupStep.Bio -> SetupBioStep(metrics, state, send)
        AccountSetupStep.Age -> SetupAgeStep(metrics, state, send)
        AccountSetupStep.Country -> SetupCountryStep(metrics, state, send)
    }
}

@Composable
private fun SetupProgressDots(
    metrics: DesignMetrics,
    step: AccountSetupStep,
) {
    val stepIndex = step.ordinal
    val dotSize = 26f
    val gap = 34f
    val totalWidth = 4 * dotSize + 3 * gap
    val startX = (BOTTOM_DESIGN_WIDTH - totalWidth) / 2f
    repeat(4) { index ->
        Box(
            modifier = Modifier
                .designBounds(
                    metrics,
                    startX + index * (dotSize + gap),
                    70f,
                    dotSize,
                    dotSize,
                )
                .clip(CircleShape)
                .background(
                    if (index <= stepIndex) PocketTeal else PocketTeal.copy(alpha = 0.22f),
                ),
        )
    }
}

@Composable
private fun SetupBackChevron(
    metrics: DesignMetrics,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .designBounds(metrics, 28f, 32f, 128f, 128f)
            .clip(CircleShape)
            .testTag("setup_back")
            .controllerTarget("setup_back") { if (enabled) onClick() }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(metrics.dp(44f)),
        ) {
            val stroke = size.width * 0.26f
            drawLine(
                color = PocketTeal,
                start = Offset(size.width, 0f),
                end = Offset(0f, size.height / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = PocketTeal,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun SetupHeader(
    metrics: DesignMetrics,
    title: String,
    description: String,
    error: String?,
) {
    Text(
        text = title,
        modifier = Modifier.designBounds(metrics, 120f, 196f, 1000f, 114f),
        style = pocketAuthText(metrics, 96f, PocketTeal, FontWeight.Bold),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
    Text(
        text = error ?: description,
        modifier = Modifier.designBounds(metrics, 70f, 334f, 1100f, 54f),
        style = pocketAuthText(
            metrics,
            38f,
            if (error != null) SetupErrorRed else PocketGreenText.copy(alpha = 0.85f),
            FontWeight.SemiBold,
        ),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SetupCounter(
    metrics: DesignMetrics,
    text: String,
) {
    Text(
        text = text,
        modifier = Modifier.designBounds(metrics, 102f, 396f, 1036f, 40f),
        style = pocketAuthText(metrics, 30f, PocketBorder, FontWeight.SemiBold),
        textAlign = TextAlign.End,
        maxLines = 1,
    )
}

@Composable
private fun SetupNameStep(
    metrics: DesignMetrics,
    state: AccountSetupUiState,
    send: (AccountSetupEvent) -> Unit,
) {
    SetupHeader(
        metrics = metrics,
        title = "Username",
        description = "The name everyone will see",
        error = state.error,
    )
    SetupCounter(metrics, "${state.nameDraft.length}/$PROFILE_NAME_MAX_LENGTH")
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state.errorShakeNonce) {
        if (state.errorShakeNonce > 0 && platformAnimationsEnabled()) {
            shake.snapTo(0f)
            listOf(-11f, 9f, -6f, 4f, 0f).forEach { offset ->
                shake.animateTo(offset, tween(48))
            }
        }
    }
    Box(Modifier.graphicsLayer { translationX = shake.value }) {
        PocketPanel(
            metrics = metrics,
            x = 102f,
            y = 448f,
            width = 1036f,
            height = 166f,
            borderColor = PocketBorder,
            borderWidth = 18f,
            radius = 118f,
            fillBrush = PocketWhitePanel,
            tag = "setup_name_input",
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val empty = state.nameDraft.isEmpty()
                Text(
                    text = if (empty) "yourname" else state.nameDraft,
                    style = pocketAuthText(
                        metrics,
                        55f,
                        if (empty) PocketGreenText.copy(alpha = 0.56f) else PocketGreenText,
                        FontWeight.Medium,
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
    PocketKeyboard(
        metrics = metrics,
        layout = PocketKeyboardLayout.Text,
        submitLabel = "Continue",
        submitEnabled = state.canContinue,
        onKey = { key ->
            when (key) {
                is PocketKey.Character ->
                    send(AccountSetupEvent.NameChanged(state.nameDraft + key.value))
                PocketKey.Backspace ->
                    send(AccountSetupEvent.NameChanged(state.nameDraft.dropLast(1)))
                PocketKey.Space, PocketKey.Alphabet, PocketKey.Emoji -> Unit
                PocketKey.Submit -> send(AccountSetupEvent.Continue)
            }
        },
    )
}

@Composable
private fun SetupBioStep(
    metrics: DesignMetrics,
    state: AccountSetupUiState,
    send: (AccountSetupEvent) -> Unit,
) {
    SetupHeader(
        metrics = metrics,
        title = "Your Bio",
        description = "Say hello to the people you meet",
        error = state.error,
    )
    SetupCounter(metrics, "${state.bioDraft.length}/$BIO_MAX_LENGTH")
    val fieldShape = RoundedCornerShape(metrics.dp(45f))
    Box(
        modifier = Modifier
            .designBounds(metrics, 102f, 448f, 1036f, 166f)
            .clip(fieldShape)
            .pocketFrame(pocketPalette.surfaceSunken, metrics.dp(8f), PocketBorder, fieldShape)
            .testTag("setup_bio_input"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = metrics.dp(36f),
                    vertical = metrics.dp(20f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val empty = state.bioDraft.isEmpty()
            Text(
                text = if (empty) "Say hello to everyone you meet!" else state.bioDraft,
                style = pocketAuthText(
                    metrics,
                    38f,
                    if (empty) Color(0xFF8FB9C6) else PocketTeal,
                    FontWeight.SemiBold,
                ).copy(lineHeight = metrics.sp(48f)),
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    PocketKeyboard(
        metrics = metrics,
        layout = PocketKeyboardLayout.Text,
        submitLabel = "Continue",
        submitEnabled = state.canContinue,
        onKey = { key ->
            when (key) {
                is PocketKey.Character ->
                    send(AccountSetupEvent.BioChanged(state.bioDraft + key.value))
                PocketKey.Space ->
                    send(AccountSetupEvent.BioChanged(state.bioDraft + " "))
                PocketKey.Backspace ->
                    send(AccountSetupEvent.BioChanged(state.bioDraft.dropLast(1)))
                PocketKey.Alphabet, PocketKey.Emoji -> Unit
                PocketKey.Submit -> send(AccountSetupEvent.Continue)
            }
        },
    )
}

@Composable
private fun SetupAgeStep(
    metrics: DesignMetrics,
    state: AccountSetupUiState,
    send: (AccountSetupEvent) -> Unit,
) {
    SetupHeader(
        metrics = metrics,
        title = "Your Age",
        description = "Optional, shown on your profile",
        error = state.error,
    )
    SetupCounter(metrics, "13-120")
    PocketPanel(
        metrics = metrics,
        x = 402f,
        y = 448f,
        width = 436f,
        height = 166f,
        borderColor = PocketBorder,
        borderWidth = 18f,
        radius = 83f,
        fillBrush = PocketWhitePanel,
        tag = "setup_age_input",
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val empty = state.ageDraft.isEmpty()
            Text(
                text = if (empty) "--" else state.ageDraft,
                style = pocketAuthText(
                    metrics,
                    70f,
                    if (empty) PocketGreenText.copy(alpha = 0.45f) else PocketGreenText,
                    FontWeight.Bold,
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
    AuthTextAction(
        metrics = metrics,
        x = 880f,
        y = 486f,
        width = 230f,
        height = 90f,
        label = "Skip",
        tag = "setup_age_skip",
        onClick = { send(AccountSetupEvent.SkipAge) },
        enabled = !state.submitting,
    )
    PocketKeyboard(
        metrics = metrics,
        layout = PocketKeyboardLayout.Numeric,
        submitLabel = "Continue",
        submitEnabled = state.canContinue,
        onKey = { key ->
            when (key) {
                is PocketKey.Character ->
                    send(AccountSetupEvent.AgeChanged(state.ageDraft + key.value))
                PocketKey.Backspace ->
                    send(AccountSetupEvent.AgeChanged(state.ageDraft.dropLast(1)))
                PocketKey.Space, PocketKey.Alphabet, PocketKey.Emoji -> Unit
                PocketKey.Submit -> send(AccountSetupEvent.Continue)
            }
        },
    )
}

@Composable
private fun SetupCountryStep(
    metrics: DesignMetrics,
    state: AccountSetupUiState,
    send: (AccountSetupEvent) -> Unit,
) {
    SetupHeader(
        metrics = metrics,
        title = "Country",
        description = "Pick where you play from",
        error = state.error,
    )
    val expanded = state.countryListExpanded
    val focus = LocalControllerFocus.current
    val listState = rememberLazyListState()
    val collapseMotion by animateFloatAsState(
        targetValue = if (expanded) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 340f),
        label = "country list collapse",
    )
    val collapse = collapseMotion.coerceIn(0f, 1f)
    var wasExpanded by remember { mutableStateOf(expanded) }
    LaunchedEffect(expanded) {
        val code = state.countryCode
        val index = CountryCatalog.countries.indexOfFirst { it.code == code }
        if (!expanded && index >= 0) {
            listState.scrollToItem(index)
            focus?.focus("setup_finish", reveal = false)
        } else if (expanded && !wasExpanded && code != null) {
            focus?.focus("setup_country_$code", reveal = false)
        }
        wasExpanded = expanded
    }
    val listShape = RoundedCornerShape(metrics.dp(COUNTRY_LIST_RADIUS))
    val listFocusViewport = remember(listShape) { ControllerFocusViewport(shape = listShape) }
    val listHeight = COUNTRY_LIST_EXPANDED_HEIGHT -
        (COUNTRY_LIST_EXPANDED_HEIGHT - COUNTRY_LIST_COLLAPSED_HEIGHT) * collapse
    Box(
        modifier = Modifier
            .designBounds(metrics, COUNTRY_LIST_X, COUNTRY_LIST_Y, COUNTRY_LIST_WIDTH, listHeight)
            .clip(listShape)
            .pocketFrame(
                pocketPalette.surface,
                metrics.dp(COUNTRY_LIST_BORDER),
                PocketBorder,
                listShape,
            )
            .then(
                if (expanded) {
                    Modifier
                } else {
                    Modifier.controllerTarget(
                        "setup_country_list",
                        cornerRadius = COUNTRY_LIST_RADIUS,
                    ) { send(AccountSetupEvent.ExpandCountryList) }
                },
            )
            .controllerFocusViewport(listFocusViewport)
            .testTag("setup_country_list"),
    ) {
        CompositionLocalProvider(LocalControllerFocusViewport provides listFocusViewport) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = metrics.dp(28f),
                        vertical = metrics.dp((COUNTRY_LIST_BORDER + COUNTRY_ROW_GAP) * collapse),
                    ),
                contentPadding = PaddingValues(
                    top = metrics.dp(COUNTRY_LIST_TOP_INSET * (1f - collapse)),
                    bottom = metrics.dp(130f),
                ),
                userScrollEnabled = expanded,
            ) {
                items(CountryCatalog.countries, key = CountryOption::code) { country ->
                    val selected = country.code == state.countryCode
                    val selection by animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = tween(durationMillis = 240),
                        label = "country ${country.code}",
                    )
                    val rowShape = RoundedCornerShape(metrics.dp(48f))
                    val interaction = remember(country.code) { MutableInteractionSource() }
                    val rowTeal = pocketPalette.ink(PocketTeal)
                    val rowSelected = pocketPalette.ink(SetupSelectedText)
                    val rowFill = pocketPalette.tint(SetupSelectedGreen)
                    val activate = {
                        if (expanded) {
                            send(AccountSetupEvent.CountrySelected(country.code))
                        } else {
                            send(AccountSetupEvent.ExpandCountryList)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .requiredSize(metrics.dp(980f), metrics.dp(COUNTRY_ROW_HEIGHT))
                            .clip(rowShape)
                            .drawBehind {
                                drawRect(
                                    lerp(Color.Transparent, rowFill, selection),
                                )
                            }
                            .testTag("setup_country_${country.code}")
                            .then(
                                if (expanded) {
                                    Modifier.controllerTarget(
                                        "setup_country_${country.code}",
                                        cornerRadius = 48f,
                                    ) { activate() }
                                } else {
                                    Modifier
                                },
                            )
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = activate,
                            ),
                    ) {
                        BasicText(
                            text = "${country.flag}  ${country.name}",
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(horizontal = metrics.dp(38f)),
                            style = pocketAuthText(
                                metrics,
                                44f,
                                PocketTeal,
                                FontWeight.SemiBold,
                            ),
                            color = { lerp(rowTeal, rowSelected, selection) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "✓",
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = metrics.dp(40f))
                                .graphicsLayer {
                                    alpha = selection
                                    scaleX = 0.6f + 0.4f * selection
                                    scaleY = 0.6f + 0.4f * selection
                                },
                            style = pocketAuthText(
                                metrics,
                                50f,
                                SetupSelectedText,
                                FontWeight.Bold,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
    val density = LocalDensity.current
    val buttonShiftPx = with(density) {
        metrics.dp(BOTTOM_DESIGN_HEIGHT - COUNTRY_FINISH_Y + 40f).toPx()
    }
    if (!expanded || collapse > 0.001f) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = buttonShiftPx * (1f - collapse) },
        ) {
            AuthButton(
                metrics = metrics,
                y = COUNTRY_FINISH_Y,
                label = if (state.submitting) "Saving…" else "Continue",
                tag = "setup_finish",
                onClick = { send(AccountSetupEvent.Submit) },
                enabled = !state.submitting,
            )
        }
    }
}

private const val COUNTRY_LIST_X = 102f
private const val COUNTRY_LIST_Y = 420f
private const val COUNTRY_LIST_WIDTH = 1036f
private const val COUNTRY_LIST_BORDER = 12f
private const val COUNTRY_LIST_RADIUS = 60f
private const val COUNTRY_ROW_HEIGHT = 96f
private const val COUNTRY_ROW_GAP = 20f
private const val COUNTRY_LIST_TOP_INSET = 26f
private const val COUNTRY_LIST_EXPANDED_HEIGHT = 760f
private const val COUNTRY_LIST_COLLAPSED_HEIGHT =
    2 * COUNTRY_LIST_BORDER + 2 * COUNTRY_ROW_GAP + COUNTRY_ROW_HEIGHT
private const val COUNTRY_FINISH_Y = COUNTRY_LIST_Y + COUNTRY_LIST_COLLAPSED_HEIGHT + 64f
