package com.tcmfactory

import android.app.Application
import com.tcmfactory.core.datastore.KnowledgeSettingsRepository
import com.tcmfactory.core.datastore.SettingsRepository
import com.tcmfactory.core.notify.NotificationHelper
import com.tcmfactory.data.repository.KnowledgeRepository
import com.tcmfactory.data.seed.SeedInitializer
import com.tcmfactory.data.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TcmApp : Application() {

    @Inject
    lateinit var seedInitializer: SeedInitializer

    @Inject
    lateinit var knowledgeRepository: KnowledgeRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var knowledgeSettings: KnowledgeSettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // 通知渠道必须在任何通知发送前创建。重复调用无副作用（内部判空）。
        NotificationHelper.ensureChannels(this)

        appScope.launch {
            // 1) 首次启动写入内置知识库；已初始化则立即返回，开销可忽略。
            try {
                seedInitializer.seedIfNeeded()
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                android.util.Log.e("TcmApp", "内置知识库初始化失败", t)
            }

            // 2) 为历史数据补挂知识包溯源列。
            //    v1.2.0 之前的数据库没有 packId / externalId，而知识库更新依赖它们
            //    定位内容。幂等操作：只处理 packId IS NULL 的行，且只写两个溯源列，
            //    不会触碰用户的背诵进度。必须在 seed 之后执行。
            try {
                val attached = knowledgeRepository.backfillBuiltinPacks()
                if (attached > 0) {
                    android.util.Log.i("TcmApp", "已为 $attached 条内置内容补挂知识包标识")
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                android.util.Log.e("TcmApp", "知识包标识补挂失败", t)
            }

            // 3) 应用定时任务设置
            applyScheduledWork()
        }
    }

    /**
     * 按当前设置安排定时任务。
     *
     * 每次启动都重新应用一遍，而不是只在用户改设置时才调度。原因：
     *  · 应用被「强行停止」后，部分机型的 WorkManager 任务会被清空；
     *  · 用户可能在系统设置里撤销了通知权限，需要重装时自然失效；
     *  · 升级安装后旧任务可能带有过期的参数。
     * 用 UPDATE 策略重复入队是幂等的，代价可忽略。
     */
    private suspend fun applyScheduledWork() {
        runCatching {
            val settings = settingsRepository.settingsSnapshot()
            if (settings.reminderEnabled && settings.reminderHour in 0..23) {
                WorkScheduler.scheduleReminder(this, settings.reminderHour)
            } else {
                WorkScheduler.cancelReminder(this)
            }
        }.onFailure { android.util.Log.w("TcmApp", "复习提醒调度失败", it) }

        runCatching {
            if (knowledgeSettings.current().autoCheck) {
                WorkScheduler.scheduleKnowledgeCheck(this)
            } else {
                WorkScheduler.cancelKnowledgeCheck(this)
            }
        }.onFailure { android.util.Log.w("TcmApp", "知识库检查调度失败", it) }
    }
}
