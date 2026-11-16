# -*- coding: utf-8 -*-
"""1a 响铃页 WiFi 标签字号缩小；2 纯时间提醒取消冷却。"""
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
            print("      锚点片段:", old[:60].replace("\n", "⏎"))
    open(p, "w", encoding="utf-8", newline="\n").write(s)


# ---------- 1a：响铃页触发源文字（WiFi/地点）字号 34sp → 24sp，单行省略 ------------------
patch("alarm/RingActivity.kt", [(
    """                    style = if (hour != null) {
                        MaterialTheme.typography.displayLarge
                    } else {
                        // 中文用 74sp 会溢出，降两档
                        MaterialTheme.typography.headlineLarge
                    },
                    color = c.text,
                )""",
    """                    style = if (hour != null) {
                        MaterialTheme.typography.displayLarge
                    } else {
                        // 触发源（「连上 TP-LINK_1602_5G」/「到达 公司」）用 24sp：
                        // 34sp 时 SSID 一长就换行、顶到小狗，2026-11-16 用户反馈"太大太长"
                        MaterialTheme.typography.titleLarge
                    },
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )""",
    "响铃页触发源字号 34→24 + 单行省略",
)])

# ---------- 2a：编辑页 —— 纯时间提醒不显示冷却卡片 ------------------
patch("ui/ReminderEditScreen.kt", [(
    """        // ---------------- 冷却（三种触发共用）----------------
        EditCard(
            title = "冷却",""",
    """        // ---------------- 冷却（只有 WiFi / 地点 才需要）----------------
        // 纯时间提醒每天到点只响一次，不存在"同一天反复触发"的问题，
        // 冷却对它没有意义（2026-11-16 用户要求去掉）。
        if (draft.wifiEnabled || draft.placeEnabled) {
        EditCard(
            title = "冷却",""",
    "冷却卡片加显示条件（上半）",
), (
    """            }
        }

        // ---------------- 响铃 ----------------""",
    """            }
        }
        }

        // ---------------- 响铃 ----------------""",
    "冷却卡片加显示条件（下半）",
)])

# ---------- 2b：引擎 —— 纯时间提醒跳过冷却 ------------------
patch("trigger/TriggerEngine.kt", [(
    """            if (!TriggerRules.cooldownPassed(
                    reminder.lastFiredAt,
                    reminder.cooldownMinutes,
                    System.currentTimeMillis(),
                )
            ) {""",
    """            // 纯时间提醒不需要冷却（每天到点只响一次）；只有带 WiFi/地点 条件时才有意义
            if ((reminder.wifiEnabled || reminder.placeEnabled) &&
                !TriggerRules.cooldownPassed(
                    reminder.lastFiredAt,
                    reminder.cooldownMinutes,
                    System.currentTimeMillis(),
                )
            ) {""",
    "时间路径冷却只对多条件生效",
)])
print("完成")
