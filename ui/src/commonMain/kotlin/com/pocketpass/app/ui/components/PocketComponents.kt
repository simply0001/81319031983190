package com.pocketpass.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.StatusInfo
import com.pocketpass.app.status.StatusFormatter
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.PocketAsset
import com.pocketpass.app.ui.supportsAnimatedPatterns
import com.pocketpass.ui.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import com.pocketpass.app.ui.DesignAnchor
import com.pocketpass.app.ui.DesignBackdrop
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.LocalDesignOrigin
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.anchorOrigin
import com.pocketpass.app.ui.anchoredBounds
import com.pocketpass.app.ui.controller.ControllerFocusViewport
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.theme.pocketPalette

const val POCKET_SHADOW_ALPHA = 0.32f
const val POCKET_SHADOW_BLUR = 10f

fun Modifier.pocketShadow(
    metrics: DesignMetrics,
    cornerRadius: Float,
    alpha: Float = POCKET_SHADOW_ALPHA,
    blurRadius: Float = POCKET_SHADOW_BLUR,
): Modifier = this.drawWithCache {
    val mask = roundedShadowMask(
        size,
        metrics.dp(cornerRadius).toPx(),
        metrics.dp(blurRadius).toPx(),
    )
    onDrawBehind { drawRoundedShadow(mask, alpha) }
}

internal const val SHADOW_MASK_SCALE = 0.25f
internal const val SHADOW_MASK_PADDING = 2.5f

fun Modifier.pocketBorder(width: Dp, color: Color, shape: Shape): Modifier =
    pocketBorder(width, SolidColor(color), shape)

fun Modifier.pocketBorder(border: BorderStroke, shape: Shape): Modifier =
    pocketBorder(border.width, border.brush, shape)

fun Modifier.pocketBorder(width: Dp, brush: Brush, shape: Shape): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(width.toPx() * 2f)
    onDrawWithContent {
        drawContent()
        drawOutline(outline, brush, style = stroke)
    }
}

fun Modifier.pocketFrame(fill: Color, width: Dp, color: Color, shape: Shape): Modifier =
    pocketFrame(SolidColor(fill), width, SolidColor(color), shape)

fun Modifier.pocketFrame(fill: Brush, width: Dp, color: Color, shape: Shape): Modifier =
    pocketFrame(fill, width, SolidColor(color), shape)

fun Modifier.pocketFrame(fill: Color, width: Dp, brush: Brush, shape: Shape): Modifier =
    pocketFrame(SolidColor(fill), width, brush, shape)

fun Modifier.pocketFrame(fill: Brush, border: BorderStroke, shape: Shape): Modifier =
    pocketFrame(fill, border.width, border.brush, shape)

fun Modifier.pocketFrame(fill: Brush, width: Dp, brush: Brush, shape: Shape): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val strokeWidth = width.toPx()
    val inner = outline.inset(strokeWidth / 2f)
    val stroke = Stroke(strokeWidth * 2f)
    onDrawWithContent {
        if (inner != null) drawOutline(inner, fill)
        drawContent()
        drawOutline(outline, brush, style = stroke)
    }
}

private fun Outline.inset(amount: Float): Outline? = when (this) {
    is Outline.Rectangle -> rect.deflate(amount).takeUnless { it.isEmpty }?.let(Outline::Rectangle)
    is Outline.Rounded -> {
        val bounds = Rect(roundRect.left, roundRect.top, roundRect.right, roundRect.bottom).deflate(amount)
        if (bounds.isEmpty) {
            null
        } else {
            Outline.Rounded(
                RoundRect(
                    rect = bounds,
                    topLeft = roundRect.topLeftCornerRadius.shrink(amount),
                    topRight = roundRect.topRightCornerRadius.shrink(amount),
                    bottomRight = roundRect.bottomRightCornerRadius.shrink(amount),
                    bottomLeft = roundRect.bottomLeftCornerRadius.shrink(amount),
                ),
            )
        }
    }
    is Outline.Generic -> this
}

private fun CornerRadius.shrink(amount: Float): CornerRadius =
    CornerRadius((x - amount).coerceAtLeast(0f), (y - amount).coerceAtLeast(0f))

// Composition is single-threaded, so a plain map is a safe read-once cache for asset bytes.
private val assetByteCache = mutableMapOf<String, ByteArray>()

