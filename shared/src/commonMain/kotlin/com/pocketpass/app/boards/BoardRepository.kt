package com.pocketpass.app.boards

import com.pocketpass.app.domain.model.ACCOUNT_BANNED_HINT
import com.pocketpass.app.domain.state.AccountBanSignal
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.io.encoding.Base64
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*

fun boardArgs(vararg pairs: Pair<String, JsonElement?>): JsonObject = JsonObject(pairs.mapNotNull { (k,v) -> v?.let { k to it } }.toMap())
fun String.boardValue(): JsonPrimitive = JsonPrimitive(this)
fun Boolean.boardValue(): JsonPrimitive = JsonPrimitive(this)
fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
inline fun <reified T> JsonElement.boardDecode(): T = BoardJson.decodeFromJsonElement(this)
inline fun <reified T> T.boardEncode(): JsonElement = BoardJson.encodeToJsonElement(this)

class BoardFailure(message: String, val retryable: Boolean, val accessDenied: Boolean = false, val code: String? = null) : Exception(message)

interface BoardApi {
    suspend fun uploadBranding(accountId: String, boardId: String, kind: String, bytes: ByteArray, operationId: String): JsonObject = throw BoardFailure("Image imports are unavailable", false)
    suspend fun query(accountId: String, operation: String, args: JsonObject = JsonObject(emptyMap())): JsonElement
    suspend fun mutate(accountId: String, operation: String, args: JsonObject, operationId: String): JsonObject
}

class SupabaseBoardApi(private val client: SupabaseClient) : BoardApi {
    private val mediaClient by lazy { HttpClient() }
    override suspend fun uploadBranding(accountId: String, boardId: String, kind: String, bytes: ByteArray, operationId: String): JsonObject {
        if (client.auth.currentUserOrNull()?.id != accountId) throw BoardFailure("Sign in to use Boards", false, true)
        require(bytes.size <= 10 * 1024 * 1024) { "Choose an image smaller than 10 MB" }
        try {
            val response = mediaClient.post(client.supabaseHttpUrl + "/boards/media") {
                header("Authorization", "Bearer ${client.auth.currentAccessTokenOrNull()}")
                contentType(ContentType.Application.Json)
                setBody(boardArgs("board_id" to boardId.boardValue(), "kind" to kind.boardValue(),
                    "operation_id" to operationId.boardValue(), "image" to Base64.encode(bytes).boardValue()).toString())
            }
            val data = BoardJson.parseToJsonElement(response.bodyAsText()).jsonObject
            if (!response.status.isSuccess()) throw BoardFailure(data.text("message") ?: "Image upload failed", response.status.value >= 500, response.status.value == 403)
            return data
        } catch(e: CancellationException) { throw e } catch(e: BoardFailure) { throw e }
        catch(_: Exception) { throw BoardFailure("Image upload wasn't confirmed. Retry to finish the same upload.", true) }
    }
    private suspend fun rpc(accountId: String, name: String, args: JsonObject): JsonElement {
        if (client.auth.currentUserOrNull()?.id != accountId) throw BoardFailure("Sign in to use Boards", false, true)
        try {
            return BoardJson.parseToJsonElement(client.postgrest.rpc(name, args).data)
        } catch (e: CancellationException) { throw e
        } catch (e: PostgrestRestException) {
            if (e.hint == ACCOUNT_BANNED_HINT) AccountBanSignal.report()
            throw BoardFailure(e.error, e.statusCode >= 500 || e.statusCode == 429 || e.hint == "BOARD_RATE_LIMIT", e.statusCode == 403 || e.statusCode == 404, e.hint)
        } catch (_: Exception) { throw BoardFailure("Couldn't reach Boards. Your draft is saved. Try again when you're online.", true) }
    }
    override suspend fun query(accountId: String, operation: String, args: JsonObject): JsonElement {
        val result = when (operation) {
            "inbox" -> rpc(accountId, "boards_inbox", boardArgs("p_cursor" to args["cursor"], "p_limit" to args["limit"]))
            "friend_code" -> rpc(accountId, "resolve_friend_code", boardArgs("p_code" to args["code"]))
            "mention_candidates" -> rpc(accountId, "boards_mention_candidates", boardArgs("p_board_id" to args["board_id"], "p_search" to args["search"]))
            else -> rpc(accountId, "boards_query", boardArgs("p_operation" to operation.boardValue(), "p_args" to args))
        }
        fun withAvatar(element: JsonElement, field: String = "author_avatar"): JsonElement {
            val post = element as? JsonObject ?: return element
            val path = post.text(field)?.takeIf { it.isNotBlank() } ?: return post
            return JsonObject(post + (field to client.storage.from("avatars").authenticatedUrl(path).boardValue()))
        }
        return when(operation) {
            "post" -> withAvatar(result)
            "feed", "replies" -> JsonObject(result.jsonObject.mapValues { (key, value) -> when(key) {
                "items" -> JsonArray(value.jsonArray.map { withAvatar(it) })
                "post" -> withAvatar(value)
                else -> value
            } })
            "members" -> JsonObject(result.jsonObject.mapValues { (key, value) ->
                if(key == "items") JsonArray(value.jsonArray.map { withAvatar(it, "avatar_path") }) else value
            })
            "mention_candidates" -> JsonArray(result.jsonArray.map { withAvatar(it, "avatar_path") })
            else -> result
        }
    }
    override suspend fun mutate(accountId: String, operation: String, args: JsonObject, operationId: String): JsonObject =
        rpc(accountId, "boards_mutate", boardArgs("p_operation" to operation.boardValue(), "p_args" to args, "p_operation_id" to operationId.boardValue())).jsonObject
}

