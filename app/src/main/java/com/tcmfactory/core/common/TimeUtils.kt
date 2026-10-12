package com.tcmfactory.core.common

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 学习日边界工具。
 *
 * 中医学生普遍熬夜，若以自然日 00:00 为界，凌晨 2 点学的内容会被算作「新的一天」，
 * 导致每日任务量与统计断裂。因此引入可配置的日界（默认凌晨 04:00）。
 */
object DayBoundary {

    const val DEFAULT_BOUNDARY_HOUR = 4

    /** 返回该时刻所属「学习日」的起点时间戳。 */
    fun dayStartOf(timestamp: Long, boundaryHour: Int = DEFAULT_BOUNDARY_HOUR): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, boundaryHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis > timestamp) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return cal.timeInMillis
    }

    /** 返回该时刻所属「学习日」的终点时间戳（下一天界）。 */
    fun dayEndOf(timestamp: Long, boundaryHour: Int = DEFAULT_BOUNDARY_HOUR): Long {
        val start = dayStartOf(timestamp, boundaryHour)
        val cal = Calendar.getInstance()
        cal.timeInMillis = start
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }

    /** 生成 yyyy-MM-dd 形式的统计键。 */
    fun dateKey(timestamp: Long, boundaryHour: Int = DEFAULT_BOUNDARY_HOUR): String =
        formatDate(dayStartOf(timestamp, boundaryHour))

    fun formatDate(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(timestamp))

    /** 把「距今天数」转为中文短描述，用于卡片列表的到期提示。 */
    fun describeDue(dueAt: Long, now: Long, boundaryHour: Int = DEFAULT_BOUNDARY_HOUR): String {
        if (dueAt <= now) return "待复习"
        val todayStart = dayStartOf(now, boundaryHour)
        val dueDayStart = dayStartOf(dueAt, boundaryHour)
        val diffDays = ((dueDayStart - todayStart) / 86_400_000L).toInt()
        return when {
            diffDays <= 0 -> "今天"
            diffDays == 1 -> "明天"
            diffDays == 2 -> "后天"
            diffDays < 30 -> "$diffDays 天后"
            else -> "${diffDays / 30} 个月后"
        }
    }

    /** 问候语，随一天中的时段变化。 */
    fun greeting(now: Long, boundaryHour: Int = DEFAULT_BOUNDARY_HOUR): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        // 相对学习日起点的偏移小时数
        val offsetHours = ((now - dayStartOf(now, boundaryHour)) / 3_600_000L).toInt()
        val h = (boundaryHour + offsetHours) % 24
        return when (h) {
            in 4..10 -> "早上好"
            in 11..13 -> "中午好"
            in 14..17 -> "下午好"
            in 18..22 -> "晚上好"
            else -> "夜深了"
        }
    }
}

object TimeFormat {
    fun duration(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) "${minutes}分${seconds}秒" else "${seconds}秒"
    }
}
