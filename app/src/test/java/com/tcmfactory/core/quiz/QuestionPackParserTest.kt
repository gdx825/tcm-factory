package com.tcmfactory.core.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 题库解析器单元测试。
 *
 * 重点验证「容错」：用户拿到的题库来自各种渠道，格式千奇百怪，
 * 解析器必须尽可能救回可用题目，同时把真正有问题的题连同原因报给用户。
 */
class QuestionPackParserTest {

    private fun parse(raw: String): QuestionPackParser.ParseOutcome =
        QuestionPackParser.parse(raw).getOrThrow()

    // ---------------- 基础解析 ----------------

    @Test
    fun `标准单选题目被正确解析`() {
        val json = """
            {
              "schemaVersion": 1,
              "packName": "中药学练习",
              "subject": "MATERIA_MEDICA",
              "questions": [
                {
                  "chapter": "解表药",
                  "type": "SINGLE",
                  "stem": "麻黄的功效不包括下列哪一项？",
                  "options": ["发汗解表", "宣肺平喘", "利水消肿", "温通经脉"],
                  "answer": "D",
                  "explanation": "温通经脉为桂枝的功效。",
                  "difficulty": 2,
                  "tags": ["高频"]
                }
              ]
            }
        """.trimIndent()

        val outcome = parse(json)
        assertEquals("中药学练习", outcome.packName)
        assertEquals("MATERIA_MEDICA", outcome.subject)
        assertEquals(1, outcome.accepted.size)

        val q = outcome.accepted[0]
        assertEquals(QuestionType.SINGLE, q.type)
        assertEquals("解表药", q.chapter)
        assertEquals(listOf(3), q.answers)      // D → 下标 3
        assertEquals("D", q.correctLettersText)
        assertEquals("温通经脉为桂枝的功效。", q.explanation)
        assertEquals(listOf("高频"), q.tags)
        assertTrue(outcome.rejected.isEmpty())
    }

    @Test
    fun `选项自带的字母前缀会被剥离`() {
        val json = """
            {"packName":"t","questions":[
              {"stem":"题干","options":["A. 发汗解表","B、宣肺平喘","C) 利水消肿","D．温通经脉"],"answer":"B"}
            ]}
        """.trimIndent()

        val q = parse(json).accepted[0]
        assertEquals(listOf("发汗解表", "宣肺平喘", "利水消肿", "温通经脉"), q.options)
        assertEquals(listOf(1), q.answers)
    }

    // ---------------- 答案格式容错 ----------------

    @Test
    fun `多选答案支持多种分隔写法`() {
        listOf("AC", "A,C", "A、C", "A C", "a c").forEach { answer ->
            val json = """{"questions":[{"type":"MULTI","stem":"题干","options":["甲","乙","丙","丁"],"answer":"$answer"}]}"""
            val q = parse(json).accepted[0]
            assertEquals("答案写法 $answer 解析错误", listOf(0, 2), q.answers)
            assertEquals("AC", q.correctLettersText)
        }
    }

    @Test
    fun `答案支持数字序号`() {
        val json = """{"questions":[{"stem":"题干","options":["甲","乙","丙"],"answer":"2"}]}"""
        val q = parse(json).accepted[0]
        assertEquals(listOf(1), q.answers)
    }

    @Test
    fun `判断题无需写选项且支持中文答案`() {
        val json = """
            {"questions":[
              {"type":"JUDGE","stem":"麻黄性温。","answer":"对"},
              {"type":"JUDGE","stem":"桂枝性寒。","answer":"错误"}
            ]}
        """.trimIndent()

        val outcome = parse(json)
        assertEquals(2, outcome.accepted.size)
        assertEquals(listOf("正确", "错误"), outcome.accepted[0].options)
        assertEquals(listOf(0), outcome.accepted[0].answers)
        assertEquals(listOf(1), outcome.accepted[1].answers)
    }

