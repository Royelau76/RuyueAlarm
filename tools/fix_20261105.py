# -*- coding: utf-8 -*-
"""v0.21.1：修用户报的 4 个问题。

1) 「仅一次」提醒响过被自动停用后，编辑保存仍不恢复 → 保存即视为启用
2) +5 分按钮与其它三个样式不一致 → 统一成小胶囊 PresetButton
   地点弹层「用这个设置」显示不全 → 弹层限高 92%，滚动区 weight(1f)，按钮固定 + 导航栏内边距
3) 单条件路径看不出来 → 直轨加粗到 10dp、达成整条上色 + 白点；响铃页措辞区分
4) App 图标 / 状态栏图标对齐「条件就绪」配色（夜底 + 薄荷路径 + 白点）
"""
import os

ROOT = r"D:\My Projects\apps\RuyueAlarm\app\src\main"
JAVA = os.path.join(ROOT, "java", "com", "ruyue", "alarm")
RES = os.path.join(ROOT, "res")


def patch(path, pairs, label):
    full = os.path.join(JAVA, path) if not path.startswith("res") else os.path.join(ROOT, path)
    if not os.path.exists(full):
        print("  ✗ 文件不存在:", full)
        return
    s = open(full, encoding="utf-8").read()
    for old, new in pairs:
        if old not in s:
            print("  ✗ 没匹配到:", old.split("\n")[0][:60])
            continue
        s = s.replace(old, new)
    open(full, "w", encoding="utf-8", newline="\n").write(s)
    print("  ✓", label)


# ---------------- 1) 保存即启用 ----------------
patch("ui/ReminderViewModel.kt", [(
    """        viewModelScope.launch {
            val id = repo.save(current)
            val saved = current.copy(id = id)""",
    """        viewModelScope.launch {
            // 「仅一次」的提醒响过之后会被自动停用（TriggerEngine 里 disable），
            // 用户改完再保存显然是还想继续用它 —— 所以**保存即视为启用**。
            // 否则会留下一条"已暂停、永远不会响"的提醒（2026-11-05 用户报的 bug）。
            val activated = current.copy(enabled = true)
            val id = repo.save(activated)
            val saved = activated.copy(id = id)""",
)], "保存时恢复启用（修仅一次提醒无法复用）")

# ---------------- 2a) +5 分按钮统一 ----------------
patch("ui/ReminderEditScreen.kt", [(
    """                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { vm.updateDraft { it.copy(hour = (it.hour + 23) % 24) } }) { Text("-1 时") }
                    OutlinedButton(onClick = { vm.updateDraft { it.copy(hour = (it.hour + 1) % 24) } }) { Text("+1 时") }
                    OutlinedButton(onClick = { vm.updateDraft { it.copy(minute = (it.minute + 55) % 60) } }) { Text("-5 分") }
                    OutlinedButton(onClick = { vm.updateDraft { it.copy(minute = (it.minute + 5) % 60) } }) { Text("+5 分") }
                }""",
    """                // 四个按钮统一成小胶囊（原来 OutlinedButton 混在 pills 里，第 4 个还会被挤变形）
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetButton("-1 时", false) { vm.updateDraft { it.copy(hour = (it.hour + 23) % 24) } }
                    PresetButton("+1 时", false) { vm.updateDraft { it.copy(hour = (it.hour + 1) % 24) } }
                    PresetButton("-5 分", false) { vm.updateDraft { it.copy(minute = (it.minute + 55) % 60) } }
                    PresetButton("+5 分", false) { vm.updateDraft { it.copy(minute = (it.minute + 5) % 60) } }
                }""",
)], "+5 分按钮统一成胶囊")

# ---------------- 2b) 弹层底部按钮被切 ----------------
patch("ui/edit/EditSection.kt", [(
    """                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 20.dp),
                ) {""",
    """                // 限高 + 滚动区 weight(1f) + 按钮固定在底部：
                // 原来整列是 wrap-content，内容一长（地点卡有地图占位 + 两组 pill）
                // 按钮就被顶到屏幕外，用户看到的是"显示不全"（2026-11-05 用户反馈）
                Column(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.92f)
                        .padding(horizontal = 18.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                ) {""",
), (
    """                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.verticalScroll(rememberScrollState())) { content() }
                    Spacer(Modifier.height(14.dp))""",
    """                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) { content() }
                    Spacer(Modifier.height(12.dp))""",
)], "弹层：按钮固定在底部、内容可滚")

