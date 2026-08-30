package com.pocketpass.app.ui.phone

import android.animation.ValueAnimator
import android.os.Build
import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pocketpass.app.auth.AuthEvent
import com.pocketpass.app.auth.AuthStep
import com.pocketpass.app.auth.AuthUiError
import com.pocketpass.app.auth.AuthUiState
import com.pocketpass.app.auth.filterPocketPassOtp
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.domain.model.PROFILE_NAME_MAX_LENGTH
import com.pocketpass.app.feature.AccountSetupEvent
import com.pocketpass.app.feature.AccountSetupStep
import com.pocketpass.app.feature.AccountSetupUiState
import com.pocketpass.app.mii.MiiEditorEvent
import com.pocketpass.app.mii.renderer.MiiEditorRenderSurface
import com.pocketpass.app.model.BIO_MAX_LENGTH
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.nearby.NearbyPermissionUiState
import com.pocketpass.app.state.PocketPassViewModel
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.BOTTOM_DESIGN_HEIGHT
import com.pocketpass.app.ui.BOTTOM_DESIGN_WIDTH
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.TOP_DESIGN_HEIGHT
import com.pocketpass.app.ui.TOP_DESIGN_WIDTH
import com.pocketpass.app.ui.auth.PocketBorder
import com.pocketpass.app.ui.auth.PocketGreenBorder
import com.pocketpass.app.ui.auth.PocketGreenButton
import com.pocketpass.app.ui.auth.PocketGreenText
import com.pocketpass.app.ui.auth.PocketTeal
import com.pocketpass.app.ui.auth.PocketWhitePanel
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.mii.MiiEditorBottomScreen
import com.pocketpass.app.ui.mii.MiiEditorTopScreen
import com.pocketpass.app.ui.screens.AppUpdateStatusPanel
import com.pocketpass.app.ui.setup.CountryCatalog
import com.pocketpass.app.ui.theme.pocketPalette

private val DiscordButton = Brush.verticalGradient(
    colorStops = arrayOf(0f to Color(0xFF5765E2), 0.52f to Color(0xFF5E63ED), 1f to Color(0xFF2935BC)),
)
private val DiscordBorder = Color(0xFF4D4BC2)
private val SetupErrorRed = Color(0xFF9B3434)

