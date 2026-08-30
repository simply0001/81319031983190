package com.pocketpass.app.ui.components

import android.animation.ValueAnimator
import android.os.Build
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.Display
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView

@Composable
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun AnimatedPatternSurface(
    topColor: Color,
    bottomColor: Color,
    holdFraction: Float,
    designWidth: Float,
    designHeight: Float,
    geometryWidth: Float = designWidth,
) {
    AndroidView(
        factory = { context ->
            AnimatedPatternView(context).apply {
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            view.configure(
                topColor = topColor.toArgb(),
                bottomColor = bottomColor.toArgb(),
                holdFraction = holdFraction,
                designWidth = designWidth,
                designHeight = designHeight,
                geometryWidth = geometryWidth,
            )
        },
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AnimatedPatternView(context: Context) : View(context) {
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private var topColor: Int = android.graphics.Color.TRANSPARENT
    private var bottomColor: Int = android.graphics.Color.TRANSPARENT
    private var holdFraction: Float = 0.5f
    private var designWidth: Float = 1f
    private var designHeight: Float = 1f
    private var geometry: PatternGeometry = TOP_PATTERN_GEOMETRY
    private var phase: Float = 0f
    private var lastDrawRequestMillis: Long = 0L
    private var lastDisplayState: Int = Display.STATE_UNKNOWN
    private var lastWindowVisibility: Int = visibility
    private var hadWindowFocus: Boolean = false
    private var displayListenerRegistered: Boolean = false
    private var renderRecoveryPosted: Boolean = false

    private val patternShader = RuntimeShader(PATTERN_SHADER)
    private val shaderPaint = Paint().apply { shader = patternShader }
    private val bandYUniform = FloatArray(MAX_BANDS)
    private val bandHUniform = FloatArray(MAX_BANDS)

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        addUpdateListener { animation ->
            phase = animation.animatedValue as Float
            val now = SystemClock.uptimeMillis()
            if (now - lastDrawRequestMillis >= FRAME_INTERVAL_MILLIS) {
                lastDrawRequestMillis = now
                invalidate()
            }
        }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            val currentDisplay = display ?: return
            if (currentDisplay.displayId != displayId) return

            val currentState = currentDisplay.state
            val resumed = currentState == Display.STATE_ON &&
                lastDisplayState != Display.STATE_ON
            lastDisplayState = currentState
            if (resumed) {
                scheduleRenderRecovery()
            } else if (currentState != Display.STATE_ON) {
                pauseAnimator()
            }
        }
    }

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setWillNotDraw(false)
        applyGeometryUniforms()
    }

    fun configure(
        topColor: Int,
        bottomColor: Int,
        holdFraction: Float,
        designWidth: Float,
        designHeight: Float,
        geometryWidth: Float,
    ) {
        val nextGeometry = if (geometryWidth > 1_500f) {
            TOP_PATTERN_GEOMETRY
        } else {
            BOTTOM_PATTERN_GEOMETRY
        }
        val visualChanged =
            this.topColor != topColor ||
                this.bottomColor != bottomColor ||
                this.holdFraction != holdFraction ||
                this.designWidth != designWidth ||
                this.designHeight != designHeight ||
                geometry !== nextGeometry
        val durationChanged = geometry.rowCycleMillis != nextGeometry.rowCycleMillis

        this.topColor = topColor
        this.bottomColor = bottomColor
        this.holdFraction = holdFraction
        this.designWidth = designWidth
        this.designHeight = designHeight
        geometry = nextGeometry

        if (durationChanged || animator.duration != geometry.rowCycleMillis.toLong()) {
            animator.duration = geometry.rowCycleMillis.toLong()
        }
        if (visualChanged) {
            applyGeometryUniforms()
            invalidate()
        }
        if ((visualChanged || durationChanged) && isAttachedToWindow) {
            ensureAnimatorRunning()
            scheduleRenderRecovery()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastDisplayState = display?.state ?: Display.STATE_UNKNOWN
        if (!displayListenerRegistered) {
            displayManager.registerDisplayListener(displayListener, handler)
            displayListenerRegistered = true
        }
        ensureAnimatorRunning()
        scheduleRenderRecovery()
    }

    override fun onDetachedFromWindow() {
        if (displayListenerRegistered) {
            displayManager.unregisterDisplayListener(displayListener)
            displayListenerRegistered = false
        }
        renderRecoveryPosted = false
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        val becameVisible =
            visibility == VISIBLE && lastWindowVisibility != VISIBLE
        lastWindowVisibility = visibility
        if (becameVisible) {
            scheduleRenderRecovery()
        } else if (visibility != VISIBLE) {
            pauseAnimator()
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        val regainedFocus = hasWindowFocus && !hadWindowFocus
        hadWindowFocus = hasWindowFocus
        if (regainedFocus) scheduleRenderRecovery()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        patternShader.setFloatUniform("viewSize", width.toFloat(), height.toFloat())
        patternShader.setFloatUniform("designSize", designWidth, designHeight)
        patternShader.setFloatUniform("phase", phase)
        patternShader.setFloatUniform("holdFraction", holdFraction)
        patternShader.setFloatUniform("lift", if (isDarkBackdrop(topColor, bottomColor)) 1f else 0f)
        patternShader.setFloatUniform("topColor", channels(topColor))
        patternShader.setFloatUniform("bottomColor", channels(bottomColor))
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shaderPaint)
    }

    private fun applyGeometryUniforms() {
        val bands = geometry.bands
        for (index in 0 until MAX_BANDS) {
            val band = bands.getOrNull(index) ?: bands.last()
            bandYUniform[index] = band.sourceCenterY / 2f
            bandHUniform[index] = band.logicalNativeHeight / 2f
        }
        patternShader.setFloatUniform("bandY", bandYUniform)
        patternShader.setFloatUniform("bandH", bandHUniform)
        patternShader.setIntUniform("bandCount", bands.size)
        patternShader.setFloatUniform("rowPitch", geometry.rowPitch)
        patternShader.setFloatUniform("firstRowCenter", geometry.firstRowCenter)
        patternShader.setFloatUniform("cellPeriod", geometry.nativeCellWidth / 2f)
        patternShader.setFloatUniform("firstCenter", geometry.nativePhaseOffset / 2f)
        patternShader.setFloatUniform(
            "depthRange",
            geometry.bands.first().sourceCenterY / 2f,
            geometry.bands.last().sourceCenterY / 2f,
        )
        patternShader.setFloatUniform("triangleColor", channels(geometry.triangleColor))
        patternShader.setFloatUniform(
            "sourceAlpha",
            (android.graphics.Color.alpha(geometry.triangleColor) / 255f) *
                TRIANGLE_COLOR_BURN_STRENGTH,
        )
    }

    private fun channels(color: Int): FloatArray = floatArrayOf(
        android.graphics.Color.red(color) / 255f,
        android.graphics.Color.green(color) / 255f,
        android.graphics.Color.blue(color) / 255f,
    )

    private fun scheduleRenderRecovery() {
        if (!isAttachedToWindow || renderRecoveryPosted) return
        renderRecoveryPosted = true
        post {
            renderRecoveryPosted = false
            if (
                !isAttachedToWindow ||
                windowVisibility != VISIBLE ||
                display?.state != Display.STATE_ON
            ) {
                return@post
            }
            ensureAnimatorRunning()
            postInvalidateOnAnimation()
        }
    }

    private fun ensureAnimatorRunning() {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        animator.duration = geometry.rowCycleMillis.toLong()
        when {
            animator.isPaused -> animator.resume()
            !animator.isRunning -> {
                animator.cancel()
                animator.start()
            }
        }
    }

    private fun pauseAnimator() {
        if (animator.isRunning && !animator.isPaused) animator.pause()
    }
}

private data class PatternBand(
    val sourceTop: Int,
    val sourceBottomExclusive: Int,
    val logicalNativeHeight: Int = sourceBottomExclusive - sourceTop,
) {
    val sourceCenterY: Float get() = (sourceTop + sourceBottomExclusive) / 2f
}

private data class PatternGeometry(
    val nativeCellWidth: Int,
    val nativePhaseOffset: Int,
    val firstRowCenter: Float,
    val rowPitch: Float,
    val rowCycleMillis: Int,
    val triangleColor: Int,
    val bands: List<PatternBand>,
)

private val BOTTOM_PATTERN_GEOMETRY = PatternGeometry(
    nativeCellWidth = 162,
    nativePhaseOffset = 29,
    firstRowCenter = 8.75f,
    rowPitch = 70f,
    rowCycleMillis = 4_292,
    triangleColor = 0x307075F4,
    bands = listOf(
        PatternBand(0, 35),
        PatternBand(93, 177),
        PatternBand(235, 320),
        PatternBand(373, 462),
        PatternBand(510, 604),
        PatternBand(648, 747),
        PatternBand(790, 889),
        PatternBand(922, 1031),
        PatternBand(1065, 1174),
        PatternBand(1202, 1316),
        PatternBand(1340, 1458),
        PatternBand(1477, 1601),
        PatternBand(1620, 1743),
        PatternBand(1757, 1886),
        PatternBand(1894, 2023),
        PatternBand(2032, 2160),
    ),
)

private val TOP_PATTERN_GEOMETRY = PatternGeometry(
    nativeCellWidth = 214,
    nativePhaseOffset = 0,
    firstRowCenter = 18.75f,
    rowPitch = 92.5f,
    rowCycleMillis = 5_672,
    triangleColor = 0x267075F4,
    bands = listOf(
        PatternBand(0, 75),
        PatternBand(146, 263),
        PatternBand(327, 451),
        PatternBand(509, 639),
        PatternBand(697, 827),
        PatternBand(872, 1016),
        PatternBand(1060, 1204),
        PatternBand(1242, 1392),
        PatternBand(1423, 1580),
        PatternBand(1605, 1768),
        PatternBand(1793, 1956),
        PatternBand(1975, 2160, logicalNativeHeight = 169),
    ),
)

private const val FRAME_INTERVAL_MILLIS = 42L
private const val TRIANGLE_ASPECT_RATIO = 1.155f
private const val TRIANGLE_COLOR_BURN_STRENGTH = 0.78f
private const val MAX_BANDS = 16

private val PATTERN_SHADER = """
uniform float2 viewSize;
uniform float2 designSize;
uniform float phase;
uniform float holdFraction;
uniform float lift;
uniform float3 topColor;
uniform float3 bottomColor;
uniform float3 triangleColor;
uniform float sourceAlpha;
uniform float rowPitch;
uniform float firstRowCenter;
uniform float cellPeriod;
uniform float firstCenter;
uniform float2 depthRange;
uniform float bandY[$MAX_BANDS];
uniform float bandH[$MAX_BANDS];
uniform int bandCount;

float envelopeAt(float y) {
    float h = bandH[0];
    for (int i = 1; i < $MAX_BANDS; i++) {
        if (i >= bandCount) { break; }
        float y0 = bandY[i - 1];
        float y1 = bandY[i];
        if (y >= y0) {
            float f = clamp((y - y0) / max(y1 - y0, 0.0001), 0.0, 1.0);
            h = mix(bandH[i - 1], bandH[i], f);
        }
    }
    return h;
}

half4 main(float2 fragCoord) {
    float2 scale = viewSize / designSize;
    float2 p = fragCoord / scale;
    float aa = max(designSize.x / viewSize.x, 0.0001);

    float travel = phase * rowPitch;
    float rowIndex = floor((p.y + travel - firstRowCenter) / rowPitch + 0.5);
    float centerY = firstRowCenter + rowIndex * rowPitch - travel;

    float envH = envelopeAt(centerY);
    float depth = clamp(
        (centerY - depthRange.x) / max(depthRange.y - depthRange.x, 0.0001),
        0.0,
        1.0
    );
    float triH = envH * (0.68 + 0.32 * depth);
    float triW = triH * $TRIANGLE_ASPECT_RATIO;
    float top = centerY - envH * 0.5;
    float bottom = centerY + envH * 0.5;

    float dxD = p.x - firstCenter;
    dxD = dxD - cellPeriod * floor(dxD / cellPeriod + 0.5);
    float tD = (p.y - top) / max(triH, 0.0001);
    float wD = triW * 0.5 * (1.0 - tD);
    float covD = clamp((wD - abs(dxD)) / aa + 0.5, 0.0, 1.0) *
        clamp((p.y - top) / aa + 0.5, 0.0, 1.0) *
        clamp(((top + triH) - p.y) / aa + 0.5, 0.0, 1.0);

    float dxU = p.x - (firstCenter + cellPeriod * 0.5);
    dxU = dxU - cellPeriod * floor(dxU / cellPeriod + 0.5);
    float tU = (bottom - p.y) / max(triH, 0.0001);
    float wU = triW * 0.5 * (1.0 - tU);
    float covU = clamp((wU - abs(dxU)) / aa + 0.5, 0.0, 1.0) *
        clamp((bottom - p.y) / aa + 0.5, 0.0, 1.0) *
        clamp((p.y - (bottom - triH)) / aa + 0.5, 0.0, 1.0);

    float cov = max(covD, covU);
    if (cov <= 0.0) {
        return half4(0.0);
    }

    float gradientStart = designSize.y * holdFraction;
    float gradientFraction = centerY <= gradientStart
        ? 0.0
        : clamp(
            (centerY - gradientStart) / max(designSize.y - gradientStart, 0.0001),
            0.0,
            1.0
        );
    float3 backdrop = mix(topColor, bottomColor, gradientFraction);
    float3 burned = clamp(
        1.0 - (1.0 - backdrop) / max(triangleColor, float3(0.0001)),
        0.0,
        1.0
    );
    float3 lifted = clamp(backdrop + triangleColor * 0.085 + float3(0.035), 0.0, 1.0);
    float3 shaded = burned * sourceAlpha + backdrop * (1.0 - sourceAlpha);
    float3 outColor = mix(shaded, lifted, lift);
    return half4(half3(outColor) * half(cov), half(cov));
}
"""

private fun isDarkBackdrop(topColor: Int, bottomColor: Int): Boolean {
    fun luminance(color: Int): Float =
        (0.2126f * android.graphics.Color.red(color) +
            0.7152f * android.graphics.Color.green(color) +
            0.0722f * android.graphics.Color.blue(color)) / 255f
    return (luminance(topColor) + luminance(bottomColor)) / 2f < 0.5f
}
