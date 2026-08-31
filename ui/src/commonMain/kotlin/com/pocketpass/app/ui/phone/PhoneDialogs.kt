package com.pocketpass.app.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.pocketpass.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pocketpass.app.model.BIO_MAX_LENGTH
import com.pocketpass.app.domain.model.PROFILE_NAME_MAX_LENGTH
import com.pocketpass.app.model.FriendsOverlay
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.BOTTOM_DESIGN_HEIGHT
import com.pocketpass.app.ui.BOTTOM_DESIGN_WIDTH
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.components.pocketFrame
import androidx.compose.ui.layout.ContentScale
import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.mii.MII_FIRST_SLOT
import com.pocketpass.app.mii.MII_SLOT_COUNT
import com.pocketpass.app.mii.MiiEditorEvent
import com.pocketpass.app.mii.PretendoId
import com.pocketpass.app.mii.PretendoImportState
import com.pocketpass.app.ui.screens.ConnectedAppsOverlay
import com.pocketpass.app.ui.screens.MiiSlotsOverlay
import com.pocketpass.app.ui.screens.OAuthConsentOverlay
import com.pocketpass.app.ui.showsPocketPassApp
import com.pocketpass.app.ui.screens.DynamicAvatar
import com.pocketpass.app.ui.screens.cancelButtonBrush
import com.pocketpass.app.ui.screens.greenButtonBrush
import com.pocketpass.app.ui.screens.greyButtonBrush
import com.pocketpass.app.ui.screens.greyPanelBrush
import com.pocketpass.app.ui.theme.pocketPalette

private val FriendsBorder = Color(0xFFCB4AC0)

