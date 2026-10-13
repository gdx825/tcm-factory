package com.tcmfactory.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tcmfactory.core.database.entity.QuestionEntity
import com.tcmfactory.core.database.entity.QuizRecordEntity
import com.tcmfactory.core.database.entity.QuizSessionEntity
import com.tcmfactory.core.database.entity.WrongQuestionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 刷题相关 DAO。第一阶段仅建表与基础访问能力，
 * 完整能力（导入、组卷、错题策略）在第三阶段实现。
 */
@Dao
interface QuizDao {

    // ---------- questions ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>): List<Long>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getQuestion(id: Long): QuestionEntity?

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun countQuestions(): Int

    @Query("SELECT * FROM questions WHERE subject = :subject ORDER BY chapter ASC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getBySubject(subject: String, limit: Int, offset: Int): List<QuestionEntity>

    @Query("SELECT * FROM questions ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandom(limit: Int): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE subject = :subject AND chapter = :chapter ORDER BY id ASC")
    suspend fun getByChapter(subject: String, chapter: String): List<QuestionEntity>

    @Query("SELECT subject, COUNT(*) AS count FROM questions GROUP BY subject")
    fun observeSubjectCounts(): Flow<List<SubjectCountRow>>

    @Query(
        """
        SELECT DISTINCT chapter FROM questions
        WHERE subject = :subject AND chapter IS NOT NULL AND chapter != ''
        ORDER BY chapter ASC
        """
    )
    fun observeChapters(subject: String): Flow<List<String>>

    @Query(
        """
        SELECT chapter, COUNT(*) AS cnt FROM questions
        WHERE subject = :subject AND chapter IS NOT NULL AND chapter != ''
        GROUP BY chapter ORDER BY chapter ASC
        """
    )
    suspend fun getChapterCounts(subject: String): List<ChapterCountRow>

    // ---------- sessions ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: QuizSessionEntity): Long

    @Update
    suspend fun updateSession(session: QuizSessionEntity)

    @Query("SELECT * FROM quiz_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeSessions(limit: Int): Flow<List<QuizSessionEntity>>

    /** 只更新结束信息，避免整行覆盖把 mode / startedAt 抹掉。 */
    @Query(
        """
        UPDATE quiz_sessions SET
            finishedAt = :finishedAt, totalCount = :total,
            correctCount = :correct, elapsedMs = :elapsed
        WHERE id = :id
        """
    )
    suspend fun finishSession(id: Long, finishedAt: Long, total: Int, correct: Int, elapsed: Long)

    // ---------- records ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: QuizRecordEntity): Long

    @Query("SELECT * FROM quiz_records WHERE sessionId = :sessionId ORDER BY answeredAt ASC")
    suspend fun getRecords(sessionId: Long): List<QuizRecordEntity>

    @Query("SELECT * FROM quiz_records WHERE questionId = :questionId ORDER BY answeredAt DESC")
    suspend fun getRecordsForQuestion(questionId: Long): List<QuizRecordEntity>

    // ---------- wrong book ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWrongQuestion(wrong: WrongQuestionEntity)

    @Query("SELECT * FROM wrong_questions WHERE status = :status ORDER BY lastWrongAt DESC")
    fun observeWrongQuestions(status: String = WrongQuestionEntity.STATUS_ACTIVE): Flow<List<WrongQuestionEntity>>

    @Query("SELECT * FROM wrong_questions WHERE questionId = :questionId")
    suspend fun getWrongQuestion(questionId: Long): WrongQuestionEntity?

    @Query("SELECT * FROM wrong_questions")
    suspend fun getWrongList(): List<WrongQuestionEntity>

    @Query("SELECT COUNT(*) FROM wrong_questions WHERE status = 'ACTIVE'")
    fun observeWrongCount(): Flow<Int>

    @Query("UPDATE wrong_questions SET status = :status WHERE questionId = :questionId")
    suspend fun setWrongStatus(questionId: Long, status: String)

    @Query("DELETE FROM wrong_questions WHERE questionId = :questionId")
    suspend fun deleteWrongQuestion(questionId: Long)

    @Query(
        """
        SELECT q.* FROM questions q
        INNER JOIN wrong_questions w ON w.questionId = q.id
        WHERE w.status = 'ACTIVE'
        ORDER BY w.lastWrongAt DESC
        """
    )
    suspend fun getWrongQuestionsWithDetail(): List<QuestionEntity>

    /**
     * 按题库包标识批量删除题目。
     *
     * 用于清理**早期版本的内置题库**：那批题目由 SeedQuestionPacks 直接写入，
     * packId 取的是中文包名。现在题库已统一迁入知识包，旧记录必须删掉，
     * 否则同一道题会在题库里出现两次。
     *
     * @return 实际删除条数
     */
    @Query("DELETE FROM questions WHERE packId IN (:packIds)")
    suspend fun deleteByPackIds(packIds: List<String>): Int
}

data class SubjectCountRow(val subject: String, val count: Int)

data class ChapterCountRow(val chapter: String, val cnt: Int)
