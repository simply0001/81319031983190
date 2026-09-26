@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.pocketpass.app

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pocketpass.app.boards.*
import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.domain.model.FriendProfileStats
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.model.*
import com.pocketpass.app.ui.*
import com.pocketpass.app.ui.phone.*
import com.pocketpass.app.ui.screens.*
import com.pocketpass.app.ui.controller.*
import org.junit.Assert.*
import kotlinx.serialization.json.*
import org.junit.Rule
import org.junit.Test

class BoardsUiTest {
    @get:Rule val compose = createComposeRule()
    private val board = Board("board", FixtureData.CurrentUserId.value, "Handheld hangout", "Games, sketches, and things we made.", role = "owner")
    private var state by mutableStateOf(PocketPassUiState(profile = FixtureData.currentProfile,
        accountSetup = com.pocketpass.app.feature.AccountSetupUiState(resolved = true),
        sessionState = SessionState.Authenticated(FixtureData.CurrentUserId),
        routes = listOf(PocketPassRoute.Root(PocketPassDestination.Messages)),
        boards = BoardsUiState(enabled = true, board = board)))
    private val events = mutableListOf<BoardAction>()
    private val appEvents = mutableListOf<PocketPassEvent>()
    private val focus = ControllerFocus()
    private fun send(event: PocketPassEvent) {
        appEvents.add(event)
        if(event !is PocketPassEvent.Boards) return
        val a = event.action; events.add(a)
        when(a) {
            is BoardAction.Stroke -> {
                val history = state.boards.drawingHistory.add(a.stroke)
                state = state.copy(boards = state.boards.copy(drawingHistory = history, activeStroke = null,
                    draft = state.boards.draft!!.copy(content = state.boards.draft!!.content.copy(drawing = history.drawing))))
            }
            is BoardAction.Focus -> state = state.copy(boards = state.boards.copy(focused = a.post))
            is BoardAction.PreviewStroke -> state = state.copy(boards = state.boards.copy(activeStroke = a.stroke))
            is BoardAction.Directory -> state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Directory, explore = a.explore))
            BoardAction.OpenChats -> state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Chats))
            BoardAction.Drafts -> state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Drafts))
            BoardAction.Inbox -> state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Inbox))
            BoardAction.Notices -> state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Notices))
            BoardAction.Propose -> state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Propose))
            BoardAction.Manage -> state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Manage))
            is BoardAction.Sort -> state = state.copy(boards = state.boards.copy(sort = a.sort, period = a.period))
            is BoardAction.Compose -> state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Compose,
                draft = LocalBoardDraft(boardId = board.id, content = BoardDraftContent())))
            is BoardAction.Tool -> state = state.copy(boards = state.boards.copy(pen = a.pen ?: state.boards.pen, ink = a.color ?: state.boards.ink, penSize = a.size ?: state.boards.penSize))
            else -> Unit
        }
    }
    private fun show(phone: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalControllerFocus provides focus) {
                PocketPassTheme(state.themeMode) {
                    Box(Modifier.fillMaxSize()) {
                    if(phone) PhoneSurface { PhoneRoot(it, state, ::send, null, PocketPassExtensions.None) }
                    else Box(Modifier.aspectRatio(1240f/1080f, matchHeightConstraintsFirst = true)) {
                        BottomDisplayContent(state, ::send)
                    }
                    ControllerFocusHighlight(focus)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2000)
    }
    @Test fun chooserAndSpoilersUseTheSameControlsOnPhone() {
        show(true)
        compose.onNodeWithTag("boards_chats").performClick()
        compose.runOnIdle { assertEquals(BoardAction.OpenChats,events.last()); state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Board,
            posts = listOf(BoardPost("post",board.id,authorName="A friend",body="Secret text",spoiler=true,hasDrawing=true)))) }
        compose.onNodeWithText("Secret text").assertDoesNotExist()
        compose.onNodeWithTag("spoiler_post").performScrollTo().assertIsDisplayed()
        for(mode in ThemeMode.entries.filter { it != ThemeMode.System }) {
            compose.runOnIdle { state = state.copy(themeMode = mode) }
            capture("boards-phone-${mode.name}")
        }
    }
    @Test fun drawingTapsCreateDotsAndKeepEarlierPenStrokes() {
        val stroke = BoardStroke("pixel", "#3379D6",4f,listOf(listOf(30f,30f),listOf(200f,180f)))
        val drawing = BoardDrawing(strokes=listOf(stroke))
        state = state.copy(boards = state.boards.copy(screen=BoardsScreen.Compose,
            pen="smooth",drawingHistory=BoardDrawingHistory(drawing),draft=LocalBoardDraft(boardId=board.id,content=BoardDraftContent(drawing=drawing))))
        show(false)
        compose.onNodeWithTag("board_drawing_canvas").performScrollTo().performTouchInput { click(center) }
        val paperBounds = compose.onNodeWithTag("board_drawing_canvas").fetchSemanticsNode().boundsInRoot
        assertEquals(4f / 3f, paperBounds.width / paperBounds.height, .01f)
        compose.runOnIdle {
            assertEquals(2,state.boards.drawingHistory.drawing.strokes.size)
            assertEquals(stroke,state.boards.drawingHistory.drawing.strokes.first())
            assertEquals("smooth",state.boards.drawingHistory.drawing.strokes.last().pen)
            assertEquals(1,state.boards.drawingHistory.drawing.strokes.last().points.size)
        }
        capture("boards-dual-drawing")
    }
    @Test fun thorFieldsUsePocketKeyboard() {
        state = state.copy(boards = state.boards.copy(screen=BoardsScreen.Propose))
        show(false)
        compose.onNodeWithText("Board name").performScrollTo().performClick()
        compose.onNodeWithTag("pocket_keyboard",useUnmergedTree=true).assertIsDisplayed()
        capture("boards-dual-keyboard")
        compose.runOnIdle {
            assertTrue(state.hasDismissableLayer())
            assertTrue(com.pocketpass.app.input.handleBackGamepadKeyEvent(
                android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK), state, ::send, focus))
        }
        compose.onNodeWithTag("pocket_keyboard", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Board name").assertIsDisplayed()
    }
    @Test fun controllerFocusSelectsTheNoteForTopPreview() {
        val post = BoardPost("controller_note", board.id, authorName = "A friend", body = "A little sketch")
        state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Board, posts = listOf(post)))
        show(false)
        compose.onNodeWithTag("board_post_controller_note").performScrollTo()
        compose.runOnIdle { focus.focus("preview_controller_note"); assertTrue(focus.activate()) }
        compose.runOnIdle { assertEquals(post,state.boards.focused) }
    }
    @Test fun phonePostHighlightSurvivesFeedReordering() = postHighlightSurvivesFeedReordering(true)
    @Test fun dualScreenPostHighlightSurvivesFeedReordering() = postHighlightSurvivesFeedReordering(false)

    private fun postHighlightSurvivesFeedReordering(phone: Boolean) {
        val first = BoardPost("first", board.id, authorName = "First", body = "First note")
        val selected = BoardPost("selected", board.id, authorName = "Selected", body = "Keep reading this note")
        val newest = BoardPost("newest", board.id, authorName = "New", body = "New note")
        state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Board, posts = listOf(first, selected)))
        show(phone)
        compose.onNodeWithTag("board_post_selected").performScrollTo()
        compose.runOnIdle { focus.focus("preview_selected") }
        compose.runOnIdle { state = state.copy(boards = state.boards.copy(posts = listOf(newest, first, selected))) }
        compose.runOnIdle {
            assertEquals("preview_selected", focus.focusedTarget(null)?.id)
            assertEquals(selected.id, state.boards.focused?.id)
            focus.focus("actions_selected")
            assertTrue(focus.activate())
        }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { focus.focus("report_selected") }
        compose.onNodeWithTag("report_selected").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            state = state.copy(boards = state.boards.copy(posts = listOf(selected.copy(yeahCount = 3), newest, first)))
        }
        compose.mainClock.advanceTimeBy(31_000)
        compose.onNodeWithTag("report_selected").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("report_selected", focus.focusedTarget(null)?.id)
            assertEquals("preview_selected", focus.focusedTarget(null)?.parentId)
            assertEquals(selected.id, state.boards.focused?.id)
        }
        capture("boards-${if(phone) "phone" else "dual"}-retained-post-focus")
        compose.runOnIdle {
            assertTrue(focus.exitToParent())
            assertEquals("preview_selected", focus.focusedTarget(null)?.id)
            assertTrue(focus.move(FocusDirection.Down))
            assertEquals("preview_newest", focus.focusedTarget(null)?.id)
        }
    }

    @Test fun revealingASpoilerKeepsTheHighlightOnItsPost() {
        val post = BoardPost("spoiler", board.id, authorName = "A friend", body = "Revealed note", spoiler = true)
        state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Board, posts = listOf(post)))
        show(false)
        compose.onNodeWithTag("spoiler_spoiler").performScrollTo()
        compose.runOnIdle {
            focus.focus("spoiler_spoiler")
            assertTrue(focus.activate())
            assertEquals(BoardAction.Reveal(post), events.last())
            state = state.copy(boards = state.boards.copy(revealed = setOf(post.id)))
        }
        compose.onNodeWithText("Revealed note").assertExists()
        compose.runOnIdle { assertEquals("preview_spoiler", focus.focusedTarget(null)?.id) }
        capture("boards-revealed-spoiler-focus")
        compose.mainClock.advanceTimeBy(31_000)
        compose.runOnIdle { state = state.copy(boards = state.boards.copy(posts = listOf(post.copy(yeahCount = 1)))) }
        compose.onNodeWithTag("spoiler_spoiler").assertDoesNotExist()
        compose.onNodeWithText("Revealed note").assertExists()
        compose.runOnIdle { assertEquals("preview_spoiler", focus.focusedTarget(null)?.id) }
    }
    @Test fun topPreviewKeepsTheDrawingVisibleWithALongCaption() {
        val drawing=BoardDrawing(strokes=listOf(BoardStroke("smooth","#3379D6",12f,listOf(listOf(60f,300f),listOf(400f,100f),listOf(740f,300f)))))
        val post=BoardPost("preview",board.id,authorName="A friend",body="A caption with plenty to say. ".repeat(30),drawing=drawing,hasDrawing=true)
        state=state.copy(boards=state.boards.copy(screen=BoardsScreen.Board,focused=post))
        compose.setContent { PocketPassTheme(ThemeMode.Dark) {
            Box(Modifier.aspectRatio(16f/9f).fillMaxWidth()) { TopDisplayContent(state.copy(themeMode = ThemeMode.Dark), ::send) }
        } }
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithText("A friend").assertIsDisplayed()
        val paper = compose.onNodeWithTag("board_top_drawing").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Long captions must not shrink the top-screen paper", paper.height > root.width * .28f)
        assertTrue("Paper overlaps status chrome", paper.top > root.width * 210f / 1920f)
        capture("boards-top-preview")
    }
    @Test fun activityOpenUsesOneDestinationActionAndShowsTheNote() {
        val notice = BoardNotice("activity", board.id, board.name, "note", "activity", 2,
            subject = "Weekend drawing plans", subjectType = "text", threadAuthorName = "Ada",
            latestActorName = "Bea")
        state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Inbox, notices = listOf(notice)))
        show(false)
        compose.onNodeWithText("Activity on Ada’s note: “Weekend drawing plans”").assertIsDisplayed()
        compose.onNodeWithText("Latest from Bea · 2 updates").assertIsDisplayed()
        capture("boards-activity-detail")
        compose.onNodeWithTag("notice_activity").performClick()
        compose.runOnIdle { assertEquals(listOf(BoardAction.OpenNotice(notice)), events) }
    }
    @Test fun boardInviteListOmitsFriendsWhoAlreadyJoinedAndUsesFriendCodes() {
        val friends = FixtureData.friends.take(2)
        assertEquals(2, friends.size)
        val memberId = friends.first().profile.userId.value
        state = state.copy(friends = friends, boards = state.boards.copy(screen = BoardsScreen.Manage,
            inviteMemberIds = setOf(memberId), inviteMembersLoaded = true))
        show(false)
        compose.onNodeWithTag("board_section_invite").performScrollTo().performClick()
        compose.onNodeWithTag("board_invite_$memberId").assertDoesNotExist()
        compose.onNodeWithTag("board_invite_${friends.last().profile.userId.value}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Friend Code (8 digits)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("PocketPass account ID").assertDoesNotExist()
    }
    @Test fun memberActionsReceiveVisibleControllerFocusAfterOpening() {
        val member = BoardMember(FixtureData.friends.first().profile.userId.value, displayName = "Board friend")
        state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Manage, members = listOf(member),
            inviteMembersLoaded = true, inviteMemberIds = setOf(member.userId)))
        show(false)
        compose.onNodeWithTag("board_section_members").performScrollTo().performClick()
        compose.onNodeWithTag("manage_member_${member.userId}").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { focus.focus("manage_member_${member.userId}"); assertTrue(focus.activate()) }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals("member_profile_${member.userId}", focus.focusedTarget(FocusDisplay.Bottom)?.id) }
        compose.onNodeWithTag("member_profile_${member.userId}").assertIsDisplayed()
        compose.runOnIdle { assertTrue(focus.move(FocusDirection.Down)); assertEquals("moderator_${member.userId}", focus.focusedTarget(FocusDisplay.Bottom)?.id) }
        compose.onNodeWithTag("moderator_${member.userId}").assertIsDisplayed()
        compose.runOnIdle { assertTrue(focus.move(FocusDirection.Down)); assertEquals("transfer_${member.userId}", focus.focusedTarget(FocusDisplay.Bottom)?.id) }
        compose.onNodeWithTag("transfer_${member.userId}").assertIsDisplayed()
    }
    @Test fun boardProfileRequestIsOnTheTopScreen() {
        state = state.copy(profileViewer = ProfileViewerUiState(
            selectedUserId = FixtureData.SpobUserId.value,
            source = ProfileViewerSource.Board,
            profile = FixtureData.spobProfile,
            friendRequestState = ProfileFriendRequestState.Available,
            stats = FriendProfileStats(encounterCount = 3, trophyCount = 12),
        ))
        compose.setContent {
            CompositionLocalProvider(LocalControllerFocus provides focus, LocalFocusDisplay provides FocusDisplay.Top) {
                PocketPassTheme(state.themeMode) {
                    Box(Modifier.aspectRatio(16f / 9f).fillMaxWidth()) { TopDisplayContent(state, ::send) }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithTag("profile_trophy_count", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("profile_encounter_count", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("POCKETPASS PROFILE").assertDoesNotExist()
        compose.onNodeWithText("Connect on PocketPass").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("profile_friend_request", focus.focusId)
            assertTrue(focus.activate())
        }
        compose.onNodeWithTag("profile_friend_request").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(PocketPassEvent.SendProfileFriendRequest, appEvents.last()) }
        capture("boards-profile-top", "profile_viewer")
        compose.runOnIdle {
            state = state.copy(profileViewer = state.profileViewer.copy(profile = FixtureData.spobProfile.copy(
                displayName = "A very long PocketPass member name",
                bio = "A long Board bio that talks about drawing, games, friends, and all the things we share here. ".repeat(3),
                age = 25,
                countryCode = "SE",
            )))
        }
        val card = compose.onNodeWithTag("board_profile_card", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("board_profile_request_panel", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("The request panel must stay inside the profile card", panel.bottom <= card.bottom)
        capture("boards-profile-top-long-bio", "profile_viewer")
        compose.runOnIdle {
            state = state.copy(profileViewer = state.profileViewer.copy(
                profile = FixtureData.spobProfile.copy(blockInvites = true),
                friendRequestState = ProfileFriendRequestState.Unavailable,
            ))
        }
        compose.onNodeWithTag("profile_friend_request").assertDoesNotExist()
        compose.onNodeWithText("This person isn't accepting friend requests.").assertIsDisplayed()
    }
    @Test fun boardProfileRequestHasAPhoneCardAndPendingState() {
        state = state.copy(profileViewer = ProfileViewerUiState(
            selectedUserId = FixtureData.SpobUserId.value,
            source = ProfileViewerSource.Board,
            profile = FixtureData.spobProfile,
            friendRequestState = ProfileFriendRequestState.Available,
        ))
        show(true)
        compose.onNodeWithTag("profile_friend_request").performScrollTo().assertIsDisplayed()
        capture("boards-profile-phone")
        compose.runOnIdle {
            state = state.copy(profileViewer = state.profileViewer.copy(friendRequestState = ProfileFriendRequestState.Pending))
        }
        compose.onNodeWithTag("profile_friend_request").assertDoesNotExist()
        compose.onNodeWithText("Friend request sent").assertIsDisplayed()
    }
    @Test fun boardProfileLeavesTheLowerBoardVisible() {
        state = state.copy(profileViewer = ProfileViewerUiState(
            selectedUserId = FixtureData.SpobUserId.value,
            source = ProfileViewerSource.Board,
            profile = FixtureData.spobProfile,
            friendRequestState = ProfileFriendRequestState.Available,
            stats = FriendProfileStats(encounterCount = 3, trophyCount = 12),
        ))
        show(false)
        compose.onNodeWithTag("profile_bottom_dismiss").assertIsDisplayed()
        compose.onNodeWithTag("friend_profile_overlay").assertDoesNotExist()
        compose.onNodeWithTag("profile_trophy_count").assertDoesNotExist()
        capture("boards-profile-bottom")
        compose.onNodeWithTag("profile_bottom_dismiss").performClick()
        compose.runOnIdle { assertEquals(PocketPassEvent.CloseUserProfile, appEvents.last()) }
    }
    @Test fun boardFriendProfileActionsStayOnTop() {
        state = state.copy(profileViewer = ProfileViewerUiState(
            selectedUserId = FixtureData.SpobUserId.value,
            source = ProfileViewerSource.Board,
            profile = FixtureData.spobProfile,
            friendRequestState = ProfileFriendRequestState.Friends,
            stats = FriendProfileStats(encounterCount = 3, trophyCount = 12),
        ))
        compose.setContent {
            CompositionLocalProvider(LocalControllerFocus provides focus, LocalFocusDisplay provides FocusDisplay.Top) {
                PocketPassTheme(state.themeMode) {
                    Box(Modifier.aspectRatio(16f / 9f).fillMaxWidth()) { TopDisplayContent(state, ::send) }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithTag("profile_message").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(PocketPassEvent.MessageProfileFriend, appEvents.last()) }
        compose.onNodeWithTag("profile_remove_friend").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(PocketPassEvent.OpenRemoveFriend, appEvents.last()) }
        capture("friend-profile-top", "profile_viewer")
    }
    @Test fun friendsTabKeepsOriginalProfileViewer() {
        state = state.copy(
            routes = listOf(PocketPassRoute.Root(PocketPassDestination.Friends)),
            profileViewer = ProfileViewerUiState(
                selectedUserId = FixtureData.SpobUserId.value,
                source = ProfileViewerSource.Friend,
                profile = FixtureData.spobProfile,
                friendRequestState = ProfileFriendRequestState.Friends,
                stats = FriendProfileStats(encounterCount = 3, trophyCount = 12),
            ),
        )
        compose.setContent {
            CompositionLocalProvider(LocalControllerFocus provides focus, LocalFocusDisplay provides FocusDisplay.Top) {
                PocketPassTheme(state.themeMode) {
                    Box(Modifier.aspectRatio(16f / 9f).fillMaxWidth()) { TopDisplayContent(state, ::send) }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithTag("friend_profile_hero", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("board_profile_card", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(FixtureData.spobProfile.displayName).assertIsDisplayed()
    }
    @Test fun thorBoardsReplacesMainNavigationAndKeepsItsHeaderPinned() {
        state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Directory))
        show(false)
        val header = compose.onNodeWithTag("boards_header").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("tab_messages").assertDoesNotExist()
        compose.onNodeWithTag("boards_exit").assertIsDisplayed()
        val nav = compose.onNodeWithTag("boards_navigation").fetchSemanticsNode().boundsInRoot
        assertTrue("Boards navigation overlaps its header", nav.top >= header.bottom)
        compose.onNodeWithTag("boards_code_section").performSemanticsAction(SemanticsActions.OnClick) { it() }
        // Drag within the visible viewport: performScrollTo's distances don't
        // account for DesignSurface's graphics-layer scale in this preview.
        repeat(4) {
            if(!compose.onNodeWithTag("boards_code").isDisplayed()) {
                compose.onNodeWithTag("boards_scroll").performTouchInput { swipeUp(startY = height * .55f, endY = height * .12f) }
            }
        }
        compose.onNodeWithTag("boards_code").assertIsDisplayed()
        assertEquals(header.top, compose.onNodeWithTag("boards_header").fetchSemanticsNode().boundsInRoot.top, 1f)
        capture("boards-dual-directory")
        compose.onNodeWithTag("boards_options").performClick()
        compose.onNodeWithTag("boards_drafts").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("boards_global_push").performScrollTo().assertIsDisplayed()
        capture("boards-dual-options")
    }

    @Test fun phoneBoardsHasItsOwnNavigationAndAnExit() {
        show(true)
        compose.onNodeWithTag("tab_messages").assertDoesNotExist()
        compose.onNodeWithTag("boards_exit").performClick()
        compose.runOnIdle { assertTrue(appEvents.contains(PocketPassEvent.SelectDestination(PocketPassDestination.Home))) }
    }

    @Test fun phoneBoardsGallery() { gallery(true) }
    @Test fun thorBoardsGallery() { gallery(false) }

    @Test fun backClosesAboutBeforeLeavingTheBoard() {
        state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Board))
        show(false)
        compose.onNodeWithTag("board_about").performClick()
        compose.onNodeWithTag("board_manage").assertIsDisplayed()
        compose.onNodeWithTag("boards_back").performClick()
        compose.onNodeWithTag("board_manage").assertDoesNotExist()
        compose.runOnIdle { assertFalse(events.contains(BoardAction.Back)) }
        compose.onNodeWithTag("boards_back").performClick()
        compose.runOnIdle { assertEquals(BoardAction.Back, events.last()) }
    }

    @Test fun backClosesTheExpandedSettingsSectionFirst() {
        state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Manage))
        show(true)
        compose.onNodeWithTag("board_section_joining").performScrollTo().performClick()
        reveal("board_archive")
        compose.onNodeWithTag("boards_back").performClick()
        compose.onNodeWithTag("board_archive").assertDoesNotExist()
        compose.runOnIdle { assertFalse(events.contains(BoardAction.Back)) }
    }

    @Test fun controllerOutlinesFollowBoardControlsAndScrollViewport() {
        state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Board))
        show(false)
        compose.runOnIdle {
            focus.focus("boards_back")
            assertEquals(40f, focus.focusedTarget(null)!!.cornerRadius!!, 0f)
            focus.focus("board_sort_newest")
            val filter = focus.focusedTarget(null)!!
            assertEquals(20f, filter.cornerRadius!!, 0f)
            assertNotNull(filter.viewport)
            assertTrue(filter.viewport!!.bounds.top > 0f)
            assertTrue(focus.move(FocusDirection.Right))
            assertEquals("board_sort_activity", focus.focusId)
            assertTrue(focus.activate())
        }
        compose.runOnIdle { assertEquals(BoardSort.Activity, state.boards.sort) }
        compose.onNodeWithTag("board_about").performClick()
        compose.runOnIdle {
            focus.focus("board_manage")
            assertEquals(28f, focus.focusedTarget(null)!!.cornerRadius!!, 0f)
            assertTrue(focus.activate())
        }
        compose.onNodeWithTag("board_section_joining").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { focus.focus("board_archive") }
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithTag("board_archive").assertIsDisplayed()
        compose.runOnIdle {
            val target = focus.focusedTarget(null)!!
            assertTrue("Focused controls must be scrolled into the board viewport", target.viewport!!.bounds.contains(target.bounds.center))
        }
        capture("boards-controller-archive")
    }

    @Test fun controllerCanOpenMessagesAndEnterNoteActions() {
        state = state.copy(conversations = FixtureData.conversations, boards = state.boards.copy(screen = BoardsScreen.Chats))
        show(true)
        val conversation = FixtureData.conversations.first()
        compose.runOnIdle {
            focus.focus("message_${conversation.id.value}")
            assertNotNull("The Boards navigation must not block message rows", focus.focusedTarget(null))
            assertTrue(focus.activate())
            assertTrue(appEvents.contains(PocketPassEvent.OpenMessage(conversation.id.value)))
            state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Board,
                posts = listOf(BoardPost("actions", board.id, authorName = "A friend", body = "Hello"))))
        }
        compose.runOnIdle { focus.focus("preview_actions"); assertTrue(focus.activate()) }
        compose.runOnIdle {
            assertEquals("actions_actions", focus.focusId)
            assertTrue(focus.move(FocusDirection.Down))
            assertEquals("thread_actions", focus.focusId)
            assertTrue(focus.move(FocusDirection.Left))
            assertEquals("yeah_actions", focus.focusId)
            assertTrue(focus.exitToParent())
            assertEquals("preview_actions", focus.focusId)
        }
    }

    @Test fun controllerSkipsDisabledDrawingToolsAndUsesRoundInkOutlines() {
        state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Compose,
            draft = LocalBoardDraft(boardId = board.id, content = BoardDraftContent(drawing = BoardDrawing()))))
        show(false)
        compose.runOnIdle {
            focus.focus("drawing_undo")
            assertNull("An unavailable undo must not capture controller focus", focus.focusedTarget(null))
            focus.focus("pen_pixel")
            assertEquals(22f, focus.focusedTarget(null)!!.cornerRadius!!, 0f)
            focus.focus("ink_4")
            assertEquals(44f, focus.focusedTarget(null)!!.cornerRadius!!, 0f)
            assertTrue(focus.activate())
        }
        compose.runOnIdle { assertEquals(BoardDrawingTools.colors[4], state.boards.ink) }
        compose.onNodeWithText("Add a caption…").performScrollTo().performClick()
        compose.runOnIdle {
            focus.focus("board_keyboard_done")
            assertNotNull("Done shares the keyboard's focus layer", focus.focusedTarget(null))
            assertTrue(focus.activate())
        }
        compose.onNodeWithTag("pocket_keyboard", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun aboutAndFeedSelectionAnimateBetweenStates() {
        state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Board))
        show(false)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("board_about").performClick()
        compose.mainClock.advanceTimeBy(96)
        val opening = compose.onNodeWithTag("board_about_reveal").fetchSemanticsNode().boundsInRoot.height
        compose.mainClock.advanceTimeBy(400)
        val opened = compose.onNodeWithTag("board_about_reveal").fetchSemanticsNode().boundsInRoot.height
        assertTrue("About must expand over time", opening > 0f && opened > opening)
        compose.onNodeWithTag("board_about").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeBy(400)
        val start = compose.onNodeWithTag("board_sort_newest_selection").fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithTag("board_sort_popular").performClick()
        compose.mainClock.advanceTimeBy(96)
        val moving = compose.onNodeWithTag("board_sort_newest_selection").fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.advanceTimeBy(400)
        val end = compose.onNodeWithTag("board_sort_newest_selection").fetchSemanticsNode().boundsInRoot.left
        assertTrue("The selection should slide, not jump", moving > start && moving < end)
        compose.onNodeWithTag("board_period_week").assertExists()
        compose.mainClock.autoAdvance = true
    }

    @Test fun settingsAndComposerAnimateOncePerNavigation() {
        state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Board))
        show(true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("board_manage").performClick()
        compose.mainClock.advanceTimeBy(32)
        val entering = compose.onNodeWithTag("boards_scroll").fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.advanceTimeBy(1600)
        val settled = compose.onNodeWithTag("boards_scroll").fetchSemanticsNode().boundsInRoot.left
        assertTrue("Settings should move into place ($entering -> $settled)", entering > settled)
        compose.runOnIdle { state = state.copy(boards = state.boards.copy(info = "Updated")) }
        compose.mainClock.advanceTimeBy(32)
        assertEquals("Refreshing must not replay the page entrance", settled,
            compose.onNodeWithTag("boards_scroll").fetchSemanticsNode().boundsInRoot.left, .1f)
        compose.runOnIdle { state = state.copy(boards = state.boards.copy(screen = BoardsScreen.Board)) }
        compose.mainClock.advanceTimeBy(1600)
        compose.onNodeWithTag("board_compose").performClick()
        compose.mainClock.advanceTimeBy(32)
        val noteEntering = compose.onNodeWithTag("boards_scroll").fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.advanceTimeBy(1600)
        assertTrue("The composer should move into place", noteEntering > compose.onNodeWithTag("boards_scroll").fetchSemanticsNode().boundsInRoot.left)
        compose.onNodeWithTag("note_text").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    private fun reveal(tag: String) {
        repeat(12) {
            if(!compose.onNodeWithTag(tag).isDisplayed()) {
                compose.onNodeWithTag("boards_scroll").performTouchInput { swipeUp(startY = height * .65f, endY = height * .15f) }
            }
        }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    @Test fun messagesAndBoardsShareNavigationEvenWhenBoardsAreDisabled() {
        state = state.copy(boards = state.boards.copy(enabled = false))
        show(true)
        compose.onNodeWithTag("boards_chats").performClick()
        compose.onNodeWithText("Private messages").assertIsDisplayed()
        compose.onNodeWithTag("messages_new_group").assertIsDisplayed()
        compose.onNodeWithTag("boards_directory").performClick()
        compose.onNodeWithTag("boards_retry").performScrollTo().assertIsDisplayed()
    }

    @Test fun sharedMessagesTabOpensAnExistingConversation() {
        state = state.copy(conversations = FixtureData.conversations)
        show(true)
        compose.onNodeWithTag("boards_chats").performClick()
        val conversation = FixtureData.conversations.first()
        compose.onNodeWithTag("message_${conversation.id.value}").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(appEvents.contains(PocketPassEvent.OpenMessage(conversation.id.value))) }
        compose.onNodeWithTag("boards_directory").assertIsDisplayed()
    }

    private fun gallery(phone: Boolean) {
        state = state.copy(conversations = FixtureData.conversations, friends = FixtureData.friends)
        show(phone)
        val drawing = BoardDrawing(strokes = listOf(BoardStroke("smooth", "#3379D6", 12f,
            listOf(listOf(80f, 450f), listOf(200f, 180f), listOf(340f, 450f), listOf(460f, 230f), listOf(720f, 450f)))))
        val post = BoardPost("sample", board.id, authorName = "A friend", body = "Anyone playing something good this weekend?", yeahCount = 12, replyCount = 3)
        val drawn = BoardPost("sketch", board.id, authorName = "Another friend", body = "A quick sketch from my walk", drawing = drawing, hasDrawing = true)
        val draft = LocalBoardDraft(boardId = board.id, content = BoardDraftContent(body = "A little drawing from today.", drawing = drawing))
        val base = BoardsUiState(enabled = true, board = board, boards = listOf(board, board.copy(id = "other", name = "Sketches and works in progress", visibility = "private", memberCount = 24)),
            posts = listOf(post, drawn), focused = drawn, drafts = listOf(draft), draft = draft,
            drawingHistory = BoardDrawingHistory(drawing), stationery = listOf(BoardStationery("plain", "Plain paper", available = true)),
            notices = listOf(BoardNotice("notice", board.id, board.name, post.id, "activity", 3,
                subject = post.body, subjectType = "text", threadAuthorName = post.authorName, latestActorName = "Another friend")),
            members = listOf(BoardMember(FixtureData.friends.first().profile.userId.value, displayName = FixtureData.friends.first().profile.displayName)),
            management = buildJsonObject { put("requests", buildJsonArray { add(buildJsonObject { put("user_id", "joiner"); put("display_name", "New member") }) }) },
            moderationNotices = buildJsonObject { put("actions", buildJsonArray { add(buildJsonObject { put("id", "case-12"); put("board_id", board.id); put("action", "remove"); put("reason", "Please keep posts on topic.") }) }) })
        for(theme in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            for(screen in BoardsScreen.entries.filter { it != BoardsScreen.Chooser }) {
                compose.runOnIdle { state = state.copy(themeMode = theme, boards = base.copy(screen = screen, thread = post.takeIf { screen == BoardsScreen.Thread }, posts = if(screen == BoardsScreen.Thread) listOf(drawn.copy(threadId = post.id)) else base.posts)) }
                compose.mainClock.advanceTimeBy(2000)
                compose.onNodeWithTag("boards_header").assertIsDisplayed()
                capture("boards-${if(phone) "phone" else "thor"}-${theme.name}-${screen.name}")
            }
            compose.runOnIdle { state = state.copy(boards = base.copy(screen = BoardsScreen.Directory, boards = emptyList(), focused = null)) }
            capture("boards-${if(phone) "phone" else "thor"}-${theme.name}-empty")
            compose.onNodeWithTag("boards_options").performClick()
            capture("boards-${if(phone) "phone" else "thor"}-${theme.name}-options")
            compose.runOnIdle { state = state.copy(boards = base.copy(screen = BoardsScreen.Compose)) }
            compose.onNodeWithTag("drawing_tools").performScrollTo().performClick()
            compose.onNodeWithTag("ink_4").performScrollTo().assertIsDisplayed()
            capture("boards-${if(phone) "phone" else "thor"}-${theme.name}-tools")
            compose.runOnIdle { state = state.copy(boards = base.copy(screen = BoardsScreen.Manage)) }
            for(section in listOf("invite", "details", "joining", "artwork", "requests", "members")) {
                compose.onNodeWithTag("board_section_$section").performScrollTo().performClick()
                capture("boards-${if(phone) "phone" else "thor"}-${theme.name}-manage-$section")
                compose.onNodeWithTag("board_section_$section").performClick()
            }
        }
    }

    private fun capture(name: String, tag: String? = null) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        android.os.SystemClock.sleep(250)
        val bitmap=(if(tag == null) compose.onRoot() else compose.onNodeWithTag(tag)).captureToImage().asAndroidBitmap()
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.FileOutputStream(java.io.File(context.getExternalFilesDir(null),"$name.png")).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
}
