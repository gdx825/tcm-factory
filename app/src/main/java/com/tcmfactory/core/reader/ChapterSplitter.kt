package com.tcmfactory.core.reader

/** 切分出的章节。字符偏移是**书级绝对偏移**，与后续是否重新切分无关。 */
data class ParsedChapter(
    val index: Int,
    val title: String,
    /** 1=卷 2=篇 3=章 4=条 5=短标题 0=物理切分 */
    val level: Int,
    val startCharOffset: Int,
    val endCharOffset: Int
) {
    val charCount: Int get() = endCharOffset - startCharOffset
}

data class SplitResult(
    val chapters: List<ParsedChapter>,
    /** 0 表示未识别到结构，走了物理切分 */
    val structureLevel: Int,
    val structureName: String
) {
    val hasStructure: Boolean get() = structureLevel > 0
    val chapterCount: Int get() = chapters.size
}

/**
 * 多级章节切分器。
 *
 * 古籍 TXT 的章节标记情况千差万别：
 *  · 规范型：「卷一」「第一篇」「第三章」→ 正则可直接命中
 *  · 条文型：《伤寒论》「1．太阳之为病…」→ 条级切分，是制卡联动的基础
 *  · 光杆型：标题独立成行、前后空行、无任何标记（如「辨太阳病脉证并治上」）
 *  · 无标记型：整本书就是一大坨 → 只能按字数物理切分，并明确告知用户
 *
 * 设计取舍：**只选一级主结构**做扁平章节列表。
 * 多级树形目录虽然更"完整"，但会让阅读器的章节导航逻辑复杂一倍，
 * 而对古籍阅读而言，「快速跳到某一篇」的收益远大于层级完整性。
 */
object ChapterSplitter {

    private val VOLUME = Regex("""^第[一二三四五六七八九十百千零〇\d]+卷""")
    private val SECTION = Regex("""^第[一二三四五六七八九十百千零〇\d]+[篇编]""")
    private val CHAPTER = Regex("""^第[一二三四五六七八九十百千零〇\d]+[章节回]""")
    private val ORDERED = Regex("""^(?:\d{1,4}|[一二三四五六七八九十百千]+)\s*[、．.]\s*\S""")

    /** 优先级从粗到细；4（条）放在最后，因为它最细碎。 */
    private val STRUCTURE_PRIORITY = listOf(1, 2, 3, 5, 4)

    /**
     * 最少标题数。取 1 而非 2：一本书只有一个标题时，
     * 「前言 + 正文」的切分依然是有意义的，不该退化成物理切分。
     */
    private const val MIN_HEADINGS = 1
    private const val MAX_HEADINGS = 300

    private data class HeadingLine(val lineIndex: Int, val level: Int, val title: String)

    fun split(text: String, fallbackChunkSize: Int = 3000): SplitResult {
        if (text.isBlank()) {
            return SplitResult(emptyList(), 0, "空文档")
        }

        val lines = text.split('\n')
        val lineStarts = IntArray(lines.size)
        var cursor = 0
        for (i in lines.indices) {
            lineStarts[i] = cursor
            cursor += lines[i].length + 1   // +1 为换行符
        }

        val headings = ArrayList<HeadingLine>()
        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed.isEmpty()) continue
            val level = headingLevel(lines, i, trimmed) ?: continue
            headings += HeadingLine(i, level, trimmed)
        }

        val chosenLevel = pickStructureLevel(headings)
            ?: return physicalSplit(text, fallbackChunkSize)

        val mainHeadings = headings.filter { it.level == chosenLevel }

        val chapters = ArrayList<ParsedChapter>(mainHeadings.size + 1)
        var index = 0

        // 首个标题之前的内容作为「前言」，避免正文被吞掉
        val firstStart = lineStarts[mainHeadings.first().lineIndex]
        if (firstStart > 0 && text.substring(0, firstStart).isNotBlank()) {
            chapters += ParsedChapter(
                index = index++,
                title = "前言",
                level = chosenLevel,
                startCharOffset = 0,
                endCharOffset = firstStart
            )
        }

        mainHeadings.forEachIndexed { i, heading ->
            val start = lineStarts[heading.lineIndex]
            val end = if (i + 1 < mainHeadings.size) {
                lineStarts[mainHeadings[i + 1].lineIndex]
            } else {
                text.length
            }
            chapters += ParsedChapter(
                index = index++,
                title = heading.title.take(60),
                level = chosenLevel,
                startCharOffset = start,
                endCharOffset = end
            )
        }

        return SplitResult(chapters, chosenLevel, structureName(chosenLevel))
    }

    // ------------------------------------------------------------------

    private fun headingLevel(lines: List<String>, i: Int, trimmed: String): Int? {
        if (trimmed.length > 40) return null
        return when {
            VOLUME.containsMatchIn(trimmed) -> 1
            SECTION.containsMatchIn(trimmed) -> 2
            CHAPTER.containsMatchIn(trimmed) -> 3
            ORDERED.containsMatchIn(trimmed) -> 4
            isShortTitle(lines, i, trimmed) -> 5
            else -> null
        }
    }

    /**
     * 「光杆标题」识别：短、无标点、且前后都是空行。
     * 这是古籍整理稿里最常见的标题形式（如「辨太阳病脉证并治上」）。
     */
    private fun isShortTitle(lines: List<String>, i: Int, trimmed: String): Boolean {
        if (trimmed.length !in 2..16) return false
        if (trimmed.any { it in "。，、；：！？（）《》「」" }) return false
        val prevBlank = i == 0 || lines[i - 1].isBlank()
        val nextBlank = i == lines.size - 1 || lines[i + 1].isBlank()
        return prevBlank && nextBlank
    }

    private fun pickStructureLevel(headings: List<HeadingLine>): Int? {
        if (headings.size < MIN_HEADINGS) return null

        for (level in STRUCTURE_PRIORITY) {
            val count = headings.count { it.level == level }
            if (count in MIN_HEADINGS..MAX_HEADINGS) return level
        }

        // 兜底：取命中最多且不少于 2 的层级
        return headings.groupBy { it.level }
            .maxByOrNull { it.value.size }
            ?.takeIf { it.value.size >= MIN_HEADINGS }
            ?.key
    }

    private fun physicalSplit(text: String, chunkSize: Int): SplitResult {
        val chapters = ArrayList<ParsedChapter>()
        var start = 0
        var i = 0
        while (start < text.length) {
            var end = (start + chunkSize).coerceAtMost(text.length)
            if (end < text.length) {
                // 尽量切在换行处，避免把一个段落劈成两半
                val newline = text.lastIndexOf('\n', end)
                if (newline > start + chunkSize / 2) end = newline + 1
            }
            i++
            chapters += ParsedChapter(
                index = i - 1,
                title = "第 $i 节",
                level = 0,
                startCharOffset = start,
                endCharOffset = end
            )
            start = end
        }
        return SplitResult(chapters, 0, "按字数切分")
    }

    private fun structureName(level: Int): String = when (level) {
        1 -> "卷"
        2 -> "篇"
        3 -> "章"
        4 -> "条"
        5 -> "小节"
        else -> "无结构"
    }
}
