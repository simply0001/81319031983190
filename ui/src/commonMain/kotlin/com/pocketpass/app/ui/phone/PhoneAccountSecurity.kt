package com.pocketpass.app.ui.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import com.pocketpass.app.auth.OTP_LENGTH
import com.pocketpass.app.auth.filterPocketPassOtp
import com.pocketpass.app.feature.AccountSecurityEvent
import com.pocketpass.app.feature.AccountSecurityStep
import com.pocketpass.app.feature.AccountSecurityUiState
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.auth.PocketBorder
import com.pocketpass.app.ui.auth.PocketGreenBorder
import com.pocketpass.app.ui.auth.PocketGreenButton
import com.pocketpass.app.ui.auth.PocketGreenText
import com.pocketpass.app.ui.auth.PocketTeal
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.screens.AccountStatusPanel
import com.pocketpass.app.ui.screens.ChangePasswordPanel
import com.pocketpass.app.ui.screens.LinkEmailPanel
import com.pocketpass.app.ui.theme.pocketPalette

private val AccountErrorRed = Color(0xFF9B3434)

@Composable
internal fun ColumnScope.PhoneAccountSecurity(
    metrics: DesignMetrics,
    state: AccountSecurityUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val send: (AccountSecurityEvent) -> Unit = { dispatch(PocketPassEvent.AccountSecurity(it)) }
    when (state.step) {
        AccountSecurityStep.Overview -> {
            PhoneSubpageRow(metrics, order = 0) { AccountStatusPanel(metrics, 0f, state) }
            var order = 1
            if (state.isUsernameAccount) {
                PhoneSubpageRow(metrics, order = order) {
                    LinkEmailPanel(metrics, 0f) { send(AccountSecurityEvent.OpenLinkEmail) }
                }
                order += 1
            }
            if (state.canChangePassword) {
                PhoneSubpageRow(metrics, order = order) {
                    ChangePasswordPanel(metrics, 0f) { send(AccountSecurityEvent.OpenChangePassword) }
                }
            }
            (state.error ?: state.notice)?.let { message ->
                PhoneAccountMessage(metrics, message, isError = state.error != null)
            }
        }

        AccountSecurityStep.LinkEmail -> PhoneAccountCard(metrics) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
            PhoneTextField(
                metrics = metrics,
                value = state.emailDraft,
                onValueChange = { send(AccountSecurityEvent.EmailChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "you@example.com",
                fontSize = 46f,
                minHeight = 140f,
                textColor = pocketPalette.ink(PocketTeal),
                placeholderColor = pocketPalette.ink(PocketGreenText).copy(alpha = 0.56f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (state.canSubmitEmail) send(AccountSecurityEvent.SubmitEmail) }),
                borderColor = PocketBorder,
                tag = "account_email_input",
                focusRequester = focusRequester,
            )
            Spacer(Modifier.height(metrics.dp(28f)))
            PhoneButton(
                metrics = metrics,
                label = if (state.submitting) "Sending code…" else "Send code",
                modifier = Modifier.fillMaxWidth(),
                fill = PocketGreenButton,
                borderColor = PocketGreenBorder,
                enabled = state.canSubmitEmail,
                height = 150f,
                tag = "account_send_code",
            ) { send(AccountSecurityEvent.SubmitEmail) }
            state.error?.let { PhoneAccountMessage(metrics, it, isError = true) }
        }

        AccountSecurityStep.LinkEmailCode,
        AccountSecurityStep.ChangePasswordCode,
        -> PhoneAccountCard(metrics) {
            val linking = state.step == AccountSecurityStep.LinkEmailCode
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
            PhoneDigitSlots(
                metrics = metrics,
                value = state.codeDraft,
                length = OTP_LENGTH,
                slotWidth = 140f,
                slotHeight = 154f,
                gap = 22f,
                filledBorder = PocketGreenBorder,
                emptyBorder = PocketBorder,
                textColor = pocketPalette.ink(PocketTeal),
                tag = "account_code_input",
                onValueChange = { send(AccountSecurityEvent.CodeChanged(filterPocketPassOtp(it))) },
                onDone = { if (state.canContinueWithCode) send(AccountSecurityEvent.ContinueWithCode) },
                focusRequester = focusRequester,
            )
            Spacer(Modifier.height(metrics.dp(20f)))
            PhoneTextAction(
                metrics = metrics,
                label = if (state.resendSecondsRemaining > 0) {
                    "Resend code in ${state.resendSecondsRemaining}s"
                } else {
                    "Resend code"
                },
                tag = "account_resend_code",
                onClick = { send(AccountSecurityEvent.ResendCode) },
                fontSize = 36f,
                color = pocketPalette.ink(PocketGreenText),
                enabled = state.canResend,
            )
            Spacer(Modifier.height(metrics.dp(16f)))
            PhoneButton(
                metrics = metrics,
                label = when {
                    state.submitting -> "Verifying…"
                    linking -> "Verify"
                    else -> "Continue"
                },
                modifier = Modifier.fillMaxWidth(),
                fill = PocketGreenButton,
                borderColor = PocketGreenBorder,
                enabled = state.canContinueWithCode,
                height = 150f,
                tag = "account_continue_code",
            ) { send(AccountSecurityEvent.ContinueWithCode) }
            state.error?.let { PhoneAccountMessage(metrics, it, isError = true) }
        }

        AccountSecurityStep.ChangePasswordEntry -> PhoneAccountCard(metrics) {
            val masked = !state.showPassword
            val transformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PhoneTextField(
                    metrics = metrics,
                    value = state.newPassword,
                    onValueChange = { send(AccountSecurityEvent.NewPasswordChanged(it)) },
                    modifier = Modifier.weight(1f),
                    placeholder = "new password",
                    fontSize = 46f,
                    minHeight = 140f,
                    textColor = pocketPalette.ink(PocketTeal),
                    placeholderColor = pocketPalette.ink(PocketGreenText).copy(alpha = 0.56f),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    borderColor = PocketBorder,
                    tag = "account_new_password",
                    focusRequester = focusRequester,
                    visualTransformation = transformation,
                )
                PhoneTextAction(
                    metrics = metrics,
                    label = if (masked) "Show" else "Hide",
                    tag = "account_show_password",
                    onClick = { send(AccountSecurityEvent.TogglePasswordVisibility) },
                    fontSize = 32f,
                    color = pocketPalette.ink(PocketGreenText),
                )
            }
            Spacer(Modifier.height(metrics.dp(60f)))
            PhoneTextField(
                metrics = metrics,
                value = state.newPasswordRepeat,
                onValueChange = { send(AccountSecurityEvent.NewPasswordRepeatChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "repeat new password",
                fontSize = 46f,
                minHeight = 140f,
                textColor = pocketPalette.ink(PocketTeal),
                placeholderColor = pocketPalette.ink(PocketGreenText).copy(alpha = 0.56f),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (state.canSubmitPassword) send(AccountSecurityEvent.SubmitNewPassword) },
                ),
                borderColor = PocketBorder,
                tag = "account_new_password_repeat",
                visualTransformation = transformation,
            )
            Spacer(Modifier.height(metrics.dp(60f)))
            PhoneButton(
                metrics = metrics,
                label = if (state.submitting) "Saving…" else "Save password",
                modifier = Modifier.fillMaxWidth(),
                fill = PocketGreenButton,
                borderColor = PocketGreenBorder,
                enabled = state.canSubmitPassword,
                height = 150f,
                tag = "account_save_password",
            ) { send(AccountSecurityEvent.SubmitNewPassword) }
            state.error?.let { PhoneAccountMessage(metrics, it, isError = true) }
        }
    }
}

@Composable
private fun PhoneAccountCard(
    metrics: DesignMetrics,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = metrics.dp(PHONE_CONTENT_MARGIN))
            .fillMaxWidth()
            .phonePanel(metrics, radius = 110f)
            .padding(horizontal = metrics.dp(60f), vertical = metrics.dp(50f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun PhoneAccountMessage(
    metrics: DesignMetrics,
    text: String,
    isError: Boolean,
) {
    Spacer(Modifier.height(metrics.dp(24f)))
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.dp(PHONE_CONTENT_MARGIN)),
        color = pocketPalette.ink(if (isError) AccountErrorRed else PocketGreenText),
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = metrics.sp(32f),
        textAlign = TextAlign.Center,
        maxLines = 3,
    )
}
