package com.pocketpass.app.ui.mii

import androidx.compose.ui.graphics.Color
import com.pocketpass.app.mii.MiiAppearance
import com.pocketpass.app.mii.MiiTraitField
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.pocketpass.ui.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

internal object MiiTraitIconCatalog {
    private const val RESOURCE_PATH = "files/mii/icons.json"

    private var iconsByFamily: Map<String, List<String>>? = null

    private val renderedCache = mutableMapOf<String, ByteArray>()

    // Non-suspending fast path, valid once the icon catalog has been read.
    fun cachedIcon(
        field: MiiTraitField,
        index: Int,
        appearance: MiiAppearance,
        centerContent: Boolean = false,
    ): ByteArray? = iconsByFamily?.let { icon(it, field, index, appearance, centerContent) }

    suspend fun icon(
        field: MiiTraitField,
        index: Int,
        appearance: MiiAppearance,
        centerContent: Boolean = false,
    ): ByteArray? = icon(icons(), field, index, appearance, centerContent)

    private fun icon(
        families: Map<String, List<String>>,
        field: MiiTraitField,
        index: Int,
        appearance: MiiAppearance,
        centerContent: Boolean,
    ): ByteArray? {
        val family = field.iconFamily ?: return null
        val source = families[family]?.getOrNull(index) ?: return null
        val paletteKey = appearance.iconPaletteKey()
        val cacheKey = "$family:$index:$paletteKey:$centerContent"
        return renderedCache.getOrPut(cacheKey) {
            source
                .replace("currentColor", "#39798B")
                .replaceCssVariables(appearance)
                .let { if (centerContent) it.withCenteredViewBox() else it }
                .encodeToByteArray()
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun icons(): Map<String, List<String>> =
        iconsByFamily
            ?: Json.parseToJsonElement(Res.readBytes(RESOURCE_PATH).decodeToString())
                .jsonObject
                .mapValues { (_, value) ->
                    value.jsonArray.map { it.jsonPrimitive.content }
                }
                .also { iconsByFamily = it }
}

@Composable
internal fun rememberMiiTraitIcon(
    field: MiiTraitField,
    index: Int,
    appearance: MiiAppearance,
    centerContent: Boolean = false,
): ByteArray? {
    val bytes by produceState(
        MiiTraitIconCatalog.cachedIcon(field, index, appearance, centerContent),
        field,
        index,
        appearance,
        centerContent,
    ) {
        if (value == null) {
            value = MiiTraitIconCatalog.icon(field, index, appearance, centerContent)
        }
    }
    return bytes
}

private val MiiTraitField.iconFamily: String?
    get() = when (this) {
        MiiTraitField.FaceType -> "face"
        MiiTraitField.MakeupType -> "makeup"
        MiiTraitField.WrinklesType -> "wrinkles"
        MiiTraitField.HairType -> "hair"
        MiiTraitField.EyebrowType -> "eyebrows"
        MiiTraitField.EyeType -> "eyes"
        MiiTraitField.NoseType -> "nose"
        MiiTraitField.MouthType -> "mouth"
        MiiTraitField.MustacheType -> "mustache"
        MiiTraitField.BeardType -> "goatee"
        MiiTraitField.GlassesType -> "glasses"
        MiiTraitField.HatType -> "hat"
        MiiTraitField.Gender -> null
    }

private fun String.replaceCssVariables(appearance: MiiAppearance): String {
    val hair = MiiEditorColors.hair.getOrElse(appearance.hairColor) { MiiEditorColors.hair[0] }
    val eyebrows = MiiEditorColors.hair.getOrElse(appearance.eyebrowColor) { hair }
    val facialHair = MiiEditorColors.hair.getOrElse(appearance.facialHairColor) { hair }
    val eyes = MiiEditorColors.eyes.getOrElse(appearance.eyeColor) { MiiEditorColors.eyes[0] }
    val glasses = MiiEditorColors.glasses.getOrElse(appearance.glassesColor) {
        MiiEditorColors.glasses[0]
    }
    val favorite = MiiEditorColors.favorite.getOrElse(appearance.favoriteColor) {
        MiiEditorColors.favorite[0]
    }
    val hat = MiiEditorColors.favorite.getOrElse(appearance.extHatColor) { favorite }
    val mouthTop = MiiEditorColors.mouthTop.getOrElse(appearance.mouthColor) {
        MiiEditorColors.mouthTop[0]
    }
    val mouthBottom = MiiEditorColors.mouth.getOrElse(appearance.mouthColor) {
        MiiEditorColors.mouth[0]
    }

    return replace("var(--eye-color)", eyes.toSvgHex())
        .replace("var(--icon-eyebrow-fill)", eyebrows.toSvgHex())
        .replace("var(--icon-face-detail)", "#8D8D8D")
        .replace("var(--icon-face-fill)", "#FFFFFF")
        .replace("var(--icon-face-stroke)", "#6F6F6F")
        .replace("var(--icon-face-wrinkles)", "#8D8D8D")
        .replace("var(--icon-facial-hair-fill)", facialHair.toSvgHex())
        .replace("var(--icon-glasses-fill)", glasses.toSvgHex())
        .replace("var(--icon-glasses-shade)", glasses.toSvgHex(alpha = 0x77))
        .replace("var(--icon-hair-fill)", hair.toSvgHex())
        .replace("var(--icon-hair-tie)", favorite.toSvgHex())
        .replace("var(--icon-hat-fill)", hat.toSvgHex())
        .replace("var(--icon-hat-stroke)", hat.darken(0.48f).toSvgHex())
        .replace("var(--icon-head-fill)", "#FFFFFF")
        .replace("var(--icon-head-stroke)", "#999999")
        .replace("var(--icon-lip-color-bottom)", mouthBottom.toSvgHex())
        .replace("var(--icon-lip-color-top)", mouthTop.toSvgHex())
        .replace("var(--icon-mouth-tooth)", "#FFFFFF")
}

private fun MiiAppearance.iconPaletteKey(): String = listOf(
    hairColor,
    eyebrowColor,
    facialHairColor,
    eyeColor,
    glassesColor,
    favoriteColor,
    mouthColor,
    extHatColor,
).joinToString(":")

private fun Color.toSvgHex(alpha: Int? = null): String {
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    return if (alpha == null) {
        "#${r.toHexByte()}${g.toHexByte()}${b.toHexByte()}"
    } else {
        "#${r.toHexByte()}${g.toHexByte()}${b.toHexByte()}${alpha.coerceIn(0, 255).toHexByte()}"
    }
}

private fun Int.toHexByte(): String = toString(16).uppercase().padStart(2, '0')

private fun Color.darken(multiplier: Float): Color = Color(
    red = red * multiplier,
    green = green * multiplier,
    blue = blue * multiplier,
    alpha = alpha,
)

internal object MiiEditorColors {
    val figmaEyes = listOf(
        Color(0xFF373737),
        Color(0xFFA1A1A1),
        Color(0xFF6E412E),
        Color(0xFF5A6840),
        Color(0xFF6B7AC5),
        Color(0xFF598A7B),
    )

    val skin = listOf(
        Color(0xFFFFD3AD),
        Color(0xFFFFB66B),
        Color(0xFFDE7942),
        Color(0xFFFFAA8C),
        Color(0xFFAD5129),
        Color(0xFF632C18),
        Color(0xFFFFBEA5),
        Color(0xFFFFC58F),
        Color(0xFF8C3C23),
        Color(0xFF3C2D23),
    )

    val hair = listOf(
        Color(0xFF000000),
        Color(0xFF402010),
        Color(0xFF5C180A),
        Color(0xFF7C3A14),
        Color(0xFF787880),
        Color(0xFF4E3E11),
        Color(0xFF875917),
        Color(0xFFD0A049),
    )

    val eyes = figmaEyes

    val mouth = listOf(
        Color(0xFFD04401),
        Color(0xFFF30100),
        Color(0xFFFD393A),
        Color(0xFFF58862),
        Color(0xFF1F1D1D),
    )

    val mouthTop = listOf(
        Color(0xFF823018),
        Color(0xFF780C0D),
        Color(0xFF882028),
        Color(0xFFDC7751),
        Color(0xFF461E0A),
    )

    val glasses = listOf(
        Color(0xFF666666),
        Color(0xFF8D694A),
        Color(0xFFA01612),
        Color(0xFFB5C0F0),
        Color(0xFFA4601E),
        Color(0xFF766F67),
    )

    val common = listOf(
        Color(0.1764706f, 0.1568628f, 0.1568628f),
        Color(0.2509804f, 0.1254902f, 0.0627451f),
        Color(0.3607844f, 0.0941177f, 0.0392157f),
        Color(0.4862746f, 0.2274510f, 0.0784314f),
        Color(0.4705883f, 0.4705883f, 0.5019608f),
        Color(0.3058824f, 0.2431373f, 0.0627451f),
        Color(0.5333334f, 0.3450981f, 0.0941177f),
        Color(0.8156863f, 0.6274510f, 0.2901961f),
        Color(0.0500000f, 0.0500000f, 0.0600000f),
        Color(0.4235295f, 0.4392157f, 0.4392157f),
        Color(0.4000000f, 0.2352942f, 0.1725491f),
        Color(0.3764706f, 0.3686275f, 0.1882353f),
        Color(0.2745099f, 0.3294118f, 0.6588236f),
        Color(0.2196079f, 0.4392157f, 0.3450981f),
        Color(0.3764706f, 0.2196079f, 0.0627451f),
        Color(0.6588236f, 0.0627451f, 0.0313726f),
        Color(0.1254902f, 0.1882353f, 0.4078432f),
        Color(0.6588236f, 0.3764706f, 0.0000000f),
        Color(0.4705883f, 0.4392157f, 0.4078432f),
        Color(0.8470589f, 0.3215687f, 0.0313726f),
        Color(0.9411765f, 0.0470589f, 0.0313726f),
        Color(0.9607844f, 0.2823530f, 0.2823530f),
        Color(0.9411765f, 0.6039216f, 0.4549020f),
        Color(0.5490197f, 0.3137255f, 0.2509804f),
        Color(0.5176471f, 0.1490197f, 0.1490197f),
        Color(1.0000000f, 0.4509804f, 0.4000000f),
        Color(1.0000000f, 0.6509804f, 0.6509804f),
        Color(1.0000000f, 0.7529412f, 0.7294118f),
        Color(0.4509804f, 0.1803922f, 0.2313726f),
        Color(0.6000000f, 0.1215687f, 0.2392157f),
        Color(0.5411765f, 0.0901961f, 0.2431373f),
        Color(0.7098040f, 0.2431373f, 0.2588236f),
        Color(0.7803922f, 0.1176471f, 0.3372550f),
        Color(0.6901961f, 0.3254902f, 0.5058824f),
        Color(0.7803922f, 0.3294118f, 0.4313726f),
        Color(0.9803922f, 0.4588236f, 0.5921569f),
        Color(0.9882353f, 0.6745099f, 0.7882353f),
        Color(1.0000000f, 0.7882353f, 0.8470589f),
        Color(0.1921569f, 0.1098040f, 0.2509804f),
        Color(0.2156863f, 0.1568628f, 0.2392157f),
        Color(0.2980393f, 0.0941177f, 0.3019608f),
        Color(0.4352942f, 0.2588236f, 0.7019608f),
        Color(0.5215687f, 0.3607844f, 0.7215687f),
        Color(0.7529412f, 0.5137255f, 0.8000000f),
        Color(0.6588236f, 0.5764706f, 0.7882353f),
        Color(0.7725491f, 0.6745099f, 0.9019608f),
        Color(0.9333334f, 0.7450981f, 0.9803922f),
        Color(0.8235295f, 0.7725491f, 0.9294118f),
        Color(0.0980393f, 0.1215687f, 0.2509804f),
        Color(0.0705883f, 0.2470589f, 0.4000000f),
        Color(0.1647059f, 0.5098040f, 0.8313726f),
        Color(0.3411765f, 0.7058824f, 0.9490197f),
        Color(0.4784314f, 0.7725491f, 0.8705883f),
        Color(0.5372550f, 0.6509804f, 0.9803922f),
        Color(0.5176471f, 0.7411765f, 0.9803922f),
        Color(0.6313726f, 0.8901961f, 1.0000000f),
        Color(0.0431373f, 0.1803922f, 0.2117648f),
        Color(0.0039216f, 0.2392157f, 0.2313726f),
        Color(0.0509804f, 0.3098040f, 0.3490197f),
        Color(0.1372550f, 0.4000000f, 0.3882353f),
        Color(0.1882353f, 0.4941177f, 0.5490197f),
        Color(0.3098040f, 0.6823530f, 0.6901961f),
        Color(0.4784314f, 0.7686275f, 0.6196079f),
        Color(0.4980393f, 0.8313726f, 0.7529412f),
        Color(0.5294118f, 0.8980393f, 0.7137255f),
        Color(0.0392157f, 0.2901961f, 0.2078432f),
        Color(0.2627451f, 0.4784314f, 0.0000000f),
        Color(0.0078432f, 0.4588236f, 0.3843138f),
        Color(0.2117648f, 0.6000000f, 0.4392157f),
        Color(0.2941177f, 0.6784314f, 0.1019608f),
        Color(0.5725491f, 0.7490197f, 0.0392157f),
        Color(0.3882353f, 0.7803922f, 0.5333334f),
        Color(0.6196079f, 0.8784314f, 0.2588236f),
        Color(0.5882353f, 0.8705883f, 0.4941177f),
        Color(0.7333334f, 0.9490197f, 0.6666667f),
        Color(0.6000000f, 0.5764706f, 0.1686275f),
        Color(0.6509804f, 0.5843138f, 0.3882353f),
        Color(0.8000000f, 0.7529412f, 0.2235295f),
        Color(0.8000000f, 0.7254902f, 0.5294118f),
        Color(0.8509804f, 0.8000000f, 0.5098040f),
        Color(0.8352942f, 0.8509804f, 0.4352942f),
        Color(0.8352942f, 0.9019608f, 0.5137255f),
        Color(0.8470589f, 0.9803922f, 0.6156863f),
        Color(0.4901961f, 0.2705883f, 0.0000000f),
        Color(0.9019608f, 0.7333334f, 0.4784314f),
        Color(0.9960785f, 0.8862746f, 0.2901961f),
        Color(0.9803922f, 0.8705883f, 0.5098040f),
        Color(0.9686275f, 0.9176471f, 0.6117648f),
        Color(0.9803922f, 0.9725491f, 0.6078432f),
        Color(0.6509804f, 0.3019608f, 0.1176471f),
        Color(1.0000000f, 0.5882353f, 0.0509804f),
        Color(0.8196079f, 0.6078432f, 0.4117648f),
        Color(1.0000000f, 0.6980393f, 0.4000000f),
        Color(1.0000000f, 0.7607844f, 0.5490197f),
        Color(0.8980393f, 0.8117648f, 0.6941177f),
        Color(0.2549020f, 0.2549020f, 0.2549020f),
        Color(0.6078432f, 0.6078432f, 0.6078432f),
        Color(0.7450981f, 0.7450981f, 0.7450981f),
        Color(0.8627451f, 0.8431373f, 0.8039216f),
        Color(1.0000000f, 1.0000000f, 1.0000000f),
    )

    val favorite = listOf(
        Color(0xFFD21E14),
        Color(0xFFFF6E19),
        Color(0xFFFFD820),
        Color(0xFF78D220),
        Color(0xFF007830),
        Color(0xFF0A48BC),
        Color(0xFF3CAADE),
        Color(0xFFF55A7D),
        Color(0xFF7328AD),
        Color(0xFF483818),
        Color(0xFFE0E0E0),
        Color(0xFF181814),
    )

    const val PALETTE_COLUMNS = 10

    val commonDisplayOrder: List<Int> by lazy {
        val keys = common.map { it.oklab() }
        val neutrals = common.indices.sortedBy { keys[it].chroma }.take(PALETTE_COLUMNS)
        val chromatic = common.indices
            .filter { it !in neutrals }
            .sortedBy { (keys[it].hue - RAINBOW_START + 360f) % 360f }
        chromatic.chunked(PALETTE_COLUMNS)
            .map { row -> smoothestRamp(row, keys) }
            .flatMapIndexed { index, row -> if (index % 2 == 1) row.reversed() else row } +
            neutrals.sortedBy { keys[it].lightness }
    }
}

private const val RAINBOW_START = 20f

internal data class Oklab(
    val lightness: Float,
    val a: Float,
    val b: Float,
) {
    val chroma: Float get() = hypot(a, b)
    val hue: Float get() = (atan2(b, a) * (180f / PI.toFloat()) + 360f) % 360f

    fun distanceTo(other: Oklab): Float =
        sqrt((lightness - other.lightness).pow(2) + (a - other.a).pow(2) + (b - other.b).pow(2))
}

internal fun Color.oklab(): Oklab {
    val r = red.srgbToLinear()
    val g = green.srgbToLinear()
    val bl = blue.srgbToLinear()
    val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * bl)
    val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * bl)
    val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * bl)
    return Oklab(
        lightness = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
        a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
        b = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s,
    )
}

