# -*- coding: utf-8 -*-
"""修两个问题：
① 两条提醒 10 秒内先后触发时，后一条的响铃页顶掉前一条（前者静默丢失）
   → 已有响铃在进行中时，本条 **只发通知**：不抢全屏、不换声音。
     用户处理完第一条，点状态栏里那条通知就能进第二条（信息不丢）。
② v0.27.1 的「同时满足·全部通过」证据日志只加在时间路径 → 补到 fire()（地点/WiFi 触发）
"""
import os

AK = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm"


def patch(rel, pairs):
    p = os.path.join(AK, rel)
    s = open(p, encoding="utf-8").read()
    for old, new, label in pairs:
        if old in s:
            s = s.replace(old, new)
            print("  ✓", label)
        else:
            print("  ✗ 没匹配到:", label)
    open(p, "w", encoding="utf-8", newline="\n").write(s)


# ---------------- ① 通知：不抢占 ----------------
patch("alarm/ReminderNotifier.kt", [(
    """        val full = PendingIntent.getActivity(""",
    """        // 已经有另一条在响 → **不要抢占全屏、也不要换掉声音**，只发一条通知。
        // 2026-11-08 11:41 实测：两条提醒隔 10 秒触发，后一条拉起响铃页把前一条顶掉，
        // 前一条连 RING_ACK 都没有（等于静默丢失）。用户处理完第一条，
        // 点状态栏里那条通知就能进第二条 —— 信息不丢，也不会互相打断。
        val busy = RingPlayer.isPlaying()
        if (busy) {
            EventWriter.write(
                context,
                EventType.NOTE,
                detail = "已有响铃在进行中，提醒「$title」只发通知、不抢占全屏",
            )
        }

        val full = PendingIntent.getActivity(""",
    "插入 busy 判定",
), (
    """            .setFullScreenIntent(full, true)""",
    """            // busy 时不给全屏意图权限：系统就不会为它弹全屏，只出通知
            .setFullScreenIntent(full, !busy)""",
    "全屏意图按 busy 决定",
), (
    """            RingPlayer.start(context, reminderId, ringtoneUri, rampSeconds)""",
    """            // 已经有声音在响就不要再 start（会换掉正在响的那条的声音）
            if (!busy) RingPlayer.start(context, reminderId, ringtoneUri, rampSeconds)""",
    "声音不抢占",
)])

# ---------------- ② fire() 也记录「验证了什么」 ----------------
patch("trigger/TriggerEngine.kt", [(
    """                Ledger.settleSkipped(
                    context,
                    reminder.id,
                    reason = "已满足 $source，但缺少 ${unmet.joinToString("；")}",
                )
                return
            }
        }""",
    """                Ledger.settleSkipped(
                    context,
                    reminder.id,
                    reason = "已满足 $source，但缺少 ${unmet.joinToString("；")}",
                )
                return
            }
            // 闸门通过 → 与时间路径同样的证据日志（v0.27.1 只补了时间路径，落下了这里）
            log.log(
                EventType.NOTE,
                detail = "「${reminder.title}」同时满足·全部通过（由 $source 触发；" + buildString {
                    if (reminder.timeEnabled) {
                        append("时间 %02d:%02d 在窗口内".format(reminder.hour, reminder.minute))
                    }
                    if (reminder.wifiEnabled) {
                        if (isNotEmpty()) append("；")
                        val cur = runCatching { WifiReader.snapshot(context).ssid }.getOrNull()
                        append("WiFi 当前=${cur ?: "未连接"} 目标=${reminder.ssidList.joinToString("/")}")
                    }
                    if (reminder.placeEnabled) {
                        if (isNotEmpty()) append("；")
                        append("地点 需在「${reminder.placeName}」${reminder.radiusMeters}m 内")
                    }
                } + "）",
            )
        }""",
    "fire() 加证据日志",
)])
print("完成")
