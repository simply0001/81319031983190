package com.pocketpass.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import com.pocketpass.app.ui.PlatformBackHandler
import com.pocketpass.app.ui.controller.ControllerBackHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.pocketpass.app.boards.*
import com.pocketpass.app.domain.state.accountIdOrNull
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.ProfileViewerSource
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.PocketAsset
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.components.EntranceMotion
import com.pocketpass.app.ui.components.MotionLayer
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.components.PocketKeyboard
import com.pocketpass.app.ui.components.PocketKeyboardLayout
import com.pocketpass.app.ui.components.PocketKey
import com.pocketpass.app.ui.controller.ControllerFocusViewport
import com.pocketpass.app.ui.controller.LocalControllerFocusViewport
import com.pocketpass.app.ui.controller.controllerFocusViewport
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.pocketpass.app.ui.phone.PhoneTextField
import com.pocketpass.app.ui.theme.pocketPalette
import com.pocketpass.app.ui.platformAnimationsEnabled
import kotlinx.serialization.json.*
import kotlin.time.Instant
import coil3.compose.AsyncImage

@Composable
internal fun BoardsBottom(state: PocketPassUiState, dispatch: (PocketPassEvent) -> Unit) {
    BottomPage(EntranceMotion.None) { metrics ->
        var field by remember { mutableStateOf<BoardFieldEditor?>(null) }
        var text by remember { mutableStateOf("") }
        var keyboardLayout by remember { mutableStateOf(PocketKeyboardLayout.Text) }
        LaunchedEffect(state.boards.screen, state.routes) { field = null }
        PlatformBackHandler(field != null) { field = null }
        ControllerBackHandler(field != null) { field = null }
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalBoardFieldEditor provides { selected -> field = selected; text = selected.value }, LocalBoardCompactComposer provides true, LocalBoardKeyboardOpen provides (field != null)) {
                BoardsContent(metrics, state, { event ->
                    if(event == PocketPassEvent.Boards(BoardAction.Back) && field != null) field = null else dispatch(event)
                }, Modifier.padding(top = metrics.dp(BOARDS_THOR_TOP)))
            }
            field?.let { current ->
                Box(Modifier.fillMaxSize().background(pocketPalette.surface).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {})
                Column(Modifier.fillMaxWidth().padding(top = metrics.dp(BOARDS_THOR_TOP)).background(pocketPalette.surface).padding(metrics.dp(40f)), verticalArrangement = Arrangement.spacedBy(metrics.dp(14f))) {
                    BoardLabel(metrics, current.label, 44f, true)
                    BoardLabel(metrics, text.takeLast(180).ifEmpty { "Start typing…" }, maxLines = 2)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(metrics.dp(18f))) {
                        CompositionLocalProvider(LocalBoardFocusLayer provides 30) {
                        BoardButton(metrics, if(current.onSubmit != null) "Search" else "Done", "board_keyboard_done") { current.onSubmit?.invoke(); field = null }
                        if(current.multiline) BoardButton(metrics, "New line", "board_keyboard_newline") { text += "\n"; current.change(text) }
                        }
                    }
                }
                PocketKeyboard(metrics, keyboardLayout, if(field?.onSubmit != null) "Search" else "Done", true, { key ->
                    when(key) {
                        is PocketKey.Character -> { text += key.value; current.change(text) }
                        PocketKey.Space -> { text += " "; current.change(text) }
                        PocketKey.Backspace -> { text = text.dropLast(if(text.lastOrNull()?.isLowSurrogate() == true) 2 else 1); current.change(text) }
                        PocketKey.Submit -> { field?.onSubmit?.invoke(); field = null }
                        PocketKey.Emoji -> keyboardLayout = PocketKeyboardLayout.Emoji
                        PocketKey.Alphabet -> keyboardLayout = PocketKeyboardLayout.Text
                    }
                }, focusLayer = 30, emojiKey = true, canBackspace = text.isNotEmpty(),
                    submitSound = if(field?.onSubmit != null) null else com.pocketpass.app.audio.SoundEffect.Confirm)
            }
        }
    }
}

private data class BoardFieldEditor(val value: String, val label: String, val multiline: Boolean, val change: (String)->Unit,
    val onSubmit: (() -> Unit)? = null)
private val LocalBoardFieldEditor = staticCompositionLocalOf<((BoardFieldEditor)->Unit)?> { null }
internal val LocalBoardCompactComposer = staticCompositionLocalOf { false }

