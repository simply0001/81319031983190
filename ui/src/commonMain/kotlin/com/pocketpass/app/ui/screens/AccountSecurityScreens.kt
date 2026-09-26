package com.pocketpass.app.ui.screens

import com.pocketpass.app.audio.SoundEffect

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.pocketpass.app.auth.OTP_LENGTH
import com.pocketpass.app.auth.PASSWORD_RULE_MESSAGE
import com.pocketpass.app.auth.filterPocketPassOtp
import com.pocketpass.app.feature.AccountSecurityEvent
import com.pocketpass.app.feature.AccountSecurityStep
import com.pocketpass.app.feature.AccountSecurityUiState
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignAnchor
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.anchoredBounds
import com.pocketpass.app.ui.auth.AuthButton
import com.pocketpass.app.ui.auth.AuthTextAction
import com.pocketpass.app.ui.auth.CredentialPanel
import com.pocketpass.app.ui.auth.OtpSlot
import com.pocketpass.app.ui.auth.PocketGreenText
import com.pocketpass.app.ui.auth.applyCredentialKey
import com.pocketpass.app.ui.auth.pocketAuthText
import com.pocketpass.app.ui.components.EntranceMotion
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.POCKET_KEYBOARD_HEIGHT
import com.pocketpass.app.ui.components.PocketKey
import com.pocketpass.app.ui.components.PocketKeyboard
import com.pocketpass.app.ui.components.PocketKeyboardLayout
import com.pocketpass.app.ui.components.PocketPanel
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.theme.pocketPalette

private val AccountErrorRed = Color(0xFF9B3434)

internal fun accountSecurityTitle(step: AccountSecurityStep): String = when (step) {
    AccountSecurityStep.Overview -> "Account"
    AccountSecurityStep.LinkEmail -> "Link an email"
    AccountSecurityStep.LinkEmailCode -> "Confirm your email"
    AccountSecurityStep.ChangePasswordCode -> "Confirm it's you"
    AccountSecurityStep.ChangePasswordEntry -> "New password"
}

internal fun accountSecuritySubtitle(state: AccountSecurityUiState): String = when (state.step) {
    AccountSecurityStep.Overview -> "How you sign in to PocketPass."
    AccountSecurityStep.LinkEmail -> "A six-digit code confirms the address."
    AccountSecurityStep.LinkEmailCode -> "Enter the code sent to ${state.normalizedEmail}."
    AccountSecurityStep.ChangePasswordCode -> "Enter the code sent to ${state.accountEmail.orEmpty()}."
    AccountSecurityStep.ChangePasswordEntry -> PASSWORD_RULE_MESSAGE
}

@Composable
internal fun AccountPanel(
    metrics: DesignMetrics,
    y: Float,
    onClick: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "account",
        onClick = onClick,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsFriendCode,
            title = "Account",
            subtitle = "Sign-in, email and password",
        )
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
        )
    }
}

@Composable
internal fun AccountStatusPanel(
    metrics: DesignMetrics,
    y: Float,
    state: AccountSecurityUiState,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "account_status",
    ) {
        val username = state.username
        val accountEmail = state.accountEmail
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsFriendCode,
            title = username ?: if (accountEmail != null) "Email account" else "Signed in",
            subtitle = when {
                username != null -> "Username account, no email linked"
                accountEmail != null -> accountEmail
                else -> "Account details are loading"
            },
            subtitleSize = 40f,
        )
    }
}

@Composable
internal fun LinkEmailPanel(
    metrics: DesignMetrics,
    y: Float,
    onClick: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "account_link_email",
        onClick = onClick,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsSocial,
            title = "Link an email address",
            subtitle = "Needed to change your password",
            subtitleSize = 40f,
        )
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
        )
    }
}

@Composable
internal fun ChangePasswordPanel(
    metrics: DesignMetrics,
    y: Float,
    onClick: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "account_change_password",
        onClick = onClick,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsEditName,
            title = "Change password",
            subtitle = "",
            centerTitle = true,
        )
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
        )
    }
}

@Composable
internal fun AccountSecurityBottom(
    state: AccountSecurityUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val send: (AccountSecurityEvent) -> Unit = { dispatch(PocketPassEvent.AccountSecurity(it)) }
    BottomPage(entrance = EntranceMotion.None) { metrics ->
        SubpageHeader(
            metrics = metrics,
            title = accountSecurityTitle(state.step),
            subtitle = accountSecuritySubtitle(state),
            backTag = "account_back",
        ) { dispatch(PocketPassEvent.Back) }
        when (state.step) {
            AccountSecurityStep.Overview -> AccountOverview(metrics, state, send)
            AccountSecurityStep.LinkEmail -> AccountEmailEntry(metrics, state, send)
            AccountSecurityStep.LinkEmailCode,
            AccountSecurityStep.ChangePasswordCode,
            -> AccountCodeEntry(metrics, state, send)

            AccountSecurityStep.ChangePasswordEntry -> AccountPasswordEntry(metrics, state, send)
        }
    }
}

