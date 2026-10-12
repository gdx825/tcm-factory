package com.tcmfactory.core.knowledge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * 知识库分发格式。
 *
 * 设计目标：**一套格式覆盖背诵卡 / 题库 / 古籍三类内容**，因为它们的更新
 * 生命周期完全一样（检查版本 → 下载 → 校验 → 落库），没必要为每类各造一套。
 *
 * 分两层：
 *  · [KnowledgeManifest]：远端索引，体积必须小（只含元信息，不含内容），
 *    客户端先拉它做版本比对，再决定下载哪些包。
 *  · [KnowledgePack]：单包内容，下载后整体校验。
 *
 * 为什么不用「一个巨型 JSON 装下全部知识库」：中医知识库终态会到几千条，
 * 单文件意味着每次更新都要重下全部内容，且任一字节损坏就整库不可用。
 * 按学科分包后，更新「中药学」不会动到「方剂学」，也支持只装自己需要的科目。
 */
object PackKind {
    const val CARDS = "cards"
    const val QUESTIONS = "questions"
    const val BOOK = "book"

    val ALL = setOf(CARDS, QUESTIONS, BOOK)
}

/** 包的来源，用于界面展示与「用户自建内容不被覆盖」的判定。 */
object PackSource {
    const val BUILTIN = "BUILTIN"
    const val NETWORK = "NETWORK"
    const val FILE = "FILE"
}

/**
 * 解析知识库 JSON 的共享配置。
 *
 * `ignoreUnknownKeys = true` 是关键：服务端将来加字段时，
 * 旧版客户端必须还能正常解析，否则一次服务端升级会让所有旧用户更新失败。
 */
val KnowledgeJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}

// ==================================================================
//  远端索引
// ==================================================================

@Serializable
data class KnowledgeManifest(
    val schemaVersion: Int = 1,
    /** ISO-8601 时间戳，仅供展示。 */
    val generatedAt: String? = null,
    /**
     * 要求的最低应用版本。低于此版本时客户端整体拒绝更新，
     * 避免新格式包被旧客户端解析出错误内容。
     */
    val minAppVersion: String? = null,
    val packs: List<ManifestEntry> = emptyList()
)

@Serializable
data class ManifestEntry(
    val packId: String,
    val packName: String,
    val kind: String,
    val version: String,
    val subject: String? = null,
    val description: String? = null,
    val license: String? = null,
    val itemCount: Int = 0,
    val bytes: Long = 0,
    /** 小写十六进制 SHA-256。下载后强制校验，不匹配即丢弃。 */
    val sha256: String,
    /** 相对路径或绝对 URL。相对路径基于数据源 baseUrl 解析。 */
    val url: String
)

// ==================================================================
//  单包内容
// ==================================================================

@Serializable
data class KnowledgePack(
    val schemaVersion: Int = 1,
    val packId: String,
    val packName: String,
    val kind: String,
    val version: String,
    val subject: String? = null,
    val description: String? = null,
    /** 授权说明。内置包全部为公有领域 / 国家标准事实数据 / 自编条目。 */
    val license: String? = null,
    /** 数据来源与校验说明，会展示给用户，便于溯源。 */
    val sourceNote: String? = null,
    /**
     * 条目列表。用 [JsonElement] 而非具体类型，是为了让同一份外层结构
     * 承载三种异构内容；解析时再按 [kind] 反序列化为对应 DTO。
     */
    val items: List<JsonElement> = emptyList()
)

// ==================================================================
//  条目 DTO（按 kind 三选一）
// ==================================================================

/** kind = cards */
@Serializable
data class CardItemDto(
    /**
     * 包内稳定标识，更新时据此匹配，保证用户学习进度不丢。
     *
     * 可省略 —— 省略时由 [PackValidator] 按 (牌组, 正面) 内容派生。
     * 注意必须给默认值：若声明为必填，缺少该字段的 JSON 会在**反序列化阶段**
     * 就抛异常，派生逻辑根本没机会执行。
     */
    val externalId: String = "",
    val deck: String? = null,
    val front: String = "",
    val back: String = "",
    val extra: String? = null,
    val tags: List<String> = emptyList(),
    val sourceRef: String? = null,
    /** BASIC / CLOZE / QA / ACUPOINT */
    val type: String? = null
)

/** kind = questions。字段与第三阶段题库格式保持一致，复用同一套答案容错解析。 */
@Serializable
data class QuestionItemDto(
    /** 可省略，省略时按 (科目, 题干) 内容派生。 */
    val externalId: String = "",
    val subject: String? = null,
    val chapter: String? = null,
    @SerialName("type") val type: String? = null,
    val stem: String = "",
    val options: List<String> = emptyList(),
    val answer: JsonElement? = null,
    val explanation: String? = null,
    val difficulty: Int = 0,
    val sourceRef: String? = null,
    val tags: List<String> = emptyList()
)

/** kind = book。古籍正文以整段文本传输，客户端切分章节。 */
@Serializable
data class BookItemDto(
    /** 可省略，省略时按书名派生。 */
    val externalId: String = "",
    val title: String = "",
    val author: String? = null,
    val dynasty: String? = null,
    /** 纯文本（已归一化）。客户端走 ChapterSplitter 切分。 */
    val content: String = ""
)
