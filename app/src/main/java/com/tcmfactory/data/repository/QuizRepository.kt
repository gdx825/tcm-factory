package com.tcmfactory.data.repository

import androidx.room.withTransaction
import com.tcmfactory.core.common.DayBoundary
import com.tcmfactory.core.database.TcmDatabase
import com.tcmfactory.core.database.dao.QuizDao
import com.tcmfactory.core.database.dao.StatsDao
import com.tcmfactory.core.database.entity.QuestionEntity
import com.tcmfactory.core.database.entity.QuizRecordEntity
import com.tcmfactory.core.database.entity.QuizSessionEntity
import com.tcmfactory.core.database.entity.WrongQuestionEntity
import com.tcmfactory.core.quiz.QuestionPackParser
import com.tcmfactory.domain.model.Question
import com.tcmfactory.domain.model.QuizMode
import com.tcmfactory.domain.model.SubjectQuestionCount
import com.tcmfactory.domain.model.WrongQuestion
import com.tcmfactory.domain.model.encodeIntList
import com.tcmfactory.domain.model.encodeStringList
import com.tcmfactory.domain.model.parseTags
import com.tcmfactory.domain.model.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 一次会话的答题明细，用于结果页回顾。 */
data class AnswerRecord(
    val question: Question,
    val userAnswers: List<Int>,
    val isCorrect: Boolean,
    val elapsedMs: Long
)

data class SessionResult(
    val sessionId: Long,
    val records: List<AnswerRecord>
) {
    val total: Int get() = records.size
    val correct: Int get() = records.count { it.isCorrect }
    val wrong: Int get() = total - correct
    val accuracy: Int get() = if (total == 0) 0 else correct * 100 / total
    val totalMs: Long get() = records.sumOf { it.elapsedMs }
    val wrongRecords: List<AnswerRecord> get() = records.filter { !it.isCorrect }
}

data class ImportSummary(
    val packName: String,
    val subject: String,
    val imported: Int,
    val rejected: List<QuestionPackParser.Rejection>
) {
    val rejectedCount: Int get() = rejected.size
}

