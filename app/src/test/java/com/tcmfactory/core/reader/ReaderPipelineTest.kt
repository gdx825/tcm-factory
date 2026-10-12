package com.tcmfactory.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TXT 导入管线的单元测试。
 *
 * 这条管线直接决定「用户导入自己的古籍能不能正常读」，
 * 而古籍 TXT 质量高度不可控，因此每个环节都要有断言兜住。
 */
class ReaderPipelineTest {

    // ==================================================================
    //  1. 编码探测
    // ==================================================================

    @Test
    fun `UTF-8 BOM 文件被正确识别并跳过 BOM`() {
        val text = "太阳之为病，脉浮，头项强痛而恶寒。"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            text.toByteArray(Charsets.UTF_8)

        val detection = TextEncodingDetector.detect(bytes)
        assertEquals("UTF-8", detection.charsetName)
        assertEquals(3, detection.bomLength)
        assertEquals(1f, detection.confidence, 1e-6f)

        val decoded = TextEncodingDetector.decode(bytes, detection.charsetName)
        assertEquals(text, decoded)
    }

    @Test
    fun `无 BOM 的 UTF-8 中文被识别为 UTF-8`() {
        val text = "麻黄汤：麻黄、桂枝、杏仁、甘草。"
        val bytes = text.toByteArray(Charsets.UTF_8)

        val detection = TextEncodingDetector.detect(bytes)
        assertEquals("UTF-8", detection.charsetName)
        assertTrue("UTF-8 置信度应较高", detection.confidence > 0.9f)
    }

    @Test
    fun `GB18030 无 BOM 文件不会被误判为 UTF-8`() {
        val text = "阳明之为病，胃家实是也。少阴之为病，脉微细，但欲寐也。"
        val gb18030 = runCatching { java.nio.charset.Charset.forName("GB18030") }.getOrNull()
            ?: return   // 运行环境不支持则跳过
        val bytes = text.toByteArray(gb18030)

        // 前提：这份字节序列不应通过 UTF-8 严格校验，否则测试无意义
        assertFalse(TextEncodingDetector.isValidUtf8(bytes))

        val detection = TextEncodingDetector.detect(bytes)
        val decoded = TextEncodingDetector.decode(bytes, detection.charsetName)
        assertEquals(text, decoded)
    }

    @Test
    fun `UTF-8 严格校验能拒绝过长编码与非法续字节`() {
        assertTrue(TextEncodingDetector.isValidUtf8("正常的中文文本".toByteArray(Charsets.UTF_8)))
        assertTrue(TextEncodingDetector.isValidUtf8("".toByteArray()))

        // 孤立续字节
        assertFalse(TextEncodingDetector.isValidUtf8(byteArrayOf(0x80.toByte())))
        // 过长编码：0xC0 0x80 是 U+0000 的非法两字节表示
        assertFalse(TextEncodingDetector.isValidUtf8(byteArrayOf(0xC0.toByte(), 0x80.toByte())))
        // 不完整的 3 字节序列
        assertFalse(TextEncodingDetector.isValidUtf8(byteArrayOf(0xE4.toByte(), 0xB8.toByte())))
    }

    @Test
    fun `解码质量评分能区分正确与错误的解码结果`() {
        val correct = "太阳病，头痛发热，汗出恶风，桂枝汤主之。"
        val garbage = "\uFFFD\uFFFD\uFFFD\uFFFD"
        assertTrue(TextEncodingDetector.scoreDecoded(correct) > 0.9f)
        assertTrue(TextEncodingDetector.scoreDecoded(garbage) < 0.3f)
    }

    // ==================================================================
    //  2. 文本清洗
    // ==================================================================

    @Test
    fun `换行符被归一化且隐形字符被清除`() {
        val raw = "第一段\uFEFF\u200B内容\r\n第二段\r第三段"
        val result = TextCleaner.clean(raw)

        assertFalse(result.text.contains('\uFEFF'))
        assertFalse(result.text.contains('\u200B'))
        assertFalse(result.text.contains('\r'))
        assertTrue(result.text.contains("第一段内容"))
    }

    @Test
    fun `页码与水印噪音行被剔除`() {
        val raw = """
            辨太阳病脉证并治上
            - 12 -
            太阳之为病，脉浮，头项强痛而恶寒。
            【第13页】
            www.example.com
            太阳病，发热，汗出，恶风，脉缓者，名为中风。
        """.trimIndent()

        val result = TextCleaner.clean(raw)
        assertEquals(3, result.removedLines)
        assertFalse(result.text.contains("- 12 -"))
        assertFalse(result.text.contains("【第13页】"))
        assertFalse(result.text.contains("www.example.com"))
        assertTrue(result.text.contains("太阳之为病"))
    }

    @Test
    fun `硬换行被合并回自然段`() {
        val raw = """
            太阳病，发热，汗出，恶风，脉缓者，名为
            中风。
            太阳病，或已发热，或未发热，必恶寒，
            体痛，呕逆，脉阴阳俱紧者，名为伤寒。
        """.trimIndent()

        val result = TextCleaner.clean(raw)

        assertEquals(2, result.mergedBreaks)
        assertTrue(
            "两句应各自合并为完整一段: ${result.text}",
            result.text.contains("脉缓者，名为中风。")
        )
        assertTrue(result.text.contains("必恶寒，体痛，呕逆，脉阴阳俱紧者，名为伤寒。"))
    }

