package com.pocketpass.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.pocketpass.app.audio.LocalSoundEffects
import com.pocketpass.app.audio.SoundEffect
import com.pocketpass.app.ui.DesignAnchor
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.FocusDirection
import com.pocketpass.app.ui.controller.controllerTarget
import kotlin.math.abs
import com.pocketpass.app.ui.anchoredBounds
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.theme.PocketPalette
import com.pocketpass.app.ui.theme.pocketPalette

const val POCKET_KEYBOARD_HEIGHT = 460f

sealed interface PocketKey {
    data class Character(val value: String) : PocketKey
    data object Backspace : PocketKey
    data object Space : PocketKey

    data object Alphabet : PocketKey
    data object Emoji : PocketKey
    data object Submit : PocketKey
}

enum class PocketKeyboardLayout {
    Text,
    Email,
    Numeric,
    Emoji,
}

data class PocketKeyboardPalette(
    val surface: Brush,
    val keyFill: Brush,
    val accentFill: Brush,
    val keyBorder: Color,
    val keyBorderWidth: Float,
    val label: Color,
    val submitFill: Brush,
    val submitLabel: Color,
) {
    companion object {
        val Messages = PocketKeyboardPalette(
            surface = Brush.verticalGradient(
                listOf(Color(0xFFDCE6EC), Color(0xFFC4D4DE)),
            ),
            keyFill = Brush.verticalGradient(listOf(Color.White, Color(0xFFEDF3F6))),
            accentFill = Brush.verticalGradient(
                listOf(Color(0xFFD3E0E7), Color(0xFFBACCD6)),
            ),
            keyBorder = Color(0xFF9FB6C1),
            keyBorderWidth = 4f,
            label = Color(0xFF1D596B),
            submitFill = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color(0xFF57E25F),
                    0.5f to Color(0xFF5EED6F),
                    0.553f to Color(0xFF57E25F),
                    1f to Color(0xFF3CBC29),
                ),
            ),
            submitLabel = Color(0xFF0E4A17),
        )

        val Home = PocketKeyboardPalette(
            surface = Brush.verticalGradient(
                listOf(Color(0xFFDCECE2), Color(0xFFC4DECE)),
            ),
            keyFill = Brush.verticalGradient(listOf(Color.White, Color(0xFFEDF6F0))),
            accentFill = Brush.verticalGradient(
                listOf(Color(0xFFD3E7DB), Color(0xFFBAD6C4)),
            ),
            keyBorder = Color(0xFF9FC1AC),
            keyBorderWidth = 4f,
            label = Color(0xFF1D596B),
            submitFill = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color(0xFF57E25F),
                    0.5f to Color(0xFF5EED6F),
                    0.553f to Color(0xFF57E25F),
                    1f to Color(0xFF3CBC29),
                ),
            ),
            submitLabel = Color(0xFF0E4A17),
        )

        val FriendCode = PocketKeyboardPalette(
            surface = Brush.verticalGradient(
                listOf(Color(0xFFFBE1FB), Color(0xFFEDBFEF)),
            ),
            keyFill = Brush.verticalGradient(listOf(Color.White, Color(0xFFFDEBFD))),
            accentFill = Brush.verticalGradient(
                listOf(Color(0xFFF4DAF5), Color(0xFFE6C0E8)),
            ),
            keyBorder = Color(0xFFCB4AC0),
            keyBorderWidth = 6f,
            label = Color(0xFF511D6B),
            submitFill = Brush.verticalGradient(
                listOf(Color.White, Color(0xFFA8FFC7)),
            ),
            submitLabel = Color(0xFF27853A),
        )

        private val greenSubmit = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color(0xFF57E25F),
                0.5f to Color(0xFF5EED6F),
                0.553f to Color(0xFF57E25F),
                1f to Color(0xFF3CBC29),
            ),
        )
    }

    fun themed(theme: PocketPalette): PocketKeyboardPalette {
        if (!theme.isDark) return this
        return when (this) {
            FriendCode -> darkOf(
                theme = theme,
                surfaceTop = Color(0xFFFBE1FB),
                surfaceBottom = Color(0xFFEDBFEF),
                accentTop = Color(0xFFF4DAF5),
                accentBottom = Color(0xFFE6C0E8),
                submitFill = Brush.verticalGradient(
                    listOf(theme.surface, theme.tint(Color(0xFFA8FFC7))),
                ),
            )
            Home -> darkOf(
                theme = theme,
                surfaceTop = Color(0xFFDCECE2),
                surfaceBottom = Color(0xFFC4DECE),
                accentTop = Color(0xFFD3E7DB),
                accentBottom = Color(0xFFBAD6C4),
                submitFill = greenSubmit,
            )
            else -> darkOf(
                theme = theme,
                surfaceTop = Color(0xFFDCE6EC),
                surfaceBottom = Color(0xFFC4D4DE),
                accentTop = Color(0xFFD3E0E7),
                accentBottom = Color(0xFFBACCD6),
                submitFill = greenSubmit,
            )
        }
    }

    private fun darkOf(
        theme: PocketPalette,
        surfaceTop: Color,
        surfaceBottom: Color,
        accentTop: Color,
        accentBottom: Color,
        submitFill: Brush,
    ): PocketKeyboardPalette = PocketKeyboardPalette(
        surface = Brush.verticalGradient(listOf(theme.tint(surfaceTop), theme.tint(surfaceBottom))),
        keyFill = Brush.verticalGradient(listOf(theme.surface, theme.surfaceLow)),
        accentFill = Brush.verticalGradient(listOf(theme.tint(accentTop), theme.tint(accentBottom))),
        keyBorder = theme.line(keyBorder),
        keyBorderWidth = keyBorderWidth,
        label = theme.ink(label),
        submitFill = submitFill,
        submitLabel = theme.ink(submitLabel),
    )
}

