package com.pocketpass.app.boards

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import com.pocketpass.app.domain.model.UserId
import kotlinx.serialization.json.*
import kotlin.test.*

class BoardRepositoryTest {
    private val account = "99290000-0000-4000-8000-000000000001"
    private val board = "99290000-0000-4000-8000-000000000010"

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun timedRefreshKeepsRevealedSpoilersAndTheFocusedReply() = runTest {
        val api = ReadingBoardApi(board, account)
        val repo = RoomBoardRepository(api, MemoryBoardDao())
        val holder = BoardsStateHolder(repo, MutableStateFlow(UserId(account)), backgroundScope, MutableStateFlow(true))
        runCurrent()
        holder.dispatch(BoardAction.OpenDestination(board, api.root.id)); runCurrent()
        holder.dispatch(BoardAction.Reveal(holder.state.value.thread!!)); runCurrent()
        holder.dispatch(BoardAction.Reveal(holder.state.value.posts.first())); runCurrent()
        holder.dispatch(BoardAction.Focus(holder.state.value.posts.first())); runCurrent()
        api.root = api.root.copy(body = "Updated root")
        api.notes = api.notes.map { it.copy(body = "Updated ${it.id}", yeahCount = 4) }
        advanceTimeBy(BOARDS_POLL_MILLIS + 1); runCurrent()
        val state = holder.state.value
        assertEquals(setOf(api.root.id, api.notes.first().id), state.revealed)
        assertEquals(api.root, state.thread)
        assertEquals(api.notes.first(), state.posts.first())
        assertEquals(api.notes.first(), state.focused)
        assertEquals("", state.posts[1].body)
        assertNull(state.posts[1].drawing)
        assertFalse(state.loading)
        assertNull(state.error)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun realtimeRefreshKeepsTheLatestSelectionWhileReading() = runTest {
        val api = ReadingBoardApi(board, account)
        val repo = RoomBoardRepository(api, MemoryBoardDao())
        val holder = BoardsStateHolder(repo, MutableStateFlow(UserId(account)), backgroundScope, MutableStateFlow(true))
        runCurrent()
        holder.dispatch(BoardAction.OpenDestination(board, api.root.id)); runCurrent()
        holder.dispatch(BoardAction.Reveal(holder.state.value.posts.first())); runCurrent()
        api.pending = CompletableDeferred()
        repo.invalidate(); runCurrent(); advanceTimeBy(151); runCurrent()
        holder.dispatch(BoardAction.Focus(holder.state.value.posts[1]))
        api.notes = api.notes.reversed()
        api.pending!!.complete(Unit); runCurrent()
        assertEquals(api.notes.first().id, holder.state.value.focused?.id)
        assertEquals(api.notes.last(), holder.state.value.posts.last())
        assertEquals(setOf(api.notes.last().id), holder.state.value.revealed)
        assertNull(holder.state.value.error)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun refreshRetainsLoadedPagesAndSelectionWithoutSkippingTheNextPage() = runTest {
        val api = ReadingBoardApi(board, account).apply {
            pageSize = 2
            notes = List(6) { BoardPost("note_$it", board, body = "Note $it") }
        }
        val holder = BoardsStateHolder(RoomBoardRepository(api, MemoryBoardDao()), MutableStateFlow(UserId(account)), backgroundScope, MutableStateFlow(true))
        runCurrent()
        holder.dispatch(BoardAction.OpenBoard(board)); runCurrent()
        holder.dispatch(BoardAction.More); runCurrent()
        holder.dispatch(BoardAction.Focus(holder.state.value.posts.last()))
        holder.dispatch(BoardAction.Refresh); runCurrent()
        assertEquals(api.notes.take(4), holder.state.value.posts)
        assertEquals(api.notes[3], holder.state.value.focused)
        holder.dispatch(BoardAction.More); runCurrent()
        assertEquals(api.notes, holder.state.value.posts)
        holder.dispatch(BoardAction.Sort(BoardSort.Activity)); runCurrent()
        assertEquals(api.notes.take(2), holder.state.value.posts)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun removedSpoilersAndRevokedAccessNeverRestorePrivateContent() = runTest {
        val api = ReadingBoardApi(board, account)
        val holder = BoardsStateHolder(RoomBoardRepository(api, MemoryBoardDao()), MutableStateFlow(UserId(account)), backgroundScope, MutableStateFlow(true))
        runCurrent()
        holder.dispatch(BoardAction.OpenBoard(board)); runCurrent()
        holder.dispatch(BoardAction.Reveal(holder.state.value.posts.first())); runCurrent()
        api.notes = api.notes.map { it.copy(removed = true, body = "", drawing = null) }
        holder.refresh(); runCurrent()
        assertEquals(emptySet(), holder.state.value.revealed)
        assertTrue(holder.state.value.posts.all { it.removed && it.body.isEmpty() && it.drawing == null })
        api.notes = listOf(api.root)
        holder.refresh(); runCurrent()
        holder.dispatch(BoardAction.Reveal(holder.state.value.posts.first())); runCurrent()
        api.denied = true
        holder.refresh(); runCurrent()
        assertTrue(holder.state.value.revealed.isEmpty())
        assertTrue(holder.state.value.posts.isEmpty())
        assertNull(holder.state.value.focused)
        assertNull(holder.state.value.thread)
        assertNull(holder.state.value.board)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun revealsSurviveReopeningTheBoardButNotAccountChangesOrDisablingBoards() = runTest {
        val api = ReadingBoardApi(board, account)
        val currentAccount = MutableStateFlow<UserId?>(UserId(account))
        val holder = BoardsStateHolder(RoomBoardRepository(api, MemoryBoardDao()), currentAccount, backgroundScope, MutableStateFlow(true))
        runCurrent()
        holder.dispatch(BoardAction.OpenBoard(board)); runCurrent()
        holder.dispatch(BoardAction.Reveal(holder.state.value.posts.first())); runCurrent()
        holder.dispatch(BoardAction.Directory()); runCurrent()
        holder.dispatch(BoardAction.OpenBoard(board)); runCurrent()
        assertEquals(api.notes.first(), holder.state.value.posts.first())
        currentAccount.value = UserId("another-account"); runCurrent()
        holder.dispatch(BoardAction.OpenBoard(board)); runCurrent()
        assertTrue(holder.state.value.revealed.isEmpty())
        assertEquals("", holder.state.value.posts.first().body)
        holder.dispatch(BoardAction.Reveal(holder.state.value.posts.first())); runCurrent()
        api.enabled = false
        advanceTimeBy(BOARDS_SETTINGS_POLL_MILLIS + 1); runCurrent()
        assertTrue(holder.state.value.revealed.isEmpty())
        assertFalse(holder.state.value.enabled)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun inviteFilteringLoadsEveryMemberPageForARegularInviter() = runTest {
        val community = Board(board, account, "Testing board", role = "member", membersCanInvite = true)
        val firstMember = "99290000-0000-4000-8000-000000000021"
        val laterMember = "99290000-0000-4000-8000-000000000022"
        var memberReads = 0
        val api = object : BoardApi {
            override suspend fun query(accountId: String, operation: String, args: JsonObject): JsonElement = when (operation) {
                "settings" -> boardArgs("enabled" to true.boardValue())
                "board" -> community.boardEncode()
                "feed", "directory" -> boardArgs("items" to JsonArray(emptyList()))
                "invitations", "proposals" -> JsonArray(emptyList())
                "management" -> boardArgs("invitations" to JsonArray(emptyList()))
                "members" -> {
                    memberReads++
                    if (args["cursor"] == null) boardArgs(
                        "items" to listOf(BoardMember(firstMember)).boardEncode(),
                        "cursor" to boardArgs("next" to true.boardValue()))
                    else boardArgs("items" to listOf(BoardMember(laterMember)).boardEncode())
                }
                else -> JsonObject(emptyMap())
            }
            override suspend fun mutate(accountId: String, operation: String, args: JsonObject, operationId: String) = JsonObject(emptyMap())
        }
        val holder = BoardsStateHolder(RoomBoardRepository(api, MemoryBoardDao()), MutableStateFlow(UserId(account)), backgroundScope, MutableStateFlow(true))
        runCurrent()
        holder.dispatch(BoardAction.OpenBoard(board)); runCurrent()
        holder.dispatch(BoardAction.Manage); runCurrent()
        assertTrue(holder.state.value.inviteMembersLoaded)
        assertEquals(setOf(firstMember, laterMember), holder.state.value.inviteMemberIds)
        assertEquals(2, memberReads)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun friendCodeInvitationResolvesToAccountIdBeforeBoardMutation() = runTest {
        val recipient = "99290000-0000-4000-8000-000000000023"
        var resolvedCode: String? = null
        var sentArgs: JsonObject? = null
        val api = object : BoardApi {
            override suspend fun query(accountId: String, operation: String, args: JsonObject): JsonElement = when (operation) {
                "settings" -> boardArgs("enabled" to true.boardValue())
                "directory" -> boardArgs("items" to JsonArray(emptyList()))
                "invitations", "proposals" -> JsonArray(emptyList())
                "friend_code" -> {
                    resolvedCode = args.text("code")
                    JsonArray(listOf(boardArgs("user_id" to recipient.boardValue())))
                }
                else -> JsonObject(emptyMap())
            }
            override suspend fun mutate(accountId: String, operation: String, args: JsonObject, operationId: String): JsonObject {
                assertEquals("invite", operation)
                sentArgs = args
                return JsonObject(emptyMap())
            }
        }
        val holder = BoardsStateHolder(RoomBoardRepository(api, MemoryBoardDao()), MutableStateFlow(UserId(account)), backgroundScope, MutableStateFlow(true))
        runCurrent()
        holder.dispatch(BoardAction.Mutate("invite", boardArgs("board_id" to board.boardValue(), "friend_code" to "12345678".boardValue())))
        runCurrent()
        assertEquals("12345678", resolvedCode)
        assertEquals(recipient, sentArgs?.text("user_id"))
        assertNull(sentArgs?.text("friend_code"))
        assertEquals("Invitation sent", holder.state.value.info)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun openingAnInboxItemWaitsForItsDestinationBeforeMarkingRead() = runTest {
        val community = Board(board, account, "Testing board", role = "owner")
        val note = BoardPost("99290000-0000-4000-8000-000000000011", board, body = "Plans")
        val notice = BoardNotice("99290000-0000-4000-8000-000000000012", board, community.name,
            note.id, "activity", subject = "Plans", subjectType = "text")
        val report = BoardNotice("99290000-0000-4000-8000-000000000013", board, community.name,
            kind = "report")
        val boardReady = CompletableDeferred<Unit>()
        val mutations = mutableListOf<String>()
        val api = object : BoardApi {
            override suspend fun query(accountId: String, operation: String, args: JsonObject): JsonElement = when (operation) {
                "settings" -> boardArgs("enabled" to true.boardValue())
                "directory" -> boardArgs("items" to emptyList<Board>().boardEncode())
                "invitations", "proposals" -> JsonArray(emptyList())
                "inbox" -> boardArgs("items" to listOf(notice, report).boardEncode())
                "board" -> { boardReady.await(); community.boardEncode() }
                "post" -> note.boardEncode()
                "replies" -> boardArgs("post" to note.boardEncode(), "items" to emptyList<BoardPost>().boardEncode())
                "management" -> JsonObject(emptyMap())
                "members" -> boardArgs("items" to JsonArray(emptyList()))
                else -> JsonObject(emptyMap())
            }
            override suspend fun mutate(accountId: String, operation: String, args: JsonObject, operationId: String): JsonObject {
                mutations += operation
                return boardArgs("ok" to true.boardValue())
            }
        }
        val holder = BoardsStateHolder(RoomBoardRepository(api, MemoryBoardDao()), MutableStateFlow(UserId(account)), backgroundScope, MutableStateFlow(true))
        runCurrent()
        holder.dispatch(BoardAction.Inbox); runCurrent()
        holder.dispatch(BoardAction.OpenNotice(notice)); runCurrent()
        assertEquals(BoardsScreen.Inbox, holder.state.value.screen)
        assertTrue(mutations.isEmpty())
        boardReady.complete(Unit); runCurrent()
        assertEquals(BoardsScreen.Thread, holder.state.value.screen)
        assertEquals(note.id, holder.state.value.thread?.id)
        assertEquals(listOf("read_thread", "read_event"), mutations)
        assertTrue(holder.back()); runCurrent()
        assertEquals(BoardsScreen.Inbox, holder.state.value.screen)

        holder.dispatch(BoardAction.OpenNotice(report)); runCurrent()
        assertEquals(BoardsScreen.Manage, holder.state.value.screen)
        assertTrue(holder.state.value.openReportsFromInbox)
        assertEquals("read_event", mutations.last())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun refreshRetainsTheVisiblePageButRevokedAccessClearsIt() = runTest {
        val community = Board(board, account, "Testing board", visibility = "private", role = "owner")
        var notes = emptyList<BoardPost>()
        var pending: CompletableDeferred<Unit>? = null
        var denied = false
        val api = object : BoardApi {
            override suspend fun query(accountId: String, operation: String, args: JsonObject): JsonElement = when(operation) {
                "settings" -> boardArgs("enabled" to true.boardValue())
                "board" -> {
                    if(denied) throw BoardFailure("Membership required", false, accessDenied = true)
                    community.boardEncode()
                }
                "feed" -> { pending?.await(); boardArgs("items" to notes.boardEncode()) }
                "invitations", "proposals" -> JsonArray(emptyList())
                else -> boardArgs("items" to JsonArray(emptyList()))
            }
            override suspend fun mutate(accountId: String, operation: String, args: JsonObject, operationId: String) = boardArgs("ok" to true.boardValue())
        }
        val repo = RoomBoardRepository(api, MemoryBoardDao())
        val holder = BoardsStateHolder(repo, MutableStateFlow(UserId(account)), backgroundScope, MutableStateFlow(true))
        runCurrent()
        holder.dispatch(BoardAction.OpenBoard(board)); runCurrent()
        assertFalse(holder.state.value.loading)
        assertTrue(holder.state.value.posts.isEmpty())

        pending = CompletableDeferred()
        holder.refresh(); runCurrent()
        assertFalse(holder.state.value.loading)
        assertEquals(community, holder.state.value.board)
        notes = listOf(BoardPost("note", board, body = "A note"))
        pending!!.complete(Unit); runCurrent()
        assertEquals(notes, holder.state.value.posts)

        pending = CompletableDeferred()
        repo.invalidate(); runCurrent(); advanceTimeBy(151); runCurrent()
        assertEquals(notes, holder.state.value.posts)
        assertFalse(holder.state.value.loading)
        pending!!.complete(Unit); runCurrent(); pending = null

        denied = true
        holder.refresh(); runCurrent()
        assertNull(holder.state.value.board)
        assertTrue(holder.state.value.posts.isEmpty())
        assertNull(holder.state.value.focused)
        assertTrue(holder.state.value.assets.isEmpty())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun boardsStayIdleWhileInactiveAndReuseDownloadedArtwork() = runTest {
        val community = Board(board, account, "Testing board", role = "member", iconAssetId = "icon")
        val reads = mutableListOf<String>()
        val api = object : BoardApi {
            override suspend fun query(accountId: String, operation: String, args: JsonObject): JsonElement {
                reads += operation
                return when (operation) {
                    "settings" -> boardArgs("enabled" to true.boardValue())
                    "directory" -> boardArgs("items" to listOf(community).boardEncode())
                    "invitations", "proposals" -> JsonArray(emptyList())
                    "asset" -> boardArgs("data" to "AQID".boardValue())
                    "feed" -> boardArgs("items" to JsonArray(emptyList()))
                    else -> JsonObject(emptyMap())
                }
            }
            override suspend fun mutate(accountId: String, operation: String, args: JsonObject, operationId: String) = JsonObject(emptyMap())
        }
        val active = MutableStateFlow(false)
        val repo = RoomBoardRepository(api, MemoryBoardDao())
        val holder = BoardsStateHolder(repo, MutableStateFlow(UserId(account)), backgroundScope, active)
        runCurrent()
        repo.invalidate(); runCurrent()
        advanceTimeBy(BOARDS_SETTINGS_POLL_MILLIS + 1); runCurrent()
        assertEquals(listOf("settings"), reads)

        active.value = true; runCurrent(); advanceTimeBy(151); runCurrent()
        assertEquals(listOf("settings", "settings", "directory", "invitations", "proposals", "asset", "feed"), reads)
        assertEquals(setOf("icon"), holder.state.value.assets.keys)

        repo.invalidate(); runCurrent(); advanceTimeBy(151); runCurrent()
        assertEquals(2, reads.count { it == "directory" })
        assertEquals(1, reads.count { it == "asset" })
        assertEquals(setOf("icon"), holder.state.value.assets.keys)

        active.value = false; runCurrent()
        val idle = reads.size
        repo.invalidate(); runCurrent()
        advanceTimeBy(BOARDS_POLL_MILLIS * 3); runCurrent()
        assertEquals(idle, reads.size)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun backgroundRefreshWaitsForAnOpeningBoardInsteadOfCancellingIt() = runTest {
        val community = Board(board, account, "Testing board", role = "member")
        val boardReady = CompletableDeferred<Unit>()
        val api = object : BoardApi {
            override suspend fun query(accountId: String, operation: String, args: JsonObject): JsonElement = when (operation) {
                "settings" -> boardArgs("enabled" to true.boardValue())
                "directory" -> boardArgs("items" to emptyList<Board>().boardEncode())
                "invitations", "proposals" -> JsonArray(emptyList())
                "board" -> { boardReady.await(); community.boardEncode() }
                "feed" -> boardArgs("items" to JsonArray(emptyList()))
                else -> JsonObject(emptyMap())
            }
            override suspend fun mutate(accountId: String, operation: String, args: JsonObject, operationId: String) = boardArgs("ok" to true.boardValue())
        }
        val repo = RoomBoardRepository(api, MemoryBoardDao())
        val holder = BoardsStateHolder(repo, MutableStateFlow(UserId(account)), backgroundScope, MutableStateFlow(true))
        runCurrent()
        holder.dispatch(BoardAction.OpenBoard(board)); runCurrent()
        repo.invalidate(); runCurrent()
        advanceTimeBy(BOARDS_POLL_MILLIS + 1); runCurrent()
        assertEquals(BoardsScreen.Directory, holder.state.value.screen)
        boardReady.complete(Unit); runCurrent()
        assertEquals(BoardsScreen.Board, holder.state.value.screen)
        assertEquals(community, holder.state.value.board)
        assertFalse(holder.state.value.loading)
        assertNull(holder.state.value.error)
    }

    @Test fun failedPublishKeepsExactPayloadAcrossRepositoryRestart() = runTest {
        val dao = MemoryBoardDao()
        val calls = mutableListOf<Pair<String, JsonObject>>()
        var uncertain = true
        val api = FakeBoardApi { operation, args, id ->
            assertEquals("publish", operation)
            calls += id to args
            if (uncertain) { uncertain = false; throw BoardFailure("Connection interrupted", true) }
            boardArgs("id" to args.getValue("id"), "thread_id" to args.getValue("id"))
        }
        val repo = RoomBoardRepository(api, dao)
        val draft = LocalBoardDraft(boardId = board, content = BoardDraftContent(body = "A saved note"))
        repo.saveLocal(account, draft)
        assertFailsWith<BoardFailure> { repo.publish(account, draft.id) }
        val saved = repo.drafts(account).single()
        assertNotNull(saved.pendingPublishId)
        assertFailsWith<IllegalStateException> { saved.edited(saved.content.copy(body = "Different")) }
        val restarted = RoomBoardRepository(api, dao)
        restarted.publish(account, draft.id)
        assertEquals(calls[0], calls[1])
        assertTrue(restarted.drafts(account).isEmpty())
    }

    @Test fun definitiveRejectionRetainsEditableDraftAndServerReason() = runTest {
        val repo = RoomBoardRepository(FakeBoardApi { _, _, _ -> throw BoardFailure("Please change this phrase", false) }, MemoryBoardDao())
        val draft = LocalBoardDraft(boardId = board, content = BoardDraftContent(body = "Original"))
        repo.saveLocal(account, draft)
        assertEquals("Please change this phrase", assertFailsWith<BoardFailure> { repo.publish(account, draft.id) }.message)
        val retained = repo.drafts(account).single()
        assertNull(retained.pendingPublishId)
        assertEquals("Original", retained.content.body)
        assertEquals("Revised", retained.edited(retained.content.copy(body = "Revised")).content.body)
    }

    @Test fun cloudAutosaveNeverPublishesAndLocalEditDuringUploadSurvives() = runTest {
        val uploadStarted = CompletableDeferred<Unit>()
        val uploadDone = CompletableDeferred<Unit>()
        val api = FakeBoardApi { operation, _, _ ->
            assertEquals("save_draft", operation)
            uploadStarted.complete(Unit); uploadDone.await(); boardArgs("ok" to true.boardValue())
        }
        val repo = RoomBoardRepository(api, MemoryBoardDao())
        val original = LocalBoardDraft(boardId = board, content = BoardDraftContent(body = "Phone"))
        repo.saveLocal(account, original)
        val sync = async { repo.syncDraft(account, original.id) }
        uploadStarted.await()
        val newer = original.edited(original.content.copy(body = "Phone with another sentence"))
        repo.saveLocal(account, newer); uploadDone.complete(Unit); sync.await()
        val saved = repo.drafts(account).single()
        assertEquals(newer.content, saved.content)
        assertEquals(original.revisionId, saved.cloudBaseId)
        assertFalse(saved.cloudSynced)
    }

    @Test fun expiredCloudBaseForksWithoutLosingWorkAndStaleUiCannotRestoreIt() = runTest {
        val calls = mutableListOf<JsonObject>()
        val repo = RoomBoardRepository(FakeBoardApi { operation, args, _ ->
            assertEquals("save_draft", operation); calls += args
            if(calls.size == 1) throw BoardFailure("Old base", false, code = "BOARD_DRAFT_BASE_EXPIRED")
            boardArgs("ok" to true.boardValue())
        }, MemoryBoardDao())
        val stale = LocalBoardDraft(boardId = board, cloudBaseId = newBoardId(), content = BoardDraftContent(body = "Offline note"))
        repo.saveLocal(account, stale)
        val recovered = assertNotNull(repo.syncDraft(account, stale.id))
        assertNotEquals(stale.cloudDraftId, recovered.cloudDraftId)
        assertNull(recovered.cloudBaseId)
        assertTrue(recovered.recovered)
        repo.saveLocal(account, stale.edited(stale.content.copy(body = "Another sentence")))
        val saved = assertNotNull(repo.syncDraft(account, stale.id))
        assertEquals(recovered.cloudDraftId, saved.cloudDraftId)
        assertEquals("Another sentence", saved.content.body)
        assertTrue(saved.cloudSynced)
        assertNull(calls.last()["base_id"])
    }

    @Test fun staleUiEditKeepsAcknowledgedCloudBase() = runTest {
        val repo = RoomBoardRepository(FakeBoardApi(), MemoryBoardDao())
        val stale = LocalBoardDraft(boardId = board, content = BoardDraftContent(body = "First"))
        repo.saveLocal(account, stale); repo.syncDraft(account, stale.id)
        repo.saveLocal(account, stale.edited(stale.content.copy(body = "Second")))
        assertEquals(stale.revisionId, repo.drafts(account).single().cloudBaseId)
    }

    @Test fun cloudConflictRecoveryKeepsLocalAndRemoteCopies() = runTest {
        val remoteRevision = newBoardId()
        val local = LocalBoardDraft(boardId = board, content = BoardDraftContent(body = "Thor copy"))
        val api = FakeBoardApi().apply {
            queryResult = boardArgs("items" to JsonArray(listOf(boardArgs("id" to remoteRevision.boardValue(),
                "draft_id" to local.cloudDraftId.boardValue(), "board_id" to board.boardValue(),
                "payload" to BoardDraftContent(body = "Phone copy").boardEncode()))))
        }
        val repo = RoomBoardRepository(api, MemoryBoardDao())
        repo.saveLocal(account, local); repo.recoverCloudDrafts(account); repo.recoverCloudDrafts(account)
        val copies = repo.drafts(account)
        assertEquals(2, copies.size)
        assertEquals(setOf("Thor copy", "Phone copy"), copies.map { it.content.body }.toSet())
        assertTrue(copies.single { it.revisionId == remoteRevision }.recovered)
    }

    @Test fun draftsAreAccountScopedAndClearingContentDoesNotEraseUnpublishedWork() = runTest {
        val repo = RoomBoardRepository(FakeBoardApi(), MemoryBoardDao())
        repo.saveLocal(account, LocalBoardDraft(boardId = board, content = BoardDraftContent(body = "Private draft")))
        assertTrue(repo.drafts("another-account").isEmpty())
        repo.clearContentCache(account)
        assertEquals("Private draft", repo.drafts(account).single().content.body)
    }

    @Test fun drawingHistoryPreservesPensAndRedoIsDiscardedAfterNewStroke() {
        val pixel = BoardStroke(points = listOf(listOf(20f, 30f)))
        val smooth = BoardStroke(pen = "smooth", points = listOf(listOf(40f, 50f)))
        val full = BoardDrawingHistory().add(pixel).add(smooth)
        assertEquals(listOf("pixel", "smooth"), full.drawing.strokes.map { it.pen })
        assertEquals(full, full.undo().redo())
        val replacement = full.undo().add(pixel.copy(color = "#E84A5F"))
        assertTrue(replacement.redo.isEmpty())
        assertEquals(pixel, replacement.drawing.strokes.first())
    }

    @Test fun drawingLimitsRejectNonFiniteCoordinatesUnsupportedToolsAndOversizedNotes() {
        val dot = BoardStroke(points = listOf(listOf(1f, 2f)))
        BoardDrawing(strokes = listOf(dot)).validate()
        assertFailsWith<IllegalArgumentException> { BoardDrawing(width = 801).validate() }
        assertFailsWith<IllegalArgumentException> { BoardDrawing(strokes = listOf(dot.copy(points = listOf(listOf(Float.NaN, 2f))))).validate() }
        assertFailsWith<IllegalArgumentException> { BoardDrawing(strokes = listOf(dot.copy(pen = "photo"))).validate() }
        assertFailsWith<IllegalArgumentException> { BoardDrawing(strokes = List(1001) { dot }).validate() }
        assertFailsWith<IllegalArgumentException> { BoardDrawing(strokes = listOf(dot.copy(points = List(20_001) { listOf(1f, 2f) }))).validate() }
    }
}

private class ReadingBoardApi(boardId: String, ownerId: String) : BoardApi {
    val community = Board(boardId, ownerId, "Reading tests", role = "member")
    var root = BoardPost("root", boardId, body = "Root spoiler", spoiler = true)
    var notes = listOf(
        BoardPost("first", boardId, body = "First spoiler", spoiler = true, hasDrawing = true,
            drawing = BoardDrawing(strokes = listOf(BoardStroke(points = listOf(listOf(10f, 10f)))))),
        BoardPost("second", boardId, body = "Second spoiler", spoiler = true),
    )
    var pageSize = 30
    var pending: CompletableDeferred<Unit>? = null
    var denied = false
    var enabled = true
    private fun BoardPost.redacted() = if (spoiler) copy(body = "", drawing = null, stationery = null) else this
    override suspend fun query(accountId: String, operation: String, args: JsonObject): JsonElement = when (operation) {
        "settings" -> boardArgs("enabled" to enabled.boardValue())
        "board" -> {
            if (denied) throw BoardFailure("Membership required", false, accessDenied = true)
            community.boardEncode()
        }
        "feed", "replies" -> {
            pending?.await()
            val offset = (args["cursor"] as? JsonObject)?.get("offset")?.jsonPrimitive?.int ?: 0
            val next = offset + pageSize
            boardArgs("items" to notes.drop(offset).take(pageSize).map { it.redacted() }.boardEncode(),
                "post" to root.redacted().boardEncode().takeIf { operation == "replies" },
                "cursor" to boardArgs("offset" to JsonPrimitive(next)).takeIf { next < notes.size })
        }
        "post" -> {
            val post = (notes + root).first { it.id == args.text("post_id") }
            (if (args["reveal"]?.jsonPrimitive?.booleanOrNull == true) post else post.redacted()).boardEncode()
        }
        "directory" -> boardArgs("items" to emptyList<Board>().boardEncode())
        "invitations", "proposals" -> JsonArray(emptyList())
        else -> JsonObject(emptyMap())
    }
    override suspend fun mutate(accountId: String, operation: String, args: JsonObject, operationId: String) = boardArgs("ok" to true.boardValue())
}

private class FakeBoardApi(val write: suspend (String, JsonObject, String)->JsonObject = { _, _, _ -> boardArgs("ok" to true.boardValue()) }) : BoardApi {
    var queryResult: JsonElement = boardArgs("items" to JsonArray(emptyList()))
    override suspend fun query(accountId: String, operation: String, args: JsonObject) = queryResult
    override suspend fun mutate(accountId: String, operation: String, args: JsonObject, operationId: String) = write(operation,args,operationId)
}
private class MemoryBoardDao : BoardDao {
    val rows = MutableStateFlow<List<BoardDraftEntity>>(emptyList())
    override fun observeDrafts(accountId: String) = rows.map { all -> all.filter { it.accountId == accountId } }
    override suspend fun drafts(accountId: String) = rows.value.filter { it.accountId == accountId }
    override suspend fun draft(accountId: String, draftId: String) = rows.value.firstOrNull { it.accountId == accountId && it.draftId == draftId }
    override suspend fun saveDraft(draft: BoardDraftEntity) { rows.value = rows.value.filterNot { it.accountId == draft.accountId && it.draftId == draft.draftId } + draft }
    override suspend fun deleteDraft(accountId: String, draftId: String) { rows.value = rows.value.filterNot { it.accountId == accountId && it.draftId == draftId } }
    override suspend fun cache(record: BoardRecordEntity) = Unit
    override suspend fun invalidateBoard(accountId: String, boardId: String) = Unit
    override suspend fun invalidateAccount(accountId: String) = Unit
}
