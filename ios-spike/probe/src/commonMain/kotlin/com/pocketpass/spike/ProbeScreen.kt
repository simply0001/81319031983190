package com.pocketpass.spike

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.pocketpass.spike.resources.Res
import com.pocketpass.spike.resources.achievement_day_one
import com.pocketpass.spike.resources.leaderboard_trophy
import com.pocketpass.spike.resources.nav_activities
import com.pocketpass.spike.resources.nav_friends
import com.pocketpass.spike.resources.nav_home
import com.pocketpass.spike.resources.nav_messages
import com.pocketpass.spike.resources.nav_settings
import com.pocketpass.spike.resources.settings_arrow
import com.pocketpass.spike.resources.settings_gear_face
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val NavIcons: List<Pair<String, DrawableResource>>
    get() = listOf(
        "nav_home" to Res.drawable.nav_home,
        "nav_friends" to Res.drawable.nav_friends,
        "nav_messages" to Res.drawable.nav_messages,
        "nav_activities" to Res.drawable.nav_activities,
        "nav_settings" to Res.drawable.nav_settings,
    )

private val DeckIcons: List<Pair<String, DrawableResource>>
    get() = listOf(
        "settings_gear_face (paths + gradients)" to Res.drawable.settings_gear_face,
        "leaderboard_trophy (linearGradient)" to Res.drawable.leaderboard_trophy,
        "achievement_day_one (multi-layer)" to Res.drawable.achievement_day_one,
        "settings_arrow (flat fill)" to Res.drawable.settings_arrow,
    )

private val SurfaceTop = Color(0xFFB9E9F2)
private val SurfaceBottom = Color(0xFF7075F4)

@Composable
fun ProbeApp(
    rendererSlot: (@Composable (Modifier) -> Unit)? = null,
    rendererLog: List<String> = emptyList(),
) {
    PhoneSurface { metrics ->
        val insets = LocalPhoneInsets.current
        Box(Modifier.fillMaxSize().background(SurfaceTop)) {
            PatternSurface(
                topColor = SurfaceTop,
                bottomColor = SurfaceBottom,
                holdFraction = 0.35f,
                designWidth = metrics.designWidth,
                designHeight = metrics.designHeight,
                modifier = Modifier.fillMaxSize(),
                geometryWidth = metrics.designWidth,
            )
            ProbeDeck(metrics, insets, rendererSlot, rendererLog)
        }
    }
}

@Composable
private fun ProbeDeck(
    metrics: DesignMetrics,
    insets: PhoneInsets,
    rendererSlot: (@Composable (Modifier) -> Unit)?,
    rendererLog: List<String>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = metrics.dp(64),
            end = metrics.dp(64),
            top = metrics.dp(64 + insets.top),
            bottom = metrics.dp(64 + insets.bottom),
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.dp(32)),
    ) {
        item { ProbeHeader(metrics) }
        item { MetricsCard(metrics, insets) }
        item { NavIconStrip(metrics) }
        if (rendererSlot != null) {
            item { RendererCard(metrics, rendererSlot, rendererLog) }
        }
        items(DeckIcons) { (label, resource) -> IconCard(metrics, label, resource) }
        items(List(12) { it }) { index -> FillerCard(metrics, index) }
    }
}

@Composable
private fun ProbeHeader(metrics: DesignMetrics) {
    Column {
        DesignText(
            text = "PocketPass",
            metrics = metrics,
            sizePx = 96,
            weight = FontWeight.Bold,
            color = Color(0xFF1B1B2F),
        )
        DesignText(
            text = "Compose Multiplatform probe",
            metrics = metrics,
            sizePx = 44,
            weight = FontWeight.Medium,
            color = Color(0xFF3A3A52),
        )
    }
}

@Composable
private fun MetricsCard(metrics: DesignMetrics, insets: PhoneInsets) {
    Card(metrics) {
        DesignText("PhoneSurface", metrics, 44, FontWeight.Bold, Color(0xFF1B1B2F))
        val lines = listOf(
            "design  ${metrics.designWidth.fmt()} x ${metrics.designHeight.fmt()} units",
            "scale   ${metrics.scale.fmt(4)} px/unit",
            "layout  ${phoneLayout(metrics.designWidth, metrics.designHeight)}",
            "inset t ${insets.top.fmt()}  b ${insets.bottom.fmt()}",
            "inset s ${insets.start.fmt()}  e ${insets.end.fmt()}",
            "safeTop ${insets.safeTop.fmt()}   ime ${insets.ime.fmt()}",
        )
        lines.forEach { line ->
            DesignText(line, metrics, 34, FontWeight.Normal, Color(0xFF3A3A52))
        }
    }
}

@Composable
private fun RendererCard(
    metrics: DesignMetrics,
    rendererSlot: @Composable (Modifier) -> Unit,
    rendererLog: List<String>,
) {
    Card(metrics) {
        DesignText("Mii renderer (WKWebView)", metrics, 44, FontWeight.Bold, Color(0xFF1B1B2F))
        rendererSlot(
            Modifier
                .fillMaxWidth()
                .height(metrics.dp(900)),
        )
        if (rendererLog.isEmpty()) {
            DesignText("waiting for the renderer…", metrics, 30, FontWeight.Normal, Color(0xFF6A6A85))
        } else {
            rendererLog.takeLast(6).forEach { line ->
                DesignText(line.take(160), metrics, 26, FontWeight.Normal, Color(0xFF3A3A52))
            }
        }
    }
}

@Composable
private fun NavIconStrip(metrics: DesignMetrics) {
    Card(metrics) {
        DesignText("SVG resources", metrics, 44, FontWeight.Bold, Color(0xFF1B1B2F))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = metrics.dp(16)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavIcons.forEach { (name, resource) ->
                Image(
                    painter = painterResource(resource),
                    contentDescription = name,
                    modifier = Modifier.size(metrics.dp(110)),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun IconCard(metrics: DesignMetrics, label: String, resource: DrawableResource) {
    Card(metrics) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(resource),
                contentDescription = label,
                modifier = Modifier.size(metrics.dp(120)),
                contentScale = ContentScale.Fit,
            )
            Box(Modifier.padding(start = metrics.dp(28))) {
                DesignText(label, metrics, 34, FontWeight.Medium, Color(0xFF3A3A52))
            }
        }
    }
}

@Composable
private fun FillerCard(metrics: DesignMetrics, index: Int) {
    Card(metrics) {
        DesignText(
            "Scrolling deck row ${index + 1}",
            metrics,
            38,
            FontWeight.Medium,
            Color(0xFF3A3A52),
        )
    }
}

@Composable
private fun Card(metrics: DesignMetrics, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.dp(40)))
            .background(Color.White.copy(alpha = 0.92f))
            .padding(metrics.dp(32)),
        verticalArrangement = Arrangement.spacedBy(metrics.dp(8)),
    ) {
        content()
    }
}

@Composable
private fun DesignText(
    text: String,
    metrics: DesignMetrics,
    sizePx: Int,
    weight: FontWeight,
    color: Color,
) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = Rubik,
            fontWeight = weight,
            fontSize = metrics.sp(sizePx),
            lineHeight = metrics.sp(sizePx * 1.3f),
            color = color,
            textAlign = TextAlign.Start,
        ),
    )
}

private fun Float.fmt(decimals: Int = 1): String {
    var factor = 1f
    repeat(decimals) { factor *= 10f }
    val rounded = kotlin.math.round(this * factor) / factor
    return rounded.toString()
}
