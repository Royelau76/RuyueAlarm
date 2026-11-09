# -*- coding: utf-8 -*-
"""修：非时间提醒的响铃页永远显示 09:00。

根因：Reminder.hour/minute 默认 9/0，timeEnabled=false 时没人改，
      而 RingActivity 无条件把它们传给 RingScreen。
修法（方案 B 的变体）：
  · 非时间提醒传 null → 大时间位置不再画假时间
  · 该位置改显示**触发源**（「到达 公司」/「连上 TP-LINK_1602_5G」），字号降到 headlineLarge
    （74sp 的中文会溢出，时间数字才用 74sp）
"""
import os

RING = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm\alarm\RingActivity.kt"
s = open(RING, encoding="utf-8").read()


def rep(old, new, label):
    global s
    if old in s:
        s = s.replace(old, new)
        print("  ✓", label)
    else:
        print("  ✗ 没匹配到:", label)


# 1) 传值时判断 timeEnabled
rep(
    """                    hour = reminder?.hour,
                    minute = reminder?.minute,""",
    """                    // 非时间提醒没有「时刻」——hour/minute 只是实体默认值 9:00，
                    // 无条件传过去会让响铃页永远顶着假的 09:00（2026-11-08 用户报）
                    hour = reminder?.takeIf { it.timeEnabled }?.hour,
                    minute = reminder?.takeIf { it.timeEnabled }?.minute,
                    sourceLabel = when {
                        reminder == null -> null
                        reminder!!.placeEnabled -> "到达 " + reminder!!.placeName.ifBlank { "地点" }
                        reminder!!.wifiEnabled ->
                            "连上 " + (reminder!!.ssidList.firstOrNull() ?: "WiFi")
                        else -> null
                    },""",
    "传 null + 触发源文案",
)

# 2) RingScreen 签名
rep(
    """    hour: Int?,
    minute: Int?,""",
    """    hour: Int?,
    minute: Int?,
    /** 非时间提醒时，大标题位置改显示触发源（如「到达 公司」） */
    sourceLabel: String?,""",
    "RingScreen 签名",
)

# 3) 大标题：有时刻画时刻（74sp），没时刻画触发源（34sp）
rep(
    """                Text(
                    text = if (hour != null && minute != null) "%02d:%02d".format(hour, minute) else "",
                    style = MaterialTheme.typography.displayLarge,
                    color = c.text,
                )""",
    """                Text(
                    text = if (hour != null && minute != null) {
                        "%02d:%02d".format(hour, minute)
                    } else {
                        sourceLabel.orEmpty()
                    },
                    style = if (hour != null) {
                        MaterialTheme.typography.displayLarge
                    } else {
                        // 中文用 74sp 会溢出，降两档
                        MaterialTheme.typography.headlineLarge
                    },
                    color = c.text,
                )""",
    "大标题分流",
)

open(RING, "w", encoding="utf-8", newline="\n").write(s)
print("完成")
