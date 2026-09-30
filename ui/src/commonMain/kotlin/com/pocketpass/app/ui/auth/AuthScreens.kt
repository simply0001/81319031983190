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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.asksToRunInBackground
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
import com.pocketpass.app.auth.AuthIntent
import com.pocketpass.app.auth.AuthStep
import com.pocketpass.app.auth.AuthUiError
import com.pocketpass.app.auth.AuthUiState
import com.pocketpass.app.auth.FORGOT_PASSWORD_MESSAGE
import com.pocketpass.app.auth.NO_PASSWORD_RESET_MESSAGE
import com.pocketpass.app.auth.filterPocketPassOtp
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.model.StatusInfo
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.BOTTOM_DESIGN_HEIGHT
import com.pocketpass.app.ui.BOTTOM_DESIGN_WIDTH
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.DesignAnchor
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
import com.pocketpass.app.ui.controller.FocusDirection
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.anchoredBounds
import com.pocketpass.app.ui.theme.pocketPalette
import com.pocketpass.app.model.PocketPassDestination
import kotlinx.coroutines.delay
import kotlin.math.min

internal const val AUTH_PRIMARY_SHADOW = 0.17f
private const val AUTH_FIELD_SHADOW = 0.18f
private const val AUTH_SECONDARY_SHADOW = 0.3f
private const val AUTH_FIELD_X = 50f
private const val AUTH_FIELD_WIDTH = 1140f
private const val AUTH_FIELD_HEIGHT = 166f
private const val AUTH_ROW_HEIGHT = 164f
private const val AUTH_HALF_WIDTH = 542f
private const val AUTH_SECOND_COLUMN_X = 648f
private const val AUTH_HEADER_HEIGHT = 65f
private const val AUTH_KEYBOARD_TOP = BOTTOM_DESIGN_HEIGHT - POCKET_KEYBOARD_HEIGHT
private const val AUTH_KEYBOARD_MARGIN = 30f

private fun keyboardLift(fieldBottom: Float): Float =
    (fieldBottom + AUTH_KEYBOARD_MARGIN - AUTH_KEYBOARD_TOP).coerceAtLeast(0f)

