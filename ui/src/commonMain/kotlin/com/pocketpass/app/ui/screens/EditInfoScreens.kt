package com.pocketpass.app.ui.screens

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pocketpass.app.model.CountryEditorUiState
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignAnchor
import com.pocketpass.app.ui.DesignBox
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.PocketAsset
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.anchoredBounds
import com.pocketpass.app.ui.auth.PocketTeal
import com.pocketpass.app.ui.auth.pocketAuthText
import com.pocketpass.app.ui.components.EntranceMotion
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.PocketPanel
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.components.pocketShadow
import com.pocketpass.app.ui.controller.ControllerFocusViewport
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.LocalControllerFocusViewport
import com.pocketpass.app.ui.controller.controllerFocusBarrier
import com.pocketpass.app.ui.controller.controllerFocusViewport
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.platformAnimationsEnabled
import com.pocketpass.app.ui.setup.CountryCatalog
import com.pocketpass.app.ui.setup.CountryOption
import com.pocketpass.app.ui.theme.pocketPalette

internal fun PocketPassUiState.editInfoName(): String =
    profile?.displayName?.ifBlank { null } ?: profile?.username.orEmpty()

internal fun PocketPassUiState.editInfoAge(): String =
    profile?.age?.toString() ?: "Hidden"

internal fun PocketPassUiState.editInfoCountry(): String =
    profile?.countryCode?.let(::countryLabel) ?: "Not set"

@Composable
internal fun EditInfoBottom(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    BottomPage(entrance = EntranceMotion.None) { metrics ->
        SubpageHeader(
            metrics = metrics,
            title = "Edit Info",
            subtitle = "Your name, age and country.",
            backTag = "edit_info_back",
        ) { dispatch(PocketPassEvent.Back) }
        val scroll = rememberScrollState()
        val belowHeader = remember(metrics) { BelowSubpageHeaderShape(metrics) }
        val focusViewport = rememberBelowSubpageHeaderFocusViewport(metrics)
        DesignBox(metrics, 0f, 0f, 1240f, 1080f, DesignAnchor.Stretch, DesignAnchor.Stretch,
            modifier = Modifier.clip(belowHeader).controllerFocusViewport(focusViewport)
                .verticalScroll(scroll).testTag("edit_info_scroll")) {
            CompositionLocalProvider(LocalControllerFocusViewport provides focusViewport) {
                Box(Modifier.padding(top = metrics.dp(SUBPAGE_CONTENT_TOP))
                    .requiredWidth(metrics.dp(1240f + 2f * metrics.overscanX))
                    .requiredHeight(metrics.dp(SETTINGS_PANEL_GAP + 3 * SUBPAGE_ROW_PITCH))) {
                    EditInfoRow(metrics, SETTINGS_PANEL_GAP, Assets.SettingsEditName, "Name",
                        state.editInfoName(), "edit_info_name") { dispatch(PocketPassEvent.OpenNameEditor) }
                    EditInfoRow(metrics, SETTINGS_PANEL_GAP + SUBPAGE_ROW_PITCH, Assets.SettingsClock, "Age",
                        state.editInfoAge(), "edit_info_age") { dispatch(PocketPassEvent.OpenAgeEditor) }
                    EditInfoRow(metrics, SETTINGS_PANEL_GAP + 2 * SUBPAGE_ROW_PITCH, Assets.SettingsGlobe, "Country",
                        state.editInfoCountry(), "edit_info_country") { dispatch(PocketPassEvent.OpenCountryEditor) }
                }
            }
        }
    }
}

@Composable
internal fun EditInfoRow(
    metrics: DesignMetrics,
    y: Float,
    icon: PocketAsset,
    title: String,
    value: String,
    tag: String,
    onClick: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = tag,
        onClick = onClick,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = icon,
            title = title,
            subtitle = value,
            subtitleOverflow = TextOverflow.Ellipsis,
        )
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
        )
    }
}

@Composable
internal fun EditInfoBottomOverlays(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    NameEditorBottomOverlay(metrics, state, dispatch)
    AgeEditorBottomOverlay(metrics, state, dispatch)
    CountryEditorBottomOverlay(metrics, state, dispatch)
}

