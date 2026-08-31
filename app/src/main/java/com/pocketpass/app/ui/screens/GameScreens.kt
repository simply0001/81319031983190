package com.pocketpass.app.ui.screens

import com.pocketpass.app.R
import com.pocketpass.app.ui.toJavaInstant
import android.animation.ValueAnimator
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pocketpass.app.domain.model.BingoCell
import com.pocketpass.app.model.GameTarget
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.BOTTOM_DESIGN_HEIGHT
import com.pocketpass.app.ui.BOTTOM_DESIGN_WIDTH
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.GochiHand
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.Staatliches
import com.pocketpass.app.ui.components.EntranceMotion
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.FullBleedArtwork
import com.pocketpass.app.ui.components.MotionLayer
import com.pocketpass.app.ui.components.PatternBackground
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.components.pocketShadow
import com.pocketpass.app.ui.components.rememberContinuousRotation
import com.pocketpass.app.ui.controller.controllerFocusBarrier
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.setup.CountryCatalog
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val GAME_CLOSED_OFFSET = 42f

private val BingoGoals = listOf(
    "Meet someone from Lebanon!",
    "Meet someone from Poland!",
    "Own a hat item!",
    "Wave at 5 people!",
    "Meet someone from Japan!",
    "Send 10 messages!",
    "Meet 3 people in one day!",
    "Meet someone from Brazil!",
    "Add a new friend!",
    "Meet someone from Canada!",
    "Buy a shop item!",
    "Meet someone at night!",
    "Meet someone from Egypt!",
    "Get 5 waves back!",
    "Meet someone from France!",
    "Change your mood!",
    "Meet someone from India!",
    "Meet the same person twice!",
    "Meet someone from Mexico!",
    "Send an emoji!",
    "Meet someone from Italy!",
    "Meet 10 people!",
    "Meet someone from Kenya!",
    "Message a new friend!",
    "Meet someone from Spain!",
)

private val BingoGoalShortLabels = listOf(
    "Lebanon",
    "Poland",
    "Hat Owner",
    "5 Waves",
    "Japan",
    "10 Msgs",
    "3 a Day",
    "Brazil",
    "New Friend",
    "Canada",
    "Go Shopping",
    "Night Owl",
    "Egypt",
    "5 Waves Back",
    "France",
    "New Mood",
    "India",
    "Rematch",
    "Mexico",
    "Emoji",
    "Italy",
    "10 People",
    "Kenya",
    "Say Hi",
    "Spain",
)

@Composable
fun TopActiveGame(
    metrics: DesignMetrics,
    state: PocketPassUiState,
) {
    val target = state.games.activeGame
    var retained by remember { mutableStateOf<GameTarget?>(null) }
    val translation = remember { Animatable(GAME_CLOSED_OFFSET) }
    SideEffect {
        if (target != null) retained = target
    }
    LaunchedEffect(target) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            translation.snapTo(if (target != null) 0f else GAME_CLOSED_OFFSET)
            if (target == null) retained = null
            return@LaunchedEffect
        }
        if (target != null) {
            translation.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
        } else if (retained != null) {
            translation.animateTo(
                targetValue = GAME_CLOSED_OFFSET,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
            retained = null
        }
    }
    val content = target ?: retained ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val settled = 1f - (translation.value / GAME_CLOSED_OFFSET).coerceIn(0f, 1f)
                translationY = translation.value
                alpha = settled
            }
            .testTag("top_game"),
    ) {
        when (content) {
            GameTarget.PuzzleSwap -> TopPuzzleSwap(metrics)
            GameTarget.Bingo -> TopBingo(metrics)
            GameTarget.WorldTour -> TopWorldTour(metrics)
        }
    }
}

@Composable
private fun TopPuzzleSwap(metrics: DesignMetrics) {
    FullBleedArtwork(metrics, Assets.GameWoodTop)
    FigmaAsset(
        resource = Assets.PuzzleSwapTitle,
        modifier = Modifier.designBounds(metrics, 135.99f, 405.5f, 1648f, 491f),
    )
}

