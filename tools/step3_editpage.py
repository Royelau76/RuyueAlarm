# -*- coding: utf-8 -*-
"""Step 3a：把编辑页从「7 个折叠区」改成「开关驱动的渐进披露」（设计稿 new.png）。

只改**容器与顺序**，不动任何参数控件与校验逻辑。
"""
import re

P = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm\ui\ReminderEditScreen.kt"
s = open(P, encoding="utf-8").read()
orig = s


def show(step, ok, extra=""):
    print(("  ✓ " if ok else "  ✗ ") + step + (("  " + extra) if extra else ""))


# ---------- 1) import + 触发源色 ----------
if "com.ruyue.alarm.ui.edit.EditCard" not in s:
    s = s.replace(
        "import com.ruyue.alarm.ui.Hint",
        "import com.ruyue.alarm.ui.Hint\n"
        "import com.ruyue.alarm.ui.edit.EditCard\n"
        "import com.ruyue.alarm.ui.edit.EditSectionLabel\n"
        "import com.ruyue.alarm.ui.edit.HeroInputCard",
    )
if "LocalRuyueColors.current" not in s:
    s = s.replace(
        "    val context = LocalContext.current",
        "    val context = LocalContext.current\n    val ruyue = LocalRuyueColors.current",
    )
    s = s.replace(
        "import com.ruyue.alarm.ui.Hint",
        "import com.ruyue.alarm.ui.theme.LocalRuyueColors\nimport com.ruyue.alarm.ui.Hint",
    )
show("import + ruyue 色板", "LocalRuyueColors.current" in s)

# ---------- 2) CollapsibleSection -> EditCard ----------
n = s.count("CollapsibleSection(")
s = s.replace("CollapsibleSection(", "EditCard(")
show("折叠区 → EditCard", n > 0, f"替换 {n} 处")

# ---------- 3) 三张条件卡加 accent / switch / bigSummary ----------
def add_args(anchor, extra):
    global s
    try:
        i = s.index(anchor)
        # 锚点必须是"8 空格缩进的 ) {"（调用收尾）；之前写死 ") {" 会撞进 summary = if (...) { 里
        j = s.index("\n        ) {", i)
    except ValueError:
        return False
    s = s[:j] + extra + s[j:]
    return True


ok1 = add_args(
    'title = "① 到点提醒（时间）"',
    "\n            accent = ruyue.time,"
    "\n            switchOn = draft.timeEnabled,"
    "\n            onSwitch = { v -> vm.updateDraft { it.copy(timeEnabled = v) } },"
    "\n            bigSummary = true,",
)
ok2 = add_args(
    'title = "② 到地点提醒（连上指定 WiFi）"',
    "\n            accent = ruyue.wifi,"
    "\n            switchOn = draft.wifiEnabled,"
    "\n            onSwitch = { v -> vm.updateDraft { it.copy(wifiEnabled = v) } },",
)
ok3 = add_args(
    'title = "③ 到地点提醒（进入范围）"',
    "\n            accent = ruyue.place,"
    "\n            switchOn = draft.placeEnabled,"
    "\n            onSwitch = { v -> vm.updateDraft { it.copy(placeEnabled = v) } },",
)
show("时间卡加开关", ok1)
show("WiFi 卡加开关", ok2)
show("地点卡加开关", ok3)

# ---------- 4) 卡内重复的「启用」行删掉（开关已经在卡头） ----------
before = s
s = re.sub(
    r"\n\s*Row\(verticalAlignment = Alignment\.CenterVertically\) \{\s*\n"
    r"\s*Text\(\"启用\", Modifier\.weight\(1f\)\)\s*\n"
    r"\s*Switch\(\s*\n"
    r"\s*checked = draft\.\w+,\s*\n"
    r"\s*onCheckedChange = \{ v -> vm\.updateDraft \{ it\.copy\(\w+ = v\) \} \},\s*\n"
    r"\s*\)\s*\n"
    r"\s*\}",
    "",
    s,
)
show("删掉卡内重复的「启用」行", len(before) != len(s), f"减少 {len(before) - len(s)} 字符")

