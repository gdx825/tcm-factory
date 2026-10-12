package com.tcmfactory.core.quiz

import kotlinx.serialization.json.Json

/**
 * 题库解析与校验器。
 *
 * 核心原则：**不能因为一道题格式错误就整包导入失败**。
 * 逐题校验，把不合格的题目连同驳回原因一起返回，
 * 让用户在导入报告里看到「哪一道、为什么被拒」，而不是面对一个笼统的失败提示。
 */
object QuestionPackParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    data class Rejection(val index: Int, val stem: String, val reason: String)

    data class ParseOutcome(
        val packName: String,
        val packVersion: String,
        val subject: String,
        val sourceRef: String?,
        val accepted: List<ValidatedQuestion>,
        val rejected: List<Rejection>
    ) {
        val total: Int get() = accepted.size + rejected.size
        val hasUsableContent: Boolean get() = accepted.isNotEmpty()
    }

    /** 选项前缀形如 `A.` `A、` `A)` `A）` `A `，解析时剥离。 */
    private val OPTION_PREFIX = Regex("""^\s*([A-Za-z])\s*[.、．)）:：]\s*""")
    private val LEADING_INDEX = Regex("""^\s*(\d{1,2})\s*[.、．)）:：]\s*""")

    fun parse(raw: String): Result<ParseOutcome> = runCatching {
        val dto = json.decodeFromString<QuestionPackDto>(raw)
        validate(dto)
    }

    fun validate(dto: QuestionPackDto): ParseOutcome {
        val accepted = ArrayList<ValidatedQuestion>()
        val rejected = ArrayList<Rejection>()

        dto.questions.forEachIndexed { index, q ->
            val result = validateOne(index, q)
            when (result) {
                is SingleResult.Ok -> accepted += result.question
                is SingleResult.Bad -> rejected += result.rejection
            }
        }

        return ParseOutcome(
            packName = dto.packName.ifBlank { "未命名题库" },
            packVersion = dto.packVersion,
            subject = dto.subject.ifBlank { "CUSTOM" },
            sourceRef = dto.sourceRef,
            accepted = accepted,
            rejected = rejected
        )
    }

    private sealed interface SingleResult {
        data class Ok(val question: ValidatedQuestion) : SingleResult
        data class Bad(val rejection: Rejection) : SingleResult
    }

    private fun validateOne(index: Int, q: QuestionDto): SingleResult {
        val stem = q.stem.trim()
        if (stem.isBlank()) {
            return SingleResult.Bad(Rejection(index, "", "题干为空"))
        }

        val rawType = q.type.trim().uppercase()
        var type = runCatching { QuestionType.valueOf(rawType) }.getOrNull()

        // 类型缺失时按答案形态推断，避免因一个小字段让整道题作废
        if (type == null) {
            if (rawType.isNotBlank()) {
                return SingleResult.Bad(Rejection(index, stem, "未知题型「${q.type}」，应为 SINGLE / MULTI / JUDGE"))
            }
            type = inferType(q)
        }

        // 选项归一化
        val options = normalizeOptions(type, q.options)
        if (options.size < 2) {
            return SingleResult.Bad(Rejection(index, stem, "选项少于 2 个"))
        }

        // 答案解析
        val answers = parseAnswer(q.answer, options.size)
        if (answers.isEmpty()) {
            return SingleResult.Bad(
                Rejection(index, stem, "答案「${q.answer}」无法解析为有效选项序号")
            )
        }

        if (type != QuestionType.MULTI && answers.size > 1) {
            return SingleResult.Bad(
                Rejection(index, stem, "单选题出现多个答案（${q.answer}），请将题型改为 MULTI")
            )
        }

        if (type == QuestionType.MULTI && answers.size == 1) {
            // 多选只有一个答案虽不严谨，但仍可作答 —— 降级为单选而不是驳回
            type = QuestionType.SINGLE
        }

        return SingleResult.Ok(
            ValidatedQuestion(
                index = index,
                chapter = q.chapter?.trim()?.takeIf { it.isNotBlank() },
                type = type,
                stem = stem,
                options = options,
                answers = answers.distinct().sorted(),
                explanation = q.explanation?.trim()?.takeIf { it.isNotBlank() },
                difficulty = q.difficulty.coerceIn(0, 5),
                tags = q.tags.filter { it.isNotBlank() },
                sourceRef = q.sourceRef?.trim()?.takeIf { it.isNotBlank() }
            )
        )
    }

    private fun inferType(q: QuestionDto): QuestionType {
        val letters = extractLetters(q.answer)
        val opts = q.options.orEmpty()
        // 无选项且答案是「对/错/正确/错误/T/F」→ 判断题
        if (opts.isEmpty() && q.answer.trim() in setOf(
                "对", "错", "正确", "错误", "是", "否", "T", "F", "√", "×"
            )
        ) return QuestionType.JUDGE
        return if (letters.size > 1) QuestionType.MULTI else QuestionType.SINGLE
    }

    private fun normalizeOptions(type: QuestionType, raw: List<String>?): List<String> {
        if (raw.isNullOrEmpty()) {
            return if (type == QuestionType.JUDGE) listOf("正确", "错误") else emptyList()
        }
        return raw.map { option ->
            var text = option.trim()
            text = OPTION_PREFIX.replace(text, "")
            text = LEADING_INDEX.replace(text, "")
            text.trim()
        }.filter { it.isNotBlank() }
    }

    /**
     * 答案解析。支持：
     *  · 字母：`A` / `AC` / `A,C` / `A、C` / `A C`
     *  · 字母前缀：`A. xxx`（取首个字母）
     *  · 数字：`1` / `13` / `1,3`（视为 1-based 下标）
     *  · 判断题文字：`对/错/正确/错误/T/F/√/×`
     */
    fun parseAnswer(raw: String, optionCount: Int): List<Int> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()

        // 判断题文字
        when (text.uppercase()) {
            "对", "正确", "是", "T", "√", "TRUE" -> return listOf(0)
            "错", "错误", "否", "F", "×", "FALSE" -> return listOf(1)
        }

        // extractLetters 返回的已经是 0-based 下标，这里不能再减一次 'A'
        val letters = extractLetters(text)
        if (letters.isNotEmpty()) {
            return letters.filter { it in 0 until optionCount }
        }

        // 回退：按数字解析（1-based）
        val numbers = Regex("""\d{1,2}""").findAll(text).map { it.value.toInt() - 1 }.toList()
        return numbers.filter { it in 0 until optionCount }
    }

    /** 从字符串中提取 A–Z 字母（忽略大小写、去重、保持顺序）。 */
    private fun extractLetters(text: String): List<Int> {
        val out = LinkedHashSet<Int>()
        for (ch in text.uppercase()) {
            if (ch in 'A'..'Z') out += (ch - 'A')
        }
        return out.toList()
    }
}
