package com.tcmfactory.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcmfactory.core.database.dao.StatsDao
import com.tcmfactory.core.database.entity.StudyDailyStatsEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** 单日学习量。 */
data class DayStat(
    val date: LocalDate,
    val newCards: Int = 0,
    val reviewCards: Int = 0,
    val quizCount: Int = 0,
    val correctCount: Int = 0,
    val minutes: Int = 0
) {
    val totalCards: Int get() = newCards + reviewCards
    val hasActivity: Boolean get() = totalCards > 0 || quizCount > 0
}

data class StatsUiState(
    val loading: Boolean = true,
    /** 按日期升序，末尾是今天。长度固定，无记录的日子补零。 */
    val days: List<DayStat> = emptyList(),

    // ---- 汇总 ----
    val streakDays: Int = 0,
    val activeDays: Int = 0,
    val totalCards: Int = 0,
    val totalNew: Int = 0,
    val totalReview: Int = 0,
    val totalQuiz: Int = 0,
    val totalCorrect: Int = 0,
    val totalMinutes: Int = 0
) {
    /** 答题正确率；未答过题为 null。 */
    val quizAccuracy: Float? get() = if (totalQuiz > 0) totalCorrect.toFloat() / totalQuiz else null

    /** 近 30 天，用于柱状图。 */
    val recent: List<DayStat> get() = days.takeLast(30)

    val recentMax: Int get() = recent.maxOfOrNull { it.totalCards }?.coerceAtLeast(1) ?: 1
}

/**
 * 学习统计。
 *
 * 数据源是 `study_daily_stats`——它在第一阶段就已经按学习日边界写入，
 * 所以这里不需要任何新的采集逻辑，纯粹是把已有数据读出来展示。
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsDao: StatsDao
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val today = LocalDate.now()
            val from = today.minusDays(HEATMAP_DAYS - 1L)
            statsDao.observeSince(from.toString()).collect { rows ->
                _state.value = build(rows, today)
            }
        }
    }

    private fun build(rows: List<StudyDailyStatsEntity>, today: LocalDate): StatsUiState {
        val byDate = rows.associateBy { it.date }

        // 补齐空缺的日期：热力图与柱状图都要求连续的时间轴，
        // 直接使用数据库返回的稀疏行会让「中间停学几天」在图上被压缩掉。
        val days = (0 until HEATMAP_DAYS).map { offset ->
            val date = today.minusDays(HEATMAP_DAYS - 1L - offset)
            val row = byDate[date.toString()]
            DayStat(
                date = date,
                newCards = row?.newCards ?: 0,
                reviewCards = row?.reviewCards ?: 0,
                quizCount = row?.quizCount ?: 0,
                correctCount = row?.correctCount ?: 0,
                minutes = row?.studyMinutes ?: 0
            )
        }

        // 连续学习天数：从今天往回数。今天还没学不算断 —— 否则每天零点
        // 一过，用户的连续记录就会先归零再被今天的努力补回来，看起来很挫败。
        var streak = 0
        var index = days.lastIndex
        if (index >= 0 && !days[index].hasActivity) index--
        var cursor = index
        while (cursor >= 0 && days[cursor].hasActivity) {
            streak++
            cursor--
        }

        return StatsUiState(
            loading = false,
            days = days,
            streakDays = streak,
            activeDays = days.count { it.hasActivity },
            totalCards = days.sumOf { it.totalCards },
            totalNew = days.sumOf { it.newCards },
            totalReview = days.sumOf { it.reviewCards },
            totalQuiz = days.sumOf { it.quizCount },
            totalCorrect = days.sumOf { it.correctCount },
            totalMinutes = days.sumOf { it.minutes }
        )
    }

    companion object {
        /** 热力图覆盖天数，约 21 周。 */
        const val HEATMAP_DAYS = 147L
    }
}
