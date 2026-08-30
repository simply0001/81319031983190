package com.pocketpass.app.ui.phone

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.BOTTOM_DESIGN_WIDTH
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.auth.PocketWhitePanel
import com.pocketpass.app.ui.components.AnimatedPatternSurface
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.NAV_TAB_BUTTON_WIDTH
import com.pocketpass.app.ui.components.NavTabButton
import com.pocketpass.app.ui.components.POCKET_SHADOW_ALPHA
import com.pocketpass.app.ui.components.POCKET_SHADOW_BLUR
import com.pocketpass.app.ui.components.navSpecs
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.components.pocketShadow
import com.pocketpass.app.ui.components.roundedShadowMask
import com.pocketpass.app.ui.components.shadowPaint
import com.pocketpass.app.ui.screens.cancelButtonBrush
import com.pocketpass.app.ui.screens.chevronTint
import com.pocketpass.app.ui.screens.greenButtonBrush
import com.pocketpass.app.ui.screens.greyPanelBrush
import com.pocketpass.app.ui.screens.redButtonBrush
import com.pocketpass.app.ui.theme.pocketPalette
import kotlin.math.roundToInt

const val PHONE_BACKDROP_HOLD = 0.4375f
const val PHONE_CONTENT_MARGIN = 50f
const val PHONE_PANEL_BORDER = 20.152f
private const val PHONE_CHROME_CORNER = 130f
private const val PHONE_CHROME_BLEED = 40f
internal val PhoneGreenBorder = Color(0xFF4FC24B)
internal val PhoneRedBorder = Color(0xFFC24B4B)
internal val PhoneGreyBorder = Color(0xFF8A8A8A)