@Composable
fun BoardsContent(metrics: DesignMetrics, state: PocketPassUiState, dispatch: (PocketPassEvent) -> Unit, modifier: Modifier = Modifier) {
    val s = state.boards
    val send: (BoardAction) -> Unit = { dispatch(PocketPassEvent.Boards(it)) }
    var options by remember(s.screen) { mutableStateOf(false) }
    val focus = LocalControllerFocus.current
    val inlineBack = remember(s.screen, s.board?.id, s.thread?.id, options) { BoardInlineBackStack() }
    val back: () -> Unit = { if(!inlineBack.back()) {
        if(options) {
            options = false
            focus?.focus("boards_options", reveal = false)
        } else send(BoardAction.Back)
    } }
    LaunchedEffect(state.routes) { options = false }
    val handleInlineBack = !LocalBoardKeyboardOpen.current && (options || inlineBack.canGoBack)
    PlatformBackHandler(handleInlineBack, back)
    ControllerBackHandler(handleInlineBack, back)
    val hub = s.screen in listOf(BoardsScreen.Chooser, BoardsScreen.Directory, BoardsScreen.Chats, BoardsScreen.Inbox)
    val rail = LocalBoardNavigationRail.current
    CompositionLocalProvider(LocalBoardInlineBack provides inlineBack) {
    Column(modifier.fillMaxSize().padding(top = metrics.dp(24f))) {
        BoardHeader(metrics, if(options) "Board options" else when(s.screen) {
            BoardsScreen.Chooser -> "Boards"
            BoardsScreen.Directory -> "Boards"
            BoardsScreen.Board, BoardsScreen.Thread -> s.board?.name ?: "Board"
            BoardsScreen.Compose -> if(s.draft?.content?.brandingKind != null) "Board artwork" else if(s.draft?.content?.threadId == null) "New note" else "Reply"
            BoardsScreen.Drafts -> "Your drafts"
            BoardsScreen.Propose -> "Request a board"
            BoardsScreen.Manage -> "Board settings"
            BoardsScreen.Inbox -> "Boards"
            BoardsScreen.Notices -> "Moderation notices"
            BoardsScreen.Stationery -> "Stationery"
            BoardsScreen.Chats -> "Boards"
        }, back = when {
            hub && !options && rail && !inlineBack.canGoBack -> null
            hub && !options && !inlineBack.canGoBack -> ({ dispatch(PocketPassEvent.SelectDestination(PocketPassDestination.Home)) })
            else -> back
        }, options = if(hub && !options) ({
            options = true
            focus?.focus("boards_drafts", reveal = false)
        }) else null, exit = hub && !options && !inlineBack.canGoBack)
        if(!rail && (hub || s.screen in listOf(BoardsScreen.Board, BoardsScreen.Thread)) && !options) {
            Spacer(Modifier.height(metrics.dp(22f)))
            BoardHubNavigation(metrics, state, send)
        }
        key(s.screen, s.board?.id, s.thread?.id, options) {
        val viewport = remember { ControllerFocusViewport() }
        val entrance = when {
            options -> EntranceMotion.PanelFromRight
            s.screen == BoardsScreen.Chats -> EntranceMotion.None
            s.screen == BoardsScreen.Inbox -> EntranceMotion.BoardActivityFade
            s.screen in listOf(BoardsScreen.Directory, BoardsScreen.Chooser, BoardsScreen.Board, BoardsScreen.Thread) -> EntranceMotion.BoardOpen
            else -> EntranceMotion.PanelFromRight
        }
        MotionLayer(Modifier.weight(1f).fillMaxWidth().testTag("boards_page"), entrance = entrance) {
        CompositionLocalProvider(LocalControllerFocusViewport provides viewport) {
        Column(Modifier.fillMaxSize().clipToBounds().controllerFocusViewport(viewport).verticalScroll(rememberScrollState())
            .testTag("boards_scroll").padding(horizontal = metrics.dp(50f))
            .padding(top = metrics.dp(38f), bottom = metrics.dp(16f)),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(24f))) {
        if(options) {
            BoardDirectoryOptions(metrics, s, send)
        } else if(s.screen == BoardsScreen.Chats) {
            BoardConversations(metrics, state, dispatch)
        } else if(!s.enabled && s.screen != BoardsScreen.Drafts) {
            BoardLabel(metrics, "Boards are temporarily unavailable. Your saved drafts are safe.")
            BoardButton(metrics, "Your drafts", "boards_offline_drafts") { send(BoardAction.Drafts) }
            BoardButton(metrics, "Retry", "boards_retry") { send(BoardAction.Refresh) }
        } else {
            when(s.screen) {
                BoardsScreen.Directory, BoardsScreen.Chooser -> BoardDirectory(metrics, s, send)
                BoardsScreen.Board, BoardsScreen.Thread -> BoardFeed(metrics, state, send) { userId ->
                    dispatch(PocketPassEvent.OpenUserProfile(userId, ProfileViewerSource.Board))
                }
                BoardsScreen.Compose -> BoardComposer(metrics, s, send)
                BoardsScreen.Drafts -> {
                    if(s.drafts.isEmpty()) BoardCard(metrics) { BoardLabel(metrics, "No drafts yet", 48f, true); BoardLabel(metrics, "Your unfinished notes will appear here.") }
                    s.drafts.forEach { draft -> BoardCard(metrics) {
                        draft.content.drawing?.let { drawing -> BoardPaper(metrics, Modifier.fillMaxWidth()) {
                            BoardDrawingCanvas(drawing, Modifier.fillMaxWidth().aspectRatio(4f/3f))
                        } }
                        BoardLabel(metrics, if(draft.recovered) "Recovered copy" else if(draft.content.threadId != null) "Reply draft" else "Note draft", 48f, true)
                        BoardLabel(metrics, draft.content.body.ifBlank { "Drawn note" }.take(120))
                        if(draft.pendingPublishId != null) BoardLabel(metrics, "Publishing wasn't confirmed. Open this draft to retry safely.")
                        BoardButton(metrics, "Continue", "draft_${draft.id}") { send(BoardAction.ResumeDraft(draft)) }
                        var discard by remember(draft.id) { mutableStateOf(false) }
                        if(draft.pendingPublishId == null) BoardButton(metrics, "Discard", "discard_${draft.id}") { discard = true }
                        if(discard) {
                            BoardLabel(metrics, "Discard this draft? Recovered copies stay available.")
                            BoardButton(metrics, "Confirm discard", "discard_confirm_${draft.id}") { send(BoardAction.DiscardDraft(draft)) }
                            BoardButton(metrics, "Keep draft", "discard_cancel_${draft.id}") { discard = false }
                        }
                    } }
                }
                BoardsScreen.Propose -> BoardProposal(metrics, s, send)
                BoardsScreen.Manage -> BoardManagement(metrics, state, send) { userId ->
                    dispatch(PocketPassEvent.OpenUserProfile(userId, ProfileViewerSource.Board))
                }
                BoardsScreen.Inbox -> {
                    if(s.notices.isEmpty()) BoardCard(metrics) { BoardLabel(metrics, "You’re all caught up", 48f, true); BoardLabel(metrics, "New activity from your boards will appear here.") }
                    s.notices.forEach { notice -> BoardCard(metrics) {
                        BoardLabel(metrics, notice.boardName, 48f, true)
                        BoardLabel(metrics, notice.summary(), 36f, maxLines = 2)
                        if (notice.kind != "report") {
                            val latest = notice.latestActorName?.takeIf(String::isNotBlank)?.let { "Latest from $it · " }.orEmpty()
                            BoardLabel(metrics, "$latest${notice.eventCount} ${if(notice.eventCount == 1) "update" else "updates"}", 30f,
                                color = pocketPalette.textSecondary)
                        }
                        BoardButton(metrics, "Open", "notice_${notice.id}") {
                            send(BoardAction.OpenNotice(notice))
                        }
                    } }
                    if(s.cursor != null) BoardButton(metrics, "More", "boards_more") { send(BoardAction.More) }
                }
                BoardsScreen.Notices -> BoardModerationNotices(metrics, s, send)
                BoardsScreen.Stationery -> s.stationery.forEach { paper -> BoardCard(metrics) {
                    BoardLabel(metrics, paper.name, 48f, true)
                    BoardLabel(metrics, when { paper.owned -> "Yours to keep"; paper.available -> "Available"; paper.access == "tokens" -> "${paper.price} tokens"; else -> "Unlock the ${paper.achievementKey} achievement" })
                    if(paper.available && s.draft != null) BoardButton(metrics, "Use this paper", "paper_${paper.id}", true) { send(BoardAction.SelectStationery(paper.id)) }
                    if(paper.access == "tokens" && !paper.owned) BoardButton(metrics, "Buy permanently · ${paper.price} tokens", "buy_paper_${paper.id}", enabled = !s.busy) {
                        send(BoardAction.Mutate("buy_stationery", boardArgs("stationery_id" to paper.id.boardValue())))
                    }
                } }
                else -> Unit
            }
        }
        if(s.loading && s.screen !in listOf(BoardsScreen.Directory, BoardsScreen.Chooser)) BoardLabel(metrics, "Loading…")
        s.info?.let { BoardLabel(metrics, it) }
        s.error?.let {
            BoardCard(metrics) {
                BoardLabel(metrics, it)
                BoardButton(metrics, if(s.screen == BoardsScreen.Compose) "Retry publishing" else "Retry", "boards_error_retry", enabled = !s.busy) {
                    send(if(s.screen == BoardsScreen.Compose) BoardAction.Publish else BoardAction.Refresh)
                }
            }
        }
        Spacer(Modifier.height(metrics.dp(60f)))
        }
        }
        }
        }
    }
    }
}

