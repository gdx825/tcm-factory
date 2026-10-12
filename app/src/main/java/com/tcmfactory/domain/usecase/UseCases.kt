package com.tcmfactory.domain.usecase

import com.tcmfactory.core.srs.SrsRating
import com.tcmfactory.data.repository.CardRepository
import com.tcmfactory.domain.model.Card
import com.tcmfactory.domain.model.ReviewOutcome
import com.tcmfactory.domain.model.StudyQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 组装本次背诵队列。
 * 「不预生成每日任务表」的取舍决定了这里每次都是实时查询，
 * 好处是算法升级后历史数据会自动按新规则重新解释。
 */
@Singleton
class BuildStudyQueueUseCase @Inject constructor(
    private val repository: CardRepository
) {
    suspend operator fun invoke(now: Long = System.currentTimeMillis()): StudyQueue =
        repository.buildStudyQueue(now)
}

/** 提交一次评分。 */
@Singleton
class ReviewCardUseCase @Inject constructor(
    private val repository: CardRepository
) {
    suspend operator fun invoke(
        card: Card,
        rating: SrsRating,
        elapsedMs: Long,
        now: Long = System.currentTimeMillis()
    ): ReviewOutcome = repository.review(card, rating, elapsedMs, now)
}

/** 撤销上一步评分。返回被回滚的 cardId。 */
@Singleton
class UndoReviewUseCase @Inject constructor(
    private val repository: CardRepository
) {
    suspend operator fun invoke(now: Long = System.currentTimeMillis()): Long? =
        repository.undoLast(now)
}

/** 未来 N 天内到期的卡片数量，用于完成页提示。 */
@Singleton
class CountUpcomingDueUseCase @Inject constructor(
    private val repository: CardRepository
) {
    suspend operator fun invoke(withinDays: Int = 1, now: Long = System.currentTimeMillis()): Int =
        repository.countDueBefore(now + withinDays * 86_400_000L)
}