interface BoardRepository {
    suspend fun uploadBranding(accountId: String, boardId: String, kind: String, bytes: ByteArray, operationId: String): JsonObject = throw BoardFailure("Image imports are unavailable", false)
    val invalidations: SharedFlow<Unit>
    fun invalidate()
    suspend fun query(accountId: String, operation: String, args: JsonObject = JsonObject(emptyMap())): JsonElement
    suspend fun mutate(accountId: String, operation: String, args: JsonObject, operationId: String = newBoardId()): JsonObject
    fun observeDrafts(accountId: String): Flow<List<LocalBoardDraft>>
    suspend fun saveLocal(accountId: String, draft: LocalBoardDraft)
    suspend fun drafts(accountId: String): List<LocalBoardDraft>
    suspend fun syncDraft(accountId: String, draftId: String): LocalBoardDraft?
    suspend fun recoverCloudDrafts(accountId: String)
    suspend fun discardDraft(accountId: String, draft: LocalBoardDraft)
    suspend fun publish(accountId: String, draftId: String): JsonObject
    suspend fun clearContentCache(accountId: String)
}

class RoomBoardRepository(private val api: BoardApi, private val dao: BoardDao) : BoardRepository {
    override suspend fun uploadBranding(accountId: String, boardId: String, kind: String, bytes: ByteArray, operationId: String): JsonObject =
        api.uploadBranding(accountId, boardId, kind, bytes, operationId)
    override val invalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val draftMutex = Mutex()
    private val syncMutex = Mutex()
    override fun invalidate() { invalidations.tryEmit(Unit) }
    override suspend fun query(accountId: String, operation: String, args: JsonObject): JsonElement = api.query(accountId, operation, args)
    override suspend fun mutate(accountId: String, operation: String, args: JsonObject, operationId: String): JsonObject =
        api.mutate(accountId, operation, args, operationId).also {
            args.text("board_id")?.let { dao.invalidateBoard(accountId, it) }
        }
    override fun observeDrafts(accountId: String): Flow<List<LocalBoardDraft>> = dao.observeDrafts(accountId).map { rows -> rows.map { it.decode() } }
    override suspend fun drafts(accountId: String): List<LocalBoardDraft> = dao.drafts(accountId).map { it.decode() }
    override suspend fun saveLocal(accountId: String, draft: LocalBoardDraft) = draftMutex.withLock {
        val stored = dao.draft(accountId, draft.id)?.decode()
        if(stored?.pendingPublishId != null) return@withLock
        val merged = if(stored == null) draft else draft.copy(
            cloudDraftId = stored.cloudDraftId,
            cloudBaseId = if(stored.cloudSynced && stored.revisionId != draft.revisionId) stored.revisionId else stored.cloudBaseId,
            cloudSynced = stored.cloudSynced && stored.revisionId == draft.revisionId,
            recovered = stored.recovered || draft.recovered,
        )
        save(accountId, merged)
    }
    private suspend fun save(accountId: String, draft: LocalBoardDraft) = dao.saveDraft(BoardDraftEntity(accountId, draft.id, draft.boardId, BoardJson.encodeToString(LocalBoardDraft.serializer(), draft), Clock.System.now().toEpochMilliseconds()))
    private fun BoardDraftEntity.decode(): LocalBoardDraft = BoardJson.decodeFromString(LocalBoardDraft.serializer(), payload)

