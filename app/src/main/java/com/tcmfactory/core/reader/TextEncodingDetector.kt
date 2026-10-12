package com.tcmfactory.core.reader

import java.nio.charset.Charset

/**
 * 编码探测结果。
 *
 * @param charsetName 选定编码名
 * @param confidence  置信度 [0, 1]
 * @param bomLength   BOM 字节数（解码时需跳过）
 * @param candidates  各候选编码的评分，从高到低（用于导入预览页展示与手动切换）
 * @param decodedPreview 用选定编码解码出的前若干字符，供用户肉眼确认
 */
data class EncodingDetection(
    val charsetName: String,
    val confidence: Float,
    val bomLength: Int,
    val candidates: List<CandidateScore>,
    val decodedPreview: String
) {
    data class CandidateScore(val charsetName: String, val score: Float)
}

/**
 * 古籍 TXT 编码探测器。
 *
 * 难点在于：**GB18030 对任意字节序列几乎都能"解出东西"而不抛异常**，
 * 所以不能靠 try/catch 判断，必须对解码结果做质量评分。
 *
 * 策略（按优先级）：
 *  1. BOM 命中 → 直接确定，置信度 1.0
 *  2. 通过 RFC 3629 严格 UTF-8 校验 → 判定 UTF-8
 *  3. 对 GB18030 / GBK / Big5 / UTF-16LE 逐个解码并打分，取最高分
 *
 * 即便如此仍可能误判（尤其 GBK 与 Big5 字节范围重叠），
 * 因此导入预览页必须提供**手动切换编码**入口，这是最后一道保险。
 */
object TextEncodingDetector {

    private const val SAMPLE_SIZE = 64 * 1024

    private val CANDIDATE_CHARSETS = listOf("GB18030", "GBK", "Big5", "UTF-16LE", "UTF-16BE")

    fun detect(bytes: ByteArray): EncodingDetection {
        if (bytes.isEmpty()) {
            return EncodingDetection("UTF-8", 1f, 0, emptyList(), "")
        }

        // ---------- 1. BOM ----------
        detectBom(bytes)?.let { (name, bomLen) ->
            val text = decodeAfterBom(bytes, name, bomLen)
            return EncodingDetection(
                charsetName = name,
                confidence = 1f,
                bomLength = bomLen,
                candidates = listOf(EncodingDetection.CandidateScore(name, 1f)),
                decodedPreview = text.take(200)
            )
        }

        val sample = if (bytes.size > SAMPLE_SIZE) bytes.copyOf(SAMPLE_SIZE) else bytes

        // ---------- 2. UTF-8 严格校验 ----------
        if (isValidUtf8(sample)) {
            val text = String(bytes, Charsets.UTF_8)
            return EncodingDetection(
                charsetName = "UTF-8",
                confidence = 0.95f,
                bomLength = 0,
                candidates = listOf(EncodingDetection.CandidateScore("UTF-8", 0.95f)),
                decodedPreview = text.take(200)
            )
        }

        // ---------- 3. 候选打分 ----------
        val scores = CANDIDATE_CHARSETS.mapNotNull { name ->
            val charset = charsetOrNull(name) ?: return@mapNotNull null
            val text = runCatching { String(sample, charset) }.getOrNull() ?: return@mapNotNull null
            EncodingDetection.CandidateScore(name, scoreDecoded(text))
        }.sortedByDescending { it.score }

        val best = scores.firstOrNull() ?: EncodingDetection.CandidateScore("UTF-8", 0f)
        val charset = charsetOrNull(best.charsetName) ?: Charsets.UTF_8
        val fullText = runCatching { String(bytes, charset) }.getOrDefault("")

        // 前两名分差过小 → 置信度压低，提示用户在预览页确认
        val gap = if (scores.size >= 2) best.score - scores[1].score else best.score
        val confidence = best.score.coerceIn(0f, 1f) * if (gap < 0.05f) 0.6f else 1f

        return EncodingDetection(
            charsetName = best.charsetName,
            confidence = confidence,
            bomLength = 0,
            candidates = scores,
            decodedPreview = fullText.take(200)
        )
    }

    /** 用指定编码解码（导入预览页手动切换编码时调用）。 */
    fun decode(bytes: ByteArray, charsetName: String): String {
        val bomLen = detectBom(bytes)?.takeIf { it.first == charsetName }?.second ?: 0
        val charset = charsetOrNull(charsetName) ?: Charsets.UTF_8
        return runCatching {
            if (bomLen > 0) String(bytes, bomLen, bytes.size - bomLen, charset)
            else String(bytes, charset)
        }.getOrDefault("")
    }

    // ------------------------------------------------------------------