internal fun BoardNotice.summary(): String {
    if (kind == "report") return "A report needs review"
    val target = when (subjectType) {
        "text" -> subject?.takeIf(String::isNotBlank)?.let { "“$it”" }
        "drawing" -> "a drawing"
        "spoiler" -> "a spoiler note"
        "removed" -> "a removed note"
        "note" -> "a note"
        else -> null
    } ?: return "Activity in this board"
    val author = threadAuthorName?.takeIf(String::isNotBlank)
    return if (author == null) "Activity on $target" else "Activity on $author’s note: $target"
}

@Composable
private fun BoardDirectory(m: DesignMetrics, s: BoardsUiState, send: (BoardAction) -> Unit) {
    val focus = LocalControllerFocus.current
    LaunchedEffect(focus?.focusId, s.boards.map { it.id }) {
        val highlighted = s.boards.firstOrNull { "board_${it.id}" == focus?.focusId }
            ?: s.boards.firstOrNull { it.id == s.directoryFocusId } ?: s.boards.firstOrNull()
        highlighted?.let { send(BoardAction.PreviewBoard(it.id)) }
    }
    val belowTabs = if(s.explore) "boards_search_field" else s.boards.firstOrNull()?.let { "board_${it.id}" } ?: "boards_code_section"
    BoardTabs(m, listOf(
        BoardTab("Joined", "boards_joined", !s.explore, neighbors = mapOf(FocusDirection.Up to "boards_directory", FocusDirection.Down to belowTabs, FocusDirection.Right to "boards_explore")) { send(BoardAction.Directory()) },
        BoardTab("Explore", "boards_explore", s.explore, neighbors = mapOf(FocusDirection.Up to "boards_directory", FocusDirection.Down to belowTabs, FocusDirection.Left to "boards_joined")) { send(BoardAction.Directory(true)) },
    ), confirmSound = true)
    val motion = platformAnimationsEnabled()
    MotionLayer(Modifier.fillMaxWidth().animateContentSize(tween(if(motion) 320 else 0, easing = FastOutSlowInEasing)),
        entrance = EntranceMotion.BoardOpen, replayKey = s.explore, transformOrigin = TransformOrigin(.5f, 0f)) {
    Column(Modifier.fillMaxWidth().padding(m.dp(8f)), verticalArrangement = Arrangement.spacedBy(m.dp(24f))) {
    if(s.explore) {
        var search by remember { mutableStateOf(s.search) }
        val keyboard = LocalSoftwareKeyboardController.current
        val searchAction = boardConfirmAction({ keyboard?.hide(); send(BoardAction.Directory(true, search)) })
        val belowSearch = s.boards.firstOrNull()?.let { "board_${it.id}" } ?: "boards_code_section"
        BoardField(m, search, { search = it.take(60) }, "Search boards", tag = "boards_search_field",
            neighbors = mapOf(FocusDirection.Up to "boards_explore", FocusDirection.Right to "boards_search", FocusDirection.Down to belowSearch),
            onSubmit = searchAction, trailingContent = {
                Box(Modifier.heightIn(min = m.dp(80f)).testTag("boards_search")
                    .pocketFrame(greenButtonBrush(), m.dp(3f), ThemeChoiceGreen, RoundedCornerShape(m.dp(28f)))
                    .boardControllerTarget("boards_search", neighbors = mapOf(FocusDirection.Left to "boards_search_field",
                        FocusDirection.Up to "boards_explore", FocusDirection.Down to belowSearch), onActivate = searchAction)
                    .clickable(onClick = searchAction).padding(horizontal = m.dp(24f), vertical = m.dp(14f)), contentAlignment = Alignment.Center) {
                    BoardLabel(m, "Search", 34f, true, maxLines = 1, color = Color.White)
                }
            })
    }
    s.invitations.forEach { invitation -> BoardCard(m) {
        BoardLabel(m, "Invitation to ${invitation.boardName}", 48f, true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(m.dp(24f)), verticalArrangement = Arrangement.spacedBy(m.dp(20f))) {
            BoardButton(m, "Accept", "invite_accept_${invitation.id}", true, !s.busy) { send(BoardAction.Mutate("accept_invitation", boardArgs("id" to invitation.id.boardValue()))) }
            BoardButton(m, "Decline", "invite_decline_${invitation.id}", enabled = !s.busy) { send(BoardAction.Mutate("revoke_invitation", boardArgs("id" to invitation.id.boardValue(), "board_id" to invitation.boardId.boardValue()))) }
        }
    } }
    val loadingResults = s.loading && s.boards.isEmpty()
    MotionLayer(Modifier.fillMaxWidth(), entrance = EntranceMotion.BoardActivityFade,
        replayKey = s.explore to loadingResults) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(m.dp(24f))) {
    if(loadingResults) BoardCard(m, Modifier.heightIn(min = m.dp(220f))) {
        BoardLabel(m, "Loading boards…", 40f, true)
    }
    if(s.boards.isEmpty() && !s.loading && s.error == null) BoardCard(m) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(24f))) {
            FigmaAsset(Assets.SettingsSocial, Modifier.size(m.dp(94f)), contentScale = ContentScale.Fit)
            BoardLabel(m, if(s.explore) "No boards found" else "Find your first board", 44f, true)
        }
        BoardLabel(m, if(s.explore) "Try another search, or request a board of your own." else "Find a community in Explore, or join with an invitation code.", color = pocketPalette.textSecondary)
        if(!s.explore) BoardButton(m, "Explore boards", "boards_empty_explore", true, confirmSound = true) { send(BoardAction.Directory(true)) }
        if(s.explore && s.requestsOpen) BoardButton(m, "Request a board", "boards_propose", true) { send(BoardAction.Propose) }
    }
    s.boards.forEach { board ->
        val open = { send(BoardAction.OpenBoard(board.id)) }
        BoardCard(m, Modifier.testTag("board_${board.id}").boardControllerTarget("board_${board.id}",
            neighbors = if(board.id == s.boards.firstOrNull()?.id) mapOf(FocusDirection.Up to if(s.explore) "boards_search" else "boards_joined") else emptyMap(),
            onActivate = open).clickable(onClick = open)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(24f))) {
                BoardBranding(m, board, s.assets)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(m.dp(8f))) {
                    BoardLabel(m, board.name, 44f, true, maxLines = 2)
                    BoardLabel(m, "${board.memberCount} ${if(board.memberCount == 1) "member" else "members"}${if(board.visibility == "private") " · Private" else ""}${if(board.archived) " · Archived" else ""}", 32f, color = pocketPalette.textSecondary)
                }
                FigmaAsset(Assets.SettingsArrow, Modifier.width(m.dp(25f)).height(m.dp(43f)), contentScale = ContentScale.Fit)
            }
            if(board.description.isNotBlank()) BoardLabel(m, board.description, 36f, maxLines = 2)
        }
    }
    if(s.cursor != null) BoardButton(m, "More boards", "boards_more") { send(BoardAction.More) }
    }
    }
    BoardDisclosure(m, "Join with a code", "boards_code_section") {
        var code by remember { mutableStateOf("") }
        BoardField(m, code, { code = it.take(32) }, "Invitation code")
        BoardButton(m, "Join board", "boards_code", true, code.isNotBlank() && !s.busy) { send(BoardAction.Mutate("join_code", boardArgs("code" to code.boardValue()))) }
    }
    if(s.explore && s.boards.isNotEmpty() && s.requestsOpen) BoardButton(m, "Request a board", "boards_propose") { send(BoardAction.Propose) }
    }
    }
}

