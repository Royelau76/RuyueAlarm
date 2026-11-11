# -*- coding: utf-8 -*-
"""日志页两个改进：
A 默认隐藏 HEARTBEAT（心跳占 68%，会把真事件冲掉）+ 一个开关切回来
B 文案改准（是"显示"不是"保留"），并把上限 500 → 2000
"""
import glob
import os
import re

AK = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm"

# ---------- B1：DAO 默认上限 500 → 2000 ----------
P = os.path.join(AK, "data/EventLogDao.kt")
s = open(P, encoding="utf-8").read()
if "limit: Int = 500" in s:
    s = s.replace("limit: Int = 500", "limit: Int = 2000")
    print("  ✓ EventLogDao 默认上限 2000")
open(P, "w", encoding="utf-8", newline="\n").write(s)

# ---------- B2：其它地方显式传 500 的也一起改 ----------
for p in glob.glob(os.path.join(AK, "**/*.kt"), recursive=True):
    s = open(p, encoding="utf-8").read()
    if "recent(500)" in s or "recent(limit = 500)" in s:
        s = s.replace("recent(limit = 500)", "recent(limit = 2000)").replace("recent(500)", "recent(2000)")
        open(p, "w", encoding="utf-8", newline="\n").write(s)
        print("  ✓ 改了", os.path.basename(p), "里的显式 500")

# ---------- A + B1：日志页 ----------
P = os.path.join(AK, "ui/LogsScreen.kt")
s = open(P, encoding="utf-8").read()

old_head = '''fun LogsScreen(vm: M0ViewModel, onBack: () -> Unit) {
    val logs by vm.logs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Spacer(Modifier.width(4.dp))
            Text("全部日志", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { vm.exportLogs() },
                enabled = logs.isNotEmpty(),
            ) { Text("导出") }
            TextButton(onClick = { vm.clearLogs() }) { Text("清空") }
        }
        Text(
            text = "共 ${logs.size} 条（最多保留 500 条）",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Text("还没有日志。走一遍 ①②③ 就会留下记录。")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(logs, key = { it.id }) { log ->
                    LogRow(log)
                }
            }
        }
    }
}'''

new_head = '''fun LogsScreen(vm: M0ViewModel, onBack: () -> Unit) {
    val logs by vm.logs.collectAsStateWithLifecycle()

    // 默认隐藏心跳：2026-11-11 实测 HEARTBEAT 占日志 841/1236（68%），
    // 会把真正要排查的触发/响铃事件冲掉 —— 500 条里只剩一百多条有用信息。
    // 想看心跳时点右上角切回来。
    var importantOnly by remember { mutableStateOf(true) }
    val shown = if (importantOnly) logs.filter { it.type != EventType.HEARTBEAT } else logs
    val hidden = logs.size - shown.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Spacer(Modifier.width(4.dp))
            Text("全部日志", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { importantOnly = !importantOnly }) {
                Text(if (importantOnly) "显示心跳" else "只看重要")
            }
            TextButton(
                onClick = { vm.exportLogs() },
                enabled = logs.isNotEmpty(),
            ) { Text("导出") }
            TextButton(onClick = { vm.clearLogs() }) { Text("清空") }
        }
        Text(
            // 是「显示」不是「保留」—— 更早的日志一直在库里，只是这一页有上限
            text = if (importantOnly) {
                "显示最近 ${shown.size} 条重要事件" +
                    if (hidden > 0) "（已隐藏 $hidden 条心跳）" else ""
            } else {
                "显示最近 ${shown.size} 条（含心跳，更早的仍在库里，导出时会一并带上）"
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        if (shown.isEmpty()) {
            Text("还没有日志。走一遍 ①②③ 就会留下记录。")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(shown, key = { it.id }) { log ->
                    LogRow(log)
                }
            }
        }
    }
}'''

if old_head in s:
    s = s.replace(old_head, new_head)
    print("  ✓ 日志页：默认隐藏心跳 + 文案改准")
else:
    print("  ✗ 日志页锚点没找到")

for imp in ["import androidx.compose.runtime.mutableStateOf",
            "import androidx.compose.runtime.remember",
            "import androidx.compose.runtime.setValue"]:
    if imp not in s:
        s = s.replace("import androidx.compose.runtime.getValue",
                      "import androidx.compose.runtime.getValue\n" + imp, 1)
open(P, "w", encoding="utf-8", newline="\n").write(s)
print("  ✓ import 补齐")

# ---------- 导出：确认导出的是不是全部 ----------
P = os.path.join(AK, "util/LogExporter.kt")
if os.path.exists(P):
    s = open(P, encoding="utf-8").read()
    m = re.search(r"recent\([^)]*\)|getAll|SELECT \* FROM event_logs[^\"]*", s)
    print("  · LogExporter 查询方式:", m.group(0) if m else "（未见 recent/getAll，需人工确认）")
print("完成")
