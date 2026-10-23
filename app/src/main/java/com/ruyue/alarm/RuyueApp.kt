package com.ruyue.alarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.ruyue.alarm.alarm.ReminderScheduler
import com.ruyue.alarm.data.Db
import com.ruyue.alarm.data.ReminderDb
import com.ruyue.alarm.sentinel.SentinelController
import com.ruyue.alarm.sentinel.SentinelService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class RuyueApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
        Db.get(this) // 预热数据库，避免第一次落日志时卡住
        rescheduleOnAppOpen()
    }

    /**
     * 「启动即监听」+「打开应用即重新排定」。
     *
     * 2026-10-21 用户要求：**不想管哨兵，只要在用就应该在监听**。所以这里不再检查
     * 用户之前有没有手动开过 —— 每次 App 进程启动（开应用、点通知、闹钟广播等）
     * 都无条件把它拉起来，并同步把 wanted 置回 true。
     *
     * 这也是用户点过「强行停止」或系统清过闹钟之后**唯一能立刻自愈的时机**：
     * 看门狗要等下一个自检槽（最多十几分钟），而用户打开 App 是马上发生的。
     * 从后台广播里启动前台服务可能被 Android 12+ 拒绝（非闹钟/开机广播时），
     * 所以整段都包在 runCatching 里，失败了也不影响闹钟重排。
     */
    private fun rescheduleOnAppOpen() {
        val app = applicationContext
        Thread {
            runCatching {
                val all = runBlocking { ReminderDb.get(app).reminderDao().enabled() }
                ReminderScheduler.rescheduleAll(app, all)
            }
            runCatching {
                SentinelService.setWanted(app, true)
                SentinelController.start(app)
            }.onFailure { e ->
                // 从后台广播（非闹钟/非开机）启动前台服务会被 Android 12+ 拒绝；
                // 留一条证据，否则"自动监听没起来"这种情况永远查不出来。
                com.ruyue.alarm.data.EventWriter.write(
                    app,
                    com.ruyue.alarm.data.EventType.NOTE,
                    detail = "自动启动监听失败（${e.javaClass.simpleName}）：${e.message}",
                )
            }
        }.start()
    }

    companion object {
        /**
         * 哨兵用的「无感」通知渠道。
         *
         * ⚠️ 必须用**新的 channel id**：Android 的渠道重要性**创建后不可修改**（只有用户能改），
         * 所以想让"监听通知"从状态栏消失，改旧渠道参数是无效的 —— 必须新建一个
         * `IMPORTANCE_MIN` 渠道切过去。IMPORTANCE_MIN 的效果：只在通知栏折叠区里，
         * **状态栏不出图标、不响、不亮屏、锁屏也不显示**（前台服务仍然合法存活）。
         *
         * 前台服务的通知本身是删不掉的（Android 硬约束），这是"无感"能做到的极限。
         */
        const val CHANNEL_SENTINEL_QUIET = "sentinel_quiet"

        /** 旧渠道（IMPORTANCE_LOW，状态栏会显示图标）—— 已弃用，仅用于删除 */
        private const val CHANNEL_SENTINEL_LEGACY = "sentinel"

        const val CHANNEL_ALARM = "alarm_test"

        /** 看门狗自动恢复失败时用这个渠道提醒用户点一下 */
        const val CHANNEL_ALERT = "sentinel_alert"

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return

            val sentinel = NotificationChannel(
                CHANNEL_SENTINEL_QUIET,
                context.getString(R.string.channel_sentinel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = context.getString(R.string.channel_sentinel_desc)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            }

            val alert = NotificationChannel(
                CHANNEL_ALERT,
                context.getString(R.string.channel_alert_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_alert_desc)
            }

            val alarm = NotificationChannel(
                CHANNEL_ALARM,
                context.getString(R.string.channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_alarm_desc)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            nm.createNotificationChannel(sentinel)
            nm.createNotificationChannel(alert)
            nm.createNotificationChannel(alarm)

            // 旧渠道留着只会在"通知设置"里多一项，删掉
            runCatching { nm.deleteNotificationChannel(CHANNEL_SENTINEL_LEGACY) }
        }
    }
}