@Composable
internal fun PhoneBackdrop(
    metrics: DesignMetrics,
    topColor: Color,
    bottomColor: Color,
    holdFraction: Float = PHONE_BACKDROP_HOLD,
) {
    val top by animateColorAsState(topColor, tween(280, easing = FastOutSlowInEasing), label = "Backdrop top")
    val bottom by animateColorAsState(bottomColor, tween(280, easing = FastOutSlowInEasing), label = "Backdrop bottom")
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(0f to top, holdFraction to top, 1f to bottom),
                ),
            ),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AnimatedPatternSurface(
            topColor = top,
            bottomColor = bottom,
            holdFraction = holdFraction,
            designWidth = metrics.designWidth,
            designHeight = metrics.designHeight,
            geometryWidth = BOTTOM_DESIGN_WIDTH,
        )
    } else {
        FigmaAsset(
            resource = Assets.PatternHomeBottom,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
internal fun PhoneTabBar(
    metrics: DesignMetrics,
    current: PocketPassDestination,
    onSelect: (PocketPassDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val insets = LocalPhoneInsets.current
    val palette = pocketPalette
    val corner = metrics.dp(PHONE_CHROME_CORNER)
    val shape = RoundedCornerShape(topStart = corner, topEnd = corner)
    val height = PHONE_TAB_BAR_HEIGHT + insets.bottom
    Box(
        modifier
            .fillMaxWidth()
            .height(metrics.dp(height))
            .clipToBounds(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .requiredHeight(metrics.dp(height + PHONE_CHROME_BLEED))
                .offset(y = metrics.dp(PHONE_CHROME_BLEED / 2f))
                .clip(shape)
                .pocketFrame(palette.chrome, metrics.dp(16f), palette.tealBorder, shape),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = metrics.dp(54f + insets.start),
                    end = metrics.dp(54f + insets.end),
                    top = metrics.dp(40f),
                )
                .height(metrics.dp(154f)),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            navSpecs.forEach { spec ->
                NavTabButton(metrics, spec, selected = spec.destination == current) {
                    onSelect(spec.destination)
                }
            }
        }
    }
}

@Composable
internal fun PhoneNavRail(
    metrics: DesignMetrics,
    current: PocketPassDestination,
    onSelect: (PocketPassDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val insets = LocalPhoneInsets.current
    val palette = pocketPalette
    val corner = metrics.dp(PHONE_CHROME_CORNER)
    val shape = RoundedCornerShape(topEnd = corner, bottomEnd = corner)
    val width = PHONE_RAIL_WIDTH + insets.start
    val clear = 40f + maxOf(insets.safeTop, insets.bottom)
    val start = maxOf((width - NAV_TAB_BUTTON_WIDTH) / 2f, insets.start + 16f)
    Box(
        modifier
            .fillMaxHeight()
            .width(metrics.dp(width))
            .clipToBounds(),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .requiredWidth(metrics.dp(width + PHONE_CHROME_BLEED))
                .offset(x = -metrics.dp(PHONE_CHROME_BLEED / 2f))
                .clip(shape)
                .pocketFrame(palette.chrome, metrics.dp(16f), palette.tealBorder, shape),
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(
                    start = metrics.dp(start),
                    top = metrics.dp(clear),
                    bottom = metrics.dp(clear),
                ),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            navSpecs.forEach { spec ->
                NavTabButton(metrics, spec, selected = spec.destination == current) {
                    onSelect(spec.destination)
                }
            }
        }
    }
}

@Composable
internal fun PhoneSectionHeader(
    metrics: DesignMetrics,
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    horizontalPadding: Float = PHONE_CONTENT_MARGIN,
    subtitle: String? = null,
    subtitleColor: Color = color.copy(alpha = 0.72f),
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.dp(horizontalPadding)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = color,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(73.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = subtitleColor,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = metrics.sp(40f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(metrics.dp(22f)),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
internal fun PhoneRoundAction(
    metrics: DesignMetrics,
    borderColor: Color,
    tint: Color,
    tag: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(40f))
    Box(modifier.requiredSize(metrics.dp(80f))) {
        Box(
            Modifier
                .fillMaxSize()
                .offset(y = metrics.dp(6f))
                .pocketShadow(metrics, 40f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .pocketFrame(
                    Brush.verticalGradient(listOf(pocketPalette.surface, tint)),
                    metrics.dp(6f),
                    borderColor,
                    shape,
                )
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
                ),
            contentAlignment = Alignment.Center,
            content = content,
        )
        if (badge > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = metrics.dp(25f), y = metrics.dp(-25f))
                    .requiredSize(metrics.dp(55f))
                    .clip(CircleShape)
                    .pocketFrame(Color(0xFFF44F4F), metrics.dp(5f), Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badge.coerceAtMost(99).toString(),
                    color = Color.White,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Bold,
                    fontSize = metrics.sp(26f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun BellGlyph(metrics: DesignMetrics, color: Color) {
    Canvas(
        Modifier
            .fillMaxSize()
            .padding(metrics.dp(12f)),
    ) {
        val stroke = size.width * 0.16f
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.2f, size.height * 0.18f),
            size = Size(size.width * 0.6f, size.height * 0.62f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(color, Offset(size.width * 0.2f, size.height * 0.51f), Offset(size.width * 0.12f, size.height * 0.73f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.8f, size.height * 0.51f), Offset(size.width * 0.88f, size.height * 0.73f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.12f, size.height * 0.73f), Offset(size.width * 0.88f, size.height * 0.73f), stroke, StrokeCap.Round)
        drawCircle(color, radius = stroke * 0.66f, center = Offset(size.width / 2f, size.height * 0.87f))
    }
}

@Composable
internal fun PlusGlyph(metrics: DesignMetrics, color: Color) {
    Canvas(
        Modifier
            .fillMaxSize()
            .padding(metrics.dp(20f)),
    ) {
        val stroke = size.width * 0.22f
        drawLine(color, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), stroke, StrokeCap.Round)
        drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), stroke, StrokeCap.Round)
    }
}

@Composable
internal fun CloseGlyph(metrics: DesignMetrics, color: Color) {
    Canvas(
        Modifier
            .fillMaxSize()
            .padding(metrics.dp(22f)),
    ) {
        val stroke = size.width * 0.24f
        drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
    }
}

@Composable
internal fun PhonePageHeader(
    metrics: DesignMetrics,
    title: String,
    subtitle: String?,
    backTag: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Float = PHONE_CONTENT_MARGIN,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.dp(horizontalPadding)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(metrics.dp(40f)))
                .testTag(backTag)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                )
                .padding(vertical = metrics.dp(16f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FigmaAsset(
                resource = Assets.SettingsArrow,
                modifier = Modifier
                    .padding(start = metrics.dp(12f), end = metrics.dp(40f))
                    .requiredSize(metrics.dp(40.372f), metrics.dp(68.725f))
                    .graphicsLayer { scaleX = -1f },
                colorFilter = chevronTint(),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = pocketPalette.textPrimary,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Bold,
                    fontSize = metrics.sp(88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
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
            }
        }
        trailing()
    }
}

internal fun Modifier.phoneShadow(
    metrics: DesignMetrics,
    radius: Float,
    offsetY: Float = 15.674f,
    alpha: Float = POCKET_SHADOW_ALPHA,
): Modifier = drawWithCache {
    val mask = roundedShadowMask(size, metrics.dp(radius).toPx(), metrics.dp(POCKET_SHADOW_BLUR).toPx())
    val paint = shadowPaint(alpha)
    val drop = metrics.dp(offsetY).toPx()
    onDrawBehind { mask.draw(drawContext.canvas.nativeCanvas, paint, drop) }
}

@Composable
internal fun Modifier.phonePanel(
    metrics: DesignMetrics,
    radius: Float,
    borderColor: Color = pocketPalette.borderGrey,
    fill: Brush = greyPanelBrush(),
    borderWidth: Float = PHONE_PANEL_BORDER,
    shadowOffset: Float = 15.674f,
    shadowAlpha: Float = pocketPalette.shadowAlpha,
): Modifier {
    val shape = RoundedCornerShape(metrics.dp(radius))
    return this
        .phoneShadow(metrics, radius, shadowOffset, shadowAlpha)
        .clip(shape)
        .pocketFrame(fill, metrics.dp(borderWidth), borderColor, shape)
}

@Composable
internal fun PhoneButton(
    metrics: DesignMetrics,
    label: String,
    modifier: Modifier = Modifier,
    fill: Brush = greenButtonBrush(),
    borderColor: Color = PhoneGreenBorder,
    textColor: Color = Color.White,
    enabled: Boolean = true,
    height: Float = 150f,
    radius: Float = 118f,
    fontSize: Float = 48f,
    tag: String? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(radius))
    Box(
        modifier = modifier
            .height(metrics.dp(height))
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.58f
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .phoneShadow(metrics, radius, 12f, 0.11f)
            .clip(shape)
            .pocketFrame(fill, metrics.dp(PHONE_PANEL_BORDER), borderColor, shape)
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
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
internal fun PhoneTextAction(
    metrics: DesignMetrics,
    label: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: Float = 42f,
    color: Color = pocketPalette.tealSoft,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(metrics.dp(50f)))
            .testTag(tag)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = metrics.dp(40f), vertical = metrics.dp(20f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) color else color.copy(alpha = 0.6f),
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(fontSize),
            maxLines = 1,
        )
    }
}

@Composable
internal fun PhoneScrim(
    visible: Boolean,
    tag: String,
    onDismiss: (() -> Unit)?,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(160)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(pocketPalette.scrim)
                .testTag(tag)
                .clickable(
                    enabled = onDismiss != null,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onDismiss?.invoke() },
                ),
        )
    }
}

