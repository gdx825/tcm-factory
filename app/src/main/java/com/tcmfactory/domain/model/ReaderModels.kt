package com.tcmfactory.domain.model

import com.tcmfactory.core.database.entity.BookChapterEntity
import com.tcmfactory.core.database.entity.BookEntity
import com.tcmfactory.core.database.entity.BookMarkEntity

/** 古籍。正文不入库，只存文件路径与字符偏移索引。 */
data class Book(
    val id: Long,
    val title: String,
    val author: String?,
    val dynasty: String?,
    val format: String,
    val fileName: String,
    val filePath: String,
    val encoding: String,
    val charCount: Int,
    val chapterCount: Int,
    val lastReadChapterIndex: Int,
    val lastReadCharOffset: Int,
    val lastReadAt: Long?,
    val importedAt: Long,
    val isBuiltin: Boolean
) {
    /** 朝代与作者合并显示，如「汉 · 张仲景」。 */
    val subtitle: String
        get() = listOfNotNull(dynasty?.takeIf { it.isNotBlank() }, author?.takeIf { it.isNotBlank() })
            .joinToString(" · ")

    val charCountText: String
        get() = when {
            charCount >= 10_000 -> "%.1f 万字".format(charCount / 10_000f)
            else -> "$charCount 字"
        }

    /** 已读字符占比，粗略代表阅读进度。 */
    val progress: Float
        get() = if (charCount <= 0) 0f else (lastReadCharOffset.toFloat() / charCount).coerceIn(0f, 1f)

    val progressText: String get() = "${(progress * 100).toInt()}%"

    val hasProgress: Boolean get() = lastReadAt != null && lastReadCharOffset > 0
}

data class BookChapter(
    val id: Long,
    val bookId: Long,
    val index: Int,
    val title: String,
    val level: Int,
    val startCharOffset: Int,
    val endCharOffset: Int,
    val charCount: Int
)

data class BookMark(
    val id: Long,
    val bookId: Long,
    val charOffset: Int,
    val chapterIndex: Int,
    val selectedText: String?,
    val note: String?,
    val type: String,
    val colorTag: String?,
    val createdAt: Long
)

fun BookEntity.toDomain(): Book = Book(
    id = id,
    title = title,
    author = author,
    dynasty = dynasty,
    format = format,
    fileName = fileName,
    filePath = filePath,
    encoding = encoding,
    charCount = charCount,
    chapterCount = chapterCount,
    lastReadChapterIndex = lastReadChapterIndex,
    lastReadCharOffset = lastReadCharOffset,
    lastReadAt = lastReadAt,
    importedAt = importedAt,
    isBuiltin = isBuiltin
)

fun BookChapterEntity.toDomain(): BookChapter = BookChapter(
    id = id,
    bookId = bookId,
    index = index,
    title = title,
    level = level,
    startCharOffset = startCharOffset,
    endCharOffset = endCharOffset,
    charCount = charCount
)

fun BookMarkEntity.toDomain(): BookMark = BookMark(
    id = id,
    bookId = bookId,
    charOffset = charOffset,
    chapterIndex = chapterIndex,
    selectedText = selectedText,
    note = note,
    type = type,
    colorTag = colorTag,
    createdAt = createdAt
)
