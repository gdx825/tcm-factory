package com.tcmfactory.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tcmfactory.core.database.entity.CardEntity
import com.tcmfactory.core.database.entity.KnowledgePackVersionEntity
import com.tcmfactory.core.database.entity.QuestionEntity
import kotlinx.coroutines.flow.Flow

/** 牌组精简信息，仅用于补挂 externalId。 */
data class DeckBrief(
    val id: Long,
    val name: String,
    val subject: String,
    /** 内置牌组（种子数据创建）。补挂只针对这些牌组下的内容。 */
    val isBuiltin: Boolean
)

/**
 * 知识库更新专用 DAO。
 *
 * 核心约束：**内容更新必须原地修改，不能删除重建**。因为
 *  · 卡片行上挂着用户的 SRS 学习进度（等级 / 到期时间 / EF / 遗忘次数）；
 *  · 题目行被 quiz_records 与 wrong_questions 通过 questionId 引用。
 * 一旦删了重建，学习进度清零、错题本与答题记录变成孤儿数据。
 * 所以这里只提供「按 (packId, externalId) 定位 → 更新内容字段」的写入路径。
 */
@Dao
interface KnowledgeDao {

    // ================= 包版本记录 =================

    @Query("SELECT * FROM knowledge_pack_versions ORDER BY installedAt DESC")
    fun observeAllPacks(): Flow<List<KnowledgePackVersionEntity>>

    @Query("SELECT * FROM knowledge_pack_versions")
    suspend fun getAllPacks(): List<KnowledgePackVersionEntity>

    @Query("SELECT * FROM knowledge_pack_versions WHERE packId = :packId LIMIT 1")
    suspend fun getPack(packId: String): KnowledgePackVersionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPack(entity: KnowledgePackVersionEntity)

    @Query("DELETE FROM knowledge_pack_versions WHERE packId = :packId")
    suspend fun deletePack(packId: String)

    // ================= 卡片 =================

    /** 牌组名，用于为历史数据补挂 externalId。 */
    @Query("SELECT name FROM decks WHERE id = :deckId LIMIT 1")
    suspend fun deckNameOf(deckId: Long): String?

    /** 一次性取出全部牌组，供升级后批量补挂使用（避免 N+1 查询）。 */
    @Query("SELECT id, name, subject, isBuiltin FROM decks")
    suspend fun allDecks(): List<DeckBrief>

    @Query("SELECT id FROM decks WHERE name = :name LIMIT 1")
    suspend fun findDeckIdByName(name: String): Long?

    @Query("SELECT * FROM cards WHERE packId IS NULL")
    suspend fun getCardsWithoutPack(): List<CardEntity>

    @Query("SELECT id FROM cards WHERE packId = :packId AND externalId = :externalId LIMIT 1")
    suspend fun findCardId(packId: String, externalId: String): Long?

    @Query("UPDATE cards SET packId = :packId, externalId = :externalId WHERE id = :cardId")
    suspend fun attachCardToPack(cardId: Long, packId: String, externalId: String)

    /** 只更新内容字段，刻意不触碰任何 srs* 列。 */
    @Query(
        """
        UPDATE cards SET
            front = :front,
            back = :back,
            extra = :extra,
            tags = :tags,
            sourceRef = :sourceRef,
            type = :type,
            updatedAt = :now
        WHERE id = :cardId
        """
    )
    suspend fun updateCardContent(
        cardId: Long,
        front: String,
        back: String,
        extra: String?,
        tags: String?,
        sourceRef: String?,
        type: String,
        now: Long
    )

    @Query("UPDATE cards SET deckId = :deckId WHERE id = :cardId")
    suspend fun moveCardToDeck(cardId: Long, deckId: Long)

    @Query("SELECT COUNT(*) FROM cards WHERE packId = :packId")
    suspend fun countCardsOfPack(packId: String): Int

    @Query("DELETE FROM cards WHERE packId = :packId")
    suspend fun deleteCardsOfPack(packId: String)

    // ================= 题目 =================

    @Query("SELECT * FROM questions WHERE packId IS NULL")
    suspend fun getQuestionsWithoutPack(): List<QuestionEntity>

    /**
     * 仅在确认题目不存在时调用。
     *
     * 用 IGNORE 而非 REPLACE：REPLACE 的语义是「删掉冲突行再插入」，
     * 主键会随之变化。而 quiz_records 与 wrong_questions 是靠 questionId
     * 关联的 —— 一旦换了 id，用户的答题记录与错题本会变成孤儿数据。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQuestion(entity: QuestionEntity): Long

    @Query("SELECT id FROM questions WHERE packId = :packId AND externalId = :externalId LIMIT 1")
    suspend fun findQuestionId(packId: String, externalId: String): Long?

    @Query("UPDATE questions SET packId = :packId, externalId = :externalId WHERE id = :id")
    suspend fun attachQuestionToPack(id: Long, packId: String, externalId: String)

    /** 同样只更新内容，不动 id —— 保住 quiz_records / wrong_questions 的外键关联。 */
    @Query(
        """
        UPDATE questions SET
            subject = :subject,
            chapter = :chapter,
            type = :type,
            stem = :stem,
            optionsJson = :optionsJson,
            answerJson = :answerJson,
            explanation = :explanation,
            difficulty = :difficulty,
            sourceRef = :sourceRef,
            tags = :tags,
            packId = :packId
        WHERE id = :id
        """
    )
    suspend fun updateQuestionContent(
        id: Long,
        subject: String,
        chapter: String?,
        type: String,
        stem: String,
        optionsJson: String?,
        answerJson: String,
        explanation: String?,
        difficulty: Int,
        sourceRef: String?,
        tags: String?,
        packId: String
    )

    @Query("SELECT COUNT(*) FROM questions WHERE packId = :packId")
    suspend fun countQuestionsOfPack(packId: String): Int

    @Query("DELETE FROM questions WHERE packId = :packId")
    suspend fun deleteQuestionsOfPack(packId: String)

    // ================= 统计 =================

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun totalCards(): Int

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun totalQuestions(): Int
}
