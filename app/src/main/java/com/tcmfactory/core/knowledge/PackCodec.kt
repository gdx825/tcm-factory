package com.tcmfactory.core.knowledge

import kotlinx.serialization.decodeFromString

/**
 * 知识包编解码。
 *
 * 抽成独立对象而非塞在下载客户端里，是为了让「字节 → 结构化对象」这一步
 * 可以在纯 JVM 上单测；它同时承担 BOM 容错，
 * 而 BOM 恰恰是那种「本地测没问题、用户真机一导入就失败」的典型坑。
 */
object PackCodec {

    /**
     * 去掉 UTF-8 BOM。
     * 带 BOM 的 JSON 会让解析器在第一个字符（U+FEFF）处直接报错，
     * 而很多 Windows 编辑器保存 UTF-8 时会默认加上 BOM。
     */
    fun normalizeText(bytes: ByteArray): String {
        var text = bytes.decodeToString()
        if (text.startsWith('\uFEFF')) text = text.substring(1)
        return text
    }

    fun parseManifest(bytes: ByteArray): KnowledgeManifest =
        KnowledgeJson.decodeFromString(normalizeText(bytes))

    fun parsePack(bytes: ByteArray): KnowledgePack =
        KnowledgeJson.decodeFromString(normalizeText(bytes))

    fun parseManifestSafe(bytes: ByteArray): Result<KnowledgeManifest> =
        runCatching { parseManifest(bytes) }

    fun parsePackSafe(bytes: ByteArray): Result<KnowledgePack> =
        runCatching { parsePack(bytes) }
}