private val LetterRows = listOf(
    "qwertyuiop",
    "asdfghjkl",
    "zxcvbnm",
)

private val SymbolRows = listOf(
    "1234567890",
    "-/:;()\$&@",
    ".,_+?!'\"",
)

// Only the Nintendo DS characters Sudofont draws; the wrapper Text picks the font.
private val EmojiKeys: List<String> = SudofontGlyphs.map { it.text }
private val EmojiKeyLabel: String = SudofontGlyphs.first().text

private val LocalKeyboardTopRowUp = compositionLocalOf<((Float) -> String?)?> { null }

@Composable
fun PocketKeyboard(
    metrics: DesignMetrics,
    layout: PocketKeyboardLayout,
    submitLabel: String?,
    submitEnabled: Boolean,
    onKey: (PocketKey) -> Unit,
    modifier: Modifier = Modifier,
    palette: PocketKeyboardPalette = PocketKeyboardPalette.Messages,
    height: Float = POCKET_KEYBOARD_HEIGHT,
    focusLayer: Int = 0,
    focusReturnTag: String? = null,
    canBackspace: Boolean = true,
    topRowUpTarget: ((centerX: Float) -> String?)? = null,
    emojiKey: Boolean = false,
) {
    val scale = height / POCKET_KEYBOARD_HEIGHT
    val themed = palette.themed(pocketPalette)
    val focus = LocalControllerFocus.current
    val soundEffects = LocalSoundEffects.current
    val onKeyWithSound: (PocketKey) -> Unit = { key ->
        soundEffects.play(
            when (key) {
                PocketKey.Backspace -> SoundEffect.KeyboardBackspace
                PocketKey.Submit -> SoundEffect.Confirm
                else -> SoundEffect.Keyboard
            },
        )
        onKey(key)
    }
    var swappedLayout by remember { mutableStateOf(false) }
    LaunchedEffect(layout) {
        val landing = if (swappedLayout) layoutSwapKeyTag(layout) else firstKeyTag(layout)
        swappedLayout = true
        focus?.focus(landing, reveal = false)
    }
    DisposableEffect(focus, focusReturnTag) {
        onDispose {
            if (focus != null && focusReturnTag != null && focus.focusId == null) {
                focus.focus(focusReturnTag, reveal = false)
            }
        }
    }
    val latestOnKey = rememberUpdatedState(onKeyWithSound)
    DisposableEffect(focus, focusLayer, canBackspace) {
        focus?.keyboardLayer = focusLayer
        focus?.keyboardBackspace = if (canBackspace) {
            { latestOnKey.value(PocketKey.Backspace) }
        } else {
            null
        }
        onDispose {
            focus?.keyboardBackspace = null
            focus?.keyboardLayer = null
        }
    }
    DisposableEffect(focus, submitEnabled, submitLabel) {
        focus?.keyboardSubmit = if (submitEnabled && submitLabel != null) {
            { latestOnKey.value(PocketKey.Submit) }
        } else {
            null
        }
        onDispose { focus?.keyboardSubmit = null }
    }
    Box(
        modifier = modifier
            .anchoredBounds(
                metrics,
                0f,
                1080f - height,
                1240f,
                height,
                DesignAnchor.Stretch,
                DesignAnchor.End,
            )
            .background(themed.surface)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("pocket_keyboard"),
    ) {
        Box(Modifier.designBounds(metrics, metrics.overscanX, 0f, 1240f, height)) {
        CompositionLocalProvider(LocalKeyboardTopRowUp provides topRowUpTarget) {
        when (layout) {
            PocketKeyboardLayout.Text ->
                TextKeys(
                    metrics,
                    themed,
                    submitLabel,
                    submitEnabled,
                    scale,
                    focusLayer,
                    onKeyWithSound,
                    emojiKey = emojiKey,
                )

            PocketKeyboardLayout.Email ->
                TextKeys(
                    metrics,
                    themed,
                    submitLabel,
                    submitEnabled,
                    scale,
                    focusLayer,
                    onKeyWithSound,
                    emailKeys = true,
                )

            PocketKeyboardLayout.Numeric ->
                NumericKeys(metrics, themed, submitLabel, submitEnabled, scale, focusLayer, onKeyWithSound)

            PocketKeyboardLayout.Emoji ->
                EmojiKeysGrid(metrics, themed, submitLabel, submitEnabled, scale, focusLayer, onKeyWithSound)
        }
        }
        }
    }
}

