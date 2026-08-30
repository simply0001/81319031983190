package com.pocketpass.app.mii

import java.io.File
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EDITOR_PRESENT_TIMEOUT_MS = 4_000L

class MiiEditorStateHolder(
    private val persistence: MiiEditorPersistence,
    private val scope: CoroutineScope,
    private val saveCallback: MiiEditorSaveCallback = LocalOnlyMiiEditorSaveCallback,
    private val activeSlotCallback: MiiActiveSlotCallback = LocalOnlyMiiActiveSlotCallback,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val remoteRestore: (suspend (String) -> MiiPersistedEditorSession?)? = null,
    private val ownedHatTypes: Flow<Set<Int>> = flowOf(emptySet()),
    private val pretendoMiiSource: PretendoMiiSource? = null,
) : MiiEditorController {
    private val mutableState = MutableStateFlow(MiiEditorUiState())
    override val state: StateFlow<MiiEditorUiState> = mutableState.asStateFlow()

    private val mutableRendererCommands = MutableSharedFlow<MiiRendererCommand>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val rendererCommands: SharedFlow<MiiRendererCommand> =
        mutableRendererCommands.asSharedFlow()

    private var accountKey: String? = null
    private var accountGeneration: Long = 0L
    private var activationJob: Job? = null
    private var draftPersistenceJob: Job? = null
    private var saveJob: Job? = null
    private var presentJob: Job? = null
    private var pretendoLookupJob: Job? = null
    private var pretendoLookupGeneration: Long = 0L
    private val savedProfiles = mutableMapOf<Int, MiiStoredProfile>()
    private var activeSlot: Int = MII_FIRST_SLOT
    private var editingSlot: Int = MII_FIRST_SLOT
    private var pendingSave: PendingSave? = null
    private var nextSaveRequestId: Long = 1L

    init {
        scope.launch {
            ownedHatTypes.collect { owned ->
                mutableState.update { it.copy(ownedHatTypes = owned) }
                stripLockedHat()
            }
        }
    }

    private fun stripLockedHat(
        allowedHatTypes: Set<Int> = mutableState.value.ownedHatTypes,
    ) {
        var updated: MiiEditorUiState? = null
        mutableState.update { current ->
            val stripped = current.draft.withoutLockedHat(allowedHatTypes)
            if (!current.isEditorVisible || stripped == current.draft) {
                return@update current
            }
            current.copy(
                draft = stripped,
                renderRevision = current.renderRevision + 1L,
            ).also { updated = it }
        }
        updated?.let {
            if (it.rendererStatus is MiiRendererStatus.Ready) {
                mutableRendererCommands.tryEmit(
                    MiiRendererCommand.ApplyAppearance(it.draft, it.renderRevision),
                )
            }
            persistDraftSoon()
        }
    }

    private var savedProfile: MiiStoredProfile?
        get() = savedProfiles[editingSlot]
        set(value) {
            if (value == null) {
                savedProfiles.remove(editingSlot)
            } else {
                savedProfiles[editingSlot] = value
            }
        }

    private val activeProfile: MiiStoredProfile?
        get() = savedProfiles[activeSlot]

    override fun activateAccount(accountKey: String?) {
        if (this.accountKey == accountKey && mutableState.value.isInitialized) return

        activationJob?.cancel()
        draftPersistenceJob?.cancel()
        saveJob?.cancel()
        pretendoLookupJob?.cancel()
        pretendoLookupJob = null
        pendingSave = null
        savedProfiles.clear()
        activeSlot = MII_FIRST_SLOT
        editingSlot = MII_FIRST_SLOT
        this.accountKey = accountKey
        val generation = ++accountGeneration

        if (accountKey == null) {
            mutableState.value = MiiEditorUiState(isInitialized = true)
            return
        }

        mutableState.value = MiiEditorUiState(
            activeAccountKey = accountKey,
            mode = MiiEditorMode.Loading,
            rendererStatus = MiiRendererStatus.Detached,
            ownedHatTypes = mutableState.value.ownedHatTypes,
        )
        activationJob = scope.launch {
            val restored = runCatching { persistence.load(accountKey) }
            if (generation != accountGeneration || this@MiiEditorStateHolder.accountKey != accountKey) {
                return@launch
            }

            restored.fold(
                onSuccess = { session ->
                    if (session?.savedProfiles.isNullOrEmpty() && remoteRestore != null) {
                        val remote = runCatching { remoteRestore.invoke(accountKey) }
                            .getOrNull()
                        if (
                            generation != accountGeneration ||
                            this@MiiEditorStateHolder.accountKey != accountKey
                        ) {
                            return@launch
                        }
                        if (remote != null && remote.savedProfiles.isNotEmpty()) {
                            runCatching { persistence.save(accountKey, remote) }
                            restoreSession(remote)
                            return@launch
                        }
                    }
                    restoreSession(session)
                },
                onFailure = {
                    mutableState.value = MiiEditorUiState(
                        activeAccountKey = accountKey,
                        mode = MiiEditorMode.RequiredSetup,
                        isInitialized = true,
                        ownedHatTypes = mutableState.value.ownedHatTypes,
                        rendererStatus = MiiRendererStatus.Loading,
                        saveState = MiiSaveState.Error(
                            message = "Your saved Mii could not be opened. You can create it again.",
                            locallyPersisted = false,
                        ),
                    )
                },
            )
        }
    }

    override fun beginEdit(slot: Int, wearHat: Int?) {
        val current = mutableState.value
        if (!current.isInitialized || savedProfiles.isEmpty()) return
        editingSlot = slot.coerceToMiiSlot()
        val target = savedProfiles[editingSlot]
        val preset = wearHat?.takeIf { it in 0..9 && it in current.ownedHatTypes }
        val category = if (preset != null) MiiCategory.Hair else MiiCategory.Face
        val descriptor = MiiEditorCatalog.descriptor(category)
        val base = (target?.appearance ?: MiiAppearance())
            .normalized()
            .withoutLockedHat(current.ownedHatTypes)
        mutableState.update {
            it.copy(
                mode = MiiEditorMode.EditExisting,
                editingSlot = editingSlot,
                saved = target?.appearance?.normalized(),
                savedPortraitFilePath = target?.portraitFilePath,
                savedCanonicalBase64 = target?.encodedMiiBase64,
                draft = if (preset != null) base.withTrait(MiiTraitField.HatType, preset) else base,
                renderRevision = it.renderRevision + 1L,
                selectedCategory = category,
                activeTraitField = if (preset != null) MiiTraitField.HatType else descriptor.primaryTrait(),
                activeColorField = if (preset != null) MiiColorField.Hat else descriptor.primaryColor(),
                traitPageByCategory = if (preset != null) {
                    it.traitPageByCategory + (category to 0)
                } else {
                    it.traitPageByCategory
                },
                rendererStatus = MiiRendererStatus.Loading,
                saveState = MiiSaveState.Idle,
                activeAdjustment = null,
                discardPromptVisible = false,
                presented = false,
            )
        }
        presentJob?.cancel()
        presentJob = scope.launch {
            delay(EDITOR_PRESENT_TIMEOUT_MS)
            presentEditor()
        }
    }

    private fun presentEditor() {
        presentJob?.cancel()
        presentJob = null
        if (!mutableState.value.presented) {
            mutableState.update { it.copy(presented = true) }
        }
    }

    override fun setActiveSlot(slot: Int) {
        val target = slot.coerceToMiiSlot()
        if (activeSlot == target || !savedProfiles.containsKey(target)) return
        activeSlot = target
        mutableState.update {
            it.copy(activeSlot = target, activePortraitFilePath = activeProfile?.portraitFilePath)
        }
        persistNow(draft = mutableState.value.draft)
        val key = accountKey ?: return
        scope.launch {
            runCatching { activeSlotCallback.onActiveSlotChanged(key, target) }
        }
    }

    override fun deleteSlot(slot: Int) {
        val target = slot.coerceToMiiSlot()
        val current = mutableState.value
        if (!current.isInitialized || current.isEditorVisible) return
        if (target == activeSlot) return
        val removed = savedProfiles.remove(target) ?: return
        if (editingSlot == target) editingSlot = activeSlot
        mutableState.update {
            it.copy(
                slots = slotSummaries(),
                editingSlot = editingSlot,
            )
        }
        persistNow(draft = mutableState.value.draft)
        val portraitPath = removed.portraitFilePath ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { File(portraitPath).delete() }
            }
        }
    }

    private fun slotSummaries(): Map<Int, MiiSlotSummary> =
        (MII_FIRST_SLOT..MII_SLOT_COUNT).associateWith { slot ->
            val profile = savedProfiles[slot]
            MiiSlotSummary(
                slot = slot,
                portraitFilePath = profile?.portraitFilePath,
                revision = profile?.revision ?: 0L,
            )
        }

    override fun dispatch(event: MiiEditorEvent) {
        when (event) {
            is MiiEditorEvent.SelectCategory -> selectCategory(event.category)
            is MiiEditorEvent.SelectTraitField -> selectTraitField(event.field)
            is MiiEditorEvent.SelectTrait -> selectTrait(event.field, event.index)
            is MiiEditorEvent.SelectColorField -> selectColorField(event.field)
            is MiiEditorEvent.SelectColor -> selectColor(event.field, event.index)
            is MiiEditorEvent.SetToggle -> setToggle(event.field, event.enabled)
            is MiiEditorEvent.SetTraitPage -> setTraitPage(event.page)
            is MiiEditorEvent.OpenAdjustment -> openAdjustment(event.field)
            is MiiEditorEvent.SetAdjustment -> setAdjustment(event.field, event.value)
            is MiiEditorEvent.OpenColorPalette -> openColorPalette(event.field)
            MiiEditorEvent.CloseColorPalette -> mutableState.update {
                it.copy(colorPaletteField = null)
            }

            MiiEditorEvent.CloseAdjustment -> mutableState.update {
                it.copy(activeAdjustment = null)
            }

            MiiEditorEvent.ResetDraft -> resetDraft()
            MiiEditorEvent.Continue -> continueEditor()
            MiiEditorEvent.Save -> save()
            MiiEditorEvent.RequestCancel -> requestCancel()
            MiiEditorEvent.DismissDiscardPrompt -> mutableState.update {
                it.copy(discardPromptVisible = false)
            }
            MiiEditorEvent.Cancel -> cancel()
            MiiEditorEvent.OpenPretendoImport -> openPretendoImport()
            MiiEditorEvent.ClosePretendoImport -> closePretendoImport()
            is MiiEditorEvent.SetPretendoId -> setPretendoId(event.value)
            MiiEditorEvent.LookupPretendoMii -> lookupPretendoMii()
            is MiiEditorEvent.SelectPretendoImportSlot -> selectPretendoImportSlot(event.slot)
            MiiEditorEvent.ConfirmPretendoImport -> confirmPretendoImport()
            is MiiEditorEvent.RendererReady -> rendererReady(event.rendererVersion)
            MiiEditorEvent.RendererAppearanceLoaded -> presentEditor()
            is MiiEditorEvent.RendererError -> rendererError(event.message)
            is MiiEditorEvent.RendererSaveReady -> rendererSaveReady(
                event.requestId,
                event.artifact,
            )

            is MiiEditorEvent.RendererSaveFailed -> rendererSaveFailed(
                event.requestId,
                event.message,
            )
        }
    }

    private fun restoreSession(session: MiiPersistedEditorSession?) {
        savedProfiles.clear()
        session?.savedProfiles?.let(savedProfiles::putAll)
        activeSlot = session?.activeSlot?.takeIf(savedProfiles::containsKey)
            ?: savedProfiles.keys.minOrNull()
            ?: MII_FIRST_SLOT
        editingSlot = activeSlot
        val savedAppearance = savedProfile?.appearance?.normalized()
        val draft = (session?.draft ?: savedAppearance ?: MiiAppearance()).normalized()
        val category = MiiCategory.Face
        val descriptor = MiiEditorCatalog.descriptor(category)
        val activeTrait = session?.activeTraitField
            ?.takeIf { field -> descriptor.traits.any { it.field == field } }
            ?: descriptor.primaryTrait()
        val activeColor = session?.activeColorField
            ?.takeIf { field -> descriptor.colors.any { it.field == field } }
            ?: descriptor.primaryColor()

        mutableState.value = MiiEditorUiState(
            activeAccountKey = accountKey,
            mode = if (savedProfiles.isEmpty()) {
                MiiEditorMode.RequiredSetup
            } else {
                MiiEditorMode.Inactive
            },
            isInitialized = true,
            slots = slotSummaries(),
            activeSlot = activeSlot,
            editingSlot = editingSlot,
            draft = draft,
            saved = savedAppearance,
            savedPortraitFilePath = savedProfile?.portraitFilePath,
            activePortraitFilePath = activeProfile?.portraitFilePath,
            savedCanonicalBase64 = savedProfile?.encodedMiiBase64,
            selectedCategory = category,
            activeTraitField = activeTrait,
            activeColorField = activeColor,
            traitPageByCategory = session?.traitPageByCategory.orEmpty(),
            rendererStatus = if (savedAppearance == null) {
                MiiRendererStatus.Loading
            } else {
                MiiRendererStatus.Detached
            },
            renderRevision = savedProfile?.revision ?: 0L,
            ownedHatTypes = mutableState.value.ownedHatTypes,
        )
    }

    private fun selectCategory(category: MiiCategory) {
        if (!mutableState.value.acceptsEditorInput) return
        val descriptor = MiiEditorCatalog.descriptor(category)
        mutableState.update {
            it.copy(
                selectedCategory = category,
                activeTraitField = descriptor.primaryTrait(),
                activeColorField = descriptor.primaryColor(),
                activeAdjustment = null,
                colorPaletteField = null,
            )
        }
        emitCameraCommand(category)
        persistDraftSoon()
    }

    private fun continueEditor() {
        val current = mutableState.value
        if (!current.canContinue) return
        val categories = MiiCategory.entries
        val index = categories.indexOf(current.selectedCategory)
        if (index >= categories.lastIndex) {
            save()
        } else {
            selectCategory(categories[index + 1])
        }
    }

    private fun selectTraitField(field: MiiTraitField) {
        val current = mutableState.value
        if (!current.acceptsEditorInput) return
        val descriptor = MiiEditorCatalog.trait(current.selectedCategory, field) ?: return
        val page = current.draft.traitValue(field) / TRAITS_PER_PAGE
        mutableState.update {
            it.copy(
                activeTraitField = descriptor.field,
                activeColorField = descriptor.colorField ?: it.activeColorField,
                traitPageByCategory = it.traitPageByCategory +
                    (it.selectedCategory to page),
                colorPaletteField = null,
            )
        }
        persistDraftSoon()
    }

    private fun selectTrait(field: MiiTraitField, index: Int) {
        val current = mutableState.value
        if (!current.acceptsEditorInput) return
        val descriptor = MiiEditorCatalog.trait(current.selectedCategory, field) ?: return
        val first = if (descriptor.optional) -1 else 0
        if (index !in first until descriptor.optionCount || index in descriptor.hiddenOptions) return
        if (field == MiiTraitField.HatType && index >= 0 && index !in current.ownedHatTypes) return
        updateDraft(
            current.draft.withTrait(field, index),
            activeTraitField = field,
            traitPage = index.coerceAtLeast(0) / TRAITS_PER_PAGE,
        )
    }

    private fun selectColorField(field: MiiColorField) {
        val current = mutableState.value
        if (!current.acceptsEditorInput) return
        if (MiiEditorCatalog.color(current.selectedCategory, field) == null) return
        mutableState.update { it.copy(activeColorField = field, colorPaletteField = null) }
        persistDraftSoon()
    }

    private fun selectColor(field: MiiColorField, index: Int) {
        val current = mutableState.value
        if (!current.acceptsEditorInput) return
        val descriptor = MiiEditorCatalog.color(current.selectedCategory, field) ?: return
        if (index !in 0 until descriptor.optionCount) return
        updateDraft(
            appearance = current.draft.withColor(field, index),
            activeColorField = field,
        )
    }

    private fun openColorPalette(field: MiiColorField) {
        val current = mutableState.value
        if (!current.acceptsEditorInput) return
        if (!field.isPalette || current.descriptor.colors.none { it.field == field }) return
        mutableState.update { it.copy(colorPaletteField = field, activeAdjustment = null) }
    }

    private fun setToggle(field: MiiToggleField, enabled: Boolean) {
        val current = mutableState.value
        if (!current.acceptsEditorInput) return
        if (field !in current.descriptor.toggles) return
        val gatedOff = current.descriptor.adjustments
            .filter { !enabled && it.gate == field }
            .map { it.field }
        updateDraft(
            current.draft.withToggle(field, enabled),
            activeAdjustment = current.activeAdjustment?.takeUnless { it in gatedOff },
        )
    }

    private fun setTraitPage(page: Int) {
        val current = mutableState.value
        if (!current.acceptsEditorInput) return
        val descriptor = MiiEditorCatalog.trait(
            current.selectedCategory,
            current.activeTraitField,
        ) ?: return
        val lastPage = (descriptor.optionCount - 1) / TRAITS_PER_PAGE
        mutableState.update {
            it.copy(
                traitPageByCategory = it.traitPageByCategory +
                    (it.selectedCategory to page.coerceIn(0, lastPage)),
            )
        }
        persistDraftSoon()
    }

    private fun openAdjustment(field: MiiAdjustmentField) {
        val current = mutableState.value
        if (!current.acceptsEditorInput) return
        if (MiiEditorCatalog.adjustment(current.selectedCategory, field) == null) return
        mutableState.update { it.copy(activeAdjustment = field) }
    }

    private fun setAdjustment(field: MiiAdjustmentField, value: Int) {
        val current = mutableState.value
        if (!current.acceptsEditorInput) return
        val descriptor = MiiEditorCatalog.adjustment(
            current.selectedCategory,
            field,
        ) ?: return
        updateDraft(
            appearance = current.draft.withAdjustment(
                field,
                value.coerceIn(descriptor.minimum, descriptor.maximum),
            ),
            activeAdjustment = field,
        )
    }

    private fun resetDraft() {
        val current = mutableState.value
        if (!current.acceptsEditorInput) return
        updateDraft(
            appearance = current.saved ?: MiiAppearance(),
            activeAdjustment = null,
        )
    }

    private fun openPretendoImport() {
        val current = mutableState.value
        if (
            pretendoMiiSource == null ||
            !current.isInitialized ||
            current.isEditorVisible ||
            current.pretendoImport != null ||
            savedProfiles.isEmpty()
        ) {
            return
        }
        val slot = slotSummaries().values.firstOrNull { it.isEmpty }?.slot ?: activeSlot
        mutableState.update { it.copy(pretendoImport = PretendoImportState(slot = slot)) }
    }

    private fun closePretendoImport() {
        cancelPretendoLookup()
        mutableState.update {
            if (it.pretendoImport == null) it else it.copy(pretendoImport = null)
        }
    }

    private fun cancelPretendoLookup() {
        pretendoLookupJob?.cancel()
        pretendoLookupJob = null
        pretendoLookupGeneration++
    }

    private fun setPretendoId(value: String) {
        val import = mutableState.value.pretendoImport ?: return
        val trimmed = value.filter(PretendoId::isAllowedCharacter).take(PretendoId.MAX_LENGTH)
        if (
            trimmed == import.pnid &&
            !import.lookingUp &&
            import.found == null &&
            import.error == null
        ) {
            return
        }
        cancelPretendoLookup()
        mutableState.update { current ->
            val pending = current.pretendoImport ?: return@update current
            current.copy(
                pretendoImport = pending.copy(
                    pnid = trimmed,
                    lookingUp = false,
                    found = null,
                    error = null,
                ),
            )
        }
    }

    private fun lookupPretendoMii() {
        val source = pretendoMiiSource ?: return
        val import = mutableState.value.pretendoImport ?: return
        val id = import.normalizedId ?: return
        if (import.lookingUp) return
        cancelPretendoLookup()
        val generation = pretendoLookupGeneration
        val account = accountGeneration
        mutableState.update { current ->
            val pending = current.pretendoImport ?: return@update current
            current.copy(pretendoImport = pending.copy(lookingUp = true, found = null, error = null))
        }
        pretendoLookupJob = scope.launch {
            val result = runCatching { source.lookup(id) }.getOrElse { error ->
                if (error is CancellationException) throw error
                PretendoLookupResult.Unavailable("Pretendo Network isn't reachable right now")
            }
            if (generation != pretendoLookupGeneration || account != accountGeneration) return@launch
            mutableState.update { current ->
                val pending = current.pretendoImport ?: return@update current
                current.copy(
                    pretendoImport = when (result) {
                        is PretendoLookupResult.Found -> pending.copy(
                            pnid = result.pnid,
                            lookingUp = false,
                            found = result,
                            error = null,
                        )

                        PretendoLookupResult.NotFound -> pending.copy(
                            lookingUp = false,
                            error = "No Pretendo Network ID called \"$id\"",
                        )

                        is PretendoLookupResult.Unavailable -> pending.copy(
                            lookingUp = false,
                            error = result.message,
                        )
                    },
                )
            }
        }
    }

    private fun selectPretendoImportSlot(slot: Int) {
        mutableState.update { current ->
            val pending = current.pretendoImport ?: return@update current
            current.copy(pretendoImport = pending.copy(slot = slot.coerceToMiiSlot()))
        }
    }

    private fun confirmPretendoImport() {
        val import = mutableState.value.pretendoImport ?: return
        val found = import.found ?: return
        if (import.lookingUp || savedProfiles.isEmpty()) return
        closePretendoImport()
        beginEdit(import.slot)
        updateDraft(appearance = found.appearance, activeAdjustment = null)
    }

    private fun updateDraft(
        appearance: MiiAppearance,
        activeTraitField: MiiTraitField? = null,
        activeColorField: MiiColorField? = null,
        activeAdjustment: MiiAdjustmentField? = mutableState.value.activeAdjustment,
        traitPage: Int? = null,
    ) {
        val normalized = appearance.normalized()
        var updated: MiiEditorUiState? = null
        mutableState.update { current ->
            if (!current.acceptsEditorInput || normalized == current.draft) {
                return@update current
            }
            val revision = current.renderRevision + 1L
            current.copy(
                draft = normalized,
                activeTraitField = activeTraitField ?: current.activeTraitField,
                activeColorField = activeColorField ?: current.activeColorField,
                activeAdjustment = activeAdjustment,
                traitPageByCategory = if (traitPage == null) {
                    current.traitPageByCategory
                } else {
                    current.traitPageByCategory +
                        (current.selectedCategory to traitPage)
                },
                saveState = MiiSaveState.Idle,
                renderRevision = revision,
            ).also { updated = it }
        }
        updated?.let {
            if (it.rendererStatus is MiiRendererStatus.Ready) {
                mutableRendererCommands.tryEmit(
                    MiiRendererCommand.ApplyAppearance(it.draft, it.renderRevision),
                )
            }
            persistDraftSoon()
        }
    }

    private fun save() {
        val current = mutableState.value
        if (!current.canContinue || pendingSave != null) return
        val activeRendererVersion =
            (current.rendererStatus as? MiiRendererStatus.Ready)?.rendererVersion
        val portraitIsCurrent =
            savedProfile?.rendererVersion != null &&
                savedProfile?.rendererVersion == activeRendererVersion
        if (
            current.mode == MiiEditorMode.EditExisting &&
            !current.isDirty &&
            portraitIsCurrent
        ) {
            mutableState.update {
                it.copy(
                    mode = MiiEditorMode.Inactive,
                    activeAdjustment = null,
                    rendererStatus = MiiRendererStatus.Detached,
                    saveState = MiiSaveState.Idle,
                )
            }
            return
        }
        val requestId = nextSaveRequestId++
        val revision = maxOf(
            nowEpochMillis(),
            (savedProfile?.revision ?: 0L) + 1L,
        )
        pendingSave = PendingSave(
            requestId = requestId,
            accountKey = accountKey ?: return,
            appearance = current.draft,
            revision = revision,
            slot = editingSlot,
        )
        mutableState.update {
            it.copy(saveState = MiiSaveState.Rendering(requestId))
        }
        mutableRendererCommands.tryEmit(
            MiiRendererCommand.CaptureForSave(
                requestId = requestId,
                appearance = current.draft,
                revision = revision,
            ),
        )
    }

    private fun requestCancel() {
        val current = mutableState.value
        if (current.mode != MiiEditorMode.EditExisting || pendingSave != null) return
        if (!current.isDirty) {
            cancel()
            return
        }
        mutableState.update { it.copy(discardPromptVisible = true, activeAdjustment = null) }
    }

    private fun cancel() {
        val current = mutableState.value
        if (current.mode != MiiEditorMode.EditExisting || pendingSave != null) return
        val saved = current.saved ?: MiiAppearance()
        pendingSave = null
        presentJob?.cancel()
        presentJob = null
        mutableState.update {
            it.copy(
                mode = MiiEditorMode.Inactive,
                draft = saved,
                activeAdjustment = null,
                discardPromptVisible = false,
                rendererStatus = MiiRendererStatus.Detached,
                saveState = MiiSaveState.Idle,
                renderRevision = it.renderRevision + 1L,
                presented = true,
            )
        }
        persistNow(draft = null)
    }

    private fun rendererReady(version: String) {
        val current = mutableState.value
        if (!current.isEditorVisible) return
        val readyState = current.copy(
            rendererStatus = MiiRendererStatus.Ready(version),
            saveState = if (current.saveState is MiiSaveState.Error) {
                MiiSaveState.Idle
            } else {
                current.saveState
            },
        )
        mutableState.value = readyState
        val bootMatchesDraft = readyState.mode == MiiEditorMode.EditExisting &&
            readyState.savedCanonicalBase64 != null &&
            readyState.draft == readyState.saved
        if (bootMatchesDraft) {
            emitCameraCommand(readyState.selectedCategory)
            presentEditor()
            return
        }
        mutableRendererCommands.tryEmit(
            MiiRendererCommand.LoadAppearance(
                appearance = readyState.draft,
                revision = readyState.renderRevision,
            ),
        )
        emitCameraCommand(readyState.selectedCategory)
    }

    private fun emitCameraCommand(category: MiiCategory) {
        if (mutableState.value.rendererStatus !is MiiRendererStatus.Ready) return
        mutableRendererCommands.tryEmit(
            MiiRendererCommand.SetCamera(
                camera = if (category == MiiCategory.Body) {
                    MiiEditorCamera.FullBody
                } else {
                    MiiEditorCamera.WholeHead
                },
                transitionMillis = CAMERA_TRANSITION_MILLIS,
            ),
        )
    }

    private fun rendererError(message: String) {
        presentEditor()
        val pending = pendingSave
        pendingSave = null
        mutableState.update {
            it.copy(
                rendererStatus = MiiRendererStatus.Error(
                    message.ifBlank { "The Mii preview could not be loaded." },
                ),
                saveState = if (pending == null) {
                    it.saveState
                } else {
                    MiiSaveState.Error(
                        message = "The Mii preview could not be saved.",
                        locallyPersisted = false,
                    )
                },
            )
        }
    }

    private fun rendererSaveReady(
        requestId: Long,
        artifact: MiiRendererSaveArtifact,
    ) {
        val pending = pendingSave?.takeIf { it.requestId == requestId } ?: return
        val generation = accountGeneration
        mutableState.update { it.copy(saveState = MiiSaveState.Persisting(requestId)) }
        val stateForPersistence = mutableState.value
        saveJob = scope.launch {
            if (!isCurrent(pending, generation)) return@launch
            val stored = MiiStoredProfile(
                appearance = pending.appearance,
                encodedMiiBase64 = artifact.encodedMii?.let {
                    Base64.getEncoder().encodeToString(it)
                },
                portraitFilePath = artifact.portraitFilePath,
                rendererVersion = artifact.rendererVersion
                    ?: (mutableState.value.rendererStatus as? MiiRendererStatus.Ready)
                        ?.rendererVersion,
                revision = pending.revision,
                savedAtEpochMillis = nowEpochMillis(),
            )
            val localResult = runCatching {
                persistence.save(
                    pending.accountKey,
                    persistedSession(
                        state = stateForPersistence,
                        profile = stored,
                        draft = null,
                    ),
                )
            }
            if (!isCurrent(pending, generation)) return@launch
            if (localResult.isFailure) {
                pendingSave = null
                mutableState.update {
                    it.copy(
                        saveState = MiiSaveState.Error(
                            message = "Your Mii could not be saved on this device.",
                            locallyPersisted = false,
                        ),
                    )
                }
                return@launch
            }

            val becameFirstMii = savedProfiles.isEmpty()
            savedProfile = stored
            if (becameFirstMii) {
                activeSlot = editingSlot
            }
            mutableState.update {
                it.copy(
                    saved = stored.appearance,
                    savedPortraitFilePath = stored.portraitFilePath,
                    savedCanonicalBase64 = stored.encodedMiiBase64,
                    renderRevision = stored.revision,
                    slots = slotSummaries(),
                    activeSlot = activeSlot,
                    activePortraitFilePath = activeProfile?.portraitFilePath,
                )
            }

            val callbackResult = runCatching {
                saveCallback.onMiiSaved(
                    MiiEditorSaveRequest(
                        accountKey = pending.accountKey,
                        appearance = pending.appearance,
                        artifact = artifact,
                        revision = pending.revision,
                        slot = pending.slot,
                    ),
                )
            }.getOrElse {
                MiiEditorSaveResult.Rejected("Your Mii was saved locally but could not be synced.")
            }
            if (!isCurrent(pending, generation)) return@launch
            pendingSave = null

            when (callbackResult) {
                MiiEditorSaveResult.Completed -> finishSuccessfulSave(queued = false)
                MiiEditorSaveResult.QueuedForSync -> finishSuccessfulSave(queued = true)
                is MiiEditorSaveResult.Rejected -> {
                    mutableState.update {
                        it.copy(
                            saveState = MiiSaveState.Error(
                                message = callbackResult.message,
                                locallyPersisted = true,
                            ),
                        )
                    }
                    if (callbackResult.message == MII_HAT_NOT_OWNED_MESSAGE) {
                        stripLockedHat(allowedHatTypes = emptySet())
                    }
                }
            }
        }
    }

    private fun rendererSaveFailed(requestId: Long, message: String) {
        pendingSave?.takeIf { it.requestId == requestId } ?: return
        pendingSave = null
        mutableState.update {
            it.copy(
                saveState = MiiSaveState.Error(
                    message = message.ifBlank { "Your Mii preview could not be saved." },
                    locallyPersisted = false,
                ),
            )
        }
    }

    private fun finishSuccessfulSave(queued: Boolean) {
        draftPersistenceJob?.cancel()
        mutableState.update {
            it.copy(
                mode = MiiEditorMode.Inactive,
                activeAdjustment = null,
                rendererStatus = MiiRendererStatus.Detached,
                saveState = MiiSaveState.Saved(queuedForSync = queued),
            )
        }
    }

    private fun persistDraftSoon() {
        val key = accountKey ?: return
        val generation = accountGeneration
        draftPersistenceJob?.cancel()
        draftPersistenceJob = scope.launch {
            delay(DRAFT_PERSISTENCE_DEBOUNCE_MILLIS)
            if (generation != accountGeneration || accountKey != key) return@launch
            runCatching {
                persistence.save(
                    key,
                    persistedSession(
                        state = mutableState.value,
                        profile = savedProfile,
                        draft = mutableState.value.draft,
                    ),
                )
            }
        }
    }

    private fun persistNow(draft: MiiAppearance?) {
        val key = accountKey ?: return
        val generation = accountGeneration
        draftPersistenceJob?.cancel()
        draftPersistenceJob = scope.launch {
            if (generation != accountGeneration || accountKey != key) return@launch
            runCatching {
                persistence.save(
                    key,
                    persistedSession(
                        state = mutableState.value,
                        profile = savedProfile,
                        draft = draft,
                    ),
                )
            }
        }
    }

    private fun persistedSession(
        state: MiiEditorUiState,
        profile: MiiStoredProfile?,
        draft: MiiAppearance?,
    ): MiiPersistedEditorSession = MiiPersistedEditorSession(
        savedProfiles = if (profile == null) {
            savedProfiles.toMap()
        } else {
            savedProfiles + (editingSlot to profile)
        },
        activeSlot = activeSlot,
        draft = draft,
        selectedCategory = state.selectedCategory,
        activeTraitField = state.activeTraitField,
        activeColorField = state.activeColorField,
        traitPageByCategory = state.traitPageByCategory,
        draftUpdatedAtEpochMillis = draft?.let { nowEpochMillis() },
    )

    private fun isCurrent(pending: PendingSave, generation: Long): Boolean =
        accountGeneration == generation &&
            accountKey == pending.accountKey &&
            pendingSave?.requestId == pending.requestId

    private data class PendingSave(
        val requestId: Long,
        val accountKey: String,
        val appearance: MiiAppearance,
        val revision: Long,
        val slot: Int,
    )

    private companion object {
        const val TRAITS_PER_PAGE = 12
        const val DRAFT_PERSISTENCE_DEBOUNCE_MILLIS = 200L
        const val CAMERA_TRANSITION_MILLIS = 500
    }
}

private fun MiiCategoryDescriptor.primaryTrait(): MiiTraitField =
    traits.firstOrNull { it.figmaPrimary }?.field
        ?: traits.first().field

private fun MiiCategoryDescriptor.primaryColor(): MiiColorField? =
    colors.firstOrNull { it.figmaPrimary }?.field
        ?: colors.firstOrNull()?.field

private val MiiEditorUiState.acceptsEditorInput: Boolean
    get() = isEditorVisible &&
        saveState !is MiiSaveState.Rendering &&
        saveState !is MiiSaveState.Persisting
