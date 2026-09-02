package com.pocketpass.app.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pocketpass.app.PocketPassLauncherActivity
import com.pocketpass.app.R
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.theme.DarkPalette
import com.pocketpass.app.ui.theme.LightPalette
import com.pocketpass.app.ui.theme.PocketPalette
import com.pocketpass.app.ui.widget.WidgetAssets
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The two home-screen widgets, built from native RemoteViews through Glance so
 * the launcher lays them out at their real size: text stays crisp and follows
 * the system font size, and nothing is stretched between size buckets. The
 * design language survives as shape drawables (the Home backdrop gradient with
 * the pocket-frame stroke, the pill capsule, the status dots) and the palette
 * colours; only the figma icons and the framed avatar are rasterised, each at
 * the exact pixel size it is shown at.
 */
abstract class PocketPassWidget : GlanceAppWidget() {
    /** Composed for the cell's actual size, so the content grows into it instead of leaving margins. */
    override val sizeMode: SizeMode = SizeMode.Exact

    class Scene(
        val snapshot: WidgetSnapshot?,
        val palette: PocketPalette,
        val dark: Boolean,
        val nowEpochMillis: Long,
    )

    /** Rasterises whatever [Content] needs for one cell size, off the UI thread. */
    protected abstract suspend fun prepare(context: Context, scene: Scene, size: DpSize): Bitmap?

    @Composable
    protected abstract fun Content(scene: Scene, size: DpSize, prepared: Bitmap?)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore.read(context)
        val dark = resolveDark(context, snapshot?.themeMode)
        val scene = Scene(
            snapshot = snapshot,
            palette = if (dark) DarkPalette else LightPalette,
            dark = dark,
            nowEpochMillis = System.currentTimeMillis(),
        )
        val signedIn = snapshot != null && snapshot.signedIn
        val sizes = runCatching { GlanceAppWidgetManager(context).getAppWidgetSizes(id) }
            .getOrDefault(emptyList())
            .ifEmpty { listOf(FALLBACK_SIZE) }
        val prepared = if (signedIn) sizes.associateWith { size -> prepare(context, scene, size) } else emptyMap()
        provideContent {
            val size = LocalSize.current
            Card(scene) {
                if (signedIn) {
                    Content(scene, size, prepared[size] ?: prepared.nearest(size))
                } else {
                    Placeholder(scene)
                }
            }
        }
    }

    private fun Map<DpSize, Bitmap?>.nearest(size: DpSize): Bitmap? =
        entries.minByOrNull { (candidate, _) ->
            abs(candidate.width.value - size.width.value) + abs(candidate.height.value - size.height.value)
        }?.value

    private fun resolveDark(context: Context, themeMode: String?): Boolean = when (themeMode) {
        "Dark" -> true
        "Light" -> false
        else -> {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            night == Configuration.UI_MODE_NIGHT_YES
        }
    }

    private companion object {
        val FALLBACK_SIZE = DpSize(310.dp, 120.dp)
    }
}

@Composable
private fun Card(scene: PocketPassWidget.Scene, content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(
                ImageProvider(if (scene.dark) R.drawable.widget_card_dark else R.drawable.widget_card_light),
                contentScale = ContentScale.FillBounds,
            )
            .clickable(actionStartActivity<PocketPassLauncherActivity>()),
    ) {
        content()
    }
}

@Composable
private fun Placeholder(scene: PocketPassWidget.Scene) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Open PocketPass", style = style(scene.palette.teal, 15.sp, FontWeight.Bold))
    }
}

class StreetPassSummaryWidget : PocketPassWidget() {
    override suspend fun prepare(context: Context, scene: Scene, size: DpSize): Bitmap? =
        WidgetAssets.icon(context, Assets.FriendWave, context.px(iconSize(size)))

    @Composable
    override fun Content(scene: Scene, size: DpSize, prepared: Bitmap?) {
        val snapshot = scene.snapshot ?: return
        val palette = scene.palette
        val wide = size.width >= 260.dp
        val tall = size.height >= 150.dp
        val heroSize = when {
            tall && wide -> 52.sp
            tall -> 44.sp
            wide -> 36.sp
            else -> 32.sp
        }
        Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (prepared != null) {
                    Image(
                        provider = ImageProvider(prepared),
                        contentDescription = null,
                        modifier = GlanceModifier.size(iconSize(size)),
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                }
                Text(
                    text = snapshot.encountersToday.toString(),
                    style = style(palette.teal, heroSize, FontWeight.Bold),
                    maxLines = 1,
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                NearbyPill(scene, snapshot.nearbyStatus)
            }
            Text(
                text = if (snapshot.encountersToday == 1) "encounter today" else "encounters today",
                style = style(palette.textMuted, if (tall) 13.sp else 12.sp, FontWeight.Medium),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            if (tall && !wide) {
                Stat(
                    scene,
                    R.drawable.widget_dot_red,
                    "${snapshot.unreadNotifications} unread",
                    modifier = GlanceModifier.padding(bottom = 6.dp),
                )
                Stat(
                    scene,
                    tealDot(scene),
                    "${snapshot.friendsOnline} online",
                    modifier = GlanceModifier.padding(bottom = 6.dp),
                )
            }
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = lastPassLabel(snapshot.lastEncounterEpochMillis, scene.nowEpochMillis),
                    style = style(palette.tealSoft, if (tall) 13.sp else 12.sp, FontWeight.Bold),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (wide) {
                    Stat(scene, R.drawable.widget_dot_red, "${snapshot.unreadNotifications} unread")
                    Spacer(modifier = GlanceModifier.width(12.dp))
                    Stat(scene, tealDot(scene), "${snapshot.friendsOnline} online")
                }
            }
        }
    }

