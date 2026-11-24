# -*- coding: utf-8 -*-
"""方案 B：响铃 5 分钟无人处理自动停止后，补一条高优先级通知。

点它 → 打开响铃页但**静音**（不会再吵一次），仍能看到「为什么现在响」。
"""
import os

AK = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm"


def patch(rel, pairs):
    p = os.path.join(AK, rel)
    s = open(p, encoding="utf-8").read()
    for old, new, label in pairs:
        if old in s:
            s = s.replace(old, new, 1)
            print("  ✓", label)
        else:
            print("  ✗ 没匹配到:", label)
            print("      片段:", old[:70].replace("\n", "⏎"))
    open(p, "w", encoding="utf-8", newline="\n").write(s)


# ---------- 1) ReminderNotifier：新增 showMissed ----------
patch("alarm/ReminderNotifier.kt", [(
    """    /**
     * 撤掉这条提醒的通知。""",
    """    /**
     * 「这条提醒你一直没处理」—— 响铃 5 分钟自动停止后补发一条高优先级通知。
     *
     * 起因（2026-11-24 用户选方案 B）：11-23 22:00 的提醒其实响了，但闹钟音量只有 1/15、
     * 5 分钟无人处理后自动停止，等于"静默漏掉"。现在会留一条能看见的通知，
     * 点开进响铃页但不重新出声。
     */
    @SuppressLint("MissingPermission")
    fun showMissed(
        context: Context,
        reminderId: Long,
        title: String,
        note: String,
    ) {
        cancel(context, reminderId) // 先撤掉原来那条「正在响」的常驻通知
        val open = PendingIntent.getActivity(
            context,
            (3000 + reminderId % 100000).toInt(),
            Intent(context, RingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
                .putExtra(ReminderScheduler.EXTRA_TITLE, title)
                .putExtra(ReminderScheduler.EXTRA_NOTE, note)
                .putExtra(RingActivity.EXTRA_SILENT, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, RuyueApp.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("「$title」响过了，你没处理")
            .setContentText(if (note.isBlank()) "点开看看是哪一条" else note)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(reminderId), n)
        }
    }

    /**
     * 撤掉这条提醒的通知。""",
    "新增 showMissed",
)])

# ---------- 2) RingActivity：EXTRA_SILENT + 静音打开 + 自动停止时补通知 ----------
patch("alarm/RingActivity.kt", [(
    """        // 正常情况下声音**已经**在通知发出时由 RingPlayer 开始播了。
        // 只有一种情况需要在这里补一次：用户点开的是"响铃已自动停止"的旧通知。
        if (!RingPlayer.isPlaying()) {""",
    """        // 静音打开：点「响过了，你没处理」那条通知进来时不要再响一遍
        val silentOpen = intent?.getBooleanExtra(EXTRA_SILENT, false) ?: false

        // 正常情况下声音**已经**在通知发出时由 RingPlayer 开始播了。
        // 只有一种情况需要在这里补一次：用户点开的是"响铃已自动停止"的旧通知。
        if (!silentOpen && !RingPlayer.isPlaying()) {""",
    "静音打开不重新出声",
), (
    """    const val EXTRA_RAMP_SECONDS = "ruyue.extra.ramp_seconds\"""",
    """    const val EXTRA_RAMP_SECONDS = "ruyue.extra.ramp_seconds"

    /** true = 只展示响铃页、不重新出声（点「响过了但没处理」的通知进来时用） */
    const val EXTRA_SILENT = "ruyue.extra.silent\"""",
    "加 EXTRA_SILENT 常量",
)])
print("完成")
