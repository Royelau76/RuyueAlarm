package com.ruyue.alarm.sentinel

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ruyue.alarm.R
import com.ruyue.alarm.RuyueApp
import com.ruyue.alarm.data.Db
import com.ruyue.alarm.data.EventType
import com.ruyue.alarm.data.EventWriter
import kotlinx.coroutines.runBlocking

/**
 * 哨兵的自愈机制（看门狗）。
 *
 * 为什么必须有：M0 实测（2026-10-01）里哨兵被系统杀掉后，直到用户手动打开 App 或重启手机
 * 都没能恢复，中间 7 天一条日志都没有。对「到达提醒」这种产品，服务悄悄死掉就等于功能失效。
 *
 * 为什么用 AlarmManager 而不是 WorkManager：Android 12+ 禁止应用在后台启动前台服务，
 * 但「**精确闹钟触发的广播**」是官方豁免之一。WorkManager 的任务没有这个豁免，
 * 从里面 startForegroundService 会抛 ForegroundServiceStartNotAllowedException。
 * 所以看门狗走闹钟 + 广播；没拿到精确闹钟授权时退化成不精确闹钟，只能尽力而为
 * （失败会在日志里留下 ERROR，不会静默）。
 *
 * 判活依据是数据库里「最后一条心跳」而不是内存状态 —— 进程被杀后内存状态就没了，
 * 只有落库的心跳时间能跨进程判断服务是否还活着。
 */
object SentinelWatchdog {

    /**
     * 心跳间隔 5 分钟；超过这个时长没有任何心跳就认为服务死了。
     *
     * 从 12 分钟收到 7 分钟的原因（2026-10-11 实测）：那天 09:12 左右进程被静默杀掉，
     * 紧接着 09:23 的自检槽因为「最后心跳才 11.5 分钟」判定为「没死」而跳过，
     * 一直拖到下一个槽才可能补上。阈值必须明显小于检查间隔，否则会整槽空转。
     */
    const val DEAD_AFTER_MS = 7 * 60 * 1000L

    /**
     * 多久检查一次。
     *
     * 用 10 分钟而不是 5 分钟：`setExactAndAllowWhileIdle` 在 Doze 下被限流为
     * **每 9 分钟最多一次**，排 5 分钟只会被系统丢弃；10 分钟既在限流之上，
     * 又能把「被杀 → 被发现」压到十几分钟内。
     */
    const val CHECK_INTERVAL_MS = 10 * 60 * 1000L

    private const val REQ_WATCHDOG = 7001
    private const val ACTION_WATCHDOG = "com.ruyue.alarm.action.WATCHDOG"

    /** 排下一次自检。可重复调用（同一个 PendingIntent 会被替换，不会堆叠）。 */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = System.currentTimeMillis() + CHECK_INTERVAL_MS
        val pi = pendingIntent(context)
        runCatching {
            if (isExactAllowed(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
    }

    /** 用户主动停用哨兵时调用，避免看门狗又把它拉起来。 */
    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.cancel(pendingIntent(context)) }
    }

    fun isExactAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQ_WATCHDOG,
        Intent(context, WatchdogReceiver::class.java).setAction(ACTION_WATCHDOG),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 闹钟是一次性的：无论本次检查结果如何，都先把下一次排上，否则自愈链会断
        SentinelWatchdog.schedule(context)

        val pending = goAsync()
        Thread {
            // 顺带跑一次「触发对账」：确认上次该响的提醒都真的响了。
            // 内部有 20 小时节流，不是每 10 分钟都干活；失败绝不能影响下面的保活判断。
            runCatching { runBlocking { com.ruyue.alarm.trigger.Ledger.runCheck(context) } }

            val wanted = SentinelService.isWanted(context)
            val running = ServiceState.running.value
            val lastBeat = runCatching {
                runBlocking { Db.get(context).eventLogDao().lastOfType(EventType.HEARTBEAT) }
            }.getOrNull()
            val sinceMs = System.currentTimeMillis() - (lastBeat?.timestamp ?: 0L)
            val dead = !running && (lastBeat == null || sinceMs > SentinelWatchdog.DEAD_AFTER_MS)

            if (!wanted || !dead) {
                pending.finish()
            } else {
                val started = runCatching { SentinelController.start(context) }
                EventWriter.write(
                    context,
                    if (started.isSuccess) EventType.NOTE else EventType.ERROR,
                    detail = buildString {
                        append("看门狗：哨兵不在运行（最后心跳距今 ${sinceMs / 1000}s）")
                        if (started.isSuccess) {
                            append("，已自动拉起")
                        } else {
                            append("，自动拉起失败：")
                            append(
                                started.exceptionOrNull()
                                    ?.let { "${it.javaClass.simpleName}: ${it.message}" }
                            )
                            append("（改发通知提醒，等用户点一下）")
                        }
                    },
                ) {
                    // 自动拉起失败（多半是系统禁止后台启动前台服务）→ 退化成一条通知。
                    // 用户点通知属于「用户操作」，是官方豁免路径，能正常启动前台服务。
                    if (!started.isSuccess) postRecoveryNotification(context)
                    pending.finish()
                }
            }
        }.start()
    }

    /**
     * 自动恢复失败时的兜底：发一条高优先级通知，用户点一下即可恢复监听。
     * 这条路径存在的意义：把「静默死掉」变成「一眼看见 + 一次点击恢复」。
     */
    @SuppressLint("MissingPermission")
    private fun postRecoveryNotification(context: Context) {
        val restart = PendingIntent.getForegroundService(
            context,
            REQ_RESTART,
            Intent(context, SentinelService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, RuyueApp.CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("如约 · 地点监听已停止")
            .setContentText("系统阻止了自动恢复，点一下立即重新开始监听")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(restart)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(REQ_RESTART, notification)
        }
    }

    private companion object {
        const val REQ_RESTART = 7002
    }
}