@Composable
private fun EmojiKeysGrid(
    metrics: DesignMetrics,
    palette: PocketKeyboardPalette,
    submitLabel: String?,
    submitEnabled: Boolean,
    scale: Float,
    focusLayer: Int,
    onKey: (PocketKey) -> Unit,
) {
    val columns = 10
    val keySize = 108f
    val gap = 10f * scale
    val rowStartX = { length: Int -> (1240f - (length * keySize + (length - 1) * gap)) / 2f }
    val topPadding = 22f * scale
    val keyHeight = 88f * scale
    val backspaceX = if (submitLabel != null) BACKSPACE_X else BACKSPACE_X_NO_SUBMIT

    val emojiSlots = EmojiKeys.indices.chunked(columns).map { rowIndices ->
        val startX = rowStartX(rowIndices.size)
        rowIndices.map { index ->
            val column = index % columns
            KeySlot("key_emoji_$index", startX + column * (keySize + gap) + keySize / 2f)
        }
    }
    val bottomSlots = buildList {
        add(KeySlot("key_alphabet", 40f + 168f / 2f))
        add(KeySlot("key_backspace", backspaceX + 168f / 2f))
        if (submitLabel != null) add(KeySlot("key_submit", 894f + 306f / 2f))
    }
    val neighbors = wrapNeighbors(emojiSlots + listOf(bottomSlots), LocalKeyboardTopRowUp.current)

    EmojiKeys.forEachIndexed { index, emoji ->
        val row = index / columns
        val column = index % columns
        val rowLength = minOf(columns, EmojiKeys.size - row * columns)
        val tag = "key_emoji_$index"
        PocketKeyButton(
            metrics = metrics,
            palette = palette,
            focusLayer = focusLayer,
            x = rowStartX(rowLength) + column * (keySize + gap),
            y = topPadding + row * (keyHeight + gap),
            width = keySize,
            height = keyHeight,
            label = emoji,
            fontSize = 46f,
            tag = tag,
            neighbors = neighbors[tag].orEmpty(),
            onClick = { onKey(PocketKey.Character(emoji)) },
        )
    }

    val bottomY = topPadding + 3f * (keyHeight + gap)
    PocketKeyButton(
        metrics = metrics,
        palette = palette,
        focusLayer = focusLayer,
        x = 40f,
        y = bottomY,
        width = 168f,
        height = keyHeight,
        label = "ABC",
        fontSize = 34f,
        fill = palette.accentFill,
        tag = "key_alphabet",
        neighbors = neighbors["key_alphabet"].orEmpty(),
        onClick = { onKey(PocketKey.Alphabet) },
    )
    PocketKeyButton(
        metrics = metrics,
        palette = palette,
        focusLayer = focusLayer,
        x = backspaceX,
        y = bottomY,
        width = 168f,
        height = keyHeight,
        label = "Backspace",
        icon = Icons.AutoMirrored.Filled.Backspace,
        fill = palette.accentFill,
        tag = "key_backspace",
        neighbors = neighbors["key_backspace"].orEmpty(),
        onClick = { onKey(PocketKey.Backspace) },
    )
    if (submitLabel != null) {
        PocketKeyButton(
            metrics = metrics,
            palette = palette,
            focusLayer = focusLayer,
            x = 894f,
            y = bottomY,
            width = 306f,
            height = keyHeight,
            label = submitLabel,
            fontSize = 36f,
            enabled = submitEnabled,
            fill = palette.submitFill,
            labelColor = palette.submitLabel,
            tag = "key_submit",
            neighbors = neighbors["key_submit"].orEmpty(),
            onClick = { onKey(PocketKey.Submit) },
        )
    }
}