@Composable
private fun CountryEditorBottomOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    val active = state.countryEditor.takeIf { it.visible }
    var retained by remember { mutableStateOf<CountryEditorUiState?>(null) }
    val progress = remember { Animatable(0f) }
    SideEffect {
        if (active != null) retained = active
    }
    LaunchedEffect(active != null) {
        if (!platformAnimationsEnabled()) {
            progress.snapTo(if (active != null) 1f else 0f)
            if (active == null) retained = null
            return@LaunchedEffect
        }
        if (active != null) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
            )
        } else if (retained != null) {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
            )
            retained = null
        }
    }
    val editor = active ?: retained ?: return
    val close = { dispatch(PocketPassEvent.CloseCountryEditor) }
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .graphicsLayer { alpha = progress.value }
            .background(palette.scrim)
            .testTag("country_editor_overlay")
            .controllerFocusBarrier("country_editor_overlay", layer = NAME_EDITOR_FOCUS_LAYER)
            .clickable(
                enabled = active != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { close() },
    )
    Box(
        Modifier
            .designBounds(metrics, 80f, 74f, 1080f, COUNTRY_EDITOR_HEIGHT)
            .graphicsLayer {
                translationY = (1f - progress.value) * 56f
                alpha = progress.value
            }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 60f, 1080f, COUNTRY_EDITOR_HEIGHT)
            .graphicsLayer {
                translationY = (1f - progress.value) * 56f
                alpha = progress.value
            }
            .clip(panelShape)
            .pocketFrame(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to palette.surface,
                        0.62f to palette.surface,
                        1f to palette.tint(Color(0xFFBDF8CB)),
                    ),
                ),
                metrics.dp(15f),
                palette.tealBorder,
                panelShape,
            )
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("country_editor_panel"),
    ) {
        Text(
            text = "Country",
            modifier = Modifier.designBounds(metrics, 58f, 46f, 760f, 94f),
            color = palette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(72f),
            maxLines = 1,
        )
        Canvas(
            Modifier
                .designBounds(metrics, 945f, 55f, 72f, 72f)
                .testTag("close_country_editor")
                .controllerTarget("close_country_editor", layer = NAME_EDITOR_FOCUS_LAYER) { close() }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { close() },
        ) {
            val ink = palette.ink(Color(0xFF2F948C))
            drawLine(ink, Offset(size.width * 0.2f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.8f), strokeWidth = 9f, cap = StrokeCap.Round)
            drawLine(ink, Offset(size.width * 0.8f, size.height * 0.2f), Offset(size.width * 0.2f, size.height * 0.8f), strokeWidth = 9f, cap = StrokeCap.Round)
        }
        Text(
            text = editor.error ?: if (editor.savingCode != null) "Saving…" else "Pick where you play from",
            modifier = Modifier.designBounds(metrics, 58f, 150f, 964f, 50f),
            color = if (editor.error != null) palette.ink(Color(0xFFB31E3A)) else palette.tealBorder,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(36f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        CountryEditorList(
            metrics = metrics,
            selectedCode = editor.savingCode ?: state.profile?.countryCode,
            open = active != null,
            enabled = active != null && editor.savingCode == null,
        ) { code -> dispatch(PocketPassEvent.SaveCountry(code)) }
    }
}

@Composable
private fun CountryEditorList(
    metrics: DesignMetrics,
    selectedCode: String?,
    open: Boolean,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val palette = pocketPalette
    val focus = LocalControllerFocus.current
    val listState = rememberLazyListState()
    val countries = CountryCatalog.countries
    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        val index = countries.indexOfFirst { it.code == selectedCode }
        if (index >= 0) {
            listState.scrollToItem((index - 2).coerceAtLeast(0))
            focus?.focus(countryEditorTag(countries[index]), reveal = false)
        } else {
            countries.firstOrNull()?.let { focus?.focus(countryEditorTag(it), reveal = false) }
        }
    }
    val listShape = RoundedCornerShape(metrics.dp(60f))
    val listFocusViewport = remember(listShape) { ControllerFocusViewport(shape = listShape) }
    Box(
        modifier = Modifier
            .designBounds(metrics, 58f, 222f, 964f, COUNTRY_EDITOR_HEIGHT - 280f)
            .clip(listShape)
            .pocketFrame(palette.surface, metrics.dp(10f), palette.tealBorder, listShape)
            .controllerFocusViewport(listFocusViewport)
            .testTag("country_editor_list"),
    ) {
        CompositionLocalProvider(LocalControllerFocusViewport provides listFocusViewport) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = metrics.dp(22f)),
                contentPadding = PaddingValues(vertical = metrics.dp(24f)),
            ) {
                items(countries, key = CountryOption::code) { country ->
                    CountryEditorRow(
                        metrics = metrics,
                        country = country,
                        selected = country.code == selectedCode,
                        enabled = enabled,
                        onSelect = onSelect,
                    )
                }
            }
        }
    }
}

@Composable
private fun CountryEditorRow(
    metrics: DesignMetrics,
    country: CountryOption,
    selected: Boolean,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val palette = pocketPalette
    val selection by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "country editor ${country.code}",
    )
    val rowShape = RoundedCornerShape(metrics.dp(48f))
    val rowTeal = palette.ink(PocketTeal)
    val rowSelected = palette.ink(CountrySelectedText)
    val rowFill = palette.tint(Color(0xFFBDF8CB))
    val tag = countryEditorTag(country)
    val activate = { if (enabled) onSelect(country.code) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.dp(96f))
            .clip(rowShape)
            .drawBehind { drawRect(lerp(Color.Transparent, rowFill, selection)) }
            .testTag(tag)
            .controllerTarget(tag, layer = NAME_EDITOR_FOCUS_LAYER, cornerRadius = 48f) { activate() }
            .clickable(
                interactionSource = remember(country.code) { MutableInteractionSource() },
                indication = null,
                onClick = activate,
            ),
    ) {
        BasicText(
            text = "${country.flag}  ${country.name}",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = metrics.dp(38f), end = metrics.dp(110f)),
            style = pocketAuthText(metrics, 44f, PocketTeal, FontWeight.SemiBold),
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
            style = pocketAuthText(metrics, 50f, CountrySelectedText, FontWeight.Bold),
            maxLines = 1,
        )
    }
}

private fun countryEditorTag(country: CountryOption): String = "country_editor_${country.code}"

private val CountrySelectedText = Color(0xFF1D6B25)
private const val COUNTRY_EDITOR_HEIGHT = 960f
