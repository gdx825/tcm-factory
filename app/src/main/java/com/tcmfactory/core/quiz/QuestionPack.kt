package com.tcmfactory.core.quiz

import kotlinx.serialization.Serializable

/**
 * 题库 JSON 交换格式（schemaVersion = 1，已冻结）。
 *
 * 设计原则：**对人友好优先于对机器友好**。用户拿到的题库来自各种渠道
 * （自己整理、机构导出、Excel 另存），因此：
 *  · 除 stem / answer 外所有字段都可省略
 *  · 答案既支持字母（`"A"` / `"AC"` / `"A,C"`）也支持数字（`"1"`）
 *  · 选项带不带 `A.` 前缀都能识别
 *  · 判断题可以只写 type=JUDGE 而不写 options，自动补「正确 / 错误」
 */
@Serializable
data class QuestionPackDto(
    val schemaVersion: Int = 1,
    val packName: String = "",
    val packVersion: String = "1.0.0",
    /** 科目枚举，与卡片科目保持一致；无法归类时用 CUSTOM */
    val subject: String = "CUSTOM",
    val sourceRef: String? = null,
    val questions: List<QuestionDto> = emptyList()
)

@Serializable
data class QuestionDto(
    val chapter: String? = null,
    /**
     * SINGLE 单选 / MULTI 多选 / JUDGE 判断。
     * **留空则按答案形态自动推断** —— 所以这里不能用 "SINGLE" 作默认值，
     * 否则「未提供类型」与「显式声明单选」无法区分，推断逻辑永远不会生效。
     */
    val type: String = "",
    val stem: String = "",
    val options: List<String>? = null,
    val answer: String = "",
    val explanation: String? = null,
    val difficulty: Int = 0,
    val tags: List<String> = emptyList(),
    val sourceRef: String? = null
)

enum class QuestionType {
    SINGLE, MULTI, JUDGE;

    val label: String
        get() = when (this) {
            SINGLE -> "单选"
            MULTI -> "多选"
            JUDGE -> "判断"
        }
}

/**
 * 校验通过的一道题。选项已归一化为**不含字母前缀**的纯文本，
 * 答案已转换为**下标列表**，便于与用户作答直接比较。
 */
data class ValidatedQuestion(
    val index: Int,
    val chapter: String?,
    val type: QuestionType,
    val stem: String,
    val options: List<String>,
    val answers: List<Int>,
    val explanation: String?,
    val difficulty: Int,
    val tags: List<String>,
    val sourceRef: String?
) {
    /** 是否为多选（答对需完全一致且不可少选）。 */
    val isMulti: Boolean get() = type == QuestionType.MULTI

    val correctLettersText: String
        get() = answers.sorted().joinToString("") { ('A' + it).toString() }
}