@Composable
fun PhoneDialogs(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val root = state.rootDestination
    val buyItem = state.shop.buyPromptItem
    val retainedBuy = remember { mutableStateOf(buyItem) }
    if (buyItem != null) retainedBuy.value = buyItem
    PhoneConfirmDialog(
        metrics = metrics,
        visible = root == PocketPassDestination.Activities && buyItem != null,
        tag = "buy_shop_item",
        title = "Buy ${retainedBuy.value?.name.orEmpty()}?",
        body = "It costs ${retainedBuy.value?.priceTokens ?: 0} tokens. You have ${state.shop.tokenBalance}.",
        confirmLabel = "Buy",
        confirmFill = com.pocketpass.app.ui.screens.greenButtonBrush(),
        confirmBorder = PhoneGreenBorder,
        onCancel = { dispatch(PocketPassEvent.CloseBuyShopItem) },
        onConfirm = { dispatch(PocketPassEvent.ConfirmBuyShopItem) },
    )

    val friendName = state.profileViewer.profile?.displayName?.takeIf { it.isNotBlank() } ?: "This friend"
    PhoneConfirmDialog(
        metrics = metrics,
        visible = state.removeFriendPromptVisible,
        tag = "remove_friend",
        title = "Remove this friend?",
        body = "$friendName will be removed from your friends. You can add them again with a friend code.",
        confirmLabel = "Remove",
        confirmEnabled = !state.profileViewer.actionInProgress,
        onCancel = { dispatch(PocketPassEvent.CloseRemoveFriend) },
        onConfirm = { dispatch(PocketPassEvent.RemoveProfileFriend) },
    )

    PhoneMessageActionsSheet(
        metrics = metrics,
        visible = state.routes.lastOrNull() is PocketPassRoute.MessageDetail &&
            state.messageActionMessageId != null,
        message = state.selectedMessages.firstOrNull { it.id.value == state.messageActionMessageId },
        dispatch = dispatch,
    )

    PhoneGroupInfoSheet(metrics, state, dispatch)

    PhoneConfirmDialog(
        metrics = metrics,
        visible = root == PocketPassDestination.Settings && state.deleteAccountVisible,
        tag = "delete_account",
        title = "Delete Account?",
        body = "This erases your profile, friends and messages from PocketPass. It cannot be undone.",
        error = state.deleteAccountError,
        confirmLabel = if (state.deleteAccountInProgress) "Deleting..." else "Delete Forever",
        confirmEnabled = !state.deleteAccountInProgress,
        onCancel = { dispatch(PocketPassEvent.CloseDeleteAccount) },
        onConfirm = { dispatch(PocketPassEvent.ConfirmDeleteAccount) },
    )

    PhoneAddFriendDialog(
        metrics = metrics,
        visible = root == PocketPassDestination.Friends && state.friendsOverlay == FriendsOverlay.AddFriend,
        state = state,
        dispatch = dispatch,
    )

    PhoneBioEditorDialog(
        metrics = metrics,
        visible = root == PocketPassDestination.Home && state.bioEditor.visible,
        state = state,
        dispatch = dispatch,
    )

    PhoneNameEditorDialog(
        metrics = metrics,
        visible = root == PocketPassDestination.Settings && state.nameEditor.visible,
        state = state,
        dispatch = dispatch,
    )

    if (root == PocketPassDestination.Settings && state.miiSlotsVisible) {
        PhoneScrim(visible = true, tag = "mii_slots_scrim", onDismiss = { dispatch(PocketPassEvent.CloseMiiSlots) })
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(metrics.dp(BOTTOM_DESIGN_WIDTH), metrics.dp(BOTTOM_DESIGN_HEIGHT))) {
                MiiSlotsOverlay(metrics, state, dispatch, showPretendoDialog = false)
            }
        }
    }

    PhonePretendoImportDialog(
        metrics = metrics,
        visible = root == PocketPassDestination.Settings &&
            state.miiSlotsVisible &&
            state.miiEditor.pretendoImport != null,
        state = state,
        dispatch = dispatch,
    )

    if (root == PocketPassDestination.Settings && state.connectedApps.visible) {
        PhoneScrim(
            visible = true,
            tag = "connected_apps_scrim",
            onDismiss = { dispatch(PocketPassEvent.CloseConnectedApps) },
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(metrics.dp(BOTTOM_DESIGN_WIDTH), metrics.dp(BOTTOM_DESIGN_HEIGHT))) {
                ConnectedAppsOverlay(metrics, state, dispatch, showRevokeDialog = false)
            }
        }
    }

    PhoneConfirmDialog(
        metrics = metrics,
        visible = root == PocketPassDestination.Settings && state.connectedApps.revokeClientId != null,
        tag = "connected_app_revoke",
        title = "Disconnect ${state.connectedApps.revokeTarget?.name ?: "this app"}?",
        body = "It loses access to your account immediately and must ask again to reconnect.",
        error = state.connectedApps.revokeError,
        confirmLabel = if (state.connectedApps.revokeInProgress) "Disconnecting..." else "Disconnect",
        confirmEnabled = !state.connectedApps.revokeInProgress,
        onCancel = { dispatch(PocketPassEvent.CloseRevokeConnectedApp) },
        onConfirm = { dispatch(PocketPassEvent.ConfirmRevokeConnectedApp) },
    )

    if (state.sessionState.showsPocketPassApp() && state.oauthConsent.visible) {
        PhoneScrim(
            visible = true,
            tag = "oauth_consent_scrim",
            onDismiss = { dispatch(PocketPassEvent.DismissOAuthConsent) },
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(metrics.dp(BOTTOM_DESIGN_WIDTH), metrics.dp(BOTTOM_DESIGN_HEIGHT))) {
                OAuthConsentOverlay(metrics, state, dispatch)
            }
        }
    }
}

@Composable
private fun DialogTitleRow(
    metrics: DesignMetrics,
    title: String,
    color: Color,
    closeTag: String,
    onClose: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = color,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .requiredSize(metrics.dp(84f))
                .clip(CircleShape)
                .testTag(closeTag)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        ) { CloseGlyph(metrics, color) }
    }
}

