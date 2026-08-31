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
actual fun AnimatedPatternSurface(
    topColor: Color,
    bottomColor: Color,
    holdFraction: Float,
    designWidth: Float,
    designHeight: Float,
    geometryWidth: Float,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
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
        val nextGeometry = patternGeometryFor(geometryWidth)
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
        patternShader.setFloatUniform("triangleColor", patternChannels(geometry.triangleColor))
        patternShader.setFloatUniform(
            "sourceAlpha",
            patternAlpha(geometry.triangleColor) * TRIANGLE_COLOR_BURN_STRENGTH,
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

private const val FRAME_INTERVAL_MILLIS = 42L

private fun isDarkBackdrop(topColor: Int, bottomColor: Int): Boolean {
    fun luminance(color: Int): Float =
        (0.2126f * android.graphics.Color.red(color) +
            0.7152f * android.graphics.Color.green(color) +
            0.0722f * android.graphics.Color.blue(color)) / 255f
    return (luminance(topColor) + luminance(bottomColor)) / 2f < 0.5f
}
