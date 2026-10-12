package com.tcmfactory.data.repository

import androidx.room.withTransaction
import com.tcmfactory.core.common.DayBoundary
import com.tcmfactory.core.database.TcmDatabase
import com.tcmfactory.core.database.dao.CardDao
import com.tcmfactory.core.database.dao.DeckDao
import com.tcmfactory.core.database.dao.ReviewLogDao
import com.tcmfactory.core.database.dao.StatsDao
import com.tcmfactory.core.database.entity.CardEntity
import com.tcmfactory.core.database.entity.DeckEntity
import com.tcmfactory.core.database.entity.ReviewLogEntity
import com.tcmfactory.core.datastore.SettingsRepository
import com.tcmfactory.core.knowledge.CardItemDto
import com.tcmfactory.core.knowledge.ExternalId
import com.tcmfactory.core.knowledge.KnowledgeJson
import com.tcmfactory.core.knowledge.KnowledgePack
import com.tcmfactory.core.knowledge.PackKind
import com.tcmfactory.core.srs.SrsConfig
import com.tcmfactory.core.srs.SrsRating
import com.tcmfactory.core.srs.SrsState
import com.tcmfactory.core.srs.TcmSrsScheduler
import com.tcmfactory.domain.model.Card
import com.tcmfactory.domain.model.Deck
import com.tcmfactory.domain.model.DeckSummary
import com.tcmfactory.domain.model.ReviewOutcome
import com.tcmfactory.domain.model.StudyQueue
import com.tcmfactory.domain.model.TodayProgress
import com.tcmfactory.domain.model.subjectLabel
import com.tcmfactory.domain.model.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 背诵模块仓库：队列组装、评分提交、撤销、统计。
 *
 * 核心设计：**不预生成「每日任务表」**，而是每次按 dueAt 实时查询组装队列。
 * 这样算法升级后历史数据会自动按新规则重新解释，且不存在任务过期/时区漂移等脏数据问题。
 */