    @Test
    fun `题型缺失时能按答案形态推断`() {
        val single = parse("""{"questions":[{"stem":"题干","options":["甲","乙"],"answer":"A"}]}""")
        assertEquals(QuestionType.SINGLE, single.accepted[0].type)

        val multi = parse("""{"questions":[{"stem":"题干","options":["甲","乙","丙"],"answer":"AB"}]}""")
        assertEquals(QuestionType.MULTI, multi.accepted[0].type)

        val judge = parse("""{"questions":[{"stem":"题干","answer":"对"}]}""")
        assertEquals(QuestionType.JUDGE, judge.accepted[0].type)
    }

    // ---------------- 驳回与降级 ----------------

    @Test
    fun `题干为空被驳回并给出原因`() {
        val json = """{"questions":[{"stem":"  ","options":["甲","乙"],"answer":"A"}]}"""
        val outcome = parse(json)
        assertTrue(outcome.accepted.isEmpty())
        assertEquals(1, outcome.rejected.size)
        assertEquals("题干为空", outcome.rejected[0].reason)
    }

    @Test
    fun `答案越界被驳回`() {
        val json = """{"questions":[{"stem":"题干","options":["甲","乙"],"answer":"E"}]}"""
        val outcome = parse(json)
        assertTrue(outcome.accepted.isEmpty())
        assertTrue(outcome.rejected[0].reason.contains("无法解析"))
    }

    @Test
    fun `单选题出现多个答案被驳回`() {
        val json = """{"questions":[{"type":"SINGLE","stem":"题干","options":["甲","乙","丙"],"answer":"AB"}]}"""
        val outcome = parse(json)
        assertTrue(outcome.accepted.isEmpty())
        assertTrue(outcome.rejected[0].reason.contains("单选题出现多个答案"))
    }

    @Test
    fun `多选只有一个答案时降级为单选而不是驳回`() {
        val json = """{"questions":[{"type":"MULTI","stem":"题干","options":["甲","乙"],"answer":"A"}]}"""
        val outcome = parse(json)
        assertEquals(1, outcome.accepted.size)
        assertEquals(QuestionType.SINGLE, outcome.accepted[0].type)
    }

    @Test
    fun `未知题型被驳回`() {
        val json = """{"questions":[{"type":"ESSAY","stem":"题干","options":["甲","乙"],"answer":"A"}]}"""
        val outcome = parse(json)
        assertTrue(outcome.accepted.isEmpty())
        assertTrue(outcome.rejected[0].reason.contains("未知题型"))
    }

    @Test
    fun `选项不足两个被驳回`() {
        val json = """{"questions":[{"stem":"题干","options":["只有一个"],"answer":"A"}]}"""
        val outcome = parse(json)
        assertTrue(outcome.accepted.isEmpty())
        assertEquals("选项少于 2 个", outcome.rejected[0].reason)
    }

    // ---------------- 整包行为 ----------------

    @Test
    fun `个别题目有问题不影响其余题目导入`() {
        val json = """
            {"packName":"混合题库","questions":[
              {"stem":"好题一","options":["甲","乙"],"answer":"A"},
              {"stem":"","options":["甲","乙"],"answer":"A"},
              {"stem":"好题二","options":["甲","乙"],"answer":"B"},
              {"stem":"坏题","options":["甲","乙"],"answer":"Z"}
            ]}
        """.trimIndent()

        val outcome = parse(json)
        assertEquals(4, outcome.total)
        assertEquals(2, outcome.accepted.size)
        assertEquals(2, outcome.rejected.size)
        assertTrue(outcome.hasUsableContent)
        assertEquals(listOf("好题一", "好题二"), outcome.accepted.map { it.stem })
    }

    @Test
    fun `未知字段与缺失可选字段不会导致解析失败`() {
        val json = """
            {"packName":"t","vendor":"某机构","extraField":123,"questions":[
              {"stem":"题干","options":["甲","乙"],"answer":"A","unknownKey":"x"}
            ]}
        """.trimIndent()

        val outcome = parse(json)
        assertEquals(1, outcome.accepted.size)
        // 未提供的章节 / 解析 / 难度走默认值
        assertEquals(null, outcome.accepted[0].chapter)
        assertEquals(0, outcome.accepted[0].difficulty)
        assertTrue(outcome.accepted[0].tags.isEmpty())
    }

