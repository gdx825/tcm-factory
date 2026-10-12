package com.tcmfactory.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tcmfactory.core.database.entity.ReviewLogEntity
import com.tcmfactory.core.database.entity.StudyDailyStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewLogDao {

    @Insert
    suspend fun insert(log: ReviewLogEntity): Long

    /** 取最近一条日志，用于「撤销上一步」。 */
    @Query("SELECT * FROM review_logs ORDER BY id DESC LIMIT 1")
    suspend fun latest(): ReviewLogEntity?

    @Query("SELECT * FROM review_logs WHERE cardId = :cardId ORDER BY reviewedAt DESC LIMIT 1")
    suspend fun latestForCard(cardId: Long): ReviewLogEntity?

    @Query("DELETE FROM review_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 今日新学卡片数：prevLevel = 0 表示这张卡本次是首次学习。 */
    @Query(
        """
        SELECT COUNT(*) FROM review_logs
        WHERE prevLevel = 0 AND mode = 'DAILY'
          AND reviewedAt >= :dayStart AND reviewedAt < :dayEnd
        """
    )
    suspend fun countNewLearned(dayStart: Long, dayEnd: Long): Int

    /** 今日复习（非新学）卡片数。 */
    @Query(
        """
        SELECT COUNT(*) FROM review_logs
        WHERE prevLevel > 0 AND mode = 'DAILY'
          AND reviewedAt >= :dayStart AND reviewedAt < :dayEnd
        """
    )
    suspend fun countReviewed(dayStart: Long, dayEnd: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM review_logs
        WHERE rating = 'AGAIN' AND reviewedAt >= :dayStart AND reviewedAt < :dayEnd
        """
    )
    suspend fun countForgotten(dayStart: Long, dayEnd: Long): Int

    @Query("SELECT * FROM review_logs WHERE reviewedAt >= :from ORDER BY reviewedAt DESC")
    fun observeSince(from: Long): Flow<List<ReviewLogEntity>>
}

@Dao
abstract class StatsDao {

    @Query("SELECT * FROM study_daily_stats WHERE date = :date")
    abstract suspend fun get(date: String): StudyDailyStatsEntity?

    @Query("SELECT * FROM study_daily_stats WHERE date >= :from ORDER BY date ASC")
    abstract fun observeSince(from: String): Flow<List<StudyDailyStatsEntity>>

    @Query("SELECT * FROM study_daily_stats ORDER BY date DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int): Flow<List<StudyDailyStatsEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(stat: StudyDailyStatsEntity)

    /**
     * 累加当日统计。
     * 注意：不使用 SQLite UPSERT（ON CONFLICT DO UPDATE 需要 SQLite 3.24+，
     * 而 Android 8.0 / API 26 自带的是 3.18），改为「先 IGNORE 插入，再累加更新」。
     */
    @Query(
        """
        UPDATE study_daily_stats SET
            newCards = newCards + :newCards,
            reviewCards = reviewCards + :reviewCards,
            quizCount = quizCount + :quizCount,
            correctCount = correctCount + :correctCount,
            studyMinutes = studyMinutes + :minutes,
            remembered = remembered + :remembered,
            forgotten = forgotten + :forgotten
        WHERE date = :date
        """
    )
    abstract suspend fun accumulate(
        date: String,
        newCards: Int,
        reviewCards: Int,
        quizCount: Int,
        correctCount: Int,
        minutes: Int,
        remembered: Int,
        forgotten: Int
    )

    @Transaction
    open suspend fun bump(
        date: String,
        newCards: Int = 0,
        reviewCards: Int = 0,
        quizCount: Int = 0,
        correctCount: Int = 0,
        minutes: Int = 0,
        remembered: Int = 0,
        forgotten: Int = 0
    ) {
        insertIfAbsent(StudyDailyStatsEntity(date = date))
        accumulate(date, newCards, reviewCards, quizCount, correctCount, minutes, remembered, forgotten)
    }
}
