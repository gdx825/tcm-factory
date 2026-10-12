package com.tcmfactory.core.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TCM-SRS 调度器单元测试。
 *
 * 这是本项目最重要的测试文件：算法错了，用户的学习效果就是错的，
 * 而且错误在界面上完全看不出来。因此这里覆盖单调性、收敛性、
 * 遗忘恢复、边界、可复现性五个维度。
 */
class TcmSrsSchedulerTest {

    private val now = 1_700_000_000_000L
    private val noJitter = SrsConfig(enableJitter = false)
    private val withJitter = SrsConfig(enableJitter = true)

    // ---------- 1. 初始状态 ----------

    @Test
    fun `新卡初始为 NEW 且立即可学`() {
        val card = TcmSrsScheduler.newCard(cardId = 1L, now = now)
        assertEquals(SrsState.NEW, card.state)
        assertEquals(0, card.level)
        assertEquals(now, card.dueAt)
        assertEquals(2.5, card.ease, 1e-9)
        assertEquals(0, card.reps)
        assertEquals(0, card.lapses)
    }

    // ---------- 2. 单调性：等级与间隔严格递增 ----------

    @Test
    fun `连续 GOOD 时等级逐级上升且跨越学习期与复习期`() {
        var card = TcmSrsScheduler.newCard(1L, now)
        var t = now
        val states = mutableListOf<SrsState>()
        val intervals = mutableListOf<Double>()

        repeat(11) {
            card = TcmSrsScheduler.schedule(card, SrsRating.GOOD, t, noJitter)
            states += card.state
            intervals += card.intervalMin
            t = card.dueAt
        }

        // level 1/2/3 对应 5min / 30min / 12h，仍属学习期；
        // level 4 起对应 1 天及以上，毕业进入复习期。
        assertEquals(SrsState.LEARNING, states[0])
        assertEquals(SrsState.LEARNING, states[1])
        assertEquals(SrsState.LEARNING, states[2])
        assertEquals(SrsState.REVIEW, states[3])
        assertTrue("毕业后应全程保持 REVIEW", states.drop(3).all { it == SrsState.REVIEW })

        // 等级序列 1..11
        assertEquals(11, card.level)

        // 间隔单调不减
        assertTrue(
            "间隔必须单调不减: $intervals",
            intervals.zipWithNext().all { (a, b) -> b >= a }
        )

        // 前三级间隔完全等于阶梯表（学习期不做 EF 微调）
        assertEquals(5.0, intervals[0], 1e-9)
        assertEquals(30.0, intervals[1], 1e-9)
        assertEquals(720.0, intervals[2], 1e-9)
    }

    // ---------- 3. 收敛性：长期复习不溢出 ----------

    @Test
    fun `模拟三年复习后等级封顶且时间戳不溢出`() {
        var card = TcmSrsScheduler.newCard(7L, now)
        var t = now
        repeat(1000) {
            card = TcmSrsScheduler.schedule(card, SrsRating.GOOD, t, withJitter)
            t = card.dueAt
            assertTrue("dueAt 必须为正", card.dueAt > 0)
            assertTrue("level 不得超过上限", card.level <= TcmSrsScheduler.MAX_LEVEL)
        }
        assertEquals(TcmSrsScheduler.MAX_LEVEL, card.level)
        // 180 天 × EF 上限 1.6 × 抖动上限 1.05 ≈ 302 天，取宽松上界防护
        assertTrue("间隔不应失控", card.intervalMin < 259200.0 * 1.6 * 1.05 + 1.0)
        assertTrue("ease 应被钳制在 2.8", card.ease <= 2.8 + 1e-9)
    }

    // ---------- 4. 遗忘恢复 ----------