@Singleton
class QuizRepository @Inject constructor(
    private val db: TcmDatabase,
    private val quizDao: QuizDao,
    private val statsDao: StatsDao
) {

    /** 连续答对多少次后自动移出错题本。 */
    private companion object {
        const val MASTER_THRESHOLD = 2
        const val DEFAULT_SESSION_SIZE = 20

        /**
         * 早期版本内置题库的 packId（当时直接用了 packName）。
         * 这批题目现已全部迁入知识包，此列表仅用于一次性清理。
         */
        val LEGACY_BUILTIN_PACKS = listOf(
            "中药学 · 解表药与清热药",
            "方剂学 · 常用方剂组成与主治",
            "针灸学 · 常用腧穴定位",
            "我的题库"
        )
    }

    // ---------------- 内置题库 ----------------

    /**
     * 清理早期版本直接写入数据库的内置题库。
     *
     * 背景：内置题库原先是 SeedQuestionPacks 里的 Kotlin 字符串，直接调 [importPack] 写入，
     * packId 用的是中文包名；现已统一迁移到知识包（tcm.quiz.core / tcm.quiz.internal），
     * 走 [KnowledgeRepository.installFromBytes] 通道分发。
     *
     * 不清理会有两个后果：
     *  1. 同一道题在题库里出现两次（新旧通道各一条）；
     *  2. **旧记录的内容可能是错的**——早期解析正则会误把比例类选项 `1:1` 剥成 `1`，
     *     这些错误文本已经落库，只能删掉、由知识包重新导入正确内容。
     *
     * 天然幂等：没有旧数据时返回 0。
     *
     * @return 实际删除的题目数
     */
    suspend fun purgeLegacyBuiltinQuestions(): Int =
        quizDao.deleteByPackIds(LEGACY_BUILTIN_PACKS)

    // ---------------- 导入 ----------------

    suspend fun importPack(raw: String): ImportSummary {
        val outcome = QuestionPackParser.parse(raw).getOrThrow()
        val now = System.currentTimeMillis()

        val entities = outcome.accepted.map { q ->
            QuestionEntity(
                subject = outcome.subject,
                chapter = q.chapter,
                type = q.type.name,
                stem = q.stem,
                optionsJson = encodeStringList(q.options),
                answerJson = encodeIntList(q.answers),
                explanation = q.explanation,
                difficulty = q.difficulty,
                sourceRef = q.sourceRef ?: outcome.sourceRef,
                tags = encodeStringList(q.tags),
                packId = outcome.packName,
                createdAt = now
            )
        }
        if (entities.isNotEmpty()) {
            quizDao.insertQuestions(entities)
        }

        return ImportSummary(
            packName = outcome.packName,
            subject = outcome.subject,
            imported = entities.size,
            rejected = outcome.rejected
        )
    }

    // ---------------- 观察 ----------------

    fun observeSubjectCounts(): Flow<List<SubjectQuestionCount>> =
        quizDao.observeSubjectCounts().map { rows ->
            rows.map { SubjectQuestionCount(it.subject, it.count) }
        }

    fun observeWrongCount(): Flow<Int> = quizDao.observeWrongCount()

    fun observeChapters(subject: String): Flow<List<String>> =
        quizDao.observeChapters(subject)

    suspend fun questionCount(): Int = quizDao.countQuestions()

    // ---------------- 组卷 ----------------

    suspend fun buildSession(
        mode: QuizMode,
        subject: String? = null,
        chapter: String? = null,
        count: Int = DEFAULT_SESSION_SIZE
    ): List<Question> {
        val entities = when (mode) {
            QuizMode.RANDOM -> quizDao.getRandom(count)

            QuizMode.CHAPTER -> if (subject != null && chapter != null) {
                quizDao.getByChapter(subject, chapter).shuffled().take(count)
            } else emptyList()

            QuizMode.WRONG -> quizDao.getWrongQuestionsWithDetail().take(count)

            QuizMode.SEQUENTIAL -> if (subject != null) {
                quizDao.getBySubject(subject, count, 0)
            } else {
                quizDao.getRandom(count)
            }
        }
        return entities.map { it.toDomain() }
    }

    // ---------------- 会话 ----------------

    suspend fun startSession(
        mode: QuizMode,
        subject: String?,
        chapter: String?,
        totalCount: Int,
        now: Long = System.currentTimeMillis()
    ): Long = quizDao.insertSession(
        QuizSessionEntity(
            mode = mode.name,
            subject = subject,
            chapter = chapter,
            startedAt = now,
            totalCount = totalCount
        )
    )

    suspend fun finishSession(sessionId: Long, result: SessionResult, now: Long = System.currentTimeMillis()) {
        quizDao.finishSession(
            id = sessionId,
            finishedAt = now,
            total = result.total,
            correct = result.correct,
            elapsed = result.totalMs
        )
    }

    /**
     * 提交一道题的作答。
     *
     * 错题本策略：答错则归集并重置连对计数；答对则累加连对，
     * 连续答对 MASTER_THRESHOLD 次自动标记为已掌握（移出活跃错题列表）。
     * 这个策略比「答对一次就移出」温和，也比「永不移出」实用。
     */
    suspend fun submitAnswer(
        sessionId: Long,
        question: Question,
        userAnswers: Collection<Int>,
        elapsedMs: Long,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val isCorrect = question.isCorrect(userAnswers)

        db.withTransaction {
            quizDao.insertRecord(
                QuizRecordEntity(
                    sessionId = sessionId,
                    questionId = question.id,
                    userAnswerJson = encodeIntList(userAnswers.sorted()),
                    isCorrect = isCorrect,
                    answeredAt = now,
                    elapsedMs = elapsedMs
                )
            )

            val existing = quizDao.getWrongQuestion(question.id)
            if (isCorrect) {
                if (existing != null && existing.status == WrongQuestionEntity.STATUS_ACTIVE) {
                    val streak = existing.correctStreak + 1
                    if (streak >= MASTER_THRESHOLD) {
                        quizDao.setWrongStatus(question.id, WrongQuestionEntity.STATUS_MASTERED)
                    } else {
                        quizDao.upsertWrongQuestion(existing.copy(correctStreak = streak))
                    }
                }
            } else {
                quizDao.upsertWrongQuestion(
                    existing?.copy(
                        wrongCount = existing.wrongCount + 1,
                        correctStreak = 0,
                        lastWrongAt = now,
                        status = WrongQuestionEntity.STATUS_ACTIVE
                    ) ?: WrongQuestionEntity(
                        questionId = question.id,
                        wrongCount = 1,
                        correctStreak = 0,
                        lastWrongAt = now,
                        status = WrongQuestionEntity.STATUS_ACTIVE,
                        addedAt = now
                    )
                )
            }

            statsDao.bump(
                date = DayBoundary.dateKey(now),
                quizCount = 1,
                correctCount = if (isCorrect) 1 else 0
            )
        }

        return isCorrect
    }

    // ---------------- 错题本 ----------------

    suspend fun getWrongQuestions(): List<Question> {
        val entities = quizDao.getWrongQuestionsWithDetail()
        val wrongMap = quizDao.getWrongList()
            .filter { it.status == WrongQuestionEntity.STATUS_ACTIVE }
            .associateBy { it.questionId }
        return entities.map { it.toDomain(wrongMap[it.id]?.wrongCount ?: 0) }
    }

    suspend fun removeFromWrong(questionId: Long) =
        quizDao.setWrongStatus(questionId, WrongQuestionEntity.STATUS_REMOVED)

    suspend fun restoreToWrong(questionId: Long) =
        quizDao.setWrongStatus(questionId, WrongQuestionEntity.STATUS_ACTIVE)

    // ---------------- 统计 ----------------

    /** 某科目的章节 → 题目数，用于章节练习选择。 */
    suspend fun chapterCounts(subject: String): List<Pair<String, Int>> =
        quizDao.getChapterCounts(subject).map { it.chapter to it.cnt }
}
