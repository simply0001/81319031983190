package com.pocketpass.app.ui.controller

import androidx.compose.ui.graphics.Paint
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter

@Suppress("DEPRECATION_ERROR")
internal actual fun Paint.applyBlurMaskFilter(radius: Float) {
    asFrameworkPaint().maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, radius * 0.57735f + 0.5f)
}
