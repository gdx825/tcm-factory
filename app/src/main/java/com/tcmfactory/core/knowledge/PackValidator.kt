package com.tcmfactory.core.knowledge

import kotlinx.serialization.json.decodeFromJsonElement

/** 单条目的问题记录。会进入导入报告，方便用户与开发者定位。 */
data class ItemIssue(
    val index: Int,
    val externalId: String?,
    val message: String
)

/**
 * 解析后的知识包内容，已可直接落库。
 */
data class ParsedPack(
    val pack: KnowledgePack,
    val cards: List<CardItemDto>,
    val questions: List<QuestionItemDto>,
    val books: List<BookItemDto>,
    val errors: List<ItemIssue>,
    val warnings: List<ItemIssue>
) {
    val validCount: Int get() = cards.size + questions.size + books.size
    val totalCount: Int get() = validCount + errors.size

    /** 错误条目占比。整包损坏时用它兜底拒绝。 */
    val errorRatio: Double get() = if (totalCount == 0) 0.0 else errors.size.toDouble() / totalCount
}

/**
 * 知识包校验器。
 *
 * 采用**两段式**策略，与第三阶段题库导入保持一致：
 *  1. 整包级硬性校验（[rejectReason]）：结构本身不可信时整体拒绝，一条都不落库。
 *  2. 条目级容错（[parse]）：个别条目有问题只跳过它并记入报告。
 *
 * 但知识库比题库更严：错误率超过 [MAX_ITEM_ERROR_RATIO] 说明包整体质量可疑，
 * 宁可整体回退，也不要把半坏的知识灌进用户的学习资料里。
 */
object PackValidator {

    /** 支持的最高 schema 版本。遇到更高版本应提示用户升级 App，而非尝试解析。 */
    const val SUPPORTED_SCHEMA = 1

    const val MAX_ITEM_ERROR_RATIO = 0.30

    /**
     * 触发「按比例拒绝」所需的最少坏条目数。
     *
     * 没有这道闸门，小包会被比例误伤：一个 3 条的小包坏 1 条就是 33%，
     * 超过阈值直接整包拒绝 —— 但 1 条坏数据远不足以判断整个包不可信。
     * 大包则不同，坏 5 条以上且占比又高，才是真正的「内容可疑」信号。
     */
    const val MIN_ERRORS_FOR_RATIO_REJECT = 3
    const val MAX_ITEMS = 20_000
    const val MAX_CONTENT_CHARS = 8 * 1024 * 1024

    private val PACK_ID_PATTERN = Regex("^[a-z0-9][a-z0-9._-]{1,63}$")

    /**
     * 整包级拒绝原因（返回 null 表示通过）。
     * @param expectedPackId 从索引预期的 packId，传入则要求严格一致（防张冠李戴）。
     */
    fun rejectReason(pack: KnowledgePack, expectedPackId: String? = null): String? = when {
        pack.schemaVersion > SUPPORTED_SCHEMA ->
            "知识包 schema 版本为 ${pack.schemaVersion}，当前应用仅支持到 $SUPPORTED_SCHEMA，请升级应用"
        expectedPackId != null && pack.packId != expectedPackId ->
            "包标识不一致：索引声明为 $expectedPackId，包内实际为 ${pack.packId}"
        pack.packId.isBlank() -> "packId 为空"
        !PACK_ID_PATTERN.matches(pack.packId) -> "packId 含非法字符：${pack.packId}"
        pack.version.isBlank() -> "version 为空"
        pack.kind !in PackKind.ALL -> "未知的 kind：${pack.kind}"
        pack.items.isEmpty() -> "包内没有任何条目"
        pack.items.size > MAX_ITEMS -> "包内条目数 ${pack.items.size} 超过上限 $MAX_ITEMS"
        else -> null
    }

