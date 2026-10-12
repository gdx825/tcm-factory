package com.tcmfactory.domain.model

import com.tcmfactory.core.database.entity.QuestionEntity
import com.tcmfactory.core.database.entity.WrongQuestionEntity
import com.tcmfactory.core.quiz.AnswerNormalizer
import com.tcmfactory.core.quiz.QuestionType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** 刷题模式。 */
enum class QuizMode(val label: String) {
    SEQUENTIAL("顺序练习"),
    RANDOM("随机练习"),
    CHAPTER("章节练习"),
    WRONG("错题重做")
}

data class Question(
    val id: Long,
    val subject: String,
    val chapter: String?,
    val type: QuestionType,
    val stem: String,
    val options: List<String>,
    val answers: List<Int>,
    val explanation: String?,
    val difficulty: Int,
    val tags: List<String>,
    val sourceRef: String?,
    val wrongCount: Int = 0
) {
    val isMulti: Boolean get() = type == QuestionType.MULTI

    /** 正确答案的字母显示，如「AC」。 */
    val correctText: String
        get() = answers.sorted().joinToString("") { ('A' + it).toString() }

    /** 判断用户作答是否正确：多选需完全一致，少选/多选均不得分。 */
    fun isCorrect(userAnswers: Collection<Int>): Boolean =
        answers.toSet() == userAnswers.toSet()
}

data class SubjectQuestionCount(val subject: String, val count: Int)

data class ChapterQuestionCount(val chapter: String, val count: Int)

private val quizJson = Json { ignoreUnknownKeys = true }

fun QuestionEntity.toDomain(wrongCount: Int = 0): Question {
    // 统一规范化「选项 + 答案」。历史上有两种写法：题库导入存的是下标数组
    // `[0,3]`，知识包存的是字母/文字 `"D"`、`"对"`。若只按下标解析，
    // 后者会得到空答案，表现为「答错后既不标出正确选项、底部也是空白」。
    // 放在读取端规范化可一次性修好设备上已有的数据，无需迁移。
    val normalized = AnswerNormalizer.normalizeStored(optionsJson, answerJson, type)
    return Question(
        id = id,
        subject = subject,
        chapter = chapter,
        type = runCatching { QuestionType.valueOf(normalized.type) }
            .getOrDefault(QuestionType.SINGLE),
        stem = stem,
        options = normalized.options,
        answers = normalized.answerIndices,
        explanation = explanation,
        difficulty = difficulty,
        tags = com.tcmfactory.domain.model.parseTags(tags),
        sourceRef = sourceRef,
        wrongCount = wrongCount
    )
}

fun WrongQuestionEntity.toDomain(): WrongQuestion = WrongQuestion(
    questionId = questionId,
    wrongCount = wrongCount,
    correctStreak = correctStreak,
    lastWrongAt = lastWrongAt,
    status = status,
    note = note,
    addedAt = addedAt
)

data class WrongQuestion(
    val questionId: Long,
    val wrongCount: Int,
    val correctStreak: Int,
    val lastWrongAt: Long,
    val status: String,
    val note: String?,
    val addedAt: Long
)

fun encodeIntList(values: List<Int>): String =
    quizJson.encodeToString(ListSerializer(Int.serializer()), values)

fun encodeStringList(values: List<String>): String =
    quizJson.encodeToString(ListSerializer(String.serializer()), values)