private fun Float.srgbToLinear(): Float =
    if (this <= 0.04045f) this / 12.92f else ((this + 0.055f) / 1.055f).pow(2.4f)

private fun smoothestRamp(row: List<Int>, keys: List<Oklab>): List<Int> {
    val byLightness = row.sortedBy { keys[it].lightness }
    if (byLightness.size < 4) return byLightness
    val first = byLightness.first()
    val last = byLightness.last()
    val middle = byLightness.subList(1, byLightness.size - 1)
    val n = middle.size
    val full = (1 shl n) - 1
    val best = Array(1 shl n) { FloatArray(n) { Float.POSITIVE_INFINITY } }
    val parent = Array(1 shl n) { IntArray(n) { -1 } }
    for (j in 0 until n) best[1 shl j][j] = keys[first].distanceTo(keys[middle[j]])
    for (mask in 1..full) {
        for (j in 0 until n) {
            val cost = best[mask][j]
            if (cost == Float.POSITIVE_INFINITY || mask and (1 shl j) == 0) continue
            for (k in 0 until n) {
                if (mask and (1 shl k) != 0) continue
                val next = mask or (1 shl k)
                val candidate = cost + keys[middle[j]].distanceTo(keys[middle[k]])
                if (candidate < best[next][k]) {
                    best[next][k] = candidate
                    parent[next][k] = j
                }
            }
        }
    }
    var end = (0 until n).minBy { best[full][it] + keys[middle[it]].distanceTo(keys[last]) }
    var mask = full
    val ramp = ArrayDeque<Int>()
    while (end >= 0) {
        ramp.addFirst(middle[end])
        val previous = parent[mask][end]
        mask = mask and (1 shl end).inv()
        end = previous
    }
    return listOf(first) + ramp + last
}

