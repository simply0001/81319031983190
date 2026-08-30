package com.pocketpass.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.mii.MII_FIRST_SLOT
import com.pocketpass.app.mii.MII_SLOT_COUNT
import com.pocketpass.app.mii.MiiEditorEvent
import com.pocketpass.app.mii.PretendoId
import com.pocketpass.app.mii.PretendoImportState
import com.pocketpass.app.mii.PretendoLookupResult
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.BOTTOM_DESIGN_HEIGHT
import com.pocketpass.app.ui.BOTTOM_DESIGN_WIDTH
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.components.PocketKey
import com.pocketpass.app.ui.components.PocketKeyboard
import com.pocketpass.app.ui.components.PocketKeyboardLayout
import com.pocketpass.app.ui.components.PocketKeyboardPalette
import com.pocketpass.app.ui.components.TYPING_CARET_INLINE_ID
import com.pocketpass.app.ui.components.firstKeyTag
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.components.pocketShadow
import com.pocketpass.app.ui.components.typingCaretInline
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.controllerFocusBarrier
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.theme.pocketPalette

@Composable
internal fun PretendoImportButton(
    metrics: DesignMetrics,
    enabled: Boolean,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    val shape = RoundedCornerShape(metrics.dp(39f))
    val onClick = { dispatch(PocketPassEvent.Mii(MiiEditorEvent.OpenPretendoImport)) }
    Box(
        modifier = Modifier
            .designBounds(metrics, 240f, 690f, 600f, 78f)
            .clip(shape)
            .pocketFrame(
                Brush.verticalGradient(listOf(palette.surface, palette.tint(Color(0xFFD6ECF5)))),
                metrics.dp(7f),
                palette.tealBorder,
                shape,
            )
            .testTag(PRETENDO_IMPORT_BUTTON_TAG)
            .controllerTarget(PRETENDO_IMPORT_BUTTON_TAG, layer = 10, cornerRadius = 39f) {
                if (enabled) onClick()
            }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Import from Pretendo",
            color = palette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            maxLines = 1,
        )
    }
}

@Composable
internal fun PretendoImportOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    import: PretendoImportState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    val focus = LocalControllerFocus.current
    val found = import.found
    LaunchedEffect(found != null) {
        if (found != null) focus?.focus(PRETENDO_CONFIRM_TAG, reveal = false)
    }
    val close = { dispatch(PocketPassEvent.Mii(MiiEditorEvent.ClosePretendoImport)) }
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, BOTTOM_DESIGN_WIDTH, BOTTOM_DESIGN_HEIGHT)
            .background(palette.scrim)
            .testTag("pretendo_import_overlay")
            .controllerFocusBarrier("pretendo_import_barrier", layer = PRETENDO_FOCUS_LAYER)
            .clickable(
                enabled = !import.lookingUp,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = close,
            ),
    )
    if (found == null) {
        PretendoEntryPanel(metrics, import, dispatch)
        PocketKeyboard(
            metrics = metrics,
            layout = PocketKeyboardLayout.Text,
            submitLabel = if (import.lookingUp) "Looking up…" else "Look up",
            submitEnabled = import.canLookup,
            canBackspace = import.pnid.isNotEmpty(),
            onKey = { key ->
                when (key) {
                    is PocketKey.Character -> dispatch(
                        PocketPassEvent.Mii(
                            MiiEditorEvent.SetPretendoId(import.pnid + key.value.lowercase()),
                        ),
                    )

                    PocketKey.Backspace -> dispatch(
                        PocketPassEvent.Mii(MiiEditorEvent.SetPretendoId(import.pnid.dropLast(1))),
                    )

                    PocketKey.Submit -> dispatch(PocketPassEvent.Mii(MiiEditorEvent.LookupPretendoMii))
                    else -> Unit
                }
            },
            palette = PocketKeyboardPalette.Messages,
            focusLayer = PRETENDO_FOCUS_LAYER,
            focusReturnTag = PRETENDO_IMPORT_BUTTON_TAG,
            topRowUpTarget = { PRETENDO_FIELD_TAG },
        )
    } else {
        PretendoPreviewPanel(metrics, state, import, found, dispatch)
    }
}