    @Test
    fun `高等级卡片评 AGAIN 后回退四档并立即进入重学`() {
        var card = TcmSrsScheduler.newCard(3L, now).copy(
            state = SrsState.REVIEW,
            level = 11,
            ease = 2.5,
            intervalMin = 259200.0
        )
        card = TcmSrsScheduler.schedule(card, SrsRating.AGAIN, now, noJitter)

        assertEquals(7, card.level)                 // 11 - 4
        assertEquals(SrsState.RELEARNING, card.state)
        assertEquals(5.0, card.intervalMin, 1e-9)   // 5 分钟后重学
        assertEquals(now + 5 * 60_000L, card.dueAt)
        assertEquals(2.30, card.ease, 1e-9)         // 2.5 - 0.20
        assertEquals(1, card.lapses)
        assertEquals(1, card.reps)
    }

    @Test
    fun `新卡首次评 AGAIN 会进入五分钟重学档`() {
        var card = TcmSrsScheduler.newCard(4L, now)
        card = TcmSrsScheduler.schedule(card, SrsRating.AGAIN, now, noJitter)

        // 一旦被学习过（哪怕答错），语义上就不再是「未学习的新卡」，故落到 level 1
        assertEquals(1, card.level)
        assertEquals(SrsState.LEARNING, card.state)   // NEW 忘记 → LEARNING，而非 RELEARNING
        assertEquals(now + 5 * 60_000L, card.dueAt)
        assertEquals(5.0, card.intervalMin, 1e-9)
    }

    @Test
    fun `低等级卡片评 AGAIN 时等级不低于一档`() {
        val card = TcmSrsScheduler.newCard(5L, now).copy(state = SrsState.LEARNING, level = 2)
        val next = TcmSrsScheduler.schedule(card, SrsRating.AGAIN, now, noJitter)
        assertEquals(1, next.level)
        assertEquals(SrsState.RELEARNING, next.state)
        assertEquals(5.0, next.intervalMin, 1e-9)
    }

    // ---------- 5. EF 边界 ----------

    @Test
    fun `反复 AGAIN 时 ease 不会跌破下限`() {
        var card = TcmSrsScheduler.newCard(6L, now).copy(state = SrsState.REVIEW, level = 6, ease = 2.5)
        var t = now
        repeat(20) {
            card = TcmSrsScheduler.schedule(card, SrsRating.AGAIN, t, noJitter)
            t = card.dueAt
            assertTrue("ease 不得低于 1.3", card.ease >= 1.3 - 1e-9)
        }
        assertEquals(1.3, card.ease, 1e-9)
    }

    @Test
    fun `反复 EASY 时 ease 不会超过上限`() {
        var card = TcmSrsScheduler.newCard(8L, now)
        var t = now
        repeat(30) {
            card = TcmSrsScheduler.schedule(card, SrsRating.EASY, t, noJitter)
            t = card.dueAt
            assertTrue("ease 不得超过 2.8", card.ease <= 2.8 + 1e-9)
        }
        assertEquals(2.8, card.ease, 1e-9)
    }

    // ---------- 6. HARD / EASY 的档位行为 ----------

    @Test
    fun `HARD 只下降一档且间隔打折`() {
        val card = TcmSrsScheduler.newCard(9L, now).copy(
            state = SrsState.REVIEW, level = 6, ease = 2.5
        )
        val next = TcmSrsScheduler.schedule(card, SrsRating.HARD, now, noJitter)
        assertEquals(5, next.level)
        assertEquals(SrsState.REVIEW, next.state)
        // LADDER[5] = 2 天 = 2880min，先 × 0.6 打折，再乘 EF 系数（ease 同时降到 2.35）
        val expected = 2880.0 * 0.6 * (2.35 / 2.5)
        assertEquals(expected, next.intervalMin, 1e-6)
        assertEquals(2.35, next.ease, 1e-9)
    }

    @Test
    fun `EASY 一次上升两档`() {
        val card = TcmSrsScheduler.newCard(10L, now).copy(
            state = SrsState.REVIEW, level = 5, ease = 2.5
        )
        val next = TcmSrsScheduler.schedule(card, SrsRating.EASY, now, noJitter)
        assertEquals(7, next.level)
        // LADDER[7] = 7 天 = 10080min，再乘 EF 系数（ease 升到 2.65）
        val expected = 10080.0 * (2.65 / 2.5)
        assertEquals(expected, next.intervalMin, 1e-6)
        assertEquals(2.65, next.ease, 1e-9)
    }

