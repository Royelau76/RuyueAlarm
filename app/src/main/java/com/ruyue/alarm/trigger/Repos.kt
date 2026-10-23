package com.ruyue.alarm.trigger

import android.content.Context
import com.ruyue.alarm.data.Db
import com.ruyue.alarm.data.LogRepository
import com.ruyue.alarm.data.ReminderDb
import com.ruyue.alarm.data.ReminderRepository

/**
 * 给 `BroadcastReceiver` 用的仓库构造。
 *
 * Receiver 里没有 Hilt 注入（那会引入额外的编译期风险），所以按项目既有的
 * 「Db 单例」套路手工拼装 —— 两个仓库都是无状态的薄封装，构造多少次都安全。
 * 时间触发收进 [TriggerEngine.onTimeAlarm] 之后就需要它。
 */
object Repos {

    fun reminders(context: Context): ReminderRepository = ReminderRepository(
        ReminderDb.get(context).reminderDao(),
        ReminderDb.get(context).ssidHistoryDao(),
        logs(context),
    )

    fun logs(context: Context): LogRepository = LogRepository(Db.get(context).eventLogDao())
}
