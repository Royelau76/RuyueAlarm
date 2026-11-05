# -*- coding: utf-8 -*-
"""Step 4：设置页对齐设计稿 set.png（大标题 / 一句话定位 / 页脚 / 卡片配色）。

只改「框架与配色」，功能项一个不动。
"""
import os

P = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm\ui\SettingsScreen.kt"
s = open(P, encoding="utf-8").read()


def rep(old, new, label):
    global s
    if old in s:
        s = s.replace(old, new)
        print("  ✓", label)
    else:
        print("  ✗ 没匹配到:", label)


# 1) 大标题
rep(
    'Text("设置", style = MaterialTheme.typography.titleLarge)',
    'Text("设置", style = MaterialTheme.typography.headlineLarge)',
    "标题 24sp → 34sp",
)

# 2) 一句话定位（插在滚动列最前面）
rep(
    """                .padding(horizontal = 16.dp),
        ) {
            SettingsItem(""",
    """                .padding(horizontal = 16.dp),
        ) {
            // 设计稿：大标题下一句话就把"为什么可以放心用"说完
            Text(
                text = "🔒 数据只在本机。不同步、不上云。",
                style = MaterialTheme.typography.bodyMedium,
                color = ruyue.textSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 8.dp),
            )
            SettingsItem(""",
    "副标题",
)

# 3) 页脚
rep(
    """            Spacer(Modifier.height(20.dp))
            Text(
                text = "如约 v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Hint("自用版本：不做多设备同步、不上云。所有数据只在本机（两个 Room 库文件）。")""",
    """            Spacer(Modifier.height(26.dp))
            Text(
                text = "这个版本只有你一个人用。\\n所以没有账号、没有同步、没有订阅。",
                style = MaterialTheme.typography.bodySmall,
                color = ruyue.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "如约 v${BuildConfig.VERSION_NAME} · 数据只在本机（两个 Room 库）",
                style = MaterialTheme.typography.labelSmall,
                color = ruyue.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )""",
    "页脚",
)

# 4) SettingsItem 配色
rep(
    """@Composable
private fun SettingsItem(title: String, desc: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}""",
    """@Composable
private fun SettingsItem(title: String, desc: String, onClick: () -> Unit) {
    val c = LocalRuyueColors.current
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = c.card),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = c.textSecondary,
            )
        }
    }
}""",
    "SettingsItem 配色",
)

# 5) 补 import / 取色板
if "import com.ruyue.alarm.ui.theme.LocalRuyueColors" not in s:
    s = s.replace(
        "import com.ruyue.alarm.BuildConfig",
        "import com.ruyue.alarm.BuildConfig\nimport com.ruyue.alarm.ui.theme.LocalRuyueColors",
    )
if "import androidx.compose.ui.text.style.TextAlign" not in s:
    s = s.replace(
        "import androidx.compose.ui.unit.dp",
        "import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.unit.dp",
    )
if "import androidx.compose.foundation.shape.RoundedCornerShape" not in s:
    s = s.replace(
        "import androidx.compose.ui.unit.dp",
        "import androidx.compose.foundation.shape.RoundedCornerShape\nimport androidx.compose.ui.unit.dp",
    )

# 6) 在 composable 里取色板：找到 ScrolledColumn 之前的 val context 之类
if "val ruyue = LocalRuyueColors.current" not in s:
    s = s.replace(
        "fun SettingsScreen(",
        "fun SettingsScreen(",
    )
    # 插在函数体第一行
    idx = s.index("fun SettingsScreen(")
    brace = s.index("{", s.index(")", idx))
    s = s[: brace + 1] + "\n    val ruyue = LocalRuyueColors.current" + s[brace + 1 :]

open(P, "w", encoding="utf-8", newline="\n").write(s)
print("完成")
