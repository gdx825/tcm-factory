package com.tcmfactory.data.repository

import androidx.room.withTransaction
import com.tcmfactory.BuildConfig
import com.tcmfactory.core.database.TcmDatabase
import com.tcmfactory.core.database.dao.CardDao
import com.tcmfactory.core.database.dao.DeckDao
import com.tcmfactory.core.database.dao.KnowledgeDao
import com.tcmfactory.core.database.entity.CardEntity
import com.tcmfactory.core.database.entity.DeckEntity
import com.tcmfactory.core.database.entity.KnowledgePackVersionEntity
import com.tcmfactory.core.database.entity.QuestionEntity
import com.tcmfactory.core.knowledge.CardItemDto
import com.tcmfactory.core.knowledge.ExternalId
import com.tcmfactory.core.knowledge.InstalledPackInfo
import com.tcmfactory.core.knowledge.ItemIssue
import com.tcmfactory.core.knowledge.KnowledgeJson
import com.tcmfactory.core.knowledge.KnowledgeManifest
import com.tcmfactory.core.knowledge.KnowledgePack
import com.tcmfactory.core.knowledge.ManifestEntry
import com.tcmfactory.core.knowledge.PackKind
import com.tcmfactory.core.knowledge.PackSource
import com.tcmfactory.core.knowledge.PackValidator
import com.tcmfactory.core.knowledge.ParsedPack
import com.tcmfactory.core.knowledge.PlanItem
import com.tcmfactory.core.knowledge.QuestionItemDto
import com.tcmfactory.core.knowledge.Sha256
import com.tcmfactory.core.knowledge.UpdatePlan
import com.tcmfactory.core.knowledge.UpdatePlanner
import com.tcmfactory.core.quiz.AnswerNormalizer
import com.tcmfactory.core.datastore.KnowledgeSettingsRepository
import com.tcmfactory.data.network.KnowledgeSourceClient
import com.tcmfactory.domain.model.encodeTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/** 已安装知识包（界面用）。 */
data class InstalledPack(
    val packId: String,
    val packName: String,
    val version: String,
    val kind: String,
    val source: String,
    val itemCount: Int,
    val installedAt: Long,
    val updatedAt: Long?,
    val subject: String?,
    val description: String?,
    val license: String?
)

/** 一次安装的结果，会原样呈现给用户，所以字段尽量可解释。 */
data class InstallReport(
    val packId: String,
    val packName: String,
    val version: String,
    val kind: String,
    val source: String,
    val added: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val itemErrors: List<ItemIssue> = emptyList(),
    val warnings: List<String> = emptyList(),
    val success: Boolean = true,
    val errorMessage: String? = null
) {
    val total: Int get() = added + updated
}

/**
 * 知识库仓库：检查更新、安装、卸载、以及升级后的历史数据补挂。
 *
 * 三条硬性设计约束：
 *  1. **更新内容 ≠ 重置进度**。写卡片只碰内容列，srs* 一律不动。
 *  2. **幂等**。同一包重复安装不会产生重复条目（按 packId + externalId 定位）。
 *  3. **失败不留半成品**。整个包的写入放在一个事务里，出错整体回滚。
 */