@Singleton
class CardRepository @Inject constructor(
    private val db: TcmDatabase,
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
    private val reviewLogDao: ReviewLogDao,
    private val statsDao: StatsDao,
    private val settingsRepository: SettingsRepository
) {

    /**
     * 把某个牌组的全部卡片导出为知识包 JSON。
     *
     * 刻意使用与「联网更新」**完全相同**的格式，因此导出的文件可以：
     *  · 直接再导入本机或另一台设备（走同一条 installFromBytes 通道）；
     *  · 上传到任意静态托管，作为知识源供他人订阅。
     *
     * 导出的是**内容**而非学习进度：进度是本机状态，换设备后并无意义，
     * 强行带上反而会让用户误以为「导入了就能恢复背到第几天」。
     */
    suspend fun exportDeckAsPackJson(deckId: Long): String = withContext(Dispatchers.IO) {
        val deck = deckDao.getById(deckId) ?: error("牌组不存在或已被删除")
        val cards = cardDao.getByDeck(deckId)
        if (cards.isEmpty()) error("该牌组下还没有卡片")

        val items = cards.map { card ->
            val dto = CardItemDto(
                // 已有 externalId 就沿用；用户自建的卡片没有，按内容派生，
                // 保证同一份内容反复导出导入不会产生重复条目。
                externalId = card.externalId?.takeIf { it.isNotBlank() }
                    ?: ExternalId.of(deck.name, card.front),
                deck = deck.name,
                front = card.front,
                back = card.back,
                extra = card.extra,
                tags = parseTags(card.tags),
                sourceRef = card.sourceRef,
                type = card.type
            )
            KnowledgeJson.encodeToJsonElement(CardItemDto.serializer(), dto)
        }

        val pack = KnowledgePack(
            schemaVersion = 1,
            packId = "tcm.export.deck$deckId",
            packName = "${deck.name}（导出）",
            kind = PackKind.CARDS,
            version = "1.0.0",
            subject = subjectLabel(deck.subject),
            description = "从「中医工厂」导出的卡片包，共 ${cards.size} 张。",
            license = "用户自行导出",
            sourceNote = "内容由用户从本机导出，版权与准确性由导出者负责。",
            items = items
        )
        KnowledgeJson.encodeToString(KnowledgePack.serializer(), pack)
    }

    private fun parseTags(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            KnowledgeJson.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    companion object {
        /** 单次会话的硬上限，防止一次载入过多数据。 */
        private const val MAX_QUEUE_SIZE = 300
        /** 新卡与复习卡的交错比例：每 4 张新卡插 1 张复习卡，避免连续新卡造成认知疲劳。 */
        private const val NEW_PER_REVIEW = 4
    }

    // ---------------- 观察型查询 ----------------

    fun observeDeckSummaries(now: Long): Flow<List<DeckSummary>> =
        deckDao.observeDeckSummaries(now).map { rows -> rows.map { it.toDomain() } }

    fun observeTotalCardCount(): Flow<Int> = cardDao.observeTotalCount()

    fun observeWrongCountPlaceholder(): Flow<Int> = cardDao.observeTotalCount()

    /**
     * 今日进度。newTarget / reviewTarget 取「设置上限」与「实际存量」的较小值，
     * 否则一张卡都没有的新用户会永远看到一个 0% 的进度环。
     */
    fun observeTodayProgress(): Flow<TodayProgress> = combine(
        settingsRepository.settings,
        cardDao.observeNewCount(),
        cardDao.observeDueCount(System.currentTimeMillis())
    ) { settings, totalNew, dueNow ->
        val now = System.currentTimeMillis()
        val dayStart = DayBoundary.dayStartOf(now, settings.dayBoundaryHour)
        val dayEnd = DayBoundary.dayEndOf(now, settings.dayBoundaryHour)

        val newDone = reviewLogDao.countNewLearned(dayStart, dayEnd)
        val reviewDone = reviewLogDao.countReviewed(dayStart, dayEnd)
        val forgotten = reviewLogDao.countForgotten(dayStart, dayEnd)

        val newTarget = if (settings.newPerDay <= 0) totalNew else minOf(settings.newPerDay, totalNew)
        val reviewTarget = minOf(settings.reviewPerDay, reviewDone + dueNow)

        TodayProgress(
            newDone = newDone,
            newTarget = newTarget,
            reviewDone = reviewDone,
            reviewTarget = reviewTarget,
            forgottenToday = forgotten,
            dueNow = dueNow + (totalNew - newDone).coerceAtLeast(0)
        )
    }

    // ---------------- 队列组装 ----------------

    /**
     * 组装本次背诵队列。三段式：
     *  ① 学习期卡片（LEARNING / RELEARNING）—— 不受每日上限约束，必须当日清空
     *  ② 到期复习卡 —— 按 dueAt 升序（逾期越久越靠前），受 reviewPerDay 限制
     *  ③ 新卡 —— 受 (newPerDay − 今日已学新卡数) 限制
     */
    suspend fun buildStudyQueue(now: Long = System.currentTimeMillis()): StudyQueue {
        val settings = settingsRepository.settingsSnapshot()
        val boundary = settings.dayBoundaryHour
        val dayStart = DayBoundary.dayStartOf(now, boundary)
        val dayEnd = DayBoundary.dayEndOf(now, boundary)

        val deckIds = settings.selectedDeckIds.toList()
        val scopeAll = deckIds.isEmpty()

        val deckNames = deckDao.getAll().associate { it.id to it.name }

        // ① 学习期
        val learning = cardDao.learningQueue()
            .filter { scopeAll || it.deckId in deckIds }

        // ② 到期复习
        val reviewLimit = minOf(settings.reviewPerDay.coerceAtLeast(0), MAX_QUEUE_SIZE)
        val review = when {
            reviewLimit <= 0 -> emptyList()
            scopeAll -> cardDao.dueReviewQueueAll(now, reviewLimit)
            else -> cardDao.dueReviewQueueInDecks(now, deckIds, reviewLimit)
        }

        // ③ 新卡
        val newLearnedToday = reviewLogDao.countNewLearned(dayStart, dayEnd)
        val newRemaining = when {
            settings.newPerDay <= 0 -> MAX_QUEUE_SIZE
            else -> (settings.newPerDay - newLearnedToday).coerceAtLeast(0)
        }
        val newCards = when {
            newRemaining <= 0 -> emptyList()
            scopeAll -> cardDao.newQueueAll(newRemaining.coerceAtMost(MAX_QUEUE_SIZE))
            else -> cardDao.newQueueInDecks(deckIds, newRemaining.coerceAtMost(MAX_QUEUE_SIZE))
        }

        val ordered = interleaveNewAndReview(newCards, review)

        val all = (learning + ordered)
            .distinctBy { it.id }
            .take(MAX_QUEUE_SIZE)
            .map { it.toDomain(deckNames[it.deckId].orEmpty()) }

        return StudyQueue(
            cards = all,
            newCount = newCards.size,
            reviewCount = review.size,
            learningCount = learning.size
        )
    }

    /** 每 NEW_PER_REVIEW 张新卡插入 1 张复习卡。 */
    private fun interleaveNewAndReview(
        newCards: List<CardEntity>,
        reviewCards: List<CardEntity>
    ): List<CardEntity> {
        if (reviewCards.isEmpty()) return newCards
        if (newCards.isEmpty()) return reviewCards

        val result = ArrayList<CardEntity>(newCards.size + reviewCards.size)
        var ni = 0
        var ri = 0
        while (ni < newCards.size || ri < reviewCards.size) {
            repeat(NEW_PER_REVIEW) {
                if (ni < newCards.size) result += newCards[ni++]
            }
            if (ri < reviewCards.size) result += reviewCards[ri++]
        }
        return result
    }

    // ---------------- 评分提交 ----------------

    /**
     * 提交一次评分。整个过程在一个事务内完成：
     * 更新卡片调度状态 → 追加不可变日志 → 累加当日统计。
     *
     * @param elapsedMs 单卡决策耗时，写入日志供后续剔除「手滑」样本
     */
    suspend fun review(
        card: Card,
        rating: SrsRating,
        elapsedMs: Long,
        now: Long = System.currentTimeMillis()
    ): ReviewOutcome {
        val settings = settingsRepository.settingsSnapshot()
        val config = SrsConfig(lapseStep = settings.lapseStep)
        val prev = card.srs
        val next = TcmSrsScheduler.schedule(prev, rating, now, config)
        val isNewLearned = prev.state == SrsState.NEW

        db.withTransaction {
            cardDao.updateSrs(
                cardId = card.id,
                state = next.state.name,
                level = next.level,
                ease = next.ease,
                intervalMin = next.intervalMin,
                dueAt = next.dueAt,
                lastReviewAt = next.lastReviewAt,
                reps = next.reps,
                lapses = next.lapses,
                now = now
            )

            reviewLogDao.insert(
                ReviewLogEntity(
                    cardId = card.id,
                    reviewedAt = now,
                    rating = rating.name,
                    prevLevel = prev.level,
                    nextLevel = next.level,
                    prevIntervalMin = prev.intervalMin,
                    nextIntervalMin = next.intervalMin,
                    srsEaseAfter = next.ease,
                    prevState = prev.state.name,
                    prevEase = prev.ease,
                    prevDueAt = prev.dueAt,
                    prevReps = prev.reps,
                    prevLapses = prev.lapses,
                    prevLastReviewAt = prev.lastReviewAt,
                    elapsedMs = elapsedMs,
                    mode = "DAILY",
                    deckId = card.deckId
                )
            )

            statsDao.bump(
                date = DayBoundary.dateKey(now, settings.dayBoundaryHour),
                newCards = if (isNewLearned) 1 else 0,
                reviewCards = if (isNewLearned) 0 else 1,
                remembered = if (rating == SrsRating.AGAIN) 0 else 1,
                forgotten = if (rating == SrsRating.AGAIN) 1 else 0
            )
        }

        return ReviewOutcome(
            cardId = card.id,
            rating = rating,
            nextIntervalMin = next.intervalMin,
            nextDueText = TcmSrsScheduler.describeNext(next.intervalMin),
            isNewLearned = isNewLearned
        )
    }

    /**
     * 撤销最近一次评分。返回被回滚的 cardId；若无日志可撤销则返回 null。
     * 依赖 review_logs 中保存的完整前状态快照做精确回滚。
     */
    suspend fun undoLast(now: Long = System.currentTimeMillis()): Long? {
        val log = reviewLogDao.latest() ?: return null
        val settings = settingsRepository.settingsSnapshot()

        db.withTransaction {
            cardDao.updateSrs(
                cardId = log.cardId,
                state = log.prevState,
                level = log.prevLevel,
                ease = log.prevEase,
                intervalMin = log.prevIntervalMin,
                dueAt = log.prevDueAt,
                lastReviewAt = log.prevLastReviewAt,
                reps = log.prevReps,
                lapses = log.prevLapses,
                now = now
            )
            reviewLogDao.deleteById(log.id)

            // 同步回滚当日统计（负数累加）
            val dateKey = DayBoundary.dateKey(log.reviewedAt, settings.dayBoundaryHour)
            if (statsDao.get(dateKey) != null) {
                val wasNew = log.prevLevel == 0
                statsDao.accumulate(
                    date = dateKey,
                    newCards = if (wasNew) -1 else 0,
                    reviewCards = if (wasNew) 0 else -1,
                    quizCount = 0,
                    correctCount = 0,
                    minutes = 0,
                    remembered = if (log.rating == SrsRating.AGAIN.name) 0 else -1,
                    forgotten = if (log.rating == SrsRating.AGAIN.name) -1 else 0
                )
            }
        }
        return log.cardId
    }

    /** 是否存在可撤销的操作。 */
    suspend fun canUndo(): Boolean = reviewLogDao.latest() != null

    // ---------------- 卡片管理 ----------------

    suspend fun getCards(
        deckId: Long?,
        state: String?,
        keyword: String?,
        limit: Int = 100,
        offset: Int = 0
    ): List<Card> {
        val names = deckDao.getAll().associate { it.id to it.name }
        return cardDao.queryManaged(deckId, state, keyword?.takeIf { it.isNotBlank() }, limit, offset)
            .map { it.toDomain(names[it.deckId].orEmpty()) }
    }

    suspend fun setSuspended(cardId: Long, suspended: Boolean, now: Long = System.currentTimeMillis()) =
        cardDao.setSuspended(cardId, suspended, now)

    suspend fun resetProgress(cardId: Long, now: Long = System.currentTimeMillis()) =
        cardDao.resetProgress(cardId, now)

    suspend fun deleteCard(cardId: Long) = cardDao.deleteById(cardId)

    suspend fun getAllDecks(): List<Deck> = deckDao.getAll().map { it.toDomain() }

    suspend fun getCard(cardId: Long): Card? {
        val entity = cardDao.getById(cardId) ?: return null
        val deckName = deckDao.getById(entity.deckId)?.name.orEmpty()
        return entity.toDomain(deckName)
    }

    suspend fun addCard(
        deckId: Long,
        front: String,
        back: String,
        extra: String? = null,
        sourceRef: String? = null,
        tags: List<String> = emptyList(),
        type: String = CardEntity.TYPE_BASIC,
        /** 古籍溯源三列：支撑第二阶段「查看原文」反向跳转 */
        sourceBookId: Long? = null,
        sourceCharStart: Int? = null,
        sourceCharEnd: Int? = null,
        now: Long = System.currentTimeMillis()
    ): Long = cardDao.insert(
        CardEntity(
            deckId = deckId,
            type = type,
            front = front,
            back = back,
            extra = extra,
            sourceRef = sourceRef,
            tags = com.tcmfactory.domain.model.encodeTags(tags),
            createdAt = now,
            updatedAt = now,
            srsDueAt = now,
            sourceBookId = sourceBookId,
            sourceCharStart = sourceCharStart,
            sourceCharEnd = sourceCharEnd
        )
    )

    /** 按名称查找牌组，不存在则创建。用于把阅读时即兴创建的卡片归入合适位置。 */
    suspend fun ensureDeck(name: String, subject: String): Long {
        deckDao.findByNameAndParent(name, null)?.let { return it.id }
        val now = System.currentTimeMillis()
        val maxOrder = deckDao.getAll().maxOfOrNull { it.sortOrder } ?: 0
        return deckDao.insert(
            DeckEntity(
                name = name,
                subject = subject,
                parentId = null,
                sortOrder = maxOrder + 10,
                isBuiltin = false,
                createdAt = now
            )
        )
    }

    suspend fun updateCard(card: Card, now: Long = System.currentTimeMillis()) {
        val existing = cardDao.getById(card.id) ?: return
        cardDao.update(
            existing.copy(
                front = card.front,
                back = card.back,
                extra = card.extra,
                note = card.note,
                sourceRef = card.sourceRef,
                tags = com.tcmfactory.domain.model.encodeTags(card.tags),
                updatedAt = now
            )
        )
    }

    /** 未来 N 天内预计到期数量，用于完成页的「下次复习」提示。 */
    suspend fun countDueBefore(timestamp: Long): Int =
        cardDao.dueReviewQueueAll(timestamp, MAX_QUEUE_SIZE).size
}
