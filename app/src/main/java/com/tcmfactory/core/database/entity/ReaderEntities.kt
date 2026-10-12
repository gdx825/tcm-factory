package com.tcmfactory.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 古籍书籍。
 * 设计要点：正文**整体作为字符串存文件系统**，本表只存元信息与阅读进度。
 * 几 MB 的正文入库后逐行查询会拖垮滚动性能。
 */
@Entity(tableName = "books", indices = [Index("lastReadAt")])
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String? = null,
    val dynasty: String? = null,
    /** TXT / EPUB */
    val format: String = FORMAT_TXT,
    /** 原始文件名 */
    val fileName: String,
    /** 正文文件在 filesDir 下的相对路径 */
    val filePath: String,
    val fileSize: Long = 0,
    /** 探测到的编码，如 UTF-8 / GB18030 / BIG5 */
    val encoding: String = "UTF-8",
    val charCount: Int = 0,
    val chapterCount: Int = 0,
    /** 阅读进度：章节序号 + 章内字符偏移 + 章内滚动位置 */
    val lastReadChapterIndex: Int = 0,
    val lastReadCharOffset: Int = 0,
    val lastReadScrollY: Int = 0,
    val lastReadAt: Long? = null,
    val importedAt: Long,
    val isBuiltin: Boolean = false
) {
    companion object {
        const val FORMAT_TXT = "TXT"
        const val FORMAT_EPUB = "EPUB"
    }
}

/**
 * 古籍章节索引。只存字符偏移，不存正文。
 */
@Entity(
    tableName = "book_chapters",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId", "index"], unique = true)]
)
data class BookChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    /** 章内序号，从 0 开始 */
    val index: Int,
    val title: String,
    /** 层级：1=卷/篇，2=章，3=条 */
    val level: Int = 1,
    val startCharOffset: Int,
    val endCharOffset: Int,
    val charCount: Int
)

/**
 * 书签 / 划线 / 批注。
 */
@Entity(
    tableName = "book_marks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId", "charOffset"])]
)
data class BookMarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    /** 书籍绝对字符偏移 */
    val charOffset: Int,
    val chapterIndex: Int,
    val selectedText: String? = null,
    val note: String? = null,
    /** BOOKMARK / HIGHLIGHT / NOTE */
    val type: String = TYPE_BOOKMARK,
    val colorTag: String? = null,
    val createdAt: Long
) {
    companion object {
        const val TYPE_BOOKMARK = "BOOKMARK"
        const val TYPE_HIGHLIGHT = "HIGHLIGHT"
        const val TYPE_NOTE = "NOTE"
    }
}
