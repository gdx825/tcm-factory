package com.tcmfactory.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 定时任务调度。
 *
 * 全部使用 `enqueueUniquePeriodicWork` + `UPDATE` 策略：
 * 用户反复修改提醒时间时，只会替换同一份任务，不会堆出多个并行任务
 * 导致「一天收到三条提醒」。
 */
object WorkScheduler {

    private const val UNIQUE_REMINDER = "tcm_daily_reminder"
    private const val UNIQUE_KNOWLEDGE_CHECK = "tcm_knowledge_check"

    /** 后台静默检查更新的时刻：固定在上午，与晚间复习提醒错开。 */
    private const val KNOWLEDGE_CHECK_HOUR = 10

    fun scheduleReminder(context: Context, hour: Int) {
        if (hour !in 0..23) {
            cancelReminder(context)
            return
        }
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(millisUntilNext(hour), TimeUnit.MILLISECONDS)
            // 提醒本身不依赖网络，也不该等充电：只判断本地到期数量
            .setConstraints(Constraints.NONE)
            .build()

        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_REMINDER,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    fun cancelReminder(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_REMINDER) }
    }

    fun scheduleKnowledgeCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<KnowledgeCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(millisUntilNext(KNOWLEDGE_CHECK_HOUR), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_KNOWLEDGE_CHECK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    fun cancelKnowledgeCheck(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_KNOWLEDGE_CHECK) }
    }

    /**
     * 距离下一个 hour:00 的毫秒数。
     *
     * 若今天的该时刻已过，则顺延到明天 —— 否则 WorkManager 会在
     * 首次执行时立刻触发一次，出现「刚设好提醒就弹通知」的怪异体验。
     */
    private fun millisUntilNext(hour: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }
}
