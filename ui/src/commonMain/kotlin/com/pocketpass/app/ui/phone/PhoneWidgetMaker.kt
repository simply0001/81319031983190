package com.pocketpass.app.ui.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.WidgetSlot
import com.pocketpass.app.model.editingWidgetDesign
import com.pocketpass.app.model.widgetPreviewSnapshot
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.screens.SETTINGS_ROW_HEIGHT
import com.pocketpass.app.ui.screens.WIDGET_BANNER_HEIGHT
import com.pocketpass.app.ui.screens.WIDGET_BUTTON_HEIGHT
import com.pocketpass.app.ui.screens.WIDGET_MESSAGE_HEIGHT
import com.pocketpass.app.ui.screens.WidgetActionButton
import com.pocketpass.app.ui.screens.WidgetAssignBanner
import com.pocketpass.app.ui.screens.WidgetBlockOption
import com.pocketpass.app.ui.screens.WidgetDesignRow
import com.pocketpass.app.ui.screens.WidgetEmptyPanel
import com.pocketpass.app.ui.screens.WidgetMessagePanel
import com.pocketpass.app.ui.screens.WidgetPreviewPanel
import com.pocketpass.app.ui.screens.WidgetRenamePanel
import com.pocketpass.app.ui.screens.WidgetSizePanel
import com.pocketpass.app.ui.screens.WidgetSlotPanel
import com.pocketpass.app.ui.screens.blockAt
import com.pocketpass.app.ui.screens.greenButtonBrush
import com.pocketpass.app.ui.screens.label
import com.pocketpass.app.ui.screens.redButtonBrush
import com.pocketpass.app.ui.screens.widgetBlockChoices
import com.pocketpass.app.ui.screens.widgetPreviewPanelHeight
import com.pocketpass.app.ui.theme.pocketPalette
import com.pocketpass.app.widget.WidgetDesign
import kotlin.time.Clock

private val PhoneWidgetGreenBorder = androidx.compose.ui.graphics.Color(0xFF3CBC29)
private val PhoneWidgetRedBorder = androidx.compose.ui.graphics.Color(0xFFC24B4B)

@Composable
internal fun PhoneWidgetsPage(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val assigning = state.widgetMaker.assigningAppWidgetId != null
    val snapshot = remember(state) { state.widgetPreviewSnapshot(Clock.System.now().toEpochMilliseconds()) }
    PhoneSubpage(
        metrics = metrics,
        title = "Widgets",
        subtitle = if (assigning) "Pick a design for your new widget." else "Design one, then add it to your home screen.",
        backTag = "widgets_back",
        onBack = { dispatch(PocketPassEvent.Back) },
    ) {
        var order = 0
        if (assigning) {
            PhoneSubpageRow(metrics, order = order++, height = WIDGET_BANNER_HEIGHT) {
                WidgetAssignBanner(metrics, 0f)
            }
        }
        state.widgetMaker.message?.let { message ->
            PhoneSubpageRow(metrics, order = order++, height = WIDGET_MESSAGE_HEIGHT) {
                WidgetMessagePanel(metrics, 0f, message) { dispatch(PocketPassEvent.DismissWidgetMessage) }
            }
        }
        if (state.widgetDesigns.isEmpty()) {
            PhoneSubpageRow(metrics, order = order++) { WidgetEmptyPanel(metrics, 0f) }
        }
        state.widgetDesigns.forEach { design ->
            PhoneSubpageRow(metrics, order = order++) {
                WidgetDesignRow(
                    metrics = metrics,
                    y = 0f,
                    design = design,
                    snapshot = snapshot,
                    ownAvatar = state.profile?.avatar,
                    assigning = assigning,
                ) {
                    if (assigning) {
                        dispatch(PocketPassEvent.AssignWidgetDesign(design.id))
                    } else {
                        dispatch(PocketPassEvent.OpenWidgetEditor(design.id))
                    }
                }
            }
        }
        PhoneSubpageRow(metrics, order = order, height = WIDGET_BUTTON_HEIGHT) {
            WidgetActionButton(
                metrics = metrics,
                y = 0f,
                label = "NEW WIDGET",
                fill = greenButtonBrush(),
                borderColor = PhoneWidgetGreenBorder,
                tag = "widget_new",
            ) { dispatch(PocketPassEvent.CreateWidgetDesign) }
        }
    }
}

