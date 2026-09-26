package com.pocketpass.app.boards

import com.pocketpass.app.domain.model.UserId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64

class BoardsStateHolder(
    private val repository: BoardRepository?,
    private val account: StateFlow<UserId?>,
    private val scope: CoroutineScope,
) {
    private val mutable = MutableStateFlow(BoardsUiState())
    val state = mutable.asStateFlow()
    private var draftSync: Job? = null
    private var readJob: Job? = null
    private var previewJob: Job? = null
    private var readGeneration = 0L
    private var loadedPage: String? = null
    private var mutationJob: Job? = null
    private var failedMutation: BoardAction.Mutate? = null
    private data class PendingImage(val board: String, val kind: String, val bytes: ByteArray, val id: String = newBoardId())
    private var pendingImage: PendingImage? = null

    init {
        scope.launch {
            account.collectLatest { user ->
                draftSync?.cancel(); readJob?.cancel(); mutationJob?.cancel(); previewJob?.cancel()
                mutable.value = BoardsUiState()
                loadedPage = null
                readGeneration++
                pendingImage = null; failedMutation = null
                if (user == null || repository == null) return@collectLatest
                repository.clearContentCache(user.value)
                launch { repository.observeDrafts(user.value).collect { drafts ->
                    mutable.update { s -> s.copy(drafts = drafts, draft = s.draft?.let { current -> drafts.firstOrNull { it.id == current.id && (it.revisionId == current.revisionId || it.content == current.content) } ?: current }) }
                } }
                refreshSettings(user.value)
                if (mutable.value.enabled && mutable.value.screen == BoardsScreen.Directory) refresh()
                launch { repository.invalidations.collect {
                    // Revalidate with the server before replacing the visible page.
                    // Access failures still purge private content in fail().
                    delay(150)
                    if (!mutable.value.busy) refresh()
                } }
                while (true) {
                    delay(30_000)
                    refreshSettings(user.value)
                    if (mutable.value.enabled) {
                        // Sync only drafts. A reconnect must never publish a note.
                        try {
                            repository.drafts(user.value).filter { !it.cloudSynced && it.pendingPublishId == null }
                                .forEach { repository.syncDraft(user.value, it.id) }
                        } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                    }
                    if (mutable.value.screen !in listOf(BoardsScreen.Chooser, BoardsScreen.Chats, BoardsScreen.Compose) && !mutable.value.busy) refresh()
                }
            }
        }
    }

    private suspend fun refreshSettings(user: String) {
        try {
            val config = repository?.query(user, "settings")?.jsonObject ?: return
            val enabled = config["enabled"]?.jsonPrimitive?.booleanOrNull == true
            mutable.update { s -> if (account.value?.value != user) s else s.copy(enabled = enabled,
                pushEnabled = config["push_enabled"]?.jsonPrimitive?.booleanOrNull != false,
                requestsOpen = config["requests_open"]?.jsonPrimitive?.booleanOrNull != false,
                posts = if (enabled) s.posts else emptyList(), focused = if (enabled) s.focused else null,
                revealed = if (enabled) s.revealed else emptySet(),
                thread = if (enabled) s.thread else null, boards = if (enabled) s.boards else emptyList(),
                directoryFocusId = if(enabled) s.directoryFocusId else null,
                directoryLatest = if(enabled) s.directoryLatest else null,
                pinnedPostId = if(enabled) s.pinnedPostId else null) }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { /* First-party chat remains usable during an outage. */ }
    }

    fun dispatch(action: BoardAction) {
        when (action) {
            is BoardAction.OpenDestination -> openDestination(action.boardId, action.threadId, action.review)
            is BoardAction.OpenNotice -> openNotice(action.notice)
            BoardAction.OpenChooser -> dispatch(BoardAction.Directory())
            BoardAction.OpenChats -> {
                previewJob?.cancel()
                readJob?.cancel()
                mutable.update { it.copy(screen = BoardsScreen.Chats, loading = false, error = null, info = null, cursor = null) }
            }
            is BoardAction.Directory -> {
                previewJob?.cancel()
                val previous = state.value
                val changed = previous.screen != BoardsScreen.Directory || previous.explore != action.explore || previous.search != action.search
                if(changed) loadedPage = null
                mutable.update { it.copy(reviewing = false, screen = BoardsScreen.Directory, explore = action.explore, search = action.search, board = null, thread = null, focused = null, cursor = null, posts = emptyList(),
                    noticeReturnToInbox = false, openReportsFromInbox = false,
                    members = emptyList(), management = null, inviteMemberIds = emptySet(), inviteMembersLoaded = false,
                    boards = if(changed) emptyList() else it.boards,
                    pinnedPostId = null, directoryFocusId = null, directoryLatest = null, directoryPreviewLoading = false, directoryPreviewError = false) }
                refresh()
            }
            is BoardAction.OpenBoard -> openBoard(action.id)
            is BoardAction.OpenThread -> {
                loadedPage = null
                mutable.update { it.copy(screen = BoardsScreen.Thread, thread = action.post, focused = action.post, posts = emptyList(), cursor = null, pinnedPostId = null) }
                refresh()
            }
            is BoardAction.Focus -> mutable.update { it.copy(focused = action.post) }
            is BoardAction.PreviewBoard -> previewBoard(action.id)
            is BoardAction.TogglePin -> mutable.update { s ->
                if(s.screen != BoardsScreen.Thread || (s.posts + listOfNotNull(s.thread)).none { it.id == action.postId }) s
                else s.copy(pinnedPostId = action.postId.takeUnless { it == s.pinnedPostId })
            }
            is BoardAction.CanvasViewport -> mutable.update { it.copy(canvasViewport = action.viewport) }
            is BoardAction.Reveal -> read { user, repo ->
                val post: BoardPost = repo.query(user, "post", boardArgs("post_id" to action.post.id.boardValue(), "reveal" to true.boardValue(), "review" to state.value.reviewing.boardValue())).boardDecode()
                mutable.update { it.copy(revealed = it.revealed + post.id, focused = post,
                    thread = if (it.thread?.id == post.id) post else it.thread, posts = it.posts.map { old -> if (old.id == post.id) post else old }) }
            }
            is BoardAction.Sort -> { mutable.update { it.copy(sort = action.sort, period = action.period, cursor = null) }; refresh() }
            BoardAction.More -> refresh(more = true)
            BoardAction.Refresh -> { pendingImage?.let { importBranding(it.kind); return }; failedMutation?.let { dispatch(it); return }; refresh() }
            BoardAction.Back -> back()
            BoardAction.ClearError -> mutable.update { it.copy(error = null, info = null) }
            is BoardAction.Compose -> {
                val b = state.value.board ?: return
                val draft = LocalBoardDraft(boardId = b.id, content = BoardDraftContent(threadId = state.value.thread?.id, replyTo = action.replyTo))
                mutable.update { it.copy(screen = BoardsScreen.Compose, draft = draft, drawingHistory = BoardDrawingHistory(),
                    activeStroke = null, canvasViewport = BoardCanvasViewport(), error = null) }
                persistDraft(draft)
            }
            is BoardAction.ImportBranding -> importBranding(action.kind)
            is BoardAction.DrawBranding -> {
                val b = state.value.board ?: return
                val drawing = (if(action.kind == "icon") b.iconDrawing else b.coverDrawing) ?: BoardDrawing()
                val draft = LocalBoardDraft(boardId = b.id, content = BoardDraftContent(brandingKind = action.kind, drawing = drawing))
                mutable.update { it.copy(screen = BoardsScreen.Compose, draft = draft, drawingHistory = BoardDrawingHistory(drawing),
                    activeStroke = null, canvasViewport = BoardCanvasViewport(drawing = true), error = null) }
                persistDraft(draft)
            }
            is BoardAction.ResumeDraft -> {
                mutable.update { it.copy(screen = BoardsScreen.Compose, draft = action.draft, error = null,
                    activeStroke = null, canvasViewport = BoardCanvasViewport(drawing = action.draft.content.drawing != null),
                    drawingHistory = BoardDrawingHistory(action.draft.content.drawing ?: BoardDrawing())) }
                read { user, repo ->
                    val b: Board = repo.query(user, "board", boardArgs("board_id" to action.draft.boardId.boardValue())).boardDecode()
                    mutable.update { it.copy(board = b) }
                }
            }
            is BoardAction.DiscardDraft -> read { user, repo -> repo.discardDraft(user, action.draft) }
            is BoardAction.Text -> editDraft { it.copy(body = action.text.take(if (it.threadId == null) 1000 else 500)) }
            is BoardAction.Spoiler -> editDraft { it.copy(spoiler = action.enabled) }
            is BoardAction.Tool -> mutable.update { it.copy(pen = action.pen ?: it.pen, ink = action.color ?: it.ink, penSize = action.size ?: it.penSize) }
            is BoardAction.Stroke -> { drawingChange { it.add(action.stroke) }; mutable.update { it.copy(activeStroke = null) } }
            is BoardAction.PreviewStroke -> mutable.update { it.copy(activeStroke = action.stroke) }
            BoardAction.Undo -> drawingChange { it.undo() }
            BoardAction.Redo -> drawingChange { it.redo() }
            BoardAction.Publish -> publish()
            BoardAction.Drafts -> { mutable.update { it.copy(screen = BoardsScreen.Drafts) }; read { user, repo -> repo.recoverCloudDrafts(user) } }
            BoardAction.Propose -> mutable.update { it.copy(screen = BoardsScreen.Propose, error = null) }
            BoardAction.Manage -> { mutable.update { it.copy(screen = BoardsScreen.Manage, openReportsFromInbox = false,
                inviteMemberIds = emptySet(), inviteMembersLoaded = false) }; refresh() }
            BoardAction.Inbox -> { mutable.update { it.copy(screen = BoardsScreen.Inbox, cursor = null, noticeReturnToInbox = false, openReportsFromInbox = false) }; refresh() }
            BoardAction.Notices -> { mutable.update { it.copy(screen = BoardsScreen.Notices) }; refresh() }
            BoardAction.Stationery -> { mutable.update { it.copy(screen = BoardsScreen.Stationery) }; refresh() }
            is BoardAction.SelectStationery -> {
                editDraft { it.copy(stationeryId = action.id) }
                mutable.update { it.copy(screen = BoardsScreen.Compose) }
            }
            is BoardAction.Mutate -> mutate(action)
        }
    }

    fun back(): Boolean {
        val s = state.value
        if (s.screen == BoardsScreen.Chooser || (s.screen == BoardsScreen.Directory && !s.explore)) return false
        val next = if (s.noticeReturnToInbox && s.screen in listOf(BoardsScreen.Board, BoardsScreen.Thread, BoardsScreen.Manage)) BoardsScreen.Inbox else when (s.screen) {
            BoardsScreen.Chats, BoardsScreen.Directory -> BoardsScreen.Directory
            BoardsScreen.Thread, BoardsScreen.Manage -> BoardsScreen.Board
            BoardsScreen.Compose -> if (s.draft?.content?.threadId != null && s.thread != null) BoardsScreen.Thread else if (s.board != null) BoardsScreen.Board else BoardsScreen.Drafts
            BoardsScreen.Stationery -> if (s.draft != null) BoardsScreen.Compose else BoardsScreen.Directory
            BoardsScreen.Board, BoardsScreen.Inbox, BoardsScreen.Notices, BoardsScreen.Propose, BoardsScreen.Drafts -> BoardsScreen.Directory
            BoardsScreen.Chooser -> BoardsScreen.Chooser
        }
        mutable.update { it.copy(screen = next, explore = if(next == BoardsScreen.Directory) false else it.explore,
            error = null, info = null, cursor = null, thread = if (next == BoardsScreen.Board) null else it.thread,
            noticeReturnToInbox = s.screen == BoardsScreen.Compose && next == BoardsScreen.Thread && it.noticeReturnToInbox,
            openReportsFromInbox = false,
            pinnedPostId = it.pinnedPostId.takeIf { next == BoardsScreen.Thread || next == BoardsScreen.Compose }) }
        if (next !in listOf(BoardsScreen.Compose, BoardsScreen.Chooser, BoardsScreen.Chats, BoardsScreen.Drafts)) refresh()
        return true
    }

    fun openDestination(boardId: String, threadId: String? = null, review: Boolean = false) = read { user, repo ->
        val board: Board = repo.query(user, "board", boardArgs("board_id" to boardId.boardValue(), "review" to review.boardValue())).boardDecode()
        var post: BoardPost? = threadId?.let { repo.query(user, "post", boardArgs("post_id" to it.boardValue(), "review" to review.boardValue())).boardDecode() }
        post?.threadId?.let { parent -> post = repo.query(user, "post", boardArgs("post_id" to parent.boardValue(), "review" to review.boardValue())).boardDecode() }
        if(!review) repo.mutate(user, "read_thread", boardArgs("board_id" to boardId.boardValue(), "thread_id" to post?.id?.boardValue()))
        loadedPage = null
        mutable.update { it.copy(reviewing = review, board = board, thread = post, focused = post, screen = if (post == null) BoardsScreen.Board else BoardsScreen.Thread, cursor = null, posts = emptyList(), pinnedPostId = null, noticeReturnToInbox = false, openReportsFromInbox = false,
            members = emptyList(), management = null, inviteMemberIds = emptySet(), inviteMembersLoaded = false) }
        refresh()
    }
    private fun openNotice(notice: BoardNotice) = read { user, repo ->
        val report = notice.kind == "report"
        val board: Board = repo.query(user, "board", boardArgs("board_id" to notice.boardId.boardValue())).boardDecode()
        var post: BoardPost? = if (report) null else notice.threadId?.let {
            repo.query(user, "post", boardArgs("post_id" to it.boardValue())).boardDecode()
        }
        post?.threadId?.let { parent -> post = repo.query(user, "post", boardArgs("post_id" to parent.boardValue())).boardDecode() }
        loadedPage = null
        mutable.update { it.copy(reviewing = false, board = board, thread = post, focused = post,
            screen = if (report) BoardsScreen.Manage else if (post == null) BoardsScreen.Board else BoardsScreen.Thread,
            cursor = null, posts = emptyList(), pinnedPostId = null, noticeReturnToInbox = true,
            members = emptyList(), management = null, inviteMemberIds = emptySet(), inviteMembersLoaded = false,
            openReportsFromInbox = report, error = null) }
        // Keep read markers independent of page loads: their invalidations can refresh
        // the page, but cannot interrupt opening the destination or each other.
        val openedThreadId = post?.id
        scope.launch {
            try {
                if (!report) repo.mutate(user, "read_thread", boardArgs("board_id" to notice.boardId.boardValue(), "thread_id" to openedThreadId?.boardValue()))
            } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            try { repo.mutate(user, "read_event", boardArgs("id" to notice.id.boardValue())) }
            catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
        refresh()
    }
    private fun openBoard(id: String) = openDestination(id)

    private fun importBranding(kind: String) {
        if (mutable.value.busy) return
        val board = mutable.value.board ?: return
        val user = account.value?.value ?: return
        val repo = repository ?: return
        mutable.update { it.copy(busy = true, error = null) }
        mutationJob = scope.launch {
            try {
                val upload = pendingImage ?: BoardBrandingPicker.pick()?.let { PendingImage(board.id, kind, it) }
                if (upload != null) {
                    pendingImage = upload
                    repo.uploadBranding(user, upload.board, upload.kind, upload.bytes, upload.id)
                    pendingImage = null
                    mutable.update { it.copy(info = "Board artwork saved", assets = emptyMap()) }
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(e is BoardFailure && !e.retryable) pendingImage = null; fail(e) }
            finally { mutable.update { it.copy(busy = false) } }
            if (pendingImage == null && mutable.value.error == null) refresh()
        }
    }

    private suspend fun loadArtwork(user: String, repo: BoardRepository, boards: List<Board>) {
        val assets = mutableMapOf<String, ByteArray>()
        boards.flatMap { listOfNotNull(it.iconAssetId, it.coverAssetId) }.distinct().forEach { id ->
            val value = repo.query(user, "asset", boardArgs("asset_id" to id.boardValue())).jsonObject
            value.text("data")?.let { assets[id] = Base64.Mime.decode(it) }
        }
        mutable.update { it.copy(assets = assets) }
    }

    private fun drawingChange(change: (BoardDrawingHistory) -> BoardDrawingHistory) {
        try {
            val history = change(state.value.drawingHistory)
            editDraft { it.copy(drawing = history.drawing.takeIf { drawing -> drawing.strokes.isNotEmpty() }) }
            mutable.update { it.copy(drawingHistory = history) }
        } catch (e: IllegalArgumentException) { mutable.update { it.copy(error = e.message ?: "This drawing is too large") } }
    }
    private fun editDraft(change: (BoardDraftContent) -> BoardDraftContent) {
        val draft = state.value.draft ?: return
        if (draft.pendingPublishId != null || state.value.busy) return
        val updated = draft.edited(change(draft.content))
        mutable.update { it.copy(draft = updated, error = null) }
        persistDraft(updated)
    }
    private fun persistDraft(draft: LocalBoardDraft) {
        val user = account.value?.value ?: return
        val repo = repository ?: return
        scope.launch { repo.saveLocal(user, draft) }
        draftSync?.cancel()
        draftSync = scope.launch {
            delay(900)
            try { repo.syncDraft(user, draft.id) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Local saves stay intact; retry syncing on next edit/open. */ }
        }
    }

    private fun publish() {
        if (mutable.value.busy) return
        val draft = mutable.value.draft ?: return
        val user = account.value?.value ?: return
        val repo = repository ?: return
        draftSync?.cancel()
        mutable.update { it.copy(busy = true, error = null) }
        mutationJob = scope.launch {
            try {
                repo.saveLocal(user, draft)
                val result = repo.publish(user, draft.id)
                mutable.update { it.copy(draft = null, busy = false, info = "Your note is posted") }
                openDestination(draft.boardId, result.text("thread_id"))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(e); mutable.update { it.copy(busy = false, draft = repo.drafts(user).firstOrNull { it.id == draft.id } ?: draft) } }
        }
    }
    private fun mutate(action: BoardAction.Mutate) {
        if (mutable.value.busy) return
        val user = account.value?.value ?: return
        val repo = repository ?: return
        val request = action.copy(args = JsonObject(boardArgs("review" to state.value.reviewing.boardValue()) + action.args))
        val inviteStatus = if(action.operation == "invite") BoardInviteStatus(
            action.args.text("board_id").orEmpty(), action.args.text("user_id") ?: action.args.text("friend_code").orEmpty()) else null
        mutable.update { it.copy(busy = true, error = null, info = null, inviteStatus = inviteStatus ?: it.inviteStatus) }
        mutationJob = scope.launch {
            try {
                val args = if (action.operation == "invite" && action.args.text("friend_code") != null) {
                    val code = requireNotNull(action.args.text("friend_code"))
                    val recipient = repo.query(user, "friend_code", boardArgs("code" to code.boardValue()))
                        .jsonArray.firstOrNull()?.jsonObject?.text("user_id")
                        ?: throw BoardFailure("No PocketPass user is available with that friend code.", false)
                    JsonObject(request.args - "friend_code" + ("user_id" to recipient.boardValue()))
                } else request.args
                val result = repo.mutate(user, request.operation, args, request.operationId)
                failedMutation = null
                mutable.update { it.copy(busy = false, inviteStatus = inviteStatus?.copy(sending = false) ?: it.inviteStatus,
                    inviteCode = result.text("code") ?: it.inviteCode, info = when(action.operation) {
                    "propose" -> "Your board request is ready for staff review"
                    "report" -> "Report sent. Case ${result.text("case_id")}"
                    "invite" -> "Invitation sent"
                    "appeal" -> "Appeal sent to the board moderators"
                    else -> "Saved"
                }) }
                when (action.operation) {
                    "push_preference" -> refreshSettings(user)
                    "leave" -> dispatch(BoardAction.Directory())
                    "join_code", "accept_invitation" -> result.text("board_id")?.let { if(result.text("status") == "joined") openBoard(it) else refresh() }
                    "propose" -> dispatch(BoardAction.Directory())
                    else -> refresh()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                failedMutation = request.takeIf { e !is BoardFailure || e.retryable }
                fail(e)
                mutable.update { it.copy(busy = false,
                    inviteStatus = inviteStatus?.copy(sending = false, error = it.error ?: "Could not send invitation. Try again.") ?: it.inviteStatus) }
            }
        }
    }

    fun refresh(more: Boolean = false) {
        val snapshot = state.value
        val pageKey = "${snapshot.screen}:${snapshot.board?.id}:${snapshot.thread?.id}:${snapshot.explore}:${snapshot.search}:${snapshot.sort}:${snapshot.period}"
        val refreshCount = if (!more && loadedPage == pageKey) snapshot.posts.size else 0
        read(showLoading = loadedPage != pageKey) { user, repo ->
            if (!snapshot.enabled) refreshSettings(user)
            when (snapshot.screen) {
                BoardsScreen.Directory -> {
                    val page = repo.query(user, "directory", boardArgs("scope" to (if(snapshot.explore) "explore" else "joined").boardValue(), "search" to snapshot.search.boardValue(), "cursor" to snapshot.cursor.takeIf { more })).jsonObject
                    val rows: List<Board> = page.getValue("items").boardDecode()
                    val invites: List<BoardInvitation> = repo.query(user, "invitations").boardDecode()
                    val proposals = repo.query(user, "proposals").jsonArray
                    mutable.update { it.copy(proposals = proposals, boards = (if(more) it.boards + rows else rows).distinctBy(Board::id), invitations = invites, cursor = page["cursor"] as? JsonObject) }
                    loadArtwork(user, repo, mutable.value.boards)
                    val previewId = mutable.value.directoryFocusId?.takeIf { id -> mutable.value.boards.any { it.id == id } }
                        ?: mutable.value.boards.firstOrNull()?.id
                    if(previewId != null) previewBoard(previewId, refresh = true)
                    else mutable.update { it.copy(directoryFocusId = null, directoryLatest = null, directoryPreviewLoading = false) }
                }
                BoardsScreen.Board, BoardsScreen.Thread -> {
                    val b = snapshot.board ?: return@read
                    val board: Board = repo.query(user, "board", boardArgs("board_id" to b.id.boardValue(), "review" to snapshot.reviewing.boardValue())).boardDecode()
                    val isThread = snapshot.screen == BoardsScreen.Thread
                    var cursor = snapshot.cursor.takeIf { more }
                    var thread: BoardPost? = null
                    val rows = mutableListOf<BoardPost>()
                    do {
                        val previousCursor = cursor
                        val page = repo.query(user, if(isThread) "replies" else "feed", boardArgs("board_id" to b.id.boardValue(),
                            "post_id" to snapshot.thread?.id?.boardValue(), "review" to snapshot.reviewing.boardValue(), "sort" to snapshot.sort.wire.boardValue(), "period" to snapshot.period.wire.boardValue(), "cursor" to cursor)).jsonObject
                        rows += page.getValue("items").boardDecode<List<BoardPost>>()
                        thread = (page["post"] as? JsonObject)?.boardDecode()
                        cursor = page["cursor"] as? JsonObject
                        if (cursor == previousCursor) break
                    } while (cursor != null && rows.size < refreshCount)
                    suspend fun restoreReveal(post: BoardPost): BoardPost =
                        if (post.spoiler && !post.removed && post.id in snapshot.revealed)
                            repo.query(user, "post", boardArgs("post_id" to post.id.boardValue(),
                                "reveal" to true.boardValue(), "review" to snapshot.reviewing.boardValue())).boardDecode()
                        else post
                    val refreshedRows = rows.map { restoreReveal(it) }
                    val refreshedThread = thread?.let { restoreReveal(it) }
                    mutable.update {
                        val posts = (if(more) it.posts + refreshedRows else refreshedRows).distinctBy(BoardPost::id)
                        val visible = posts + listOfNotNull(refreshedThread)
                        it.copy(board = board, posts = posts, thread = refreshedThread,
                            focused = visible.firstOrNull { post -> post.id == it.focused?.id } ?: refreshedThread ?: posts.firstOrNull(),
                            pinnedPostId = it.pinnedPostId?.takeIf { id -> visible.any { post -> post.id == id } },
                            revealed = it.revealed - visible.filter { post -> post.removed }.map { post -> post.id }.toSet(), cursor = cursor)
                    }
                    loadArtwork(user, repo, listOf(board))
                }
                BoardsScreen.Manage -> {
                    val b = snapshot.board ?: return@read
                    val current: Board = repo.query(user, "board", boardArgs("board_id" to b.id.boardValue())).boardDecode()
                    mutable.update { it.copy(board = current) }
                    loadArtwork(user, repo, listOf(current))
                    val offset = snapshot.cursor?.get("management_offset")
                    val data = if(more && offset == null) boardArgs("board" to current.boardEncode()) else
                        repo.query(user, "management", boardArgs("board_id" to b.id.boardValue(), "limit" to JsonPrimitive(50), "offset" to offset.takeIf { more })).jsonObject
                    val memberCursor = snapshot.cursor?.get("members") as? JsonObject
                    val page = if(!current.canModerate || (more && memberCursor == null)) boardArgs("items" to JsonArray(emptyList())) else
                        repo.query(user, "members", boardArgs("board_id" to b.id.boardValue(), "review" to true.boardValue(), "limit" to JsonPrimitive(50), "cursor" to memberCursor.takeIf { more })).jsonObject
                    val rows: List<BoardMember> = page.getValue("items").boardDecode()
                    val inviteIds = when {
                        more && snapshot.inviteMembersLoaded -> snapshot.inviteMemberIds
                        !current.canInvite -> emptySet()
                        current.canModerate && !more && page["cursor"] !is JsonObject -> rows.mapTo(mutableSetOf()) { it.userId }
                        else -> loadInviteMemberIds(user, repo, b.id)
                    }
                    val next = boardArgs("members" to (page["cursor"] as? JsonObject), "management_offset" to data["next_offset"]?.takeUnless { it is JsonNull })
                    mutable.update { state ->
                        val merged = if(!more) data else JsonObject(data + listOf("requests", "invitations", "restrictions", "appeals", "reports").associateWith { key ->
                            JsonArray((state.management?.get(key)?.jsonArray.orEmpty() + data[key]?.jsonArray.orEmpty()).distinctBy { it.jsonObject.text("id") ?: it.jsonObject.text("user_id") })
                        })
                        state.copy(management = merged, board = current, members = (if(more) state.members + rows else rows).distinctBy(BoardMember::userId),
                            inviteMemberIds = inviteIds, inviteMembersLoaded = true, cursor = next.takeIf { it.isNotEmpty() })
                    }
                }
                BoardsScreen.Inbox -> {
                    val page = repo.query(user, "inbox", boardArgs("cursor" to snapshot.cursor.takeIf { more })).jsonObject
                    val rows: List<BoardNotice> = page.getValue("items").boardDecode()
                    mutable.update { it.copy(notices = (if(more) it.notices + rows else rows).distinctBy(BoardNotice::id), cursor = page["cursor"] as? JsonObject) }
                }
                BoardsScreen.Notices -> {
                    val data = repo.query(user, "notices").jsonObject
                    mutable.update { it.copy(moderationNotices = data) }
                }
                BoardsScreen.Stationery -> {
                    val rows: List<BoardStationery> = repo.query(user, "stationery").boardDecode()
                    mutable.update { it.copy(stationery = rows) }
                }
                BoardsScreen.Drafts -> repo.recoverCloudDrafts(user)
                else -> Unit
            }
            loadedPage = pageKey
        }
    }

    private suspend fun loadInviteMemberIds(user: String, repo: BoardRepository, boardId: String): Set<String> {
        val ids = mutableSetOf<String>()
        var cursor: JsonObject? = null
        do {
            val page = repo.query(user, "members", boardArgs("board_id" to boardId.boardValue(),
                "limit" to JsonPrimitive(100), "cursor" to cursor)).jsonObject
            page.getValue("items").jsonArray.forEach { row -> row.jsonObject.text("user_id")?.let(ids::add) }
            cursor = page["cursor"] as? JsonObject
        } while (cursor != null)
        return ids
    }

    private fun read(showLoading: Boolean = true, block: suspend (String, BoardRepository) -> Unit) {
        val user = account.value?.value ?: return
        val repo = repository ?: return
        val generation = ++readGeneration
        readJob?.cancel()
        mutable.update { it.copy(loading = showLoading, error = null) }
        readJob = scope.launch {
            try { block(user, repo) }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { fail(e) }
            finally { if(generation == readGeneration && account.value?.value == user) mutable.update { it.copy(loading = false) } }
        }
    }

    private fun previewBoard(id: String, refresh: Boolean = false) {
        val user = account.value?.value ?: return
        val repo = repository ?: return
        val current = state.value
        if(!current.enabled || current.screen != BoardsScreen.Directory || current.boards.none { it.id == id }) return
        if(!refresh && current.directoryFocusId == id) return
        previewJob?.cancel()
        mutable.update { it.copy(directoryFocusId = id,
            directoryLatest = it.directoryLatest.takeIf { post -> post?.boardId == id },
            directoryPreviewLoading = it.directoryFocusId != id || it.directoryPreviewLoading,
            directoryPreviewError = it.directoryFocusId == id && it.directoryPreviewError) }
        previewJob = scope.launch {
            fun isCurrent() = account.value?.value == user && state.value.enabled &&
                state.value.screen == BoardsScreen.Directory && state.value.directoryFocusId == id
            try {
                delay(150)
                // A normal feed read enforces audience/blocks/spoilers and does
                // not acknowledge notifications or join the community.
                val page = repo.query(user, "feed", boardArgs("board_id" to id.boardValue(),
                    "sort" to "newest".boardValue(), "limit" to JsonPrimitive(1))).jsonObject
                val latest = page["items"]?.jsonArray?.firstOrNull()?.boardDecode<BoardPost>()
                if(isCurrent()) mutable.update { it.copy(directoryLatest = latest, directoryPreviewLoading = false, directoryPreviewError = false) }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                if(isCurrent()) mutable.update { it.copy(directoryLatest = if(e is BoardFailure && e.accessDenied) null else it.directoryLatest,
                    directoryPreviewLoading = false, directoryPreviewError = true,
                    boards = if(e is BoardFailure && e.accessDenied) it.boards.filterNot { board -> board.id == id } else it.boards,
                    assets = if(e is BoardFailure && e.accessDenied) emptyMap() else it.assets) }
            }
        }
    }
    private fun fail(e: Exception) {
        mutable.update { it.copy(error = if(e is BoardFailure || e is IllegalArgumentException) e.message else "Couldn't load Boards. Please try again.",
            posts = if(e is BoardFailure && e.accessDenied) emptyList() else it.posts,
            focused = if(e is BoardFailure && e.accessDenied) null else it.focused,
            revealed = if(e is BoardFailure && e.accessDenied) emptySet() else it.revealed,
            thread = if(e is BoardFailure && e.accessDenied) null else it.thread,
            management = if(e is BoardFailure && e.accessDenied) null else it.management,
            members = if(e is BoardFailure && e.accessDenied) emptyList() else it.members,
            inviteMemberIds = if(e is BoardFailure && e.accessDenied) emptySet() else it.inviteMemberIds,
            inviteMembersLoaded = if(e is BoardFailure && e.accessDenied) false else it.inviteMembersLoaded) }
        if (e is BoardFailure && e.accessDenied) {
            loadedPage = null
            previewJob?.cancel()
            mutable.update { it.copy(assets = emptyMap(), board = null, boards = emptyList(), notices = emptyList(), invitations = emptyList(),
                pinnedPostId = null, directoryFocusId = null, directoryLatest = null, directoryPreviewLoading = false) }
        }
    }
}
