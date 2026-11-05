package com.ruyue.alarm.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条提醒。M1 的最小可用模型：**一条提醒可以同时配「时间」和「WiFi」两种触发**（或的关系）。
 *
 * 为什么没有拆成 triggers 表：自用场景下「一个提醒 = 一个时间点 + 一组家门口的 SSID」已经够用，
 * 拆表会带来一堆 join 和 UI 复杂度。哪天真需要「一条提醒挂多个不同地点/多个时间点」再规范化。
 */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val note: String = "",
    val enabled: Boolean = true,

    // ---------------- 时间触发 ----------------
    val timeEnabled: Boolean = false,
    val hour: Int = 9,
    val minute: Int = 0,
    /** 见 [Repeat]；按位表示星期几（bit0=周一 … bit6=周日），0 表示仅一次 */
    val repeatMask: Int = Repeat.EVERY_DAY,
    /**
     * 「仅一次」时指定的**日历日期**（那天 00:00 的本地毫秒；0 = 未指定，按老行为"下一个 hh:mm"）。
     *
     * 语义：`repeatMask == ONCE && onceDate > 0` → 只在那一天的 [hour]:[minute] 响一次；
     * 响完（ONCE 会自动停用）或那天过去后就再也没有下一次。
     *
     * 为什么复用 ONCE 而不是新增重复类型：ONCE 本来就是"不按星期重复"，
     * 日历日期只是把它从"下一个 hh:mm"收紧成"某一个 hh:mm"，
     * [TriggerRules.maskMatches] 等按位逻辑一行都不用改。
     */
    val onceDate: Long = 0L,

    /**
     * 「今天不再」的抑制截止时刻（本地明天 00:00 的毫秒数）。0 = 没有抑制。
     *
     * 为什么存"截止时刻"而不是布尔：到点自动失效，不需要任何每天清零的任务
     * （清零任务一旦漏跑，提醒就永久哑掉）。
     */
    @ColumnInfo(defaultValue = "0")
    val suppressUntilDay: Long = 0L,

    // ---------------- WiFi 触发 ----------------
    val wifiEnabled: Boolean = false,
    /**
     * 目标 SSID 列表，用 \n 分隔。
     *
     * 为什么是**列表**而不是单个：一台路由器常常同时有 2.4G/5G 两个 SSID（实测用户家里就是
     * `plqrd` 与 `plqrd_5GHz`），只绑一个就会出现「人明明到家了却不触发」这种最难排查的故障。
     */
    val targetSsids: String = "",
    /**
     * 连上之后要确认多少秒仍然在线，才认为「到达」。
     *
     * 默认 15 秒的依据是 M0 第 5 项的实测：72 小时内 WiFi 只连断 2~3 次（家里与办公各一次），
     * 连断极少，所以不需要 30 秒那么保守。
     */
    val wifiDwellSeconds: Int = 15,

    // ---------------- 地点触发（自研围栏，不依赖高德）----------------
    /**
     * 进入 (latitude, longitude) 半径 [radiusMeters] 内、并停留 [placeDwellSeconds] 秒就触发。
     *
     * 为什么用系统 `LocationManager` 自己算距离，而不是第三方围栏 SDK：
     * 不需要 key、不需要第三方 SDK 的隐私合规声明，自用场景精度足够（半径 200~500 米）。
     * 代价是耗电略高 —— 所以定位只在「存在启用的地点提醒」时才注册。
     */
    val placeEnabled: Boolean = false,
    val placeName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Int = 300,
    val placeDwellSeconds: Int = 60,

    // ---------------- 触发逻辑（多个触发之间是「或」还是「且」）----------------
    /**
     * 见 [MatchMode]。默认「或」：任一触发成立就响。
     * 选「且」时各条件必须**同时成立** —— 典型用法「到点提醒我，但只有我在公司时才提醒」。
     */
    val matchMode: String = MatchMode.ANY,

    // ---------------- 响铃 ----------------
    /** 自定义铃声 URI；空 = 用系统默认闹钟铃声 */
    val ringtoneUri: String = "",
    /**
     * 渐强秒数：0 = 一上来就是最大音量；>0 = 从 15% 音量渐强到 100%。
     * 早上突然一声巨响很吓人，渐强更友好（实现见 RingActivity，用 MediaPlayer 缩放而不是改系统音量）。
     */
    val volumeRampSeconds: Int = 15,

    // ---------------- 冷却 ----------------
    /** 同一提醒多久内只触发一次（分钟），默认一天一次，避免家里 WiFi 反复重连就反复响 */
    val cooldownMinutes: Int = 24 * 60,
    val lastFiredAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val ssidList: List<String>
        get() = targetSsids.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    /** 给列表页用的一句话描述 */
    fun triggerSummary(): String {
        val parts = mutableListOf<String>()
        if (timeEnabled) {
            parts += timeLabel()
        }
        if (wifiEnabled) {
            val names = ssidList
            parts += when {
                names.isEmpty() -> "到 WiFi（未设置）"
                // 多了就折叠，否则一张卡片会被 SSID 撑成三四行
                names.size <= 2 -> "连上 " + names.joinToString(" / ")
                else -> "连上 ${names.first()} 等 ${names.size} 个网络"
            }
        }
        if (placeEnabled) {
            parts += "到 ${placeName.ifBlank { "已设定地点" }}（${radiusMeters}m 内）"
        }
        return if (parts.isEmpty()) {
            "（未设置触发条件）"
        } else {
            // 多个条件时，中间的连接词直接体现「或 / 且」，列表页一眼能看出逻辑
            parts.joinToString(if (matchMode == MatchMode.ALL) "   且   " else "   或   ")
        }
    }

    /** 是否配了至少一种触发 */
    fun hasAnyTrigger(): Boolean = timeEnabled || wifiEnabled || placeEnabled

    /**
     * 时间触发的一行标签（列表页与编辑页共用）。
     *
     * 「仅一次 + 指定日期」显示成 `09:00 · 11月15日`，而不是笼统的「仅一次」——
     * 列表里一眼就能看出它是"哪一天的"。日期用 `M月d日`（跨年时补年份）。
     */
    fun timeLabel(): String {
        val hm = "%02d:%02d".format(hour, minute)
        if (repeatMask != Repeat.ONCE || onceDate <= 0L) {
            return hm + " · " + Repeat.label(repeatMask)
        }
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = onceDate }
        val thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val pattern = if (cal.get(java.util.Calendar.YEAR) == thisYear) "M月d日" else "yyyy年M月d日"
        return hm + " · " + java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
            .format(java.util.Date(onceDate))
    }
}

object MatchMode {
    /** 任一条件成立就响（默认） */
    const val ANY = "ANY"

    /** 所有启用的条件同时成立才响 */
    const val ALL = "ALL"

    fun label(mode: String): String = if (mode == ALL) "同时满足（且）" else "任一满足（或）"
}

object Repeat {
    /** 没有星期位 = 只触发一次，触发后自动关闭 */
    const val ONCE = 0
    const val EVERY_DAY = 0b1111111
    const val WEEKDAYS = 0b0011111
    const val WEEKEND = 0b1100000

    val DAY_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")

    fun daysOfWeek(mask: Int): List<Int> = (0..6).filter { mask and (1 shl it) != 0 }

    fun label(mask: Int): String = when (mask) {
        ONCE -> "仅一次"
        EVERY_DAY -> "每天"
        WEEKDAYS -> "工作日"
        WEEKEND -> "周末"
        else -> daysOfWeek(mask).joinToString("、") { "周${DAY_NAMES[it]}" }
    }
}
