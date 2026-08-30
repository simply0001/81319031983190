package com.pocketpass.app.mii

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.Serializable

const val MII_SLOT_COUNT = 3
const val MII_FIRST_SLOT = 1

fun Int.coerceToMiiSlot(): Int = coerceIn(MII_FIRST_SLOT, MII_SLOT_COUNT)

enum class MiiEditorMode {
    Inactive,
    Loading,
    RequiredSetup,
    EditExisting,
}

data class MiiSlotSummary(
    val slot: Int,
    val portraitFilePath: String? = null,
    val revision: Long = 0L,
) {
    val isEmpty: Boolean
        get() = portraitFilePath.isNullOrBlank()
}

sealed interface MiiRendererStatus {
    data object Detached : MiiRendererStatus
    data object Loading : MiiRendererStatus
    data class Ready(val rendererVersion: String) : MiiRendererStatus
    data class Error(val message: String) : MiiRendererStatus
}

sealed interface MiiSaveState {
    data object Idle : MiiSaveState
    data class Rendering(val requestId: Long) : MiiSaveState
    data class Persisting(val requestId: Long) : MiiSaveState
    data class Saved(val queuedForSync: Boolean) : MiiSaveState
    data class Error(
        val message: String,
        val locallyPersisted: Boolean,
    ) : MiiSaveState
}

data class PretendoImportState(
    val slot: Int,
    val pnid: String = "",
    val lookingUp: Boolean = false,
    val found: PretendoLookupResult.Found? = null,
    val error: String? = null,
) {
    val normalizedId: String?
        get() = PretendoId.normalize(pnid)

    val canLookup: Boolean
        get() = normalizedId != null && !lookingUp

    val canImport: Boolean
        get() = found != null && !lookingUp
}

data class MiiEditorUiState(
    val activeAccountKey: String? = null,
    val mode: MiiEditorMode = MiiEditorMode.Inactive,
    val isInitialized: Boolean = false,
    val slots: Map<Int, MiiSlotSummary> = emptyMap(),
    val activeSlot: Int = MII_FIRST_SLOT,
    val editingSlot: Int = MII_FIRST_SLOT,
    val draft: MiiAppearance = MiiAppearance(),
    val saved: MiiAppearance? = null,
    val savedPortraitFilePath: String? = null,
    val activePortraitFilePath: String? = null,
    val savedCanonicalBase64: String? = null,
    val selectedCategory: MiiCategory = MiiCategory.Face,
    val activeTraitField: MiiTraitField = MiiTraitField.FaceType,
    val activeColorField: MiiColorField? = MiiColorField.Skin,
    val traitPageByCategory: Map<MiiCategory, Int> = emptyMap(),
    val activeAdjustment: MiiAdjustmentField? = null,
    val colorPaletteField: MiiColorField? = null,
    val discardPromptVisible: Boolean = false,
    val rendererStatus: MiiRendererStatus = MiiRendererStatus.Detached,
    val saveState: MiiSaveState = MiiSaveState.Idle,
    val renderRevision: Long = 0L,
    val presented: Boolean = true,
    val ownedHatTypes: Set<Int> = emptySet(),
    val pretendoImport: PretendoImportState? = null,
) {
    val descriptor: MiiCategoryDescriptor
        get() = MiiEditorCatalog.descriptor(selectedCategory)

    val colorPaletteOpen: Boolean
        get() = colorPaletteField != null

    val currentTraitPage: Int
        get() = traitPageByCategory[selectedCategory] ?: 0

    val selectedTraitIndex: Int
        get() = draft.traitValue(activeTraitField)

    val selectedColorIndex: Int?
        get() = activeColorField?.let(draft::colorValue)

    val activeAdjustmentValue: Int?
        get() = activeAdjustment?.let(draft::adjustmentValue)

    val isDirty: Boolean
        get() = saved == null || draft != saved

    val setupComplete: Boolean
        get() = saved != null

    val isEditorVisible: Boolean
        get() = mode == MiiEditorMode.RequiredSetup || mode == MiiEditorMode.EditExisting

    val isEditorPreparing: Boolean
        get() = isEditorVisible && !presented

    val isEditorPresented: Boolean
        get() = isEditorVisible && presented

    val canContinue: Boolean
        get() = isEditorVisible &&
            rendererStatus is MiiRendererStatus.Ready &&
            saveState !is MiiSaveState.Rendering &&
            saveState !is MiiSaveState.Persisting
}

sealed interface MiiEditorEvent {
    data class SelectCategory(val category: MiiCategory) : MiiEditorEvent
    data class SelectTraitField(val field: MiiTraitField) : MiiEditorEvent
    data class SelectTrait(
        val field: MiiTraitField,
        val index: Int,
    ) : MiiEditorEvent

    data class SelectColorField(val field: MiiColorField) : MiiEditorEvent
    data class SelectColor(
        val field: MiiColorField,
        val index: Int,
    ) : MiiEditorEvent

    data class SetToggle(
        val field: MiiToggleField,
        val enabled: Boolean,
    ) : MiiEditorEvent

    data class SetTraitPage(val page: Int) : MiiEditorEvent
    data class OpenAdjustment(val field: MiiAdjustmentField) : MiiEditorEvent
    data class SetAdjustment(
        val field: MiiAdjustmentField,
        val value: Int,
    ) : MiiEditorEvent