    override suspend fun syncDraft(accountId: String, draftId: String): LocalBoardDraft? = syncMutex.withLock {
        val snapshot = draftMutex.withLock { dao.draft(accountId, draftId)?.decode() } ?: return@withLock null
        if (snapshot.cloudSynced || snapshot.pendingPublishId != null) return@withLock snapshot
        try { api.mutate(accountId, "save_draft", boardArgs(
            "board_id" to snapshot.boardId.boardValue(), "draft_id" to snapshot.cloudDraftId.boardValue(),
            "revision_id" to snapshot.revisionId.boardValue(), "base_id" to snapshot.cloudBaseId?.boardValue(), "payload" to snapshot.content.boardEncode(),
        ), snapshot.revisionId) } catch(e: BoardFailure) {
            if(e.code != "BOARD_DRAFT_BASE_EXPIRED") throw e
            return@withLock draftMutex.withLock {
                dao.draft(accountId,draftId)?.decode()?.copy(cloudDraftId = newBoardId(), cloudBaseId = null,
                    revisionId = newBoardId(), cloudSynced = false, recovered = true)?.also { save(accountId,it) }
            }
        }
        draftMutex.withLock {
            val latest = dao.draft(accountId, draftId)?.decode() ?: return@withLock null
            val updated = if (latest.revisionId == snapshot.revisionId) latest.copy(cloudSynced = true)
                else latest.copy(cloudBaseId = snapshot.revisionId)
            save(accountId, updated)
            updated
        }
    }

    override suspend fun recoverCloudDrafts(accountId: String) {
        var cursor: JsonObject? = null
        do {
            val page = api.query(accountId, "drafts", boardArgs("cursor" to cursor)).jsonObject
            draftMutex.withLock {
                page.getValue("items").jsonArray.forEach { element ->
                    val remote = element.jsonObject
                    val revision = remote.getValue("id").jsonPrimitive.content
                    val local = drafts(accountId)
                    if (local.none { it.revisionId == revision || it.cloudBaseId == revision }) {
                        val group = remote.getValue("draft_id").jsonPrimitive.content
                        val peers = local.filter { it.cloudDraftId == group }
                        val recovered = LocalBoardDraft(id = revision, cloudDraftId = group,
                            boardId = remote.getValue("board_id").jsonPrimitive.content, revisionId = revision,
                            cloudBaseId = remote.text("base_id"), cloudSynced = true,
                            content = remote.getValue("payload").boardDecode(), recovered = peers.any { !it.cloudSynced } || peers.size > 1)
                        save(accountId, recovered)
                        peers.filter { it.cloudSynced && it.pendingPublishId == null && it.revisionId == recovered.cloudBaseId }
                            .forEach { dao.deleteDraft(accountId, it.id) }
                    }
                }
            }
            cursor = page["cursor"] as? JsonObject
        } while (cursor != null)
    }

    override suspend fun discardDraft(accountId: String, draft: LocalBoardDraft) = syncMutex.withLock {
        require(draft.pendingPublishId == null) { "Retry the pending publish first" }
        (if (draft.cloudSynced) draft.revisionId else draft.cloudBaseId)?.let {
            api.mutate(accountId, "discard_draft", boardArgs("revision_id" to it.boardValue()), newBoardId())
        }
        draftMutex.withLock { dao.deleteDraft(accountId, draft.id) }
    }

    override suspend fun publish(accountId: String, draftId: String): JsonObject = syncMutex.withLock {
        val pending = draftMutex.withLock {
            val draft = requireNotNull(dao.draft(accountId, draftId)?.decode()) { "Draft unavailable" }
            draft.content.drawing?.validate()
            require(draft.content.body.length <= if (draft.content.threadId == null) 1000 else 500) { "Your note is too long" }
            require(draft.content.body.isNotBlank() || draft.content.drawing != null) { "Write a note or draw something first" }
            if (draft.pendingPublishId != null) draft else {
                val id = newBoardId()
                val args = JsonObject(draft.content.boardEncode().jsonObject + boardArgs("id" to newBoardId().boardValue(),
                    "board_id" to draft.boardId.boardValue(), "draft_revision_id" to draft.revisionId.boardValue(),
                    "draft_base_id" to draft.cloudBaseId?.boardValue(), "draft_id" to draft.cloudDraftId.boardValue(), "kind" to draft.content.brandingKind?.boardValue()))
                draft.copy(pendingPublishId = id, pendingPublishArgs = args).also { save(accountId, it) }
            }
        }
        try {
            val result = api.mutate(accountId, if(pending.content.brandingKind != null) "draw_branding" else "publish", requireNotNull(pending.pendingPublishArgs), requireNotNull(pending.pendingPublishId))
            draftMutex.withLock { dao.deleteDraft(accountId, draftId) }
            result
        } catch (e: BoardFailure) {
            if (!e.retryable) draftMutex.withLock { save(accountId, pending.copy(pendingPublishId = null, pendingPublishArgs = null)) }
            throw e
        }
    }
    override suspend fun clearContentCache(accountId: String) { dao.invalidateAccount(accountId) }
}
