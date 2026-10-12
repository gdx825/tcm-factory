package com.tcmfactory.core.reader

/**
 * 清洗选项。每一项都对应导入预览页上的一个可开关选项，
 * 让用户能看见处理过程并自行取舍 —— 古籍 TXT 质量太不可控，
 * 静默处理完直接丢给用户一个结果是差评的主要来源。
 */
data class CleanOptions(
    val normalizeNewlines: Boolean = true,
    val stripInvisible: Boolean = true,
    val removeNoiseLines: Boolean = true,
    /** 智能段落重排：把"硬换行"型 TXT 的碎行合并回自然段。 */
    val mergeHardWraps: Boolean = true,
    /** 全角数字字母转半角。默认关闭，因为古籍缩进常用全角空格。 */
    val fullWidthToHalfWidth: Boolean = false,
    val normalizeSpaces: Boolean = true
)

data class CleanResult(
    val text: String,
    /** 被合并掉的换行数，用于向用户展示"已重排 N 处硬换行" */
    val mergedBreaks: Int,
    /** 被剔除的噪音行数 */
    val removedLines: Int
) {
    val charCount: Int get() = text.length
}

/**
 * 古籍文本清洗器。
 *
 * 中文古籍 TXT 的典型脏数据：
 *  · 硬换行（每行固定 20–40 字就断行，一句话被切成十几行）→ 阅读体验灾难
 *  · 页码 / 水印 / 扫描痕迹行
 *  · 零宽字符、BOM 残留
 *  · 全角半角混用
 */
object TextCleaner {

    private const val INVISIBLE_CHARS = "\uFEFF\u200B\u200C\u200D\u200E\u200F\u00AD\u2060"

    /** 句读终结符：这些字符结尾的行不会与下一行合并。 */
    private const val SENTENCE_ENDINGS = "。！？；：…—!?;:"

    /** 右闭合类标点也算句读终结（如 ——」』）】》）。 */
    private const val CLOSING_MARKS = "」』）】》〕〉\"\u2019\u201D"

    private val HEADING_PATTERNS = listOf(
        Regex("""^第[一二三四五六七八九十百千零〇\d]+[卷篇章节回]"""),
        Regex("""^[卷篇][之第]?[一二三四五六七八九十百千\d]+"""),
        Regex("""^[一二三四五六七八九十]+[、．.]"""),
        Regex("""^\d{1,4}\s*[、．.]\s*\S"""),
        Regex("""^【.{1,24}】\s*$"""),
        Regex("""^[〇○●※◇◆□■]\s*"""),
        Regex("""^「.{1,24}」\s*$""")
    )

    private val NOISE_PATTERNS = listOf(
        Regex("""^\s*[-—=＝~～*＊]{3,}\s*$"""),
        Regex("""^\s*[【\[（(]?\s*第?\s*\d+\s*页\s*[】\]）)]?\s*$"""),
        Regex("""^\s*[-—–]\s*\d+\s*[-—–]\s*$"""),
        Regex("""^\s*\d+\s*/\s*\d+\s*$"""),
        Regex("""^\s*(?:www\.|https?://)\S+\s*$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*(?:扫描|校对|整理|录入|OCR)[:：].{0,60}$"""),
        Regex("""^\s*[·．.。、\-—_=*※☆★○●◎◇◆□■]{4,}\s*$""")
    )