internal data class SvgBounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
) {
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f

    fun union(other: SvgBounds): SvgBounds = SvgBounds(
        minX = minOf(minX, other.minX),
        minY = minOf(minY, other.minY),
        maxX = maxOf(maxX, other.maxX),
        maxY = maxOf(maxY, other.maxY),
    )
}

internal fun svgContentBounds(svg: String): SvgBounds? =
    SVG_PATH_DATA.findAll(svg)
        .mapNotNull { match -> svgPathBounds(match.groupValues[1]) }
        .reduceOrNull(SvgBounds::union)

internal fun String.withCenteredViewBox(): String {
    val viewBox = SVG_VIEW_BOX.find(this) ?: return this
    val parts = viewBox.groupValues[1].trim().split(" ", ",").mapNotNull(String::toFloatOrNull)
    if (parts.size != 4) return this
    val bounds = svgContentBounds(this) ?: return this
    val width = parts[2]
    val height = parts[3]
    val left = bounds.centerX - width / 2f
    val top = bounds.centerY - height / 2f
    return replaceRange(
        viewBox.range,
        "viewBox=\"${left.toSvgNumber()} ${top.toSvgNumber()} ${width.toSvgNumber()} ${height.toSvgNumber()}\"",
    )
}

internal fun svgPathBounds(data: String): SvgBounds? {
    val tokens = SVG_PATH_TOKEN.findAll(data).map { it.value }.toList()
    var index = 0
    var command = 'M'
    var x = 0f
    var y = 0f
    var startX = 0f
    var startY = 0f
    var bounds: SvgBounds? = null

    fun include(px: Float, py: Float) {
        bounds = bounds?.union(SvgBounds(px, py, px, py)) ?: SvgBounds(px, py, px, py)
    }

    fun next(): Float = tokens[index++].toFloat()

    fun hasPair(): Boolean = index + 1 < tokens.size && tokens[index][0].isNumberStart()

    while (index < tokens.size) {
        val token = tokens[index]
        if (token.length == 1 && token[0].isLetter()) {
            command = token[0]
            index++
            if (command == 'Z' || command == 'z') {
                x = startX
                y = startY
            }
            continue
        }
        val relative = command.isLowerCase()
        when (command.uppercaseChar()) {
            'M' -> {
                if (!hasPair()) break
                val nx = next()
                val ny = next()
                x = if (relative) x + nx else nx
                y = if (relative) y + ny else ny
                startX = x
                startY = y
                include(x, y)
                command = if (relative) 'l' else 'L'
            }

            'L', 'T' -> {
                if (!hasPair()) break
                val nx = next()
                val ny = next()
                x = if (relative) x + nx else nx
                y = if (relative) y + ny else ny
                include(x, y)
            }

            'H' -> {
                val nx = next()
                x = if (relative) x + nx else nx
                include(x, y)
            }

            'V' -> {
                val ny = next()
                y = if (relative) y + ny else ny
                include(x, y)
            }

            'C', 'S', 'Q' -> {
                val pairs = if (command.uppercaseChar() == 'C') 3 else 2
                var endX = x
                var endY = y
                repeat(pairs) {
                    if (!hasPair()) return bounds
                    val nx = next()
                    val ny = next()
                    endX = if (relative) x + nx else nx
                    endY = if (relative) y + ny else ny
                    include(endX, endY)
                }
                x = endX
                y = endY
            }

            'A' -> {
                if (index + 6 >= tokens.size) break
                index += 5
                val nx = next()
                val ny = next()
                x = if (relative) x + nx else nx
                y = if (relative) y + ny else ny
                include(x, y)
            }

            else -> index++
        }
    }
    return bounds
}

private fun Char.isNumberStart(): Boolean = isDigit() || this == '-' || this == '.' || this == '+'

private fun Float.toSvgNumber(): String =
    if (this == toInt().toFloat()) {
        toInt().toString()
    } else {
        val scaled = (this * 1000).roundToInt()
        val magnitude = abs(scaled)
        val sign = if (scaled < 0) "-" else ""
        "$sign${magnitude / 1000}.${(magnitude % 1000).toString().padStart(3, '0')}"
    }

private val SVG_PATH_DATA = Regex("""<path\b[^>]*?\sd="([^"]*)"""")
private val SVG_VIEW_BOX = Regex("""viewBox="([^"]*)"""")
private val SVG_PATH_TOKEN = Regex("""[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?""")
