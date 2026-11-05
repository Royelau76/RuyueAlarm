# -*- coding: utf-8 -*-
"""Step 5b：详情页接线 + 列表卡片改跳详情。"""
import os

AK = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm"


def pd(rel):
    return os.path.join(AK, rel)


def rep(s, old, new, label):
    if old in s:
        print("  ✓", label)
        return s.replace(old, new)
    print("  ✗ 没匹配到:", label)
    return s


# ---------------- 0) 先修详情页自己的两个问题 ----------------
P = pd("ui/ReminderDetailScreen.kt")
s = open(P, encoding="utf-8").read()
s = rep(s, ".copy(fontSize = 30.sp2())", ".copy(fontSize = 30.sp)", "修 30.sp 笔误")
if "import androidx.compose.ui.unit.sp" not in s:
    s = s.replace("import androidx.compose.ui.unit.dp", "import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp")
    print("  ✓ 补 import sp")
# ⚠️ 去掉 return@Column（这正是 v0.19.3 那次崩溃的成因）
s = rep(s, """                )
                return@Column
            }
""", """                )
            } else {
""", "去掉 return@Column（改 if/else）")
s = rep(s, """                Spacer(Modifier.height(28.dp))
            }
        }
    }
}""", """                Spacer(Modifier.height(28.dp))
            }
            }
        }
    }
}""", "补上 else 的收尾括号")
open(P, "w", encoding="utf-8", newline="\n").write(s)

# ---------------- 1) 路由 ----------------
P = pd("MainActivity.kt")
s = open(P, encoding="utf-8").read()
s = rep(s, """    /** id == 0 表示新建 */
    data class Edit(val id: Long) : Route""", """    /** 详情页：点列表卡片先进这里，再从这页进编辑 */
    data class Detail(val id: Long) : Route

    /** id == 0 表示新建 */
    data class Edit(val id: Long) : Route""", "Route.Detail")
s = rep(s, """            Route.List -> ReminderListScreen(
                vm = reminderVm,
                onEdit = { id -> route = Route.Edit(id) },
                onOpenSettings = { route = Route.Settings },
            )""", """            Route.List -> ReminderListScreen(
                vm = reminderVm,
                onEdit = { id -> route = Route.Edit(id) },
                onDetail = { id -> route = Route.Detail(id) },
                onOpenSettings = { route = Route.Settings },
            )

            is Route.Detail -> ReminderDetailScreen(
                reminderId = current.id,
                vm = reminderVm,
                onBack = backToList,
                onEdit = { id -> route = Route.Edit(id) },
            )""", "路由分支")
open(P, "w", encoding="utf-8", newline="\n").write(s)

# ---------------- 2) 列表页：加 onDetail ------------
P = pd("ui/ReminderListScreen.kt")
s = open(P, encoding="utf-8").read()
s = rep(s, """    vm: ReminderViewModel,
    onEdit: (Long) -> Unit,
    onOpenSettings: () -> Unit,""", """    vm: ReminderViewModel,
    onEdit: (Long) -> Unit,
    onDetail: (Long) -> Unit,
    onOpenSettings: () -> Unit,""", "签名加 onDetail")
s = rep(s, "                                onClick = { onEdit(hero.id) },",
        "                                onClick = { onDetail(hero.id) },", "主卡 → 详情")
s = rep(s, "                            onClick = { onEdit(reminder.id) },",
        "                            onClick = { onDetail(reminder.id) },", "行 → 详情")
open(P, "w", encoding="utf-8", newline="\n").write(s)
print("完成")