@Composable
private fun TextKeys(
    metrics: DesignMetrics,
    palette: PocketKeyboardPalette,
    submitLabel: String?,
    submitEnabled: Boolean,
    scale: Float,
    focusLayer: Int,
    onKey: (PocketKey) -> Unit,
    emailKeys: Boolean = false,
    emojiKey: Boolean = false,
) {
    var shifted by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }
    val rows = if (symbols) SymbolRows else LetterRows

    val keyWidth = 108f
    val keyHeight = 88f * scale
    val gap = 10f * scale
    val topPadding = 22f * scale
    val bottomY = topPadding + 3f * (keyHeight + gap)
    val backspaceX = if (submitLabel != null) BACKSPACE_X else BACKSPACE_X_NO_SUBMIT
    // The ?123/ABC key sits at the left of the third row, so the bottom row
    // runs shift, emoji, space, backspace, submit with room for the space bar.
    val showEmojiKey = emojiKey && !emailKeys
    val emojiX = if (symbols) MODE_KEY_X else MODE_KEY_X + 130f + KEY_ROW_GAP
    val spaceX = when {
        showEmojiKey -> emojiX + 130f + KEY_ROW_GAP
        symbols -> MODE_KEY_X
        else -> MODE_KEY_X + 130f + KEY_ROW_GAP
    }
    val modeKeyY = topPadding + MODE_KEY_ROW * (keyHeight + gap)
    val rowStart = { rowIndex: Int, row: String ->
        if (rowIndex == MODE_KEY_ROW) {
            MODE_KEY_ROW_START
        } else {
            (1240f - (row.length * keyWidth + (row.length - 1) * gap)) / 2f
        }
    }
    val spaceWidth = backspaceX - KEY_ROW_GAP - spaceX
    val shortcutWidth = (spaceWidth - KEY_ROW_GAP) / 2f

    val letterSlots = rows.mapIndexed { rowIndex, row ->
        val startX = rowStart(rowIndex, row)
        val keys = row.mapIndexed { index, character ->
            KeySlot("key_$character", startX + index * (keyWidth + gap) + keyWidth / 2f)
        }
        if (rowIndex == MODE_KEY_ROW) {
            listOf(KeySlot("key_symbols", MODE_KEY_X + MODE_KEY_WIDTH / 2f)) + keys
        } else {
            keys
        }
    }
    val bottomSlots = buildList {
        if (!symbols) add(KeySlot("key_shift", MODE_KEY_X + 130f / 2f))
        if (showEmojiKey) add(KeySlot("key_emoji", emojiX + 130f / 2f))
        if (emailKeys) {
            EMAIL_SHORTCUTS.forEachIndexed { index, shortcut ->
                add(
                    KeySlot(
                        emailShortcutTag(shortcut),
                        spaceX + index * (shortcutWidth + KEY_ROW_GAP) + shortcutWidth / 2f,
                    ),
                )
            }
        } else {
            add(KeySlot("key_space", spaceX + spaceWidth / 2f))
        }
        add(KeySlot("key_backspace", backspaceX + 168f / 2f))
        if (submitLabel != null) add(KeySlot("key_submit", 894f + 306f / 2f))
    }
    val neighbors = wrapNeighbors(letterSlots + listOf(bottomSlots), LocalKeyboardTopRowUp.current)

    rows.forEachIndexed { rowIndex, row ->
        val startX = rowStart(rowIndex, row)
        val y = topPadding + rowIndex * (keyHeight + gap)
        row.forEachIndexed { index, character ->
            val label = if (shifted && !symbols) {
                character.uppercaseChar().toString()
            } else {
                character.toString()
            }
            val tag = "key_$character"
            PocketKeyButton(
                metrics = metrics,
                palette = palette,
                focusLayer = focusLayer,
                x = startX + index * (keyWidth + gap),
                y = y,
                width = keyWidth,
                height = keyHeight,
                label = label,
                tag = tag,
                neighbors = neighbors[tag].orEmpty(),
                onClick = {
                    onKey(PocketKey.Character(label))
                    if (shifted && !symbols) shifted = false
                },
            )
        }
    }

    PocketKeyButton(
        metrics = metrics,
        palette = palette,
        focusLayer = focusLayer,
        x = MODE_KEY_X,
        y = modeKeyY,
        width = MODE_KEY_WIDTH,
        height = keyHeight,
        label = if (symbols) "ABC" else "?123",
        fontSize = 34f,
        fill = palette.accentFill,
        tag = "key_symbols",
        neighbors = neighbors["key_symbols"].orEmpty(),
        onClick = { symbols = !symbols },
    )
    if (!symbols) {
        PocketKeyButton(
            metrics = metrics,
            palette = palette,
            focusLayer = focusLayer,
            x = MODE_KEY_X,
            y = bottomY,
            width = 130f,
            height = keyHeight,
            label = "Shift",
            icon = Icons.Filled.KeyboardCapslock,
            fill = if (shifted) shiftActiveFill() else palette.accentFill,
            tag = "key_shift",
            neighbors = neighbors["key_shift"].orEmpty(),
            onClick = { shifted = !shifted },
        )
    }
    if (showEmojiKey) {
        PocketKeyButton(
            metrics = metrics,
            palette = palette,
            focusLayer = focusLayer,
            x = emojiX,
            y = bottomY,
            width = 130f,
            height = keyHeight,
            label = EmojiKeyLabel,
            fontSize = 40f,
            fill = palette.accentFill,
            tag = "key_emoji",
            neighbors = neighbors["key_emoji"].orEmpty(),
            onClick = { onKey(PocketKey.Emoji) },
        )
    }
    if (emailKeys) {
        EMAIL_SHORTCUTS.forEachIndexed { index, shortcut ->
            val tag = emailShortcutTag(shortcut)
            PocketKeyButton(
                metrics = metrics,
                palette = palette,
                focusLayer = focusLayer,
                x = spaceX + index * (shortcutWidth + KEY_ROW_GAP),
                y = bottomY,
                width = shortcutWidth,
                height = keyHeight,
                label = shortcut,
                fontSize = 38f,
                fill = palette.accentFill,
                tag = tag,
                neighbors = neighbors[tag].orEmpty(),
                onClick = { onKey(PocketKey.Character(shortcut)) },
            )
        }
    } else {
        PocketKeyButton(
            metrics = metrics,
            palette = palette,
            focusLayer = focusLayer,
            x = spaceX,
            y = bottomY,
            width = spaceWidth,
            height = keyHeight,
            label = "space",
            fontSize = 34f,
            tag = "key_space",
            neighbors = neighbors["key_space"].orEmpty(),
            onClick = { onKey(PocketKey.Space) },
        )
    }
    PocketKeyButton(
        metrics = metrics,
        palette = palette,
        focusLayer = focusLayer,
        x = backspaceX,
        y = bottomY,
        width = 168f,
        height = keyHeight,
        label = "Backspace",
        icon = Icons.AutoMirrored.Filled.Backspace,
        fill = palette.accentFill,
        tag = "key_backspace",
        neighbors = neighbors["key_backspace"].orEmpty(),
        onClick = { onKey(PocketKey.Backspace) },
    )
    if (submitLabel != null) {
        PocketKeyButton(
            metrics = metrics,
            palette = palette,
            focusLayer = focusLayer,
            x = 894f,
            y = bottomY,
            width = 306f,
            height = keyHeight,
            label = submitLabel,
            fontSize = 36f,
            enabled = submitEnabled,
            fill = palette.submitFill,
            labelColor = palette.submitLabel,
            tag = "key_submit",
            neighbors = neighbors["key_submit"].orEmpty(),
            onClick = { onKey(PocketKey.Submit) },
        )
    }
}

