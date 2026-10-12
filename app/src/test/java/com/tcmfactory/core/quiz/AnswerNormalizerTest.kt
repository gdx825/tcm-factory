package com.tcmfactory.core.quiz

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 答案解析的容错测试。
 *
 * 背景：用户报过一个 bug ——「随机答题选错了，有时不告诉正确选项」。
 * 根因是答案在数据库里有两种写法，而读取端只认其中一种：
 *  · 题库导入写入**下标数组** `[0,3]`；
 *  · 知识包写入**字母 / 文字** `"D"`、`"对"`。
 * 后者被当作下标解析时得到空列表，界面于是既不标正确选项、底部也是空白。
 *
 * 这组用例里最重要的是 [写入后读出的往返链路必须保持一致]——
 * 此前恰好缺的就是这一层：题库解析器只测「文本 → 下标」，
 * 知识包只测「包结构」，没有任何测试验证「写进数据库再读出来」的闭环。
 */
class AnswerNormalizerTest {

    private val json = Json

    private fun str(value: String) = JsonPrimitive(value)
    private fun arr(vararg values: String) = JsonArray(values.map { JsonPrimitive(it) })
    private fun nums(vararg values: Int) = JsonArray(values.map { JsonPrimitive(it) })

    private val fourOptions = listOf("发汗解肌", "温通经脉", "平冲降气", "清热燥湿")

    // ==================================================================
    //  有选项：各种答案写法
    // ==================================================================

    @Test
    fun `单个字母答案转成下标`() {
        val r = AnswerNormalizer.normalize(fourOptions, str("D"), "SINGLE")
        assertEquals(listOf(3), r.answerIndices)
        assertEquals("SINGLE", r.type)
        assertEquals(fourOptions, r.options)
    }

    @Test
    fun `字母数组答案转成下标并判定为多选`() {
        val r = AnswerNormalizer.normalize(fourOptions, arr("A", "B", "C"), null)
        assertEquals(listOf(0, 1, 2), r.answerIndices)
        assertEquals("MULTI", r.type)
    }

    @Test
    fun `下标数组保持原语义不变`() {
        // 题库导入路径的既有格式，改动不能破坏它
        val r = AnswerNormalizer.normalize(fourOptions, nums(0, 3), null)
        assertEquals(listOf(0, 3), r.answerIndices)
        assertEquals("MULTI", r.type)
    }

    @Test
    fun `连写字母与带分隔符的字母都能解析`() {
        assertEquals(listOf(0, 2), AnswerNormalizer.normalize(fourOptions, str("AC"), null).answerIndices)
        assertEquals(listOf(0, 2), AnswerNormalizer.normalize(fourOptions, str("A,C"), null).answerIndices)
        assertEquals(listOf(0, 2), AnswerNormalizer.normalize(fourOptions, str("A、C"), null).answerIndices)
        assertEquals(listOf(1, 3), AnswerNormalizer.normalize(fourOptions, str("b d"), null).answerIndices)
    }

    @Test
    fun `越界的字母会被剔除而不是产生非法下标`() {
        // 只有 4 个选项，答案写成 F 属数据错误，应丢弃而非越界
        val r = AnswerNormalizer.normalize(fourOptions, str("F"), null)
        assertEquals(emptyList(), r.answerIndices)
    }

    @Test
    fun `纯数字字符串按一基编号处理`() {
        // 用户手写题库常从 1 开始编号，与 JSON 数组里的 0 起始下标刻意区分
        assertEquals(listOf(2), AnswerNormalizer.normalize(fourOptions, str("3"), null).answerIndices)
        assertEquals(listOf(0), AnswerNormalizer.normalize(fourOptions, str("1"), null).answerIndices)
    }

    @Test
    fun `有选项时答案为判断文字也能映射到正确项`() {
        // 少数题库把判断题写成 options = [正确, 错误] 并给出「对」
        val twoOptions = listOf("正确", "错误")
        assertEquals(listOf(0), AnswerNormalizer.normalize(twoOptions, str("对"), null).answerIndices)
        assertEquals(listOf(1), AnswerNormalizer.normalize(twoOptions, str("错"), null).answerIndices)
    }

    // ==================================================================
    //  判断题：没有选项时补上固定选项
    // ==================================================================

    @Test
    fun `判断题会补上正确与错误两个选项`() {
        val r = AnswerNormalizer.normalize(emptyList(), str("对"), "JUDGE")
        assertEquals(AnswerNormalizer.JUDGE_OPTIONS, r.options)
        assertEquals(listOf(0), r.answerIndices)
        assertEquals("JUDGE", r.type)

        val wrong = AnswerNormalizer.normalize(emptyList(), str("错"), "JUDGE")
        assertEquals(listOf(1), wrong.answerIndices)
    }

    @Test
    fun `判断题答案的各种写法都被识别`() {
        val truthy = listOf("对", "正确", "是", "√", "T", "true", "1")
        val falsy = listOf("错", "错误", "否", "×", "F", "false", "0")

        truthy.forEach { token ->
            val r = AnswerNormalizer.normalize(emptyList(), str(token), "JUDGE")
            assertEquals(listOf(0), r.answerIndices, "「$token」应判定为正确")
        }
        falsy.forEach { token ->
            val r = AnswerNormalizer.normalize(emptyList(), str(token), "JUDGE")
            assertEquals(listOf(1), r.answerIndices, "「$token」应判定为错误")
        }
    }

