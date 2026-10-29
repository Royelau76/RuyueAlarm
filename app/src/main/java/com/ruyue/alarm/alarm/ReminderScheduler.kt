package com.ruyue.alarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ruyue.alarm.data.Reminder
import com.ruyue.alarm.data.Repeat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 时间触发的排定与重排。
 *
 * 用 `setAlarmClock` 而不是 `setExactAndAllowWhileIdle`：
 * 前者**不需要精确闹钟权限**、免除 Doze、还会在状态栏显示闹钟图标 —— M0 第 3 项已实测
 * 锁屏下能正常全屏弹出，所以直接沿用这条验证过的路径。
 */
object ReminderScheduler {

    const val EXTRA_REMINDER_ID = "reminder_id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_NOTE = "note"

    /** 「稍后提醒」的分钟数；0 = 不是稍后提醒（正常排期） */
    const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"

    private fun fireRequestCode(reminderId: Long): Int = (1000 + reminderId % 100000).toInt()

    private fun showRequestCode(reminderId: Long): Int = fireRequestCode(reminderId) + 50000

    /**
     * 「稍后提醒」用**独立的 request code**。
     *
     * ⚠️ 这是个真 bug 的修复（v0.10.0 之前）：稍后提醒原来复用 [fireRequestCode]，
     * 而 `PendingIntent` 是按 (类型, requestCode, intent) 唯一化的 —— 同一个 code 的
     * `getBroadcast` + `setAlarmClock` 会**覆盖掉原来的"下一次"闹钟**。
     * 结果：重复提醒一旦点过「稍后」，第二天就再也不会响（而且日志里看不出异常）。
     */
    private fun snoozeRequestCode(reminderId: Long): Int = fireRequestCode(reminderId) + 200000

    /**
     * 下一次时间触发时刻；没有配时间触发、或找不到合适的日期时返回 null。
     */
    fun nextTriggerAt(reminder: Reminder, now: Long = System.currentTimeMillis()): Long? {
        if (!reminder.timeEnabled) return null

        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (reminder.repeatMask == Repeat.ONCE) {
            // 仅一次：今天的点已经过了就顺延到明天（跟系统闹钟的直觉一致）
            if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }

        // 每天/工作日/自定义：从今天起最多找 8 天
        repeat(8) {
            if (cal.timeInMillis > now && maskHasDay(reminder.repeatMask, cal)) {
                return cal.timeInMillis
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    /** Calendar 里 1=周日…7=周六；本项目位序是 0=周一…6=周日 */
    private fun maskHasDay(mask: Int, cal: Calendar): Boolean {
        val index = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        return mask and (1 shl index) != 0
    }

    fun schedule(context: Context, reminder: Reminder) {
        val at = nextTriggerAt(reminder) ?: return
        scheduleAt(context, at, reminder.id, reminder.title, reminder.note)
    }

    /**
     * 排定一次触发。
     *
     * @param snoozeMinutes > 0 表示这是**用户点的「稍后提醒」**：
     *   · 用独立的 request code（否则会覆盖正式的"下一次"闹钟，见 [snoozeRequestCode]）
     *   · intent 里带上这个值，让引擎**跳过条件闸门** —— 用户已经明确要求过 N 分钟再提醒，
     *     此时再按「且」模式重查条件（比如还没到点/已离开 WiFi）就会静默失效
     */
    fun scheduleAt(
        context: Context,
        triggerAtMillis: Long,
        reminderId: Long,
        title: String,
        note: String,
        snoozeMinutes: Int = 0,
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val requestCode = if (snoozeMinutes > 0) {
            snoozeRequestCode(reminderId)
        } else {
            fireRequestCode(reminderId)
        }

        val fire = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java)
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_NOTE, note)
                .putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val show = PendingIntent.getActivity(
            context,
            showRequestCode(reminderId),
            Intent(context, RingActivity::class.java)
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_NOTE, note),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, show), fire)
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context,
            fireRequestCode(reminderId),
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching { am.cancel(pi) }
    }

    /** 开机 / 时区变更 / 应用更新后，把所有启用中的时间提醒重新排一遍 */
    fun rescheduleAll(context: Context, reminders: List<Reminder>) {
        reminders.forEach { reminder ->
            if (reminder.enabled && reminder.timeEnabled) schedule(context, reminder)
        }
    }

    fun describeNext(reminder: Reminder): String? =
        nextTriggerAt(reminder)?.let {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it))
        }
}
