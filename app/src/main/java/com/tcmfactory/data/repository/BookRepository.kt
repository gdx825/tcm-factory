package com.tcmfactory.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.tcmfactory.core.database.TcmDatabase
import com.tcmfactory.core.database.dao.BookDao
import com.tcmfactory.core.database.entity.BookChapterEntity
import com.tcmfactory.core.database.entity.BookEntity
import com.tcmfactory.core.database.entity.BookMarkEntity
import com.tcmfactory.core.reader.ChapterSplitter
import com.tcmfactory.core.reader.CleanOptions
import com.tcmfactory.core.reader.ParsedChapter
import com.tcmfactory.core.reader.TextCleaner
import com.tcmfactory.core.reader.TextEncodingDetector
import com.tcmfactory.domain.model.Book
import com.tcmfactory.domain.model.BookChapter
import com.tcmfactory.domain.model.BookMark
import com.tcmfactory.domain.model.toDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导入分析结果。**不落库**，只用于导入预览页展示。
 * 用户确认前，所有解析都在内存中完成。
 */
data class ImportPreview(
    val fileName: String,
    val suggestedTitle: String,
    val encoding: String,
    val encodingConfidence: Float,
    val candidateEncodings: List<EncodingCandidate>,
    val charCount: Int,
    val chapterCount: Int,
    val structureName: String,
    val hasStructure: Boolean,
    val mergedBreaks: Int,
    val removedLines: Int,
    val chapters: List<ChapterBrief>,
    val headText: String
) {
    data class EncodingCandidate(val name: String, val score: Float)
    data class ChapterBrief(val title: String, val charCount: Int)
}