@Composable
private fun NumericKeys(
    metrics: DesignMetrics,
    palette: PocketKeyboardPalette,
    submitLabel: String?,
    submitEnabled: Boolean,
    scale: Float,
    focusLayer: Int,
    onKey: (PocketKey) -> Unit,
) {
    val keyWidth = 200f
    val keyHeight = 92f * scale
    val gap = 16f * scale
    val startX = (1240f - (3f * keyWidth + 2f * gap)) / 2f
    val topPadding = 24f * scale
    val columnCenter = { column: Int -> startX + column * (keyWidth + gap) + keyWidth / 2f }

    val digitSlots = (0 until 9).chunked(3).map { rowIndices ->
        rowIndices.map { index -> KeySlot("key_${index + 1}", columnCenter(index % 3)) }
    }
    val bottomSlots = buildList {
        add(KeySlot("key_backspace", columnCenter(0)))
        add(KeySlot("key_0", columnCenter(1)))
        if (submitLabel != null) add(KeySlot("key_submit", columnCenter(2)))
    }
    val neighbors = wrapNeighbors(digitSlots + listOf(bottomSlots), LocalKeyboardTopRowUp.current)

    repeat(9) { index ->
        val row = index / 3
        val column = index % 3
        val tag = "key_${index + 1}"
        PocketKeyButton(
            metrics = metrics,
            palette = palette,
            focusLayer = focusLayer,
            x = startX + column * (keyWidth + gap),
            y = topPadding + row * (keyHeight + gap),
            width = keyWidth,
            height = keyHeight,
            label = (index + 1).toString(),
            fontSize = 46f,
            tag = tag,
            neighbors = neighbors[tag].orEmpty(),
            onClick = { onKey(PocketKey.Character((index + 1).toString())) },
        )
    }

    val lastRowY = topPadding + 3f * (keyHeight + gap)
    PocketKeyButton(
        metrics = metrics,
        palette = palette,
        focusLayer = focusLayer,
        x = startX,
        y = lastRowY,
        width = keyWidth,
        height = keyHeight,
        label = "Backspace",
        icon = Icons.AutoMirrored.Filled.Backspace,
        fill = palette.accentFill,
        tag = "key_backspace",
        neighbors = neighbors["key_backspace"].orEmpty(),
        onClick = { onKey(PocketKey.Backspace) },
    )
    PocketKeyButton(
        metrics = metrics,
        palette = palette,
        focusLayer = focusLayer,
        x = startX + keyWidth + gap,
        y = lastRowY,
        width = keyWidth,
        height = keyHeight,
        label = "0",
        fontSize = 46f,
        tag = "key_0",
        neighbors = neighbors["key_0"].orEmpty(),
        onClick = { onKey(PocketKey.Character("0")) },
    )
    if (submitLabel != null) {
        PocketKeyButton(
            metrics = metrics,
            palette = palette,
            focusLayer = focusLayer,
            x = startX + 2f * (keyWidth + gap),
            y = lastRowY,
            width = keyWidth,
            height = keyHeight,
            label = submitLabel,
            fontSize = 36f,
            enabled = submitEnabled,
            fill = palette.submitFill,
            labelColor = palette.submitLabel,
            tag = "key_submit",
            neighbors = neighbors["key_submit"].orEmpty(),
            onClick = { onKey(PocketKey.Submit) },
        )
    }
}