    @Test
    fun `句读终结处不会被合并`() {
        val raw = """
            太阳之为病，脉浮，头项强痛而恶寒。
            太阳病，发热，汗出，恶风，脉缓者，名为中风。
        """.trimIndent()

        val result = TextCleaner.clean(raw)
        assertEquals(0, result.mergedBreaks)
        assertTrue(result.text.contains("\n"))
    }

    @Test
    fun `标题行不会被并入上段正文`() {
        val raw = """
            太阳之为病，脉浮，头项强痛而恶寒
            辨阳明病脉证并治
            阳明之为病，胃家实是也。
        """.trimIndent()

        val result = TextCleaner.clean(raw)
        // 第二行是标准篇标题，必须独立成行
        val lines = result.text.split('\n').filter { it.isNotBlank() }
        assertTrue(
            "篇标题应独立成行: $lines",
            lines.any { it.trim() == "辨阳明病脉证并治" }
        )
    }

    @Test
    fun `关闭重排选项时不合并任何换行`() {
        val raw = "太阳病，发热，汗出\n恶风，脉缓者"
        val result = TextCleaner.clean(raw, CleanOptions(mergeHardWraps = false))
        assertEquals(0, result.mergedBreaks)
        assertTrue(result.text.contains("\n"))
    }

    // ==================================================================
    //  3. 章节切分
    // ==================================================================

    @Test
    fun `卷篇章标记能被识别为主结构`() {
        val raw = """
            第一篇 总论

            阴阳者，天地之道也。

            第二篇 藏象

            心者，君主之官也。
        """.trimIndent()

        val result = ChapterSplitter.split(raw)
        assertEquals(2, result.chapterCount)
        assertEquals(2, result.structureLevel)
        assertEquals("篇", result.structureName)
        assertTrue(result.chapters[0].title.contains("第一篇"))
        assertTrue(result.chapters[1].title.contains("第二篇"))
    }

    @Test
    fun `光杆标题型古籍能被正确切分`() {
        val raw = """
            辨太阳病脉证并治上

            1．太阳之为病，脉浮，头项强痛而恶寒。

            2．太阳病，发热，汗出，恶风，脉缓者，名为中风。

            辨阳明病脉证并治

            180．阳明之为病，胃家实是也。
        """.trimIndent()

        val result = ChapterSplitter.split(raw)
        assertEquals(2, result.chapterCount)
        assertEquals(5, result.structureLevel)
        assertEquals("辨太阳病脉证并治上", result.chapters[0].title)
        assertEquals("辨阳明病脉证并治", result.chapters[1].title)
    }

    @Test
    fun `完全无标记的文本走物理切分并如实告知`() {
        val body = "太阳之为病，脉浮，头项强痛而恶寒。".repeat(400)   // 约 7200 字
        val result = ChapterSplitter.split(body, fallbackChunkSize = 3000)

        assertFalse("不应声称识别到了结构", result.hasStructure)
        assertEquals("按字数切分", result.structureName)
        assertTrue("应切成 2 段以上", result.chapterCount >= 2)
    }

    @Test
    fun `章节偏移量连续且完整覆盖全文`() {
        val raw = """
            第一篇 上

            甲乙丙丁戊己庚辛。

            第二篇 中

            子丑寅卯辰巳午未。

            第三篇 下

            金木水火土。
        """.trimIndent()

        val result = ChapterSplitter.split(raw)
        assertEquals(3, result.chapterCount)

        // 首个章节从 0 开始，最后一个覆盖到文末，中间首尾相接
        assertEquals(0, result.chapters.first().startCharOffset)
        result.chapters.forEach { ch ->
            assertTrue("章节偏移必须合法", ch.startCharOffset < ch.endCharOffset)
            assertTrue("字数必须为正", ch.charCount > 0)
        }
        for (i in 0 until result.chapterCount - 1) {
            assertEquals(
                "章节之间不能有缝隙",
                result.chapters[i].endCharOffset,
                result.chapters[i + 1].startCharOffset
            )
        }
    }

    @Test
    fun `标题之前的内容被保留为前言`() {
        val raw = """
            这是整理者写的一段说明文字。

            第一篇 正文开始

            太阳之为病，脉浮。
        """.trimIndent()

        val result = ChapterSplitter.split(raw)
        assertEquals(2, result.chapterCount)
        assertEquals("前言", result.chapters[0].title)
        assertEquals(0, result.chapters[0].startCharOffset)
    }

    // ==================================================================
    //  4. 管线端到端
    // ==================================================================

    @Test
    fun `从字节到章节的完整管线可用`() {
        val original = """
            伤寒论节选

            辨太阳病脉证并治上

            1．太阳之为病，脉浮，头项强痛而恶寒。

            2．太阳病，发热，汗出，恶风，脉缓者，名为中风。

            辨阳明病脉证并治

            180．阳明之为病，胃家实是也。
        """.trimIndent()

        val bytes = original.toByteArray(Charsets.UTF_8)
        val detection = TextEncodingDetector.detect(bytes)
        val decoded = TextEncodingDetector.decode(bytes, detection.charsetName)
        val cleaned = TextCleaner.clean(decoded)
        val split = ChapterSplitter.split(cleaned.text)

        assertEquals("UTF-8", detection.charsetName)
        assertTrue(split.chapterCount >= 2)

        // 每个章节都能从正文中按偏移量原样取出，这是渲染与制卡定位的基础
        split.chapters.forEach { ch ->
            val slice = cleaned.text.substring(ch.startCharOffset, ch.endCharOffset)
            assertTrue("章节内容不应为空", slice.isNotBlank())
        }
    }
}
