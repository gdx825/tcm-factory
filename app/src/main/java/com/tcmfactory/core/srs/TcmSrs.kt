package com.tcmfactory.core.srs

import kotlin.math.roundToInt

/**
 * 中医工厂 —— TCM-SRS 遗忘曲线调度器
 *
 * 设计要点（详见第一阶段方案 4.2–4.4）：
 *  · 采用「固定阶梯 + 难度因子 EF 微调」的混合模型，而非标准 SM-2。
 *    原因：用户期望的复习节点是 5min/30min/12h/1d/2d/4d/7d/15d 这类**确定性阶梯**，
 *    而 SM-2 会递推出 2.5d / 6.2d / 15.6d 这类不可预期的小数间隔。
 *  · 阶梯 1–3（5min / 30min / 12h）为「学习期」，不受每日复习上限约束（集中巩固优于分散）。
 *  · 阶梯 4+（1 天起）进入「复习期」，按 dueAt 升序进入队列。
 *  · 阶梯 0 专指「尚未学习的新卡」，其间隔为 0（立即可学）。
 *  · 仅 level ≥ 4 才叠加 EF 微调与抖动——短阶梯上做微调是毫无意义的扰动。
 *
 * 本文件为**纯 Kotlin，零 Android 依赖**，可在 JVM 上完整单元测试。
 */

/** 用户对一张卡片的评分。默认 UI 只暴露 AGAIN / GOOD 两档。 */
enum class SrsRating { AGAIN, HARD, GOOD, EASY }

/** 卡片的调度状态。 */
enum class SrsState { NEW, LEARNING, REVIEW, RELEARNING }

/**
 * 一张卡片的 SRS 状态快照。与 Room Entity 中的内联字段一一对应，
 * 但不依赖任何 Android/Room 类型。
 */
data class SrsSnapshot(
    val cardId: Long,
    val state: SrsState = SrsState.NEW,
    val level: Int = 0,
    val ease: Double = 2.5,
    val intervalMin: Double = 0.0,
    val dueAt: Long = 0L,
    val lastReviewAt: Long? = null,
    val reps: Int = 0,
    val lapses: Int = 0
)

/** 算法可调参数。默认值即产品默认值。 */
data class SrsConfig(
    val ladderMin: List<Double> = DEFAULT_LADDER_MIN,
    /** 评 AGAIN 时回退的档数。2–6 可配。 */
    val lapseStep: Int = 4,
    val easeMin: Double = 1.3,
    val easeMax: Double = 2.8,
    val easeInit: Double = 2.5,
    /** 天级间隔的抖动比例，避免同批卡片永远同日到期。 */
    val jitterRatio: Double = 0.05,
    val enableJitter: Boolean = true,
    /** 开始应用 EF 微调的最低档位（4 = 间隔 1 天及以上）。 */
    val easeApplyFromLevel: Int = 4
) {
    init {
        require(ladderMin.size >= 2) { "阶梯表至少需要 2 档" }
        require(ladderMin.zipWithNext().all { (a, b) -> b >= a }) { "阶梯必须单调不减" }
        require(lapseStep in 1..10) { "lapseStep 需在 1..10" }
    }
}

/**
 * 阶梯表，单位分钟。**下标即 level**，二者严格一一对应：
 * 卡片处于 level L 时，下一次复习的基准间隔就是 LADDER[L]。
 *
 * level 0 是特殊档：表示尚未学习的新卡（间隔 0，立即可学）。
 * 因此「新卡首次记住」→ level 1 → 5 分钟后复习，与艾宾浩斯最初节点一致。
 */
val DEFAULT_LADDER_MIN: List<Double> = listOf(
    0.0,        // L0  新卡（尚未学习）
    5.0,        // L1  5 min
    30.0,       // L2  30 min
    720.0,      // L3  12 h
    1440.0,     // L4  1 d   ← 毕业，进入长期记忆
    2880.0,     // L5  2 d
    5760.0,     // L6  4 d
    10080.0,    // L7  7 d
    21600.0,    // L8  15 d
    43200.0,    // L9  30 d
    86400.0,    // L10 60 d
    172800.0,   // L11 120 d
    259200.0    // L12 180 d
)

object TcmSrsScheduler {

    /** 记忆等级上限，对应阶梯的最后一档（180 天）。 */
    const val MAX_LEVEL = 12

    /** 达到该等级即视为「毕业」，进入长期记忆复习期（间隔 ≥ 1 天）。 */
    const val GRADUATION_LEVEL = 4

    private const val MINUTE_MS = 60_000L

    /** 评 AGAIN 后的固定重学延迟：5 分钟。 */
    private const val AGAIN_DELAY_MS = 5 * MINUTE_MS

    /** 天级间隔的下限，低于此值不叠加抖动。 */
    private const val MIN_INTERVAL_FOR_JITTER_MIN = 1440.0

    /** 新卡的初始快照：立即可学（dueAt = now）。 */
    fun newCard(cardId: Long, now: Long, config: SrsConfig = SrsConfig()): SrsSnapshot =
        SrsSnapshot(
            cardId = cardId,
            state = SrsState.NEW,
            level = 0,
            ease = config.easeInit,
            intervalMin = 0.0,
            dueAt = now,
            lastReviewAt = null,
            reps = 0,
            lapses = 0
        )