@Composable
internal fun PhoneWidgetEditorPage(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    designId: String,
) {
    val design = state.widgetDesigns.firstOrNull { it.id == designId }
    val snapshot = remember(state) { state.widgetPreviewSnapshot(Clock.System.now().toEpochMilliseconds()) }
    PhoneSubpage(
        metrics = metrics,
        title = design?.name ?: "Widget",
        subtitle = design?.let { "${it.size.label} · ${it.size.cellsLabel} cells" } ?: "This widget was deleted.",
        backTag = "widget_editor_back",
        onBack = { dispatch(PocketPassEvent.Back) },
    ) {
        if (design == null) return@PhoneSubpage
        var order = 0
        PhoneSubpageRow(metrics, order = order++, height = widgetPreviewPanelHeight(design.size)) {
            WidgetPreviewPanel(metrics, 0f, design, snapshot, state.profile?.avatar)
        }
        PhoneSubpageRow(metrics, order = order++) {
            WidgetRenamePanel(metrics, 0f, design.name) { dispatch(PocketPassEvent.OpenWidgetRename) }
        }
        PhoneSubpageRow(metrics, order = order++, height = com.pocketpass.app.ui.screens.THEME_PANEL_HEIGHT) {
            WidgetSizePanel(metrics, 0f, design.size) { size ->
                dispatch(PocketPassEvent.UpdateWidgetDesign(design.withSize(size, design.updatedAtEpochMillis)))
            }
        }
        val slots = listOf(WidgetSlot.Hero) + List(design.size.tileCount) { WidgetSlot.Tile(it) }
        slots.forEach { slot ->
            PhoneSubpageRow(metrics, order = order++) {
                WidgetSlotPanel(metrics, 0f, slot, design.blockAt(slot), snapshot) {
                    dispatch(PocketPassEvent.OpenWidgetBlockPicker(slot))
                }
            }
        }
        state.widgetMaker.message?.let { message ->
            PhoneSubpageRow(metrics, order = order++, height = WIDGET_MESSAGE_HEIGHT) {
                WidgetMessagePanel(metrics, 0f, message) { dispatch(PocketPassEvent.DismissWidgetMessage) }
            }
        }
        PhoneSubpageRow(metrics, order = order++, height = WIDGET_BUTTON_HEIGHT) {
            WidgetActionButton(
                metrics = metrics,
                y = 0f,
                label = "ADD TO HOME SCREEN",
                fill = greenButtonBrush(),
                borderColor = PhoneWidgetGreenBorder,
                tag = "widget_pin",
            ) { dispatch(PocketPassEvent.PinWidgetDesign(design.id)) }
        }
        PhoneSubpageRow(metrics, order = order, height = WIDGET_BUTTON_HEIGHT) {
            WidgetActionButton(
                metrics = metrics,
                y = 0f,
                label = "DELETE WIDGET",
                fill = redButtonBrush(),
                borderColor = PhoneWidgetRedBorder,
                tag = "widget_delete",
            ) { dispatch(PocketPassEvent.OpenWidgetDeletePrompt) }
        }
    }
}

@Composable
internal fun PhoneWidgetMakerDialogs(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val onSettings = state.rootDestination == PocketPassDestination.Settings
    val design = state.editingWidgetDesign
    PhoneWidgetBlockPickerDialog(
        metrics = metrics,
        visible = onSettings && state.widgetMaker.blockPicker != null && design != null,
        state = state,
        dispatch = dispatch,
    )
    PhoneWidgetRenameDialog(
        metrics = metrics,
        visible = onSettings && state.widgetMaker.renameDraft != null,
        state = state,
        dispatch = dispatch,
    )
    val retainedDesign = remember { androidx.compose.runtime.mutableStateOf(design) }
    if (design != null) retainedDesign.value = design
    PhoneConfirmDialog(
        metrics = metrics,
        visible = onSettings && state.widgetMaker.deletePromptVisible && design != null,
        tag = "widget_delete",
        title = "Delete ${retainedDesign.value?.name ?: "widget"}?",
        body = "Copies on your home screen will ask for a new design.",
        confirmLabel = "Delete",
        onCancel = { dispatch(PocketPassEvent.CloseWidgetDeletePrompt) },
        onConfirm = { retainedDesign.value?.let { dispatch(PocketPassEvent.DeleteWidgetDesign(it.id)) } },
    )
}