# ---------- 5) 标题改成设计稿的说法 ----------
for old, new in [
    ('"① 到点提醒（时间）"', '"时间"'),
    ('"② 到地点提醒（连上指定 WiFi）"', '"连上 Wi-Fi"'),
    ('"③ 到地点提醒（进入范围）"', '"到某个地点"'),
    ('"触发逻辑（多个条件之间）"', '"条件之间"'),
    ('"冷却（避免反复响）"', '"冷却"'),
]:
    s = s.replace(old, new)
show("标题改成设计稿说法", '"时间"' in s)

# ---------- 6) 基本信息 → 顶部输入卡 ----------
old_basic = '''        // ---------------- 基本信息 ----------------
        EditCard(
            title = "基本信息",
            summary = draft.title.ifBlank { "还没填标题" },
            initiallyExpanded = true,
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { v -> vm.updateDraft { it.copy(title = v) } },
                label = { Text("提醒什么（必填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.note,
                onValueChange = { v -> vm.updateDraft { it.copy(note = v) } },
                label = { Text("备注（可选，响铃时会显示）") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
'''
new_basic = '''        // ---------------- 顶部输入卡（设计稿第一屏最显眼那块） ----------------
        HeroInputCard(
            title = draft.title,
            note = draft.note,
            onTitleChange = { v -> vm.updateDraft { it.copy(title = v) } },
            onNoteChange = { v -> vm.updateDraft { it.copy(note = v) } },
        )

        // ---------------- 什么时候 · 在哪儿 · 什么网 ----------------
        EditSectionLabel("什么时候 · 在哪儿 · 什么网")
'''
if old_basic in s:
    s = s.replace(old_basic, new_basic)
    show("基本信息 → HeroInputCard + 分区小标题", True)
else:
    show("基本信息 → HeroInputCard + 分区小标题", False, "没匹配到原文")

# ---------- 7) 顶栏：取消 / 标题 / 保存 ----------
old_top = '''        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (id > 0L) "编辑提醒" else "新建提醒",
                style = MaterialTheme.typography.titleLarge,
            )
        }
'''
new_top = '''        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("取消", style = MaterialTheme.typography.labelLarge, color = ruyue.textSecondary)
            }
            Spacer(Modifier.width(2.dp))
            Text(
                text = if (id > 0L) "编辑约定" else "新建约定",
                style = MaterialTheme.typography.titleLarge,
                color = ruyue.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "保存",
                style = MaterialTheme.typography.labelLarge,
                color = ruyue.time,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(ruyue.time.copy(alpha = 0.14f))
                    .clickable {
                        vm.save {
                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
'''
if old_top in s:
    s = s.replace(old_top, new_top)
    show("顶栏：取消 / 新建约定 / 保存", True)
else:
    show("顶栏：取消 / 新建约定 / 保存", False, "没匹配到原文")

# ---------- 8) 底栏只留「删除」 ----------
old_bottom = '''        Surface(
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (id > 0L) {
                    OutlinedButton(onClick = { confirmDelete = true }) { Text("删除") }
                }
                Button(
                    onClick = {
                        vm.save {
                            // 保存后立刻退回列表，VM 里那条「已保存」消息来不及看见 —— 用 Toast 补上反馈
                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
            }
        }
'''
new_bottom = '''        // 保存已挪到顶栏（设计稿），底栏只留删除 —— 危险操作离手指远一点
        if (id > 0L) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "删除这条约定",
                    style = MaterialTheme.typography.labelLarge,
                    color = ruyue.danger,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(ruyue.danger.copy(alpha = 0.10f))
                        .clickable { confirmDelete = true }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
'''
if old_bottom in s:
    s = s.replace(old_bottom, new_bottom)
    show("底栏：只留删除", True)
else:
    show("底栏：只留删除", False, "没匹配到原文")

# ---------- 9) 补 import：clip / background / RoundedCornerShape ----------
for imp in [
    "androidx.compose.foundation.background",
    "androidx.compose.foundation.clickable",
    "androidx.compose.foundation.shape.RoundedCornerShape",
    "androidx.compose.ui.draw.clip",
]:
    if "import " + imp not in s:
        s = s.replace("import androidx.compose.material3.", "import " + imp + "\nimport androidx.compose.material3.", 1)

open(P, "w", encoding="utf-8", newline="\n").write(s)
print(f"\n写入完成：{len(orig)} → {len(s)} 字符")
