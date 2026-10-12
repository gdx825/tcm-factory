package com.tcmfactory.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tcmfactory.core.database.entity.BookChapterEntity
import com.tcmfactory.core.database.entity.BookEntity
import com.tcmfactory.core.database.entity.BookMarkEntity
import kotlinx.coroutines.flow.Flow

/**
 * 古籍相关 DAO。第一阶段仅建表与基础访问能力，
 * 完整能力（导入管线、搜索、进度恢复）在第二阶段实现。
 */
@Dao
interface BookDao {

    // ---------- books ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("SELECT * FROM books ORDER BY lastReadAt DESC, importedAt DESC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: Long): BookEntity?

    @Query("SELECT * FROM books ORDER BY lastReadAt DESC LIMIT 1")
    suspend fun latestReadBook(): BookEntity?

    @Query("SELECT COUNT(*) FROM books")
    suspend fun countBooks(): Int

    /** 知识包导入古籍前查重，避免同名书重复入库。 */
    @Query("SELECT COUNT(*) FROM books WHERE title = :title")
    suspend fun countByTitle(title: String): Int

    @Query("DELETE FROM books WHERE id = :id AND isBuiltin = 0")
    suspend fun deleteBook(id: Long)

    @Query(
        """
        UPDATE books SET
            lastReadChapterIndex = :chapterIndex,
            lastReadCharOffset = :charOffset,
            lastReadScrollY = :scrollY,
            lastReadAt = :at
        WHERE id = :bookId
        """
    )
    suspend fun updateProgress(
        bookId: Long,
        chapterIndex: Int,
        charOffset: Int,
        scrollY: Int,
        at: Long
    )

    // ---------- book_chapters ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<BookChapterEntity>)

    @Query("SELECT * FROM book_chapters WHERE bookId = :bookId ORDER BY `index` ASC")
    suspend fun getChapters(bookId: Long): List<BookChapterEntity>

    @Query("SELECT * FROM book_chapters WHERE bookId = :bookId ORDER BY `index` ASC")
    fun observeChapters(bookId: Long): Flow<List<BookChapterEntity>>

    @Query("SELECT COUNT(*) FROM book_chapters WHERE bookId = :bookId")
    suspend fun countChapters(bookId: Long): Int

    // ---------- book_marks ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMark(mark: BookMarkEntity): Long

    @Query("SELECT * FROM book_marks WHERE bookId = :bookId ORDER BY charOffset ASC")
    fun observeMarks(bookId: Long): Flow<List<BookMarkEntity>>

    @Query("SELECT * FROM book_marks WHERE bookId = :bookId ORDER BY charOffset ASC")
    suspend fun getMarks(bookId: Long): List<BookMarkEntity>

    @Query("DELETE FROM book_marks WHERE id = :id")
    suspend fun deleteMark(id: Long)

    @Query("DELETE FROM book_marks WHERE bookId = :bookId AND type = :type AND charOffset = :offset")
    suspend fun deleteMarkAt(bookId: Long, type: String, offset: Int)
}
