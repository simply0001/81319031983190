package com.pocketpass.app.boards

import com.pocketpass.app.domain.model.ClientOperationId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray

val BoardJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
fun newBoardId(): String = ClientOperationId.new().value

@Serializable
data class Board(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val description: String = "",
    val rules: String = "",
    val visibility: String = "public",
    @SerialName("join_policy") val joinPolicy: String = "open",
    @SerialName("code_policy") val codePolicy: String = "open",
    @SerialName("members_can_invite") val membersCanInvite: Boolean = false,
    val accent: String = "blue",
    @SerialName("icon_asset_id") val iconAssetId: String? = null,
    @SerialName("cover_asset_id") val coverAssetId: String? = null,
    @SerialName("icon_drawing") val iconDrawing: BoardDrawing? = null,
    @SerialName("cover_drawing") val coverDrawing: BoardDrawing? = null,
    val archived: Boolean = false,
    val role: String? = null,
    @SerialName("member_count") val memberCount: Int = 0,
    val muted: Boolean = false,
    @SerialName("push_enabled") val pushEnabled: Boolean = true,
    @SerialName("join_requested") val joinRequested: Boolean = false,
    @SerialName("transfer_pending") val transferPending: Boolean? = false,
    @SerialName("access_revision") val accessRevision: Long = 1,
) {
    val canModerate get() = role == "owner" || role == "moderator"
    val canPost get() = role != null && !archived
    val canInvite get() = !archived && (canModerate || (role != null && membersCanInvite))
}

@Serializable
data class BoardPost(
    val id: String,
    @SerialName("board_id") val boardId: String,
    @SerialName("author_id") val authorId: String? = null,
    @SerialName("author_name") val authorName: String = "",
    @SerialName("author_avatar") val authorAvatar: String? = null,
    @SerialName("thread_id") val threadId: String? = null,
    @SerialName("reply_to") val replyTo: String? = null,
    val body: String = "",
    val drawing: BoardDrawing? = null,
    val stationery: PublishedStationery? = null,
    @SerialName("has_drawing") val hasDrawing: Boolean = false,
    val spoiler: Boolean = false,
    val removed: Boolean = false,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("yeah_count") val yeahCount: Int = 0,
    val yeah: Boolean = false,
    @SerialName("reply_count") val replyCount: Int = 0,
)

@Serializable
data class BoardDrawing(val version: Int = 1, val width: Int = 800, val height: Int = 600, val strokes: List<BoardStroke> = emptyList()) {
    fun validate() {
        require(version == 1 && width == 800 && height == 600) { "Notes use 4:3 paper" }
        require(strokes.size <= 1_000 && strokes.sumOf { it.points.size } <= 20_000) { "This note has too many strokes" }
        strokes.forEach { stroke ->
            require(stroke.pen in BoardDrawingTools.pens && stroke.color in BoardDrawingTools.colors)
            require(stroke.size.isFinite() && stroke.size in 1f..40f && stroke.points.isNotEmpty())
            stroke.points.forEach { require(it.size == 2 && it[0].isFinite() && it[1].isFinite() && it[0] in 0f..800f && it[1] in 0f..600f) }
            if (stroke.pen == "bucket") {
                require(stroke.size == 2f && stroke.points.size % 2 == 0) { "Invalid fill" }
                stroke.points.chunked(2).forEach { (start, end) ->
                    require(start[1] == end[1] && end[0] > start[0] && start[1] < 600f &&
                        start[0] % BoardPixelRaster.cellSize == 0f && end[0] % BoardPixelRaster.cellSize == 0f &&
                        start[1] % BoardPixelRaster.cellSize == 0f) { "Invalid fill run" }
                }
            }
        }
        require(BoardJson.encodeToString(serializer(), this).encodeToByteArray().size <= 524_288) { "This drawing is too large" }
    }
}

@Serializable
data class BoardStroke(val pen: String = "pixel", val color: String = "#222222", val size: Float = 4f, val points: List<List<Float>>)

object BoardDrawingTools {
    val colors = listOf("#222222", "#E84A5F", "#F6B93B", "#4CAF70", "#3379D6", "#9564C8", "#FFFFFF")
    val pens = listOf("pixel", "smooth", "eraser", "bucket")
    val sizes = listOf(2f, 4f, 8f, 16f, 32f)
}

