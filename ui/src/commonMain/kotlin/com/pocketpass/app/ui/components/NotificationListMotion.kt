package com.pocketpass.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import com.pocketpass.app.domain.model.PocketPassNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val ENTER_DURATION_MS = 480
private const val ENTER_STAGGER_MS = 45L
private const val ENTER_CASCADE_BUDGET_MS = 360L
private const val EXIT_DURATION_MS = 260
private const val EXIT_STAGGER_MS = 55L
private const val EXIT_CASCADE_BUDGET_MS = 420L
private const val SLIDE_FRACTION = 0.6f

class NotificationListMotion(
    private val scope: CoroutineScope,
) {
    private val seen = mutableSetOf<String>()
    private val entrances = HashMap<String, Animatable<Float, AnimationVector1D>>()
    private val pendingEntrances = ArrayList<String>()
    private val exits = mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>()

    var retained: List<PocketPassNotification>? by mutableStateOf(null)
        private set

    val clearing: Boolean
        get() = retained != null

    fun shown(notifications: List<PocketPassNotification>): List<PocketPassNotification> {
        if (!clearing) {
            notifications.forEach { notification ->
                val id = notification.id.value
                if (seen.add(id)) {
                    entrances[id] = Animatable(0f)
                    pendingEntrances += id
                }
            }
        }
        return retained ?: notifications
    }

    fun startPendingEntrances() {
        if (pendingEntrances.isEmpty()) return
        val starting = pendingEntrances.toList()
        pendingEntrances.clear()
        val stagger = minOf(ENTER_STAGGER_MS, ENTER_CASCADE_BUDGET_MS / starting.size)
        starting.forEachIndexed { index, id ->
            val progress = entrances[id] ?: return@forEachIndexed
            scope.launch {
                delay(index * stagger)
                progress.animateTo(1f, tween(ENTER_DURATION_MS, easing = FastOutSlowInEasing))
                entrances.remove(id)
            }
        }
    }

    fun clearAll(notifications: List<PocketPassNotification>, onClear: () -> Unit) {
        if (clearing) return
        val leaving = notifications.filter(PocketPassNotification::canDelete)
        if (leaving.isEmpty()) return
        retained = notifications
        notifications.forEach { seen.add(it.id.value) }
        val stagger = minOf(EXIT_STAGGER_MS, EXIT_CASCADE_BUDGET_MS / leaving.size)
        leaving.forEach { exits[it.id.value] = Animatable(0f) }
        onClear()
        scope.launch {
            leaving.mapIndexed { index, notification ->
                launch {
                    delay(index * stagger)
                    exits[notification.id.value]?.animateTo(
                        1f,
                        tween(EXIT_DURATION_MS, easing = FastOutLinearInEasing),
                    )
                }
            }.joinAll()
            retained = null
            exits.clear()
        }
    }

    fun entrance(id: String): Float = entrances[id]?.value ?: 1f

    fun exit(id: String): Float = exits[id]?.value ?: 0f

    fun presence(id: String): Float = entrance(id) * (1f - exit(id))
}

@Composable
fun rememberNotificationListMotion(
    notifications: List<PocketPassNotification>,
): NotificationListMotion {
    val scope = rememberCoroutineScope()
    val motion = remember { NotificationListMotion(scope) }
    LaunchedEffect(notifications, motion.clearing) { motion.startPendingEntrances() }
    return motion
}

@Composable
fun PinListToNewestNotification(
    listState: LazyListState,
    shown: List<PocketPassNotification>,
) {
    val firstId = shown.firstOrNull()?.id?.value
    val lastFirst = remember { arrayOf(firstId) }
    if (lastFirst[0] != firstId) {
        val inserted = lastFirst[0] != null && shown.getOrNull(1)?.id?.value == lastFirst[0]
        if (
            inserted &&
            listState.firstVisibleItemIndex == 0 &&
            listState.firstVisibleItemScrollOffset == 0
        ) {
            listState.requestScrollToItem(0)
        }
        lastFirst[0] = firstId
    }
}

fun Modifier.notificationMotion(
    motion: NotificationListMotion,
    id: String,
    gap: Dp,
): Modifier =
    this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val gapPx = gap.roundToPx()
            val height = ((placeable.height + gapPx) * motion.presence(id)).roundToInt()
            layout(placeable.width, height) {
                placeable.placeRelative(0, 0)
            }
        }
        .graphicsLayer {
            val entrance = motion.entrance(id)
            val exit = motion.exit(id)
            compositingStrategy = CompositingStrategy.ModulateAlpha
            transformOrigin = TransformOrigin(0.5f, 0f)
            scaleY = entrance * (1f - exit)
            translationX = size.width * SLIDE_FRACTION * ((1f - entrance) + exit)
            alpha = entrance * (1f - exit)
        }
