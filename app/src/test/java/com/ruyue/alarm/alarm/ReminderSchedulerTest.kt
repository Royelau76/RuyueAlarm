package com.ruyue.alarm.alarm

import com.ruyue.alarm.data.Reminder
import com.ruyue.alarm.data.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/**
 * 只测 `nextTriggerAt` 这一条纯计算：「仅一次 + 指定日期」到底排在哪一刻。
 *
 * 为什么要单独测它：这条逻辑错了不会崩，只会**静默不响**——
 *  · 日期读错一天 → 少响一天
 *  · 已过去的日期还返回时刻 → 会排一个"立刻触发"的闹钟，一保存就响
 *  · 已过去的日期返回 0/负值 → AlarmManager 立刻触发，同样一保存就响
 *
 * 用「相对今天」构造日期，避免依赖跑测试的具体日期。
 */
class ReminderSchedulerTest {

    private fun dayStart(offsetDays: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, offsetDays)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun reminder(date: Long, hour: Int = 9, minute: Int = 0) = Reminder(
        timeEnabled = true,
        hour = hour,
        minute = minute,
        repeatMask = Repeat.ONCE,
        onceDate = date,
    )

    private fun hmOf(millis: Long): String = Calendar.getInstance().apply {
        timeInMillis = millis
    }.let { "%02d:%02d".format(it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE)) }

    private fun dayOf(millis: Long): Int = Calendar.getInstance().apply {
        timeInMillis = millis
    }.get(Calendar.DAY_OF_YEAR)

    @Test
    fun `指定明天 - 排在明天那个时刻`() {
        val at = ReminderScheduler.nextTriggerAt(reminder(dayStart(1), hour = 14, minute = 30))
        assertNotNull(at)
        assertEquals(dayOf(dayStart(1)), dayOf(at!!))
        assertEquals("14:30", hmOf(at))
    }

    @Test
    fun `指定今天且时间还没到 - 就排今天`() {
        // ⚠️ 必须注入固定的 now。原来用 System.currentTimeMillis() + 2 小时：
        // 晚上 22 点之后 +2h 会跨到明天，"今天那个 hh:mm"就成了过去时刻 →
        // nextTriggerAt 正确返回 null → 断言必然挂（2026-11-04 22:0x 真的撞到了）。
        val fixedNow = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val at = ReminderScheduler.nextTriggerAt(
            reminder(dayStart(0), hour = 11, minute = 30),
            now = fixedNow,
        )
        assertNotNull(at)
        assertEquals(dayOf(fixedNow), dayOf(at!!))
        assertEquals("11:30", hmOf(at))
    }

    @Test
    fun `指定昨天 - 没有下一次（返回 null，不能排成"立刻响"）`() {
        assertNull(ReminderScheduler.nextTriggerAt(reminder(dayStart(-1))))
    }

    @Test
    fun `指定明天但已停用 - 也返回 null`() {
        assertNull(
            ReminderScheduler.nextTriggerAt(reminder(dayStart(1)).copy(timeEnabled = false))
        )
    }

    @Test
    fun `老的仅一次没有日期 - 仍然按下一个时刻顺延`() {
        val r = Reminder(timeEnabled = true, hour = 9, minute = 0, repeatMask = Repeat.ONCE, onceDate = 0L)
        val at = ReminderScheduler.nextTriggerAt(r, now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis)
        assertNotNull(at)
        // 23:30 时"09:00 仅一次"→ 顺延到明天 09:00
        assertEquals("09:00", hmOf(at!!))
    }
}