@Composable
private fun TopBingo(metrics: DesignMetrics) {
    FullBleedArtwork(metrics, Assets.GameWoodTop)
    FigmaAsset(
        resource = Assets.BingoPaper,
        modifier = Modifier
            .designBounds(metrics, 280f, 157.3f, 1360f, 809.4f)
            .graphicsLayer { rotationZ = -7.85f },
    )
    FigmaAsset(
        resource = Assets.BingoTitle,
        modifier = Modifier.designBounds(metrics, 244f, 160f, 1432f, 804f),
    )
}

@Composable
private fun TopWorldTour(metrics: DesignMetrics) {
    FullBleedArtwork(metrics, Assets.WorldTourSpace)
    WorldTourGlobe(
        modifier = Modifier.designBounds(metrics, 537f, 183.5f, 846f, 846f),
    )
    Box(
        modifier = Modifier.designBounds(metrics, 50f, 182f, 1820f, 848f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "World Tour",
            color = Color.White,
            fontFamily = Staatliches,
            fontWeight = FontWeight.Normal,
            fontSize = metrics.sp(322.56f),
            maxLines = 1,
        )
    }
}

private const val GLOBE_SPIN_MILLIS = 36_000

private val GlobeShader = """
uniform shader map;
uniform float2 resolution;
uniform float2 mapSize;
uniform float spin;

float facetHash(float2 cell) {
    return fract(sin(dot(cell, float2(127.1, 311.7))) * 43758.5453);
}

half4 main(float2 coord) {
    float2 p = coord / resolution * 2.0 - 1.0;
    float r2 = dot(p, p);
    if (r2 >= 1.02) {
        return half4(0.0);
    }
    float z = sqrt(max(1.0 - r2, 0.0001));
    float u = atan(p.x, z) / 6.2831853 - spin;
    float v = asin(clamp(p.y, -1.0, 1.0)) / 3.1415926 + 0.5;

    float2 q = float2(u * 36.0, v * 18.0);
    float2 g = float2(q.x - q.y * 0.57735, q.y * 1.1547);
    float2 cell = floor(g);
    float2 f = fract(g);
    float upper = step(1.0, f.x + f.y);
    float2 id = cell + float2(upper * 7.0, upper * 3.0);
    float2 facetG = cell + mix(float2(0.333333), float2(0.666667), upper);
    float2 qc = float2(facetG.x + facetG.y * 0.5, facetG.y * 0.866025);
    float uc = qc.x / 36.0;
    float vc = clamp(qc.y / 18.0, 0.02, 0.98);

    half4 tex = map.eval(float2(uc * mapSize.x, vc * mapSize.y));
    float land = step(tex.b, tex.g);

    float h1 = facetHash(id);
    float h2 = facetHash(id + 19.0);
    float h3 = facetHash(id + 47.0);

    float lonView = (uc + spin) * 6.2831853;
    float latView = (vc - 0.5) * 3.1415926;
    float latCos = cos(latView);
    float3 facetNormal = float3(
        sin(lonView) * latCos,
        sin(latView),
        cos(lonView) * latCos
    );
    facetNormal = normalize(
        facetNormal +
            float3(h1 - 0.5, h2 - 0.5, (h3 - 0.5) * 0.5) * 0.30
    );

    float3 lightDir = normalize(float3(-0.40, -0.55, 0.73));
    float diffuse = clamp(dot(facetNormal, lightDir), 0.0, 1.0);
    float glint = pow(
        clamp(dot(normalize(lightDir + float3(0.0, 0.0, 1.0)), facetNormal), 0.0, 1.0),
        14.0
    );

    float tone = 0.3 + 0.5 * h1;
    half3 ocean = mix(half3(0.165, 0.627, 0.729), half3(0.478, 0.804, 0.863), tone);
    half3 ground = mix(half3(0.682, 0.788, 0.239), half3(0.824, 0.863, 0.306), tone);
    half3 base = mix(ocean, ground, land);

    float shade = 0.60 + 0.50 * diffuse;
    half3 color = base * shade + half3(0.12) * glint;

    color = color * (1.0 - 0.14 * smoothstep(0.80, 1.0, r2));
    float wobble = (h1 - 0.5) * 0.02;
    float edge = 1.0 - smoothstep(0.975 + wobble, 0.995 + wobble, r2);
    return half4(color * edge, edge);
}
""".trimIndent()