@Composable
private fun BoardDirectoryOptions(m: DesignMetrics, s: BoardsUiState, send: (BoardAction) -> Unit) {
    BoardCard(m) {
        BoardNavigationRow(m, "Your drafts", "Unfinished notes and drawings", "boards_drafts") { send(BoardAction.Drafts) }
        BoardDivider(m)
        BoardNavigationRow(m, "Activity", "Updates from your boards", "boards_inbox") { send(BoardAction.Inbox) }
        BoardDivider(m)
        BoardNavigationRow(m, "Moderation notices", "Decisions and appeals", "boards_notices") { send(BoardAction.Notices) }
        if(s.requestsOpen) {
            BoardDivider(m)
            BoardNavigationRow(m, "Request a board", "Start your own community", "boards_propose") { send(BoardAction.Propose) }
        }
    }
    BoardCard(m) {
        BoardToggle(m, "Push notifications", "Updates from boards you have joined", s.pushEnabled, "boards_global_push", !s.busy) {
            send(BoardAction.Mutate("push_preference", boardArgs("enabled" to (!s.pushEnabled).boardValue())))
        }
    }
    s.proposals.forEach { element ->
        val proposal = element.jsonObject
        BoardCard(m) {
            BoardLabel(m, proposal.text("name").orEmpty(), 44f, true)
            BoardLabel(m, "Board request · ${proposal.text("status")}")
            proposal.text("reason")?.takeIf { it.isNotBlank() }?.let { BoardLabel(m, it) }
            proposal.text("board_id")?.let { id -> BoardButton(m, "Open board", "proposal_board_$id") { send(BoardAction.OpenBoard(id)) } }
        }
    }
}