    data object CloseAdjustment : MiiEditorEvent
    data class OpenColorPalette(val field: MiiColorField) : MiiEditorEvent
    data object CloseColorPalette : MiiEditorEvent
    data object ResetDraft : MiiEditorEvent
    data object Continue : MiiEditorEvent
    data object Save : MiiEditorEvent
    data object RequestCancel : MiiEditorEvent
    data object DismissDiscardPrompt : MiiEditorEvent
    data object Cancel : MiiEditorEvent

    data object OpenPretendoImport : MiiEditorEvent
    data object ClosePretendoImport : MiiEditorEvent
    data class SetPretendoId(val value: String) : MiiEditorEvent
    data object LookupPretendoMii : MiiEditorEvent
    data class SelectPretendoImportSlot(val slot: Int) : MiiEditorEvent
    data object ConfirmPretendoImport : MiiEditorEvent

    data class RendererReady(val rendererVersion: String) : MiiEditorEvent
    data object RendererAppearanceLoaded : MiiEditorEvent
    data class RendererError(val message: String) : MiiEditorEvent
    data class RendererSaveReady(
        val requestId: Long,
        val artifact: MiiRendererSaveArtifact,
    ) : MiiEditorEvent

    data class RendererSaveFailed(
        val requestId: Long,
        val message: String,
    ) : MiiEditorEvent
}

enum class MiiEditorCamera {
    WholeHead,
    FullBody,
}

sealed interface MiiRendererCommand {
    data class LoadAppearance(
        val appearance: MiiAppearance,
        val revision: Long,
    ) : MiiRendererCommand

    data class ApplyAppearance(
        val appearance: MiiAppearance,
        val revision: Long,
    ) : MiiRendererCommand

    data class SetCamera(
        val camera: MiiEditorCamera,
        val transitionMillis: Int,
    ) : MiiRendererCommand

    data class CaptureForSave(
        val requestId: Long,
        val appearance: MiiAppearance,
        val revision: Long,
    ) : MiiRendererCommand
}

data class MiiRendererSaveArtifact(
    val encodedMii: ByteArray? = null,
    val portraitFilePath: String? = null,
    val rendererVersion: String? = null,
)

@Serializable
data class MiiStoredProfile(
    val appearance: MiiAppearance,
    val encodedMiiBase64: String? = null,
    val portraitFilePath: String? = null,
    val rendererVersion: String? = null,
    val revision: Long,
    val savedAtEpochMillis: Long,
)

@Serializable
data class MiiPersistedEditorSession(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val savedProfile: MiiStoredProfile? = null,
    val savedProfiles: Map<Int, MiiStoredProfile> = emptyMap(),
    val activeSlot: Int = MII_FIRST_SLOT,
    val draft: MiiAppearance? = null,
    val selectedCategory: MiiCategory = MiiCategory.Face,
    val activeTraitField: MiiTraitField = MiiTraitField.FaceType,
    val activeColorField: MiiColorField? = MiiColorField.Skin,
    val traitPageByCategory: Map<MiiCategory, Int> = emptyMap(),
    val draftUpdatedAtEpochMillis: Long? = null,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 2
    }
}

interface MiiEditorPersistence {
    suspend fun load(accountKey: String): MiiPersistedEditorSession?
    suspend fun save(accountKey: String, session: MiiPersistedEditorSession)
    suspend fun clear(accountKey: String)
}

data class MiiEditorSaveRequest(
    val accountKey: String,
    val appearance: MiiAppearance,
    val artifact: MiiRendererSaveArtifact,
    val revision: Long,
    val slot: Int = MII_FIRST_SLOT,
)

sealed interface MiiEditorSaveResult {
    data object Completed : MiiEditorSaveResult
    data object QueuedForSync : MiiEditorSaveResult
    data class Rejected(val message: String) : MiiEditorSaveResult
}

fun interface MiiEditorSaveCallback {
    suspend fun onMiiSaved(request: MiiEditorSaveRequest): MiiEditorSaveResult
}

object LocalOnlyMiiEditorSaveCallback : MiiEditorSaveCallback {
    override suspend fun onMiiSaved(
        request: MiiEditorSaveRequest,
    ): MiiEditorSaveResult = MiiEditorSaveResult.Completed
}

fun interface MiiActiveSlotCallback {
    suspend fun onActiveSlotChanged(accountKey: String, slot: Int): MiiEditorSaveResult
}

object LocalOnlyMiiActiveSlotCallback : MiiActiveSlotCallback {
    override suspend fun onActiveSlotChanged(
        accountKey: String,
        slot: Int,
    ): MiiEditorSaveResult = MiiEditorSaveResult.Completed
}

interface MiiEditorController {
    val state: kotlinx.coroutines.flow.StateFlow<MiiEditorUiState>
    val rendererCommands: SharedFlow<MiiRendererCommand>

    fun activateAccount(accountKey: String?)
    fun beginEdit(slot: Int = MII_FIRST_SLOT, wearHat: Int? = null)
    fun setActiveSlot(slot: Int)
    fun deleteSlot(slot: Int)
    fun dispatch(event: MiiEditorEvent)
}