internal data class KeySlot(val tag: String, val centerX: Float)

private fun List<KeySlot>.nearest(centerX: Float): KeySlot =
    minBy { abs(it.centerX - centerX) }

internal fun wrapNeighbors(
    rows: List<List<KeySlot>>,
    topRowUp: ((centerX: Float) -> String?)? = null,
): Map<String, Map<FocusDirection, String>> {
    val populated = rows.filter { it.isNotEmpty() }
    if (populated.isEmpty()) return emptyMap()
    return buildMap {
        populated.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { index, slot ->
                val links = buildMap {
                    put(
                        FocusDirection.Right,
                        if (index == row.lastIndex) {
                            populated[(rowIndex + 1) % populated.size].first().tag
                        } else {
                            row[index + 1].tag
                        },
                    )
                    put(
                        FocusDirection.Left,
                        if (index == 0) {
                            populated[(rowIndex - 1 + populated.size) % populated.size].last().tag
                        } else {
                            row[index - 1].tag
                        },
                    )
                    if (rowIndex == populated.lastIndex) {
                        put(FocusDirection.Down, populated.first().nearest(slot.centerX).tag)
                    }
                    if (rowIndex == 0) {
                        topRowUp?.invoke(slot.centerX)?.let { put(FocusDirection.Up, it) }
                    }
                }
                if (links.isNotEmpty()) put(slot.tag, links)
            }
        }
    }
}