@Singleton
class BookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val db: TcmDatabase
) {

    private val booksDir: File
        get() = File(context.filesDir, "books").apply { if (!exists()) mkdirs() }

    // ---------------- 观察 ----------------

    fun observeBooks(): Flow<List<Book>> =
        bookDao.observeBooks().map { list -> list.map { it.toDomain() } }

    fun observeChapters(bookId: Long): Flow<List<BookChapter>> =
        bookDao.observeChapters(bookId).map { list -> list.map { it.toDomain() } }

    fun observeMarks(bookId: Long): Flow<List<BookMark>> =
        bookDao.observeMarks(bookId).map { list -> list.map { it.toDomain() } }

    suspend fun getBook(bookId: Long): Book? = bookDao.getBook(bookId)?.toDomain()

    suspend fun getChapters(bookId: Long): List<BookChapter> =
        bookDao.getChapters(bookId).map { it.toDomain() }

    suspend fun bookCount(): Int = bookDao.countBooks()

    /** 是否已存在同名古籍。知识包导入前查重，避免重复入库。 */
    suspend fun existsByTitle(title: String): Boolean = bookDao.countByTitle(title) > 0

    // ---------------- 导入：分析（不落库） ----------------

    /**
     * 解析字节流并返回预览信息。整个过程在 Default 调度器上执行，
     * 百万字级文本的处理耗时约数百毫秒，不会阻塞主线程。
     *
     * @param forcedEncoding 用户在预览页手动指定编码时传入，覆盖自动探测结果
     */
    suspend fun analyze(
        bytes: ByteArray,
        fileName: String,
        options: CleanOptions = CleanOptions(),
        forcedEncoding: String? = null
    ): ImportPreview = withContext(Dispatchers.Default) {
        val auto = TextEncodingDetector.detect(bytes)
        val encoding = forcedEncoding ?: auto.charsetName

        val decoded = TextEncodingDetector.decode(bytes, encoding)
        val cleaned = TextCleaner.clean(decoded, options)
        val split = ChapterSplitter.split(cleaned.text)

        ImportPreview(
            fileName = fileName,
            suggestedTitle = guessTitle(cleaned.text, fileName),
            encoding = encoding,
            encodingConfidence = if (forcedEncoding != null) 1f else auto.confidence,
            candidateEncodings = auto.candidates.map {
                ImportPreview.EncodingCandidate(it.charsetName, it.score)
            },
            charCount = cleaned.text.length,
            chapterCount = split.chapterCount,
            structureName = split.structureName,
            hasStructure = split.hasStructure,
            mergedBreaks = cleaned.mergedBreaks,
            removedLines = cleaned.removedLines,
            chapters = split.chapters
                .take(80)
                .map { ImportPreview.ChapterBrief(it.title, it.charCount) },
            headText = cleaned.text.take(400)
        )
    }

    // ---------------- 导入：落库 ----------------

    /**
     * 确认导入。重新跑一遍管线（保证与预览结果完全一致），
     * 正文写入 filesDir/books/{uuid}.txt，数据库只存元信息与字符偏移。
     */
    suspend fun commitImport(
        bytes: ByteArray,
        fileName: String,
        encoding: String,
        options: CleanOptions,
        title: String,
        author: String? = null,
        dynasty: String? = null,
        isBuiltin: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        val decoded = TextEncodingDetector.decode(bytes, encoding)
        val cleaned = TextCleaner.clean(decoded, options)
        val split = ChapterSplitter.split(cleaned.text)

        val storedName = "${UUID.randomUUID()}.txt"
        val file = File(booksDir, storedName)
        file.writeText(cleaned.text, Charsets.UTF_8)

        val now = System.currentTimeMillis()
        db.withTransaction {
            val bookId = bookDao.insertBook(
                BookEntity(
                    title = title.ifBlank { fileName.substringBeforeLast('.') },
                    author = author?.takeIf { it.isNotBlank() },
                    dynasty = dynasty?.takeIf { it.isNotBlank() },
                    format = BookEntity.FORMAT_TXT,
                    fileName = fileName,
                    filePath = storedName,
                    fileSize = file.length(),
                    encoding = encoding,
                    charCount = cleaned.text.length,
                    chapterCount = split.chapterCount,
                    lastReadChapterIndex = 0,
                    lastReadCharOffset = 0,
                    lastReadScrollY = 0,
                    lastReadAt = null,
                    importedAt = now,
                    isBuiltin = isBuiltin
                )
            )

            bookDao.insertChapters(
                split.chapters.map { ch ->
                    BookChapterEntity(
                        bookId = bookId,
                        index = ch.index,
                        title = ch.title,
                        level = ch.level,
                        startCharOffset = ch.startCharOffset,
                        endCharOffset = ch.endCharOffset,
                        charCount = ch.charCount
                    )
                }
            )
            bookId
        }
    }

    // ---------------- 正文读取 ----------------

    /** 读取全书正文。正文不入库，从文件系统按需读取。 */
    suspend fun loadFullText(bookId: Long): String = withContext(Dispatchers.IO) {
        val book = bookDao.getBook(bookId) ?: return@withContext ""
        val file = File(booksDir, book.filePath)
        if (!file.exists()) return@withContext ""
        runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
    }

    /** 按字符偏移读取片段，用于章节懒加载与制卡定位。 */
    suspend fun loadSlice(bookId: Long, start: Int, end: Int): String {
        val full = loadFullText(bookId)
        if (full.isEmpty()) return ""
        val s = start.coerceIn(0, full.length)
        val e = end.coerceIn(s, full.length)
        return full.substring(s, e)
    }

    // ---------------- 进度 ----------------

    suspend fun saveProgress(
        bookId: Long,
        chapterIndex: Int,
        charOffset: Int,
        scrollY: Int = 0,
        now: Long = System.currentTimeMillis()
    ) = bookDao.updateProgress(bookId, chapterIndex, charOffset, scrollY, now)

    suspend fun renameBook(bookId: Long, title: String, author: String?, dynasty: String?) {
        val book = bookDao.getBook(bookId) ?: return
        bookDao.updateBook(
            book.copy(
                title = title,
                author = author?.takeIf { it.isNotBlank() },
                dynasty = dynasty?.takeIf { it.isNotBlank() }
            )
        )
    }

    suspend fun deleteBook(bookId: Long) = withContext(Dispatchers.IO) {
        val book = bookDao.getBook(bookId) ?: return@withContext
        if (book.isBuiltin) return@withContext
        runCatching { File(booksDir, book.filePath).delete() }
        bookDao.deleteBook(bookId)
    }

    // ---------------- 书签 ----------------

    suspend fun addMark(
        bookId: Long,
        charOffset: Int,
        chapterIndex: Int,
        selectedText: String?,
        type: String = BookMarkEntity.TYPE_BOOKMARK,
        now: Long = System.currentTimeMillis()
    ): Long = bookDao.insertMark(
        BookMarkEntity(
            bookId = bookId,
            charOffset = charOffset,
            chapterIndex = chapterIndex,
            selectedText = selectedText,
            type = type,
            createdAt = now
        )
    )

    suspend fun removeMark(markId: Long) = bookDao.deleteMark(markId)

    // ---------------- 工具 ----------------

    /**
     * 猜测书名：优先取首个非空行（大量古籍 TXT 的首行就是书名），
     * 否则回退到文件名。
     */
    private fun guessTitle(text: String, fileName: String): String {
        val firstLine = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && it.length <= 30 }
        val fromFile = fileName.substringBeforeLast('.').trim()
        return when {
            firstLine != null && firstLine.length in 2..20 && fromFile.length in 2..30 -> {
                // 两者差异较大时，通常是文件名更规范（如「伤寒论.txt」）
                if (fromFile.contains(firstLine) || firstLine.contains(fromFile)) fromFile
                else if (fromFile.length <= firstLine.length) fromFile
                else firstLine
            }
            fromFile.isNotBlank() -> fromFile
            firstLine != null -> firstLine
            else -> "未命名古籍"
        }
    }

    /** 供内置古籍写入使用（跳过用户可见的导入流程）。 */
    suspend fun importBuiltin(
        title: String,
        author: String?,
        dynasty: String?,
        content: String
    ): Long = commitImport(
        bytes = content.toByteArray(Charsets.UTF_8),
        fileName = "$title.txt",
        encoding = "UTF-8",
        options = CleanOptions(),
        title = title,
        author = author,
        dynasty = dynasty,
        isBuiltin = true
    )

    /** 章节标题预览（用于目录抽屉的当前章节高亮）。 */
    internal fun chapterTitleAt(chapters: List<ParsedChapter>, offset: Int): String =
        chapters.firstOrNull { offset in it.startCharOffset until it.endCharOffset }?.title.orEmpty()
}
