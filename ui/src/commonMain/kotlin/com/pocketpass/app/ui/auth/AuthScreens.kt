package com.pocketpass.app.ui.auth

import com.pocketpass.app.ui.PocketAsset
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.requiresLegacyLocationPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pocketpass.app.auth.AuthEvent
import com.pocketpass.app.auth.AuthStep
import com.pocketpass.app.auth.AuthUiError
import com.pocketpass.app.auth.AuthUiState
import com.pocketpass.app.auth.filterPocketPassOtp
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.model.StatusInfo
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.BOTTOM_DESIGN_HEIGHT
import com.pocketpass.app.ui.BOTTOM_DESIGN_WIDTH
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.TOP_DESIGN_HEIGHT
import com.pocketpass.app.ui.TOP_DESIGN_WIDTH
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.POCKET_KEYBOARD_HEIGHT
import com.pocketpass.app.ui.components.PatternBackground
import com.pocketpass.app.ui.components.PocketKey
import com.pocketpass.app.ui.components.PocketKeyboard
import com.pocketpass.app.ui.components.PocketKeyboardLayout
import com.pocketpass.app.ui.components.PocketPanel
import com.pocketpass.app.ui.components.StatusPills
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.theme.pocketPalette
import com.pocketpass.app.model.PocketPassDestination
import kotlinx.coroutines.delay
import kotlin.math.min

private const val AUTH_EMAIL_KEYBOARD_LIFT = 60f

private const val AUTH_OTP_KEYBOARD_LIFT = 50f

val PocketTeal = Color(0xFF1D596B)
val PocketGreenText = Color(0xFF26706A)
val PocketBorder = Color(0xFF5A96A9)
val PocketGreenBorder = Color(0xFF55C24B)
val PocketGreenButton = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to Color(0xFF5CE257),
        0.50f to Color(0xFF5EED60),
        0.55f to Color(0xFF57E257),
        1f to Color(0xFF29BC2B),
    ),
)
private val PocketDiscordButton = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to Color(0xFF5765E2),
        0.52f to Color(0xFF5E63ED),
        1f to Color(0xFF2935BC),
    ),
)
val PocketWhitePanel: Brush
    @Composable
    get() = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to pocketPalette.surface,
            0.68f to pocketPalette.surface,
            1f to pocketPalette.tint(Color(0xFFBDF8CB)),
        ),
    )

@Composable
fun AuthTopScreen(
    metrics: DesignMetrics,
    status: StatusInfo,
) {
    PatternBackground(
        metrics = metrics,
        pattern = Assets.PatternHomeTop,
        topColor = pocketPalette.background(PocketPassDestination.Home, top = true).top,
        bottomColor = pocketPalette.background(PocketPassDestination.Home, top = true).bottom,
        holdFraction = 0.5f,
        designWidth = TOP_DESIGN_WIDTH,
        designHeight = TOP_DESIGN_HEIGHT,
    )
    FigmaAsset(
        resource = Assets.AuthLeaf,
        modifier = Modifier.designBounds(metrics, 734f, 232f, 452.1f, 530f),
    )
    Text(
        text = "PocketPass",
        modifier = Modifier.designBounds(metrics, 510f, 828f, 900f, 152f),
        style = pocketAuthText(metrics, 128f, PocketTeal, FontWeight.Bold),
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
    )
    StatusPills(metrics, status)
}

