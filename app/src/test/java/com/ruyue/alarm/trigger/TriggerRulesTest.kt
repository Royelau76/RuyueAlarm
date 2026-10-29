package com.ruyue.alarm.trigger

import com.ruyue.alarm.data.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 只测「五条纯规则」—— 不追求覆盖率，追求**挡住真实踩过的坑**。
 *
 * 每条用例的注释里都写了它对应哪次事故。这样测试的"为什么存在"不会随着时间丢失，
 * 将来有人想改规则时，至少能看到改坏的后果长什么样。
 *
 * 跑法：`cd RuyueAlarm && ./gradlew testDebugUnitTest`
 */
class TriggerRulesTest {

    // 固定一个"今天 09:00"的时刻，避免测试依赖真实时间
    private val today0900 = 1_760_000_000_000L // 任意基准，只用于加减
    private val minute = 60_000L
    private val hour = 60 * minute

    private val monday = java.util.Calendar.MONDAY   // 2
    private val saturday = java.util.Calendar.SATURDAY // 7
    private val sunday = java.util.Calendar.SUNDAY   // 1

    // ---------------- 时间闸门（v0.5.5：早上到公司就响）----------------

    @Test
    fun `还没到点 - 时间条件不成立`() {
        val unmet = TriggerRules.timeGateUnmet(
            hour = 14, minute = 0, repeatMask = Repeat.EVERY_DAY,
            now = today0900 + 9 * hour, // 09:00
            dayOfWeek = monday,
            targetTodayMillis = today0900 + 14 * hour, // 今天 14:00
        )
        assertEquals("时间（还没到 14:00）", unmet)
    }

    @Test
    fun `到点前几秒仍算成立 - 闹钟可能略早触发`() {
        val target = today0900 + 9 * hour
        val unmet = TriggerRules.timeGateUnmet(
            hour = 9, minute = 0, repeatMask = Repeat.EVERY_DAY,
            now = target - 3_000L, // 早 3 秒，在 5 秒容差内
            dayOfWeek = monday, targetTodayMillis = target,
        )
        assertNull(unmet)
    }

    @Test
    fun `到点后半小时仍成立 - 迟到也提醒`() {
        val target = today0900 + 9 * hour
        val unmet = TriggerRules.timeGateUnmet(
            hour = 9, minute = 0, repeatMask = Repeat.EVERY_DAY,
            now = target + 30 * minute,
            dayOfWeek = monday, targetTodayMillis = target,
        )
        assertNull(unmet)
    }

    @Test
    fun `超过一小时算今天错过 - 不能晚上八点还提醒下午的事`() {
        val target = today0900 + 14 * hour
        val unmet = TriggerRules.timeGateUnmet(
            hour = 14, minute = 0, repeatMask = Repeat.EVERY_DAY,
            now = target + 61 * minute,
            dayOfWeek = monday, targetTodayMillis = target,
        )
        assertEquals("时间（14:00 已过去超过 60 分钟）", unmet)
    }

    @Test
    fun `工作日提醒在周六不成立`() {
        val target = today0900 + 9 * hour
        val unmet = TriggerRules.timeGateUnmet(
            hour = 9, minute = 0, repeatMask = Repeat.WEEKDAYS,
            now = target,
            dayOfWeek = saturday, targetTodayMillis = target,
        )
        assertTrue(unmet!!.startsWith("时间（今天不是重复日"))
    }

    @Test
    fun `仅一次的提醒不受星期限制`() {
        val target = today0900 + 9 * hour
        val unmet = TriggerRules.timeGateUnmet(
            hour = 9, minute = 0, repeatMask = Repeat.ONCE,
            now = target,
            dayOfWeek = sunday, targetTodayMillis = target,
        )
        assertNull(unmet)
    }

    // ---------------- 重复日位（工作日/周末/自定义）----------------

    @Test
    fun `工作日掩码 - 周一到周五匹配 周末不匹配`() {
        listOf(
            java.util.Calendar.MONDAY,
            java.util.Calendar.TUESDAY,
            java.util.Calendar.WEDNESDAY,
            java.util.Calendar.THURSDAY,
            java.util.Calendar.FRIDAY,
        ).forEach { day ->
            assertTrue("工作日应匹配 dayOfWeek=$day", TriggerRules.maskMatches(Repeat.WEEKDAYS, day))
        }
        assertFalse(TriggerRules.maskMatches(Repeat.WEEKDAYS, saturday))
        assertFalse(TriggerRules.maskMatches(Repeat.WEEKDAYS, sunday))
    }

    @Test
    fun `周末掩码只匹配周六周日`() {
        assertTrue(TriggerRules.maskMatches(Repeat.WEEKEND, saturday))
        assertTrue(TriggerRules.maskMatches(Repeat.WEEKEND, sunday))
        assertFalse(TriggerRules.maskMatches(Repeat.WEEKEND, monday))
    }

    // ---------------- 冷却（v0.4.1 / v0.5.4：同一趟到访反复提醒、同秒响两遍）----------------

    @Test
    fun `冷却期内不放行`() {
        val now = 10 * hour
        assertFalse(TriggerRules.cooldownPassed(now - 59 * minute, cooldownMinutes = 60, now = now))
    }

    @Test
    fun `冷却到点即放行`() {
        val now = 10 * hour
        assertTrue(TriggerRules.cooldownPassed(now - 60 * minute, cooldownMinutes = 60, now = now))
    }

    @Test
    fun `冷却为零表示可以连续触发`() {
        val now = 10 * hour
        assertTrue(TriggerRules.cooldownPassed(now, cooldownMinutes = 0, now = now))
    }

    @Test
    fun `从未触发过 - 立刻放行`() {
        // 注意：lastFiredAt=0 只有在"真实时刻"下才必然满足冷却；
        // 用 epoch 附近的小数字当 now 会算成"刚响过"，所以这里用真实量级的基准
        assertTrue(TriggerRules.cooldownPassed(0L, cooldownMinutes = 1440, now = today0900))
    }

    // ---------------- 候选区滞环（v0.3.1：站在边缘反复进出）----------------

    @Test
    fun `候选区等于半径加最大值 - 半径300 得 800`() {
        assertEquals(800f, TriggerRules.candidateLimitMeters(300), 0.001f)
    }

    @Test
    fun `半径大于500时外扩用半径本身 - 半径1000 得 2000`() {
        assertEquals(2000f, TriggerRules.candidateLimitMeters(1000), 0.001f)
    }

    @Test
    fun `在外面时按未放大阈值判定`() {
        // 800 是边界：750 进、850 出
        assertTrue(TriggerRules.insideCandidate(750f, 300, wasInside = false))
        assertFalse(TriggerRules.insideCandidate(850f, 300, wasInside = false))
    }

    @Test
    fun `已经在里面时退出阈值放大两成 - 850 仍算在里面`() {
        // 800 * 1.2 = 960
        assertTrue(TriggerRules.insideCandidate(850f, 300, wasInside = true))
        assertTrue(TriggerRules.insideCandidate(959f, 300, wasInside = true))
        assertFalse(TriggerRules.insideCandidate(961f, 300, wasInside = true))
    }

    // ---------------- 到达容差（v0.5.0：301m vs 300m 差 1 米被拒）----------------

    @Test
    fun `擦边1米不再可惜 - 301米算到达`() {
        assertTrue(TriggerRules.withinRadiusTolerance(301f, 300))
    }

    @Test
    fun `超出五个点容差就不算到达`() {
        assertTrue(TriggerRules.withinRadiusTolerance(315f, 300))
        assertFalse(TriggerRules.withinRadiusTolerance(316f, 300))
    }
}
