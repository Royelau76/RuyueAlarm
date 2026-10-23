package com.ruyue.alarm.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmScheduler {

    const val EXTRA_LABEL = "label"
    private const val REQ_FIRE = 1001
    private const val REQ_SHOW = 1002

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.canUseFullScreenIntent()
    }

    /**
     * 用 setAlarmClock 而不是 setExactAndAllowWhileIdle：
     * 前者不需要「精确闹钟」权限、免受 Doze 影响，还会在系统状态栏显示闹钟图标，
     * 是闹钟类应用最可靠的一条路（M0 第 3 项验证的就是它 + 全屏意图）。
     */
    fun scheduleAlarmClock(context: Context, triggerAtMillis: Long, label: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return

        val fire = PendingIntent.getBroadcast(
            context,
            REQ_FIRE,
            Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_LABEL, label),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val show = PendingIntent.getActivity(
            context,
            REQ_SHOW,
            Intent(context, RingActivity::class.java).putExtra(EXTRA_LABEL, label),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, show), fire)
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context,
            REQ_FIRE,
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
    }

    fun nextTriggerTime(context: Context): Long? {
        val am = context.getSystemService(AlarmManager::class.java) ?: return null
        return am.nextAlarmClock?.triggerTime
    }
}