@Composable
private fun PretendoEntryPanel(
    metrics: DesignMetrics,
    import: PretendoImportState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    val focus = LocalControllerFocus.current
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier
            .designBounds(metrics, 80f, 74f, 1080f, 500f)
            .graphicsLayer { translationY = entrance.value }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 60f, 1080f, 500f)
            .graphicsLayer { translationY = entrance.value }
            .clip(panelShape)
            .pocketFrame(greyPanelBrush(), metrics.dp(15f), palette.borderGrey, panelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("pretendo_import_panel"),
    ) {
        Text(
            text = "Import from Pretendo",
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
            text = "Type a Pretendo Network ID to copy its Mii",
            modifier = Modifier.designBounds(metrics, 60f, 140f, 960f, 46f),
            color = palette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(32f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        PretendoIdField(
            metrics = metrics,
            x = 60f,
            y = 220f,
            width = 960f,
            height = 120f,
            value = import.pnid,
            onClick = { focus?.focus(firstKeyTag(PocketKeyboardLayout.Text), reveal = false) },
        )
        val error = import.error
        Text(
            text = error ?: "6 to 16 letters, numbers, - _ or .",
            modifier = Modifier.designBounds(metrics, 60f, 372f, 960f, 90f),
            color = if (error != null) palette.ink(Color(0xFFB31E3A)) else palette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(30f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PretendoIdField(
    metrics: DesignMetrics,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    value: String,
    onClick: () -> Unit,
) {
    val palette = pocketPalette
    val shape = RoundedCornerShape(metrics.dp(height / 2f))
    Box(
        modifier = Modifier
            .designBounds(metrics, x, y, width, height)
            .clip(shape)
            .pocketFrame(palette.surfaceSunken, metrics.dp(8f), palette.tealBorder, shape)
            .testTag(PRETENDO_FIELD_TAG)
            .controllerTarget(
                PRETENDO_FIELD_TAG,
                layer = PRETENDO_FOCUS_LAYER,
                cornerRadius = height / 2f,
            ) { onClick() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        val caretInline = typingCaretInline(metrics, palette.teal, 52f)
        Text(
            text = buildAnnotatedString {
                if (value.isEmpty()) {
                    appendInlineContent(TYPING_CARET_INLINE_ID, "|")
                    append("pretendo id")
                } else {
                    append(value)
                    appendInlineContent(TYPING_CARET_INLINE_ID, "|")
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
            text = "${value.length}/${PretendoId.MAX_LENGTH}",
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
private fun PretendoPreviewPanel(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    import: PretendoImportState,
    found: PretendoLookupResult.Found,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier
            .designBounds(metrics, 80f, 114f, 1080f, 860f)
            .graphicsLayer { translationY = entrance.value }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 100f, 1080f, 860f)
            .graphicsLayer { translationY = entrance.value }
            .clip(panelShape)
            .pocketFrame(greyPanelBrush(), metrics.dp(15f), palette.borderGrey, panelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("pretendo_import_panel"),
    ) {
        Text(
            text = "Import this Mii?",
            modifier = Modifier.designBounds(metrics, 60f, 44f, 960f, 90f),
            color = palette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(70f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .designBounds(metrics, 70f, 160f, 220f, 220f)
                .clip(CircleShape)
                .background(palette.surfaceSunken)
                .testTag("pretendo_preview"),
        ) {
            DynamicAvatar(
                avatar = AvatarReference.Remote(found.portraitUrl),
                fallbackResource = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            text = found.miiName.ifBlank { found.pnid },
            modifier = Modifier.designBounds(metrics, 330f, 176f, 690f, 80f),
            color = palette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(56f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "@${found.pnid} on Pretendo Network",
            modifier = Modifier.designBounds(metrics, 330f, 266f, 690f, 50f),
            color = palette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(32f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Choose the Piip slot it goes into",
            modifier = Modifier.designBounds(metrics, 60f, 424f, 960f, 46f),
            color = palette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(32f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        (MII_FIRST_SLOT..MII_SLOT_COUNT).forEach { slot ->
            val summary = state.miiEditor.slots[slot]
            PretendoSlotChip(
                metrics = metrics,
                x = 60f + (slot - MII_FIRST_SLOT) * 330f,
                y = 490f,
                slot = slot,
                selected = slot == import.slot,
                empty = summary == null || summary.isEmpty,
                onClick = {
                    dispatch(PocketPassEvent.Mii(MiiEditorEvent.SelectPretendoImportSlot(slot)))
                },
            )
        }
        Text(
            text = "Hats aren't imported — pick one in the editor.",
            modifier = Modifier.designBounds(metrics, 60f, 632f, 960f, 40f),
            color = palette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(28f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        PretendoActionButton(
            metrics = metrics,
            x = 60f,
            y = 690f,
            label = "Change",
            fill = cancelButtonBrush(),
            borderColor = Color(0xFF8A8A8A),
            tag = "pretendo_import_change",
            onClick = { dispatch(PocketPassEvent.Mii(MiiEditorEvent.SetPretendoId(import.pnid))) },
        )
        PretendoActionButton(
            metrics = metrics,
            x = 550f,
            y = 690f,
            label = "Import & edit",
            fill = greenButtonBrush(),
            borderColor = Color(0xFF3CBC29),
            tag = PRETENDO_CONFIRM_TAG,
            onClick = { dispatch(PocketPassEvent.Mii(MiiEditorEvent.ConfirmPretendoImport)) },
        )
    }
}

@Composable
private fun PretendoSlotChip(
    metrics: DesignMetrics,
    x: Float,
    y: Float,
    slot: Int,
    selected: Boolean,
    empty: Boolean,
    onClick: () -> Unit,
) {
    val palette = pocketPalette
    val shape = RoundedCornerShape(metrics.dp(55f))
    val tag = "pretendo_slot_$slot"
    Box(
        modifier = Modifier
            .designBounds(metrics, x, y, 300f, 110f)
            .clip(shape)
            .pocketFrame(
                if (selected) greenButtonBrush() else greyButtonBrush(),
                metrics.dp(10f),
                if (selected) Color(0xFF3CBC29) else palette.borderGrey,
                shape,
            )
            .testTag(tag)
            .controllerTarget(tag, layer = PRETENDO_FOCUS_LAYER, cornerRadius = 55f) { onClick() }
            .clickable(
                interactionSource = remember(tag) { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Piip $slot · ${if (empty) "empty" else "replace"}",
            color = Color.White,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            maxLines = 1,
        )
    }
}

@Composable
private fun PretendoActionButton(
    metrics: DesignMetrics,
    x: Float,
    y: Float,
    label: String,
    fill: Brush,
    borderColor: Color,
    tag: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(118f))
    Box(
        modifier = Modifier
            .designBounds(metrics, x, y, 470f, 150f)
            .clip(shape)
            .pocketFrame(fill, metrics.dp(20.152f), borderColor, shape)
            .testTag(tag)
            .controllerTarget(tag, layer = PRETENDO_FOCUS_LAYER) { onClick() }
            .clickable(
                interactionSource = remember(tag) { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(44f),
            maxLines = 1,
        )
    }
}

internal const val PRETENDO_IMPORT_BUTTON_TAG = "mii_import_pretendo"
private const val PRETENDO_FIELD_TAG = "pretendo_id_field"
private const val PRETENDO_CONFIRM_TAG = "pretendo_import_confirm"
private const val PRETENDO_FOCUS_LAYER = 20