    @Test
    fun `判断题即使答案无法识别也保持可作答`() {
        // 宁可给一个默认值，也不要出现一道点不动的题
        val r = AnswerNormalizer.normalize(emptyList(), null, "JUDGE")
        assertEquals(AnswerNormalizer.JUDGE_OPTIONS, r.options)
        assertEquals(listOf(0), r.answerIndices)
    }

    @Test
    fun `无选项且答案为空时不会凭空造出选项`() {
        // 填空题等类型不应被误判成判断题
        val r = AnswerNormalizer.normalize(emptyList(), null, "FILL")
        assertEquals(emptyList(), r.options)
        assertEquals(emptyList(), r.answerIndices)
    }

    // ==================================================================
    //  健壮性
    // ==================================================================

    @Test
    fun `空答案与 null 答案都不抛异常`() {
        assertEquals(emptyList(), AnswerNormalizer.normalize(fourOptions, null, null).answerIndices)
        assertEquals(emptyList(), AnswerNormalizer.normalize(fourOptions, str(""), null).answerIndices)
        assertEquals(emptyList(), AnswerNormalizer.normalize(fourOptions, str("   "), null).answerIndices)
    }

    @Test
    fun `选项中的空白项被清理`() {
        val r = AnswerNormalizer.normalize(listOf(" A ", "", "B"), str("A"), null)
        assertEquals(listOf("A", "B"), r.options)
    }

    @Test
    fun `存储在库里的损坏 JSON 不会导致崩溃`() {
        // 旧版本或手工修改过的数据可能不是合法 JSON
        val r = AnswerNormalizer.normalizeStored("这不是数组", "也不是", "SINGLE")
        assertEquals(emptyList(), r.options)
        assertEquals(emptyList(), r.answerIndices)
    }

    @Test
    fun `声明为多选但答案只有一个时仍按多选处理`() {
        // 多选题可能只有一个正确项（如「下列哪一项不正确」的变体）
        val r = AnswerNormalizer.normalize(fourOptions, str("B"), "MULTI")
        assertEquals(listOf(1), r.answerIndices)
        assertEquals("MULTI", r.type)
    }

    // ==================================================================
    //  往返：写入数据库再读出来必须一致
    // ==================================================================

    /** 模拟写库：选项存 JSON 字符串数组，答案存以下标列表。 */
    private fun storedOptions(options: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), options)

    private fun storedAnswers(indices: List<Int>): String =
        json.encodeToString(ListSerializer(Int.serializer()), indices)

    @Test
    fun `写入后读出的往返链路必须保持一致`() {
        // 这是此前缺失、也正是漏掉 bug 的那一层测试
        val cases = listOf(
            Triple(fourOptions, str("D"), "SINGLE"),
            Triple(fourOptions, arr("A", "B", "C"), null),
            Triple(fourOptions, nums(0, 3), null),
            Triple(fourOptions, str("AC"), null),
            Triple(emptyList(), str("对"), "JUDGE"),
            Triple(emptyList(), str("错"), "JUDGE")
        )

        cases.forEach { (options, answer, type) ->
            val imported = AnswerNormalizer.normalize(options, answer, type)

            // 走一次"入库 → 出库"
            val reread = AnswerNormalizer.normalizeStored(
                optionsJson = storedOptions(imported.options),
                answerJson = storedAnswers(imported.answerIndices),
                declaredType = imported.type
            )

            assertEquals(imported.options, reread.options, "选项经往返后应不变：$options")
            assertEquals(
                imported.answerIndices, reread.answerIndices,
                "答案经往返后应不变：$options / $answer"
            )
            assertEquals(imported.type, reread.type, "题型经往返后应不变：$options")
        }
    }

    @Test
    fun `往返后正确答案的字母显示与原始写法一致`() {
        // 界面底部显示的是「正确答案 ACD」，必须与题库作者写的答案对得上
        listOf(
            "D" to listOf(3),
            "AC" to listOf(0, 2),
            "ACD" to listOf(0, 2, 3)
        ).forEach { (written, expected) ->
            val imported = AnswerNormalizer.normalize(fourOptions, str(written), null)
            assertEquals(expected, imported.answerIndices, "「$written」应解析为 $expected")

            val reread = AnswerNormalizer.normalizeStored(
                storedOptions(imported.options),
                storedAnswers(imported.answerIndices),
                imported.type
            )
            // 还原成字母，应当与作者写的完全一致
            val letters = reread.answerIndices.sorted().joinToString("") { ('A' + it).toString() }
            assertEquals(written, letters)
        }
    }

    @Test
    fun `旧格式数据与新格式数据读出的结果相同`() {
        // 同一道题，一个来自旧题库（下标），一个来自知识包（字母），
        // 规范化后必须得到完全一致的下标 —— 这正是界面上「有时」的根源
        val fromQuizImport = AnswerNormalizer.normalizeStored(
            optionsJson = storedOptions(fourOptions),
            answerJson = storedAnswers(listOf(3)),
            declaredType = "SINGLE"
        )
        val fromKnowledgePack = AnswerNormalizer.normalizeStored(
            optionsJson = storedOptions(fourOptions),
            answerJson = json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive("D")),
            declaredType = "SINGLE"
        )

        assertEquals(fromQuizImport.answerIndices, fromKnowledgePack.answerIndices)
        assertTrue(fromKnowledgePack.answerIndices.isNotEmpty(), "知识包来源的答案不应为空")
    }
}
