# -*- coding: utf-8 -*-
"""响铃页大时间：显示**实际触发时刻**，而不是约定里定的那个时间。

为什么错：页面一直画 `reminder.hour:minute`（约定里写的 09:00），
但「同时满足」真正响的时刻可能是 09:07（等到地点也满足），
稍后提醒更是完全不同的时刻（22:10）。2026-11-23 用户指出。

改法：从事件日志里取这条提醒**最近一次** ALARM_FIRED 的时间戳 ——
它才是"这次到底几点响的"，稍后提醒也走这条路径（detail 是「稍后提醒（N 分钟后）」）。
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
    open(p, "w", encoding="utf-8", newline="\n").write(s)


patch("alarm/RingActivity.kt", [(
    """                // 这条提醒绑定网络的备注（「家里网」），响铃页优先显示它而不是一长串 SSID
                var ssidAlias by remember { mutableStateOf<String?>(null) }""",
    """                // 这条提醒绑定网络的备注（「家里网」），响铃页优先显示它而不是一长串 SSID
                var ssidAlias by remember { mutableStateOf<String?>(null) }
                // **实际触发时刻**：同时满足/稍后提醒都会偏离约定时间，页面要显示真实的那一个
                var firedAtMs by remember { mutableStateOf<Long?>(null) }""",
    "加 firedAtMs 状态",
), (
    """                    // 备注要在协程里查（DAO 是 suspend），查到就存进 state 给下面的文案用""",
    """                    // 实际触发时刻：取这条提醒最近一次 ALARM_FIRED 的时间戳
                    // （稍后提醒的 detail 是「稍后提醒（N 分钟后）：标题」，同样命中这里）
                    firedAtMs = runCatching {
                        Db.get(this@RingActivity).eventLogDao()
                            .listByTypesSince(listOf(EventType.ALARM_FIRED), now - 12 * 3600_000L)
                    }.getOrDefault(emptyList())
                        .lastOrNull { it.detail?.contains(title) == true }
                        ?.timestamp
                        ?: now

                    // 备注要在协程里查（DAO 是 suspend），查到就存进 state 给下面的文案用""",
    "查询实际触发时刻",
), (
    """                        hour = reminder?.takeIf { it.timeEnabled }?.hour,
                    minute = reminder?.takeIf { it.timeEnabled }?.minute,""",
    """                        hour = reminder?.takeIf { it.timeEnabled }?.hour,
                    minute = reminder?.takeIf { it.timeEnabled }?.minute,
                    firedAt = firedAtMs,""",
    "传给 RingScreen firedAt",
)])

# RingScreen 签名
p = os.path.join(AK, "alarm/RingActivity.kt")
s = open(p, encoding="utf-8").read()
old_sig = """    hour: Int?,
    minute: Int?,
    /** 非时间提醒时，大标题位置改显示触发源（如「到达 公司」） */
    sourceLabel: String?,"""
new_sig = """    hour: Int?,
    minute: Int?,
    /** 实际触发时刻（毫秒）—— 同时满足/稍后提醒会偏离约定时间，优先显示它 */
    firedAt: Long?,
    /** 非时间提醒时，大标题位置改显示触发源（如「到达 公司」） */
    sourceLabel: String?,"""
if old_sig in s:
    s = s.replace(old_sig, new_sig, 1)
    print("  ✓ RingScreen 签名加 firedAt")
else:
    print("  ✗ RingScreen 签名没匹配到")

# 大标题渲染
old_render = """                Text(
                    text = if (hour != null && minute != null) {
                        "%02d:%02d".format(hour, minute)
                    } else {
                        sourceLabel.orEmpty()
                    },
                    style = if (hour != null) {"""
new_render = """                Text(
                    text = when {
                        // 实际触发时刻优先：同时满足可能比约定晚、稍后提醒则是另一个时刻
                        firedAt != null ->
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(firedAt))
                        hour != null && minute != null -> "%02d:%02d".format(hour, minute)
                        else -> sourceLabel.orEmpty()
                    },
                    style = if (hour != null) {"""
if old_render in s:
    s = s.replace(old_render, new_render, 1)
    print("  ✓ 大标题改用 firedAt")
else:
    print("  ✗ 大标题渲染没匹配到")

open(p, "w", encoding="utf-8", newline="\n").write(s)
print("完成")
