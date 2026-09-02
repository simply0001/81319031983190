package com.pocketpass.app.ui.components

import com.pocketpass.app.ui.platformAnimationsEnabled
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

val LocalRouteRevealGeneration = compositionLocalOf { 0 }

enum class EntranceMotion {
    None,
    HeroRise,
    ArcadeDrop,
    ActivityCoinSettle,
    ActivityPuzzleSettle,
    ActivityCountRise,
    ActivityButtonRise,
    MessagePop,
    FriendSweep,
    SettingsTurn,
    TextRise,
    PanelRise,
    PanelFromLeft,
    PanelFromRight,
    OverlayPop,
}

enum class IdleMotion {
    None,
    MessageFloat,
    CoinRock,
    PuzzleBob,
    FriendPulse,
}

private data class EntranceValues(
    val scale: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f,
    val rotation: Float = 0f,
    val alpha: Float = 1f,
    val dampingRatio: Float = 0.75f,
    val stiffness: Float = 420f,
)

private fun EntranceMotion.values(): EntranceValues = when (this) {
    EntranceMotion.None -> EntranceValues()
    EntranceMotion.HeroRise ->
        EntranceValues(scale = 0.96f, y = 24f, dampingRatio = 0.78f, stiffness = 430f)
    EntranceMotion.ArcadeDrop ->
        EntranceValues(
            scale = 0.90f,
            y = -22f,
            rotation = -1.5f,
            dampingRatio = 0.64f,
            stiffness = 390f,
        )
    EntranceMotion.ActivityCoinSettle ->
        EntranceValues(
            scale = 0.965f,
            x = -12f,
            y = 12f,
            rotation = -2.8f,
            dampingRatio = 0.82f,
            stiffness = 360f,
        )
    EntranceMotion.ActivityPuzzleSettle ->
        EntranceValues(
            scale = 0.965f,
            x = 12f,
            y = 12f,
            rotation = 2.2f,
            dampingRatio = 0.82f,
            stiffness = 360f,
        )
    EntranceMotion.ActivityCountRise ->
        EntranceValues(
            scale = 0.99f,
            y = 10f,
            dampingRatio = 0.86f,
            stiffness = 520f,
        )
    EntranceMotion.ActivityButtonRise ->
        EntranceValues(
            scale = 0.985f,
            y = 18f,
            dampingRatio = 0.88f,
            stiffness = 460f,
        )
    EntranceMotion.MessagePop ->
        EntranceValues(scale = 0.88f, y = 10f, dampingRatio = 0.66f, stiffness = 440f)
    EntranceMotion.FriendSweep ->
        EntranceValues(
            scale = 0.95f,
            x = -34f,
            rotation = -1.2f,
            dampingRatio = 0.74f,
            stiffness = 410f,
        )
    EntranceMotion.SettingsTurn ->
        EntranceValues(
            scale = 0.95f,
            rotation = -8f,
            dampingRatio = 0.76f,
            stiffness = 400f,
        )
    EntranceMotion.TextRise ->
        EntranceValues(scale = 0.985f, y = 18f, dampingRatio = 0.84f, stiffness = 500f)
    EntranceMotion.PanelRise ->
        EntranceValues(scale = 0.98f, y = 28f, dampingRatio = 0.82f, stiffness = 470f)
    EntranceMotion.PanelFromLeft ->
        EntranceValues(scale = 0.985f, x = -30f, dampingRatio = 0.82f, stiffness = 470f)
    EntranceMotion.PanelFromRight ->
        EntranceValues(scale = 0.985f, x = 30f, dampingRatio = 0.82f, stiffness = 470f)
    EntranceMotion.OverlayPop ->
        EntranceValues(
            scale = 0.92f,
            y = 22f,
            alpha = 0f,
            dampingRatio = 0.70f,
            stiffness = 430f,
        )
}

private data class IdleValues(
    val y: Float = 0f,
    val rotation: Float = 0f,
    val scale: Float = 0f,
    val durationMillis: Int = 2_000,
)

private fun IdleMotion.values(): IdleValues = when (this) {
    IdleMotion.None -> IdleValues()
    IdleMotion.MessageFloat ->
        IdleValues(y = 7f, rotation = 1.1f, scale = 0.006f, durationMillis = 2_400)
    IdleMotion.CoinRock ->
        IdleValues(y = 1.5f, rotation = 0.9f, scale = 0.002f, durationMillis = 3_200)
    IdleMotion.PuzzleBob ->
        IdleValues(y = 3.5f, rotation = -0.55f, scale = 0.003f, durationMillis = 3_600)
    IdleMotion.FriendPulse ->
        IdleValues(y = 4f, rotation = 0.7f, scale = 0.012f, durationMillis = 2_150)
}