@Composable
internal fun WorldTourGlobe(modifier: Modifier) {
    if (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        !ValueAnimator.areAnimatorsEnabled()
    ) {
        FigmaAsset(
            resource = Assets.WorldTourGlobe,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
        return
    }

    val context = LocalContext.current
    val mapBitmap = remember(context) {
        BitmapFactory.decodeResource(
            context.resources,
            R.drawable.worldtour_globe_map,
            BitmapFactory.Options().apply { inScaled = false },
        )
    }
    val spin = rememberContinuousRotation(GLOBE_SPIN_MILLIS, "World Tour globe")
    val shader = remember { RuntimeShader(GlobeShader) }
    val brush = remember(mapBitmap) {
        shader.setInputShader(
            "map",
            BitmapShader(mapBitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP),
        )
        ShaderBrush(shader)
    }
    Canvas(modifier) {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("mapSize", mapBitmap.width.toFloat(), mapBitmap.height.toFloat())
        shader.setFloatUniform("spin", spin.value / 360f)
        drawRect(brush)
    }
}

@Composable
fun GameBottomOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val blockInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("game_bottom_overlay")
            .controllerFocusBarrier("game_bottom_overlay", layer = 20)
            .clickable(
                interactionSource = blockInteraction,
                indication = null,
            ) {},
    ) {
        when (state.games.activeGame) {
            GameTarget.PuzzleSwap -> PuzzleSwapBottom(metrics)
            GameTarget.Bingo -> BingoBottom(metrics, state, dispatch)
            GameTarget.WorldTour -> WorldTourBottom(metrics, state, dispatch)
            null -> Unit
        }
        if (
            state.games.activeGame == GameTarget.WorldTour &&
            state.games.worldTourRegionsVisible
        ) {
            WorldTourRegionsBottom(metrics, state)
        }
    }
}

@Composable
private fun PuzzleSwapBottom(metrics: DesignMetrics) {
    FullBleedArtwork(metrics, Assets.PuzzleSwapBottom, Modifier.testTag("game_puzzle_swap"))
}

private const val BINGO_CELL_SIZE = 166f
private const val BINGO_CELL_STEP = 176f
private const val BINGO_CARD_SIZE = 870f
private const val BINGO_CARD_ROTATION = -1.73f

