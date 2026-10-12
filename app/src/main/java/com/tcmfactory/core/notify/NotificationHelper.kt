package com.tcmfactory.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tcmfactory.MainActivity
import com.tcmfactory.R

/**
 * 通知封装。
 *
 * 两条原则：
 *  1. **静默失败**：通知只是增强，任何一步失败都不应影响主流程 ——
 *     权限被拒、渠道被用户关闭、后台受限，都不能抛异常。
 *  2. **渠道只建一次**：Android 规定渠道创建后其重要性不可再修改，
 *     所以只在缺失时创建，重复调用无副作用。
 */
object NotificationHelper {

    const val CHANNEL_REMINDER = "tcm_daily_reminder"
    const val CHANNEL_KNOWLEDGE = "tcm_knowledge_update"

    private const val ID_REMINDER = 1001
    private const val ID_KNOWLEDGE = 1002

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        if (manager.getNotificationChannel(CHANNEL_REMINDER) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_REMINDER,
                    "每日复习提醒",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "在设定时间提醒当天待复习的卡片数量"
                }
            )
        }

        if (manager.getNotificationChannel(CHANNEL_KNOWLEDGE) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_KNOWLEDGE,
                    "知识库更新",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "发现可用的知识库新版本时提示"
                }
            )
        }
    }

    /** 当天有到期卡片时提醒。 */
    fun showReminder(context: Context, dueCount: Int) {
        val text = when {
            dueCount >= 50 -> "今天有 $dueCount 张卡片到期，建议分两次完成"
            dueCount > 0 -> "今天有 $dueCount 张卡片待复习"
            else -> return
        }
        notify(context, CHANNEL_REMINDER, ID_REMINDER, "今日复习提醒", text)
    }

    /** 发现知识库新版本时提示。 */
    fun showKnowledgeUpdate(context: Context, packCount: Int) {
        notify(
            context,
            CHANNEL_KNOWLEDGE,
            ID_KNOWLEDGE,
            "知识库有更新",
            "发现 $packCount 个知识包可更新，前往「我的 → 知识库」查看"
        )
    }

    private fun notify(
        context: Context,
        channelId: String,
        id: Int,
        title: String,
        text: String
    ) {
        // 未授权时直接返回：不请求、不报错。权限请求由界面在用户开启开关时发起。
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (!hasChannel(context, channelId)) return

        val notification = runCatching {
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context))
                .build()
        }.getOrNull() ?: return

        // 少数机型在权限被撤销的瞬间仍会抛 SecurityException，这里兜住。
        runCatching { manager.notify(id, notification) }
    }

    private fun hasChannel(context: Context, channelId: String): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.getNotificationChannel(channelId) != null
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