@Composable
fun rememberContinuousRotation(durationMillis: Int, label: String): State<Float> {
    if (!platformAnimationsEnabled()) {
        return remember { mutableFloatStateOf(0f) }
    }

    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = durationMillis,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "$label rotation",
    )
}

@Composable
fun rememberGearRotation(): State<Float> =
    rememberContinuousRotation(durationMillis = 30_000, label = "Settings gear")

@Composable
fun rememberActivitiesSwapProgress(
    showSecondVariant: Boolean,
): Animatable<Float, AnimationVector1D> {
    val target = if (showSecondVariant) 1f else 0f
    val progress = remember { Animatable(target) }

    LaunchedEffect(target) {
        if (!platformAnimationsEnabled()) {
            progress.snapTo(target)
            return@LaunchedEffect
        }

        if (target == progress.value) return@LaunchedEffect

        progress.animateTo(
            targetValue = target,
            animationSpec = spring(
                dampingRatio = ACTIVITIES_SPRING_DAMPING_RATIO,
                stiffness = ACTIVITIES_SPRING_STIFFNESS,
                visibilityThreshold = ACTIVITIES_SPRING_VISIBILITY_THRESHOLD,
            ),
        )
    }

    return progress
}

@Composable
fun MotionLayer(
    modifier: Modifier = Modifier,
    entrance: EntranceMotion = EntranceMotion.None,
    idle: IdleMotion = IdleMotion.None,
    idleActive: Boolean = true,
    delayMillis: Int = 0,
    idlePhaseMillis: Int = 0,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
    replayKey: Any? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    if (
        !platformAnimationsEnabled() ||
        (entrance == EntranceMotion.None && idle == IdleMotion.None)
    ) {
        Box(modifier = modifier, content = content)
        return
    }

    val entranceValues = entrance.values()
    val progress = remember(entrance, replayKey) {
        Animatable(if (entrance == EntranceMotion.None) 1f else 0f)
    }
    LaunchedEffect(entrance, delayMillis, replayKey) {
        if (entrance == EntranceMotion.None) return@LaunchedEffect
        if (delayMillis > 0) delay(delayMillis.toLong())
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = entranceValues.dampingRatio,
                stiffness = entranceValues.stiffness,
            ),
        )
    }

    val idleValues = idle.values()
    val idleAmount: State<Float> = if (idle == IdleMotion.None) {
        remember { mutableFloatStateOf(0f) }
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "PocketPass idle")
        infiniteTransition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = idleValues.durationMillis,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(
                    offsetMillis = idlePhaseMillis,
                    offsetType = StartOffsetType.FastForward,
                ),
            ),
            label = idle.name,
        )
    }

    // The idle wave starts at -1, so switching it on would land as a small
    // jump; fading its amplitude in and out keeps the settle seamless.
    val idleGain = animateFloatAsState(
        targetValue = if (idleActive && idle != IdleMotion.None) 1f else 0f,
        animationSpec = tween(durationMillis = IDLE_GAIN_MILLIS, easing = FastOutSlowInEasing),
        label = "PocketPass idle gain",
    )

    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.value
            val wave = idleAmount.value * idleGain.value
            val entranceScale = entranceValues.scale + (1f - entranceValues.scale) * p
            val idleScale = 1f + idleValues.scale * wave
            scaleX = entranceScale * idleScale
            scaleY = entranceScale * idleScale
            translationX = entranceValues.x * (1f - p)
            translationY = entranceValues.y * (1f - p) + idleValues.y * wave
            rotationZ = entranceValues.rotation * (1f - p) +
                idleValues.rotation * wave
            alpha = entranceValues.alpha + (1f - entranceValues.alpha) * p
            compositingStrategy = CompositingStrategy.ModulateAlpha
            this.transformOrigin = transformOrigin
        },
        content = content,
    )
}

private const val ACTIVITIES_SPRING_DAMPING_RATIO = 0.86f
private const val ACTIVITIES_SPRING_STIFFNESS = 180f
private const val ACTIVITIES_SPRING_VISIBILITY_THRESHOLD = 0.0002f
private const val IDLE_GAIN_MILLIS = 450