@Composable
private fun BingoBottom(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    FullBleedArtwork(metrics, Assets.GameWoodBottom, Modifier.testTag("game_bingo"))
    val board = state.bingo.cells
    val displayCells: List<BingoCell?> =
        if (board.size == 24 && board.none { it.position == 12 }) {
            val byPosition = board.associateBy(BingoCell::position)
            List(25) { index -> if (index == 12) null else byPosition[index] }
        } else {
            List(25) { index ->
                if (index == 12) {
                    null
                } else {
                    BingoCell(
                        position = index,
                        slug = "fallback_$index",
                        text = BingoGoals[index % BingoGoals.size],
                        shortLabel = BingoGoalShortLabels[index % BingoGoalShortLabels.size],
                        completed = false,
                        progressCurrent = 0,
                        progressTarget = 1,
                    )
                }
            }
        }
    Box(
        modifier = Modifier.designBounds(metrics, 172.03f, 118.869f, 895.94f, 895.94f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .designBounds(metrics, 12.97f, 12.97f, BINGO_CARD_SIZE, BINGO_CARD_SIZE)
                .graphicsLayer { rotationZ = BINGO_CARD_ROTATION },
        ) {
            repeat(25) { index ->
                val row = index / 5
                val column = index % 5
                val cell = displayCells[index]
                val interaction = remember(index) { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .designBounds(
                            metrics,
                            column * BINGO_CELL_STEP,
                            row * BINGO_CELL_STEP,
                            BINGO_CELL_SIZE,
                            BINGO_CELL_SIZE,
                        )
                        .background(Color.White)
                        .border(metrics.dp(3f), Color.Black)
                        .testTag("bingo_cell_$index")
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = cell != null,
                        ) { dispatch(PocketPassEvent.SelectBingoSquare(index)) },
                    contentAlignment = Alignment.Center,
                ) {
                    val label = cell?.shortLabel ?: "FREE"
                    var labelSize by remember(index, label) {
                        mutableStateOf(if (cell == null) 44f else 34f)
                    }
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = metrics.dp(10f)),
                        color = Color.Black.copy(alpha = if (cell == null) 0.78f else 0.62f),
                        fontFamily = GochiHand,
                        fontSize = metrics.sp(labelSize),
                        lineHeight = metrics.sp(labelSize * 1.12f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        onTextLayout = { layout ->
                            if (breaksBadly(label, layout) && labelSize > 18f) {
                                labelSize *= 0.9f
                            }
                        },
                    )
                    if (cell == null || cell.completed) {
                        BingoStamp(index = index)
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .border(metrics.dp(10f), Color.Black),
            )
        }
    }

    val goalIndex = state.games.bingoGoalIndex
    val selectedCell = goalIndex?.let(displayCells::getOrNull)
    if (goalIndex != null && selectedCell != null) {
        val scrimInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF080808).copy(alpha = 0.8f))
                .testTag("bingo_goal_scrim")
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                ) { dispatch(PocketPassEvent.CloseBingoSquare) },
        )
        FigmaAsset(
            resource = Assets.BingoNotePaper,
            modifier = Modifier.designBounds(metrics, 117f, 48.5f, 1005f, 1005f),
        )
        var goalFontSize by remember(goalIndex) { mutableStateOf(100.167f) }
        Box(
            modifier = Modifier
                .designBounds(metrics, 289.5f, 268.5f, 660f, 565f)
                .graphicsLayer { rotationZ = -2.94f },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(metrics.dp(28f)),
            ) {
                Text(
                    text = selectedCell.text,
                    color = Color.Black.copy(alpha = 0.88f),
                    fontFamily = GochiHand,
                    fontWeight = FontWeight.Normal,
                    fontSize = metrics.sp(goalFontSize),
                    lineHeight = metrics.sp(goalFontSize * 1.18f),
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    onTextLayout = { layout ->
                        if (
                            breaksBadly(selectedCell.text, layout) &&
                            goalFontSize > 44f
                        ) {
                            goalFontSize *= 0.92f
                        }
                    },
                )
                if (selectedCell.completed) {
                    Text(
                        text = "Done!",
                        color = Color(0xFF3CBC29),
                        fontFamily = GochiHand,
                        fontSize = metrics.sp(64f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                } else if (selectedCell.progressTarget > 1) {
                    Text(
                        text = "${selectedCell.progressCurrent}/" +
                            "${selectedCell.progressTarget} so far",
                        color = Color.Black.copy(alpha = 0.5f),
                        fontFamily = GochiHand,
                        fontSize = metrics.sp(52f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun breaksBadly(text: String, layout: TextLayoutResult): Boolean {
    if (layout.hasVisualOverflow) return true
    return (0 until layout.lineCount - 1).any { line ->
        val end = layout.getLineEnd(line, visibleEnd = false)
        end in 1 until text.length &&
            !text[end - 1].isWhitespace() &&
            !text[end].isWhitespace()
    }
}

@Composable
private fun BingoStamp(index: Int) {
    val wobble = ((index * 37) % 11 - 5).toFloat()
    Canvas(Modifier.fillMaxSize()) {
        val stroke = Stroke(
            width = size.minDimension * 0.075f,
            cap = StrokeCap.Round,
        )
        rotate(degrees = wobble) {
            drawOval(
                color = Color(0xFF3CBC29).copy(alpha = 0.85f),
                topLeft = Offset(size.width * 0.06f, size.height * 0.10f),
                size = Size(size.width * 0.88f, size.height * 0.80f),
                style = stroke,
            )
            drawOval(
                color = Color(0xFF3CBC29).copy(alpha = 0.5f),
                topLeft = Offset(size.width * 0.10f, size.height * 0.15f),
                size = Size(size.width * 0.80f, size.height * 0.70f),
                style = Stroke(
                    width = size.minDimension * 0.045f,
                    cap = StrokeCap.Round,
                ),
            )
        }
    }
}

@Composable
private fun WorldTourBottom(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    FullBleedArtwork(metrics, Assets.WorldTourMap, Modifier.testTag("game_world_tour"))

    val regions = state.worldTour.regions
    val totalRegions = CountryCatalog.countries.size
    val latestRegion = regions.firstOrNull()
    val pillText = latestRegion?.let { region ->
        val name = CountryCatalog.countries
            .firstOrNull { it.code == region.countryCode }
            ?.name
            ?: region.countryCode
        "$name ${CountryCatalog.flagEmoji(region.countryCode)}"
    } ?: "No regions yet"

    val pillShape = RoundedCornerShape(percent = 50)
    Box(modifier = Modifier.designBounds(metrics, 247.5f, 375.5f, 745f, 153f)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = 14f }
                .pocketShadow(metrics, 76.5f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(pillShape)
                .pocketFrame(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.White,
                            0.39364f to Color.White,
                            0.99791f to Color(0xFFBDF8CB),
                        ),
                    ),
                    metrics.dp(15f),
                    Color(0xFF5E9AAC),
                    pillShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = pillText,
                color = Color(0xFF1D596B),
                fontFamily = Rubik,
                fontWeight = FontWeight.Medium,
                fontSize = metrics.sp(80f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    Box(
        modifier = Modifier
            .designBounds(metrics, 0f, 757.5f, 1240f, 322.5f)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.95043f to Color(0xFF00264B),
                        1f to Color(0xFF00264B),
                    ),
                ),
            ),
    )
    Box(
        modifier = Modifier.designBounds(metrics, 0f, 821.5f, 1240f, 95f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${regions.size}/$totalRegions Regions Discovered",
            color = Color.White,
            fontFamily = Rubik,
            fontWeight = FontWeight.Medium,
            fontSize = metrics.sp(80f),
            maxLines = 1,
        )
    }

    val buttonInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .designBounds(metrics, 1080f, 40f, 120f, 120f)
            .testTag("world_tour_regions_button")
            .controllerTarget("world_tour_regions_button", layer = 20) {
                dispatch(PocketPassEvent.OpenWorldTourRegions)
            }
            .clickable(
                interactionSource = buttonInteraction,
                indication = null,
            ) { dispatch(PocketPassEvent.OpenWorldTourRegions) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = 10f }
                .pocketShadow(metrics, 60f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .pocketFrame(Color.White, metrics.dp(15f), Color(0xFF5E9AAC), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FigmaAsset(
                resource = Assets.NavSettings,
                modifier = Modifier.requiredSize(metrics.dp(64f)),
                colorFilter = ColorFilter.tint(Color(0xFF1D596B)),
            )
        }
    }

    val barShape = RoundedCornerShape(metrics.dp(118f))
    val fillWidth = if (regions.isEmpty()) {
        0f
    } else {
        (1154f * regions.size / totalRegions).coerceIn(72f, 1154f)
    }
    Box(modifier = Modifier.designBounds(metrics, 43f, 944.85f, 1154f, 72f)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = 15.674f }
                .pocketShadow(metrics, 118f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(barShape)
                .pocketFrame(Color.White, metrics.dp(20.152f), Color(0xFF9F9F9F), barShape),
        )
        if (fillWidth > 0f) {
            Box(
                modifier = Modifier
                    .designBounds(metrics, 0f, 0f, fillWidth, 72f)
                    .clip(barShape)
                    .pocketFrame(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.19231f to Color(0xFF57E25F),
                                0.50962f to Color(0xFF5EED6F),
                                0.55288f to Color(0xFF57E25F),
                                1f to Color(0xFF3CBC29),
                            ),
                        ),
                        metrics.dp(20.152f),
                        Color(0xFF4BC252),
                        barShape,
                    ),
            )
        }
    }
}

private val WorldTourTextColor = Color(0xFF1D596B)
private val WorldTourBorderColor = Color(0xFF5E9AAC)

@Composable
private fun WorldTourRegionsBottom(
    metrics: DesignMetrics,
    state: PocketPassUiState,
) {
    val blockInteraction = remember { MutableInteractionSource() }
    PatternBackground(
        metrics = metrics,
        pattern = Assets.PatternActivitiesBottom,
        topColor = Color(0xFFE9EFF6),
        bottomColor = Color(0xFFBCDCFC),
        holdFraction = 0.4375f,
        designWidth = BOTTOM_DESIGN_WIDTH,
        designHeight = BOTTOM_DESIGN_HEIGHT,
    )
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .testTag("world_tour_regions_overlay")
            .controllerFocusBarrier("world_tour_regions_overlay", layer = 30)
            .clickable(
                interactionSource = blockInteraction,
                indication = null,
            ) {},
    )
    val regions = state.worldTour.regions
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    }
    Column(
        modifier = Modifier
            .designBounds(metrics, 40f, 60f, 1160f, 980f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(metrics.dp(40f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MotionLayer(
            entrance = EntranceMotion.OverlayPop,
            delayMillis = 40,
        ) {
            Box(
                Modifier
                    .requiredWidth(metrics.dp(1140f))
                    .requiredHeight(metrics.dp(141f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Regions Discovered",
                    color = WorldTourTextColor,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = metrics.sp(90f),
                    maxLines = 1,
                )
            }
        }
        MotionLayer(
            entrance = EntranceMotion.OverlayPop,
            delayMillis = 100,
        ) {
            val panelShape = RoundedCornerShape(metrics.dp(129f))
            Box(Modifier.requiredWidth(metrics.dp(1140f))) {
                Box(
                    Modifier
                        .matchParentSize()
                        .offset(y = metrics.dp(15.674f))
                        .pocketShadow(metrics, 129f),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(panelShape)
                        .pocketFrame(
                            Color.White,
                            metrics.dp(20.152f),
                            WorldTourBorderColor,
                            panelShape,
                        )
                        .padding(
                            horizontal = metrics.dp(52f),
                            vertical = metrics.dp(55f),
                        ),
                    verticalArrangement = Arrangement.spacedBy(metrics.dp(31f)),
                ) {
                    if (regions.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .requiredHeight(metrics.dp(124f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No regions discovered yet",
                                color = WorldTourTextColor.copy(alpha = 0.4f),
                                fontFamily = Rubik,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = metrics.sp(45f),
                            )
                        }
                    }
                    regions.forEachIndexed { index, region ->
                        if (index > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .requiredHeight(metrics.dp(9f))
                                    .clip(RoundedCornerShape(metrics.dp(100f)))
                                    .background(WorldTourTextColor.copy(alpha = 0.13f)),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .requiredHeight(metrics.dp(110f))
                                .testTag("world_tour_region_${region.countryCode}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(metrics.dp(40f)),
                        ) {
                            Text(
                                text = CountryCatalog.flagEmoji(region.countryCode),
                                fontSize = metrics.sp(80f),
                                maxLines = 1,
                            )
                            Text(
                                text = CountryCatalog.countries
                                    .firstOrNull { it.code == region.countryCode }
                                    ?.name
                                    ?: region.countryCode,
                                modifier = Modifier.weight(1f),
                                color = WorldTourTextColor,
                                fontFamily = Rubik,
                                fontWeight = FontWeight.Bold,
                                fontSize = metrics.sp(64f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = dateFormatter.format(
                                    region.firstMetAt.toJavaInstant().atZone(ZoneId.systemDefault()),
                                ),
                                color = WorldTourTextColor.copy(alpha = 0.55f),
                                fontFamily = Rubik,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = metrics.sp(45f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