@OptIn(ExperimentalResourceApi::class)
@Composable
fun rememberPocketAssetBytes(resource: PocketAsset): ByteArray? {
    val bytes by produceState(assetByteCache[resource.path], resource) {
        if (value == null) {
            value = Res.readBytes(resource.path).also { assetByteCache[resource.path] = it }
        }
    }
    return bytes
}

@Composable
fun FigmaAsset(
    resource: PocketAsset,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillBounds,
    alpha: Float = 1f,
    description: String? = null,
    colorFilter: ColorFilter? = null,
) {
    AsyncImage(
        model = rememberPocketAssetBytes(resource),
        contentDescription = description,
        modifier = modifier.alpha(alpha),
        contentScale = contentScale,
        colorFilter = colorFilter,
    )
}

@Composable
fun PatternBackground(
    metrics: DesignMetrics,
    pattern: PocketAsset,
    topColor: Color,
    bottomColor: Color,
    holdFraction: Float,
    designWidth: Float,
    designHeight: Float,
    alpha: () -> Float = { 1f },
) {
    DesignBackdrop(metrics, alpha, key = listOf(pattern, topColor, bottomColor, holdFraction)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to topColor,
                            holdFraction to topColor,
                            1f to bottomColor,
                        ),
                    ),
                ),
        )
        if (supportsAnimatedPatterns()) {
            AnimatedPatternSurface(
                topColor = topColor,
                bottomColor = bottomColor,
                holdFraction = holdFraction,
                designWidth = designWidth + 2f * metrics.overscanX,
                designHeight = designHeight + 2f * metrics.overscanY,
                geometryWidth = designWidth,
            )
        } else {
            FigmaAsset(
                resource = pattern,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
fun FullBleedArtwork(
    metrics: DesignMetrics,
    resource: PocketAsset,
    modifier: Modifier = Modifier,
) {
    if (metrics.hasOverscan) {
        DesignBackdrop(metrics, key = resource) {
            FigmaAsset(
                resource = resource,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(metrics.dp(24f)),
                contentScale = ContentScale.Crop,
            )
        }
    }
    FigmaAsset(
        resource = resource,
        modifier = modifier.designBounds(metrics, 0f, 0f, metrics.designWidth, metrics.designHeight),
    )
}

@Composable
fun StatusPills(
    metrics: DesignMetrics,
    status: StatusInfo,
) {
    StatusPill(
        metrics = metrics,
        x = 50f,
        width = 301f,
        horizontal = DesignAnchor.Start,
    ) {
        Text(
            text = status.time,
            color = pocketPalette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.Medium,
            fontSize = metrics.sp(73.915f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
    StatusPill(
        metrics = metrics,
        x = 1383f,
        width = 487f,
        horizontal = DesignAnchor.End,
    ) {
        StatusConnectivityContent(metrics, status)
    }
}

@Composable
fun StatusConnectivityContent(
    metrics: DesignMetrics,
    status: StatusInfo,
) {
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = metrics.dp(50f))
                .requiredSize(metrics.dp(387f), metrics.dp(132f)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WifiStatusIcon(
                status = status,
                modifier = Modifier.requiredSize(
                    metrics.dp(70.21f),
                    metrics.dp(49.732f),
                ),
            )
            BatteryStatusIcon(
                status = status,
                modifier = Modifier.requiredSize(
                    metrics.dp(94f),
                    metrics.dp(51.273f),
                ),
            )
            Text(
                text = StatusFormatter.battery(status.batteryPercent),
                modifier = Modifier.offset(y = metrics.dp(-4f)),
                color = pocketPalette.teal,
                fontFamily = Rubik,
                fontWeight = FontWeight.Medium,
                fontSize = metrics.sp(73.915f),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun WifiStatusIcon(
    status: StatusInfo,
    modifier: Modifier = Modifier,
) {
    val dotAlpha by animateFloatAsState(
        targetValue = if (status.wifiConnected) 1f else 0.24f,
        animationSpec = tween(durationMillis = 250),
        label = "wifi-dot",
    )
    val middleAlpha by animateFloatAsState(
        targetValue = if (status.wifiConnected && status.wifiSignalLevel >= 1) 1f else 0.24f,
        animationSpec = tween(durationMillis = 250),
        label = "wifi-middle",
    )
    val outerAlpha by animateFloatAsState(
        targetValue = if (status.wifiConnected && status.wifiSignalLevel >= 2) 1f else 0.24f,
        animationSpec = tween(durationMillis = 250),
        label = "wifi-outer",
    )
    val disconnectedAlpha by animateFloatAsState(
        targetValue = if (status.wifiConnected) 0f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "wifi-disconnected",
    )
    val iconColor = pocketPalette.teal

    Canvas(modifier) {
        val scaleX = size.width / 70.2097f
        val scaleY = size.height / 49.7319f

        drawCircle(
            color = iconColor,
            radius = 7.3135f * scaleX,
            center = Offset(35.1049f * scaleX, 42.4184f * scaleY),
            alpha = dotAlpha,
        )

        val middle = Path().apply {
            moveTo(18.5763f * scaleX, 33.2034f * scaleY)
            lineTo(12.433f * scaleX, 26.9137f * scaleY)
            cubicTo(
                15.3096f * scaleX,
                24.0371f * scaleY,
                18.686f * scaleX,
                21.7577f * scaleY,
                22.5622f * scaleX,
                20.0756f * scaleY,
            )
            cubicTo(
                26.4384f * scaleX,
                18.3935f * scaleY,
                30.6193f * scaleX,
                17.5524f * scaleY,
                35.1049f * scaleX,
                17.5524f * scaleY,
            )
            cubicTo(
                39.5905f * scaleX,
                17.5524f * scaleY,
                43.7714f * scaleX,
                18.4057f * scaleY,
                47.6476f * scaleX,
                20.1122f * scaleY,
            )
            cubicTo(
                51.5237f * scaleX,
                21.8187f * scaleY,
                54.9001f * scaleX,
                24.1346f * scaleY,
                57.7768f * scaleX,
                27.06f * scaleY,
            )
            lineTo(51.6334f * scaleX, 33.2034f * scaleY)
            cubicTo(
                49.4881f * scaleX,
                31.0581f * scaleY,
                47.0015f * scaleX,
                29.3759f * scaleY,
                44.1736f * scaleX,
                28.157f * scaleY,
            )
            cubicTo(
                41.3457f * scaleX,
                26.9381f * scaleY,
                38.3228f * scaleX,
                26.3287f * scaleY,
                35.1049f * scaleX,
                26.3287f * scaleY,
            )
            cubicTo(
                31.8869f * scaleX,
                26.3287f * scaleY,
                28.864f * scaleX,
                26.9381f * scaleY,
                26.0361f * scaleX,
                28.157f * scaleY,
            )
            cubicTo(
                23.2082f * scaleX,
                29.3759f * scaleY,
                20.7216f * scaleX,
                31.0581f * scaleY,
                18.5763f * scaleX,
                33.2034f * scaleY,
            )
            close()
        }
        drawPath(middle, iconColor, alpha = middleAlpha)

        val outer = Path().apply {
            moveTo(6.14335f * scaleX, 20.7704f * scaleY)
            lineTo(0f, 14.627f * scaleY)
            cubicTo(
                4.48562f * scaleX,
                10.0439f * scaleY,
                9.72698f * scaleX,
                6.46027f * scaleY,
                15.7241f * scaleX,
                3.87616f * scaleY,
            )
            cubicTo(
                21.7211f * scaleX,
                1.29205f * scaleY,
                28.1814f * scaleX,
                0f,
                35.1049f * scaleX,
                0f,
            )
            cubicTo(
                42.0283f * scaleX,
                0f,
                48.4886f * scaleX,
                1.29205f * scaleY,
                54.4857f * scaleX,
                3.87616f * scaleY,
            )
            cubicTo(
                60.4828f * scaleX,
                6.46027f * scaleY,
                65.7241f * scaleX,
                10.0439f * scaleY,
                70.2097f * scaleX,
                14.627f * scaleY,
            )
            lineTo(64.0664f * scaleX, 20.7704f * scaleY)
            cubicTo(
                60.3121f * scaleX,
                17.0161f * scaleY,
                55.9606f * scaleX,
                14.0785f * scaleY,
                51.0118f * scaleX,
                11.9576f * scaleY,
            )
            cubicTo(
                46.063f * scaleX,
                9.83668f * scaleY,
                40.7607f * scaleX,
                8.77622f * scaleY,
                35.1049f * scaleX,
                8.77622f * scaleY,
            )
            cubicTo(
                29.4491f * scaleX,
                8.77622f * scaleY,
                24.1468f * scaleX,
                9.83668f * scaleY,
                19.198f * scaleX,
                11.9576f * scaleY,
            )
            cubicTo(
                14.2492f * scaleX,
                14.0785f * scaleY,
                9.89762f * scaleX,
                17.0161f * scaleY,
                6.14335f * scaleX,
                20.7704f * scaleY,
            )
            close()
        }
        drawPath(outer, iconColor, alpha = outerAlpha)

        if (disconnectedAlpha > 0f) {
            drawLine(
                color = Color.White,
                start = Offset(8f * scaleX, 5f * scaleY),
                end = Offset(62f * scaleX, 45f * scaleY),
                strokeWidth = 11f * scaleX,
                alpha = disconnectedAlpha,
            )
            drawLine(
                color = iconColor,
                start = Offset(8f * scaleX, 5f * scaleY),
                end = Offset(62f * scaleX, 45f * scaleY),
                strokeWidth = 6f * scaleX,
                alpha = disconnectedAlpha,
            )
        }
    }
}

@Composable
fun BatteryStatusIcon(
    status: StatusInfo,
    modifier: Modifier = Modifier,
) {
    val fillFraction by animateFloatAsState(
        targetValue = StatusFormatter.batteryFillFraction(status.batteryPercent),
        animationSpec = tween(durationMillis = 350),
        label = "battery-fill",
    )
    val iconColor = pocketPalette.teal

    Canvas(modifier) {
        val scaleX = size.width / 94f
        val scaleY = size.height / 51.2727f
        drawRoundRect(
            color = iconColor,
            topLeft = Offset(4.27273f * scaleX, 4.27273f * scaleY),
            size = Size(74.77274f * scaleX, 42.72724f * scaleY),
            cornerRadius = CornerRadius(8.54545f * scaleX, 8.54545f * scaleY),
            style = Stroke(width = 8.54545f * scaleX),
        )

        val terminal = Path().apply {
            moveTo(87.5909f * scaleX, 36.3182f * scaleY)
            lineTo(87.5909f * scaleX, 14.9545f * scaleY)
            lineTo(89.7273f * scaleX, 14.9545f * scaleY)
            cubicTo(
                90.9379f * scaleX,
                14.9545f * scaleY,
                91.9527f * scaleX,
                15.364f * scaleY,
                92.7716f * scaleX,
                16.183f * scaleY,
            )
            cubicTo(
                93.5905f * scaleX,
                17.0019f * scaleY,
                94f * scaleX,
                18.0167f * scaleY,
                94f * scaleX,
                19.2273f * scaleY,
            )
            lineTo(94f * scaleX, 32.0455f * scaleY)
            cubicTo(
                94f * scaleX,
                33.2561f * scaleY,
                93.5905f * scaleX,
                34.2708f * scaleY,
                92.7716f * scaleX,
                35.0898f * scaleY,
            )
            cubicTo(
                91.9527f * scaleX,
                35.9087f * scaleY,
                90.9379f * scaleX,
                36.3182f * scaleY,
                89.7273f * scaleX,
                36.3182f * scaleY,
            )
            close()
        }
        drawPath(terminal, iconColor)

        val fillWidth = 57.6818f * scaleX * fillFraction
        if (fillWidth > 0f) {
            drawRect(
                color = iconColor,
                topLeft = Offset(12.8182f * scaleX, 12.8182f * scaleY),
                size = Size(fillWidth, 25.6363f * scaleY),
            )
        }

        if (status.batteryCharging) {
            val bolt = Path().apply {
                moveTo(46.5f * scaleX, 8.75f * scaleY)
                lineTo(28.5f * scaleX, 28f * scaleY)
                lineTo(39.25f * scaleX, 28f * scaleY)
                lineTo(35.25f * scaleX, 42.5f * scaleY)
                lineTo(54.25f * scaleX, 21.25f * scaleY)
                lineTo(43.25f * scaleX, 21.25f * scaleY)
                close()
            }
            drawPath(
                path = bolt,
                color = iconColor,
                style = Stroke(
                    width = 3.5f * scaleX,
                    join = StrokeJoin.Round,
                ),
            )
            drawPath(bolt, Color.White)
        }
    }
}

@Composable
private fun StatusPill(
    metrics: DesignMetrics,
    x: Float,
    width: Float,
    horizontal: DesignAnchor,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(66f))
    val palette = pocketPalette
    Box(
        Modifier
            .anchoredBounds(metrics, x, 64f, width, 132f, horizontal, DesignAnchor.Start)
            .pocketShadow(metrics, 66f),
    )
    Box(
        Modifier
            .anchoredBounds(metrics, x, 50f, width, 132f, horizontal, DesignAnchor.Start)
            .clip(shape)
            .pocketFrame(
                Brush.verticalGradient(
                    listOf(palette.surface, palette.tint(Color(0xFFBDF8CB))),
                ),
                metrics.dp(15f),
                palette.tealBorder,
                shape,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

data class NavSpec(
    val destination: PocketPassDestination,
    val icon: PocketAsset,
    val color: Color,
)

val navSpecs = listOf(
    NavSpec(PocketPassDestination.Messages, Assets.NavMessages, Color(0xFF52A6FF)),
    NavSpec(PocketPassDestination.Friends, Assets.NavFriends, Color(0xFFFF7BED)),
    NavSpec(PocketPassDestination.Home, Assets.NavHome, Color(0xFF51FF85)),
    NavSpec(PocketPassDestination.Activities, Assets.NavActivities, Color(0xFFF44F4F)),
    NavSpec(PocketPassDestination.Settings, Assets.NavSettings, Color(0xFF919191)),
)

private const val BOTTOM_TAB_BAR_TOP = -20f
private const val BOTTOM_TAB_BAR_HEIGHT = 254f
private const val BOTTOM_TAB_BAR_CORNER = 130f

class BelowTabBarShape(private val metrics: DesignMetrics) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = with(density) {
        val corner = CornerRadius(metrics.dp(BOTTOM_TAB_BAR_CORNER).toPx())
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = metrics.dp(BOTTOM_TAB_BAR_TOP).toPx(),
                    right = size.width,
                    bottom = metrics.dp(BOTTOM_TAB_BAR_TOP + BOTTOM_TAB_BAR_HEIGHT).toPx(),
                    bottomRightCornerRadius = corner,
                    bottomLeftCornerRadius = corner,
                ),
            )
        }
        Outline.Generic(path)
    }
}

@Composable
fun rememberBelowTabBarFocusViewport(metrics: DesignMetrics): ControllerFocusViewport {
    val density = LocalDensity.current
    return remember(metrics, density) {
        ControllerFocusViewport(
            shape = BelowTabBarShape(metrics),
            topInset = with(density) {
                metrics.dp(BOTTOM_TAB_BAR_TOP + BOTTOM_TAB_BAR_HEIGHT).toPx()
            },
        )
    }
}

@Composable
fun BottomTabBar(
    metrics: DesignMetrics,
    current: PocketPassDestination,
    onSelect: (PocketPassDestination) -> Unit,
) {
    val outerShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = metrics.dp(BOTTOM_TAB_BAR_CORNER),
        bottomEnd = metrics.dp(BOTTOM_TAB_BAR_CORNER),
    )
    val palette = pocketPalette
    Box(
        modifier = Modifier
            .anchoredBounds(
                metrics,
                0,
                BOTTOM_TAB_BAR_TOP,
                1240,
                BOTTOM_TAB_BAR_HEIGHT,
                horizontal = DesignAnchor.Stretch,
                vertical = DesignAnchor.Start,
            )
            .clip(outerShape)
            .pocketFrame(palette.chrome, metrics.dp(16f), palette.tealBorder, outerShape),
    )

    Row(
        modifier = Modifier.anchoredBounds(
            metrics,
            54,
            40,
            1132,
            154,
            horizontal = DesignAnchor.Stretch,
            vertical = DesignAnchor.Start,
        ),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        navSpecs.forEach { spec ->
            NavTabButton(metrics, spec, selected = spec.destination == current) { onSelect(spec.destination) }
        }
    }
}

const val NAV_TAB_BUTTON_WIDTH = 199.2f
internal const val NAV_TAB_BUTTON_HEIGHT = 154f

@Composable
fun NavTabButton(
    metrics: DesignMetrics,
    spec: NavSpec,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = pocketPalette
    val interactionSource = remember(spec.destination) { MutableInteractionSource() }
    val buttonShape = RoundedCornerShape(metrics.dp(78f))
    val fill by animateColorAsState(
        targetValue = if (selected) palette.surfaceLower else palette.chrome,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "Tab fill",
    )
    val lift by animateFloatAsState(
        targetValue = if (selected) 0f else 1f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "Tab lift",
    )
    Box(
        modifier = Modifier
            .requiredSize(metrics.dp(NAV_TAB_BUTTON_WIDTH), metrics.dp(NAV_TAB_BUTTON_HEIGHT)),
    ) {
        Box(
            modifier = Modifier
                .offset(y = metrics.dp(8.8f))
                .fillMaxSize()
                .clip(buttonShape)
                .background(Color.Black.copy(alpha = 0.07f * lift)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(buttonShape)
                .pocketFrame(
                    fill,
                    metrics.dp(if (spec.destination == PocketPassDestination.Home) 12.336f else 11f),
                    spec.color,
                    buttonShape,
                )
                .testTag("tab_${spec.destination.name.lowercase()}")
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            FigmaAsset(
                resource = spec.icon,
                modifier = Modifier.requiredSize(
                    width = metrics.dp(
                        when (spec.destination) {
                            PocketPassDestination.Messages -> 87.313f
                            PocketPassDestination.Friends -> 84.893f
                            PocketPassDestination.Home -> 102.335f
                            PocketPassDestination.Activities -> 73.539f
                            PocketPassDestination.Settings -> 76.832f
                        },
                    ),
                    height = metrics.dp(
                        when (spec.destination) {
                            PocketPassDestination.Messages -> 69.85f
                            PocketPassDestination.Friends -> 84.893f
                            PocketPassDestination.Home -> 79.077f
                            PocketPassDestination.Activities -> 86.275f
                            PocketPassDestination.Settings -> 76.45f
                        },
                    ),
                ),
            )
        }
    }
}

@Composable
fun PocketPanel(
    metrics: DesignMetrics,
    x: Number,
    y: Number,
    width: Number,
    height: Number,
    borderColor: Color,
    borderWidth: Number,
    radius: Number,
    fill: Color? = null,
    fillBrush: Brush? = null,
    shadowAlpha: Float? = null,
    shadowOffset: Number = 15.674f,
    tag: String? = null,
    focusLayer: Int = 0,
    onClick: (() -> Unit)? = null,
    onControllerActivate: (() -> Unit)? = null,
    horizontal: DesignAnchor? = null,
    vertical: DesignAnchor = DesignAnchor.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(radius))
    val palette = pocketPalette
    val anchor = horizontal
        ?: if (x.toFloat() <= 50f && x.toFloat() + width.toFloat() >= 1190f) {
            DesignAnchor.Stretch
        } else {
            DesignAnchor.Center
        }
    Box(
        modifier = Modifier
            .anchoredBounds(
                metrics,
                x,
                y.toFloat() + shadowOffset.toFloat(),
                width,
                height,
                anchor,
                vertical,
            )
            .pocketShadow(metrics, radius.toFloat(), shadowAlpha ?: palette.shadowAlpha),
    )
    val base = Modifier
        .anchoredBounds(metrics, x, y, width, height, anchor, vertical)
        .clip(shape)
        .pocketFrame(
            fillBrush ?: SolidColor(fill ?: palette.surface),
            metrics.dp(borderWidth),
            borderColor,
            shape,
        )
        .then(if (tag == null) Modifier else Modifier.testTag(tag))
    val interactionSource = remember { MutableInteractionSource() }
    val activate = onClick ?: onControllerActivate
    Box(
        modifier = base
            .then(
                if (tag == null || activate == null) {
                    Modifier
                } else {
                    Modifier.controllerTarget(tag, focusLayer, radius.toFloat()) { activate() }
                },
            )
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                },
            ),
    ) {
        val scope = this
        CompositionLocalProvider(
            LocalDesignOrigin provides metrics.anchorOrigin(anchor, vertical),
        ) {
            scope.content()
        }
    }
}

@Composable
fun pocketTextStyle(
    metrics: DesignMetrics,
    size: Float,
    color: Color,
    weight: FontWeight = FontWeight.ExtraBold,
): TextStyle = TextStyle(
    fontFamily = Rubik,
    fontWeight = weight,
    fontSize = metrics.sp(size),
    color = color,
)