val PocketTeal = Color(0xFF1D596B)
val PocketGreenText = Color(0xFF26706A)
val PocketBorder = Color(0xFF5A96A9)
val PocketGreenBorder = Color(0xFF55C24B)
internal val PocketDiscordBorder = Color(0xFF4D4BC2)
private val AuthErrorRed = Color(0xFF9B3434)
private val AuthSecondaryBorder = Color(0xFF9F9F9F)
private val AuthSecondaryText = Color(0xFF5B5B5B)
val PocketGreenButton = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to Color(0xFF5CE257),
        0.50f to Color(0xFF5EED60),
        0.55f to Color(0xFF57E257),
        1f to Color(0xFF29BC2B),
    ),
)
internal val PocketDiscordButton = Brush.verticalGradient(
    colorStops = arrayOf(
        0.19f to Color(0xFF5765E2),
        0.51f to Color(0xFF5E63ED),
        0.55f to Color(0xFF575BE2),
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

private val AuthSecondaryFill: Brush
    @Composable
    get() = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to pocketPalette.surface,
            0.626f to pocketPalette.surface,
            1f to pocketPalette.tint(Color(0xFFC6C6C6)),
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
            AuthStep.Landing -> AuthLanding(metrics, dispatch)
            AuthStep.Method -> AuthMethod(metrics, state, dispatch)
            AuthStep.Email -> AuthEmail(metrics, state, dispatch)
            AuthStep.Otp -> AuthOtp(metrics, state, dispatch)
            AuthStep.Credentials -> AuthCredentials(metrics, state, dispatch)
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
    val asksBackground = asksToRunInBackground()
    val panelY = if (asksBackground) 40f else 62f
    val panelHeight = if (asksBackground) 838f else PERMISSION_PANEL_HEIGHT
    val firstRowY = if (asksBackground) 330f else 358f
    val rowPitch = if (asksBackground) 146f else 168f
    val errorY = if (asksBackground) 756f else 664f
    PocketPanel(
        metrics = metrics,
        x = 70f,
        y = panelY,
        width = 1100f,
        height = panelHeight,
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
            y = firstRowY,
            icon = Assets.SettingsNearby,
            title = "Nearby devices",
            detail = "Lets PocketPass find players around you and trade passes.",
        )
        PermissionRow(
            metrics = metrics,
            y = firstRowY + rowPitch,
            icon = if (legacyLocation) Assets.SettingsEncounterLed else Assets.SettingsNotifications,
            title = if (legacyLocation) "Location" else "Notifications",
            detail = if (legacyLocation) {
                "Android 11 needs “Allow all the time” so passes work with the screen off."
            } else {
                "Shows that Nearby is running and tells you when you meet someone."
            },
        )
        if (asksBackground) {
            PermissionRow(
                metrics = metrics,
                y = firstRowY + rowPitch * 2,
                icon = Assets.SettingsBackgroundRun,
                title = "Run in background",
                detail = "Keeps Nearby and step counting going with the screen off. Android asks once.",
            )
        }
        if (error != null) {
            Text(
                text = error,
                modifier = Modifier.designBounds(metrics, 86f, errorY, 928f, 70f),
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
        y = panelY + panelHeight + if (asksBackground) 36f else 56f,
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
    dispatch: (AuthEvent) -> Unit,
) {
    AuthHeader(metrics, "Welcome!", "Please login to use PocketPass.")
    AuthButton(
        metrics = metrics,
        y = 461f,
        label = "Login",
        tag = "auth_choose_sign_in",
        shadowAlpha = AUTH_PRIMARY_SHADOW,
        onClick = { dispatch(AuthEvent.ChooseSignIn) },
    )
    AuthSecondaryButton(
        metrics = metrics,
        y = 679f,
        label = "Sign Up",
        tag = "auth_choose_sign_up",
        onClick = { dispatch(AuthEvent.ChooseSignUp) },
    )
}

@Composable
private fun AuthMethod(
    metrics: DesignMetrics,
    state: AuthUiState,
    dispatch: (AuthEvent) -> Unit,
) {
    val signUp = state.intent == AuthIntent.SignUp
    AuthHeader(
        metrics = metrics,
        title = if (signUp) "Sign Up Method" else "Login Method",
        subtitle = state.error?.message ?: "How should we handle your profile?",
        titleY = 114f,
        subtitleY = 247f,
        error = state.error != null,
    )
    AuthButton(
        metrics = metrics,
        y = 352f,
        label = "Email",
        tag = "auth_continue_email",
        shadowAlpha = AUTH_PRIMARY_SHADOW,
        enabled = !state.isSubmitting,
        onClick = { dispatch(AuthEvent.ContinueWithEmail) },
    )
    AuthButton(
        metrics = metrics,
        y = 570f,
        label = "Discord",
        borderColor = PocketDiscordBorder,
        brush = PocketDiscordButton,
        shadowAlpha = AUTH_PRIMARY_SHADOW,
        tag = "auth_continue_discord",
        enabled = !state.isSubmitting,
        onClick = { dispatch(AuthEvent.ContinueWithDiscord) },
    )
    AuthSecondaryButton(
        metrics = metrics,
        y = 788f,
        label = "Username",
        tag = "auth_continue_credentials",
        enabled = !state.isSubmitting,
        onClick = { dispatch(AuthEvent.ContinueWithCredentials) },
    )
    AuthTextAction(
        metrics = metrics,
        x = 420f,
        y = 976f,
        width = 400f,
        height = 60f,
        label = "Back",
        tag = "auth_method_back",
        fontSize = 36f,
        color = PocketGreenText.copy(alpha = 0.65f),
        enabled = !state.isSubmitting,
        onClick = { dispatch(AuthEvent.Back) },
    )
    state.error?.let { error ->
        Text(
            text = error.code,
            modifier = Modifier.designBounds(metrics, 50f, 1040f, 1140f, 32f),
            style = pocketAuthText(metrics, 22f, PocketGreenText.copy(alpha = 0.58f), FontWeight.Medium),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private enum class CredentialField {
    Identifier,
    Password,
    Repeat,
}

@Composable
private fun AuthCredentials(
    metrics: DesignMetrics,
    state: AuthUiState,
    dispatch: (AuthEvent) -> Unit,
) {
    var focused by remember { mutableStateOf<CredentialField?>(null) }
    var forgotShown by remember { mutableStateOf(false) }
    val creating = state.isCreatingAccount
    val keyboardProgress by animateFloatAsState(
        targetValue = if (focused != null) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "authCredentialsKeyboard",
    )
    val fieldHeight = if (creating) 146f else AUTH_FIELD_HEIGHT
    val headerY = if (creating) 142.5f else 184.5f
    val identifierY = if (creating) 247.5f else 299.5f
    val pitch = if (creating) 170f else 216f
    val passwordY = identifierY + pitch
    val repeatY = identifierY + 2 * pitch
    val rowY = if (creating) 773.5f else 731.5f
    val noteY = if (creating) 955f else 920f
    val lift = when (focused) {
        CredentialField.Identifier -> keyboardLift(identifierY + fieldHeight)
        CredentialField.Password -> keyboardLift(passwordY + fieldHeight)
        CredentialField.Repeat -> keyboardLift(repeatY + fieldHeight)
        null -> 0f
    }
    val submit = {
        focused = null
        dispatch(AuthEvent.SubmitCredentials)
    }
    val masked = !state.showPassword

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = -lift * keyboardProgress },
    ) {
        AuthFormHeader(
            metrics = metrics,
            y = headerY,
            title = if (creating) "Sign up via Username" else "Login via Username",
            action = "Back",
            actionTag = "auth_credentials_back",
            onAction = {
                focused = null
                dispatch(AuthEvent.Back)
            },
            firstFieldTag = "auth_username_input",
        )
        AuthField(
            metrics = metrics,
            y = identifierY,
            height = fieldHeight,
            value = state.identifier,
            placeholder = if (creating) "username" else "username or email",
            tag = "auth_username_input",
            active = focused == CredentialField.Identifier,
            onClick = { focused = CredentialField.Identifier },
        )
        AuthField(
            metrics = metrics,
            y = passwordY,
            height = fieldHeight,
            value = state.password,
            placeholder = "password",
            tag = "auth_password_input",
            active = focused == CredentialField.Password,
            onClick = { focused = CredentialField.Password },
            masked = masked,
            trailingSpace = 150f,
        )
        CredentialChip(
            metrics = metrics,
            x = 1004f,
            y = passwordY + (fieldHeight - 66f) / 2f,
            label = if (masked) "Show" else "Hide",
            tag = "auth_show_password",
            onClick = { dispatch(AuthEvent.TogglePasswordVisibility) },
            horizontal = DesignAnchor.End,
        )
        if (creating) {
            AuthField(
                metrics = metrics,
                y = repeatY,
                height = fieldHeight,
                value = state.passwordRepeat,
                placeholder = "repeat password",
                tag = "auth_password_repeat_input",
                active = focused == CredentialField.Repeat,
                onClick = { focused = CredentialField.Repeat },
                masked = masked,
            )
        }

        val actionsShown = remember { derivedStateOf { keyboardProgress < 0.999f } }
        if (actionsShown.value) {
            Box(
                Modifier.graphicsLayer {
                    alpha = 1f - keyboardProgress
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
            ) {
                AuthConfirmButton(
                    metrics = metrics,
                    y = rowY,
                    tag = "auth_submit_credentials",
                    enabled = state.canSubmitCredentials,
                    onClick = submit,
                )
                if (creating) {
                    AuthSecondaryButton(
                        metrics = metrics,
                        x = AUTH_SECOND_COLUMN_X,
                        y = rowY,
                        width = AUTH_HALF_WIDTH,
                        height = AUTH_ROW_HEIGHT,
                        horizontal = DesignAnchor.End,
                        label = "I have an account",
                        tag = "auth_toggle_credentials_mode",
                        fontSize = 40f,
                        enabled = !state.isSubmitting,
                        onClick = {
                            focused = null
                            dispatch(AuthEvent.ToggleCredentialsMode)
                        },
                    )
                } else {
                    AuthSecondaryButton(
                        metrics = metrics,
                        x = AUTH_SECOND_COLUMN_X,
                        y = rowY,
                        width = AUTH_HALF_WIDTH,
                        height = AUTH_ROW_HEIGHT,
                        horizontal = DesignAnchor.End,
                        label = "Forgot Password?",
                        tag = "auth_forgot_password",
                        onClick = { forgotShown = !forgotShown },
                    )
                }
                AuthFormNote(
                    metrics = metrics,
                    y = noteY,
                    error = state.error,
                    note = when {
                        state.isSubmitting && creating -> "Creating account…"
                        state.isSubmitting -> "Signing in…"
                        creating -> NO_PASSWORD_RESET_MESSAGE
                        forgotShown -> FORGOT_PASSWORD_MESSAGE
                        else -> null
                    },
                )
            }
        }
    }

    val keyboardShown = remember { derivedStateOf { keyboardProgress > 0.001f } }
    if (keyboardShown.value) {
        val field = focused
        val lastField = if (creating) CredentialField.Repeat else CredentialField.Password
        PocketKeyboard(
            metrics = metrics,
            layout = if (field == CredentialField.Identifier) PocketKeyboardLayout.Email else PocketKeyboardLayout.Text,
            submitLabel = when {
                field != lastField -> "Next"
                creating -> "Create"
                else -> "Sign in"
            },
            submitEnabled = field != lastField || state.canSubmitCredentials,
            onKey = { key ->
                when (field) {
                    CredentialField.Identifier -> applyCredentialKey(
                        current = state.identifier,
                        key = key,
                        allowSpace = false,
                        onChange = { dispatch(AuthEvent.IdentifierChanged(it)) },
                        onSubmit = { focused = CredentialField.Password },
                    )

                    CredentialField.Password -> applyCredentialKey(
                        current = state.password,
                        key = key,
                        allowSpace = true,
                        onChange = { dispatch(AuthEvent.PasswordChanged(it)) },
                        onSubmit = {
                            if (creating) {
                                focused = CredentialField.Repeat
                            } else if (state.canSubmitCredentials) {
                                submit()
                            }
                        },
                    )

                    CredentialField.Repeat -> applyCredentialKey(
                        current = state.passwordRepeat,
                        key = key,
                        allowSpace = true,
                        onChange = { dispatch(AuthEvent.PasswordRepeatChanged(it)) },
                        onSubmit = { if (state.canSubmitCredentials) submit() },
                    )

                    null -> Unit
                }
            },
            modifier = Modifier.graphicsLayer {
                translationY = (1f - keyboardProgress) * POCKET_KEYBOARD_HEIGHT
            },
            focusReturnTag = when (field) {
                CredentialField.Password -> "auth_password_input"
                CredentialField.Repeat -> "auth_password_repeat_input"
                else -> "auth_username_input"
            },
        )
    }
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
    val headerY = 292.5f
    val fieldY = 407.5f
    val rowY = 623.5f
    val noteY = 812f
    val lift = keyboardLift(fieldY + AUTH_FIELD_HEIGHT)
    val submitEmail = {
        keyboardVisible = false
        dispatch(AuthEvent.SubmitEmail)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = -lift * keyboardProgress },
    ) {
        AuthFormHeader(
            metrics = metrics,
            y = headerY,
            title = if (state.intent == AuthIntent.SignUp) "Sign up via Email" else "Login via Email",
            action = "Back",
            actionTag = "auth_email_back",
            actionEnabled = !state.isSubmitting,
            onAction = {
                keyboardVisible = false
                dispatch(AuthEvent.Back)
            },
            firstFieldTag = "auth_email_input",
        )
        AuthField(
            metrics = metrics,
            y = fieldY,
            value = state.email,
            placeholder = "example@hotmail.com",
            tag = "auth_email_input",
            active = keyboardVisible,
            onClick = { keyboardVisible = !keyboardVisible },
        )

        val actionsShown = remember { derivedStateOf { keyboardProgress < 0.999f } }
        if (actionsShown.value) {
            Box(
                Modifier.graphicsLayer {
                    alpha = 1f - keyboardProgress
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
            ) {
                AuthConfirmButton(
                    metrics = metrics,
                    y = rowY,
                    width = AUTH_FIELD_WIDTH,
                    tag = "auth_submit_email",
                    enabled = state.canContinueWithEmail,
                    onClick = submitEmail,
                )
                AuthFormNote(
                    metrics = metrics,
                    y = noteY,
                    error = state.error,
                    note = if (state.isSubmitting) "Sending code…" else null,
                )
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

                    PocketKey.Space, PocketKey.Alphabet, PocketKey.Emoji -> Unit
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
    val headerY = 268.5f
    val emailY = 353.5f
    val slotsY = 443.5f
    val rowY = 647.5f
    val noteY = 836f
    val lift = keyboardLift(slotsY + 154f)
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
            .graphicsLayer { translationY = -lift * keyboardProgress },
    ) {
        AuthFormHeader(
            metrics = metrics,
            y = headerY,
            title = "Check your email",
            action = "Change email",
            actionTag = "auth_change_email",
            actionEnabled = !state.isSubmitting,
            onAction = {
                keyboardVisible = false
                dispatch(AuthEvent.ChangeEmail)
            },
            firstFieldTag = "auth_otp_input",
        )
        val emailAutoSize = remember(metrics) {
            TextAutoSize.StepBased(
                minFontSize = metrics.sp(22f),
                maxFontSize = metrics.sp(32f),
                stepSize = metrics.sp(1f),
            )
        }
        Text(
            text = "Enter the 6-digit code sent to ${state.normalizedEmail}",
            modifier = Modifier.designBounds(metrics, AUTH_FIELD_X, emailY, AUTH_FIELD_WIDTH, 40f),
            overflow = TextOverflow.Ellipsis,
            style = pocketAuthText(
                metrics,
                32f,
                PocketGreenText.copy(alpha = 0.68f),
                FontWeight.Medium,
            ),
            maxLines = 1,
            autoSize = emailAutoSize,
        )

        Box(
            modifier = Modifier
                .designBounds(metrics, 77f, slotsY, 1086f, 154f)
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

        val actionsShown = remember { derivedStateOf { keyboardProgress < 0.999f } }
        if (actionsShown.value) {
            Box(
                Modifier.graphicsLayer {
                    alpha = 1f - keyboardProgress
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
            ) {
                AuthConfirmButton(
                    metrics = metrics,
                    y = rowY,
                    tag = "auth_verify",
                    enabled = state.canVerify,
                    onClick = verifyOtp,
                )
                AuthSecondaryButton(
                    metrics = metrics,
                    x = AUTH_SECOND_COLUMN_X,
                    y = rowY,
                    width = AUTH_HALF_WIDTH,
                    height = AUTH_ROW_HEIGHT,
                    horizontal = DesignAnchor.End,
                    label = if (state.resendSecondsRemaining > 0) {
                        "Resend in ${state.resendSecondsRemaining}s"
                    } else {
                        "Resend code"
                    },
                    tag = "auth_resend",
                    fontSize = 40f,
                    enabled = state.canResend,
                    onClick = { dispatch(AuthEvent.ResendOtp) },
                )
                AuthFormNote(
                    metrics = metrics,
                    y = noteY,
                    error = state.error,
                    note = if (state.isSubmitting) "Verifying…" else null,
                )
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

                    PocketKey.Space, PocketKey.Alphabet, PocketKey.Emoji -> Unit
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
private fun AuthFormHeader(
    metrics: DesignMetrics,
    y: Float,
    title: String,
    action: String,
    actionTag: String,
    onAction: () -> Unit,
    actionEnabled: Boolean = true,
    firstFieldTag: String? = null,
) {
    Text(
        text = title,
        modifier = Modifier.anchoredBounds(metrics, 61f, y, 700f, AUTH_HEADER_HEIGHT, DesignAnchor.Start, DesignAnchor.Center),
        style = pocketAuthText(metrics, 55f, PocketGreenText, FontWeight.SemiBold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier.anchoredBounds(
            metrics,
            700f,
            y - 14f,
            453f,
            AUTH_HEADER_HEIGHT + 28f,
            DesignAnchor.End,
            DesignAnchor.Center,
        ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(metrics.dp(46f)))
                .testTag(actionTag)
                .controllerTarget(
                    id = actionTag,
                    cornerRadius = 46f,
                    neighbors = if (firstFieldTag == null) {
                        emptyMap()
                    } else {
                        mapOf(
                            FocusDirection.Down to firstFieldTag,
                            FocusDirection.Left to firstFieldTag,
                        )
                    },
                ) { if (actionEnabled) onAction() }
                .then(
                    if (actionEnabled) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onAction,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = metrics.dp(24f), vertical = metrics.dp(8f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = action,
                style = pocketAuthText(
                    metrics,
                    55f,
                    PocketGreenText.copy(alpha = if (actionEnabled) 0.65f else 0.4f),
                    FontWeight.SemiBold,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AuthField(
    metrics: DesignMetrics,
    y: Float,
    value: String,
    placeholder: String,
    tag: String,
    active: Boolean,
    onClick: () -> Unit,
    height: Float = AUTH_FIELD_HEIGHT,
    masked: Boolean = false,
    trailingSpace: Float = 0f,
) {
    PocketPanel(
        metrics = metrics,
        x = AUTH_FIELD_X,
        y = y,
        width = AUTH_FIELD_WIDTH,
        height = height,
        borderColor = if (active) PocketGreenBorder else PocketBorder,
        borderWidth = 18f,
        radius = 200f,
        fillBrush = PocketWhitePanel,
        shadowAlpha = AUTH_FIELD_SHADOW,
        shadowOffset = 14f,
        tag = tag,
        onClick = onClick,
    ) {
        val tint = if (value.isEmpty()) PocketGreenText.copy(alpha = 0.56f) else PocketGreenText
        val shown = when {
            value.isEmpty() -> placeholder
            masked -> "•".repeat(value.length)
            else -> value
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = metrics.dp(52f), end = metrics.dp(52f + trailingSpace)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = shown,
                style = pocketAuthText(metrics, 55f, tint, FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AuthConfirmButton(
    metrics: DesignMetrics,
    y: Float,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    x: Float = AUTH_FIELD_X,
    width: Float = AUTH_HALF_WIDTH,
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
            height = AUTH_ROW_HEIGHT,
            borderColor = PocketGreenBorder,
            borderWidth = 20.152f,
            radius = 118f,
            fillBrush = PocketGreenButton,
            shadowAlpha = AUTH_PRIMARY_SHADOW,
            shadowOffset = 12f,
            tag = tag,
            onClick = onClick,
            enabled = enabled,
            horizontal = if (width < AUTH_FIELD_WIDTH) DesignAnchor.Start else DesignAnchor.Stretch,
        ) {
            FigmaAsset(
                resource = Assets.AuthCheck,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(metrics.dp(74f), metrics.dp(54f)),
            )
        }
    }
}

@Composable
internal fun AuthSecondaryButton(
    metrics: DesignMetrics,
    y: Float,
    label: String,
    tag: String,
    onClick: () -> Unit,
    x: Float = 102f,
    width: Float = 1036f,
    height: Float = 166f,
    enabled: Boolean = true,
    fontSize: Float = 48f,
    horizontal: DesignAnchor? = null,
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
            horizontal = horizontal,
            borderColor = AuthSecondaryBorder,
            borderWidth = 20.152f,
            radius = 118f,
            fillBrush = AuthSecondaryFill,
            shadowAlpha = AUTH_SECONDARY_SHADOW,
            shadowOffset = 14f,
            tag = tag,
            onClick = onClick,
            enabled = enabled,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = pocketAuthText(metrics, fontSize, AuthSecondaryText, FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AuthFormNote(
    metrics: DesignMetrics,
    y: Float,
    error: AuthUiError?,
    note: String?,
) {
    when {
        error != null -> {
            Text(
                text = error.message,
                modifier = Modifier.designBounds(metrics, AUTH_FIELD_X, y, AUTH_FIELD_WIDTH, 76f),
                style = pocketAuthText(metrics, 30f, AuthErrorRed, FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                text = error.code,
                modifier = Modifier.designBounds(metrics, AUTH_FIELD_X, y + 80f, AUTH_FIELD_WIDTH, 30f),
                style = pocketAuthText(metrics, 22f, PocketGreenText.copy(alpha = 0.58f), FontWeight.Medium),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }

        note != null -> Text(
            text = note,
            modifier = Modifier.designBounds(metrics, AUTH_FIELD_X, y, AUTH_FIELD_WIDTH, 76f),
            style = pocketAuthText(metrics, 27f, PocketGreenText, FontWeight.Medium),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
internal fun OtpSlot(
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
    subtitleSize: Float = 55f,
    titleY: Float = 223f,
    subtitleY: Float = 356f,
    error: Boolean = false,
) {
    Text(
        text = title,
        modifier = Modifier.designBounds(metrics, 100f, titleY, 1040f, 114f),
        style = pocketAuthText(metrics, 96f, PocketTeal, FontWeight.Bold),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
    Text(
        text = subtitle,
        modifier = Modifier.designBounds(metrics, 50f, subtitleY, 1140f, if (error) 90f else 65f),
        style = pocketAuthText(
            metrics,
            if (error) 34f else subtitleSize,
            if (error) AuthErrorRed else PocketGreenText,
            FontWeight.SemiBold,
        ),
        textAlign = TextAlign.Center,
        maxLines = if (error) 2 else 1,
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
private fun CredentialChip(
    metrics: DesignMetrics,
    x: Float,
    y: Float,
    label: String,
    tag: String,
    onClick: () -> Unit,
    horizontal: DesignAnchor? = null,
) {
    PocketPanel(
        metrics = metrics,
        x = x,
        y = y,
        width = 146f,
        height = 66f,
        horizontal = horizontal,
        borderColor = PocketBorder,
        borderWidth = 7f,
        radius = 33f,
        fillBrush = PocketWhitePanel,
        shadowAlpha = 0f,
        shadowOffset = 0f,
        tag = tag,
        onClick = onClick,
    ) {
        Text(
            text = label,
            modifier = Modifier.align(Alignment.Center),
            style = pocketAuthText(metrics, 28f, PocketGreenText, FontWeight.Bold),
            maxLines = 1,
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
    borderWidth: Float = 20.152f,
    fontSize: Float = 48f,
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
            borderWidth = borderWidth,
            radius = 118f,
            fillBrush = brush,
            shadowAlpha = shadowAlpha,
            shadowOffset = shadowOffset,
            tag = tag,
            onClick = onClick,
            enabled = enabled,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = pocketAuthText(
                        metrics,
                        fontSize,
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