data class BoardDrawingHistory(val drawing: BoardDrawing = BoardDrawing(), val redo: List<BoardStroke> = emptyList()) {
    fun add(stroke: BoardStroke): BoardDrawingHistory {
        val next = drawing.copy(strokes = drawing.strokes + stroke)
        next.validate()
        return BoardDrawingHistory(next)
    }
    fun undo(): BoardDrawingHistory = drawing.strokes.lastOrNull()?.let {
        copy(drawing = drawing.copy(strokes = drawing.strokes.dropLast(1)), redo = redo + it)
    } ?: this
    fun redo(): BoardDrawingHistory = redo.lastOrNull()?.let {
        copy(drawing = drawing.copy(strokes = drawing.strokes + it), redo = redo.dropLast(1))
    } ?: this
}

@Serializable
data class PublishedStationery(val id: String = "plain", val name: String = "Plain paper", val version: Int = 1, val artwork: JsonObject? = null)
@Serializable
data class BoardStationery(val id: String, val name: String, val version: Int = 1, val access: String = "free", val price: Int = 0,
    @SerialName("achievement_key") val achievementKey: String? = null, val available: Boolean = false, val owned: Boolean = false, val artwork: JsonObject? = null)
@Serializable
data class BoardMember(@SerialName("user_id") val userId: String, val role: String = "member", @SerialName("display_name") val displayName: String = "", @SerialName("avatar_path") val avatarPath: String? = null)
@Serializable
data class BoardInvitation(val id: String, @SerialName("board_id") val boardId: String, @SerialName("board_name") val boardName: String = "", @SerialName("inviter_id") val inviterId: String = "")
@Serializable
data class BoardReport(val id: String, @SerialName("board_id") val boardId: String, @SerialName("post_id") val postId: String? = null, val reason: String, val status: String = "open", val resolution: String = "")
@Serializable
data class BoardRestriction(val id: String, @SerialName("board_id") val boardId: String? = null, @SerialName("user_id") val userId: String, val kind: String, val reason: String, val central: Boolean = false, @SerialName("revoked_at") val revokedAt: String? = null, @SerialName("expires_at") val expiresAt: String? = null)
@Serializable
data class BoardAppeal(val id: String, @SerialName("board_id") val boardId: String, @SerialName("user_id") val userId: String, @SerialName("case_id") val caseId: String, val body: String, val status: String = "open", val resolution: String = "")
@Serializable
data class BoardModerationAction(val id: String, @SerialName("board_id") val boardId: String? = null, val action: String, val reason: String = "", val central: Boolean = false)
@Serializable
data class BoardNotice(val id: String, @SerialName("board_id") val boardId: String, @SerialName("board_name") val boardName: String = "", @SerialName("thread_id") val threadId: String? = null, val kind: String, @SerialName("event_count") val eventCount: Int = 1, @SerialName("read_at") val readAt: String? = null,
    val subject: String? = null, @SerialName("subject_type") val subjectType: String? = null,
    @SerialName("thread_author_name") val threadAuthorName: String? = null,
    @SerialName("latest_actor_name") val latestActorName: String? = null)

@Serializable
data class BoardDraftContent(
    @SerialName("branding_kind") val brandingKind: String? = null,
    val body: String = "", val drawing: BoardDrawing? = null, val spoiler: Boolean = false,
    @SerialName("stationery_id") val stationeryId: String = "plain",
    @SerialName("thread_id") val threadId: String? = null,
    @SerialName("reply_to") val replyTo: String? = null,
)

@Serializable
data class LocalBoardDraft(
    val id: String = newBoardId(), val boardId: String, val revisionId: String = newBoardId(),
    val cloudDraftId: String = id,
    val cloudBaseId: String? = null, val cloudSynced: Boolean = false,
    val content: BoardDraftContent = BoardDraftContent(), val recovered: Boolean = false,
    val pendingPublishId: String? = null, val pendingPublishArgs: JsonObject? = null,
) {
    fun edited(content: BoardDraftContent): LocalBoardDraft {
        check(pendingPublishId == null) { "Retry the pending publish before editing this copy" }
        return copy(revisionId = newBoardId(), content = content, cloudBaseId = if (cloudSynced) revisionId else cloudBaseId, cloudSynced = false)
    }
}

enum class BoardsScreen { Chooser, Chats, Directory, Board, Thread, Compose, Drafts, Propose, Manage, Inbox, Notices, Stationery }
enum class BoardSort(val wire: String, val label: String) { Newest("newest", "Newest"), Activity("activity", "Recent Activity"), Popular("popular", "Popular") }
enum class BoardPeriod(val wire: String, val label: String) { Today("today", "Today"), Week("week", "This Week"), All("all", "All Time") }

