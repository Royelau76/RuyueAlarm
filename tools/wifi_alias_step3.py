# -*- coding: utf-8 -*-
"""Step 3/3：编辑页「用过的网络」加备注按钮 + 编辑弹窗。"""
import os

AK = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm"
P = os.path.join(AK, "ui/ReminderEditScreen.kt")
s = open(P, encoding="utf-8").read()


def rep(old, new, label):
    global s
    if old in s:
        s = s.replace(old, new, 1)
        print("  ✓", label)
        return True
    print("  ✗ 没匹配到:", label)
    return False


# 1) 状态 + 弹窗（放在已有状态块之后；Dialog 在任何位置都能渲染）
rep(
    """    // 删除前二次确认：底部「删除」现在是常驻按钮，误触就再也找不回这条提醒了
    var confirmDelete by remember { mutableStateOf(false) }""",
    """    // 删除前二次确认：底部「删除」现在是常驻按钮，误触就再也找不回这条提醒了
    var confirmDelete by remember { mutableStateOf(false) }

    // ---- WiFi 备注：给「用过的网络」起个短名字，响铃页会优先显示它 ----
    var aliasEditFor by remember { mutableStateOf<String?>(null) }
    var aliasDraft by remember { mutableStateOf("") }
    val aliasScope = rememberCoroutineScope()
    val aliasTarget = aliasEditFor
    if (aliasTarget != null) {
        AlertDialog(
            onDismissRequest = { aliasEditFor = null },
            title = { Text("给这个网络起个备注") },
            text = {
                Column {
                    Text(aliasTarget, fontSize = 12.sp, color = ruyue.textTertiary)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = aliasDraft,
                        onValueChange = { aliasDraft = it },
                        singleLine = true,
                        placeholder = { Text("例如：家里网 / 公司") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = aliasTarget
                    val text = aliasDraft.trim()
                    aliasEditFor = null
                    aliasScope.launch {
                        runCatching {
                            ReminderDb.get(context).ssidHistoryDao().updateAlias(target, text)
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { aliasEditFor = null }) { Text("取消") }
            },
        )
    }""",
    "状态 + 备注弹窗",
)

# 2) 用过的网络列表：显示备注 + 「备注」按钮
rep(
    """                // 用过的 WiFi：自动记录，点一下就能加入，不用每次手输
                val unused = history.map { it.ssid }.filter { it !in draft.ssidList }
                if (unused.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("用过的网络（点「加入」即可）：", fontWeight = FontWeight.Medium)
                    Hint("哨兵每次连上 WiFi 都会自动记下来，换机/重装前一直有效。")
                    unused.take(8).forEach { ssid ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ssid, Modifier.weight(1f), fontSize = 14.sp)
                            TextButton(onClick = { vm.addSsid(ssid) }) { Text("加入") }
                            TextButton(onClick = { vm.forgetSsid(ssid) }) { Text("移除记录") }
                        }
                    }
                }""",
    """                // 用过的 WiFi：自动记录；「备注」给它起个短名字（响铃页优先显示备注）
                val unused = history.filter { it.ssid !in draft.ssidList }
                if (unused.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("用过的网络：", fontWeight = FontWeight.Medium)
                    unused.take(8).forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.ssid, fontSize = 14.sp)
                                if (item.alias.isNotBlank()) {
                                    Text(
                                        "备注：${item.alias}",
                                        fontSize = 12.sp,
                                        color = ruyue.textTertiary,
                                    )
                                }
                            }
                            TextButton(onClick = { vm.addSsid(item.ssid) }) { Text("加入") }
                            TextButton(onClick = {
                                aliasDraft = item.alias
                                aliasEditFor = item.ssid
                            }) { Text("备注") }
                        }
                    }
                }""",
    "列表显示备注 + 备注按钮",
)

# 3) 补 import
need = [
    "import androidx.compose.material3.AlertDialog",
    "import androidx.compose.material3.OutlinedTextField",
    "import androidx.compose.runtime.rememberCoroutineScope",
    "import kotlinx.coroutines.launch",
    "import com.ruyue.alarm.data.ReminderDb",
]
lines = s.split("\n")
last = max(i for i, l in enumerate(lines) if l.startswith("import "))
missing = [k for k in need if k not in s]
for imp in reversed(missing):
    lines.insert(last + 1, imp)
if missing:
    s = "\n".join(lines)
    print("  ✓ 补 import:", len(missing), "个")

open(P, "w", encoding="utf-8", newline="\n").write(s)
print("完成")