    /**
     * 逐条解析并校验。返回结果中 [ParsedPack.errors] 非空表示存在被跳过的坏条目。
     */
    fun parse(pack: KnowledgePack): ParsedPack {
        val cards = ArrayList<CardItemDto>()
        val questions = ArrayList<QuestionItemDto>()
        val books = ArrayList<BookItemDto>()
        val errors = ArrayList<ItemIssue>()
        val warnings = ArrayList<ItemIssue>()
        val seenIds = HashSet<String>()
        var derivedCount = 0

        pack.items.forEachIndexed { index, element ->
            val parsed: Any? = when (pack.kind) {
                PackKind.CARDS -> runCatching { KnowledgeJson.decodeFromJsonElement<CardItemDto>(element) }.getOrNull()
                PackKind.QUESTIONS -> runCatching { KnowledgeJson.decodeFromJsonElement<QuestionItemDto>(element) }.getOrNull()
                PackKind.BOOK -> runCatching { KnowledgeJson.decodeFromJsonElement<BookItemDto>(element) }.getOrNull()
                else -> null
            }

            if (parsed == null) {
                errors += ItemIssue(index, null, "条目 JSON 结构无法解析（可能缺少必填字段）")
                return@forEachIndexed
            }

            val rawId = when (parsed) {
                is CardItemDto -> parsed.externalId
                is QuestionItemDto -> parsed.externalId
                is BookItemDto -> parsed.externalId
                else -> ""
            }.trim()

            // 未提供 externalId 时按内容派生，而不是直接判错。
            // 一个几千条的知识库要手写几千个 ID 既不现实、也必然写错；
            // 派生规则是 sha256(关键字段) 取前 16 位，天然稳定且幂等 ——
            // 同样的内容无论出现在第几条，得到的 ID 都一样。
            val externalId = if (rawId.isEmpty()) {
                derivedCount++
                deriveExternalId(pack, parsed)
            } else {
                rawId
            }

            if (externalId.isEmpty()) {
                errors += ItemIssue(index, null, "externalId 缺失且无法从内容派生（关键字段为空）")
                return@forEachIndexed
            }
            if (!seenIds.add(externalId)) {
                errors += ItemIssue(index, externalId, "externalId 在本包内重复，已跳过后续重复项")
                return@forEachIndexed
            }

            when (parsed) {
                is CardItemDto -> {
                    val msg = validateCard(parsed)
                    // 必须把派生出的 ID 写回 DTO：下游（KnowledgeRepository）是按
                    // dto.externalId 去查已有条目的，若这里不回写，空 ID 会让每次
                    // 更新都被判为「新条目」而重复插入。
                    if (msg != null) errors += ItemIssue(index, externalId, msg)
                    else cards += parsed.copy(externalId = externalId)
                }
                is QuestionItemDto -> {
                    val msg = validateQuestion(parsed)
                    if (msg != null) errors += ItemIssue(index, externalId, msg)
                    else questions += parsed.copy(externalId = externalId)
                }
                is BookItemDto -> {
                    val msg = validateBook(parsed)
                    if (msg != null) errors += ItemIssue(index, externalId, msg) else {
                        books += parsed.copy(externalId = externalId)
                        if (parsed.content.length > MAX_CONTENT_CHARS) {
                            warnings += ItemIssue(index, externalId, "正文长度 ${parsed.content.length} 字，可能影响首次导入速度")
                        }
                    }
                }
            }
        }

        // kind 与内容类型不匹配（比如声明 cards 却全是 question 结构）应作为警告暴露
        val expectedType = when (pack.kind) {
            PackKind.CARDS -> cards.size
            PackKind.QUESTIONS -> questions.size
            PackKind.BOOK -> books.size
            else -> 0
        }
        if (expectedType == 0 && pack.items.isNotEmpty()) {
            warnings += ItemIssue(-1, null, "包声明为 ${pack.kind}，但没有任何条目通过校验")
        }
        if (derivedCount > 0) {
            warnings += ItemIssue(
                -1, null,
                "有 $derivedCount 条未提供 externalId，已按内容自动派生（内容不变则跨版本稳定匹配）"
            )
        }

        return ParsedPack(pack, cards, questions, books, errors, warnings)
    }

    /**
     * 按内容派生稳定 ID。规则必须与打包工具 `derive_external_id` 保持一致，
     * 否则同一份内容经两条路径入库会得到不同 ID，进而产生重复条目。
     */
    private fun deriveExternalId(pack: KnowledgePack, item: Any?): String {
        val fallback = pack.subject?.trim().takeUnless { it.isNullOrEmpty() }
            ?: pack.packName.trim()
        return when (item) {
            is CardItemDto -> {
                val deck = item.deck?.trim().takeUnless { it.isNullOrEmpty() } ?: fallback
                ExternalId.of(deck, item.front)
            }
            is QuestionItemDto -> {
                val subject = item.subject?.trim().takeUnless { it.isNullOrEmpty() } ?: fallback
                ExternalId.of(subject, item.stem)
            }
            is BookItemDto -> ExternalId.of(item.title)
            else -> ""
        }
    }

    /** 错误率是否高到应当整体回退。 */
    fun shouldRejectAsWhole(parsed: ParsedPack): Boolean {
        // 一条有效内容都没有，这个包无论如何都不该被接受
        if (parsed.validCount == 0) return true
        // 坏条目太少时不看比例，避免小包被误伤
        if (parsed.errors.size < MIN_ERRORS_FOR_RATIO_REJECT) return false
        return parsed.errorRatio > MAX_ITEM_ERROR_RATIO
    }

    // ---------------- 条目级规则 ----------------

    private fun validateCard(dto: CardItemDto): String? = when {
        dto.front.isBlank() && dto.back.isBlank() -> "卡片正反面均为空"
        dto.front.isBlank() -> "卡片正面为空"
        dto.back.isBlank() -> "卡片背面为空"
        else -> null
    }

    private fun validateQuestion(dto: QuestionItemDto): String? = when {
        dto.stem.isBlank() -> "题干为空"
        dto.answer == null -> "缺少答案字段"
        else -> null
    }

    private fun validateBook(dto: BookItemDto): String? = when {
        dto.title.isBlank() -> "古籍标题为空"
        dto.content.isBlank() -> "古籍正文为空"
        else -> null
    }
}