    private fun iconSize(size: DpSize): Dp = if (size.height >= 150.dp) 36.dp else 28.dp
}

class ProfileCardWidget : PocketPassWidget() {
    override suspend fun prepare(context: Context, scene: Scene, size: DpSize): Bitmap? {
        val portrait = scene.snapshot?.portraitFileName?.let { WidgetSnapshotStore.portraitFile(context) }
        return WidgetAssets.avatar(context, portrait, context.px(avatarSize(size)), scene.dark)
    }

    @Composable
    override fun Content(scene: Scene, size: DpSize, prepared: Bitmap?) {
        val snapshot = scene.snapshot ?: return
        val palette = scene.palette
        val tall = size.height >= 150.dp
        val wide = size.width >= 330.dp
        val roomy = size.width >= 440.dp
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (prepared != null) {
                Image(
                    provider = ImageProvider(prepared),
                    contentDescription = "Avatar",
                    modifier = GlanceModifier.size(avatarSize(size)),
                )
                Spacer(modifier = GlanceModifier.width(16.dp))
            }
            // Glance columns hold at most ten children, so spacing is padding, not spacers.
            Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = snapshot.displayName.ifBlank { "PocketPass" },
                        style = style(palette.teal, if (tall) 24.sp else 20.sp, FontWeight.Bold),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    if (wide) {
                        NearbyPill(scene, snapshot.nearbyStatus, modifier = GlanceModifier.padding(start = 8.dp))
                    }
                }
                if (snapshot.bio.isNotBlank()) {
                    Text(
                        text = snapshot.bio,
                        style = style(palette.tealSoft, if (tall) 14.sp else 13.sp, FontWeight.Medium),
                        maxLines = if (tall) 3 else 2,
                        modifier = GlanceModifier.padding(top = 3.dp),
                    )
                }
                Spacer(modifier = GlanceModifier.defaultWeight())
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Stat(scene, tealDot(scene), encountersLabel(snapshot.encountersToday))
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Stat(
                        scene,
                        tealDot(scene),
                        lastPassLabel(snapshot.lastEncounterEpochMillis, scene.nowEpochMillis),
                    )
                    if (roomy) {
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Stat(scene, R.drawable.widget_dot_red, "${snapshot.unreadNotifications} unread")
                    }
                }
                if (!wide) {
                    NearbyPill(scene, snapshot.nearbyStatus, modifier = GlanceModifier.padding(top = 8.dp))
                }
            }
        }
    }

    private fun avatarSize(size: DpSize): Dp = minOf(size.height - 28.dp, 120.dp)

    private fun encountersLabel(count: Int): String = when (count) {
        0 -> "No encounters today"
        1 -> "1 encounter today"
        else -> "$count encounters today"
    }
}

/** The nearby status capsule; names are [com.pocketpass.app.nearby.NearbyRuntimeStatus] entries. */
@Composable
private fun NearbyPill(
    scene: PocketPassWidget.Scene,
    status: String,
    modifier: GlanceModifier = GlanceModifier,
) {
    val (label, dot) = when (status) {
        "Running" -> "Nearby on" to R.drawable.widget_dot_green
        "Starting" -> "Nearby starting" to R.drawable.widget_dot_green
        "BluetoothOff" -> "Bluetooth off" to greyDot(scene)
        "NeedsPermissions", "NeedsOnboarding" -> "Nearby setup" to greyDot(scene)
        "Unsupported" -> "No Bluetooth LE" to greyDot(scene)
        "Error" -> "Nearby error" to R.drawable.widget_dot_red
        else -> "Nearby off" to greyDot(scene)
    }
    Row(
        modifier = modifier
            .background(
                ImageProvider(if (scene.dark) R.drawable.widget_pill_dark else R.drawable.widget_pill_light),
                contentScale = ContentScale.FillBounds,
            )
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(provider = ImageProvider(dot), contentDescription = null, modifier = GlanceModifier.size(7.dp))
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(label, style = style(scene.palette.textPrimary, 11.sp, FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun Stat(
    scene: PocketPassWidget.Scene,
    dot: Int,
    text: String,
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(provider = ImageProvider(dot), contentDescription = null, modifier = GlanceModifier.size(7.dp))
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(text, style = style(scene.palette.textPrimary, 12.sp, FontWeight.Bold), maxLines = 1)
    }
}

private fun tealDot(scene: PocketPassWidget.Scene): Int =
    if (scene.dark) R.drawable.widget_dot_teal_dark else R.drawable.widget_dot_teal_light

private fun greyDot(scene: PocketPassWidget.Scene): Int =
    if (scene.dark) R.drawable.widget_dot_grey_dark else R.drawable.widget_dot_grey_light

private fun style(color: Color, size: TextUnit, weight: FontWeight): TextStyle =
    TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = weight)

private fun Context.px(dp: Dp): Int =
    (dp.value * resources.displayMetrics.density).roundToInt().coerceAtLeast(1)

internal fun lastPassLabel(lastEpochMillis: Long?, nowEpochMillis: Long): String {
    if (lastEpochMillis == null) return "No passes yet"
    val elapsed = (nowEpochMillis - lastEpochMillis).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    val hours = elapsed / 3_600_000L
    val days = elapsed / 86_400_000L
    return when {
        minutes < 1 -> "Last pass just now"
        minutes < 60 -> "Last pass ${minutes}m ago"
        hours < 24 -> "Last pass ${hours}h ago"
        days == 1L -> "Last pass yesterday"
        else -> "Last pass $days days ago"
    }
}

class StreetPassSummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreetPassSummaryWidget()
}

class ProfileCardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProfileCardWidget()
}