@Composable
private fun BoardFeed(m: DesignMetrics, state: PocketPassUiState, send: (BoardAction) -> Unit,
    openProfile: (String) -> Unit) {
    val s = state.boards
    val b = s.board ?: return
    if(s.screen == BoardsScreen.Board) {
        val compact = LocalBoardCompactComposer.current
        val controllerFocus = LocalControllerFocus.current
        var focusWeekRequested by remember(b.id) { mutableStateOf(false) }
        var periodsOpen by remember(b.id, s.sort) { mutableStateOf(s.sort == BoardSort.Popular) }
        var showRules by remember(b.id) { mutableStateOf(false) }
        BoardInlineBackHandler(showRules) { showRules = false }
        BoardInlineBackHandler(periodsOpen && s.sort == BoardSort.Popular) {
            focusWeekRequested = false
            periodsOpen = false
            controllerFocus?.focus("board_sort_popular")
        }
        Column(Modifier.fillMaxWidth()) {
        BoardReveal(m, !compact || showRules, Modifier.testTag("board_about_reveal")) {
        BoardCard(m) {
            BoardBranding(m, b, s.assets, true)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(24f))) {
                BoardBranding(m, b, s.assets)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(m.dp(10f))) {
                    if(b.description.isNotBlank()) BoardLabel(m, b.description, 36f, maxLines = 3)
                    BoardLabel(m, "${b.memberCount} members · ${if(b.visibility == "private") "Private" else "Public"}", 30f, color = pocketPalette.textSecondary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(m.dp(20f))) {
                BoardButton(m, "Rules", "board_rules", showRules, confirmSound = true) { showRules = !showRules }
                if(b.role != null) BoardButton(m, "Settings", "board_manage", confirmSound = true) { send(BoardAction.Manage) }
                else if(!b.archived) BoardButton(m, if(b.joinRequested) "Join requested" else "Join board", "board_join", true, !b.joinRequested && !s.busy) { send(BoardAction.Mutate("join", boardArgs("board_id" to b.id.boardValue()))) }
            }
            if(compact) {
                BoardDivider(m)
                BoardLabel(m, b.rules.ifBlank { "Follow the PocketPass community rules." }, 36f)
            } else BoardReveal(m, showRules) { BoardDivider(m); BoardLabel(m, b.rules.ifBlank { "Follow the PocketPass community rules." }, 36f) }
        }
        Spacer(Modifier.height(m.dp(16f)))
        }
        if(b.archived) BoardLabel(m, "This board is archived. Its notes stay readable.")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(20f))) {
            if(compact) {
                BoardAboutButton(m, showRules, Modifier.weight(1f)) { showRules = !showRules }
            } else BoardLabel(m, "Community notes", 44f, true, Modifier.weight(1f))
            if(b.canPost) BoardButton(m, "Write a note", "board_compose", true, icon = Assets.MessageActionAdd, confirmSound = true) { send(BoardAction.Compose()) }
            else if(compact && !b.archived && b.role == null) BoardButton(m, if(b.joinRequested) "Join requested" else "Join board", "board_join_compact", true, !b.joinRequested && !s.busy) { send(BoardAction.Mutate("join", boardArgs("board_id" to b.id.boardValue()))) }
        }
        }
        Column(Modifier.fillMaxWidth()) {
            BoardTabs(m, BoardSort.entries.map { sort ->
                BoardTab(if(sort == BoardSort.Activity) "Recent activity" else sort.label, "board_sort_${sort.wire}", s.sort == sort,
                    neighbors = buildMap {
                        if(compact) put(FocusDirection.Up, "board_about")
                        if(sort == BoardSort.Popular && s.sort == BoardSort.Popular && periodsOpen)
                            put(FocusDirection.Down, "board_period_week")
                    }) {
                    focusWeekRequested = sort == BoardSort.Popular
                    periodsOpen = sort == BoardSort.Popular
                    send(BoardAction.Sort(sort, s.period))
                }
            }, Modifier.testTag("board_filter"), confirmSound = true)
            BoardReveal(m, s.sort == BoardSort.Popular && periodsOpen, topGap = 16f) {
                LaunchedEffect(focusWeekRequested, s.sort, periodsOpen) {
                    if(focusWeekRequested && s.sort == BoardSort.Popular && periodsOpen) {
                        withFrameNanos { }
                        controllerFocus?.focus("board_period_week")
                        focusWeekRequested = false
                    }
                }
                BoardTabs(m, BoardPeriod.entries.map { period ->
                    BoardTab(period.label, "board_period_${period.wire}", s.period == period,
                        neighbors = boardPeriodNeighbors(period)) { send(BoardAction.Sort(s.sort, period)) }
                }, confirmSound = true)
            }
        }
    }
    CompositionLocalProvider(LocalBoardFocusOrder provides s.posts.map(BoardPost::id)) {
    s.thread?.let { thread -> key(thread.id) {
        BoardPostCard(m, thread, state, send, true, openProfile,
            neighbors = s.posts.firstOrNull()?.let { post -> mapOf(FocusDirection.Down to "preview_${post.id}") } ?: emptyMap())
    } }
    if(s.reviewing) BoardLabel(m, "Moderation review · Includes content hidden by blocks.")
    if(s.posts.isEmpty() && !s.loading) BoardCard(m) { BoardLabel(m, if(s.screen == BoardsScreen.Thread) "No replies yet" else "No notes yet", 48f, true); BoardLabel(m, if(b.canPost) "Share a thought or draw something." else "Notes from this community will appear here.") }
    s.posts.forEachIndexed { index, post ->
        key(post.id) {
        BoardPostCard(m, post, state, send, s.screen == BoardsScreen.Thread, openProfile, neighbors = buildMap {
            val previousPost = s.posts.getOrNull(index - 1) ?: s.thread
            if(previousPost != null) put(FocusDirection.Up, "preview_${previousPost.id}")
            else if(s.screen == BoardsScreen.Board) put(FocusDirection.Up, "board_sort_${s.sort.wire}")
            s.posts.getOrNull(index + 1)?.let { put(FocusDirection.Down, "preview_${it.id}") }
        })
        }
    }
    if(s.cursor != null) BoardButton(m, "More notes", "boards_more") { send(BoardAction.More) }
    if(s.screen == BoardsScreen.Thread && b.canPost) BoardButton(m, "Reply", "board_reply", true, confirmSound = true) { send(BoardAction.Compose(s.thread?.id)) }
    }
}

