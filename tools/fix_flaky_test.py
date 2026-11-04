# -*- coding: utf-8 -*-
"""修一个我自己写的时间依赖测试。

`指定今天且时间还没到 - 就排今天` 原来用 System.currentTimeMillis() + 2 小时：
晚上 22 点之后 +2h 会跨到明天，于是"今天那个 hh:mm"变成过去时刻 →
nextTriggerAt 正确返回 null → 断言挂掉。2026-11-04 22:0x 实际撞到了。

修法：注入固定的 now（nextTriggerAt 本来就有 now 参数，是我没用好）。
"""
P = r"D:\My Projects\apps\RuyueAlarm\app\src\test\java\com\ruyue\alarm\alarm\ReminderSchedulerTest.kt"
s = open(P, encoding="utf-8").read()

old = '''    @Test
    fun `指定今天且时间还没到 - 就排今天`() {
        val now = System.currentTimeMillis()
        // 现在的时刻 +2 小时，取分钟对齐（跨天就近到明天，测的是"正常情况"）
        val cal = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.HOUR_OF_DAY, 2) }
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val at = ReminderScheduler.nextTriggerAt(reminder(dayStart(0), hour = h, minute = m))
        assertNotNull(at)
        assertEquals(dayOf(now), dayOf(at!!))
    }'''

new = '''    @Test
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
    }'''

if old in s:
    s = s.replace(old, new)
    open(P, "w", encoding="utf-8", newline="\n").write(s)
    print("✓ 已改成注入固定 now")
else:
    print("✗ 没匹配到原文")