@Composable
private fun AccountOverview(
    metrics: DesignMetrics,
    state: AccountSecurityUiState,
    send: (AccountSecurityEvent) -> Unit,
) {
    val statusY = SUBPAGE_FIRST_ROW_Y
    SubpagePanelPop(y = statusY, height = SETTINGS_ROW_HEIGHT, order = 1) {
        AccountStatusPanel(metrics, statusY, state)
    }
    var nextY = statusY + SUBPAGE_ROW_PITCH
    var order = 2
    if (state.isUsernameAccount) {
        val y = nextY
        SubpagePanelPop(y = y, height = SETTINGS_ROW_HEIGHT, order = order) {
            LinkEmailPanel(metrics, y) { send(AccountSecurityEvent.OpenLinkEmail) }
        }
        nextY += SUBPAGE_ROW_PITCH
        order += 1
    }
    if (state.canChangePassword) {
        val y = nextY
        SubpagePanelPop(y = y, height = SETTINGS_ROW_HEIGHT, order = order) {
            ChangePasswordPanel(metrics, y) { send(AccountSecurityEvent.OpenChangePassword) }
        }
        nextY += SUBPAGE_ROW_PITCH
    }
    val message = state.error ?: state.notice
    if (message != null) {
        AccountMessage(metrics, nextY + 8f, message, isError = state.error != null)
    }
}