@Composable
internal fun PhoneDialog(
    metrics: DesignMetrics,
    visible: Boolean,
    tag: String,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    borderColor: Color = pocketPalette.borderGrey,
    fill: Brush = greyPanelBrush(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val insets = LocalPhoneInsets.current
    val short = phoneShortViewport(metrics)
    PhoneScrim(visible, "${tag}_scrim", onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .padding(
                start = metrics.dp(insets.start),
                end = metrics.dp(insets.end),
                top = metrics.dp(insets.top),
                bottom = metrics.dp(maxOf(insets.bottom, insets.ime)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 6 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 8 },
        ) {
            Column(
                modifier = modifier
                    .padding(horizontal = metrics.dp(80f), vertical = metrics.dp(if (short) 12f else 24f))
                    .widthIn(max = metrics.dp(1080f))
                    .fillMaxWidth()
                    .phonePanel(metrics, radius = 80f, borderColor = borderColor, fill = fill, borderWidth = 15f, shadowOffset = 14f)
                    .testTag(tag)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = metrics.dp(60f), vertical = metrics.dp(if (short) 36f else 52f)),
            ) {
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    content = content,
                )
            }
        }
    }
}

@Composable
internal fun phoneShortViewport(metrics: DesignMetrics): Boolean {
    val insets = LocalPhoneInsets.current
    return insets.ime > 0f && metrics.designHeight - insets.ime - insets.top < 760f
}

