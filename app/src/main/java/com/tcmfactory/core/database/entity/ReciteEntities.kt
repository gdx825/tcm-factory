package com.tcmfactory.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 牌组 / 科目。
 * subject 枚举严格对齐 307 中医综合的科目划分。
 */
@Entity(
    tableName = "decks",
    indices = [Index("parentId"), Index("subject")]
)
data class DeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** TCM_BASIC / TCM_DIAG / MATERIA_MEDICA / FORMULA / INTERNAL_MED / ACUPUNCTURE / CLASSICS / CUSTOM */
    val subject: String,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
    val isBuiltin: Boolean = false,
    val createdAt: Long
)

/**
 * 背诵卡片。SRS 状态内联在本表，不单独建 1:1 表——避免 JOIN 拖慢每次滑动的写入路径。
 *
 * sourceBookId / sourceCharStart / sourceCharEnd 三个溯源列在**第一阶段就建好**，
 * 供第二阶段「读经制卡 → 反向跳回原文」闭环使用。现在建好可避免日后写 Migration。
 */
@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("deckId"),
        Index(value = ["srsState", "srsDueAt"]),
        Index(value = ["deckId", "sortOrder"]),
        Index("sourceBookId"),
        // 知识库更新时按 (packId, externalId) 做幂等 upsert：
        // 内容更新只改 front/back/extra，用户的 SRS 进度原样保留。
        Index(value = ["packId", "externalId"], name = "index_cards_pack_external")
    ]
)
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    /** BASIC / CLOZE / QA / ACUPOINT */
    val type: String = TYPE_BASIC,
    /** 正面（题干）。CLOZE 类型使用 {{c1::...}} 语法。 */
    val front: String,
    val back: String,
    /** 补充说明、鉴别要点等 */
    val extra: String? = null,
    /** JSON 数组字符串，如 ["高频","易混淆"] */
    val tags: String? = null,
    /** 权威出处，如 GB/T 12346-2021 §5.1 */
    val sourceRef: String? = null,
    val sortOrder: Int = 0,
    val isSuspended: Boolean = false,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,

    // ---------- 内联 SRS 状态 ----------

    /** NEW / LEARNING / REVIEW / RELEARNING */
    val srsState: String = "NEW",
    /** 巩固等级 0..11，指向阶梯表索引 */
    val srsLevel: Int = 0,
    /** 难度因子 EF，钳制在 [1.3, 2.8] */
    val srsEase: Double = 2.5,
    /** 当前间隔（分钟） */
    val srsIntervalMin: Double = 0.0,
    /** 下次到期时间戳（epoch millis） */
    val srsDueAt: Long = 0L,
    val srsLastReviewAt: Long? = null,
    val srsReps: Int = 0,
    val srsLapses: Int = 0,

    // ---------- 古籍溯源（第二阶段闭环） ----------

    val sourceBookId: Long? = null,
    val sourceCharStart: Int? = null,
    val sourceCharEnd: Int? = null,

    // ---------- 知识库溯源（第四阶段联网更新） ----------

    /**
     * 所属知识包标识。内置内容为 tcm.baseline.* ，联网更新内容为数据源声明的 packId。
     * 为空表示用户手工创建的卡片（不会被任何更新覆盖）。
     */
    val packId: String? = null,
    /**
     * 包内稳定标识。更新时据此匹配已有卡片，**保证只换内容不动学习进度**。
     * 由 (牌组名 + 正面文本) 派生，因此内容相同则跨版本稳定。
     */
    val externalId: String? = null
) {
    companion object {
        const val TYPE_BASIC = "BASIC"
        const val TYPE_CLOZE = "CLOZE"
        const val TYPE_QA = "QA"
        const val TYPE_ACUPOINT = "ACUPOINT"
    }
}

/**
 * 复习日志：只增不改不删。
 * 是一切数据洞察（正确率曲线、算法调优）的唯一数据源，也是「撤销上一步」的实现基础。
 */
@Entity(
    tableName = "review_logs",
    indices = [
        Index("reviewedAt"),
        Index(value = ["cardId", "reviewedAt"]),
        Index("deckId")
    ]
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val reviewedAt: Long,
    /** AGAIN / HARD / GOOD / EASY */
    val rating: String,
    val prevLevel: Int,
    val nextLevel: Int,
    val prevIntervalMin: Double,
    val nextIntervalMin: Double,
    val srsEaseAfter: Double,
    /** 以下 5 列为完整前状态快照，用于「撤销上一步」做精确回滚 */
    val prevState: String,
    val prevEase: Double,
    val prevDueAt: Long,
    val prevReps: Int,
    val prevLapses: Int,
    val prevLastReviewAt: Long?,
    /** 单卡决策耗时，用于后续剔除「手滑」样本 */
    val elapsedMs: Long,
    /** DAILY / QUIZ / CRAM */
    val mode: String = "DAILY",
    val deckId: Long
)

/**
 * 每日统计。冗余存储以免每次现算，按自定义日界（默认 04:00）切割。
 */
@Entity(tableName = "study_daily_stats")
data class StudyDailyStatsEntity(
    /** yyyy-MM-dd，按学习日边界计算 */
    @PrimaryKey val date: String,
    val newCards: Int = 0,
    val reviewCards: Int = 0,
    val quizCount: Int = 0,
    val correctCount: Int = 0,
    val studyMinutes: Int = 0,
    val remembered: Int = 0,
    val forgotten: Int = 0
)

/**
 * 知识库包版本记录。用于内置知识库的增量更新通道。
 */
@Entity(tableName = "knowledge_pack_versions")
data class KnowledgePackVersionEntity(
    @PrimaryKey val packId: String,
    val packName: String,
    val version: String,
    val installedAt: Long,
    val cardCount: Int = 0,

    // ---------- 第四阶段新增 ----------
    // 全部声明为可空，与 Migration 中的 `ADD COLUMN x TEXT`（不带 NOT NULL/DEFAULT）
    // 严格一致 —— Room 会校验列的可空性与默认值，任何不一致都会在启动时抛异常。

    /** cards / questions / book */
    val kind: String? = null,
    /** BUILTIN / NETWORK / FILE */
    val source: String? = null,
    val sourceUrl: String? = null,
    val sha256: String? = null,
    /** 实际入库条目数（可能与索引声明的 itemCount 不同，因坏条目被跳过） */
    val itemCount: Int? = null,
    val updatedAt: Long? = null,
    val description: String? = null,
    val license: String? = null,
    val subject: String? = null
)
