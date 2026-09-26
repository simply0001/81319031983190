package com.pocketpass.app.ui.screens

import com.pocketpass.app.audio.LocalSoundEffects
import com.pocketpass.app.audio.SoundEffect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.pocketpass.app.domain.model.ChatBubbleColour
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignAnchor
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.anchoredBounds
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.PocketPanel
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.theme.pocketPalette

@Composable
internal fun ChatColoursPanel(metrics: DesignMetrics, y: Float, onClick: () -> Unit) {
    PocketPanel(metrics, x = 50f, y = y, width = 1140f, height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey, borderWidth = 20.152f, radius = 110f,
        fillBrush = greyPanelBrush(), tag = "settings_chat_colours", onClick = onClick) {
        SettingsHeading(metrics, Assets.SettingsChatColours, "Chat Colours", "Choose your message colour")
        FigmaAsset(Assets.SettingsArrow, colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End))
    }
}

@Composable
internal fun ChatColoursContent(metrics: DesignMetrics, state: PocketPassUiState, dispatch: (PocketPassEvent) -> Unit) {
    val sounds = LocalSoundEffects.current
    val profile = state.profile
    val saved = profile?.chatBubbleColour ?: ChatBubbleColour.Default
    var draft by remember(profile?.userId) { mutableStateOf(saved) }
    var dirty by remember(profile?.userId) { mutableStateOf(false) }
    LaunchedEffect(saved) { if (!dirty) draft = saved }
    val palette = remember(draft) { chatBubblePalette(draft) }
    val previewShape = RoundedCornerShape(metrics.dp(80f))
    Column(Modifier.fillMaxWidth().padding(horizontal = metrics.dp(64f)).testTag("chat_colours_content"),
        verticalArrangement = Arrangement.spacedBy(metrics.dp(28f))) {
        Box(Modifier.fillMaxWidth().clip(previewShape)
            .pocketFrame(Brush.verticalGradient(colorStops = palette.fill), metrics.dp(16f), palette.border, previewShape)
            .testTag("chat_colour_preview").padding(metrics.dp(42f))) {
            Column {
                Text("Your messages", color = palette.text, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = metrics.sp(30f))
                Spacer(Modifier.height(metrics.dp(12f)))
                Text("Hello! This is my chat colour.", color = palette.text, fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold, fontSize = metrics.sp(48f))
            }
        }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(metrics.dp(22f))) {
            ChatBubbleColour.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(22f))) {
                    row.forEach { colour ->
                        val swatch = chatBubblePalette(colour)
                        val selected = colour == draft
                        val shape = RoundedCornerShape(metrics.dp(58f))
                        val tag = "chat_colour_${colour.key}"
                        val choose = { sounds.play(SoundEffect.Confirm); draft = colour; dirty = true }
                        Box(Modifier.weight(1f).height(metrics.dp(132f)).clip(shape)
                            .pocketFrame(Brush.verticalGradient(colorStops = swatch.fill), metrics.dp(if (selected) 14f else 7f),
                                if (selected) pocketPalette.textPrimary else swatch.border, shape)
                            .testTag(tag)
                            .then(if (profile != null) Modifier.controllerTarget(tag, cornerRadius = 58f, onActivate = choose) else Modifier)
                            .selectable(selected, enabled = profile != null, role = Role.RadioButton,
                                interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = choose),
                            contentAlignment = Alignment.Center) {
                            Text((if (selected) "✓ " else "") + colour.name, color = swatch.text, fontFamily = Rubik,
                                fontWeight = FontWeight.Bold, fontSize = metrics.sp(34f), maxLines = 1)
                        }
                    }
                }
            }
        }
        Text("Everyone sees this colour on your messages.", color = pocketPalette.textPrimary, fontFamily = Rubik,
            fontSize = metrics.sp(32f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(22f))) {
            ChatColourButton(metrics, "Reset to Default", "chat_colour_reset", profile != null, Modifier.weight(1f)) {
                sounds.play(SoundEffect.Confirm)
                draft = ChatBubbleColour.Default; dirty = true
            }
            ChatColourButton(metrics, if (state.chatColourSaving) "Saving…" else "Save", "chat_colour_save",
                profile != null && !state.chatColourSaving && (draft != saved || profile.chatColourError != null || state.chatColourSaveError != null),
                Modifier.weight(1f)) {
                dirty = false
                dispatch(PocketPassEvent.SaveChatColour(draft))
            }
        }
        val status = state.chatColourSaveError ?: profile?.chatColourError ?: when {
            profile == null -> "Loading your profile…"
            state.chatColourSaving -> "Saving your colour…"
            profile.chatColourPending -> "Saved on this device. Waiting to sync online."
            draft != saved -> "Preview only — save to share your colour."
            else -> "Your colour is up to date."
        }
        Text(status, color = pocketPalette.textSecondary, fontFamily = Rubik, fontSize = metrics.sp(28f),
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().testTag("chat_colour_status"))
        Spacer(Modifier.height(metrics.dp(36f)))
    }
}

@Composable
private fun ChatColourButton(metrics: DesignMetrics, label: String, tag: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(metrics.dp(65f))
    Box(modifier.height(metrics.dp(130f)).alpha(if (enabled) 1f else 0.45f).clip(shape)
        .pocketFrame(greyPanelBrush(), metrics.dp(14f), pocketPalette.borderGrey, shape).testTag(tag)
        .then(if (enabled) Modifier.controllerTarget(tag, cornerRadius = 65f, onActivate = onClick) else Modifier)
        .clickable(enabled = enabled, role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(label, color = pocketPalette.textPrimary, fontFamily = Rubik, fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(36f), maxLines = 1)
    }
}