internal fun boardPeriodNeighbors(period: BoardPeriod): Map<FocusDirection, String> {
    val periods = BoardPeriod.entries
    val index = periods.indexOf(period)
    return mapOf(
        FocusDirection.Left to "board_period_${periods[(index - 1).coerceAtLeast(0)].wire}",
        FocusDirection.Right to "board_period_${periods[(index + 1).coerceAtMost(periods.lastIndex)].wire}",
        FocusDirection.Up to "board_sort_${when(period) {
            BoardPeriod.Today -> BoardSort.Newest
            BoardPeriod.Week -> BoardSort.Activity
            BoardPeriod.All -> BoardSort.Popular
        }.wire}",
    )
}

@Composable
private fun BoardReplyReference(m: DesignMetrics, original: BoardPost?, revealed: Boolean) {
    val hidden = original?.spoiler == true && !revealed
    val excerpt = remember(original?.body, original?.removed, hidden) {
        when {
            original == null -> ""
            original.removed -> "This note was removed."
            hidden -> "Spoiler · Open the original note to reveal"
            original.body.isNotBlank() -> original.body.replace(Regex("\\s+"), " ").trim().let {
                if(it.length > 160) it.take(160) + "…" else it
            }
            original.hasDrawing || original.drawing != null -> "Drawn note"
            else -> "Note"
        }
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)
        .background(pocketPalette.surfaceLow, RoundedCornerShape(m.dp(16f))).padding(m.dp(18f)),
        horizontalArrangement = Arrangement.spacedBy(m.dp(18f)), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(m.dp(5f)).fillMaxHeight().background(pocketPalette.teal, RoundedCornerShape(m.dp(3f))))
        if(original != null && !original.removed && !hidden) original.drawing?.let { drawing ->
            BoardDrawingCanvas(drawing, Modifier.width(m.dp(112f)).aspectRatio(4f/3f), paper = original.stationery?.artwork)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(m.dp(8f))) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(m.dp(16f))) {
                BoardLabel(m, if(original == null) "Reply to an unavailable note"
                    else "Reply to ${original.authorName.ifBlank { "PocketPass member" }}", 30f, true,
                    modifier = Modifier.weight(1f), maxLines = 1, color = pocketPalette.teal)
                val time = remember(original?.createdAt) {
                    original?.createdAt?.let { runCatching { relativeTime(Instant.parse(it)) }.getOrNull() }
                }
                if(time != null) BoardLabel(m, time, 26f, color = pocketPalette.textSecondary, maxLines = 1)
            }
            if(excerpt.isNotEmpty()) BoardLabel(m, excerpt, 30f, maxLines = 2, color = pocketPalette.textSecondary)
        }
    }
}

