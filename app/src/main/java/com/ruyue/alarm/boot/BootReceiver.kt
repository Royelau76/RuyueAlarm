package com.ruyue.alarm.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ruyue.alarm.alarm.ReminderScheduler
import com.ruyue.alarm.data.EventType
import com.ruyue.alarm.data.EventWriter
import com.ruyue.alarm.data.ReminderDb
import com.ruyue.alarm.sentinel.SentinelController
import com.ruyue.alarm.sentinel.SentinelService
import com.ruyue.alarm.sentinel.SentinelWatchdog
import kotlinx.coroutines.runBlocking

/**
 * 开机/时间变更/应用更新时：
 *  1. 留一条证据日志（用来判断开机广播到底有没有收到）
 *  2. 如果用户之前开着哨兵，就把它拉回来
 *
 * 注意：Android 15 起，从 BOOT_COMPLETED 启动某些类型的前台服务会被拒绝
 * （dataSync / mediaPlayback 等）。我们用的是 specialUse，理论上允许 —— 这正是 M0 要验的点。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: "unknown"

        val pending = goAsync()
        EventWriter.write(
            context,
            EventType.BOOT,
            detail = "收到 $action；常驻模式，自动拉起哨兵",
        ) { pending.finish() }

        // 开机后把自愈链重新排上（闹钟不会跨重启保留），再尝试拉起哨兵
        SentinelWatchdog.schedule(context)

        // M1：把所有启用中的时间提醒重排（开机/时区变更/应用更新都会走到这里）
        Thread {
            runCatching {
                val all = runBlocking { ReminderDb.get(context).reminderDao().enabled() }
                ReminderScheduler.rescheduleAll(context, all)
                EventWriter.write(
                    context,
                    EventType.NOTE,
                    detail = "已重排 ${all.count { it.timeEnabled }} 条时间提醒",
                )
            }
        }.start()

        // 常驻模式（2026-10-21 用户要求）：不再询问 wanted，开机/更新后无条件拉起。
        // BOOT_COMPLETED 与 MY_PACKAGE_REPLACED 都是启动 specialUse 前台服务的官方豁免。
        SentinelService.setWanted(context, true)
        runCatching { SentinelController.start(context) }
    }
}
