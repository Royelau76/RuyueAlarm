# -*- coding: utf-8 -*-
"""补响铃页的「今天不再」按钮（Step 5b 遗留）。"""
import os

P = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm\alarm\RingActivity.kt"
s = open(P, encoding="utf-8").read()


def rep(old, new, label):
    global s
    if old in s:
        s = s.replace(old, new)
        print("  ✓", label)
    else:
        print("  ✗ 没匹配到:", label)


# 1) 调用点：加 onSuppressToday
rep("""                        ReminderNotifier.cancel(this, reminderId)
                        stopEverything()
                        finish()
                    },
                )""",
    """                        ReminderNotifier.cancel(this, reminderId)
                        stopEverything()
                        finish()
                    },
                    onSuppressToday = {
                        // 「今天不再」：抑制到明天 0 点（数据闸门在 TriggerRules.suppressGateUnmet）
                        val id = reminderId
                        val t = title
                        lifecycleScope.launch {
                            runCatching {
                                ReminderDb.get(this@RingActivity).reminderDao()
                                    .setSuppressUntil(id, nextMidnight())
                            }
                            runCatching { ReminderScheduler.cancel(this@RingActivity, id) }
                            logAck("今天不再", t)
                            ReminderNotifier.cancel(this@RingActivity, id)
                            stopEverything()
                            finish()
                        }
                    },
                )""",
    "调用点")

# 2) RingScreen 签名
rep("""    acked: Boolean,
    onDismiss: () -> Unit,
    onSnooze: (Int) -> Unit,
) {""",
    """    acked: Boolean,
    onDismiss: () -> Unit,
    onSnooze: (Int) -> Unit,
    onSuppressToday: () -> Unit,
) {""",
    "RingScreen 签名")

# 3) UI：稍后那排下面加一行文字按钮
rep("""            Spacer(Modifier.height(10.dp))
            Text(
                text = "点「知道了」停止响铃；稍后提醒到点会再响一次",""",
    """            Spacer(Modifier.height(6.dp))
            Text(
                text = "今天不再提醒它",
                style = MaterialTheme.typography.labelLarge,
                color = c.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickableNoRipple(onSuppressToday)
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "点「知道了」停止响铃；稍后提醒到点会再响一次",""",
    "UI 按钮")

# 4) import：textAlign / nextMidnight
if "import androidx.compose.ui.text.style.TextAlign" not in s:
    s = s.replace("import androidx.compose.ui.unit.dp", "import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.unit.dp")
    print("  ✓ import TextAlign")
if "import com.ruyue.alarm.ui.nextMidnight" not in s:
    s = s.replace("import com.ruyue.alarm.ui.theme.LocalRuyueColors", "import com.ruyue.alarm.ui.nextMidnight\nimport com.ruyue.alarm.ui.theme.LocalRuyueColors")
    print("  ✓ import nextMidnight")

open(P, "w", encoding="utf-8", newline="\n").write(s)
print("完成")
