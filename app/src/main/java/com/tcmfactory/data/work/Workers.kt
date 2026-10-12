package com.tcmfactory.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tcmfactory.core.datastore.KnowledgeSettingsRepository
import com.tcmfactory.core.datastore.SettingsRepository
import com.tcmfactory.core.notify.NotificationHelper
import com.tcmfactory.data.repository.CardRepository
import com.tcmfactory.data.repository.KnowledgeRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Worker 取依赖用的入口点。
 *
 * 刻意不用 `@HiltWorker`：那需要额外引入 hilt-work 与 hilt-compiler，
 * 还要覆写 Application 的 WorkManager 初始化配置（去掉 manifest 里的默认
 * Provider），改动面大、出错点集中在启动路径上。用 EntryPoint 取依赖更轻，
 * 且这几处依赖都是 Singleton，拿到的与前台是同一实例。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun cardRepository(): CardRepository
    fun knowledgeRepository(): KnowledgeRepository
    fun knowledgeSettingsRepository(): KnowledgeSettingsRepository
}

/**
 * 每日复习提醒。
 *
 * 只有**确实有到期卡片**时才发通知 —— 没有待复习内容还每天打扰用户，
 * 是最容易招致反感、最终被关掉通知权限的做法。
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entry = runCatching {
            EntryPointAccessors.fromApplication(applicationContext, WorkEntryPoint::class.java)
        }.getOrNull() ?: return Result.success()

        val settings = runCatching { entry.settingsRepository().settingsSnapshot() }
            .getOrNull() ?: return Result.success()

        if (!settings.reminderEnabled || settings.reminderHour < 0) return Result.success()

        val due = runCatching {
            entry.cardRepository().countDueBefore(System.currentTimeMillis())
        }.getOrDefault(0)

        if (due > 0) {
            NotificationHelper.showReminder(applicationContext, due)
        }
        return Result.success()
    }
}

/**
 * 后台静默检查知识库更新。
 *
 * 只在用户开启「自动检查」且配置了数据源时运行；只检查、**不自动下载** ——
 * 下载涉及内容替换，必须由用户看到更新计划后确认。
 */
class KnowledgeCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entry = runCatching {
            EntryPointAccessors.fromApplication(applicationContext, WorkEntryPoint::class.java)
        }.getOrNull() ?: return Result.success()

        val sources = runCatching { entry.knowledgeSettingsRepository().current() }
            .getOrNull() ?: return Result.success()

        if (!sources.autoCheck || sources.urls.isEmpty()) return Result.success()

        return runCatching { entry.knowledgeRepository().checkForUpdates() }.fold(
            onSuccess = { result ->
                val pending = result.getOrNull()?.pending?.size ?: 0
                if (pending > 0) {
                    NotificationHelper.showKnowledgeUpdate(applicationContext, pending)
                }
                Result.success()
            },
            onFailure = {
                // 网络抖动导致的失败交给退避策略重试，不打扰用户
                Result.retry()
            }
        )
    }
}