@Composable
fun AuthBottomScreen(
    metrics: DesignMetrics,
    sessionState: SessionState,
    state: AuthUiState,
    dispatch: (AuthEvent) -> Unit,
) {
    PatternBackground(
        metrics = metrics,
        pattern = Assets.PatternHomeBottom,
        topColor = pocketPalette.background(PocketPassDestination.Home, top = false).top,
        bottomColor = pocketPalette.background(PocketPassDestination.Home, top = false).bottom,
        holdFraction = 0.4375f,
        designWidth = BOTTOM_DESIGN_WIDTH,
        designHeight = BOTTOM_DESIGN_HEIGHT,
    )

    when (sessionState) {
        SessionState.Initializing -> AuthStatusPanel(
            metrics = metrics,
            title = "PocketPass",
            subtitle = "Starting securely…",
        )

        is SessionState.ConfigurationError -> AuthStatusPanel(
            metrics = metrics,
            title = "Sign-in unavailable",
            subtitle = "PocketPass couldn't start sign-in.",
            buttonLabel = "Retry",
            onButton = { dispatch(AuthEvent.RetryInitialization) },
            error = state.error,
        )

        else -> when (state.step) {
            AuthStep.Landing -> AuthLanding(metrics, state, dispatch)
            AuthStep.Email -> AuthEmail(metrics, state, dispatch)
            AuthStep.Otp -> AuthOtp(metrics, state, dispatch)
        }
    }
}