@Composable
private fun PhoneWidgetBlockPickerDialog(
    metrics: DesignMetrics,
    visible: Boolean,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    val slot = remember { androidx.compose.runtime.mutableStateOf<WidgetSlot>(WidgetSlot.Hero) }
    state.widgetMaker.blockPicker?.let { slot.value = it }
    val design: WidgetDesign? = state.editingWidgetDesign
    val current = design?.blockAt(slot.value)
    val short = phoneShortViewport(metrics)
    PhoneDialog(
        metrics = metrics,
        visible = visible,
        tag = "widget_picker",
        onDismiss = { dispatch(PocketPassEvent.CloseWidgetBlockPicker) },
        borderColor = palette.tealBorder,
    ) {
        DialogTitleRow(metrics, "Choose a block", palette.teal, "close_widget_picker") {
            dispatch(PocketPassEvent.CloseWidgetBlockPicker)
        }
        Spacer(Modifier.height(metrics.dp(if (short) 8f else 14f)))
        Text(
            text = "for the ${slot.value.label().lowercase()}",
            modifier = Modifier.fillMaxWidth(),
            color = palette.tealSoft,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(metrics.dp(16f)))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = metrics.dp(if (short) 760f else 1100f))
                .verticalScroll(rememberScrollState()),
        ) {
            WidgetBlockOption(metrics, null, current == null, focusLayer = 0) {
                dispatch(PocketPassEvent.PickWidgetBlock(null))
            }
            widgetBlockChoices(slot.value).forEach { (group, blocks) ->
                Text(
                    text = group.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = metrics.dp(30f),
                            top = metrics.dp(24f),
                            bottom = metrics.dp(8f),
                        ),
                    color = palette.textMuted,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = metrics.sp(34f),
                    maxLines = 1,
                )
                blocks.forEach { block ->
                    WidgetBlockOption(metrics, block, current == block, focusLayer = 0) {
                        dispatch(PocketPassEvent.PickWidgetBlock(block))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneWidgetRenameDialog(
    metrics: DesignMetrics,
    visible: Boolean,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    val draft = state.widgetMaker.renameDraft.orEmpty()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(visible) { if (visible) runCatching { focusRequester.requestFocus() } }
    val canSave = draft.isNotBlank()
    val short = phoneShortViewport(metrics)
    PhoneDialog(
        metrics = metrics,
        visible = visible,
        tag = "widget_rename",
        onDismiss = { dispatch(PocketPassEvent.CloseWidgetRename) },
        borderColor = palette.tealBorder,
    ) {
        DialogTitleRow(metrics, "Rename Widget", palette.teal, "close_widget_rename") {
            dispatch(PocketPassEvent.CloseWidgetRename)
        }
        Spacer(Modifier.height(metrics.dp(if (short) 12f else 20f)))
        Text(
            text = "How this design is listed in Settings",
            modifier = Modifier.fillMaxWidth(),
            color = palette.tealSoft,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(metrics.dp(if (short) 16f else 24f)))
        PhoneTextField(
            metrics = metrics,
            value = draft,
            onValueChange = { dispatch(PocketPassEvent.UpdateWidgetNameDraft(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Widget name",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (canSave) dispatch(PocketPassEvent.SaveWidgetName) }),
            textAlign = TextAlign.Center,
            tag = "widget_rename_field",
            focusRequester = focusRequester,
        )
        Spacer(Modifier.height(metrics.dp(16f)))
        Text(
            text = "${draft.length}/${WidgetDesign.MAX_NAME_LENGTH}",
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
            label = "Save",
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave,
            height = 130f,
            tag = "widget_rename_save",
        ) { dispatch(PocketPassEvent.SaveWidgetName) }
    }
}