@Composable
private fun PhoneAddFriendDialog(
    metrics: DesignMetrics,
    visible: Boolean,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    val titleColor = palette.ink(Color(0xFF511D6B))
    val clipboardManager = LocalClipboardManager.current
    val code = state.myFriendCode
    val canSubmit = state.friendCodeEntry.length == 8 && !state.friendCodeSubmitting
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(visible) { if (visible) runCatching { focusRequester.requestFocus() } }
    PhoneDialog(
        metrics = metrics,
        visible = visible,
        tag = "add_friend",
        onDismiss = { dispatch(PocketPassEvent.CloseFriendsOverlay) },
        borderColor = FriendsBorder,
        fill = Brush.verticalGradient(
            colorStops = arrayOf(0f to palette.surface, 0.58f to palette.surface, 1f to palette.tint(Color(0xFFFED3FF))),
        ),
    ) {
        DialogTitleRow(metrics, "Add Friend", titleColor, "close_add_friend") { dispatch(PocketPassEvent.CloseFriendsOverlay) }
        Spacer(Modifier.height(metrics.dp(30f)))
        Text(text = "Your Friend Code", color = titleColor, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = metrics.sp(43f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = code?.formatted ?: "•••• ••••",
                modifier = Modifier.weight(1f),
                color = titleColor,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(64f),
                letterSpacing = metrics.sp(4f),
                maxLines = 1,
            )
            PhoneButton(
                metrics = metrics,
                label = "Copy",
                modifier = Modifier.width(metrics.dp(238f)),
                fill = greyPanelBrush(),
                borderColor = FriendsBorder,
                textColor = titleColor,
                enabled = code != null,
                height = 96f,
                radius = 45f,
                fontSize = 38f,
                tag = "copy_friend_code",
            ) {
                clipboardManager.setText(AnnotatedString(code?.formatted.orEmpty()))
            }
        }
        Spacer(Modifier.height(metrics.dp(34f)))
        Text(text = "Enter Friend Code", color = titleColor, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = metrics.sp(43f))
        Spacer(Modifier.height(metrics.dp(18f)))
        PhoneDigitSlots(
            metrics = metrics,
            value = state.friendCodeEntry,
            length = 8,
            slotWidth = 105.5f,
            slotHeight = 118f,
            gap = 16f,
            filledBorder = Color(0xFF73E881),
            emptyBorder = FriendsBorder,
            textColor = titleColor,
            tag = "friend_code_field",
            onValueChange = { dispatch(PocketPassEvent.UpdateFriendCode(it)) },
            onDone = { if (canSubmit) dispatch(PocketPassEvent.SubmitFriendCode) },
            focusRequester = focusRequester,
        )
        Spacer(Modifier.height(metrics.dp(34f)))
        PhoneButton(
            metrics = metrics,
            label = if (state.friendCodeSubmitting) "Finding…" else "Add Friend",
            modifier = Modifier.fillMaxWidth(),
            enabled = canSubmit,
            height = 116f,
            radius = 58f,
            tag = "submit_friend_code",
        ) { dispatch(PocketPassEvent.SubmitFriendCode) }
        val message = state.friendCodeError ?: state.friendCodeMessage
        if (message != null) {
            Spacer(Modifier.height(metrics.dp(20f)))
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth(),
                color = if (state.friendCodeError != null) palette.ink(Color(0xFFB31E3A)) else titleColor,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(30f),
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
fun PhoneDigitSlots(
    metrics: DesignMetrics,
    value: String,
    length: Int,
    slotWidth: Float,
    slotHeight: Float,
    gap: Float,
    filledBorder: Color,
    emptyBorder: Color,
    textColor: Color,
    tag: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val digits = value.filter(Char::isDigit).take(length)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(gap))) {
            repeat(length) { index ->
                val digit = digits.getOrNull(index)?.toString().orEmpty()
                val active = index == digits.length.coerceAtMost(length - 1)
                val shape = RoundedCornerShape(metrics.dp(24f))
                Box(
                    modifier = Modifier
                        .size(metrics.dp(slotWidth), metrics.dp(slotHeight))
                        .clip(shape)
                        .pocketFrame(
                            pocketPalette.surface,
                            metrics.dp(if (active && digit.isEmpty()) 8f else 6f),
                            when {
                                digit.isNotEmpty() -> filledBorder
                                active -> pocketPalette.tealBorder
                                else -> emptyBorder.copy(alpha = 0.7f)
                            },
                            shape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = digit,
                        color = textColor,
                        fontFamily = Rubik,
                        fontWeight = FontWeight.Bold,
                        fontSize = metrics.sp(64f),
                        maxLines = 1,
                    )
                }
            }
        }
        val field = remember { mutableStateOf(TextFieldValue(digits, TextRange(digits.length))) }
        if (field.value.text != digits) field.value = TextFieldValue(digits, TextRange(digits.length))
        BasicTextField(
            value = field.value,
            onValueChange = {
                val next = it.text.filter(Char::isDigit).take(length)
                field.value = TextFieldValue(next, TextRange(next.length))
                if (next != digits) onValueChange(next)
            },
            modifier = Modifier
                .matchParentSize()
                .alpha(0f)
                .testTag(tag)
                .then(if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester)),
            textStyle = TextStyle(color = Color.Transparent, fontSize = metrics.sp(1f)),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            singleLine = true,
        )
    }
}

