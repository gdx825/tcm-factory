package com.tcmfactory.core.quiz

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * 答案规范化。
 *
 * **为什么需要它：**
 * 题目的答案在数据库里历史上有两种写法，而读取端原先只认其中一种：
 *  · 题库导入路径写入的是**下标数组** `[0,3]`；
 *  · 知识包路径写入的是**字母/文字** `"D"`、`["A","B"]`、`"对"`。
 * 后者一旦被当作下标数组解析就会失败，得到空列表 —— 表现为
 * **答错后既不标出正确选项、底部「正确答案」后面也是空白**。
 *
 * 这里把「选项 + 答案」统一规范化为：
 *  · 判断题补上固定选项「正确 / 错误」，使其在界面上可点可判；
 *  · 字母、连写字母串、下标数组、文字判断统一转成**下标列表**。
 *
 * 之所以放在读取端而不只改写入端：用户设备上已经存了错误格式的数据，
 * 只改写入路径只能修好「以后新导入的题」，已装的题仍然是坏的。
 * 读取端容错才能一次性覆盖全部历史数据，且无需数据迁移。
 */
object AnswerNormalizer {

    /** 判断题的固定选项。判断题在题库里通常没有 options，需要在此补齐。 */
    val JUDGE_OPTIONS = listOf("正确", "错误")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val TRUE_TOKENS = setOf("对", "正确", "是", "真", "√", "T", "TRUE", "YES", "Y", "1")
    private val FALSE_TOKENS = setOf("错", "错误", "否", "假", "×", "X", "F", "FALSE", "NO", "N", "0")

    private val LETTER_PATTERN = Regex("[A-Za-z]")

    /** 选项在库中以 JSON 字符串数组存储。显式给出序列化器，避免类型推断失败。 */
    private val STRING_LIST_SERIALIZER = ListSerializer(String.serializer())

    data class Result(
        val options: List<String>,
        /** 正确答案的**下标**（0 起始）。 */
        val answerIndices: List<Int>,
        /** SINGLE / MULTI / JUDGE */
        val type: String
    )

    /**
     * 读取路径：从数据库字段规范化。
     * 容忍历史遗留的任意格式，不会抛异常。
     */
    fun normalizeStored(
        optionsJson: String?,
        answerJson: String?,
        declaredType: String?
    ): Result {
        val options = runCatching {
            if (optionsJson.isNullOrBlank()) emptyList()
            else json.decodeFromString(STRING_LIST_SERIALIZER, optionsJson)
        }.getOrDefault(emptyList())

        val element = answerJson
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }

        return normalize(options, element, declaredType)
    }

    /**
     * 导入路径：从原始选项与答案元素规范化。
     */
    fun normalize(
        options: List<String>,
        answer: JsonElement?,
        declaredType: String?
    ): Result {
        val cleaned = options.map { it.trim() }.filter { it.isNotEmpty() }
        val declared = declaredType?.trim()?.uppercase().orEmpty()

        // ---- 判断题：没有选项 ----
        if (cleaned.isEmpty()) {
            val verdict = judgeVerdictOf(answer)
            val looksLikeJudge = verdict != null || declared == "JUDGE"
            if (looksLikeJudge) {
                // verdict 为 null 时默认「正确」：宁可给一个可作答的默认值，
                // 也不要因为答案写得奇怪就变成一道无法作答的题。
                val index = if (verdict == false) 1 else 0
                return Result(JUDGE_OPTIONS, listOf(index), "JUDGE")
            }
            return Result(emptyList(), emptyList(), declared.ifEmpty { "SINGLE" })
        }

        // ---- 有选项 ----
        val indices = extractIndices(answer, cleaned.size)
        val type = when {
            indices.size > 1 -> "MULTI"
            declared == "MULTI" -> "MULTI"
            declared == "JUDGE" -> "JUDGE"
            else -> "SINGLE"
        }
        return Result(cleaned, indices, type)
    }

    // ================= 内部 =================

    private fun extractIndices(element: JsonElement?, optionCount: Int): List<Int> {
        if (element == null) return emptyList()
        val collected = ArrayList<Int>()
        collect(element, optionCount, collected)
        return collected.distinct().sorted()
    }

    private fun collect(element: JsonElement, optionCount: Int, out: MutableList<Int>) {
        when (element) {
            is JsonArray -> element.forEach { collect(it, optionCount, out) }

            is JsonPrimitive -> {
                if (element.isString) {
                    parseTextAnswer(element.content, optionCount).forEach { out += it }
                } else {
                    // JSON 数组/元素里的数字是**0 起始下标**（题库导入的既有格式），
                    // 与下面「纯数字字符串按 1 起始」刻意区分，避免语义冲突。
                    element.intOrNull?.let { if (it in 0 until optionCount) out += it }
                }
            }

            else -> Unit
        }
    }

    private fun parseTextAnswer(text: String, optionCount: Int): List<Int> {
        val t = text.trim()
        if (t.isEmpty()) return emptyList()

        // 1) 字母：支持 "D"、"AC"、"A,C"、"A、C" 等写法
        val letters = LETTER_PATTERN.findAll(t)
            .mapNotNull { m ->
                val c = m.value.uppercase()[0]
                if (c in 'A'..'Z') c - 'A' else null
            }
            .filter { it in 0 until optionCount }
            .toList()
        if (letters.isNotEmpty()) return letters.distinct().sorted()

        // 2) 判断类文字：用于「有选项但答案是『对/错』」的写法
        val verdict = judgeVerdictOf(JsonPrimitive(t))
        if (verdict != null && optionCount == 2) {
            return listOf(if (verdict) 0 else 1)
        }

        // 3) 纯数字字符串：按 1 起始（用户手写题库通常从 1 开始编号）
        t.toIntOrNull()?.let { value ->
            val index = value - 1
            if (index in 0 until optionCount) return listOf(index)
        }

        return emptyList()
    }

    private fun judgeVerdictOf(element: JsonElement?): Boolean? {
        val primitive = element as? JsonPrimitive ?: return null
        if (!primitive.isString && primitive.intOrNull != null) {
            return when (primitive.intOrNull) {
                1 -> true
                0 -> false
                else -> null
            }
        }
        val token = primitive.content.trim().uppercase()
        if (token.isEmpty()) return null
        return when (token) {
            in TRUE_TOKENS -> true
            in FALSE_TOKENS -> false
            else -> null
        }
    }
}