data class BoardCanvasViewport(val left: Float = 0f, val top: Float = 0f, val width: Float = 1f,
    val height: Float = 1f, val zoom: Float = 1f, val drawing: Boolean = false)

data class BoardInviteStatus(val boardId: String, val userId: String, val sending: Boolean = true, val error: String? = null)

data class BoardsUiState(
    val inviteStatus: BoardInviteStatus? = null,
    val pushEnabled: Boolean = true,
    val assets: Map<String, ByteArray> = emptyMap(),
    val reviewing: Boolean = false,
    val proposals: JsonArray = JsonArray(emptyList()),
    val screen: BoardsScreen = BoardsScreen.Directory, val enabled: Boolean = false, val requestsOpen: Boolean = true,
    val loading: Boolean = false, val busy: Boolean = false, val error: String? = null, val info: String? = null,
    val explore: Boolean = false, val search: String = "", val boards: List<Board> = emptyList(),
    val board: Board? = null, val posts: List<BoardPost> = emptyList(), val thread: BoardPost? = null,
    val focused: BoardPost? = null, val revealed: Set<String> = emptySet(),
    val pinnedPostId: String? = null,
    val directoryFocusId: String? = null, val directoryLatest: BoardPost? = null,
    val directoryPreviewLoading: Boolean = false, val directoryPreviewError: Boolean = false,
    val canvasViewport: BoardCanvasViewport = BoardCanvasViewport(),
    val cursor: JsonObject? = null, val sort: BoardSort = BoardSort.Newest, val period: BoardPeriod = BoardPeriod.Week,
    val draft: LocalBoardDraft? = null, val drafts: List<LocalBoardDraft> = emptyList(),
    val drawingHistory: BoardDrawingHistory = BoardDrawingHistory(),
    val activeStroke: BoardStroke? = null,
    val pen: String = "pixel", val ink: String = "#222222", val penSize: Float = 2f,
    val invitations: List<BoardInvitation> = emptyList(), val notices: List<BoardNotice> = emptyList(),
    val noticeReturnToInbox: Boolean = false, val openReportsFromInbox: Boolean = false,
    val members: List<BoardMember> = emptyList(), val management: JsonObject? = null, val moderationNotices: JsonObject? = null,
    val inviteMemberIds: Set<String> = emptySet(), val inviteMembersLoaded: Boolean = false,
    val stationery: List<BoardStationery> = emptyList(), val inviteCode: String? = null,
)

sealed interface BoardAction {
    data class OpenDestination(val boardId: String, val threadId: String? = null, val review: Boolean = false) : BoardAction
    data class OpenNotice(val notice: BoardNotice) : BoardAction
    data object OpenChats : BoardAction
    data class Directory(val explore: Boolean = false, val search: String = "") : BoardAction
    data class OpenBoard(val id: String) : BoardAction
    data class OpenThread(val post: BoardPost) : BoardAction
    data class Focus(val post: BoardPost) : BoardAction
    data class PreviewBoard(val id: String) : BoardAction
    data class TogglePin(val postId: String) : BoardAction
    data class CanvasViewport(val viewport: BoardCanvasViewport) : BoardAction
    data class Reveal(val post: BoardPost) : BoardAction
    data class Sort(val sort: BoardSort, val period: BoardPeriod = BoardPeriod.Week) : BoardAction
    data object More : BoardAction
    data object Refresh : BoardAction
    data object Back : BoardAction
    data class Compose(val replyTo: String? = null) : BoardAction
    data class DrawBranding(val kind: String) : BoardAction
    data class ImportBranding(val kind: String) : BoardAction
    data class ResumeDraft(val draft: LocalBoardDraft) : BoardAction
    data class DiscardDraft(val draft: LocalBoardDraft) : BoardAction
    data class Text(val text: String) : BoardAction
    data class Spoiler(val enabled: Boolean) : BoardAction
    data class Tool(val pen: String? = null, val color: String? = null, val size: Float? = null) : BoardAction
    data class Stroke(val stroke: BoardStroke) : BoardAction
    data class PreviewStroke(val stroke: BoardStroke?) : BoardAction
    data object Undo : BoardAction
    data object Redo : BoardAction
    data object Publish : BoardAction
    data object Drafts : BoardAction
    data object Propose : BoardAction
    data object Manage : BoardAction
    data object Inbox : BoardAction
    data object Notices : BoardAction
    data object Stationery : BoardAction
    data class SelectStationery(val id: String) : BoardAction
    data class Mutate(val operation: String, val args: JsonObject, val operationId: String = newBoardId()) : BoardAction
}