@Composable
private fun BoardPostCard(m: DesignMetrics, post: BoardPost, state: PocketPassUiState, send: (BoardAction) -> Unit, inThread: Boolean,
    openProfile: (String) -> Unit,
    neighbors: Map<FocusDirection, String> = emptyMap()) {
    val s = state.boards
    val owner = post.authorId == state.sessionState.accountIdOrNull()?.value
    val hidden = post.spoiler && post.id !in s.revealed
    var actions by remember(post.id) { mutableStateOf(false) }
    var form by remember(post.id) { mutableStateOf<String?>(null) }
    BoardInlineBackHandler(actions || form != null) { if(form != null) form = null else actions = false }
    var text by remember(post.id, form) { mutableStateOf(if(form == "edit") post.body else "") }
    val focus = LocalControllerFocus.current
    LaunchedEffect(focus?.focusId) { if(focus?.focusId?.endsWith("_${post.id}") == true) send(BoardAction.Focus(post)) }
    BoardCard(m, Modifier.testTag("board_post_${post.id}")
        .boardControllerTarget("preview_${post.id}", neighbors = neighbors) { send(BoardAction.Focus(post)); focus?.enterChildren("preview_${post.id}") }
        .clickable { send(BoardAction.Focus(post)) }) {
        CompositionLocalProvider(LocalBoardFocusParent provides "preview_${post.id}") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(22f))) {
            val authorId = post.authorId?.takeUnless { post.removed }
            val authorAction = authorId?.let { id -> { openProfile(id) } }
            Row(Modifier.weight(1f).then(if(authorAction != null)
                Modifier.testTag("board_author_${post.id}")
                    .boardControllerTarget("author_${post.id}", onActivate = authorAction)
                    .clickable(onClick = authorAction)
                else Modifier), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(m.dp(22f))) {
                Box(Modifier.size(m.dp(96f)).clip(RoundedCornerShape(m.dp(20f))).background(pocketPalette.surfaceLow)
                    .border(m.dp(3f), pocketPalette.borderSoft, RoundedCornerShape(m.dp(20f))), contentAlignment = Alignment.Center) {
                    BoardLabel(m, post.authorName.take(1).uppercase().ifBlank { "?" }, 46f, true, color = pocketPalette.teal)
                    if(post.authorAvatar?.startsWith("https://") == true) AsyncImage(post.authorAvatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(m.dp(6f))) {
                    BoardLabel(m, post.authorName.ifBlank { "PocketPass member" }, 40f, true, maxLines = 1)
                    val time = remember(post.createdAt) { runCatching { relativeTime(Instant.parse(post.createdAt)) }.getOrNull() }
                    if(time != null) BoardLabel(m, time + if(post.editedAt != null) " · Edited" else "", 28f, color = pocketPalette.textSecondary)
                }
            }
            if(inThread && (LocalBoardCompactComposer.current || LocalBoardNavigationRail.current)) {
                BoardButton(m, if(s.pinnedPostId == post.id) "Unpin" else "Pin on top", "pin_${post.id}",
                    selected = s.pinnedPostId == post.id, compact = true, confirmSound = true) { send(BoardAction.TogglePin(post.id)) }
            }
            BoardMoreAction(m, "actions_${post.id}", actions) { actions = !actions }
        }
        post.replyTo?.let { replyId ->
            val original = s.thread?.takeIf { it.id == replyId } ?: s.posts.firstOrNull { it.id == replyId }
            BoardReplyReference(m, original, original?.id in s.revealed)
        }
        when {
            post.removed -> BoardLabel(m, "This note was removed.")
            hidden -> BoardButton(m, "Spoiler · Tap to reveal", "spoiler_${post.id}", confirmSound = true) { send(BoardAction.Reveal(post)) }
            else -> {
                post.drawing?.let { drawing -> BoardPaper(m, Modifier.fillMaxWidth()) {
                    BoardDrawingCanvas(drawing, modifier = Modifier.fillMaxWidth().aspectRatio(4f/3f), paper = post.stationery?.artwork)
                } }
                if(post.body.isNotBlank()) BoardLabel(m, post.body, 38f)
                if(post.editedAt != null && post.createdAt.isBlank()) BoardLabel(m, "Edited", 28f)
            }
        }
        BoardDivider(m)
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().heightIn(min = m.dp(114f)), verticalAlignment = Alignment.CenterVertically) {
                if(!post.removed) BoardButton(m, "Yeah · ${post.yeahCount}", "yeah_${post.id}", post.yeah,
                    s.board?.canPost == true && !s.busy, modifier = Modifier.weight(1f), compact = true, confirmSound = true) {
                    send(BoardAction.Mutate("react", boardArgs("board_id" to post.boardId.boardValue(), "post_id" to post.id.boardValue(), "yeah" to (!post.yeah).boardValue())))
                }
                if(!inThread || s.board?.canPost == true) {
                    if(!post.removed) Spacer(Modifier.width(m.dp(20f)))
                    if(!inThread) BoardButton(m, "Replies · ${post.replyCount}", "thread_${post.id}", modifier = Modifier.weight(1f), compact = true, confirmSound = true) { send(BoardAction.OpenThread(post)) }
                    else BoardButton(m, "Reply", "reply_${post.id}", modifier = Modifier.weight(1f), compact = true, confirmSound = true) { send(BoardAction.Compose(post.id)) }
                }
                BoardInlineReveal(m, actions && (owner || s.board?.canModerate == true)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(m.dp(20f)), verticalAlignment = Alignment.CenterVertically) {
                    BoardButton(m, "${if(post.spoiler) "Unmark" else "Mark"} spoiler", "mark_spoiler_${post.id}", enabled = !s.busy, compact = true, confirmSound = true) {
                        if(owner) send(BoardAction.Mutate("spoiler", boardArgs("board_id" to post.boardId.boardValue(), "post_id" to post.id.boardValue(), "spoiler" to (!post.spoiler).boardValue())))
                        else form = "spoiler"
                    }
                    if(!post.removed) BoardButton(m, "Remove note", "delete_${post.id}", compact = true, confirmSound = true) { form = "delete_post" }
                    }
                }
            }
            BoardReveal(m, actions && !post.removed && (!owner || !hidden), topGap = 8f) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(m.dp(20f)), verticalArrangement = Arrangement.spacedBy(m.dp(20f))) {
                    if(owner && !hidden) BoardButton(m, "Edit text", "edit_${post.id}", compact = true, confirmSound = true) { form = "edit" }
                    if(!owner) BoardButton(m, "Report", "report_${post.id}", compact = true, confirmSound = true) { form = "report" }
                }
            }
        }
        form?.let { operation ->
            BoardLabel(m, when(operation) { "edit" -> "Edit the text. The drawing will stay as published."; "delete_post" -> "Remove this note? Replies will stay."; else -> "What should moderators know?" })
            if(operation != "delete_post" || !owner) BoardField(m, text, { text = it.take(if(operation == "edit" && post.threadId != null) 500 else 1000) }, if(operation == "edit") "Note text" else "Reason", true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(m.dp(24f)), verticalArrangement = Arrangement.spacedBy(m.dp(20f))) {
                BoardButton(m, if(operation == "delete_post") "Remove" else "Send", "confirm_${post.id}", enabled = !s.busy, confirmSound = true) {
                    send(BoardAction.Mutate(operation, boardArgs("board_id" to post.boardId.boardValue(), "post_id" to post.id.boardValue(), "spoiler" to (!post.spoiler).boardValue(), (if(operation == "edit") "body" else "reason") to text.boardValue())))
                    form = null
                }
                BoardButton(m, "Cancel", "cancel_${post.id}") { form = null }
            }
        }
        }
    }
}