@Singleton
class KnowledgeRepository @Inject constructor(
    private val db: TcmDatabase,
    private val knowledgeDao: KnowledgeDao,
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
    private val bookRepository: BookRepository,
    private val sourceClient: KnowledgeSourceClient,
    private val sourceSettings: KnowledgeSettingsRepository
) {

    // ================= 查询 =================

    fun observeInstalledPacks(): Flow<List<InstalledPack>> =
        knowledgeDao.observeAllPacks().map { list -> list.map { it.toDomain() } }

    suspend fun installedPacks(): List<InstalledPack> =
        withContext(Dispatchers.IO) { knowledgeDao.getAllPacks().map { it.toDomain() } }

    suspend fun installedSnapshot(): List<InstalledPackInfo> =
        withContext(Dispatchers.IO) {
            knowledgeDao.getAllPacks().map {
                InstalledPackInfo(
                    packId = it.packId,
                    packName = it.packName,
                    version = it.version,
                    kind = it.kind ?: PackKind.CARDS,
                    source = it.source ?: PackSource.BUILTIN
                )
            }
        }

    // ================= 检查更新 =================

    /**
     * 检查更新。不写数据库，只产出计划，方便界面先给用户看「将要发生什么」。
     */
    suspend fun checkForUpdates(): Result<UpdatePlan> = withContext(Dispatchers.IO) {
        val sources = sourceSettings.current()
        val manifestResult = sourceClient.fetchManifestFromSources(sources.urls)
        val manifest = manifestResult.getOrElse { return@withContext Result.failure(it) }

        val plan = UpdatePlanner.plan(
            manifest = manifest.second,
            installed = installedSnapshot(),
            currentAppVersion = BuildConfig.VERSION_NAME
        ).copy(resolvedBaseUrl = manifest.first)
        sourceSettings.markChecked()
        Result.success(plan)
    }

    /** 仅供界面展示：数据源当前配置。 */
    suspend fun sourceConfig() = sourceSettings.current()

    // ================= 安装 =================

    /**
     * 从网络数据源安装/更新一个包。
     */
    suspend fun installFromSource(
        baseUrl: String,
        entry: ManifestEntry,
        onStage: (String) -> Unit = {},
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): InstallReport = withContext(Dispatchers.IO) {
        onStage("正在下载 ${entry.packName}…")
        val bytesResult = sourceClient.downloadPack(baseUrl, entry, onProgress)
        val bytes = bytesResult.getOrElse {
            return@withContext failureReport(
                entry, PackSource.NETWORK,
                "下载失败：${it.message ?: "未知错误"}"
            )
        }

        onStage("正在校验完整性…")
        if (!Sha256.matches(entry.sha256, bytes)) {
            // 校验失败绝不降级放行 —— 宁可让用户看到失败，也不能把可能被篡改的
            // 知识内容写进学习资料。
            return@withContext failureReport(
                entry, PackSource.NETWORK,
                "完整性校验未通过（SHA-256 不匹配），该包已丢弃，未写入任何内容。" +
                    "可能是下载被截断或数据源内容已变更，请稍后重试。"
            )
        }

        onStage("正在解析与落库…")
        val pack = sourceClient.parsePackSafe(bytes).getOrElse {
            return@withContext failureReport(
                entry, PackSource.NETWORK,
                "解析失败：文件不是合法的知识包格式（${it.message ?: "未知错误"}）"
            )
        }

        installParsedPack(
            pack = pack,
            expectedPackId = entry.packId,
            sha256 = entry.sha256,
            source = PackSource.NETWORK,
            sourceUrl = entry.url,
            fallbackName = entry.packName,
            fallbackKind = entry.kind
        )
    }

    /**
     * 从本地文件安装（无需服务器）。用于用户自行整理的题库/知识包。
     */
    suspend fun installFromBytes(
        bytes: ByteArray,
        displayName: String,
        onStage: (String) -> Unit = {}
    ): InstallReport = withContext(Dispatchers.IO) {
        // 本地文件不受网络层体积保护，必须在这里兜底：
        // 用户选中一个几百 MB 的文件时，readBytes 会直接把进程内存吃光。
        if (bytes.size > MAX_LOCAL_PACK_BYTES) {
            return@withContext failureReport(
                ManifestEntry(
                    packId = displayName, packName = displayName, kind = PackKind.CARDS,
                    version = "0", sha256 = "", url = ""
                ),
                PackSource.FILE,
                "文件过大（${bytes.size / 1024 / 1024} MB），单个知识包上限为 " +
                    "${MAX_LOCAL_PACK_BYTES / 1024 / 1024} MB。"
            )
        }
        onStage("正在解析 ${displayName}…")
        val pack = sourceClient.parsePackSafe(bytes).getOrElse {
            return@withContext failureReport(
                ManifestEntry(
                    packId = displayName, packName = displayName, kind = PackKind.CARDS,
                    version = "0", sha256 = "", url = ""
                ),
                PackSource.FILE,
                "解析失败：文件不是合法的知识包格式（${it.message ?: "未知错误"}）"
            )
        }
        installParsedPack(
            pack = pack,
            expectedPackId = null,
            sha256 = Sha256.of(bytes),
            source = PackSource.FILE,
            sourceUrl = displayName,
            fallbackName = displayName,
            fallbackKind = pack.kind
        )
    }

    private suspend fun installParsedPack(
        pack: KnowledgePack,
        expectedPackId: String?,
        sha256: String?,
        source: String,
        sourceUrl: String?,
        fallbackName: String,
        fallbackKind: String
    ): InstallReport {
        // ---- 整包级校验 ----
        PackValidator.rejectReason(pack, expectedPackId)?.let { reason ->
            return failureReport(
                manifestEntryOf(pack, fallbackName, fallbackKind, sha256, sourceUrl),
                source, "该知识包已被拒绝：$reason"
            )
        }

        val parsed = PackValidator.parse(pack)
        if (PackValidator.shouldRejectAsWhole(parsed)) {
            return failureReport(
                manifestEntryOf(pack, fallbackName, fallbackKind, sha256, sourceUrl),
                source,
                "包内 ${parsed.totalCount} 条中有 ${parsed.errors.size} 条无法解析，" +
                    "错误率过高，已整体拒绝以避免污染本地知识库。"
            )
        }

        // ---- 落库（单事务）----
        val now = System.currentTimeMillis()
        var added = 0
        var updated = 0
        val warnings = ArrayList<String>()

        db.withTransaction {
            when (pack.kind) {
                PackKind.CARDS -> {
                    val r = writeCards(pack, parsed, now)
                    added = r.first
                    updated = r.second
                }
                PackKind.QUESTIONS -> {
                    val r = writeQuestions(pack, parsed, now)
                    added = r.first
                    updated = r.second
                }
                PackKind.BOOK -> {
                    val r = writeBooks(pack, parsed, warnings)
                    added = r.first
                    updated = r.second
                }
            }

            val actualCount = added + updated
            knowledgeDao.upsertPack(
                KnowledgePackVersionEntity(
                    packId = pack.packId,
                    packName = pack.packName.ifBlank { fallbackName },
                    version = pack.version,
                    installedAt = now,
                    cardCount = actualCount,
                    kind = pack.kind,
                    source = source,
                    sourceUrl = sourceUrl,
                    sha256 = sha256,
                    itemCount = actualCount,
                    updatedAt = now,
                    description = pack.description,
                    license = pack.license,
                    subject = pack.subject
                )
            )
        }

        if (parsed.errors.isNotEmpty()) {
            warnings += "有 ${parsed.errors.size} 条内容格式不合规，已跳过（见下方明细）。"
        }
        // 刻意不删除本地已有但本次包中缺失的条目：那会连带清掉用户的学习进度。
        if (source == PackSource.NETWORK && updated > 0) {
            warnings += "已有条目的内容已更新，你的背诵进度（等级 / 到期时间）保持不变。"
        }

        return InstallReport(
            packId = pack.packId,
            packName = pack.packName.ifBlank { fallbackName },
            version = pack.version,
            kind = pack.kind,
            source = source,
            added = added,
            updated = updated,
            skipped = parsed.errors.size,
            itemErrors = parsed.errors,
            warnings = warnings + parsed.warnings.map { it.message },
            success = true
        )
    }

    // ================= 各类型写入 =================

    private suspend fun writeCards(
        pack: KnowledgePack,
        parsed: ParsedPack,
        now: Long
    ): Pair<Int, Int> {
        var added = 0
        var updated = 0
        val deckCache = HashMap<String, Long>()

        parsed.cards.forEachIndexed { index, dto ->
            val deckName = dto.deck?.trim().takeUnless { it.isNullOrEmpty() }
                ?: pack.subject?.trim().takeUnless { it.isNullOrEmpty() }
                ?: pack.packName.ifBlank { "知识库" }

            val deckId = deckCache.getOrPut(deckName) {
                // 用 IGNORE 而非 REPLACE：REPLACE 在 decks 上是「删掉冲突行再插入」，
                // 而 cards.deckId 是级联外键 —— 一旦牌组行被删，其下所有卡片
                // 连同 SRS 学习进度会被一起清空。这里宁可插入失败，也不能误删。
                val inserted = deckDao.insertIgnore(
                    DeckEntity(
                        name = deckName,
                        subject = subjectKeyOf(pack.subject),
                        parentId = null,
                        sortOrder = 0,
                        isBuiltin = true,
                        createdAt = now
                    )
                )
                if (inserted > 0) inserted else knowledgeDao.findDeckIdByName(deckName) ?: inserted
            }

            val existingId = knowledgeDao.findCardId(pack.packId, dto.externalId)
            if (existingId == null) {
                cardDao.insertAll(listOf(dto.toEntity(pack.packId, deckId, now, index)))
                added++
            } else {
                knowledgeDao.updateCardContent(
                    cardId = existingId,
                    front = dto.front,
                    back = dto.back,
                    extra = dto.extra,
                    tags = encodeTags(dto.tags),
                    sourceRef = dto.sourceRef,
                    type = dto.type?.takeIf { it.isNotBlank() } ?: CardEntity.TYPE_BASIC,
                    now = now
                )
                updated++
            }
        }
        return added to updated
    }

    private suspend fun writeQuestions(
        pack: KnowledgePack,
        parsed: ParsedPack,
        now: Long
    ): Pair<Int, Int> {
        var added = 0
        var updated = 0

        parsed.questions.forEachIndexed { index, dto ->
            val subject = dto.subject?.trim().takeUnless { it.isNullOrEmpty() }
                ?: pack.subject?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "未分类"

            // 统一规范化：把字母/文字答案转成下标，并为判断题补上「正确 / 错误」选项。
            // 若直接存 "D"，读取时会按下标数组解析失败 → 答错后界面上不显示正确答案。
            val normalized = AnswerNormalizer.normalize(dto.options, dto.answer, dto.type)
            val type = normalized.type
            val optionsJson = normalized.options.takeIf { it.isNotEmpty() }
                ?.let { KnowledgeJson.encodeToString(STRING_LIST_SERIALIZER, it) }
            val answerJson = KnowledgeJson.encodeToString(INT_LIST_SERIALIZER, normalized.answerIndices)

            val existingId = knowledgeDao.findQuestionId(pack.packId, dto.externalId)
            if (existingId == null) {
                knowledgeDao.insertQuestion(
                    QuestionEntity(
                        subject = subject,
                        chapter = dto.chapter,
                        type = type,
                        stem = dto.stem,
                        optionsJson = optionsJson,
                        answerJson = answerJson,
                        explanation = dto.explanation,
                        difficulty = dto.difficulty,
                        sourceRef = dto.sourceRef,
                        tags = encodeTags(dto.tags),
                        packId = pack.packId,
                        externalId = dto.externalId,
                        createdAt = now + index
                    )
                )
                added++
            } else {
                knowledgeDao.updateQuestionContent(
                    id = existingId,
                    subject = subject,
                    chapter = dto.chapter,
                    type = type,
                    stem = dto.stem,
                    optionsJson = optionsJson,
                    answerJson = answerJson,
                    explanation = dto.explanation,
                    difficulty = dto.difficulty,
                    sourceRef = dto.sourceRef,
                    tags = encodeTags(dto.tags),
                    packId = pack.packId
                )
                updated++
            }
        }
        return added to updated
    }

    private suspend fun writeBooks(
        pack: KnowledgePack,
        parsed: ParsedPack,
        warnings: MutableList<String>
    ): Pair<Int, Int> {
        var added = 0
        var updated = 0
        parsed.books.forEach { dto ->
            if (bookRepository.existsByTitle(dto.title)) {
                // 古籍更新需要重建章节并迁移书签/卡片溯源，风险较高，这一版先不自动覆盖。
                warnings += "《${dto.title}》已存在，未覆盖。如需更新请先在书架中删除。"
                return@forEach
            }
            bookRepository.importBuiltin(
                title = dto.title,
                author = dto.author,
                dynasty = dto.dynasty,
                content = dto.content
            )
            added++
        }
        return added to updated
    }

    // ================= 卸载 =================

    /**
     * 卸载知识包：移除包记录与其条目。
     *
     * 注意这会连带删除用户的背诵进度 —— 因为条目没了，进度也无所依附。
     * 界面上必须二次确认。内置包（BUILTIN）不允许卸载。
     */
    suspend fun uninstall(packId: String): Boolean = withContext(Dispatchers.IO) {
        val pack = knowledgeDao.getPack(packId) ?: return@withContext false
        if (pack.source == PackSource.BUILTIN) return@withContext false

        db.withTransaction {
            when (pack.kind ?: PackKind.CARDS) {
                PackKind.CARDS -> knowledgeDao.deleteCardsOfPack(packId)
                PackKind.QUESTIONS -> knowledgeDao.deleteQuestionsOfPack(packId)
                PackKind.BOOK -> Unit // 古籍交由书架单独管理
            }
            knowledgeDao.deletePack(packId)
        }
        true
    }

    // ================= 历史数据补挂 =================

    /**
     * 为「升级前已存在」的内置内容补挂 packId / externalId。
     *
     * 为什么需要：v1.2.0 及更早版本的数据库里没有这两列，而更新管线靠
     * (packId, externalId) 定位内容。若不做补挂，老用户的内置卡片会被视为
     * 「不属于任何包」，既无法参与更新，也无法在知识库页看到。
     *
     * 幂等：只处理 packId IS NULL 的行，重复调用无副作用。
     * 不改变用户数据：只写两个溯源列，SRS 进度原样保留。
     */
    suspend fun backfillBuiltinPacks(): Int = withContext(Dispatchers.IO) {
        var attached = 0
        val now = System.currentTimeMillis()

        db.withTransaction {
            val deckMap = knowledgeDao.allDecks().associateBy { it.id }

            // ---- 卡片 ----
            val orphanCards = knowledgeDao.getCardsWithoutPack()
            val byPack = LinkedHashMap<String, Int>()
            orphanCards.forEach { card ->
                val deck = deckMap[card.deckId] ?: return@forEach
                // 只补挂内置牌组下的内容。用户自建卡片的 packId 同样为空，
                // 若不排除，它们会被误标成内置包 —— 既让「已安装知识包」的
                // 条目数虚高，也埋下被知识包更新覆盖的隐患。
                if (!deck.isBuiltin) return@forEach
                val packId = baselinePackIdOf(deck.subject)
                knowledgeDao.attachCardToPack(
                    cardId = card.id,
                    packId = packId,
                    externalId = ExternalId.forBuiltinCard(deck.name, card.front)
                )
                byPack[packId] = (byPack[packId] ?: 0) + 1
                attached++
            }
            byPack.forEach { (packId, count) ->
                val existing = knowledgeDao.getPack(packId)
                knowledgeDao.upsertPack(
                    KnowledgePackVersionEntity(
                        packId = packId,
                        packName = baselinePackNameOf(packId),
                        version = existing?.version ?: BASELINE_VERSION,
                        installedAt = existing?.installedAt ?: now,
                        cardCount = count,
                        kind = PackKind.CARDS,
                        source = PackSource.BUILTIN,
                        itemCount = count,
                        updatedAt = now,
                        license = "公有领域 / 国家标准事实数据 / 自编条目",
                        description = "随应用内置的基础知识卡片，可离线使用。"
                    )
                )
            }

            // ---- 题目 ----
            val orphanQuestions = knowledgeDao.getQuestionsWithoutPack()
            if (orphanQuestions.isNotEmpty()) {
                orphanQuestions.forEach { q ->
                    knowledgeDao.attachQuestionToPack(
                        id = q.id,
                        packId = BASELINE_QUESTIONS_PACK,
                        externalId = ExternalId.forBuiltinQuestion(q.subject, q.stem)
                    )
                }
                val existing = knowledgeDao.getPack(BASELINE_QUESTIONS_PACK)
                knowledgeDao.upsertPack(
                    KnowledgePackVersionEntity(
                        packId = BASELINE_QUESTIONS_PACK,
                        packName = "示例题库（内置）",
                        version = existing?.version ?: BASELINE_VERSION,
                        installedAt = existing?.installedAt ?: now,
                        cardCount = orphanQuestions.size,
                        kind = PackKind.QUESTIONS,
                        source = PackSource.BUILTIN,
                        itemCount = orphanQuestions.size,
                        updatedAt = now,
                        license = "自编示例题，仅用于演示格式",
                        description = "内置示例题目，同时作为题库 JSON 格式的活文档。"
                    )
                )
            }
        }
        attached
    }

    // ================= 辅助 =================

    private fun CardItemDto.toEntity(packId: String, deckId: Long, now: Long, index: Int) = CardEntity(
        deckId = deckId,
        type = type?.takeIf { it.isNotBlank() } ?: CardEntity.TYPE_BASIC,
        front = front,
        back = back,
        extra = extra,
        tags = encodeTags(tags),
        sourceRef = sourceRef,
        sortOrder = index,
        isSuspended = false,
        createdAt = now,
        updatedAt = now,
        srsState = "NEW",
        srsLevel = 0,
        srsEase = 2.5,
        srsIntervalMin = 0.0,
        srsDueAt = now,
        packId = packId,
        externalId = externalId
    )

    private fun manifestEntryOf(
        pack: KnowledgePack,
        fallbackName: String,
        fallbackKind: String,
        sha256: String?,
        sourceUrl: String?
    ) = ManifestEntry(
        packId = pack.packId,
        packName = pack.packName.ifBlank { fallbackName },
        kind = pack.kind.ifBlank { fallbackKind },
        version = pack.version,
        sha256 = sha256.orEmpty(),
        url = sourceUrl.orEmpty()
    )

    private fun failureReport(entry: ManifestEntry, source: String, message: String) = InstallReport(
        packId = entry.packId,
        packName = entry.packName,
        version = entry.version,
        kind = entry.kind,
        source = source,
        success = false,
        errorMessage = message
    )

    private fun KnowledgePackVersionEntity.toDomain() = InstalledPack(
        packId = packId,
        packName = packName,
        version = version,
        kind = kind ?: PackKind.CARDS,
        source = source ?: PackSource.BUILTIN,
        itemCount = itemCount ?: cardCount,
        installedAt = installedAt,
        updatedAt = updatedAt,
        subject = subject,
        description = description,
        license = license
    )

    private fun subjectKeyOf(name: String?): String = when (name?.trim()) {
        "中医基础理论", "中基" -> "TCM_BASIC"
        "中医诊断学", "中诊" -> "TCM_DIAG"
        "中药学" -> "MATERIA_MEDICA"
        "方剂学" -> "FORMULA"
        "中医内科学" -> "INTERNAL_MED"
        "针灸学" -> "ACUPUNCTURE"
        "中医经典", "经典条文" -> "CLASSICS"
        else -> "CUSTOM"
    }

    private fun baselinePackIdOf(subject: String): String = when (subject) {
        "TCM_BASIC" -> "$BASELINE_PREFIX.basic"
        "TCM_DIAG" -> "$BASELINE_PREFIX.diag"
        "MATERIA_MEDICA" -> "$BASELINE_PREFIX.materia"
        "FORMULA" -> "$BASELINE_PREFIX.formula"
        "INTERNAL_MED" -> "$BASELINE_PREFIX.internal"
        "ACUPUNCTURE" -> "$BASELINE_PREFIX.acupuncture"
        "CLASSICS" -> "$BASELINE_PREFIX.classics"
        else -> "$BASELINE_PREFIX.misc"
    }

    private fun baselinePackNameOf(packId: String): String {
        val suffix = packId.removePrefix("$BASELINE_PREFIX.")
        return "内置 · " + when (suffix) {
            "basic" -> "中医基础理论"
            "diag" -> "中医诊断学"
            "materia" -> "中药学"
            "formula" -> "方剂学"
            "internal" -> "中医内科学"
            "acupuncture" -> "针灸学"
            "classics" -> "经典条文"
            else -> "综合"
        }
    }

    companion object {
        const val BASELINE_PREFIX = "tcm.baseline"
        const val BASELINE_QUESTIONS_PACK = "$BASELINE_PREFIX.questions"
        const val BASELINE_VERSION = "1.0.0"

        /** 本地文件导入的体积上限。与网络下载保持同一量级。 */
        const val MAX_LOCAL_PACK_BYTES = 32L * 1024 * 1024
    }
}

/** List<String> 的序列化器。显式给出类型参数，否则 encodeToString 无法推断。 */
private val STRING_LIST_SERIALIZER = ListSerializer(String.serializer())

/** 答案统一以下标列表入库，读取端才不必猜测格式。 */
private val INT_LIST_SERIALIZER = ListSerializer(Int.serializer())