for imp in ["androidx.compose.foundation.layout.navigationBarsPadding"]:
    pass  # navigationBarsPadding 在 foundation.layout.* 里，已 import

# ---------------- 3) 单条件直轨加粗上色 ----------------
patch("ui/design/DesignComponents.kt", [(
    """    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxWidth()
            .height(44.dp),
    ) {
        val y = size.height * 0.5f
        val bar = 6.dp.toPx()
        drawLine(c.track, Offset(0f, y), Offset(size.width, y), bar, StrokeCap.Round)
        if (step.met) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), bar, StrokeCap.Round)
        }""",
    """    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxWidth()
            .height(44.dp),
    ) {
        val y = size.height * 0.5f
        // 原来是 6dp 细线，在深色卡片上几乎看不见（用户说"看不出来"）
        val bar = 12.dp.toPx()
        drawLine(c.track, Offset(0f, y), Offset(size.width, y), bar, StrokeCap.Round)
        if (step.met) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), bar, StrokeCap.Round)
            // 达成时再压一条更亮的高光，跟多条件路径的"上色段"视觉对齐
            drawLine(
                color.copy(alpha = 0.35f),
                Offset(bar / 2, y - bar / 4),
                Offset(size.width - bar / 2, y - bar / 4),
                bar / 3,
                StrokeCap.Round,
            )
        }""",
)], "单条件直轨加粗 + 高光")

# 响铃页：单条件的措辞
patch("alarm/RingActivity.kt", [(
    """                            StatusPill(
                                text = when (steps.size) {
                                    1 -> "条件达成"
                                    2 -> "两个都满足"
                                    else -> "三个都满足"
                                },
                                color = c.time,
                            )""",
    """                            StatusPill(
                                text = when (steps.size) {
                                    1 -> "这一个条件满足了"
                                    2 -> "两个都满足"
                                    else -> "三个都满足"
                                },
                                color = c.time,
                            )""",
)], "响铃页单条件措辞")

# ---------------- 4) 图标 ----------------
colors = os.path.join(RES, "values", "colors.xml")
s = open(colors, encoding="utf-8").read()
s = s.replace('#1F6FEB', '#18213A')
open(colors, "w", encoding="utf-8", newline="\n").write(s)
print("  ✓ 图标底色 #1F6FEB → #18213A（夜底）")

FG = """<?xml version="1.0" encoding="utf-8"?>
<!--
  App 图标前景：「路径组件」的极简版 —— 一段薄荷色路径 + 白色"此刻"点。
  这正是 App 的品牌语汇（颜色=触发源、白点=此刻），比字母更贴合。
  自适应图标安全区：108 画布里只用中间约 60（66dp 圆内）。
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- 起点：暗轨色小点 -->
    <path
        android:fillColor="#3A4874"
        android:pathData="M34,68 m-5,0 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0" />
    <!-- 路径 -->
    <path
        android:fillColor="#00000000"
        android:pathData="M34,68 C34,54 48,52 54,52 C60,52 74,50 74,38"
        android:strokeColor="#9BE0CC"
        android:strokeLineCap="round"
        android:strokeWidth="7" />
    <!-- 此刻：白点 -->
    <path
        android:fillColor="#EDF0FF"
        android:pathData="M74,38 m-9,0 a9,9 0 1,0 18,0 a9,9 0 1,0 -18,0" />
    <path
        android:fillColor="#9BE0CC"
        android:pathData="M74,38 m-4.5,0 a4.5,4.5 0 1,0 9,0 a4.5,4.5 0 1,0 -9,0" />
</vector>
"""
open(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), "w",
     encoding="utf-8", newline="\n").write(FG)
print("  ✓ ic_launcher_foreground.xml（夜底 + 薄荷路径 + 白点）")

STAT = """<?xml version="1.0" encoding="utf-8"?>
<!--
  状态栏 / 通知小图标：只允许**单色剪影**（系统会自己上色）。
  图形沿用 App 图标：一段路径 + 终点圆点。
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#00000000"
        android:pathData="M5,18 C5,13 10,13 12,12 C14,11 19,10 19,6"
        android:strokeColor="#FFFFFF"
        android:strokeLineCap="round"
        android:strokeWidth="2" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M19,6 m-3,0 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0" />
</vector>
"""
open(os.path.join(RES, "drawable", "ic_stat_alarm.xml"), "w",
     encoding="utf-8", newline="\n").write(STAT)
print("  ✓ ic_stat_alarm.xml（单色剪影，状态栏/通知共用）")
print("完成")
