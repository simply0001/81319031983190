package com.pocketpass.app.boards

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "board_records", primaryKeys = ["accountId", "kind", "recordId"], indices = [Index(value = ["accountId", "boardId"])])
data class BoardRecordEntity(val accountId: String, val kind: String, val recordId: String, val boardId: String, val payload: String)

@Entity(tableName = "board_drafts", primaryKeys = ["accountId", "draftId"])
data class BoardDraftEntity(val accountId: String, val draftId: String, val boardId: String, val payload: String, val updatedAt: Long)

@Dao
interface BoardDao {
    @Query("SELECT * FROM board_drafts WHERE accountId = :accountId ORDER BY updatedAt DESC")
    fun observeDrafts(accountId: String): Flow<List<BoardDraftEntity>>
    @Query("SELECT * FROM board_drafts WHERE accountId = :accountId ORDER BY updatedAt DESC")
    suspend fun drafts(accountId: String): List<BoardDraftEntity>
    @Query("SELECT * FROM board_drafts WHERE accountId = :accountId AND draftId = :draftId")
    suspend fun draft(accountId: String, draftId: String): BoardDraftEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: BoardDraftEntity)
    @Query("DELETE FROM board_drafts WHERE accountId = :accountId AND draftId = :draftId")
    suspend fun deleteDraft(accountId: String, draftId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cache(record: BoardRecordEntity)
    @Query("DELETE FROM board_records WHERE accountId = :accountId AND boardId = :boardId")
    suspend fun invalidateBoard(accountId: String, boardId: String)
    @Query("DELETE FROM board_records WHERE accountId = :accountId")
    suspend fun invalidateAccount(accountId: String)
}
