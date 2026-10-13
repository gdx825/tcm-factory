package com.tcmfactory.domain.model

import com.tcmfactory.core.database.dao.DeckSummaryRow
import com.tcmfactory.core.database.entity.CardEntity
import com.tcmfactory.core.database.entity.DeckEntity
import com.tcmfactory.core.srs.SrsRating
import com.tcmfactory.core.srs.SrsSnapshot
import com.tcmfactory.core.srs.SrsState
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** 领域层卡片模型。UI 与 UseCase 只依赖它，不直接接触 Room Entity。 */
data class Card(
    val id: Long,
    val deckId: Long,
    val deckName: String,
    val type: String,
    val front: String,
    val back: String,
    val extra: String?,
    val tags: List<String>,
    val sourceRef: String?,
    val note: String?,
    val isSuspended: Boolean,
    /** 古籍溯源三列，非空表示这张卡是从古籍原文制出来的，可反向跳转 */
    val sourceBookId: Long? = null,
    val sourceCharStart: Int? = null,
    val sourceCharEnd: Int? = null,
    val srs: SrsSnapshot
) {
    val isNew: Boolean get() = srs.state == SrsState.NEW
    val isLearning: Boolean get() = srs.state == SrsState.LEARNING || srs.state == SrsState.RELEARNING
    val isInReview: Boolean get() = srs.state == SrsState.REVIEW
    val isCloze: Boolean get() = type == "CLOZE"
    val isHard: Boolean get() = srs.lapses >= 3
}

data class Deck(
    val id: Long,
    val name: String,
    val subject: String,
    val parentId: Long?,
    val sortOrder: Int,
    val isBuiltin: Boolean
)

/** 首页牌组卡片所需的聚合数据。 */
data class DeckSummary(
    val deckId: Long,
    val name: String,
    val subject: String,
    val newCount: Int,
    val dueCount: Int,
    val totalCount: Int
) {
    /** 已进入长期记忆的比例，用于牌组进度条。 */
    val learnedCount: Int get() = (totalCount - newCount).coerceAtLeast(0)

    val progress: Float
        get() = if (totalCount <= 0) 0f else (learnedCount.toFloat() / totalCount).coerceIn(0f, 1f)

    /** 今日是否还有待办。 */
    val hasWork: Boolean get() = newCount > 0 || dueCount > 0
}

/** 一次背诵会话的队列。 */
data class StudyQueue(
    val cards: List<Card>,
    val newCount: Int,
    val reviewCount: Int,
    val learningCount: Int
) {
    val isEmpty: Boolean get() = cards.isEmpty()
    val total: Int get() = cards.size
    fun remaining(index: Int): Int = (cards.size - index).coerceAtLeast(0)

    companion object {
        val EMPTY = StudyQueue(emptyList(), 0, 0, 0)
    }
}

/** 今日进度。 */
data class TodayProgress(
    val newDone: Int,
    val newTarget: Int,
    val reviewDone: Int,
    val reviewTarget: Int,
    val forgottenToday: Int,
    /** 当前到期待办数量（实时查询所得，比「已完成数」更能反映真实工作量）。 */
    val dueNow: Int
) {
    val totalDone: Int get() = newDone + reviewDone
    val totalTarget: Int get() = newTarget + reviewTarget

    val ratio: Float
        get() = when {
            totalTarget <= 0 && dueNow == 0 -> 1f
            totalTarget <= 0 -> 0f
            else -> (totalDone.toFloat() / totalTarget).coerceIn(0f, 1f)
        }

    val ratioText: String get() = "${(ratio * 100).toInt()}%"

    /** 粗略估算剩余学习时长（秒）。平均单卡约 8 秒。 */
    val estimatedSecondsLeft: Int get() = dueNow * 8

    companion object {
        val EMPTY = TodayProgress(0, 0, 0, 0, 0, 0)
    }
}

/** 一次评分的执行结果，用于界面即时反馈。 */
data class ReviewOutcome(
    val cardId: Long,
    val rating: SrsRating,
    val nextIntervalMin: Double,
    val nextDueText: String,
    val isNewLearned: Boolean
)

/** 一次会话完成后的统计。 */
data class SessionStats(
    val reviewed: Int,
    val remembered: Int,
    val forgotten: Int,
    val elapsedMs: Long,
    val nextDueCount: Int
) {
    val accuracy: Int
        get() = if (reviewed <= 0) 0 else (remembered * 100 / reviewed)

    val averageMsPerCard: Long
        get() = if (reviewed <= 0) 0 else elapsedMs / reviewed
}

private val json = Json { ignoreUnknownKeys = true }

fun CardEntity.toDomain(deckName: String = ""): Card = Card(
    id = id,
    deckId = deckId,
    deckName = deckName,
    type = type,
    front = front,
    back = back,
    extra = extra,
    tags = parseTags(tags),
    sourceRef = sourceRef,
    note = note,
    isSuspended = isSuspended,
    sourceBookId = sourceBookId,
    sourceCharStart = sourceCharStart,
    sourceCharEnd = sourceCharEnd,
    srs = SrsSnapshot(
        cardId = id,
        state = runCatching { SrsState.valueOf(srsState) }.getOrDefault(SrsState.NEW),
        level = srsLevel,
        ease = srsEase,
        intervalMin = srsIntervalMin,
        dueAt = srsDueAt,
        lastReviewAt = srsLastReviewAt,
        reps = srsReps,
        lapses = srsLapses
    )
)

fun DeckEntity.toDomain(): Deck = Deck(
    id = id,
    name = name,
    subject = subject,
    parentId = parentId,
    sortOrder = sortOrder,
    isBuiltin = isBuiltin
)

fun DeckSummaryRow.toDomain(): DeckSummary = DeckSummary(
    deckId = deckId,
    name = name,
    subject = subject,
    newCount = newCount,
    dueCount = dueCount,
    totalCount = totalCount
)

fun parseTags(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
}

fun encodeTags(tags: List<String>): String? {
    if (tags.isEmpty()) return null
    // 显式传入 serializer，避免 reified 扩展在 Json 成员重载间产生歧义
    return json.encodeToString(ListSerializer(String.serializer()), tags)
}

/** 科目枚举 → 中文名。 */
fun subjectLabel(subject: String): String = when (subject) {
    "TCM_BASIC" -> "中医基础理论"
    "TCM_DIAG" -> "中医诊断学"
    "MATERIA_MEDICA" -> "中药学"
    "FORMULA" -> "方剂学"
    "INTERNAL_MED" -> "中医内科学"
    "ACUPUNCTURE" -> "针灸学"
    "CLASSICS" -> "中医经典"
    "CUSTOM" -> "自定义"
    // 未知科目**原样返回**，而不是一律塌缩成「其他」。
    // 导入外部题库时，JSON 里声明的科目名（如「中医基础理论」）或用户自填的名称
    // 都需要正确显示；否则几十个不同科目会全部挤在「其他」里无法区分。
    else -> subject.trim().ifEmpty { "其他" }
}
