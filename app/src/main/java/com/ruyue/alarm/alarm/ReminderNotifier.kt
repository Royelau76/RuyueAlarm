package com.ruyue.alarm.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ruyue.alarm.R
import com.ruyue.alarm.RuyueApp
import com.ruyue.alarm.data.EventType
import com.ruyue.alarm.data.EventWriter

/**
 * 统一的「该提醒了」通知 —— 时间触发和 WiFi 触发都走这里，
 * 保证两种来源的响铃体验完全一致（也只需要维护一份全屏意图逻辑）。
 */
object ReminderNotifier {

    @SuppressLint("MissingPermission")
    fun show(
        context: Context,
        reminderId: Long,
        title: String,
        note: String,
        ringtoneUri: String = "",
        rampSeconds: Int = 0,
    ) {        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            EventWriter.write(
                context,
                EventType.NOTE,
                detail = "通知权限未授予，提醒「$title」无法展示（触发本身已发生）",
            )
            return
        }

        val full = PendingIntent.getActivity(
            context,
            (3000 + reminderId % 100000).toInt(),
            Intent(context, RingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
                .putExtra(ReminderScheduler.EXTRA_TITLE, title)
                .putExtra(ReminderScheduler.EXTRA_NOTE, note)
                // 响铃页自己放音，所以铃声与渐强都从通知带过去（全屏意图和点击通知走同一条路）
                .putExtra(RingActivity.EXTRA_RINGTONE, ringtoneUri)
                .putExtra(RingActivity.EXTRA_RAMP_SECONDS, rampSeconds),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, RuyueApp.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title.ifBlank { "如约 · 提醒" })
            .setContentText(note.ifBlank { "该做这件事了" })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // 锁屏/息屏时靠它全屏拉起响铃页；用户正在用手机时只会出横幅（系统行为）
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(reminderId), notification)
        }
    }

    /**
     * 撤掉这条提醒的通知。
     *
     * 为什么必须有它（2026-10-23 用户实测）：`setAutoCancel(true)` 只在**用户点通知**时生效；
     * 而"点响铃页的『我知道了』"这条路只是 `finish()` 掉 Activity，**通知会一直留在状态栏**。
     * 之后点那条残留通知 → 又拉起响铃页 → **又响一遍**（日志里表现为"没有 ALARM_FIRED 的
     * RING_OPENED"，10-21 19:19 与 23:58 各一次）。
     */
    fun cancel(context: Context, reminderId: Long) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
        }
    }

    private fun notificationId(reminderId: Long): Int = (2000 + reminderId % 100000).toInt()
}
