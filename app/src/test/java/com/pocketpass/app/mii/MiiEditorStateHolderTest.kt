package com.pocketpass.app.mii

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MiiEditorStateHolderTest {
    @Test
    fun newAccountRequiresSetupAndRestoresDraft() = runTest {
        val persistence = InMemoryMiiEditorPersistence()
        persistence.save(
            ACCOUNT,
            MiiPersistedEditorSession(
                draft = MiiAppearance(hairType = 25),
                selectedCategory = MiiCategory.Hair,
            ),
        )
        val holder = MiiEditorStateHolder(persistence, this)

        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()

        assertEquals(MiiEditorMode.RequiredSetup, holder.state.value.mode)
        assertEquals(MiiCategory.Face, holder.state.value.selectedCategory)
        assertEquals(25, holder.state.value.draft.hairType)
        assertFalse(holder.state.value.setupComplete)
    }

    @Test
    fun emptyLocalStateRestoresSavedMiiFromServer() = runTest {
        val persistence = InMemoryMiiEditorPersistence()
        val serverSession = MiiPersistedEditorSession(
            savedProfiles = mapOf(
                2 to MiiStoredProfile(
                    appearance = MiiAppearance(hairType = 12),
                    revision = 41L,
                    savedAtEpochMillis = 1_000L,
                ),
            ),
            activeSlot = 2,
        )
        var fetches = 0
        val holder = MiiEditorStateHolder(
            persistence = persistence,
            scope = this,
            remoteRestore = { fetches++; serverSession },
        )

        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()

        assertEquals(1, fetches)
        assertEquals(MiiEditorMode.Inactive, holder.state.value.mode)
        assertTrue(holder.state.value.setupComplete || !holder.state.value.isEditorVisible)
        assertEquals(2, holder.state.value.activeSlot)
        assertEquals(
            12,
            persistence.load(ACCOUNT)?.savedProfiles?.get(2)?.appearance?.hairType,
        )
    }

    @Test
    fun remoteRestoreFailureFallsBackToRequiredSetup() = runTest {
        val holder = MiiEditorStateHolder(
            persistence = InMemoryMiiEditorPersistence(),
            scope = this,
            remoteRestore = { null },
        )

        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()

        assertEquals(MiiEditorMode.RequiredSetup, holder.state.value.mode)
    }

    @Test
    fun localSavedMiiSkipsRemoteRestore() = runTest {
        val persistence = InMemoryMiiEditorPersistence()
        persistence.save(
            ACCOUNT,
            MiiPersistedEditorSession(
                savedProfiles = mapOf(
                    1 to MiiStoredProfile(
                        appearance = MiiAppearance(),
                        revision = 7L,
                        savedAtEpochMillis = 1_000L,
                    ),
                ),
            ),
        )
        var fetches = 0
        val holder = MiiEditorStateHolder(
            persistence = persistence,
            scope = this,
            remoteRestore = { fetches++; null },
        )

        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()

        assertEquals(0, fetches)
        assertEquals(MiiEditorMode.Inactive, holder.state.value.mode)
    }

    @Test
    fun categoryEventsAreValidatedAndEmitRendererAppearance() = runTest {
        val holder = MiiEditorStateHolder(
            persistence = InMemoryMiiEditorPersistence(),
            scope = this,
        )
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()

        val command = async { holder.rendererCommands.first() }
        runCurrent()
        holder.dispatch(MiiEditorEvent.RendererReady("test-renderer"))
        runCurrent()
        assertTrue(command.await() is MiiRendererCommand.LoadAppearance)

        val update = async {
            holder.rendererCommands.first {
                it is MiiRendererCommand.ApplyAppearance
            }
        }
        runCurrent()
        holder.dispatch(MiiEditorEvent.SelectCategory(MiiCategory.Eyes))
        holder.dispatch(MiiEditorEvent.SelectTrait(MiiTraitField.EyeType, 59))
        holder.dispatch(MiiEditorEvent.SelectTrait(MiiTraitField.EyeType, 60))
        runCurrent()

        assertEquals(59, holder.state.value.draft.eyeType)
        assertEquals(4, holder.state.value.currentTraitPage)
        assertEquals(59, (update.await() as MiiRendererCommand.ApplyAppearance).appearance.eyeType)
    }

    @Test
    fun moleToggleAndAdjustmentsEditTheFaceDraft() = runTest {
        val holder = MiiEditorStateHolder(
            persistence = InMemoryMiiEditorPersistence(),
            scope = this,
        )
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()
        holder.dispatch(MiiEditorEvent.RendererReady("test-renderer"))
        runCurrent()

        holder.dispatch(MiiEditorEvent.SelectCategory(MiiCategory.Face))
        holder.dispatch(MiiEditorEvent.SetToggle(MiiToggleField.Mole, true))
        holder.dispatch(MiiEditorEvent.SetAdjustment(MiiAdjustmentField.MoleScale, 7))
        holder.dispatch(MiiEditorEvent.SetAdjustment(MiiAdjustmentField.MoleXPosition, 11))
        holder.dispatch(MiiEditorEvent.SetAdjustment(MiiAdjustmentField.MoleYPosition, 25))
        runCurrent()

        val draft = holder.state.value.draft
        assertTrue(draft.moleEnabled)
        assertEquals(7, draft.moleScale)
        assertEquals(11, draft.moleXPosition)
        assertEquals(25, draft.moleYPosition)

        holder.dispatch(MiiEditorEvent.SelectCategory(MiiCategory.Hair))
        holder.dispatch(MiiEditorEvent.SetToggle(MiiToggleField.Mole, false))
        runCurrent()
        assertTrue(holder.state.value.draft.moleEnabled)
    }

    @Test
    fun glassesPalettePicksCommonColorsAndFoldsLegacyOnesBack() = runTest {
        val holder = MiiEditorStateHolder(
            persistence = InMemoryMiiEditorPersistence(),
            scope = this,
        )
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()
        holder.dispatch(MiiEditorEvent.RendererReady("test-renderer"))
        runCurrent()

        holder.dispatch(MiiEditorEvent.OpenColorPalette(MiiColorField.Glasses))
        runCurrent()
        assertFalse(holder.state.value.colorPaletteOpen)

        holder.dispatch(MiiEditorEvent.SelectCategory(MiiCategory.Glasses))
        holder.dispatch(MiiEditorEvent.OpenColorPalette(MiiColorField.Glasses))
        runCurrent()
        assertTrue(holder.state.value.colorPaletteOpen)

        holder.dispatch(MiiEditorEvent.SelectColor(MiiColorField.Glasses, 57))
        runCurrent()
        assertEquals(57, holder.state.value.draft.extGlassesColor)
        assertEquals(57, holder.state.value.draft.glassesCommonColor)
        assertEquals(MiiColorField.Glasses, holder.state.value.activeColorField)

        holder.dispatch(MiiEditorEvent.SelectColor(MiiColorField.Glasses, 15))
        runCurrent()
        assertEquals(-1, holder.state.value.draft.extGlassesColor)
        assertEquals(2, holder.state.value.draft.glassesColor)
        assertEquals(15, holder.state.value.draft.glassesCommonColor)

        holder.dispatch(MiiEditorEvent.SelectColor(MiiColorField.Glasses, 40))
        holder.dispatch(MiiEditorEvent.SelectColor(MiiColorField.Glasses, 17))
        runCurrent()
        assertEquals(-1, holder.state.value.draft.extGlassesColor)
        assertEquals(4, holder.state.value.draft.glassesColor)
        assertEquals(17, holder.state.value.draft.glassesCommonColor)

        holder.dispatch(MiiEditorEvent.CloseColorPalette)
        runCurrent()
        assertFalse(holder.state.value.colorPaletteOpen)

        holder.dispatch(MiiEditorEvent.OpenColorPalette(MiiColorField.Glasses))
        holder.dispatch(MiiEditorEvent.SelectCategory(MiiCategory.Eyes))
        runCurrent()
        assertFalse(holder.state.value.colorPaletteOpen)
    }

    @Test
    fun pretendoImportOpensOnTheFirstEmptySlot() = runTest {
        val holder = holderWithSavedPiip(PretendoMiiSource { PretendoLookupResult.NotFound })

        holder.dispatch(MiiEditorEvent.OpenPretendoImport)
        runCurrent()
        assertEquals(2, holder.state.value.pretendoImport?.slot)

        holder.dispatch(MiiEditorEvent.SelectPretendoImportSlot(3))
        assertEquals(3, holder.state.value.pretendoImport?.slot)

        holder.dispatch(MiiEditorEvent.ClosePretendoImport)
        assertNull(holder.state.value.pretendoImport)
    }

    @Test
    fun pretendoImportIsRefusedWithoutASavedPiipOrASource() = runTest {
        val unsaved = MiiEditorStateHolder(
            persistence = InMemoryMiiEditorPersistence(),
            scope = this,
            pretendoMiiSource = PretendoMiiSource { PretendoLookupResult.NotFound },
        )
        unsaved.activateAccount(ACCOUNT)
        advanceUntilIdle()
        unsaved.dispatch(MiiEditorEvent.OpenPretendoImport)
        assertNull(unsaved.state.value.pretendoImport)

        val sourceless = holderWithSavedPiip(source = null)
        sourceless.dispatch(MiiEditorEvent.OpenPretendoImport)
        assertNull(sourceless.state.value.pretendoImport)
    }

    @Test
    fun pretendoLookupNeedsAValidIdAndReportsUnknownOnes() = runTest {
        var lookups = 0
        val holder = holderWithSavedPiip(
            PretendoMiiSource {
                lookups++
                PretendoLookupResult.NotFound
            },
        )
        holder.dispatch(MiiEditorEvent.OpenPretendoImport)

        holder.dispatch(MiiEditorEvent.SetPretendoId("Jon B!"))
        assertEquals("JonB", holder.state.value.pretendoImport?.pnid)
        assertFalse(holder.state.value.pretendoImport!!.canLookup)
        holder.dispatch(MiiEditorEvent.LookupPretendoMii)
        advanceUntilIdle()
        assertEquals(0, lookups)

        holder.dispatch(MiiEditorEvent.SetPretendoId("nobody-here"))
        assertTrue(holder.state.value.pretendoImport!!.canLookup)
        holder.dispatch(MiiEditorEvent.LookupPretendoMii)
        advanceUntilIdle()
        assertEquals(1, lookups)
        assertEquals(
            "No Pretendo Network ID called \"nobody-here\"",
            holder.state.value.pretendoImport?.error,
        )
        assertFalse(holder.state.value.pretendoImport!!.lookingUp)
        assertFalse(holder.state.value.pretendoImport!!.canImport)
    }

    @Test
    fun pretendoImportOpensTheEditorWithTheImportedDraft() = runTest {
        val imported = MiiAppearance(hairType = 57, favoriteColor = 9, glassesType = 3)
        val found = PretendoLookupResult.Found(
            pnid = "jonbarrow",
            pid = 42L,
            miiName = "Jon",
            portraitUrl = "https://r2-cdn.pretendo.cc/mii/42/normal_face.png",
            appearance = imported,
        )
        val persistence = InMemoryMiiEditorPersistence()
        val holder = holderWithSavedPiip(PretendoMiiSource { found }, persistence)
        holder.dispatch(MiiEditorEvent.OpenPretendoImport)
        holder.dispatch(MiiEditorEvent.SetPretendoId("JonBarrow"))
        holder.dispatch(MiiEditorEvent.LookupPretendoMii)
        advanceUntilIdle()

        val lookedUp = holder.state.value.pretendoImport
        assertEquals(found, lookedUp?.found)
        assertEquals("jonbarrow", lookedUp?.pnid)
        assertTrue(lookedUp!!.canImport)

        holder.dispatch(MiiEditorEvent.ConfirmPretendoImport)
        advanceUntilIdle()

        val editing = holder.state.value
        assertNull(editing.pretendoImport)
        assertEquals(MiiEditorMode.EditExisting, editing.mode)
        assertEquals(2, editing.editingSlot)
        assertEquals(imported.normalized(), editing.draft)
        assertTrue(editing.isDirty)
        assertEquals(imported.normalized(), persistence.load(ACCOUNT)?.draft)
    }

    @Test
    fun discardingAnImportIntoAnEmptySlotLeavesTheEditor() = runTest {
        val found = PretendoLookupResult.Found(
            pnid = "jonbarrow",
            pid = 42L,
            miiName = "Jon",
            portraitUrl = "https://r2-cdn.pretendo.cc/mii/42/normal_face.png",
            appearance = MiiAppearance(hairType = 57),
        )
        val holder = holderWithSavedPiip(PretendoMiiSource { found })
        holder.dispatch(MiiEditorEvent.OpenPretendoImport)
        holder.dispatch(MiiEditorEvent.SetPretendoId("jonbarrow"))
        holder.dispatch(MiiEditorEvent.LookupPretendoMii)
        advanceUntilIdle()
        holder.dispatch(MiiEditorEvent.ConfirmPretendoImport)
        advanceUntilIdle()
        assertEquals(MiiEditorMode.EditExisting, holder.state.value.mode)

        holder.dispatch(MiiEditorEvent.RequestCancel)
        runCurrent()
        assertTrue(holder.state.value.discardPromptVisible)

        holder.dispatch(MiiEditorEvent.Cancel)
        advanceUntilIdle()

        val state = holder.state.value
        assertEquals(MiiEditorMode.Inactive, state.mode)
        assertFalse(state.discardPromptVisible)
        assertTrue(state.slots.getValue(2).isEmpty)
        assertEquals(1, state.activeSlot)
    }

    @Test
    fun switchingAccountsDropsALatePretendoResult() = runTest {
        val gate = CompletableDeferred<PretendoLookupResult>()
        val holder = holderWithSavedPiip(PretendoMiiSource { gate.await() })
        holder.dispatch(MiiEditorEvent.OpenPretendoImport)
        holder.dispatch(MiiEditorEvent.SetPretendoId("jonbarrow"))
        holder.dispatch(MiiEditorEvent.LookupPretendoMii)
        runCurrent()
        assertTrue(holder.state.value.pretendoImport!!.lookingUp)

        holder.activateAccount(OTHER_ACCOUNT)
        advanceUntilIdle()
        gate.complete(
            PretendoLookupResult.Found(
                pnid = "jonbarrow",
                pid = 42L,
                miiName = "Jon",
                portraitUrl = "https://r2-cdn.pretendo.cc/mii/42/normal_face.png",
                appearance = MiiAppearance(),
            ),
        )
        advanceUntilIdle()

        assertNull(holder.state.value.pretendoImport)
        assertEquals(OTHER_ACCOUNT, holder.state.value.activeAccountKey)
    }

    @Test
    fun typingWhileLookingUpCancelsTheLookup() = runTest {
        val gate = CompletableDeferred<PretendoLookupResult>()
        val holder = holderWithSavedPiip(PretendoMiiSource { gate.await() })
        holder.dispatch(MiiEditorEvent.OpenPretendoImport)
        holder.dispatch(MiiEditorEvent.SetPretendoId("jonbarrow"))
        holder.dispatch(MiiEditorEvent.LookupPretendoMii)
        runCurrent()

        holder.dispatch(MiiEditorEvent.SetPretendoId("jonbarrow2"))
        gate.complete(PretendoLookupResult.NotFound)
        advanceUntilIdle()

        val import = holder.state.value.pretendoImport
        assertEquals("jonbarrow2", import?.pnid)
        assertFalse(import!!.lookingUp)
        assertNull(import.error)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.holderWithSavedPiip(
        source: PretendoMiiSource?,
        persistence: InMemoryMiiEditorPersistence = InMemoryMiiEditorPersistence(),
    ): MiiEditorStateHolder {
        persistence.save(
            ACCOUNT,
            MiiPersistedEditorSession(
                savedProfiles = mapOf(
                    1 to MiiStoredProfile(
                        appearance = MiiAppearance(hairType = 12),
                        portraitFilePath = "slot-1-portrait.png",
                        revision = 1L,
                        savedAtEpochMillis = 1L,
                    ),
                ),
                activeSlot = 1,
            ),
        )
        val holder = MiiEditorStateHolder(
            persistence = persistence,
            scope = this,
            pretendoMiiSource = source,
        )
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()
        assertEquals(MiiEditorMode.Inactive, holder.state.value.mode)
        return holder
    }

    @Test
    fun facialHairFieldsMoveTheColourColumnWithThem() = runTest {
        val holder = MiiEditorStateHolder(
            persistence = InMemoryMiiEditorPersistence(),
            scope = this,
        )
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()
        holder.dispatch(MiiEditorEvent.RendererReady("test-renderer"))
        runCurrent()

        holder.dispatch(MiiEditorEvent.SelectCategory(MiiCategory.Mouth))
        runCurrent()
        assertEquals(MiiTraitField.MouthType, holder.state.value.activeTraitField)
        assertEquals(MiiColorField.Mouth, holder.state.value.activeColorField)

        holder.dispatch(MiiEditorEvent.SelectTraitField(MiiTraitField.MustacheType))
        runCurrent()
        assertEquals(MiiTraitField.MustacheType, holder.state.value.activeTraitField)
        assertEquals(MiiColorField.FacialHair, holder.state.value.activeColorField)

        holder.dispatch(MiiEditorEvent.SelectTrait(MiiTraitField.BeardType, 2))
        runCurrent()
        assertEquals(2, holder.state.value.draft.beardType)
        assertEquals(MiiTraitField.BeardType, holder.state.value.activeTraitField)
        assertEquals(MiiColorField.FacialHair, holder.state.value.activeColorField)

        holder.dispatch(MiiEditorEvent.SelectTraitField(MiiTraitField.MouthType))
        runCurrent()
        assertEquals(MiiTraitField.MouthType, holder.state.value.activeTraitField)
        assertEquals(MiiColorField.Mouth, holder.state.value.activeColorField)
        assertEquals(2, holder.state.value.draft.beardType)
    }

    @Test
    fun hairMouthAndFacialHairPickCommonColorsAndFoldLegacyOnesBack() = runTest {
        val holder = MiiEditorStateHolder(
            persistence = InMemoryMiiEditorPersistence(),
            scope = this,
        )
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()
        holder.dispatch(MiiEditorEvent.RendererReady("test-renderer"))
        runCurrent()

        holder.dispatch(MiiEditorEvent.SelectCategory(MiiCategory.Hair))
        holder.dispatch(MiiEditorEvent.SelectColor(MiiColorField.Hair, 57))
        runCurrent()
        assertEquals(57, holder.state.value.draft.extHairColor)
        assertEquals(57, holder.state.value.draft.hairCommonColor)

        holder.dispatch(MiiEditorEvent.SelectColor(MiiColorField.Hair, 3))
        runCurrent()
        assertEquals(-1, holder.state.value.draft.extHairColor)
        assertEquals(3, holder.state.value.draft.hairColor)
        assertEquals(3, holder.state.value.draft.hairCommonColor)

        holder.dispatch(MiiEditorEvent.SelectCategory(MiiCategory.Mouth))
        holder.dispatch(MiiEditorEvent.SelectColor(MiiColorField.Mouth, 88))
        runCurrent()
        assertEquals(88, holder.state.value.draft.mouthCommonColor)

        holder.dispatch(MiiEditorEvent.SelectColor(MiiColorField.Mouth, 21))
        runCurrent()
        assertEquals(-1, holder.state.value.draft.extMouthColor)
        assertEquals(2, holder.state.value.draft.mouthColor)

        holder.dispatch(MiiEditorEvent.SelectColor(MiiColorField.FacialHair, 72))
        runCurrent()
        assertEquals(72, holder.state.value.draft.facialHairCommonColor)
        assertEquals(21, holder.state.value.draft.mouthCommonColor)
        assertEquals(MiiColorField.FacialHair, holder.state.value.activeColorField)
    }

    @Test
    fun continueAdvancesFromFaceThroughBodyThenStartsSave() = runTest {
        val holder = MiiEditorStateHolder(
            persistence = InMemoryMiiEditorPersistence(),
            scope = this,
        )
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()
        holder.dispatch(MiiEditorEvent.RendererReady("renderer"))

        val expected = listOf(
            MiiCategory.Hair,
            MiiCategory.Eyebrows,
            MiiCategory.Eyes,
            MiiCategory.Nose,
            MiiCategory.Mouth,
            MiiCategory.Glasses,
            MiiCategory.Body,
        )
        expected.forEach { category ->
            holder.dispatch(MiiEditorEvent.Continue)
            assertEquals(category, holder.state.value.selectedCategory)
        }

        val capture = async {
            holder.rendererCommands.first {
                it is MiiRendererCommand.CaptureForSave
            }
        }
        runCurrent()
        holder.dispatch(MiiEditorEvent.Continue)
        runCurrent()

        assertTrue(capture.await() is MiiRendererCommand.CaptureForSave)
        assertTrue(holder.state.value.saveState is MiiSaveState.Rendering)
        assertEquals(MiiCategory.Body, holder.state.value.selectedCategory)
    }

    @Test
    fun cameraCommandsMapAllFeatureTabsToHeadAndBodyToFullBody() = runTest {
        val holder = MiiEditorStateHolder(
            persistence = InMemoryMiiEditorPersistence(),
            scope = this,
        )
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()

        val initialCamera = async {
            holder.rendererCommands.first {
                it is MiiRendererCommand.SetCamera
            } as MiiRendererCommand.SetCamera
        }
        runCurrent()
        holder.dispatch(MiiEditorEvent.RendererReady("renderer"))
        runCurrent()
        assertEquals(MiiEditorCamera.WholeHead, initialCamera.await().camera)

        val bodyCamera = async {
            holder.rendererCommands.first {
                it is MiiRendererCommand.SetCamera
            } as MiiRendererCommand.SetCamera
        }
        runCurrent()
        holder.dispatch(MiiEditorEvent.SelectCategory(MiiCategory.Body))
        runCurrent()
        assertEquals(MiiEditorCamera.FullBody, bodyCamera.await().camera)
    }

    @Test
    fun saveCommitsLocalProfileAndCompletesRequiredSetup() = runTest {
        val persistence = InMemoryMiiEditorPersistence()
        var callbackRequest: MiiEditorSaveRequest? = null
        val holder = MiiEditorStateHolder(
            persistence = persistence,
            scope = this,
            saveCallback = MiiEditorSaveCallback {
                callbackRequest = it
                MiiEditorSaveResult.QueuedForSync
            },
            nowEpochMillis = { 1234L },
        )
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()
        holder.dispatch(MiiEditorEvent.RendererReady("renderer-1"))
        holder.dispatch(MiiEditorEvent.SelectCategory(MiiCategory.Body))
        holder.dispatch(MiiEditorEvent.SetAdjustment(MiiAdjustmentField.Height, 100))

        val capture = async {
            holder.rendererCommands.first {
                it is MiiRendererCommand.CaptureForSave
            } as MiiRendererCommand.CaptureForSave
        }
        runCurrent()
        holder.dispatch(MiiEditorEvent.Save)
        runCurrent()
        val request = capture.await()
        holder.dispatch(
            MiiEditorEvent.RendererSaveReady(
                requestId = request.requestId,
                artifact = MiiRendererSaveArtifact(
                    encodedMii = byteArrayOf(1, 2, 3),
                    portraitFilePath = "/private/mii.webp",
                    rendererVersion = "renderer-1",
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(MiiEditorMode.Inactive, holder.state.value.mode)
        assertEquals(100, holder.state.value.saved?.height)
        assertTrue(holder.state.value.setupComplete)
        assertEquals(100, callbackRequest?.appearance?.height)
        assertEquals(
            MiiSaveState.Saved(queuedForSync = true),
            holder.state.value.saveState,
        )

        val stored = persistence.load(ACCOUNT)?.savedProfiles?.get(MII_FIRST_SLOT)
        assertEquals(100, stored?.appearance?.height)
        assertEquals("AQID", stored?.encodedMiiBase64)
        assertEquals(1234L, stored?.savedAtEpochMillis)
        assertEquals(MII_FIRST_SLOT, callbackRequest?.slot)
    }

    @Test
    fun unchangedMiiRegeneratesPortraitWhenRendererVersionChanges() = runTest {
        val persistence = InMemoryMiiEditorPersistence()
        persistence.save(
            ACCOUNT,
            MiiPersistedEditorSession(
                savedProfile = MiiStoredProfile(
                    appearance = MiiAppearance(),
                    portraitFilePath = "/private/old.png",
                    rendererVersion = "renderer-old",
                    revision = 1,
                    savedAtEpochMillis = 10,
                ),
            ),
        )
        val holder = MiiEditorStateHolder(persistence, this)
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()
        holder.beginEdit()
        holder.dispatch(MiiEditorEvent.RendererReady("renderer-new"))
        holder.dispatch(MiiEditorEvent.SelectCategory(MiiCategory.Body))

        val capture = async {
            holder.rendererCommands.first {
                it is MiiRendererCommand.CaptureForSave
            }
        }
        runCurrent()
        holder.dispatch(MiiEditorEvent.Save)
        runCurrent()

        assertTrue(capture.await() is MiiRendererCommand.CaptureForSave)
        assertTrue(holder.state.value.saveState is MiiSaveState.Rendering)
    }

    @Test
    fun editCancelRestoresCommittedAppearance() = runTest {
        val persistence = InMemoryMiiEditorPersistence()
        persistence.save(
            ACCOUNT,
            MiiPersistedEditorSession(
                savedProfile = MiiStoredProfile(
                    appearance = MiiAppearance(noseType = 4),
                    revision = 2,
                    savedAtEpochMillis = 10,
                ),
            ),
        )
        val holder = MiiEditorStateHolder(persistence, this)
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()

        assertEquals(MiiEditorMode.Inactive, holder.state.value.mode)
        holder.beginEdit()
        assertEquals(MiiCategory.Face, holder.state.value.selectedCategory)
        holder.dispatch(MiiEditorEvent.RendererReady("test"))
        holder.dispatch(MiiEditorEvent.SelectCategory(MiiCategory.Nose))
        holder.dispatch(MiiEditorEvent.SelectTrait(MiiTraitField.NoseType, 12))
        assertTrue(holder.state.value.isDirty)

        holder.dispatch(MiiEditorEvent.Cancel)
        advanceUntilIdle()

        assertEquals(MiiEditorMode.Inactive, holder.state.value.mode)
        assertEquals(4, holder.state.value.draft.noseType)
        assertFalse(holder.state.value.isDirty)
    }

    @Test
    fun switchingAccountsCancelsAnUncommittedAsynchronousSave() = runTest {
        val persistence = InMemoryMiiEditorPersistence()
        var callbackCount = 0
        val holder = MiiEditorStateHolder(
            persistence = persistence,
            scope = this,
            saveCallback = MiiEditorSaveCallback {
                callbackCount += 1
                MiiEditorSaveResult.Completed
            },
        )
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()
        holder.dispatch(MiiEditorEvent.RendererReady("renderer-test"))

        val capture = async {
            holder.rendererCommands.first {
                it is MiiRendererCommand.CaptureForSave
            } as MiiRendererCommand.CaptureForSave
        }
        runCurrent()
        holder.dispatch(MiiEditorEvent.Save)
        runCurrent()
        val request = capture.await()
        holder.dispatch(
            MiiEditorEvent.RendererSaveReady(
                requestId = request.requestId,
                artifact = MiiRendererSaveArtifact(
                    encodedMii = byteArrayOf(1, 2, 3),
                    portraitFilePath = "/private/account-a.png",
                ),
            ),
        )

        holder.activateAccount(OTHER_ACCOUNT)
        advanceUntilIdle()

        assertEquals(OTHER_ACCOUNT, holder.state.value.activeAccountKey)
        assertEquals(MiiEditorMode.RequiredSetup, holder.state.value.mode)
        assertEquals(null, persistence.load(ACCOUNT)?.savedProfile)
        assertEquals(0, callbackCount)
    }

    @Test
    fun appearanceNormalizationUsesBundledRendererCatalogLimits() {
        val normalized = MiiAppearance(
            gender = 5,
            favoriteColor = 99,
            hairType = 500,
            eyebrowYPosition = -1,
            glassesType = 20,
            height = -10,
        ).normalized()

        assertEquals(1, normalized.gender)
        assertEquals(11, normalized.favoriteColor)
        assertEquals(131, normalized.hairType)
        assertEquals(3, normalized.eyebrowYPosition)
        assertEquals(19, normalized.glassesType)
        assertEquals(0, normalized.height)
    }

    @Test
    fun hatPickerAcceptsNoHatAndOwnedHatsOnly() = runTest {
        val holder = MiiEditorStateHolder(
            persistence = InMemoryMiiEditorPersistence(),
            scope = this,
            ownedHatTypes = flowOf(setOf(2)),
        )
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()
        holder.dispatch(MiiEditorEvent.RendererReady("test-renderer"))
        runCurrent()

        holder.dispatch(MiiEditorEvent.SelectCategory(MiiCategory.Hair))
        holder.dispatch(MiiEditorEvent.SelectTraitField(MiiTraitField.HatType))
        assertEquals(MiiColorField.Hat, holder.state.value.activeColorField)

        holder.dispatch(MiiEditorEvent.SelectTrait(MiiTraitField.HatType, 2))
        assertEquals(2, holder.state.value.draft.extHatType)
        assertEquals(holder.state.value.draft.favoriteColor, holder.state.value.draft.extHatColor)

        holder.dispatch(MiiEditorEvent.SelectTrait(MiiTraitField.HatType, 3))
        assertEquals(2, holder.state.value.draft.extHatType)

        holder.dispatch(MiiEditorEvent.SelectTrait(MiiTraitField.HatType, -1))
        assertEquals(-1, holder.state.value.draft.extHatType)

        holder.dispatch(MiiEditorEvent.SelectTrait(MiiTraitField.HatType, 10))
        assertEquals(-1, holder.state.value.draft.extHatType)

        holder.dispatch(MiiEditorEvent.SelectTraitField(MiiTraitField.HairType))
        assertEquals(MiiColorField.Hair, holder.state.value.activeColorField)
    }

    @Test
    fun lockedHatIsStrippedFromTheDraftWhenTheEntitlementLapses() = runTest {
        val persistence = InMemoryMiiEditorPersistence()
        persistence.save(
            ACCOUNT,
            MiiPersistedEditorSession(
                savedProfiles = mapOf(
                    1 to MiiStoredProfile(
                        appearance = MiiAppearance(extHatType = 5, extHatColor = 2),
                        portraitFilePath = "/private/old.png",
                        rendererVersion = "renderer",
                        revision = 1,
                        savedAtEpochMillis = 10,
                    ),
                ),
            ),
        )
        val lapse = CompletableDeferred<Unit>()
        val holder = MiiEditorStateHolder(
            persistence = persistence,
            scope = this,
            ownedHatTypes = flow {
                emit(setOf(5))
                lapse.await()
                emit(emptySet())
            },
        )
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()

        holder.beginEdit()
        holder.dispatch(MiiEditorEvent.RendererReady("renderer"))
        runCurrent()
        assertEquals(MiiEditorMode.EditExisting, holder.state.value.mode)
        assertEquals(5, holder.state.value.draft.extHatType)
        assertFalse(holder.state.value.isDirty)

        lapse.complete(Unit)
        runCurrent()
        assertEquals(emptySet<Int>(), holder.state.value.ownedHatTypes)

        assertEquals(-1, holder.state.value.draft.extHatType)
        assertEquals(5, holder.state.value.saved?.extHatType)
        assertTrue(holder.state.value.isDirty)
    }

    @Test
    fun beginEditStripsALockedHatButKeepsTheSavedMiiIntact() = runTest {
        val persistence = InMemoryMiiEditorPersistence()
        persistence.save(
            ACCOUNT,
            MiiPersistedEditorSession(
                savedProfiles = mapOf(
                    1 to MiiStoredProfile(
                        appearance = MiiAppearance(extHatType = 9, extHatColor = 1),
                        portraitFilePath = "/private/old.png",
                        rendererVersion = "renderer",
                        revision = 1,
                        savedAtEpochMillis = 10,
                    ),
                ),
            ),
        )
        val holder = MiiEditorStateHolder(
            persistence = persistence,
            scope = this,
            ownedHatTypes = flowOf(setOf(2)),
        )
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()

        holder.beginEdit()

        assertEquals(-1, holder.state.value.draft.extHatType)
        assertEquals(9, holder.state.value.saved?.extHatType)
        assertTrue(holder.state.value.isDirty)
    }

    @Test
    fun rejectedPublicationStripsTheLockedHatSoTheMiiCanBeSavedAgain() = runTest {
        val persistence = InMemoryMiiEditorPersistence()
        persistence.save(
            ACCOUNT,
            MiiPersistedEditorSession(
                savedProfiles = mapOf(
                    1 to MiiStoredProfile(
                        appearance = MiiAppearance(extHatType = 5, extHatColor = 2),
                        portraitFilePath = "/private/old.png",
                        rendererVersion = "renderer",
                        revision = 1,
                        savedAtEpochMillis = 10,
                    ),
                ),
            ),
        )
        val holder = MiiEditorStateHolder(
            persistence = persistence,
            scope = this,
            saveCallback = MiiEditorSaveCallback {
                MiiEditorSaveResult.Rejected(MII_HAT_NOT_OWNED_MESSAGE)
            },
            ownedHatTypes = flowOf(setOf(5)),
            nowEpochMillis = { 1234L },
        )
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()
        holder.beginEdit()
        holder.dispatch(MiiEditorEvent.RendererReady("renderer-2"))
        holder.dispatch(MiiEditorEvent.SelectCategory(MiiCategory.Body))
        holder.dispatch(MiiEditorEvent.SetAdjustment(MiiAdjustmentField.Height, 90))

        val capture = async {
            holder.rendererCommands.first {
                it is MiiRendererCommand.CaptureForSave
            } as MiiRendererCommand.CaptureForSave
        }
        runCurrent()
        holder.dispatch(MiiEditorEvent.Save)
        runCurrent()
        val request = capture.await()
        holder.dispatch(
            MiiEditorEvent.RendererSaveReady(
                requestId = request.requestId,
                artifact = MiiRendererSaveArtifact(
                    portraitFilePath = "/private/new.png",
                    rendererVersion = "renderer-2",
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(MiiEditorMode.EditExisting, holder.state.value.mode)
        assertEquals(
            MiiSaveState.Error(message = MII_HAT_NOT_OWNED_MESSAGE, locallyPersisted = true),
            holder.state.value.saveState,
        )
        assertEquals(-1, holder.state.value.draft.extHatType)
        assertEquals(90, holder.state.value.draft.height)
        assertTrue(holder.state.value.isDirty)
    }

    @Test
    fun hiddenHairstylesCannotBeSelected() = runTest {
        val holder = MiiEditorStateHolder(InMemoryMiiEditorPersistence(), this)
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()
        holder.dispatch(MiiEditorEvent.RendererReady("test-renderer"))
        runCurrent()

        holder.dispatch(MiiEditorEvent.SelectCategory(MiiCategory.Hair))
        holder.dispatch(MiiEditorEvent.SelectTrait(MiiTraitField.HairType, 33))
        holder.dispatch(MiiEditorEvent.SelectTrait(MiiTraitField.HairType, 34))
        holder.dispatch(MiiEditorEvent.SelectTrait(MiiTraitField.HairType, 57))

        assertEquals(33, holder.state.value.draft.hairType)
    }

    @Test
    fun beginEditWithAnOwnedHatOpensHairInHatMode() = runTest {
        val persistence = InMemoryMiiEditorPersistence()
        persistence.save(
            ACCOUNT,
            MiiPersistedEditorSession(
                savedProfile = MiiStoredProfile(
                    appearance = MiiAppearance(),
                    portraitFilePath = "/private/old.png",
                    rendererVersion = "renderer",
                    revision = 1,
                    savedAtEpochMillis = 10,
                ),
            ),
        )
        val holder = MiiEditorStateHolder(
            persistence = persistence,
            scope = this,
            ownedHatTypes = flowOf(setOf(5)),
        )
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()

        holder.beginEdit(wearHat = 6)
        assertEquals(MiiCategory.Face, holder.state.value.selectedCategory)
        assertEquals(-1, holder.state.value.draft.extHatType)

        holder.beginEdit(wearHat = 5)
        assertEquals(MiiCategory.Hair, holder.state.value.selectedCategory)
        assertEquals(MiiTraitField.HatType, holder.state.value.activeTraitField)
        assertEquals(MiiColorField.Hat, holder.state.value.activeColorField)
        assertEquals(5, holder.state.value.draft.extHatType)
        assertTrue(holder.state.value.isDirty)
    }

    @Test
    fun useSwitchesTheActivePortraitToTheChosenSlot() = runTest {
        var activated: Int? = null
        val holder = MiiEditorStateHolder(
            persistence = InMemoryMiiEditorPersistence(),
            scope = this,
            activeSlotCallback = MiiActiveSlotCallback { _, slot ->
                activated = slot
                MiiEditorSaveResult.Completed
            },
        )
        suspend fun saveCurrentMii(height: Int, portraitFilePath: String) {
            holder.dispatch(MiiEditorEvent.RendererReady("renderer-1"))
            holder.dispatch(MiiEditorEvent.SelectCategory(MiiCategory.Body))
            holder.dispatch(MiiEditorEvent.SetAdjustment(MiiAdjustmentField.Height, height))
            val capture = async {
                holder.rendererCommands.first {
                    it is MiiRendererCommand.CaptureForSave
                } as MiiRendererCommand.CaptureForSave
            }
            runCurrent()
            holder.dispatch(MiiEditorEvent.Save)
            runCurrent()
            val request = capture.await()
            holder.dispatch(
                MiiEditorEvent.RendererSaveReady(
                    requestId = request.requestId,
                    artifact = MiiRendererSaveArtifact(
                        encodedMii = byteArrayOf(1, 2, 3),
                        portraitFilePath = portraitFilePath,
                        rendererVersion = "renderer-1",
                    ),
                ),
            )
            advanceUntilIdle()
        }
        holder.activateAccount(ACCOUNT)
        advanceUntilIdle()
        saveCurrentMii(height = 100, portraitFilePath = "/private/slot-1.png")
        holder.beginEdit(slot = 2)
        runCurrent()
        saveCurrentMii(height = 90, portraitFilePath = "/private/slot-2.png")

        assertEquals(1, holder.state.value.activeSlot)
        assertEquals("/private/slot-1.png", holder.state.value.activePortraitFilePath)
        assertEquals("/private/slot-2.png", holder.state.value.savedPortraitFilePath)

        holder.setActiveSlot(2)
        advanceUntilIdle()

        assertEquals(2, holder.state.value.activeSlot)
        assertEquals("/private/slot-2.png", holder.state.value.activePortraitFilePath)
        assertEquals(2, activated)

        holder.setActiveSlot(1)
        advanceUntilIdle()

        assertEquals("/private/slot-1.png", holder.state.value.activePortraitFilePath)
        assertEquals(1, activated)
    }

    private companion object {
        const val ACCOUNT = "user-123"
        const val OTHER_ACCOUNT = "user-456"
    }
}
