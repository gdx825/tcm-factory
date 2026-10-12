package com.tcmfactory.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 题目。对应第三阶段刷题模块，第一阶段先建表，避免后续 Migration。
 */
@Entity(
    tableName = "questions",
    indices = [
        Index(value = ["subject", "chapter"]),
        Index("packId"),
        Index("type"),
        // 知识库更新按 (packId, externalId) 幂等 upsert，
        // 已作答记录与错题本通过 questionId 关联，故题目行必须原地更新而非重建。
        Index(value = ["packId", "externalId"], name = "index_questions_pack_external")
    ]
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 同 DeckEntity.subject 枚举 */
    val subject: String,
    val chapter: String? = null,
    /** SINGLE / MULTI / JUDGE / FILL */
    val type: String,
    val stem: String,
    /** JSON 数组字符串，如 ["A. 麻黄","B. 桂枝"] */
    val optionsJson: String? = null,
    /** JSON 字符串，如 "\"A\"" 或 "[\"A\",\"C\"]" */
    val answerJson: String,
    val explanation: String? = null,
    val difficulty: Int = 0,
    val sourceRef: String? = null,
    val tags: String? = null,
    /** 所属题库包标识 */
    val packId: String? = null,
    /** 包内稳定标识，供知识库更新时原地更新题目而不破坏答题记录与错题本关联 */
    val externalId: String? = null,
    val createdAt: Long
)

/**
 * 刷题会话。
 */
@Entity(tableName = "quiz_sessions", indices = [Index("startedAt")])
data class QuizSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** SEQUENTIAL / RANDOM / CHAPTER / WRONG / MOCK */
    val mode: String,
    val subject: String? = null,
    val chapter: String? = null,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val totalCount: Int = 0,
    val correctCount: Int = 0,
    val elapsedMs: Long = 0
)

/**
 * 答题记录。
 */
@Entity(
    tableName = "quiz_records",
    indices = [
        Index("questionId"),
        Index("sessionId"),
        Index("answeredAt")
    ]
)
data class QuizRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val questionId: Long,
    val userAnswerJson: String,
    val isCorrect: Boolean,
    val answeredAt: Long,
    val elapsedMs: Long = 0
)

/**
 * 错题本。correctStreak 用于「连对 N 次自动移出」的温和策略。
 */
@Entity(tableName = "wrong_questions", indices = [Index("status"), Index("lastWrongAt")])
data class WrongQuestionEntity(
    @PrimaryKey val questionId: Long,
    val wrongCount: Int = 1,
    val correctStreak: Int = 0,
    val lastWrongAt: Long,
    /** ACTIVE / MASTERED / REMOVED */
    val status: String = STATUS_ACTIVE,
    val note: String? = null,
    val addedAt: Long
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_MASTERED = "MASTERED"
        const val STATUS_REMOVED = "REMOVED"
    }
}