@Composable
private fun AccountEmailEntry(
    metrics: DesignMetrics,
    state: AccountSecurityUiState,
    send: (AccountSecurityEvent) -> Unit,
) {
    var keyboardVisible by remember { mutableStateOf(false) }
    val keyboardProgress by animateFloatAsState(
        targetValue = if (keyboardVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "accountEmailKeyboard",
    )
    val submit = {
        keyboardVisible = false
        send(AccountSecurityEvent.SubmitEmail)
    }
    CredentialPanel(
        metrics = metrics,
        y = 310f,
        height = 120f,
        value = state.emailDraft,
        placeholder = "you@example.com",
        tag = "account_email_input",
        active = keyboardVisible,
        onClick = { keyboardVisible = !keyboardVisible },
        fontSize = 46f,
    )
    AccountActions(keyboardProgress) {
        AuthButton(
            metrics = metrics,
            y = 470f,
            height = 130f,
            label = if (state.submitting) "Sending code…" else "Send code",
            tag = "account_send_code",
            enabled = state.canSubmitEmail,
            onClick = submit,
        )
        state.error?.let { AccountMessage(metrics, 640f, it, isError = true) }
    }
    AccountKeyboard(
        metrics = metrics,
        progress = keyboardProgress,
        layout = PocketKeyboardLayout.Email,
        submitLabel = "Send code",
        submitEnabled = state.canSubmitEmail,
        focusReturnTag = "account_email_input",
    ) { key ->
        applyCredentialKey(
            current = state.emailDraft,
            key = key,
            allowSpace = false,
            onChange = { send(AccountSecurityEvent.EmailChanged(it)) },
            onSubmit = { if (state.canSubmitEmail) submit() },
        )
    }
}

@Composable
private fun AccountCodeEntry(
    metrics: DesignMetrics,
    state: AccountSecurityUiState,
    send: (AccountSecurityEvent) -> Unit,
) {
    var keyboardVisible by remember { mutableStateOf(false) }
    val keyboardProgress by animateFloatAsState(
        targetValue = if (keyboardVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "accountCodeKeyboard",
    )
    val linking = state.step == AccountSecurityStep.LinkEmailCode
    val continueWithCode = {
        keyboardVisible = false
        send(AccountSecurityEvent.ContinueWithCode)
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .anchoredBounds(metrics, 77f, 320f, 1086f, 154f, DesignAnchor.Stretch)
            .testTag("account_code_input")
            .controllerTarget("account_code_input", cornerRadius = 40f) { keyboardVisible = !keyboardVisible }
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { keyboardVisible = !keyboardVisible },
    ) {
        repeat(OTP_LENGTH) { index ->
            OtpSlot(
                metrics = metrics,
                x = index * 184f,
                digit = state.codeDraft.getOrNull(index)?.toString().orEmpty(),
                active = keyboardVisible && index == state.codeDraft.length,
                pulse = false,
            )
        }
    }
    AccountActions(keyboardProgress) {
        AuthTextAction(
            metrics = metrics,
            x = 320f,
            y = 500f,
            width = 600f,
            height = 70f,
            label = if (state.resendSecondsRemaining > 0) {
                "Resend code in ${state.resendSecondsRemaining}s"
            } else {
                "Resend code"
            },
            tag = "account_resend_code",
            fontSize = 36f,
            enabled = state.canResend,
            onClick = { send(AccountSecurityEvent.ResendCode) },
        )
        AuthButton(
            metrics = metrics,
            y = 590f,
            height = 130f,
            label = when {
                state.submitting -> "Verifying…"
                linking -> "Verify"
                else -> "Continue"
            },
            tag = "account_continue_code",
            enabled = state.canContinueWithCode,
            onClick = continueWithCode,
        )
        state.error?.let { AccountMessage(metrics, 750f, it, isError = true) }
    }
    AccountKeyboard(
        metrics = metrics,
        progress = keyboardProgress,
        layout = PocketKeyboardLayout.Numeric,
        submitLabel = if (linking) "Verify" else "Continue",
        submitEnabled = state.canContinueWithCode,
        focusReturnTag = "account_code_input",
    ) { key ->
        when (key) {
            is PocketKey.Character ->
                send(AccountSecurityEvent.CodeChanged(filterPocketPassOtp(state.codeDraft + key.value)))

            PocketKey.Backspace -> send(AccountSecurityEvent.CodeChanged(state.codeDraft.dropLast(1)))
            PocketKey.Submit -> if (state.canContinueWithCode) continueWithCode()
            PocketKey.Space, PocketKey.Alphabet, PocketKey.Emoji -> Unit
        }
    }
}

@Composable
private fun AccountPasswordEntry(
    metrics: DesignMetrics,
    state: AccountSecurityUiState,
    send: (AccountSecurityEvent) -> Unit,
) {
    var focused by remember { mutableStateOf<Int?>(null) }
    val keyboardProgress by animateFloatAsState(
        targetValue = if (focused != null) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "accountPasswordKeyboard",
    )
    val masked = !state.showPassword
    val save = {
        focused = null
        send(AccountSecurityEvent.SubmitNewPassword)
    }
    CredentialPanel(
        metrics = metrics,
        y = 310f,
        height = 116f,
        value = state.newPassword,
        placeholder = "new password",
        tag = "account_new_password",
        active = focused == 0,
        onClick = { focused = 0 },
        masked = masked,
    )
    AuthTextAction(
        metrics = metrics,
        x = 940f,
        y = 310f,
        width = 190f,
        height = 116f,
        label = if (masked) "Show" else "Hide",
        tag = "account_show_password",
        fontSize = 32f,
        onClick = { send(AccountSecurityEvent.TogglePasswordVisibility) },
    )
    CredentialPanel(
        metrics = metrics,
        y = 486f,
        height = 116f,
        value = state.newPasswordRepeat,
        placeholder = "repeat new password",
        tag = "account_new_password_repeat",
        active = focused == 1,
        onClick = { focused = 1 },
        masked = masked,
    )
    AccountActions(keyboardProgress) {
        AuthButton(
            metrics = metrics,
            y = 662f,
            height = 130f,
            label = if (state.submitting) "Saving…" else "Save password",
            tag = "account_save_password",
            enabled = state.canSubmitPassword,
            onClick = save,
        )
        state.error?.let { AccountMessage(metrics, 822f, it, isError = true) }
    }
    val field = focused
    AccountKeyboard(
        metrics = metrics,
        progress = keyboardProgress,
        layout = PocketKeyboardLayout.Text,
        submitLabel = if (field == 0) "Next" else "Save",
        submitEnabled = field == 0 || state.canSubmitPassword,
        focusReturnTag = if (field == 1) "account_new_password_repeat" else "account_new_password",
        submitSound = if(field == 0) SoundEffect.Confirm else null,
    ) { key ->
        when (field) {
            0 -> applyCredentialKey(
                current = state.newPassword,
                key = key,
                allowSpace = true,
                onChange = { send(AccountSecurityEvent.NewPasswordChanged(it)) },
                onSubmit = { focused = 1 },
            )

            1 -> applyCredentialKey(
                current = state.newPasswordRepeat,
                key = key,
                allowSpace = true,
                onChange = { send(AccountSecurityEvent.NewPasswordRepeatChanged(it)) },
                onSubmit = { if (state.canSubmitPassword) save() },
            )

            else -> Unit
        }
    }
}

@Composable
private fun AccountActions(
    keyboardProgress: Float,
    content: @Composable () -> Unit,
) {
    if (keyboardProgress >= 0.999f) return
    Box(
        Modifier.graphicsLayer {
            alpha = 1f - keyboardProgress
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
    ) {
        content()
    }
}

@Composable
private fun AccountKeyboard(
    metrics: DesignMetrics,
    progress: Float,
    layout: PocketKeyboardLayout,
    submitLabel: String,
    submitEnabled: Boolean,
    focusReturnTag: String,
    submitSound: SoundEffect? = null,
    onKey: (PocketKey) -> Unit,
) {
    if (progress <= 0.001f) return
    PocketKeyboard(
        metrics = metrics,
        layout = layout,
        submitLabel = submitLabel,
        submitEnabled = submitEnabled,
        onKey = onKey,
        modifier = Modifier.graphicsLayer {
            translationY = (1f - progress) * POCKET_KEYBOARD_HEIGHT
        },
        focusReturnTag = focusReturnTag,
        submitSound = submitSound,
    )
}

@Composable
private fun AccountMessage(
    metrics: DesignMetrics,
    y: Float,
    text: String,
    isError: Boolean,
) {
    Text(
        text = text,
        modifier = Modifier.anchoredBounds(metrics, 110f, y, 1020f, 84f, DesignAnchor.Stretch),
        style = pocketAuthText(
            metrics,
            32f,
            if (isError) AccountErrorRed else PocketGreenText,
            FontWeight.SemiBold,
        ),
        textAlign = TextAlign.Center,
        maxLines = 2,
    )
}
