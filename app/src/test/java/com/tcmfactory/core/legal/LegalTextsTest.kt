package com.tcmfactory.core.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 免责条款的完整性检查。
 *
 * ## 为什么条款也要写测试
 *
 * 这些文字是**合规表述**，它们的价值恰恰在于"一句话都不能少"：
 * 少了"不构成购买"，打赏就可能被理解成交易；少了"与第三方内容无关"，
 * 用户就可能以为自己在为题库付费。而这些句子在后续改文案时**极容易被顺手精简掉** ——
 * 界面上完全看不出来，直到真的出现争议。
 *
 * 所以这里不测"文字长什么样"，只测**几个必须在场的关键表述**。
 * 措辞可以改，语义不能丢。
 */
class LegalTextsTest {

    private val rewardClause = LegalTexts.clauses.first { it.title.contains("打赏") }
    private val thirdPartyClause = LegalTexts.clauses.first { it.title.contains("第三方内容") }

    private val rewardText = rewardClause.paragraphs.joinToString("\n")
    private val thirdPartyText = thirdPartyClause.paragraphs.joinToString("\n")

    @Test
    fun `条款版本号为正数`() {
        // 修订条款时必须递增，否则已同意的用户不会被重新提示。
        assertTrue("条款版本号必须为正", LegalTexts.VERSION >= 1)
    }

    @Test
    fun `八类条款齐全且每类都有正文`() {
        assertEquals("条款数量发生变化，请确认是否漏删或漏加", 8, LegalTexts.clauses.size)
        LegalTexts.clauses.forEach { clause ->
            assertTrue("条款「${clause.title}」标题为空", clause.title.isNotBlank())
            assertTrue("条款「${clause.title}」没有正文", clause.paragraphs.isNotEmpty())
            assertTrue(
                "条款「${clause.title}」存在空段落",
                clause.paragraphs.all { it.isNotBlank() }
            )
        }
    }

    @Test
    fun `医疗免责排在第一条`() {
        // 顺序是刻意的：对中医类软件来说，医疗误用是唯一可能造成真实伤害的来源，
        // 它不该被排在"数据丢失"这类风险后面。
        assertTrue(
            "医疗免责应排在第一，实际第一条是「${LegalTexts.clauses.first().title}」",
            LegalTexts.clauses.first().title.contains("医疗")
        )
    }

    // ---------------- 打赏条款：这几句必须在场 ----------------

    @Test
    fun `打赏条款写明完全免费且无付费墙`() {
        assertTrue(rewardText.contains("完全免费"))
        assertTrue("必须写明不存在功能锁 / 付费墙", rewardText.contains("付费墙"))
    }

    @Test
    fun `打赏条款写明仅用于支持开发维护`() {
        // 这是本次补充的核心：只说"自愿"不够，还要说清钱用在哪。
        assertTrue(
            "打赏条款必须写明用途限于支持开发与维护",
            rewardText.contains("开发与维护")
        )
    }

    @Test
    fun `打赏条款写明不构成购买`() {
        assertTrue("必须写明不构成购买行为", rewardText.contains("不构成购买"))
        assertTrue("必须写明不产生对价关系", rewardText.contains("对价"))
    }

    @Test
    fun `打赏条款写明不购买内容也不解锁功能`() {
        assertTrue("必须写明不解锁额外功能", rewardText.contains("解锁"))
        assertTrue("必须写明不购买任何内容", rewardText.contains("不购买任何内容"))
    }

    @Test
    fun `打赏条款写明与题库等具体内容无关`() {
        // 软件可以导入第三方题库，如果不写这句，用户付款后对内容不满时
        // 主观上会认为"我付了钱"。
        assertTrue(
            "打赏条款必须写明与题库、古籍等内容无关",
            rewardText.contains("无关")
        )
        assertTrue("应点明题库这类内容", rewardText.contains("题库"))
    }

    // ---------------- 第三方内容条款 ----------------

    @Test
    fun `第三方内容条款写明内容可由使用者自行导入`() {
        assertTrue(
            "必须写明支持使用者自行导入第三方内容",
            thirdPartyText.contains("自行导入")
        )
    }

    @Test
    fun `第三方内容条款写明与打赏无关联`() {
        assertTrue(
            "必须写明打赏与第三方内容的获取、授权无关",
            thirdPartyText.contains("无关联") || thirdPartyText.contains("无关")
        )
    }

    @Test
    fun `第三方内容条款保留侵权联系渠道`() {
        // 这是内容权利人的救济路径，不能删。
        assertTrue(thirdPartyText.contains("权利证明"))
    }

    // ---------------- 首次启动确认页 ----------------

    @Test
    fun `启动确认页要点覆盖医疗与免费两件最要紧的事`() {
        val points = LegalTexts.summaryPoints.joinToString("\n")
        assertEquals("确认页要点数量变化，请确认是否有意为之", 4, LegalTexts.summaryPoints.size)
        assertTrue("必须提示不构成医疗建议", points.contains("不构成医疗建议"))
        assertTrue("必须说明完全免费", points.contains("完全免费"))
        assertTrue("必须说明打赏仅用于支持开发", points.contains("开发与维护"))
        assertTrue("必须说明与题库等内容无关", points.contains("题库"))
    }
}
