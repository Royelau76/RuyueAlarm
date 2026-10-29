package com.ruyue.alarm.trigger

import com.ruyue.alarm.data.Repeat

/**
 * 触发判定的**纯规则**。
 *
 * ## 为什么要从 TriggerEngine 里抽出来
 *
 * 这些判定原本散在 `TriggerEngine` 里，但那里每个函数都吃 `Context`、读数据库、取系统时间，
 * 想在单测里直接跑就得 mock 一大堆东西 —— 那正是"全面覆盖"陷阱的开始。
 *
 * 这里只保留**没有副作用**的部分：入参全是标量，`now` / `dayOfWeek` / `targetTodayMillis`
 * 都由调用方算好传进来。这样 `TriggerRulesTest` 能在普通 JVM 上跑（`gradlew testDebugUnitTest`，
 * 不需要手机），而 `TriggerEngine` 只负责编排。
 *
 * ## 每条规则都对着一个真实踩过的 bug
 *
 * | 规则 | 对应事故 |
 * |---|---|
 * | [timeGateUnmet] | v0.5.5：引擎「且」模式只做了单向闸门 → 「到公司且 14:00」早上到公司就响 |
 * | [cooldownPassed] | v0.4.1 / v0.5.4：同一趟到访反复提醒、同一秒响两遍 |
 * | [insideCandidate] | v0.3.1：站在半径边缘定位抖动 → 反复判定「进出」 |
 * | [withinRadiusTolerance] | v0.5.0：复核距离 301m vs 半径 300m，**差 1 米被拒**，白等一轮 |
 * | [maskMatches] | 重复日判断（工作日/周末/自定义星期位） |
 */
object TriggerRules {

    /** 粗定位候选区外扩：`候选区 = 半径 + max(这个值, 半径)` */
    const val CANDIDATE_EXTRA_METERS = 500f

    /** 出去判定比进来判定宽 20%：站在半径边缘时定位会抖，不设滞环会反复「进出」 */
    const val EXIT_HYSTERESIS = 1.2f

    /** 判定「到了」时给半径留 5% 容差 —— 擦边不再可惜 */
    const val FIRE_TOLERANCE = 1.05f

    /** 时间闸门允许早几秒：闹钟可能略早于整分触发，不能让它把自己挡掉 */
    const val TIME_GATE_EARLY_MS = 5_000L

    /** 到点后 1 小时内仍然算「这次的有效时间」：迟到也提醒，超了就当作今天错过 */
    const val TIME_GATE_GRACE_MS = 60 * 60 * 1000L

    /** `%02d:%02d` */
    fun hhmm(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

    /**
     * 重复规则是否包含某一天。
     *
     * @param dayOfWeek `Calendar.DAY_OF_WEEK` 的取值：1 = 周日 … 7 = 周六
     */
    fun maskMatches(mask: Int, dayOfWeek: Int): Boolean {
        if (mask == Repeat.ONCE) return true
        val index = (dayOfWeek + 5) % 7 // 0 = 周一，与 Repeat.DAY_NAMES 顺序一致
        return mask and (1 shl index) != 0
    }

    /** 冷却是否已过（过了才允许再次触发） */
    fun cooldownPassed(lastFiredAt: Long, cooldownMinutes: Int, now: Long): Boolean =
        now - lastFiredAt >= cooldownMinutes.toLong() * 60_000L

    /**
     * 「且」模式下**时间条件**是否成立。
     *
     * @param now 当前时刻
     * @param dayOfWeek 今天星期几（`Calendar.DAY_OF_WEEK`）
     * @param targetTodayMillis 今天的提醒时刻（由调用方用 Calendar 算好）
     * @return null = 成立；否则返回给日志看的人话原因
     */
    fun timeGateUnmet(
        hour: Int,
        minute: Int,
        repeatMask: Int,
        now: Long,
        dayOfWeek: Int,
        targetTodayMillis: Long,
    ): String? {
        val label = hhmm(hour, minute)
        if (!maskMatches(repeatMask, dayOfWeek)) {
            return "时间（今天不是重复日：${Repeat.label(repeatMask)}）"
        }
        // 允许早几秒：闹钟可能略早于整分触发，不能让它把自己挡掉
        if (now < targetTodayMillis - TIME_GATE_EARLY_MS) return "时间（还没到 $label）"
        if (now > targetTodayMillis + TIME_GATE_GRACE_MS) {
            return "时间（$label 已过去超过 ${TIME_GATE_GRACE_MS / 60_000} 分钟）"
        }
        return null
    }

    /** 粗定位「候选区」半径：宁可早一点开始计时（后面还有 GPS 精确复核兜着） */
    fun candidateLimitMeters(
        radiusMeters: Int,
        extraMeters: Float = CANDIDATE_EXTRA_METERS,
    ): Float = radiusMeters + maxOf(extraMeters, radiusMeters.toFloat())

    /**
     * 给粗定位用：这个距离算不算「在候选区里」。
     *
     * 关键在于 [wasInside] —— 已经在里面时，退出阈值放宽 [EXIT_HYSTERESIS] 倍，
     * 避免站在边缘上被定位抖动反复判成「进进出出」。
     */
    fun insideCandidate(
        distanceMeters: Float,
        radiusMeters: Int,
        wasInside: Boolean,
        extraMeters: Float = CANDIDATE_EXTRA_METERS,
        hysteresis: Float = EXIT_HYSTERESIS,
    ): Boolean {
        val base = candidateLimitMeters(radiusMeters, extraMeters)
        val limit = if (wasInside) base * hysteresis else base
        return distanceMeters <= limit
    }

    /**
     * 给 GPS 精确复核用：是否真的在半径内（带 5% 容差）。
     *
     * 实测 10-16：半径 300m、复核距离 301m —— **差 1 米被拒**，白等一轮才响。
     */
    fun withinRadiusTolerance(
        distanceMeters: Float,
        radiusMeters: Int,
        tolerance: Float = FIRE_TOLERANCE,
    ): Boolean = distanceMeters <= radiusMeters * tolerance
}
