package com.pocketpass.spike

internal const val MAX_BANDS = 16
internal const val TRIANGLE_ASPECT_RATIO = 1.155f
internal const val TRIANGLE_COLOR_BURN_STRENGTH = 0.78f

internal data class PatternBand(
    val sourceTop: Int,
    val sourceBottomExclusive: Int,
    val logicalNativeHeight: Int = sourceBottomExclusive - sourceTop,
) {
    val sourceCenterY: Float get() = (sourceTop + sourceBottomExclusive) / 2f
}

internal data class PatternGeometry(
    val nativeCellWidth: Int,
    val nativePhaseOffset: Int,
    val firstRowCenter: Float,
    val rowPitch: Float,
    val rowCycleMillis: Int,
    val triangleColor: Long,
    val bands: List<PatternBand>,
)

internal val BOTTOM_PATTERN_GEOMETRY = PatternGeometry(
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

internal val TOP_PATTERN_GEOMETRY = PatternGeometry(
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

internal val PATTERN_SHADER = """
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