@Composable
private fun PhoneOnboarding(
    metrics: DesignMetrics,
    content: @Composable ColumnScope.() -> Unit,
) {
    val insets = LocalPhoneInsets.current
    val colors = pocketPalette.background(PocketPassDestination.Home, top = false)
    val wide = phoneLayout(metrics.designWidth, metrics.designHeight) == PhoneLayout.Wide
    Box(Modifier.fillMaxSize()) {
        PhoneBackdrop(metrics, colors.top, colors.bottom)
        if (wide) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = metrics.dp(insets.start),
                        end = metrics.dp(insets.end),
                        top = metrics.dp(insets.top),
                        bottom = metrics.dp(maxOf(insets.bottom, insets.ime)),
                    ),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) { Wordmark(metrics, big = true) }
                Column(
                    Modifier
                        .width(metrics.dp(PHONE_DECK_WIDTH))
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = metrics.dp(70f), vertical = metrics.dp(60f)),
                    verticalArrangement = Arrangement.Center,
                    content = content,
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = metrics.dp(insets.start),
                        end = metrics.dp(insets.end),
                        top = metrics.dp(insets.top + 60f),
                        bottom = metrics.dp(maxOf(insets.bottom, insets.ime) + 40f),
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Wordmark(metrics, big = false)
                Spacer(Modifier.height(metrics.dp(48f)))
                Column(
                    Modifier
                        .widthIn(max = metrics.dp(PHONE_DECK_WIDTH))
                        .fillMaxWidth()
                        .padding(horizontal = metrics.dp(70f)),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun Wordmark(metrics: DesignMetrics, big: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FigmaAsset(
            resource = Assets.AuthLeaf,
            modifier = Modifier.requiredSize(
                metrics.dp(if (big) 452.1f else 230f),
                metrics.dp(if (big) 530f else 269.6f),
            ),
        )
        Spacer(Modifier.height(metrics.dp(if (big) 40f else 16f)))
        Text(
            text = "PocketPass",
            color = pocketPalette.ink(PocketTeal),
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(if (big) 128f else 84f),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun AuthCard(
    metrics: DesignMetrics,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .phonePanel(
                metrics = metrics,
                radius = 118f,
                borderColor = PocketBorder,
                fill = PocketWhitePanel,
                shadowOffset = 14f,
                shadowAlpha = 0.12f,
            )
            .padding(horizontal = metrics.dp(70f), vertical = metrics.dp(60f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun AuthHeading(metrics: DesignMetrics, title: String, subtitle: String?, error: Boolean = false) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth(),
        color = pocketPalette.ink(PocketTeal),
        fontFamily = Rubik,
        fontWeight = FontWeight.Bold,
        fontSize = metrics.sp(82f),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (subtitle != null) {
        Spacer(Modifier.height(metrics.dp(12f)))
        Text(
            text = subtitle,
            modifier = Modifier.fillMaxWidth(),
            color = pocketPalette.ink(if (error) SetupErrorRed else PocketGreenText),
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(42f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AuthErrorText(metrics: DesignMetrics, error: AuthUiError?) {
    if (error == null) return
    Spacer(Modifier.height(metrics.dp(24f)))
    Text(
        text = error.message,
        modifier = Modifier.fillMaxWidth(),
        color = pocketPalette.ink(SetupErrorRed),
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = metrics.sp(30f),
        textAlign = TextAlign.Center,
        maxLines = 3,
    )
    Text(
        text = error.code,
        modifier = Modifier.fillMaxWidth(),
        color = pocketPalette.ink(SetupErrorRed).copy(alpha = 0.62f),
        fontFamily = Rubik,
        fontWeight = FontWeight.Medium,
        fontSize = metrics.sp(24f),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
internal fun PhoneAuthScreen(
    metrics: DesignMetrics,
    sessionState: SessionState,
    state: AuthUiState,
    send: (AuthEvent) -> Unit,
) {
    PhoneOnboarding(metrics) {
        AuthCard(metrics) {
            when {
                sessionState is SessionState.Initializing -> AuthHeading(metrics, "PocketPass", "Starting securely…")
                sessionState is SessionState.ConfigurationError -> {
                    AuthHeading(
                        metrics = metrics,
                        title = "Sign-in unavailable",
                        subtitle = state.error?.message ?: "PocketPass couldn't start sign-in.",
                        error = state.error != null,
                    )
                    state.error?.code?.let { code ->
                        Text(
                            text = code,
                            color = pocketPalette.ink(PocketGreenText).copy(alpha = 0.62f),
                            fontFamily = Rubik,
                            fontWeight = FontWeight.Medium,
                            fontSize = metrics.sp(27f),
                        )
                    }
                    Spacer(Modifier.height(metrics.dp(44f)))
                    PhoneButton(
                        metrics = metrics,
                        label = "Retry",
                        modifier = Modifier.fillMaxWidth(),
                        fill = PocketGreenButton,
                        borderColor = PocketGreenBorder,
                        height = 150f,
                        tag = "auth_retry_initialization",
                    ) { send(AuthEvent.RetryInitialization) }
                }
                else -> when (state.step) {
                    AuthStep.Landing -> AuthLanding(metrics, state, send)
                    AuthStep.Email -> AuthEmail(metrics, state, send)
                    AuthStep.Otp -> AuthOtp(metrics, state, send)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.AuthLanding(metrics: DesignMetrics, state: AuthUiState, send: (AuthEvent) -> Unit) {
    AuthHeading(metrics, "Welcome!", "Continue with email or Discord.")
    Spacer(Modifier.height(metrics.dp(48f)))
    PhoneButton(
        metrics = metrics,
        label = "Continue with Email",
        modifier = Modifier.fillMaxWidth(),
        fill = PocketGreenButton,
        borderColor = PocketGreenBorder,
        height = 150f,
        tag = "auth_continue_email",
    ) { send(AuthEvent.ContinueWithEmail) }
    Spacer(Modifier.height(metrics.dp(28f)))
    PhoneButton(
        metrics = metrics,
        label = "Continue with Discord",
        modifier = Modifier.fillMaxWidth(),
        fill = DiscordButton,
        borderColor = DiscordBorder,
        enabled = !state.isSubmitting,
        height = 150f,
        tag = "auth_continue_discord",
    ) { send(AuthEvent.ContinueWithDiscord) }
    AuthErrorText(metrics, state.error)
}

@Composable
private fun ColumnScope.AuthEmail(metrics: DesignMetrics, state: AuthUiState, send: (AuthEvent) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    AuthHeading(metrics, "Continue", "Via Email")
    Spacer(Modifier.height(metrics.dp(44f)))
    PhoneTextField(
        metrics = metrics,
        value = state.email,
        onValueChange = { send(AuthEvent.EmailChanged(it)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = "you@example.com",
        textColor = pocketPalette.ink(PocketTeal),
        placeholderColor = pocketPalette.ink(PocketGreenText).copy(alpha = 0.56f),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (state.canContinueWithEmail) send(AuthEvent.SubmitEmail) }),
        borderColor = PocketBorder,
        tag = "auth_email_input",
        focusRequester = focusRequester,
    )
    Spacer(Modifier.height(metrics.dp(28f)))
    PhoneButton(
        metrics = metrics,
        label = if (state.isSubmitting) "Sending code…" else "Continue",
        modifier = Modifier.fillMaxWidth(),
        fill = PocketGreenButton,
        borderColor = PocketGreenBorder,
        enabled = state.canContinueWithEmail,
        height = 150f,
        tag = "auth_submit_email",
    ) { send(AuthEvent.SubmitEmail) }
    Spacer(Modifier.height(metrics.dp(16f)))
    PhoneTextAction(metrics, "Back", "auth_email_back", { send(AuthEvent.Back) }, color = pocketPalette.ink(PocketGreenText))
    AuthErrorText(metrics, state.error)
}

@Composable
private fun ColumnScope.AuthOtp(metrics: DesignMetrics, state: AuthUiState, send: (AuthEvent) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state.errorShakeNonce) {
        if (state.errorShakeNonce == 0 || !ValueAnimator.areAnimatorsEnabled()) return@LaunchedEffect
        listOf(-11f, 9f, -6f, 4f, 0f).forEach { shake.animateTo(it, tween(48)) }
    }
    AuthHeading(metrics, "Check your email", "Enter the 6-digit code sent to")
    Text(
        text = state.normalizedEmail,
        modifier = Modifier.fillMaxWidth(),
        color = pocketPalette.ink(PocketGreenText).copy(alpha = 0.68f),
        fontFamily = Rubik,
        fontWeight = FontWeight.Medium,
        fontSize = metrics.sp(36f),
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(metrics.dp(40f)))
    PhoneDigitSlots(
        metrics = metrics,
        value = state.otpCode,
        length = 6,
        slotWidth = 140f,
        slotHeight = 154f,
        gap = 22f,
        filledBorder = PocketGreenBorder,
        emptyBorder = PocketBorder,
        textColor = pocketPalette.ink(PocketTeal),
        tag = "auth_otp_input",
        onValueChange = { send(AuthEvent.OtpChanged(filterPocketPassOtp(it))) },
        onDone = { if (state.canVerify) send(AuthEvent.VerifyOtp) },
        modifier = Modifier.graphicsLayer { translationX = shake.value },
        focusRequester = focusRequester,
    )
    Spacer(Modifier.height(metrics.dp(20f)))
    PhoneTextAction(
        metrics = metrics,
        label = if (state.resendSecondsRemaining > 0) "Resend code in ${state.resendSecondsRemaining}s" else "Resend code",
        tag = "auth_resend",
        onClick = { send(AuthEvent.ResendOtp) },
        fontSize = 36f,
        color = pocketPalette.ink(PocketGreenText),
        enabled = state.canResend,
    )
    Spacer(Modifier.height(metrics.dp(16f)))
    PhoneButton(
        metrics = metrics,
        label = if (state.isSubmitting) "Verifying…" else "Verify",
        modifier = Modifier.fillMaxWidth(),
        fill = PocketGreenButton,
        borderColor = PocketGreenBorder,
        enabled = state.canVerify,
        height = 150f,
        tag = "auth_verify",
    ) { send(AuthEvent.VerifyOtp) }
    Spacer(Modifier.height(metrics.dp(16f)))
    PhoneTextAction(
        metrics = metrics,
        label = "Change Email",
        tag = "auth_change_email",
        onClick = { send(AuthEvent.ChangeEmail) },
        fontSize = 40f,
        color = pocketPalette.ink(PocketGreenText),
        enabled = !state.isSubmitting,
    )
    AuthErrorText(metrics, state.error)
}

@Composable
internal fun PhoneAccountSetupScreen(
    metrics: DesignMetrics,
    state: AccountSetupUiState,
    send: (AccountSetupEvent) -> Unit,
) {
    PhoneOnboarding(metrics) {
        if (!state.resolved || !state.required) return@PhoneOnboarding
        AuthCard(metrics) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(metrics.dp(96f))) {
                    if (state.step != AccountSetupStep.Name) {
                        SetupBackChevron(metrics, enabled = !state.submitting) { send(AccountSetupEvent.BackStep) }
                    }
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(metrics.dp(34f), Alignment.CenterHorizontally),
                ) {
                    AccountSetupStep.entries.forEach { step ->
                        Box(
                            Modifier
                                .size(metrics.dp(26f))
                                .clip(CircleShape)
                                .background(
                                    pocketPalette.ink(PocketTeal).copy(alpha = if (step.ordinal <= state.step.ordinal) 1f else 0.22f),
                                ),
                        )
                    }
                }
                Spacer(Modifier.size(metrics.dp(96f)))
            }
            Spacer(Modifier.height(metrics.dp(36f)))
            val (title, description) = when (state.step) {
                AccountSetupStep.Name -> "Username" to "The name everyone will see"
                AccountSetupStep.Bio -> "Your Bio" to "Say hello to the people you meet"
                AccountSetupStep.Age -> "Your Age" to "Optional, shown on your profile"
                AccountSetupStep.Country -> "Country" to "Pick where you play from"
            }
            AuthHeading(metrics, title, state.error ?: description, error = state.error != null)
            val counter = when (state.step) {
                AccountSetupStep.Name -> "${state.nameDraft.length}/$PROFILE_NAME_MAX_LENGTH"
                AccountSetupStep.Bio -> "${state.bioDraft.length}/$BIO_MAX_LENGTH"
                AccountSetupStep.Age -> "13-120"
                AccountSetupStep.Country -> null
            }
            if (counter != null) {
                Spacer(Modifier.height(metrics.dp(20f)))
                Text(
                    text = counter,
                    modifier = Modifier.fillMaxWidth(),
                    color = pocketPalette.ink(PocketBorder),
                    fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = metrics.sp(30f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(metrics.dp(16f)))
            val focusRequester = remember(state.step) { FocusRequester() }
            LaunchedEffect(state.step) {
                if (state.step != AccountSetupStep.Country) runCatching { focusRequester.requestFocus() }
            }
            val continueStep = { if (state.canContinue && !state.submitting) send(AccountSetupEvent.Continue) }
            when (state.step) {
                AccountSetupStep.Name -> PhoneTextField(
                    metrics = metrics,
                    value = state.nameDraft,
                    onValueChange = { send(AccountSetupEvent.NameChanged(it.filterNot(Char::isWhitespace).take(PROFILE_NAME_MAX_LENGTH))) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "yourname",
                    textColor = pocketPalette.ink(PocketTeal),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { continueStep() }),
                    borderColor = PocketBorder,
                    textAlign = TextAlign.Center,
                    tag = "setup_name_input",
                    focusRequester = focusRequester,
                )

                AccountSetupStep.Bio -> PhoneTextField(
                    metrics = metrics,
                    value = state.bioDraft,
                    onValueChange = { send(AccountSetupEvent.BioChanged(it.take(BIO_MAX_LENGTH))) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Say hello to everyone you meet!",
                    fontSize = 38f,
                    fontWeight = FontWeight.SemiBold,
                    textColor = pocketPalette.ink(PocketTeal),
                    placeholderColor = pocketPalette.ink(Color(0xFF8FB9C6)),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { continueStep() }),
                    singleLine = false,
                    maxLines = 3,
                    minHeight = 200f,
                    radius = 45f,
                    borderColor = PocketBorder,
                    borderWidth = 8f,
                    fill = Brush.verticalGradient(listOf(pocketPalette.surfaceSunken, pocketPalette.surfaceSunken)),
                    horizontalPadding = 36f,
                    verticalPadding = 24f,
                    tag = "setup_bio_input",
                    focusRequester = focusRequester,
                )

                AccountSetupStep.Age -> Row(verticalAlignment = Alignment.CenterVertically) {
                    PhoneTextField(
                        metrics = metrics,
                        value = state.ageDraft,
                        onValueChange = { send(AccountSetupEvent.AgeChanged(it.filter(Char::isDigit).take(3))) },
                        modifier = Modifier.width(metrics.dp(436f)),
                        placeholder = "--",
                        fontSize = 70f,
                        fontWeight = FontWeight.Bold,
                        textColor = pocketPalette.ink(PocketTeal),
                        placeholderColor = pocketPalette.ink(PocketTeal).copy(alpha = 0.45f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { continueStep() }),
                        radius = 83f,
                        borderColor = PocketBorder,
                        textAlign = TextAlign.Center,
                        tag = "setup_age_input",
                        focusRequester = focusRequester,
                    )
                    Spacer(Modifier.width(metrics.dp(30f)))
                    PhoneTextAction(
                        metrics = metrics,
                        label = "Skip",
                        tag = "setup_age_skip",
                        onClick = { send(AccountSetupEvent.SkipAge) },
                        color = pocketPalette.ink(PocketGreenText),
                        enabled = !state.submitting,
                    )
                }

                AccountSetupStep.Country -> CountryList(metrics, state.countryCode) { send(AccountSetupEvent.CountrySelected(it)) }
            }
            Spacer(Modifier.height(metrics.dp(40f)))
            PhoneButton(
                metrics = metrics,
                label = if (state.submitting) "Saving…" else "Continue",
                modifier = Modifier.fillMaxWidth(),
                fill = PocketGreenButton,
                borderColor = PocketGreenBorder,
                enabled = state.canContinue && !state.submitting,
                height = 150f,
                tag = if (state.step == AccountSetupStep.Country) "setup_finish" else "setup_continue",
            ) { send(if (state.step == AccountSetupStep.Country) AccountSetupEvent.Submit else AccountSetupEvent.Continue) }
        }
    }
}

@Composable
private fun SetupBackChevron(metrics: DesignMetrics, enabled: Boolean, onClick: () -> Unit) {
    val color = pocketPalette.ink(PocketTeal)
    Canvas(
        Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .testTag("setup_back")
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(metrics.dp(30f)),
    ) {
        val stroke = size.width * 0.26f
        drawLine(color, Offset(size.width * 0.7f, 0f), Offset(size.width * 0.3f, size.height / 2f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.3f, size.height / 2f), Offset(size.width * 0.7f, size.height), stroke, StrokeCap.Round)
    }
}

@Composable
private fun CountryList(metrics: DesignMetrics, selectedCode: String?, onSelect: (String) -> Unit) {
    val countries = CountryCatalog.countries
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val index = countries.indexOfFirst { it.code == selectedCode }
        if (index >= 0) listState.scrollToItem((index - 2).coerceAtLeast(0))
    }
    val shape = RoundedCornerShape(metrics.dp(60f))
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.dp(760f))
            .clip(shape)
            .pocketFrame(pocketPalette.surface, metrics.dp(12f), PocketBorder, shape)
            .testTag("setup_country_list"),
        contentPadding = PaddingValues(horizontal = metrics.dp(28f), vertical = metrics.dp(26f)),
    ) {
        items(countries, key = { it.code }) { country ->
            val selected = country.code == selectedCode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.dp(96f))
                    .clip(RoundedCornerShape(metrics.dp(48f)))
                    .background(if (selected) pocketPalette.tint(Color(0xFFBDF8CB)) else Color.Transparent)
                    .testTag("setup_country_${country.code}")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(country.code) }
                    .padding(horizontal = metrics.dp(38f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${country.flag}  ${country.name}",
                    modifier = Modifier.weight(1f),
                    color = pocketPalette.ink(if (selected) Color(0xFF1D6B25) else PocketTeal),
                    fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = metrics.sp(44f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) {
                    Text(
                        text = "✓",
                        color = pocketPalette.ink(Color(0xFF1D6B25)),
                        fontFamily = Rubik,
                        fontWeight = FontWeight.Bold,
                        fontSize = metrics.sp(50f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PhoneNearbyPermissionScreen(
    metrics: DesignMetrics,
    state: NearbyPermissionUiState,
    onContinue: () -> Unit,
) {
    val legacyLocation = Build.VERSION.SDK_INT <= 30
    PhoneOnboarding(metrics) {
        AuthCard(metrics) {
            Text(
                text = if (state.isRepair) "Restore Nearby Encounters" else "Meet people nearby",
                modifier = Modifier.fillMaxWidth(),
                color = pocketPalette.ink(PocketTeal),
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(60f),
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(metrics.dp(24f)))
            Text(
                text = "PocketPass uses Bluetooth to notice other players around you and swap a quick, " +
                    "anonymous pass. Your profile is never broadcast and every exchange is encrypted.",
                modifier = Modifier.fillMaxWidth(),
                color = pocketPalette.ink(PocketGreenText),
                fontFamily = Rubik,
                fontWeight = FontWeight.Medium,
                fontSize = metrics.sp(34f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(metrics.dp(40f)))
            PermissionRow(metrics, Assets.SettingsNearby, "Nearby devices", "Lets PocketPass find players around you and trade passes.")
            Spacer(Modifier.height(metrics.dp(28f)))
            PermissionRow(
                metrics = metrics,
                icon = if (legacyLocation) Assets.SettingsEncounterLed else Assets.SettingsNotifications,
                title = if (legacyLocation) "Location" else "Notifications",
                detail = if (legacyLocation) {
                    "Android 11 needs “Allow all the time” so passes work with the screen off."
                } else {
                    "Shows that Nearby is running and tells you when you meet someone."
                },
            )
            state.error?.let { error ->
                Spacer(Modifier.height(metrics.dp(24f)))
                Text(
                    text = error,
                    modifier = Modifier.fillMaxWidth(),
                    color = pocketPalette.ink(SetupErrorRed),
                    fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = metrics.sp(30f),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(metrics.dp(44f)))
            PhoneButton(
                metrics = metrics,
                label = if (state.isRepair) "Fix Permissions" else "Allow Permissions",
                modifier = Modifier.fillMaxWidth(),
                fill = PocketGreenButton,
                borderColor = PocketGreenBorder,
                height = 146f,
                tag = "nearby_permission_continue",
                onClick = onContinue,
            )
        }
    }
}

@Composable
private fun PermissionRow(metrics: DesignMetrics, @RawRes icon: Int, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FigmaAsset(resource = icon, modifier = Modifier.requiredSize(metrics.dp(124f)))
        Column(
            Modifier
                .weight(1f)
                .padding(start = metrics.dp(30f)),
        ) {
            Text(
                text = title,
                color = pocketPalette.ink(PocketTeal),
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(40f),
                maxLines = 1,
            )
            Text(
                text = detail,
                color = pocketPalette.ink(PocketGreenText),
                fontFamily = Rubik,
                fontWeight = FontWeight.Medium,
                fontSize = metrics.sp(29f),
                maxLines = 3,
            )
        }
    }
}

@Composable
internal fun PhoneForceUpdateScreen(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    PhoneOnboarding(metrics) {
        Text(
            text = "Update Required",
            modifier = Modifier.fillMaxWidth(),
            color = pocketPalette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(88f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(metrics.dp(16f)))
        Text(
            text = "This version of PocketPass is too old to keep going.",
            modifier = Modifier.fillMaxWidth(),
            color = pocketPalette.tealSoft,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(40f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(metrics.dp(48f)))
        Box(
            Modifier
                .requiredSize(metrics.dp(1240f), metrics.dp(428f)),
        ) {
            AppUpdateStatusPanel(metrics = metrics, appUpdate = state.appUpdate, y = 0f, dispatch = dispatch)
        }
    }
}

@Composable
internal fun PhoneMiiGate(metrics: DesignMetrics, state: PocketPassUiState, viewModel: PocketPassViewModel) {
    val controller = viewModel.miiEditorController
    val savedCanonical by rememberUpdatedState(state.miiEditor.savedCanonicalBase64)
    val onEvent: (MiiEditorEvent) -> Unit = { viewModel.dispatch(PocketPassEvent.Mii(it)) }
    val insets = LocalPhoneInsets.current
    val frame = Modifier
        .fillMaxSize()
        .background(Color(0xFF17232B))
        .padding(
            start = metrics.dp(insets.start),
            top = metrics.dp(insets.top),
            end = metrics.dp(insets.end),
            bottom = metrics.dp(insets.bottom),
        )
    val topBoard: @Composable () -> Unit = {
        MiiEditorTopScreen(
            state = state.miiEditor,
            status = state.status,
            onEvent = onEvent,
            modifier = Modifier.fillMaxSize(),
            saveOnly = true,
        ) {
            if (state.miiEditor.isEditorVisible) {
                MiiEditorRenderSurface(
                    editorController = controller,
                    modifier = Modifier.fillMaxSize(),
                    initialCanonicalBase64 = savedCanonical,
                )
            }
        }
    }
    val bottomBoard: @Composable () -> Unit = {
        MiiEditorBottomScreen(state = state.miiEditor, onEvent = onEvent, modifier = Modifier.fillMaxSize())
    }
    if (phoneLayout(metrics.designWidth, metrics.designHeight) == PhoneLayout.Wide) {
        Row(frame, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().aspectRatio(TOP_DESIGN_WIDTH / TOP_DESIGN_HEIGHT)) { topBoard() }
            }
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(BOTTOM_DESIGN_WIDTH / BOTTOM_DESIGN_HEIGHT, matchHeightConstraintsFirst = true),
            ) { bottomBoard() }
        }
    } else {
        Column(frame, verticalArrangement = Arrangement.Center) {
            Box(Modifier.fillMaxWidth().aspectRatio(TOP_DESIGN_WIDTH / TOP_DESIGN_HEIGHT)) { topBoard() }
            Box(Modifier.fillMaxWidth().aspectRatio(BOTTOM_DESIGN_WIDTH / BOTTOM_DESIGN_HEIGHT)) { bottomBoard() }
        }
    }
}
