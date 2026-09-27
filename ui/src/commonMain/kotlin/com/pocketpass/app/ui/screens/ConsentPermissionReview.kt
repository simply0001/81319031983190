package com.pocketpass.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.pocketpass.app.domain.model.OAuthConsentRequest
import com.pocketpass.app.domain.model.OAuthConsentScope
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.controller.*
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.theme.pocketPalette

internal const val CONSENT_REVIEW_LAYER = 25

private fun permissionWarning(key: String): String? = when(key) {
    "messages:read" -> "Private messages: this app can read and copy your conversations."
    "messages:write" -> "Acts as you: it can send, edit and delete messages."
    "groups:write" -> "Can change who belongs to your group chats."
    "friends:write" -> "Can add or remove friends and answer requests for you."
    "boards:read" -> "Includes private boards you belong to."
    "boards:write" -> "Can publish, edit and remove notes and replies as you."
    "boards:drafts" -> "Can access and change your unpublished drafts."
    "boards:manage" -> "Can change boards you own, including visibility, staff and ownership."
    "boards:moderate" -> "Can remove content and restrict members where you moderate."
    "boards:purchase" -> "Can spend your PocketPass tokens on stationery."
    "boards:membership" -> "Can join or leave boards and change board notifications."
    "boards:invite" -> "Can invite people and create invitation codes."
    "blocks:write" -> "Can block or unblock people for you."
    "privacy:write" -> "Can change your Block Messages setting."
    "presence:write" -> "Can make you appear online or typing to your friends."
    else -> null
}

private fun permissionGroup(key: String): String = when(key.substringBefore(':')) {
    "messages", "groups" -> "Messages and group chats"
    "boards" -> "Boards"
    "friends", "presence" -> "Friends and online status"
    "blocks", "privacy" -> "Blocking and privacy"
    "profile", "tokens", "encounters", "puzzles", "notifications" -> "Profile and activity"
    else -> "Other permissions"
}

internal fun consentProblem(request: OAuthConsentRequest): String? = when {
    request.suspended -> "This app has been suspended."
    request.infoError != null -> request.infoError
    request.unknownScopes.isNotEmpty() -> "Unknown permissions: ${request.unknownScopes.joinToString()}. This request cannot be allowed."
    else -> null
}

@Composable
internal fun ConsentHeading(m: DesignMetrics, request: OAuthConsentRequest) {
    BoardLabel(m, request.appName, 54f, true)
    BoardLabel(m, "Third-party app · ${request.ownerDisplayName?.let { "Made by $it" } ?: "Not made by PocketPass"}", 30f,
        color = pocketPalette.textSecondary)
    request.website?.let { BoardLabel(m, it, 28f, color = pocketPalette.textSecondary) }
}

@Composable
internal fun ConsentWarning(m: DesignMetrics, request: OAuthConsentRequest) {
    val sensitive = request.scopes.count { permissionWarning(it.key) != null }
    if(sensitive == 0) return
    Column(Modifier.fillMaxWidth().background(pocketPalette.tint(Color(0xFFFFE8C5)), RoundedCornerShape(m.dp(22f)))
        .padding(m.dp(22f)), verticalArrangement = Arrangement.spacedBy(m.dp(10f))) {
        BoardLabel(m, "Sensitive access · $sensitive ${if(sensitive == 1) "permission" else "permissions"}", 34f, true,
            color = pocketPalette.ink(Color(0xFF8C4300)))
        BoardLabel(m, "Only allow an app you trust. These permissions can expose private information or let it act as you.", 30f)
    }
}

@Composable
internal fun ConsentPermissionList(m: DesignMetrics, request: OAuthConsentRequest, wide: Boolean) {
    ConsentWarning(m, request)
    request.scopes.groupBy { permissionGroup(it.key) }.forEach { (group, scopes) ->
        BoardLabel(m, group, 38f, true)
        scopes.chunked(if(wide) 2 else 1).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(m.dp(24f))) {
                row.forEach { scope ->
                    PermissionCard(m, scope, Modifier.weight(1f))
                }
                if(wide && row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    if(request.extraClaims.isNotEmpty()) {
        BoardLabel(m, "Also shared", 38f, true)
        request.extraClaims.forEachIndexed { index, claim ->
            BoardLabel(m, claim, 32f, modifier = Modifier.fillMaxWidth()
                .controllerTarget("consent_claim_$index", layer = CONSENT_REVIEW_LAYER) {})
        }
    }
    if(request.scopes.isEmpty() && request.extraClaims.isEmpty()) BoardLabel(m, "No additional permissions requested.", 32f)
    BoardLabel(m, "Disconnect this app any time in Settings → Social → Connected Apps. Disconnecting stops future access; it cannot erase information the app already copied.", 30f,
        modifier = Modifier.fillMaxWidth().controllerTarget("consent_revoke_info", layer = CONSENT_REVIEW_LAYER) {},
        color = pocketPalette.textSecondary)
}

@Composable
private fun PermissionCard(m: DesignMetrics, scope: OAuthConsentScope, modifier: Modifier) {
    val warning = permissionWarning(scope.key)
    Column(modifier.testTag("consent_scope_${scope.key}")
        .pocketFrame(pocketPalette.surface, m.dp(3f), if(warning != null) pocketPalette.ink(Color(0xFFAE732B)) else pocketPalette.borderSoft,
            RoundedCornerShape(m.dp(22f)))
        .controllerTarget("consent_scope_${scope.key}", layer = CONSENT_REVIEW_LAYER, cornerRadius = 22f) {}
        .padding(m.dp(22f)), verticalArrangement = Arrangement.spacedBy(m.dp(12f))) {
        BoardLabel(m, scope.description.ifBlank { scope.key }, 34f, true)
        if(warning != null) BoardLabel(m, "Sensitive · $warning", 29f, color = pocketPalette.ink(Color(0xFF8C4300)))
    }
}

@Composable
internal fun ConsentScroll(m: DesignMetrics, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val viewport = remember { ControllerFocusViewport() }
    CompositionLocalProvider(LocalControllerFocusViewport provides viewport) {
        Column(modifier.clipToBounds().controllerFocusViewport(viewport).verticalScroll(rememberScrollState())
            .padding(m.dp(10f)), verticalArrangement = Arrangement.spacedBy(m.dp(22f)), content = content)
    }
}

@Composable
fun OAuthConsentTopScreen(m: DesignMetrics, state: PocketPassUiState) {
    Box(Modifier.fillMaxSize().background(pocketPalette.surfaceLow)
        .controllerFocusBarrier("consent_top", CONSENT_REVIEW_LAYER).testTag("oauth_consent_top")) {
        Column(Modifier.designBounds(m, 70f, 50f, 1780f, 980f), verticalArrangement = Arrangement.spacedBy(m.dp(20f))) {
            BoardLabel(m, "Review app permissions", 60f, true)
            val request = state.oauthConsent.request
            if(request == null) BoardLabel(m, "${if(state.oauthConsent.loading) "Loading permissions…" else "Check the request on the lower screen."}", 38f)
            else {
                ConsentHeading(m, request)
                BoardLabel(m, "X: switch screens · D-pad: browse permissions · Decide on the lower screen", 28f, color = pocketPalette.textSecondary)
                ConsentScroll(m, Modifier.weight(1f).fillMaxWidth()) {
                    ConsentPermissionList(m, request, wide = true)
                    consentProblem(request)?.let { BoardLabel(m, it, 32f, true, color = pocketPalette.ink(Color(0xFFB31E3A))) }
                }
            }
        }
    }
}