    @Test
    fun `非法 JSON 返回失败结果而非抛异常`() {
        val result = QuestionPackParser.parse("{ this is not json }")
        assertTrue(result.isFailure)
    }

    @Test
    fun `空题库不会崩溃`() {
        val outcome = parse("""{"packName":"空","questions":[]}""")
        assertEquals(0, outcome.total)
        assertFalse(outcome.hasUsableContent)
    }

    // ================================================================
    //  回归测试：选项前缀剥离不能误伤内容本身
    //
    //  起因（用户反馈的真实 bug）：
    //    「桂枝汤中桂枝与芍药的用量比例是？」四个选项显示成 1 / 2 / 1 / 2，
    //    原因是数字序号剥离正则把 `1:1` 里的 `1:` 当成了序号前缀剥掉。
    //
    //  这类缺陷的共性是：**解析成功、不报错、界面正常，只是内容错了**。
    //  所以必须靠断言原始内容来兜住。
    // ================================================================

    @Test
    fun `比例形式选项不会被误剥离`() {
        val outcome = parse(
            """
            {"packName":"回归","questions":[
              {"stem":"桂枝汤中桂枝与芍药的用量比例是？",
               "options":["1:1","1:2","2:1","3:2"],
               "answer":"A",
               "explanation":"桂枝与芍药等量配伍，一散一收，调和营卫。"}
            ]}
            """.trimIndent()
        )
        val q = outcome.accepted.single()
        assertEquals(listOf("1:1", "1:2", "2:1", "3:2"), q.options)
        assertEquals(listOf(0), q.answers)
    }

    @Test
    fun `全角冒号的比例同样不被剥离`() {
        val outcome = parse(
            """{"packName":"回归","questions":[
                 {"stem":"比例","options":["1：1","2：1"],"answer":"A"}]}"""
        )
        assertEquals(listOf("1：1", "2：1"), outcome.accepted.single().options)
    }

    @Test
    fun `小数形式选项不会被误剥离`() {
        val outcome = parse(
            """{"packName":"回归","questions":[
                 {"stem":"每次服多少","options":["1.5 克","2.5 克","5 克","10 克"],"answer":"B"}]}"""
        )
        assertEquals(
            listOf("1.5 克", "2.5 克", "5 克", "10 克"),
            outcome.accepted.single().options
        )
    }

    @Test
    fun `数字序号前缀仍会被正确剥离`() {
        val outcome = parse(
            """{"packName":"回归","questions":[
                 {"stem":"麻黄的功效是","options":["1. 发汗解表","2、宣肺平喘","3) 利水消肿","4）温中止呕"],"answer":"A"}]}"""
        )
        assertEquals(
            listOf("发汗解表", "宣肺平喘", "利水消肿", "温中止呕"),
            outcome.accepted.single().options
        )
    }

    @Test
    fun `字母前缀里的冒号仍会被剥离`() {
        val outcome = parse(
            """{"packName":"回归","questions":[
                 {"stem":"桂枝的功效是","options":["A: 发汗解肌","B：温通经脉","C. 助阳化气","D、平冲降逆"],"answer":"A"}]}"""
        )
        assertEquals(
            listOf("发汗解肌", "温通经脉", "助阳化气", "平冲降逆"),
            outcome.accepted.single().options
        )
    }

    @Test
    fun `含数字但非序号开头的内容保持不变`() {
        val outcome = parse(
            """{"packName":"回归","questions":[
                 {"stem":"用法","options":["10:1 浓缩","饭后 30 分钟服","睡前服","空腹服"],"answer":"B"}]}"""
        )
        assertEquals(
            listOf("10:1 浓缩", "饭后 30 分钟服", "睡前服", "空腹服"),
            outcome.accepted.single().options
        )
    }
}