@Composable
internal fun PhoneTopFade(metrics: DesignMetrics, color: Color, modifier: Modifier = Modifier) {
    val insets = LocalPhoneInsets.current
    if (insets.top <= 0f) return
    val tint by animateColorAsState(color, tween(280, easing = FastOutSlowInEasing), label = "Top fade")
    Box(
        modifier
            .fillMaxWidth()
            .height(metrics.dp(insets.top + 36f))
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to tint.copy(alpha = 0.94f),
                        insets.top / (insets.top + 36f) to tint.copy(alpha = 0.7f),
                        1f to tint.copy(alpha = 0f),
                    ),
                ),
            ),
    )
}

@Composable
internal fun PhoneConfirmDialog(
    metrics: DesignMetrics,
    visible: Boolean,
    tag: String,
    title: String,
    body: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    error: String? = null,
    cancelLabel: String = "Cancel",
    confirmFill: Brush = redButtonBrush(),
    confirmBorder: Color = PhoneRedBorder,
    confirmEnabled: Boolean = true,
) {
    PhoneDialog(metrics, visible, tag, onDismiss = onCancel) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(70f),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(Modifier.height(metrics.dp(28f)))
        Text(
            text = body,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.dp(30f)),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            textAlign = TextAlign.Center,
        )
        if (error != null) {
            Spacer(Modifier.height(metrics.dp(20f)))
            Text(
                text = error,
                modifier = Modifier.fillMaxWidth(),
                color = pocketPalette.ink(Color(0xFFB31E3A)),
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(30f),
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(metrics.dp(52f)))
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(20f))) {
            PhoneButton(
                metrics = metrics,
                label = cancelLabel,
                modifier = Modifier.weight(1f),
                fill = cancelButtonBrush(),
                borderColor = PhoneGreyBorder,
                fontSize = 44f,
                tag = "${tag}_cancel",
                onClick = onCancel,
            )
            PhoneButton(
                metrics = metrics,
                label = confirmLabel,
                modifier = Modifier.weight(1f),
                fill = confirmFill,
                borderColor = confirmBorder,
                enabled = confirmEnabled,
                fontSize = 44f,
                tag = "${tag}_confirm",
                onClick = onConfirm,
            )
        }
    }
}

@Composable
internal fun PhoneTextField(
    metrics: DesignMetrics,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    fontSize: Float = 55f,
    fontWeight: FontWeight = FontWeight.Medium,
    textColor: Color = pocketPalette.teal,
    placeholderColor: Color = pocketPalette.tealSoft.copy(alpha = 0.56f),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    minHeight: Float = 166f,
    radius: Float = 118f,
    borderColor: Color = pocketPalette.tealBorder,
    borderWidth: Float = 18f,
    fill: Brush = PocketWhitePanel,
    textAlign: TextAlign = TextAlign.Start,
    horizontalPadding: Float = 52f,
    verticalPadding: Float = 34f,
    tag: String? = null,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(metrics.dp(radius))
    val style = TextStyle(
        fontFamily = Rubik,
        fontWeight = fontWeight,
        fontSize = metrics.sp(fontSize),
        lineHeight = metrics.sp(fontSize * 1.25f),
        color = textColor,
        textAlign = textAlign,
    )
    val field = remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.value.text != value) field.value = TextFieldValue(value, TextRange(value.length))
    BasicTextField(
        value = field.value,
        onValueChange = {
            field.value = it
            if (it.text != value) onValueChange(it.text)
        },
        modifier = modifier
            .defaultMinSize(minHeight = metrics.dp(minHeight))
            .phoneShadow(metrics, radius, 12f, 0.12f)
            .clip(shape)
            .pocketFrame(fill, metrics.dp(borderWidth), borderColor, shape)
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .then(if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester)),
        enabled = enabled,
        textStyle = style,
        cursorBrush = SolidColor(textColor),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = metrics.dp(minHeight))
                    .padding(horizontal = metrics.dp(horizontalPadding), vertical = metrics.dp(verticalPadding)),
                contentAlignment = when (textAlign) {
                    TextAlign.Center -> Alignment.Center
                    else -> Alignment.CenterStart
                },
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        modifier = Modifier.fillMaxWidth(),
                        style = style.copy(color = placeholderColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            }
        },
    )
}
