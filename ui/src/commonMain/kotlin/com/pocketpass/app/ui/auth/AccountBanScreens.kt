package com.pocketpass.app.ui.auth

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pocketpass.app.audio.LocalSoundEffects
import com.pocketpass.app.audio.SoundEffect
import com.pocketpass.app.domain.model.AccountBanNotice
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.BOTTOM_DESIGN_HEIGHT
import com.pocketpass.app.ui.BOTTOM_DESIGN_WIDTH
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.components.PatternBackground
import com.pocketpass.app.ui.components.PocketPanel
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.formatInstant
import com.pocketpass.app.ui.theme.pocketPalette
import kotlin.time.Instant

internal const val ACCOUNT_BAN_TITLE = "Your account is banned"
internal const val ACCOUNT_BAN_APPEAL_LABEL = "Appeal on Discord"
internal const val ACCOUNT_BAN_SIGN_OUT_LABEL = "Sign out"
internal const val ACCOUNT_BAN_APPEAL_TAG = "account_ban_appeal"
internal const val ACCOUNT_BAN_SIGN_OUT_TAG = "account_ban_sign_out"
private const val ACCOUNT_BAN_APPEAL_URL = "https://pocketpass.xyz/#community"

internal fun AccountBanNotice.endsLine(): String {
    val endsAt = endsAtEpochMillis ?: return "This ban is permanent."
    return "This ban ends on ${formatInstant(Instant.fromEpochMilliseconds(endsAt), "d MMMM yyyy")}."
}

@Composable
internal fun rememberAccountBanAppeal(): () -> Unit {
    val uriHandler = LocalUriHandler.current
    val sounds = LocalSoundEffects.current
    return remember(uriHandler, sounds) {
        {
            sounds.play(SoundEffect.Confirm)
            runCatching { uriHandler.openUri(ACCOUNT_BAN_APPEAL_URL) }
        }
    }
}

@Composable
fun AccountBanBottomScreen(
    metrics: DesignMetrics,
    ban: AccountBanNotice,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val focus = LocalControllerFocus.current
    LaunchedEffect(Unit) { focus?.focus(ACCOUNT_BAN_APPEAL_TAG, reveal = false) }
    val appeal = rememberAccountBanAppeal()
    val reasonAutoSize = remember(metrics) {
        TextAutoSize.StepBased(
            minFontSize = metrics.sp(26f),
            maxFontSize = metrics.sp(40f),
            stepSize = metrics.sp(1f),
        )
    }
    PatternBackground(
        metrics = metrics,
        pattern = Assets.PatternHomeBottom,
        topColor = pocketPalette.background(PocketPassDestination.Home, top = false).top,
        bottomColor = pocketPalette.background(PocketPassDestination.Home, top = false).bottom,
        holdFraction = 0.4375f,
        designWidth = BOTTOM_DESIGN_WIDTH,
        designHeight = BOTTOM_DESIGN_HEIGHT,
    )
    PocketPanel(
        metrics = metrics,
        x = 70f,
        y = 62f,
        width = 1100f,
        height = 700f,
        borderColor = PocketBorder,
        borderWidth = 20.152f,
        radius = 118f,
        fillBrush = PocketWhitePanel,
        shadowAlpha = 0.12f,
        shadowOffset = 14f,
    ) {
        Text(
            text = ACCOUNT_BAN_TITLE,
            modifier = Modifier.designBounds(metrics, 70f, 64f, 960f, 90f),
            style = pocketAuthText(metrics, 64f, PocketTeal, FontWeight.Bold),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = ban.reason,
            modifier = Modifier.designBounds(metrics, 90f, 186f, 920f, 340f),
            style = pocketAuthText(metrics, 40f, PocketGreenText, FontWeight.Medium),
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            autoSize = reasonAutoSize,
        )
        Text(
            text = ban.endsLine(),
            modifier = Modifier.designBounds(metrics, 90f, 556f, 920f, 100f),
            style = pocketAuthText(metrics, 36f, PocketTeal, FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
    AuthButton(
        metrics = metrics,
        y = 812f,
        label = ACCOUNT_BAN_APPEAL_LABEL,
        tag = ACCOUNT_BAN_APPEAL_TAG,
        onClick = appeal,
        x = 102f,
        width = 504f,
        borderColor = PocketDiscordBorder,
        brush = PocketDiscordButton,
        shadowAlpha = AUTH_PRIMARY_SHADOW,
        fontSize = 42f,
    )
    AuthSecondaryButton(
        metrics = metrics,
        y = 812f,
        label = ACCOUNT_BAN_SIGN_OUT_LABEL,
        tag = ACCOUNT_BAN_SIGN_OUT_TAG,
        onClick = { dispatch(PocketPassEvent.SignOut) },
        x = 634f,
        width = 504f,
        fontSize = 42f,
    )
}