@Composable
private fun BoardProposal(m: DesignMetrics, s: BoardsUiState, send: (BoardAction) -> Unit) {
    var name by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }; var rules by remember { mutableStateOf("") }; var isPrivate by remember { mutableStateOf(false) }
    BoardLabel(m, "Tell us about your community. PocketPass staff will review the request.")
    BoardField(m, name, { name = it.take(60) }, "Board name")
    BoardField(m, description, { description = it.take(1000) }, "Description", true)
    BoardField(m, rules, { rules = it.take(4000) }, "Rules", true)
    BoardCard(m) {
        BoardToggle(m, "Private board", if(isPrivate) "Only invited members can see it. Private boards cannot become public." else "Signed-in PocketPass users can read it.", isPrivate, "proposal_private") { isPrivate = !isPrivate }
    }
    BoardButton(m, "Submit request", "proposal_submit", true, name.isNotBlank() && !s.busy) { send(BoardAction.Mutate("propose", boardArgs("name" to name.boardValue(), "description" to description.boardValue(), "rules" to rules.boardValue(), "visibility" to (if(isPrivate) "private" else "public").boardValue()))) }
}

@Composable
internal fun BoardCard(m: DesignMetrics, modifier: Modifier = Modifier, contentPadding: Float = 30f, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().pocketFrame(SolidColor(pocketPalette.surface), m.dp(3f), pocketPalette.borderSoft, RoundedCornerShape(m.dp(28f))).padding(m.dp(contentPadding)), verticalArrangement = Arrangement.spacedBy(m.dp(22f)), content = content)
}
@Composable
internal fun BoardLabel(m: DesignMetrics, value: String, size: Float = 40f, bold: Boolean = false, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, color: Color = pocketPalette.textPrimary) {
    Text(value, maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontFamily = Rubik, fontWeight = if(bold) FontWeight.SemiBold else FontWeight.Normal, fontSize = m.sp(size), color = color, modifier = modifier)
}
@Composable
internal fun BoardField(m: DesignMetrics, value: String, change: (String)->Unit, placeholder: String, multiline: Boolean = false, enabled: Boolean = true,
    tag: String? = null, neighbors: Map<FocusDirection, String> = emptyMap(),
    onSubmit: (() -> Unit)? = null, trailingContent: (@Composable () -> Unit)? = null) {
    val fieldTag = tag ?: remember { "board_field_${newBoardId()}" }
    val editor = LocalBoardFieldEditor.current
    if(editor != null) {
        val edit = { editor(BoardFieldEditor(value, placeholder, multiline, change, onSubmit)) }
        Row(Modifier.fillMaxWidth().heightIn(min = m.dp(if(multiline) 180f else 118f))
            .pocketFrame(SolidColor(pocketPalette.surface), m.dp(5f), pocketPalette.tealBorder, RoundedCornerShape(m.dp(45f)))
            .testTag(fieldTag).boardControllerTarget(fieldTag, radius = 45f, enabled = enabled, neighbors = neighbors, onActivate = edit)
            .clickable(enabled = enabled, onClick = edit).padding(horizontal = m.dp(30f), vertical = m.dp(if(trailingContent != null) 12f else 30f)),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(20f))) {
            BoardLabel(m, value.ifBlank { placeholder }, modifier = Modifier.weight(1f), maxLines = if(multiline) Int.MAX_VALUE else 1)
            trailingContent?.invoke()
        }
        return
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    PhoneTextField(m, value, change, Modifier.fillMaxWidth().testTag(fieldTag).boardControllerTarget(fieldTag, radius = 45f, enabled = enabled, neighbors = neighbors) { focusRequester.requestFocus(); keyboard?.show() },
        focusRequester = focusRequester, placeholder = placeholder, fontSize = 40f,
        singleLine = !multiline, maxLines = if(multiline) 8 else 1, minHeight = if(multiline) 180f else 118f,
        radius = 45f, borderWidth = 5f, horizontalPadding = 30f, verticalPadding = if(trailingContent != null) 12f else 26f, enabled = enabled,
        keyboardOptions = if(onSubmit != null) KeyboardOptions(imeAction = ImeAction.Search) else KeyboardOptions.Default,
        keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke(); keyboard?.hide() }), trailingContent = trailingContent)
}
@Composable
internal fun BoardButton(m: DesignMetrics, label: String, tag: String, selected: Boolean = false, enabled: Boolean = true, icon: PocketAsset? = null, modifier: Modifier = Modifier, compact: Boolean = false,
    neighbors: Map<FocusDirection, String> = emptyMap(), confirmSound: Boolean = false, onClick: () -> Unit) {
    val activate = boardConfirmAction(onClick, confirmSound)
    val shape = RoundedCornerShape(m.dp(28f))
    Row(modifier.heightIn(min = m.dp(98f)).testTag(tag)
        .pocketFrame(if(selected) greenButtonBrush() else greyPanelBrush(), m.dp(4f), if(selected) ThemeChoiceGreen else pocketPalette.borderSoft, shape)
        .boardControllerTarget(tag, enabled = enabled, neighbors = neighbors, onActivate = activate)
        .clickable(enabled = enabled, onClick = activate).padding(horizontal = m.dp(if(compact) 20f else 26f), vertical = m.dp(20f)),
        horizontalArrangement = Arrangement.spacedBy(m.dp(14f), Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { FigmaAsset(it, Modifier.size(m.dp(46f)), contentScale = ContentScale.Fit, description = null) }
        Text(label, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = m.sp(if(compact) 32f else 36f), color = (if(selected) Color.White else pocketPalette.textPrimary).copy(alpha = if(enabled) 1f else .45f))
    }
}
