package com.ruyue.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ruyue.alarm.data.EventType
import com.ruyue.alarm.data.EventWriter
import com.ruyue.alarm.trigger.TriggerEngine
import kotlinx.coroutines.runBlocking

/**
 * 时间触发的广播入口。两条路都走这里：
 *  · **提醒**：intent 里带 `reminder_id` → 交给 [TriggerEngine.onTimeAlarm]（统一入口，
 *    这样「且」模式、冷却、落库、响铃全套逻辑与 WiFi/地点触发完全一致）
 *  · **M0 的闹钟测试页**：只带 label → 直接发通知。保留它是为了随时回归验证全屏意图
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, 0L)
        if (reminderId > 0L) {
            handleReminder(context, reminderId, intent)
        } else {
            val label = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL) ?: "测试提醒"
            val pending = goAsync()
            EventWriter.write(
                context,
                EventType.ALARM_FIRED,
                detail = "闹钟测试触发：$label",
            ) { pending.finish() }
            ReminderNotifier.show(context, 0L, "如约 · 测试提醒", label)
        }
    }

    private fun handleReminder(context: Context, reminderId: Long, intent: Intent) {
        val fallbackTitle = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "提醒"
        val fallbackNote = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE) ?: ""
        // > 0 表示这次是用户点的「稍后提醒」，引擎要跳过条件闸门
        val snoozeMinutes = intent.getIntExtra(ReminderScheduler.EXTRA_SNOOZE_MINUTES, 0)

        // 读库/定位判定要花时间，用 goAsync 把广播的存活窗口撑开
        val pending = goAsync()
        Thread {
            val result = runCatching {
                runBlocking {
                    TriggerEngine.onTimeAlarm(
                        context,
                        reminderId,
                        fallbackTitle,
                        fallbackNote,
                        snoozeMinutes,
                    )
                }
            }
            val error = result.exceptionOrNull()
            if (error == null) {
                pending.finish()
            } else {
                EventWriter.write(
                    context,
                    EventType.ERROR,
                    detail = "时间触发处理失败：${error.message}",
                ) { pending.finish() }
            }
        }.start()
    }
}