    @Test
    fun `学习期内不应用 EF 微调`() {
        // level 2 评 GOOD → level 3，间隔必须精确等于 12h，不受 ease 影响
        val card = TcmSrsScheduler.newCard(11L, now).copy(
            state = SrsState.LEARNING, level = 2, ease = 1.5
        )
        val next = TcmSrsScheduler.schedule(card, SrsRating.GOOD, now, noJitter)
        assertEquals(3, next.level)
        assertEquals(720.0, next.intervalMin, 1e-9)
    }

    @Test
    fun `天级间隔会受 EF 放大`() {
        val base = TcmSrsScheduler.newCard(12L, now).copy(
            state = SrsState.REVIEW, level = 4, ease = 2.5
        )
        val highEase = base.copy(ease = 2.8)

        val a = TcmSrsScheduler.schedule(base, SrsRating.GOOD, now, noJitter)
        val b = TcmSrsScheduler.schedule(highEase, SrsRating.GOOD, now, noJitter)

        // 高 ease 的卡片下次间隔应明显更长（同一 level 下）
        assertEquals(a.level, b.level)
        assertTrue("高 ease 应拉长间隔: ${a.intervalMin} vs ${b.intervalMin}", b.intervalMin > a.intervalMin)
    }

    // ---------- 7. 抖动可复现性 ----------

    @Test
    fun `同一卡片的抖动因子必须完全一致`() {
        val f1 = TcmSrsScheduler.jitterFactor(12345L)
        val f2 = TcmSrsScheduler.jitterFactor(12345L)
        assertEquals(f1, f2, 0.0)

        val f3 = TcmSrsScheduler.jitterFactor(54321L)
        assertNotEquals(f1, f3)   // 不同卡片应当分散
    }

    @Test
    fun `抖动因子落在正负 5 百分点内`() {
        for (id in 1L..2000L) {
            val f = TcmSrsScheduler.jitterFactor(id, ratio = 0.05)
            assertTrue("抖动越界: $id -> $f", f in 0.95..1.05)
        }
    }

    @Test
    fun `关闭抖动时同参数结果完全确定`() {
        val card = TcmSrsScheduler.newCard(77L, now).copy(state = SrsState.REVIEW, level = 6)
        val x = TcmSrsScheduler.schedule(card, SrsRating.GOOD, now, noJitter)
        val y = TcmSrsScheduler.schedule(card, SrsRating.GOOD, now, noJitter)
        assertEquals(x, y)
    }

    // ---------- 8. 文案与预览 ----------

    @Test
    fun `间隔文案在量级边界上正确切换单位`() {
        assertEquals("5 分钟后", TcmSrsScheduler.describeNext(5.0))
        assertEquals("30 分钟后", TcmSrsScheduler.describeNext(30.0))
        assertEquals("12 小时后", TcmSrsScheduler.describeNext(720.0))
        assertEquals("1 天后", TcmSrsScheduler.describeNext(1440.0))
        assertEquals("15 天后", TcmSrsScheduler.describeNext(21600.0))
        assertEquals("2 个月后", TcmSrsScheduler.describeNext(86400.0))
    }

    @Test
    fun `四档预览返回四项且顺序固定`() {
        val card = TcmSrsScheduler.newCard(88L, now).copy(state = SrsState.REVIEW, level = 5)
        val preview = TcmSrsScheduler.previewAll(card, now, noJitter)
        assertEquals(4, preview.size)
        assertEquals(
            listOf(SrsRating.AGAIN, SrsRating.HARD, SrsRating.GOOD, SrsRating.EASY),
            preview.map { it.first }
        )
        assertEquals("5 分钟后", preview[0].second)
    }

    // ---------- 9. 配置防护 ----------

    @Test(expected = IllegalArgumentException::class)
    fun `阶梯表非单调时构造配置应抛异常`() {
        SrsConfig(ladderMin = listOf(5.0, 30.0, 20.0, 1440.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `lapseStep 越界时构造配置应抛异常`() {
        SrsConfig(lapseStep = 99)
    }
}