    private fun detectBom(bytes: ByteArray): Pair<String, Int>? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) return "UTF-8" to 3

        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return "UTF-16LE" to 2
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return "UTF-16BE" to 2
        }
        return null
    }

    private fun decodeAfterBom(bytes: ByteArray, charsetName: String, bomLen: Int): String {
        val charset = charsetOrNull(charsetName) ?: Charsets.UTF_8
        return runCatching { String(bytes, bomLen, bytes.size - bomLen, charset) }.getOrDefault("")
    }

    private fun charsetOrNull(name: String): Charset? =
        runCatching { Charset.forName(name) }.getOrNull()

    /**
     * RFC 3629 严格 UTF-8 校验：拒绝过长编码、代理对区间与非法续字节。
     * 不能只用 `String(bytes, UTF_8)` 判断，因为后者会把非法序列静默替换成 U+FFFD。
     */
    fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b < 0x80 -> i++
                b in 0xC2..0xDF -> {
                    if (i + 1 >= bytes.size || !isContinuation(bytes[i + 1])) return false
                    i += 2
                }
                b in 0xE0..0xEF -> {
                    if (i + 2 >= bytes.size) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    val b2 = bytes[i + 2].toInt() and 0xFF
                    if (!isContinuation(bytes[i + 1]) || !isContinuation(bytes[i + 2])) return false
                    if (b == 0xE0 && b1 < 0xA0) return false   // 过长编码
                    if (b == 0xED && b1 >= 0xA0) return false  // 代理对
                    i += 3
                }
                b in 0xF0..0xF4 -> {
                    if (i + 3 >= bytes.size) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    if (!isContinuation(bytes[i + 1]) ||
                        !isContinuation(bytes[i + 2]) ||
                        !isContinuation(bytes[i + 3])
                    ) return false
                    if (b == 0xF0 && b1 < 0x90) return false
                    if (b == 0xF4 && b1 >= 0x90) return false
                    i += 4
                }
                else -> return false
            }
        }
        return true
    }

    private fun isContinuation(b: Byte): Boolean {
        val v = b.toInt() and 0xFF
        return v in 0x80..0xBF
    }

    /**
     * 解码质量评分 [0, 1]。
     *
     * 关键洞察：GBK 文本用 Big5 解码后，字节同样会映射到 CJK 区，
     * 所以"有没有乱码"看不出来；但映射出的字会大量落在**生僻字/符号区**。
     * 因此这里同时考察：
     *   · 非法字符率（U+FFFD、控制字符）
     *   · 汉字占比与"常用汉字"占比
     */
    fun scoreDecoded(text: String): Float {
        if (text.isEmpty()) return 0f

        var valid = 0
        var invalid = 0
        var cjk = 0
        var commonCjk = 0

        for (ch in text) {
            val code = ch.code
            when {
                code == 0xFFFD -> invalid += 3
                code < 0x09 -> invalid += 2
                code in 0x0E..0x1F -> invalid += 2
                code in 0xE000..0xF8FF -> invalid += 2          // 私用区
                isCjk(ch) -> {
                    cjk++
                    valid++
                    if (isCommonCjk(ch)) commonCjk++
                }
                ch.isWhitespace() || ch.isLetterOrDigit() -> valid++
                isCommonPunctuation(ch) -> valid++
                else -> invalid++
            }
        }

        val total = valid + invalid
        if (total == 0) return 0f

        val validRatio = valid.toFloat() / total
        val cjkRatio = cjk.toFloat() / total
        val commonRatio = if (cjk == 0) 0f else commonCjk.toFloat() / cjk

        return (validRatio * 0.4f + cjkRatio * 0.25f + commonRatio * 0.35f).coerceIn(0f, 1f)
    }

    private fun isCjk(ch: Char): Boolean {
        val code = ch.code
        return code in 0x4E00..0x9FFF ||      // CJK 统一汉字
            code in 0x3400..0x4DBF ||          // 扩展 A
            code in 0xF900..0xFAFF ||          // 兼容汉字
            code in 0x3000..0x303F            // CJK 符号与标点
    }

    /**
     * 常用汉字判定。
     * 采用 GB2312 一级/二级字库在 Unicode 中的**分布特征**：
     * GB2312 汉字按拼音排序，对应 U+4E00 起的一段密集区间；
     * 误码产生的生僻字则零散分布在 U+9FA6 之后及扩展区。
     */
    private fun isCommonCjk(ch: Char): Boolean {
        val code = ch.code
        if (code in 0x4E00..0x9FA5) return true            // GB2312/GBK 主区，覆盖绝大多数常用字
        if (code in 0x3000..0x303F) return true            // 中文标点
        if (code in 0xFF01..0xFF5E) return true            // 全角 ASCII
        return false
    }

    private fun isCommonPunctuation(ch: Char): Boolean {
        val code = ch.code
        return code in 0x2000..0x206F ||   // 通用标点
            code == 0x00B7.toInt() ||      // ·
            code in 0x2010..0x2027
    }
}