    /**
     * 核心调度函数：输入卡片当前状态与本次评分，输出卡片的下一个状态。
     *
     * 评分规则：
     *  · AGAIN  未记住 → level 回退 lapseStep 档（不低于 0），立即 5 分钟后重学，EF −0.20，lapses +1
     *  · HARD   模糊   → level −1，间隔取 LADDER[level] × 0.6，EF −0.15
     *  · GOOD   已记住 → level +1，间隔取 LADDER[level]，EF +0.05
     *  · EASY   熟练   → level +2，间隔取 LADDER[level]，EF +0.15
     *
     * @param now 当前时间戳（epoch millis），由调用方注入以保证可测试性
     */
    fun schedule(
        card: SrsSnapshot,
        rating: SrsRating,
        now: Long,
        config: SrsConfig = SrsConfig()
    ): SrsSnapshot {
        var level = card.level
        var ease = card.ease
        val prevState = card.state

        if (rating == SrsRating.AGAIN) {
            // 回退到「5 分钟重学」档（level = 1）：新卡评 AGAIN 也进入该档，
            // 因为卡片一旦被学习过，语义上就不再是「未学习的新卡」。
            level = (level - config.lapseStep).coerceAtLeast(1)
            ease = (ease - 0.20).coerceAtLeast(config.easeMin)
            return card.copy(
                state = if (prevState == SrsState.NEW) SrsState.LEARNING else SrsState.RELEARNING,
                level = level,
                ease = ease,
                intervalMin = AGAIN_DELAY_MS.toDouble() / MINUTE_MS,
                dueAt = now + AGAIN_DELAY_MS,
                lastReviewAt = now,
                reps = card.reps + 1,
                lapses = card.lapses + 1
            )
        }

        when (rating) {
            SrsRating.HARD -> {
                level = (level - 1).coerceAtLeast(0)
                ease = (ease - 0.15).coerceAtLeast(config.easeMin)
            }
            SrsRating.GOOD -> {
                level = (level + 1).coerceAtMost(MAX_LEVEL)
                ease = (ease + 0.05).coerceAtMost(config.easeMax)
            }
            SrsRating.EASY -> {
                level = (level + 2).coerceAtMost(MAX_LEVEL)
                ease = (ease + 0.15).coerceAtMost(config.easeMax)
            }
            SrsRating.AGAIN -> Unit // 已在上方提前返回
        }

        val ladder = config.ladderMin
        var intervalMin = ladder[level.coerceIn(0, ladder.lastIndex)]

        if (rating == SrsRating.HARD) {
            intervalMin *= 0.6
        }

        // 个性化微调只在天级及以上间隔生效
        if (level >= config.easeApplyFromLevel) {
            intervalMin *= (ease / config.easeInit).coerceIn(0.70, 1.60)
            if (config.enableJitter && intervalMin >= MIN_INTERVAL_FOR_JITTER_MIN) {
                intervalMin *= jitterFactor(card.cardId, config.jitterRatio)
            }
        }

        return card.copy(
            state = if (level >= GRADUATION_LEVEL) SrsState.REVIEW else SrsState.LEARNING,
            level = level,
            ease = ease,
            intervalMin = intervalMin,
            dueAt = now + (intervalMin * MINUTE_MS).toLong(),
            lastReviewAt = now,
            reps = card.reps + 1,
            lapses = card.lapses
        )
    }

    /**
     * 基于 cardId 的**确定性**伪随机抖动因子，区间 [1-r, 1+r]。
     * 用 cardId 作种子而非全局随机，保证同一卡片每次计算结果完全一致（可复现、可测试）。
     */
    fun jitterFactor(cardId: Long, ratio: Double = 0.05): Double {
        var x = cardId * -0x61c8864680b583ebL // Knuth 乘法散列
        x = x xor (x ushr 33)
        x *= -0x7ee3623a997a5a25L
        x = x xor (x ushr 29)
        val unit = ((x ushr 11).toDouble()) / ((1L shl 53).toDouble()) // [0, 1)
        return 1.0 + (unit * 2.0 - 1.0) * ratio
    }

    /** 把间隔分钟数转成给用户看的短文案，用于滑动后的即时反馈。 */
    fun describeNext(intervalMin: Double): String = when {
        intervalMin < 1.0 -> "稍后"
        intervalMin < 60.0 -> "${intervalMin.roundToInt()} 分钟后"
        intervalMin < 1440.0 -> "${(intervalMin / 60.0).roundToInt()} 小时后"
        intervalMin < 43200.0 -> "${(intervalMin / 1440.0).roundToInt()} 天后"
        else -> "${(intervalMin / 43200.0).roundToInt()} 个月后"
    }

    /** 人类可读的状态标签。 */
    fun stateLabel(state: SrsState): String = when (state) {
        SrsState.NEW -> "新卡"
        SrsState.LEARNING -> "学习中"
        SrsState.REVIEW -> "复习中"
        SrsState.RELEARNING -> "重学中"
    }

    /**
     * 四档评分预览：给 UI 用来在卡片上显示「如果这样评，下次什么时候见」。
     * 顺序固定为 AGAIN / HARD / GOOD / EASY，便于界面横向展示。
     */
    fun previewAll(
        card: SrsSnapshot,
        now: Long,
        config: SrsConfig = SrsConfig()
    ): List<Pair<SrsRating, String>> =
        SrsRating.entries.map { rating ->
            val next = schedule(card, rating, now, config)
            rating to describeNext(next.intervalMin)
        }
}
