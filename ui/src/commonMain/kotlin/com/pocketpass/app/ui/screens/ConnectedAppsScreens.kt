package com.pocketpass.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.formatInstant
import io.ktor.http.Url
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pocketpass.app.domain.model.ConnectedApp
import com.pocketpass.app.domain.model.OAuthConsentRequest
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignAnchor
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.anchoredBounds
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.PocketPanel
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.components.pocketShadow
import com.pocketpass.app.ui.controller.controllerFocusBarrier
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.theme.pocketPalette

private const val CONNECTED_APPS_FOCUS_LAYER = 15
private const val CONNECTED_APPS_CONFIRM_FOCUS_LAYER = 20
private const val OAUTH_CONSENT_FOCUS_LAYER = 25
private const val CONNECTED_APP_CARD_HEIGHT = 160f
private const val CONNECTED_APP_CARD_GAP = 20f

@Composable
internal fun ConnectedAppsPanel(metrics: DesignMetrics, y: Float, onOpen: () -> Unit) {
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
        tag = "connected_apps",
        onClick = onOpen,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsConnectedApps,
            title = "Connected Apps",
            subtitle = "Apps that use your account",
        )
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
        )
    }
}

@Composable
fun ConnectedAppsOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    showRevokeDialog: Boolean = true,
) {
    val apps = state.connectedApps
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 0f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        )
    }
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .background(pocketPalette.scrim)
            .testTag("connected_apps_overlay")
            .controllerFocusBarrier("connected_apps_overlay", layer = CONNECTED_APPS_FOCUS_LAYER)
            .clickable(
                enabled = !apps.revokeInProgress,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { dispatch(PocketPassEvent.CloseConnectedApps) },
    )
    Box(
        Modifier
            .designBounds(metrics, 80f, 204f, 1080f, 700f)
            .graphicsLayer { translationY = entrance.value }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 190f, 1080f, 700f)
            .graphicsLayer { translationY = entrance.value }
            .clip(panelShape)
            .pocketFrame(greyPanelBrush(), metrics.dp(15f), pocketPalette.borderGrey, panelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("connected_apps_panel"),
    ) {
        Text(
            text = "Connected Apps",
            modifier = Modifier.designBounds(metrics, 60f, 48f, 960f, 90f),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(70f),
            maxLines = 1,
        )
        Text(
            text = "Apps and websites that can use your account.",
            modifier = Modifier.designBounds(metrics, 60f, 142f, 960f, 46f),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(32f),
            maxLines = 1,
        )
        val statusText = when {
            apps.loading && apps.apps.isEmpty() -> "Loading…"
            apps.error != null && apps.apps.isEmpty() -> apps.error
            apps.apps.isEmpty() -> "No apps are connected."
            else -> null
        }
        if (statusText != null) {
            Text(
                text = statusText,
                modifier = Modifier.designBounds(metrics, 60f, 380f, 960f, 60f),
                color = if (apps.error != null) {
                    pocketPalette.ink(Color(0xFFB31E3A))
                } else {
                    pocketPalette.textSecondary
                },
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(36f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Column(
                Modifier
                    .designBounds(metrics, 60f, 210f, 960f, 460f)
                    .clip(RoundedCornerShape(metrics.dp(40f)))
                    .verticalScroll(rememberScrollState()),
            ) {
                apps.apps.forEachIndexed { index, app ->
                    if (index > 0) Spacer(Modifier.requiredHeight(metrics.dp(CONNECTED_APP_CARD_GAP)))
                    ConnectedAppCard(
                        metrics = metrics,
                        app = app,
                        enabled = apps.revokeClientId == null,
                        onDisconnect = { dispatch(PocketPassEvent.OpenRevokeConnectedApp(app.clientId)) },
                    )
                }
            }
        }
    }
    if (showRevokeDialog && apps.revokeClientId != null) {
        ConnectedAppRevokeConfirmDialog(metrics, state, dispatch)
    }
}

@Composable
private fun ConnectedAppCard(
    metrics: DesignMetrics,
    app: ConnectedApp,
    enabled: Boolean,
    onDisconnect: () -> Unit,
) {
    val cardShape = RoundedCornerShape(metrics.dp(40f))
    Box(
        Modifier
            .requiredWidth(metrics.dp(960f))
            .requiredHeight(metrics.dp(CONNECTED_APP_CARD_HEIGHT))
            .clip(cardShape)
            .pocketFrame(pocketPalette.surface, metrics.dp(6f), pocketPalette.borderSoft, cardShape)
            .testTag("connected_app_${app.clientId}"),
    ) {
        Text(
            text = app.name,
            modifier = Modifier.designBounds(metrics, 36f, 18f, 600f, 54f),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(42f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(websiteHost(app.website), "Connected " + connectedDate(app))
                .joinToString(" · "),
            modifier = Modifier.designBounds(metrics, 36f, 74f, 600f, 38f),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(27f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = scopeSummary(app.scopes),
            modifier = Modifier.designBounds(metrics, 36f, 110f, 600f, 38f),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(27f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val buttonShape = RoundedCornerShape(metrics.dp(118f))
        Box(
            modifier = Modifier
                .designBounds(metrics, 668f, 35f, 256f, 90f)
                .clip(buttonShape)
                .pocketFrame(redButtonBrush(), metrics.dp(14f), Color(0xFFC24B4B), buttonShape)
                .testTag("connected_app_disconnect_${app.clientId}")
                .controllerTarget(
                    "connected_app_disconnect_${app.clientId}",
                    layer = CONNECTED_APPS_FOCUS_LAYER,
                ) { if (enabled) onDisconnect() }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                ) { onDisconnect() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Disconnect",
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(32f),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun ConnectedAppRevokeConfirmDialog(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val apps = state.connectedApps
    val busy = apps.revokeInProgress
    val name = apps.revokeTarget?.name ?: "this app"
    DialogFocusHandoff("connected_app_revoke_cancel")
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 0f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        )
    }
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .background(pocketPalette.scrim)
            .testTag("connected_app_revoke_overlay")
            .controllerFocusBarrier("connected_app_revoke_overlay", layer = CONNECTED_APPS_CONFIRM_FOCUS_LAYER)
            .clickable(
                enabled = !busy,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { dispatch(PocketPassEvent.CloseRevokeConnectedApp) },
    )
    Box(
        Modifier
            .designBounds(metrics, 80f, 294f, 1080f, 500f)
            .graphicsLayer { translationY = entrance.value }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 280f, 1080f, 500f)
            .graphicsLayer { translationY = entrance.value }
            .clip(panelShape)
            .pocketFrame(greyPanelBrush(), metrics.dp(15f), pocketPalette.borderGrey, panelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("connected_app_revoke_panel"),
    ) {
        Text(
            text = "Disconnect $name?",
            modifier = Modifier.designBounds(metrics, 60f, 44f, 960f, 90f),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(64f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "It loses access to your account immediately and must ask again to reconnect.",
            modifier = Modifier.designBounds(metrics, 90f, 148f, 900f, 96f),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            textAlign = TextAlign.Center,
        )
        apps.revokeError?.let { error ->
            Text(
                text = error,
                modifier = Modifier.designBounds(metrics, 90f, 248f, 900f, 36f),
                color = pocketPalette.ink(Color(0xFFB31E3A)),
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(28f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DialogButton(
            metrics = metrics,
            x = 60f,
            y = 300f,
            label = "Cancel",
            fill = cancelButtonBrush(),
            border = Color(0xFF8A8A8A),
            tag = "connected_app_revoke_cancel",
            layer = CONNECTED_APPS_CONFIRM_FOCUS_LAYER,
            enabled = !busy,
        ) { dispatch(PocketPassEvent.CloseRevokeConnectedApp) }
        DialogButton(
            metrics = metrics,
            x = 550f,
            y = 300f,
            label = if (busy) "Disconnecting..." else "Disconnect",
            fill = redButtonBrush(),
            border = Color(0xFFC24B4B),
            tag = "connected_app_revoke_confirm",
            layer = CONNECTED_APPS_CONFIRM_FOCUS_LAYER,
            enabled = !busy,
        ) { dispatch(PocketPassEvent.ConfirmRevokeConnectedApp) }
    }
}

@Composable
fun OAuthConsentOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    detailsOnTop: Boolean = false,
) {
    val consent = state.oauthConsent
    val busy = consent.deciding
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 0f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        )
    }
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .background(pocketPalette.scrim)
            .testTag("oauth_consent_overlay")
            .controllerFocusBarrier("oauth_consent_overlay", layer = OAUTH_CONSENT_FOCUS_LAYER)
            .clickable(
                enabled = !busy,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { dispatch(PocketPassEvent.DismissOAuthConsent) },
    )
    Box(
        Modifier
            .designBounds(metrics, 80f, 114f, 1080f, 880f)
            .graphicsLayer { translationY = entrance.value }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 100f, 1080f, 880f)
            .graphicsLayer { translationY = entrance.value }
            .clip(panelShape)
            .pocketFrame(greyPanelBrush(), metrics.dp(15f), pocketPalette.borderGrey, panelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("oauth_consent_panel"),
    ) {
        val request = consent.request
        when {
            consent.loading || (request == null && consent.error == null) -> {
                Text(
                    text = "Checking the request…",
                    modifier = Modifier.designBounds(metrics, 60f, 400f, 960f, 80f),
                    color = pocketPalette.textSecondary,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = metrics.sp(40f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }

            request == null -> {
                Text(
                    text = "This request cannot be completed",
                    modifier = Modifier.designBounds(metrics, 60f, 260f, 960f, 90f),
                    color = pocketPalette.textPrimary,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Bold,
                    fontSize = metrics.sp(56f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Text(
                    text = consent.error.orEmpty(),
                    modifier = Modifier.designBounds(metrics, 90f, 370f, 900f, 150f),
                    color = pocketPalette.textSecondary,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = metrics.sp(34f),
                    textAlign = TextAlign.Center,
                )
                DialogButton(
                    metrics = metrics,
                    x = 305f,
                    y = 640f,
                    label = "Close",
                    fill = cancelButtonBrush(),
                    border = Color(0xFF8A8A8A),
                    tag = "oauth_consent_close",
                    layer = OAUTH_CONSENT_FOCUS_LAYER,
                    enabled = true,
                ) { dispatch(PocketPassEvent.DismissOAuthConsent) }
            }

            else -> OAuthConsentContent(metrics, request, consent.error, busy, dispatch, detailsOnTop)
        }
    }
}

@Composable
private fun OAuthConsentContent(
    metrics: DesignMetrics,
    request: OAuthConsentRequest,
    error: String?,
    busy: Boolean,
    dispatch: (PocketPassEvent) -> Unit,
    detailsOnTop: Boolean,
) {
    ConsentScroll(metrics, Modifier.designBounds(metrics, 60f, 30f, 960f, 590f).testTag("oauth_consent_permissions")) {
        if(detailsOnTop) {
            BoardLabel(metrics, request.appName, 52f, true)
            BoardLabel(metrics, "Review permissions on the top screen", 36f, true)
            BoardLabel(metrics, "Press X to move the highlighter there, then use the D-pad to read the list.", 30f,
                color = pocketPalette.textSecondary)
            ConsentWarning(metrics, request)
            BoardLabel(metrics, "Allow shares all ${request.scopes.size} requested permissions with this app.", 32f)
        } else {
            ConsentHeading(metrics, request)
            ConsentPermissionList(metrics, request, wide = false)
        }
        (error ?: consentProblem(request))?.let {
            BoardLabel(metrics, it, 30f, true, color = pocketPalette.ink(Color(0xFFB31E3A)))
        }
    }
    Text(
        text = "Return to ${request.returnHost}",
        modifier = Modifier.designBounds(metrics, 70f, 640f, 940f, 44f),
        color = pocketPalette.textSecondary,
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = metrics.sp(28f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    DialogButton(metrics, 60f, 710f, if(request.allowable) "Deny" else "Go back to the app",
        cancelButtonBrush(), Color(0xFF8A8A8A), "oauth_consent_deny", OAUTH_CONSENT_FOCUS_LAYER, !busy) {
        dispatch(PocketPassEvent.DenyOAuthConsent)
    }
    if(request.allowable) {
        DialogButton(metrics, 550f, 710f, if(busy) "Connecting..." else "Allow",
            greenButtonBrush(), Color(0xFF3CBC29), "oauth_consent_allow", OAUTH_CONSENT_FOCUS_LAYER, !busy) {
            dispatch(PocketPassEvent.ApproveOAuthConsent)
        }
    }
}

@Composable
private fun DialogButton(
    metrics: DesignMetrics,
    x: Float,
    y: Float,
    label: String,
    fill: Brush,
    border: Color,
    tag: String,
    layer: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val buttonShape = RoundedCornerShape(metrics.dp(118f))
    Box(
        modifier = Modifier
            .designBounds(metrics, x, y, 470f, 150f)
            .clip(buttonShape)
            .pocketFrame(fill, metrics.dp(20.152f), border, buttonShape)
            .testTag(tag)
            .controllerTarget(tag, layer = layer) { if (enabled) onClick() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(if (label.length > 12) 36f else 44f),
            maxLines = 1,
        )
    }
}

private fun websiteHost(value: String?): String? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    if ("://" !in trimmed) return trimmed
    return runCatching { Url(trimmed).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: trimmed
}

private fun connectedDate(app: ConnectedApp): String =
    formatInstant(app.grantedAt, "d MMM yyyy")

private fun scopeSummary(scopes: List<String>): String {
    val labels = scopes
        .map { it.substringBefore(':') }
        .distinct()
        .map { it.replaceFirstChar { first -> first.uppercase() } }
    return if (labels.isEmpty()) "No permissions" else "Can use: " + labels.joinToString(" · ")
}