@Composable
private fun PhoneNameEditorDialog(
    metrics: DesignMetrics,
    visible: Boolean,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    val editor = state.nameEditor
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(visible) { if (visible) runCatching { focusRequester.requestFocus() } }
    val canSave = editor.valid && !editor.saving
    val short = phoneShortViewport(metrics)
    PhoneDialog(
        metrics = metrics,
        visible = visible,
        tag = "name_editor",
        onDismiss = { dispatch(PocketPassEvent.CloseNameEditor) },
        borderColor = palette.tealBorder,
        fill = Brush.verticalGradient(
            colorStops = arrayOf(0f to palette.surface, 0.62f to palette.surface, 1f to palette.tint(Color(0xFFBDF8CB))),
        ),
    ) {
        DialogTitleRow(metrics, "Edit Name", palette.teal, "close_name_editor") { dispatch(PocketPassEvent.CloseNameEditor) }
        Spacer(Modifier.height(metrics.dp(if (short) 12f else 20f)))
        Text(
            text = editor.error ?: "The name everyone will see",
            modifier = Modifier.fillMaxWidth(),
            color = if (editor.error != null) palette.ink(Color(0xFFB31E3A)) else palette.tealSoft,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(metrics.dp(if (short) 16f else 24f)))
        PhoneTextField(
            metrics = metrics,
            value = editor.draft,
            onValueChange = { dispatch(PocketPassEvent.UpdateNameDraft(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "yourname",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (canSave) dispatch(PocketPassEvent.SaveName) }),
            textAlign = TextAlign.Center,
            tag = "name_editor_field",
            focusRequester = focusRequester,
        )
        Spacer(Modifier.height(metrics.dp(16f)))
        Text(
            text = "${editor.draft.length}/$PROFILE_NAME_MAX_LENGTH",
            modifier = Modifier.fillMaxWidth(),
            color = palette.tealSoft,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(30f),
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        Spacer(Modifier.height(metrics.dp(if (short) 20f else 30f)))
        PhoneButton(
            metrics = metrics,
            label = if (editor.saving) "Saving…" else "Save",
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave,
            height = 130f,
            tag = "name_editor_save",
        ) { dispatch(PocketPassEvent.SaveName) }
    }
}

@Composable
private fun PhonePretendoImportDialog(
    metrics: DesignMetrics,
    visible: Boolean,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    val import = state.miiEditor.pretendoImport
        ?: PretendoImportState(slot = state.miiEditor.activeSlot)
    val found = import.found
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(visible, found == null) {
        if (visible && found == null) runCatching { focusRequester.requestFocus() }
    }
    val short = phoneShortViewport(metrics)
    val close = { dispatch(PocketPassEvent.Mii(MiiEditorEvent.ClosePretendoImport)) }
    val lookup = { dispatch(PocketPassEvent.Mii(MiiEditorEvent.LookupPretendoMii)) }
    PhoneDialog(
        metrics = metrics,
        visible = visible,
        tag = "pretendo_import",
        onDismiss = if (import.lookingUp) null else close,
        borderColor = palette.tealBorder,
        fill = Brush.verticalGradient(
            colorStops = arrayOf(0f to palette.surface, 0.62f to palette.surface, 1f to palette.tint(Color(0xFFD6ECF5))),
        ),
    ) {
        DialogTitleRow(metrics, "Import from Pretendo", palette.teal, "close_pretendo_import", close)
        Spacer(Modifier.height(metrics.dp(if (short) 12f else 20f)))
        if (found == null) {
            Text(
                text = import.error ?: "Type a Pretendo Network ID to copy its Mii",
                modifier = Modifier.fillMaxWidth(),
                color = if (import.error != null) palette.ink(Color(0xFFB31E3A)) else palette.tealSoft,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(34f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(metrics.dp(if (short) 16f else 24f)))
            PhoneTextField(
                metrics = metrics,
                value = import.pnid,
                onValueChange = { dispatch(PocketPassEvent.Mii(MiiEditorEvent.SetPretendoId(it))) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "pretendo id",
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (import.canLookup) lookup() }),
                textAlign = TextAlign.Center,
                tag = "pretendo_id_field",
                focusRequester = focusRequester,
            )
            Spacer(Modifier.height(metrics.dp(16f)))
            Text(
                text = "${import.pnid.length}/${PretendoId.MAX_LENGTH}",
                modifier = Modifier.fillMaxWidth(),
                color = palette.tealSoft,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(30f),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
            Spacer(Modifier.height(metrics.dp(if (short) 20f else 30f)))
            PhoneButton(
                metrics = metrics,
                label = if (import.lookingUp) "Looking up…" else "Look up",
                modifier = Modifier.fillMaxWidth(),
                enabled = import.canLookup,
                height = 130f,
                tag = "pretendo_lookup",
                onClick = lookup,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(metrics.dp(200f))
                        .clip(CircleShape)
                        .background(palette.surfaceSunken)
                        .testTag("pretendo_preview"),
                ) {
                    DynamicAvatar(
                        avatar = AvatarReference.Remote(found.portraitUrl),
                        fallbackResource = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                Spacer(Modifier.width(metrics.dp(30f)))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = found.miiName.ifBlank { found.pnid },
                        color = palette.teal,
                        fontFamily = Rubik,
                        fontWeight = FontWeight.Bold,
                        fontSize = metrics.sp(52f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "@${found.pnid}",
                        color = palette.tealSoft,
                        fontFamily = Rubik,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = metrics.sp(32f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(metrics.dp(if (short) 16f else 24f)))
            Text(
                text = "Choose the Piip slot it goes into",
                modifier = Modifier.fillMaxWidth(),
                color = palette.tealSoft,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(32f),
                maxLines = 1,
            )
            Spacer(Modifier.height(metrics.dp(16f)))
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(16f))) {
                (MII_FIRST_SLOT..MII_SLOT_COUNT).forEach { slot ->
                    val selected = slot == import.slot
                    PhoneButton(
                        metrics = metrics,
                        label = "Piip $slot",
                        modifier = Modifier.weight(1f),
                        fill = if (selected) greenButtonBrush() else greyButtonBrush(),
                        borderColor = if (selected) PhoneGreenBorder else Color(0xFF8A8A8A),
                        height = 110f,
                        fontSize = 36f,
                        tag = "pretendo_slot_$slot",
                    ) { dispatch(PocketPassEvent.Mii(MiiEditorEvent.SelectPretendoImportSlot(slot))) }
                }
            }
            Spacer(Modifier.height(metrics.dp(12f)))
            val targetEmpty = state.miiEditor.slots[import.slot]?.isEmpty != false
            Text(
                text = (if (targetEmpty) "Piip ${import.slot} is empty" else "Piip ${import.slot} will be replaced") +
                    " · Hats aren't imported",
                modifier = Modifier.fillMaxWidth(),
                color = palette.tealSoft,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(28f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(metrics.dp(if (short) 20f else 30f)))
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(20f))) {
                PhoneButton(
                    metrics = metrics,
                    label = "Change",
                    modifier = Modifier.weight(1f),
                    fill = cancelButtonBrush(),
                    borderColor = Color(0xFF8A8A8A),
                    height = 130f,
                    tag = "pretendo_import_change",
                ) { dispatch(PocketPassEvent.Mii(MiiEditorEvent.SetPretendoId(import.pnid))) }
                PhoneButton(
                    metrics = metrics,
                    label = "Import & edit",
                    modifier = Modifier.weight(1f),
                    height = 130f,
                    tag = "pretendo_import_confirm",
                ) { dispatch(PocketPassEvent.Mii(MiiEditorEvent.ConfirmPretendoImport)) }
            }
        }
    }
}

@Composable
private fun PhoneBioEditorDialog(
    metrics: DesignMetrics,
    visible: Boolean,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    val editor = state.bioEditor
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(visible) { if (visible) runCatching { focusRequester.requestFocus() } }
    val canSave = editor.draft.isNotBlank() && !editor.saving
    val short = phoneShortViewport(metrics)
    PhoneDialog(
        metrics = metrics,
        visible = visible,
        tag = "bio_editor",
        onDismiss = { dispatch(PocketPassEvent.CloseBioEditor) },
        borderColor = palette.tealBorder,
        fill = Brush.verticalGradient(
            colorStops = arrayOf(0f to palette.surface, 0.62f to palette.surface, 1f to palette.tint(Color(0xFFBDF8CB))),
        ),
    ) {
        DialogTitleRow(metrics, "Edit Bio", palette.teal, "close_bio_editor") { dispatch(PocketPassEvent.CloseBioEditor) }
        Spacer(Modifier.height(metrics.dp(if (short) 20f else 30f)))
        PhoneTextField(
            metrics = metrics,
            value = editor.draft,
            onValueChange = { dispatch(PocketPassEvent.UpdateBioDraft(it.take(BIO_MAX_LENGTH))) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Say hello to everyone you meet!",
            fontSize = 38f,
            fontWeight = FontWeight.SemiBold,
            placeholderColor = palette.ink(Color(0xFF8FB9C6)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSave) dispatch(PocketPassEvent.SaveBio) }),
            singleLine = false,
            maxLines = if (short) 2 else 4,
            minHeight = if (short) 150f else 230f,
            radius = 45f,
            borderWidth = 8f,
            fill = Brush.verticalGradient(listOf(palette.surfaceSunken, palette.surfaceSunken)),
            horizontalPadding = 36f,
            verticalPadding = 26f,
            tag = "bio_editor_field",
            focusRequester = focusRequester,
        )
        Spacer(Modifier.height(metrics.dp(16f)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = editor.error.orEmpty(),
                modifier = Modifier.weight(1f),
                color = palette.ink(Color(0xFFB31E3A)),
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(30f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${editor.draft.length}/$BIO_MAX_LENGTH",
                color = if (editor.draft.length >= BIO_MAX_LENGTH) palette.ink(Color(0xFFB31E3A)) else palette.tealSoft,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(30f),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(metrics.dp(if (short) 20f else 30f)))
        PhoneButton(
            metrics = metrics,
            label = if (editor.saving) "Saving…" else "Save",
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave,
            height = 130f,
            tag = "save_bio",
        ) { dispatch(PocketPassEvent.SaveBio) }
    }
}