@Composable
fun NearbyPermissionBottomScreen(
    metrics: DesignMetrics,
    isRepair: Boolean,
    error: String?,
    onContinue: () -> Unit,
) {
    PatternBackground(
        metrics = metrics,
        pattern = Assets.PatternHomeBottom,
        topColor = pocketPalette.background(PocketPassDestination.Home, top = false).top,
        bottomColor = pocketPalette.background(PocketPassDestination.Home, top = false).bottom,
        holdFraction = 0.4375f,
        designWidth = BOTTOM_DESIGN_WIDTH,
        designHeight = BOTTOM_DESIGN_HEIGHT,
    )
    val legacyLocation = requiresLegacyLocationPermission()
    PocketPanel(
        metrics = metrics,
        x = 70f,
        y = 62f,
        width = 1100f,
        height = PERMISSION_PANEL_HEIGHT,
        borderColor = PocketBorder,
        borderWidth = 20.152f,
        radius = 118f,
        fillBrush = PocketWhitePanel,
        shadowAlpha = 0.12f,
        shadowOffset = 14f,
    ) {
        Text(
            text = if (isRepair) "Restore Nearby Encounters" else "Meet people nearby",
            modifier = Modifier.designBounds(metrics, 70f, 58f, 960f, 80f),
            style = pocketAuthText(metrics, 54f, PocketTeal, FontWeight.Bold),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = "PocketPass uses Bluetooth to notice other players around you and " +
                "swap a quick, anonymous pass. Your profile is never broadcast and " +
                "every exchange is encrypted.",
            modifier = Modifier.designBounds(metrics, 110f, 152f, 880f, 170f),
            style = pocketAuthText(metrics, 31f, PocketGreenText, FontWeight.Medium),
            textAlign = TextAlign.Center,
        )
        PermissionRow(
            metrics = metrics,
            y = 358f,
            icon = Assets.SettingsNearby,
            title = "Nearby devices",
            detail = "Lets PocketPass find players around you and trade passes.",
        )
        PermissionRow(
            metrics = metrics,
            y = 526f,
            icon = if (legacyLocation) Assets.SettingsEncounterLed else Assets.SettingsNotifications,
            title = if (legacyLocation) "Location" else "Notifications",
            detail = if (legacyLocation) {
                "Android 11 needs “Allow all the time” so passes work with the screen off."
            } else {
                "Shows that Nearby is running and tells you when you meet someone."
            },
        )
        if (error != null) {
            Text(
                text = error,
                modifier = Modifier.designBounds(metrics, 86f, 664f, 928f, 70f),
                style = pocketAuthText(
                    metrics,
                    27f,
                    Color(0xFF9B3434),
                    FontWeight.SemiBold,
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
    AuthButton(
        metrics = metrics,
        y = 62f + PERMISSION_PANEL_HEIGHT + 56f,
        label = if (isRepair) "Fix Permissions" else "Allow Permissions",
        tag = "nearby_permission_continue",
        onClick = onContinue,
        x = 142f,
        width = 956f,
        height = 146f,
    )
}

private const val PERMISSION_PANEL_HEIGHT = 740f

@Composable
private fun PermissionRow(
    metrics: DesignMetrics,
    y: Float,
    icon: PocketAsset,
    title: String,
    detail: String,
) {
    FigmaAsset(
        resource = icon,
        modifier = Modifier.designBounds(metrics, 100f, y, 124f, 124f),
    )
    Text(
        text = title,
        modifier = Modifier.designBounds(metrics, 254f, y + 6f, 760f, 52f),
        style = pocketAuthText(metrics, 38f, PocketTeal, FontWeight.Bold),
        maxLines = 1,
    )
    Text(
        text = detail,
        modifier = Modifier.designBounds(metrics, 254f, y + 60f, 760f, 70f),
        style = pocketAuthText(metrics, 27f, PocketGreenText, FontWeight.Medium),
        maxLines = 2,
    )
}

@Composable
private fun AuthLanding(
    metrics: DesignMetrics,
    state: AuthUiState,
    dispatch: (AuthEvent) -> Unit,
) {
    AuthHeader(
        metrics = metrics,
        title = "Welcome!",
        subtitle = "Continue with email or Discord.",
    )
    AuthButton(
        metrics = metrics,
        y = 461f,
        label = "Continue with Email",
        tag = "auth_continue_email",
        onClick = { dispatch(AuthEvent.ContinueWithEmail) },
    )
    AuthButton(
        metrics = metrics,
        y = 679f,
        label = "Continue with Discord",
        borderColor = Color(0xFF4D4BC2),
        brush = PocketDiscordButton,
        tag = "auth_continue_discord",
        enabled = !state.isSubmitting,
        onClick = { dispatch(AuthEvent.ContinueWithDiscord) },
    )
    AuthError(metrics, state.error)
}

@Composable
private fun AuthEmail(
    metrics: DesignMetrics,
    state: AuthUiState,
    dispatch: (AuthEvent) -> Unit,
) {
    var keyboardVisible by remember { mutableStateOf(false) }
    val keyboardProgress by animateFloatAsState(
        targetValue = if (keyboardVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "authEmailKeyboard",
    )
    val submitEmail = {
        keyboardVisible = false
        dispatch(AuthEvent.SubmitEmail)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = -AUTH_EMAIL_KEYBOARD_LIFT * keyboardProgress },
    ) {
        AuthHeader(metrics, "Continue", "Via Email")
        PocketPanel(
            metrics = metrics,
            x = 102f,
            y = 461f,
            width = 1036f,
            height = 166f,
            borderColor = PocketBorder,
            borderWidth = 18f,
            radius = 118f,
            fillBrush = PocketWhitePanel,
            tag = "auth_email_input",
            onClick = { keyboardVisible = !keyboardVisible },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = metrics.dp(52f),
                        vertical = metrics.dp(34f),
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = state.email.ifEmpty { "you@example.com" },
                    style = pocketAuthText(
                        metrics,
                        55f,
                        if (state.email.isEmpty()) {
                            PocketGreenText.copy(alpha = 0.56f)
                        } else {
                            PocketGreenText
                        },
                        FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }
        }

        val actionsShown = remember { derivedStateOf { keyboardProgress < 0.999f } }
        if (actionsShown.value) {
            Box(
                Modifier.graphicsLayer {
                    alpha = 1f - keyboardProgress
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
            ) {
                AuthButton(
                    metrics = metrics,
                    y = 679f,
                    label = if (state.isSubmitting) "Sending code…" else "Continue",
                    tag = "auth_submit_email",
                    enabled = state.canContinueWithEmail,
                    onClick = submitEmail,
                )
                AuthTextAction(
                    metrics = metrics,
                    x = 520f,
                    y = 920f,
                    width = 200f,
                    height = 100f,
                    label = "Back",
                    tag = "auth_email_back",
                    onClick = { dispatch(AuthEvent.Back) },
                )
                AuthError(metrics, state.error)
            }
        }
    }

    val keyboardShown = remember { derivedStateOf { keyboardProgress > 0.001f } }
    if (keyboardShown.value) {
        PocketKeyboard(
            metrics = metrics,
            layout = PocketKeyboardLayout.Email,
            submitLabel = "Continue",
            submitEnabled = state.canContinueWithEmail,
            onKey = { key ->
                when (key) {
                    is PocketKey.Character ->
                        dispatch(AuthEvent.EmailChanged(state.email + key.value))

                    PocketKey.Space, PocketKey.Alphabet -> Unit
                    PocketKey.Backspace ->
                        dispatch(AuthEvent.EmailChanged(state.email.dropLast(1)))

                    PocketKey.Submit -> if (state.canContinueWithEmail) submitEmail()
                }
            },
            modifier = Modifier.graphicsLayer {
                translationY = (1f - keyboardProgress) * POCKET_KEYBOARD_HEIGHT
            },
            focusReturnTag = "auth_email_input",
        )
    }
}

@Composable
private fun AuthOtp(
    metrics: DesignMetrics,
    state: AuthUiState,
    dispatch: (AuthEvent) -> Unit,
) {
    var previousLength by remember { mutableIntStateOf(state.otpCode.length) }
    var pulseIndex by remember { mutableIntStateOf(-1) }
    val shake = remember { Animatable(0f) }
    var keyboardVisible by remember { mutableStateOf(false) }
    val keyboardProgress by animateFloatAsState(
        targetValue = if (keyboardVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "authOtpKeyboard",
    )
    val verifyOtp = {
        keyboardVisible = false
        dispatch(AuthEvent.VerifyOtp)
    }

    LaunchedEffect(state.otpCode) {
        if (state.otpCode.length > previousLength) {
            pulseIndex = state.otpCode.lastIndex
            delay(105)
            pulseIndex = -1
        }
        previousLength = state.otpCode.length
    }
    LaunchedEffect(state.errorShakeNonce) {
        if (state.errorShakeNonce > 0) {
            shake.snapTo(0f)
            listOf(-11f, 9f, -6f, 4f, 0f).forEach { target ->
                shake.animateTo(target, tween(48))
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = -AUTH_OTP_KEYBOARD_LIFT * keyboardProgress },
    ) {
        Text(
            text = "Check your email",
            modifier = Modifier.designBounds(metrics, 50f, 162f, 1140f, 97f),
            style = pocketAuthText(metrics, 82f, PocketTeal, FontWeight.Bold),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = "Enter the 6-digit code sent to",
            modifier = Modifier.designBounds(metrics, 70f, 267f, 1100f, 50f),
            style = pocketAuthText(metrics, 42f, PocketGreenText, FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        val emailAutoSize = remember(metrics) {
            TextAutoSize.StepBased(
                minFontSize = metrics.sp(24f),
                maxFontSize = metrics.sp(38f),
                stepSize = metrics.sp(1f),
            )
        }
        Text(
            text = state.normalizedEmail,
            modifier = Modifier.designBounds(metrics, 50f, 323f, 1140f, 76f),
            overflow = TextOverflow.Ellipsis,
            style = pocketAuthText(
                metrics,
                38f,
                PocketGreenText.copy(alpha = 0.68f),
                FontWeight.Medium,
            ),
            textAlign = TextAlign.Center,
            maxLines = 2,
            autoSize = emailAutoSize,
        )

        Box(
            modifier = Modifier
                .designBounds(metrics, 77f, 404f, 1086f, 166f)
                .graphicsLayer { translationX = shake.value }
                .testTag("auth_otp_input")
                .controllerTarget("auth_otp_input", cornerRadius = 40f) {
                    keyboardVisible = !keyboardVisible
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { keyboardVisible = !keyboardVisible },
        ) {
            repeat(6) { index ->
                val digit = state.otpCode.getOrNull(index)?.toString().orEmpty()
                val active = index == min(state.otpCode.length, 5)
                OtpSlot(
                    metrics = metrics,
                    x = index * 184f,
                    digit = digit,
                    active = active,
                    pulse = pulseIndex == index,
                )
            }
        }

        val resendLabel = if (state.resendSecondsRemaining > 0) {
            "Resend code in ${state.resendSecondsRemaining}s"
        } else {
            "Resend code"
        }
        AuthTextAction(
            metrics = metrics,
            x = 350f,
            y = 570f,
            width = 540f,
            height = 70f,
            label = resendLabel,
            fontSize = 38f,
            color = PocketGreenText.copy(alpha = if (state.canResend) 1f else 0.72f),
            tag = "auth_resend",
            enabled = state.canResend,
            onClick = { dispatch(AuthEvent.ResendOtp) },
        )

        val actionsShown = remember { derivedStateOf { keyboardProgress < 0.999f } }
        if (actionsShown.value) {
            Box(
                Modifier.graphicsLayer {
                    alpha = 1f - keyboardProgress
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
            ) {
                AuthButton(
                    metrics = metrics,
                    y = 671f,
                    label = if (state.isSubmitting) "Verifying…" else "Verify",
                    tag = "auth_verify",
                    enabled = state.canVerify,
                    onClick = verifyOtp,
                )
                AuthTextAction(
                    metrics = metrics,
                    x = 400f,
                    y = 895f,
                    width = 440f,
                    height = 100f,
                    label = "Change Email",
                    fontSize = 40f,
                    tag = "auth_change_email",
                    enabled = !state.isSubmitting,
                    onClick = { dispatch(AuthEvent.ChangeEmail) },
                )
                AuthError(metrics, state.error)
            }
        }
    }

    val keyboardShown = remember { derivedStateOf { keyboardProgress > 0.001f } }
    if (keyboardShown.value) {
        PocketKeyboard(
            metrics = metrics,
            layout = PocketKeyboardLayout.Numeric,
            submitLabel = "Verify",
            submitEnabled = state.canVerify,
            onKey = { key ->
                when (key) {
                    is PocketKey.Character -> dispatch(
                        AuthEvent.OtpChanged(
                            filterPocketPassOtp(state.otpCode + key.value),
                        ),
                    )

                    PocketKey.Space, PocketKey.Alphabet -> Unit
                    PocketKey.Backspace ->
                        dispatch(AuthEvent.OtpChanged(state.otpCode.dropLast(1)))

                    PocketKey.Submit -> if (state.canVerify) verifyOtp()
                }
            },
            modifier = Modifier.graphicsLayer {
                translationY = (1f - keyboardProgress) * POCKET_KEYBOARD_HEIGHT
            },
            focusReturnTag = "auth_otp_input",
        )
    }
}

@Composable
private fun OtpSlot(
    metrics: DesignMetrics,
    x: Float,
    digit: String,
    active: Boolean,
    pulse: Boolean,
) {
    val scale by animateFloatAsState(
        targetValue = if (pulse) 1.035f else 1f,
        animationSpec = tween(90),
        label = "otpDigit",
    )
    PocketPanel(
        metrics = metrics,
        x = x,
        y = 0f,
        width = 166f,
        height = 154f,
        borderColor = when {
            digit.isNotEmpty() -> PocketGreenBorder
            active -> PocketBorder
            else -> PocketBorder.copy(alpha = 0.62f)
        },
        borderWidth = if (active && digit.isEmpty()) 14f else 12f,
        radius = 46f,
        fillBrush = PocketWhitePanel,
        shadowAlpha = 0.14f,
        shadowOffset = 12f,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = digit,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
                style = pocketAuthText(
                    metrics,
                    70f,
                    if (active) PocketTeal else PocketGreenText,
                    FontWeight.Bold,
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun AuthHeader(
    metrics: DesignMetrics,
    title: String,
    subtitle: String,
) {
    Text(
        text = title,
        modifier = Modifier.designBounds(metrics, 300f, 223f, 640f, 114f),
        style = pocketAuthText(metrics, 96f, PocketTeal, FontWeight.Bold),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
    Text(
        text = subtitle,
        modifier = Modifier.designBounds(metrics, 145f, 356f, 950f, 65f),
        style = pocketAuthText(metrics, 55f, PocketGreenText, FontWeight.SemiBold),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun AuthStatusPanel(
    metrics: DesignMetrics,
    title: String,
    subtitle: String,
    buttonLabel: String? = null,
    onButton: (() -> Unit)? = null,
    error: AuthUiError? = null,
) {
    Text(
        title,
        Modifier.designBounds(metrics, 120f, 300f, 1000f, 120f),
        style = pocketAuthText(metrics, 82f, PocketTeal, FontWeight.Bold),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
    Text(
        error?.message ?: subtitle,
        Modifier.designBounds(metrics, 120f, 455f, 1000f, 120f),
        style = pocketAuthText(
            metrics,
            42f,
            if (error == null) PocketGreenText else Color(0xFF9B3434),
            FontWeight.SemiBold,
        ),
        textAlign = TextAlign.Center,
        maxLines = 2,
    )
    if (error != null) {
        Text(
            error.code,
            Modifier.designBounds(metrics, 120f, 580f, 1000f, 42f),
            style = pocketAuthText(
                metrics,
                27f,
                PocketGreenText.copy(alpha = 0.62f),
                FontWeight.Medium,
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
    if (buttonLabel != null && onButton != null) {
        AuthButton(
            metrics = metrics,
            y = 650f,
            label = buttonLabel,
            tag = "auth_retry_initialization",
            onClick = onButton,
        )
    }
}

@Composable
internal fun AuthButton(
    metrics: DesignMetrics,
    y: Float,
    label: String,
    tag: String,
    onClick: () -> Unit,
    x: Float = 102f,
    width: Float = 1036f,
    height: Float = 166f,
    borderColor: Color = PocketGreenBorder,
    brush: Brush = PocketGreenButton,
    textColor: Color = Color.White,
    shadowAlpha: Float = 0.11f,
    shadowOffset: Float = 12f,
    enabled: Boolean = true,
) {
    Box(
        Modifier.graphicsLayer {
            alpha = if (enabled) 1f else 0.58f
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
    ) {
        PocketPanel(
            metrics = metrics,
            x = x,
            y = y,
            width = width,
            height = height,
            borderColor = borderColor,
            borderWidth = 20.152f,
            radius = 118f,
            fillBrush = brush,
            shadowAlpha = shadowAlpha,
            shadowOffset = shadowOffset,
            tag = tag,
            onClick = if (enabled) onClick else null,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = pocketAuthText(
                        metrics,
                        48f,
                        textColor,
                        FontWeight.SemiBold,
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun AuthTextAction(
    metrics: DesignMetrics,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    label: String,
    tag: String,
    onClick: () -> Unit,
    fontSize: Float = 42f,
    color: Color = PocketGreenText,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .designBounds(metrics, x, y, width, height)
            .clip(RoundedCornerShape(metrics.dp(50f)))
            .testTag(tag)
            .controllerTarget(tag, cornerRadius = 50f) { if (enabled) onClick() }
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = pocketAuthText(
                metrics,
                fontSize,
                color,
                FontWeight.SemiBold,
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun AuthError(
    metrics: DesignMetrics,
    error: AuthUiError?,
) {
    if (error == null) return
    Text(
        text = error.message,
        modifier = Modifier.designBounds(metrics, 110f, 968f, 1020f, 48f),
        style = pocketAuthText(metrics, 30f, Color(0xFF9B3434), FontWeight.SemiBold),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
    Text(
        text = error.code,
        modifier = Modifier.designBounds(metrics, 110f, 1022f, 1020f, 36f),
        style = pocketAuthText(
            metrics,
            24f,
            PocketGreenText.copy(alpha = 0.58f),
            FontWeight.Medium,
        ),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
internal fun pocketAuthText(
    metrics: DesignMetrics,
    size: Float,
    color: Color,
    weight: FontWeight,
): TextStyle = TextStyle(
    fontFamily = Rubik,
    fontWeight = weight,
    fontSize = metrics.sp(size),
    color = pocketPalette.ink(color),
)