    fun clean(raw: String, options: CleanOptions = CleanOptions()): CleanResult {
        var mergedBreaks = 0
        var removedLines = 0

        // ---------- 1. 换行归一 ----------
        var text = raw
        if (options.normalizeNewlines) {
            text = text.replace("\r\n", "\n").replace('\r', '\n')
        }

        // ---------- 2. 隐形字符清除 ----------
        if (options.stripInvisible) {
            val sb = StringBuilder(text.length)
            for (ch in text) {
                if (ch in INVISIBLE_CHARS) continue
                val code = ch.code
                // 保留 \n(0x0A) 与 \t(0x09)，剔除其余控制字符
                if (code < 0x09 || (code in 0x0B..0x1F && code != 0x0D)) continue
                sb.append(ch)
            }
            text = sb.toString()
        }

        // ---------- 3. 逐行规范化 + 噪音剔除 ----------
        val rawLines = text.split('\n')
        val lines = ArrayList<String>(rawLines.size)
        for (line in rawLines) {
            var l = line.trimEnd()
            if (options.fullWidthToHalfWidth) {
                l = toHalfWidth(l)
            }
            if (options.removeNoiseLines && l.isNotBlank() && isNoise(l)) {
                removedLines++
                continue
            }
            lines += l
        }

        // ---------- 4. 智能段落重排 ----------
        val out = StringBuilder(text.length)
        // 只需要上一行的最后一个字符来做「是否需要断行」判断，避免长段落反复拼字符串
        var prevLastChar: Char? = null
        var prevWasHeading = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
                prevLastChar = null
                prevWasHeading = false
                continue
            }

            val heading = isHeadingLike(trimmed)

            val shouldMerge = options.mergeHardWraps &&
                prevLastChar != null &&
                !prevWasHeading &&
                !heading &&
                !isSentenceEnd(prevLastChar) &&
                !startsWithOpeningMark(trimmed)

            if (shouldMerge) {
                // 直接拼接，不加空格 —— 中文不需要词间空格
                out.append(trimmed)
                mergedBreaks++
            } else {
                // 段落之间插空行
                if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
                if (out.isNotEmpty()) out.append('\n')
                out.append(trimmed)
            }
            prevLastChar = trimmed.lastOrNull()
            prevWasHeading = heading
        }

        var result = out.toString()

        // ---------- 5. 空白压缩 ----------
        if (options.normalizeSpaces) {
            result = result
                .replace(Regex("""[ \t]{3,}"""), "  ")
                .replace(Regex("""\n{3,}"""), "\n\n")
        }

        return CleanResult(
            text = result.trim(),
            mergedBreaks = mergedBreaks,
            removedLines = removedLines
        )
    }

    /** 只要含句读标点，就说明是正文句子而非标题。 */
    private const val TITLE_FORBIDDEN = "。，、；：！？…—「」『』（）《》〈〉【】“”‘’"

    /**
     * 判断一行是否「像标题」：重排时必须在它前后断开，否则会把标题吞进正文。
     *
     * 除了标准标记（第X篇 / 第X章 / 一、/ 一、【】），还要覆盖古籍整理稿里
     * 最常见的**光杆标题**——如「辨太阳病脉证并治上」：短、无标点、独立成行。
     *
     * 这里比 ChapterSplitter 的判定更激进（不要求前后空行），因为重排阶段判错的
     * 代价只是「该行不与下一行合并」，远小于把标题吞进正文造成的结构性破坏。
     */
    fun isHeadingLike(line: String): Boolean {
        if (line.length > 40) return false
        if (HEADING_PATTERNS.any { it.containsMatchIn(line) }) return true
        return looksLikeBareTitle(line)
    }

    private fun looksLikeBareTitle(line: String): Boolean {
        if (line.length !in 2..16) return false
        if (line.first() in "，。、；：！？）】》」』") return false
        return line.none { it in TITLE_FORBIDDEN }
    }

    private fun isNoise(line: String): Boolean =
        NOISE_PATTERNS.any { it.matches(line) }

    private fun isSentenceEnd(ch: Char): Boolean =
        ch in SENTENCE_ENDINGS || ch in CLOSING_MARKS

    private fun startsWithOpeningMark(line: String): Boolean {
        val first = line.firstOrNull() ?: return false
        return first in "「『（《【〔〈"
    }

    private fun toHalfWidth(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(
                when {
                    // 全角数字与英文转半角（全角空格 U+3000 保留，古籍常用作缩进）
                    ch.code in 0xFF10..0xFF19 || ch.code in 0xFF21..0xFF3A || ch.code in 0xFF41..0xFF5A ->
                        (ch.code - 0xFEE0).toChar()
                    else -> ch
                }
            )
        }
        return sb.toString()
    }
}