private fun emailShortcutTag(shortcut: String) = "key_email_${shortcut.trimStart('.')}"

@Composable
private fun PocketKeyButton(
    metrics: DesignMetrics,
    palette: PocketKeyboardPalette,
    focusLayer: Int,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    label: String,
    tag: String,
    fontSize: Float = 42f,
    enabled: Boolean = true,
    fill: Brush = palette.keyFill,
    labelColor: Color = palette.label,
    icon: ImageVector? = null,
    iconSize: Float = 52f,
    neighbors: Map<FocusDirection, String> = emptyMap(),
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(KEY_CORNER_RADIUS))
    val interaction = remember(tag) { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .designBounds(metrics, x, y + KEY_SHADOW_DROP, width, height)
            .pocketShadow(metrics, 26f, 0.12f, 4f),
    )
    Box(
        modifier = Modifier
            .designBounds(
                metrics,
                x,
                y + if (pressed && enabled) KEY_PRESS_TRAVEL else 0f,
                width,
                height,
            )
            .clip(shape)
            .pocketFrame(fill, metrics.dp(palette.keyBorderWidth), palette.keyBorder, shape)
            .testTag(tag)
            .controllerTarget(
                tag,
                layer = focusLayer,
                cornerRadius = KEY_CORNER_RADIUS,
                neighbors = neighbors,
            ) {
                if (enabled) onClick()
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (pressed && enabled) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = KEY_PRESS_TINT)),
            )
        }
        val contentColor = if (enabled) labelColor else labelColor.copy(alpha = 0.55f)
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.requiredSize(metrics.dp(iconSize)),
                tint = contentColor,
            )
        } else {
            Text(
                text = label,
                color = contentColor,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(fontSize),
                maxLines = 1,
            )
        }
    }
}

private const val KEY_ROW_GAP = 14f
private const val MODE_KEY_X = 40f
private const val MODE_KEY_WIDTH = 168f
private const val MODE_KEY_ROW = 2
private const val MODE_KEY_ROW_START = MODE_KEY_X + MODE_KEY_WIDTH + KEY_ROW_GAP
private const val KEY_CORNER_RADIUS = 26f

fun firstKeyTag(layout: PocketKeyboardLayout): String = when (layout) {
    PocketKeyboardLayout.Text, PocketKeyboardLayout.Email -> "key_q"
    PocketKeyboardLayout.Numeric -> "key_1"
    PocketKeyboardLayout.Emoji -> "key_emoji_0"
}

internal fun layoutSwapKeyTag(layout: PocketKeyboardLayout): String = when (layout) {
    PocketKeyboardLayout.Text, PocketKeyboardLayout.Email -> "key_symbols"
    PocketKeyboardLayout.Numeric -> "key_1"
    PocketKeyboardLayout.Emoji -> "key_alphabet"
}
private val EMAIL_SHORTCUTS = listOf("@", ".com")
private const val BACKSPACE_X = 712f
private const val BACKSPACE_X_NO_SUBMIT = 1032f

private const val KEY_SHADOW_DROP = 6f
private const val KEY_PRESS_TRAVEL = 4f
private const val KEY_PRESS_TINT = 0.14f

private fun shiftActiveFill() = Brush.verticalGradient(
    listOf(Color(0xFF9FE7C4), Color(0xFF6FD3A2)),
)
