package com.tcmfactory.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tcmfactory.core.database.entity.CardEntity
import com.tcmfactory.core.database.entity.DeckEntity
import kotlinx.coroutines.flow.Flow

/** 牌组总览行（联表聚合，供首页展示）。 */
data class DeckSummaryRow(
    val deckId: Long,
    val name: String,
    val subject: String,
    val sortOrder: Int,
    val newCount: Int,
    val dueCount: Int,
    val totalCount: Int
)

@Dao
interface DeckDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deck: DeckEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(deck: DeckEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(decks: List<DeckEntity>): List<Long>

    @Query("SELECT * FROM decks ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<DeckEntity>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getById(id: Long): DeckEntity?

    @Query("SELECT * FROM decks WHERE name = :name AND parentId IS :parentId LIMIT 1")
    suspend fun findByNameAndParent(name: String, parentId: Long?): DeckEntity?

    @Query("SELECT COUNT(*) FROM decks")
    suspend fun count(): Int

    /**
     * 首页牌组总览。
     * dueCount 把「学习期卡片」与「已到期的复习卡」合并统计，因为它们都要今天清空。
     */
    @Query(
        """
        SELECT d.id AS deckId,
               d.name AS name,
               d.subject AS subject,
               d.sortOrder AS sortOrder,
               COALESCE(SUM(CASE WHEN c.srsState = 'NEW' THEN 1 ELSE 0 END), 0) AS newCount,
               COALESCE(SUM(CASE WHEN c.srsState IN ('LEARNING', 'RELEARNING')
                                   OR (c.srsState = 'REVIEW' AND c.srsDueAt <= :now)
                                 THEN 1 ELSE 0 END), 0) AS dueCount,
               COALESCE(COUNT(c.id), 0) AS totalCount
        FROM decks d
        LEFT JOIN cards c ON c.deckId = d.id AND c.isSuspended = 0
        WHERE d.parentId IS NULL
        GROUP BY d.id, d.name, d.subject, d.sortOrder
        ORDER BY d.sortOrder ASC, d.id ASC
        """
    )
    fun observeDeckSummaries(now: Long): Flow<List<DeckSummaryRow>>

    @Query("DELETE FROM decks WHERE id = :id AND isBuiltin = 0")
    suspend fun deleteUserDeck(id: Long)
}

@Dao
interface CardDao {

    // ---------------- 写入 ----------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: CardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<CardEntity>): List<Long>

    @Update
    suspend fun update(card: CardEntity)

    @Query("DELETE FROM cards WHERE id = :cardId")
    suspend fun deleteById(cardId: Long)

    @Query("DELETE FROM cards WHERE deckId = :deckId")
    suspend fun deleteByDeck(deckId: Long)

    @Query("UPDATE cards SET isSuspended = :suspended, updatedAt = :now WHERE id = :cardId")
    suspend fun setSuspended(cardId: Long, suspended: Boolean, now: Long)

    /** 重置单卡进度：退回新卡，保留内容。 */
    @Query(
        """
        UPDATE cards SET
            srsState = 'NEW', srsLevel = 0, srsEase = 2.5, srsIntervalMin = 0,
            srsDueAt = :now, srsLastReviewAt = NULL, srsReps = 0, srsLapses = 0,
            updatedAt = :now
        WHERE id = :cardId
        """
    )
    suspend fun resetProgress(cardId: Long, now: Long)

    /** 把调度结果写回卡片。这是滑动提交时唯一的热路径写入。 */
    @Query(
        """
        UPDATE cards SET
            srsState = :state, srsLevel = :level, srsEase = :ease,
            srsIntervalMin = :intervalMin, srsDueAt = :dueAt,
            srsLastReviewAt = :lastReviewAt, srsReps = :reps, srsLapses = :lapses,
            updatedAt = :now
        WHERE id = :cardId
        """
    )
    suspend fun updateSrs(
        cardId: Long,
        state: String,
        level: Int,
        ease: Double,
        intervalMin: Double,
        dueAt: Long,
        lastReviewAt: Long?,
        reps: Int,
        lapses: Int,
        now: Long
    )

    // ---------------- 队列组装（实时查询，不预生成任务表） ----------------

    /** 1) 学习期卡片：不受每日上限约束，必须当日清空。 */
    @Query(
        """
        SELECT * FROM cards
        WHERE isSuspended = 0 AND srsState IN ('LEARNING', 'RELEARNING')
        ORDER BY srsDueAt ASC
        """
    )
    suspend fun learningQueue(): List<CardEntity>

    /** 2) 到期复习卡（全部牌组）。逾期越久越靠前。 */
    @Query(
        """
        SELECT * FROM cards
        WHERE isSuspended = 0 AND srsState = 'REVIEW' AND srsDueAt <= :now
        ORDER BY srsDueAt ASC
        LIMIT :limit
        """
    )
    suspend fun dueReviewQueueAll(now: Long, limit: Int): List<CardEntity>

    /** 2') 到期复习卡（指定牌组）。 */
    @Query(
        """
        SELECT * FROM cards
        WHERE isSuspended = 0 AND srsState = 'REVIEW' AND srsDueAt <= :now
          AND deckId IN (:deckIds)
        ORDER BY srsDueAt ASC
        LIMIT :limit
        """
    )
    suspend fun dueReviewQueueInDecks(now: Long, deckIds: List<Long>, limit: Int): List<CardEntity>

    /** 3) 新卡（全部牌组）。 */
    @Query(
        """
        SELECT * FROM cards
        WHERE isSuspended = 0 AND srsState = 'NEW'
        ORDER BY deckId ASC, sortOrder ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun newQueueAll(limit: Int): List<CardEntity>

    /** 3') 新卡（指定牌组）。 */
    @Query(
        """
        SELECT * FROM cards
        WHERE isSuspended = 0 AND srsState = 'NEW'
          AND deckId IN (:deckIds)
        ORDER BY deckId ASC, sortOrder ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun newQueueInDecks(deckIds: List<Long>, limit: Int): List<CardEntity>

    // ---------------- 计数 ----------------

    @Query("SELECT COUNT(*) FROM cards WHERE isSuspended = 0 AND srsState = 'NEW'")
    fun observeNewCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM cards
        WHERE isSuspended = 0 AND (srsState IN ('LEARNING', 'RELEARNING')
              OR (srsState = 'REVIEW' AND srsDueAt <= :now))
        """
    )
    fun observeDueCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards WHERE isSuspended = 0")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards WHERE isSuspended = 0 AND srsState = :state")
    suspend fun countByState(state: String): Int

    // ---------------- 卡片管理 ----------------

    @Query(
        """
        SELECT * FROM cards
        WHERE (:deckId IS NULL OR deckId = :deckId)
          AND (:state IS NULL OR srsState = :state)
          AND (:keyword IS NULL OR front LIKE '%' || :keyword || '%' OR back LIKE '%' || :keyword || '%')
        ORDER BY deckId ASC, sortOrder ASC, id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun queryManaged(
        deckId: Long?,
        state: String?,
        keyword: String?,
        limit: Int,
        offset: Int
    ): List<CardEntity>

    @Query("SELECT * FROM cards WHERE id = :cardId")
    suspend fun getById(cardId: Long): CardEntity?

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY sortOrder ASC, id ASC")
    suspend fun getByDeck(deckId: Long): List<CardEntity>

    @Query("SELECT * FROM cards WHERE srsLapses >= :minLapses AND isSuspended = 0 ORDER BY srsLapses DESC")
    suspend fun getHardCards(minLapses: Int): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId")
    suspend fun countInDeck(deckId: Long): Int

    /** 导出用：全量卡片。 */
    @Query("SELECT * FROM cards ORDER BY deckId ASC, sortOrder ASC, id ASC")
    suspend fun getAllForExport(): List<CardEntity>
}
