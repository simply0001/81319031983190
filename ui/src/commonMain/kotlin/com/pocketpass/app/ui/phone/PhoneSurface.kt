package com.pocketpass.app.ui.phone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import com.pocketpass.app.ui.stableStatusBarTop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.LocalDesignMetrics
import kotlin.math.ceil
import kotlin.math.min

const val PHONE_DESIGN_SHORT_SIDE = 1240f
const val PHONE_DECK_WIDTH = 1240f
const val PHONE_RAIL_WIDTH = 307.2f
const val PHONE_TAB_BAR_HEIGHT = 234f
const val PHONE_STAGE_MAX_WIDTH = 1600f
const val PHONE_PANE_GAP = 72f
private const val PHONE_MIN_UNIT_DP = 0.26f
private const val PHONE_MAX_UNIT_DP = 0.36f
private const val PHONE_WIDE_MIN_WIDTH = PHONE_RAIL_WIDTH + PHONE_DECK_WIDTH + 880f

@Immutable
class PhoneInsets(
    val top: Float = 0f,
    val bottom: Float = 0f,
    val start: Float = 0f,
    val end: Float = 0f,
    val ime: Float = 0f,
    val safeTop: Float = 0f,
)

val LocalPhoneInsets = staticCompositionLocalOf { PhoneInsets() }

enum class PhoneLayout { Compact, Wide }

fun phoneScale(viewportWidthPx: Float, viewportHeightPx: Float, density: Float): Float {
    val shortSideDp = min(viewportWidthPx, viewportHeightPx) / density
    val unitDp = (shortSideDp / PHONE_DESIGN_SHORT_SIDE).coerceIn(PHONE_MIN_UNIT_DP, PHONE_MAX_UNIT_DP)
    return unitDp * density
}

fun phoneLayout(designWidth: Float, designHeight: Float): PhoneLayout =
    if (designWidth > designHeight && designWidth >= PHONE_WIDE_MIN_WIDTH) PhoneLayout.Wide else PhoneLayout.Compact

@Immutable
class WidePanes(val margin: Float, val rail: Float, val stage: Float, val gap: Float, val deck: Float)

fun widePanes(designWidth: Float, startInset: Float, endInset: Float): WidePanes {
    val rail = PHONE_RAIL_WIDTH + startInset
    val available = designWidth - rail - endInset - PHONE_PANE_GAP - PHONE_DECK_WIDTH
    val stage = available.coerceAtMost(PHONE_STAGE_MAX_WIDTH)
    return WidePanes(margin = (available - stage) / 2f, rail = rail, stage = stage, gap = PHONE_PANE_GAP, deck = PHONE_DECK_WIDTH)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(DesignMetrics) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()
        val scale = phoneScale(viewportWidth, viewportHeight, density.density)
        val designWidth = viewportWidth / scale
        val designHeight = viewportHeight / scale
        val metrics = remember(density, designWidth, designHeight, scale) {
            DesignMetrics(density, designWidth, designHeight, scale = scale)
        }
        val safe = WindowInsets.safeDrawing
        val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        val ime = WindowInsets.ime
        val insets = PhoneInsets(
            top = maxOf(safe.getTop(density), stableStatusBarTop(density)) / scale,
            bottom = bars.getBottom(density) / scale,
            start = safe.getLeft(density, layoutDirection) / scale,
            end = safe.getRight(density, layoutDirection) / scale,
            ime = ime.getBottom(density) / scale,
            safeTop = safe.getTop(density) / scale,
        )
        Box(
            Modifier
                .requiredSize(metrics.dp(ceil(designWidth)), metrics.dp(ceil(designHeight)))
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin.Center
                },
        ) {
            CompositionLocalProvider(
                LocalDesignMetrics provides metrics,
                LocalPhoneInsets provides insets,
            ) {
                content(metrics)
            }
        }
    }
}
