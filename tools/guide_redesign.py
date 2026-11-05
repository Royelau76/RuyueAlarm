# -*- coding: utf-8 -*-
"""引导页按设计语言重做（分组卡列表 + 右侧「去设置 ›」，参照 set.png 的权限分组）。

只替换 GuideScreen 的函数体 + 增补几个局部组件，**底部那批 openXxx(context) 跳转函数原样保留**。
"""
import os
import re

P = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm\ui\GuideScreen.kt"
s = open(P, encoding="utf-8").read()

BODY = '''fun GuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val ruyue = LocalRuyueColors.current

    RuyueBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "\u2039 返回",
                    style = MaterialTheme.typography.labelLarge,
                    color = ruyue.time,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
            ) {
                Text(
                    text = "\u5148\u628a\u8fd9\u56db\u6837\u5f00\u5f00",
                    style = MaterialTheme.typography.headlineLarge,
                    color = ruyue.text,
                )
                Text(
                    text = "\u4e0d\u6253\u5f00\u300c\u540e\u53f0\u5f39\u51fa\u754c\u9762\u300d\uff0c\u9501\u5c4f\u95f9\u949f\u53ea\u4f1a\u53d8\u6210\u4e00\u6761\u901a\u77e5\u3002",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ruyue.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )

                GuideLabel("\u5fc5\u987b\u5f00\u7684")
                GuideCard {
                    GuideRow("\u81ea\u542f\u52a8", "\u624b\u673a\u7ba1\u5bb6 \u2192 \u5e94\u7528\u7ba1\u7406 \u2192 \u6743\u9650 \u2192 \u81ea\u542f\u52a8\u7ba1\u7406", "\u53bb\u8bbe\u7f6e") { openMiuiAutostart(context) }
                    GuideDivider()
                    GuideRow("\u7701\u7535\u7b56\u7565 \u2192 \u65e0\u9650\u5236", "\u8bbe\u7f6e \u2192 \u5e94\u7528\u7ba1\u7406 \u2192 \u5982\u7ea6 \u2192 \u7701\u7535\u7b56\u7565", "\u53bb\u8bbe\u7f6e") { openAppDetails(context) }
                    GuideDivider()
                    GuideRow("\u540e\u53f0\u5f39\u51fa\u754c\u9762", "\u6700\u5173\u952e\u7684\u4e00\u9879\uff1b\u4e0d\u5f00\u5c31\u53ea\u6709\u901a\u77e5\uff0c\u6ca1\u6709\u5168\u5c4f\u95f9\u949f", "\u53bb\u8bbe\u7f6e") { openAppDetails(context) }
                    GuideDivider()
                    GuideRow("\u901a\u77e5\u6743\u9650 + \u9501\u5c4f\u901a\u77e5", "\u5141\u8bb8\u901a\u77e5\uff0c\u5e76\u52fe\u4e0a\u300c\u9501\u5c4f\u663e\u793a\u300d", "\u53bb\u8bbe\u7f6e") { openNotificationSettings(context) }
                }

                GuideLabel("\u5efa\u8bae\u4e00\u8d77\u5f00\u7684")
                GuideCard {
                    GuideRow("\u7535\u6c60\u4f18\u5316\u767d\u540d\u5355", "\u9632\u6b62 Doze \u628a\u540e\u53f0\u95f9\u949f\u5ef6\u540e", "\u53bb\u8bbe\u7f6e") { openBatteryOptimization(context) }
                    GuideDivider()
                    GuideRow("\u5168\u5c4f\u901a\u77e5\u5f00\u5173\uff08Android 14+\uff09", "\u90e8\u5206 ROM \u9ed8\u8ba4\u62e6\u622a\u5168\u5c4f\u610f\u56fe", "\u53bb\u8bbe\u7f6e") { openFullScreenIntent(context) }
                    GuideDivider()
                    GuideRow("\u59cb\u7ec8\u4fdd\u6301 WiFi \u8fde\u63a5", "\u606f\u5c4f\u540e\u88ab\u5173 WiFi\uff0c\u4f1a\u8ba9\u300c\u5230\u5bb6\u63d0\u9192\u300d\u5ef6\u8fdf", null) {}
                }

                GuideLabel("\u5f3a\u505c = \u5168\u90e8\u5931\u6548")
                GuideCard {
                    Text(
                        text = "Android \u7684\u786c\u9650\u5236\uff1a\u5728\u7cfb\u7edf\u8bbe\u7f6e\u91cc\u70b9\u300c\u5f3a\u884c\u505c\u6b62\u300d\uff0c\u6240\u6709\u95f9\u949f\u4e0e\u76d1\u542c\u90fd\u4f1a\u5931\u6548\u3002\n" +
                            "\u4e0d\u7528\u614c \u2014\u2014 \u91cd\u65b0\u6253\u5f00\u4e00\u6b21\u300c\u5982\u7ea6\u300d\uff0c\u5b83\u4f1a\u81ea\u52a8\u91cd\u65b0\u6392\u5b9a\u5168\u90e8\u63d0\u9192\u3002",
                        style = MaterialTheme.typography.bodySmall,
                        color = ruyue.textTertiary,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                Text(
                    text = "\u81ea\u542f\u52a8\u548c\u300c\u540e\u53f0\u5f39\u51fa\u754c\u9762\u300d\u662f\u5c0f\u7c73\u79c1\u6709\u6743\u9650\uff0cadb \u6388\u4e0d\u4e86\uff0c\u53ea\u80fd\u624b\u52a8\u70b9\u3002",
                    style = MaterialTheme.typography.labelSmall,
                    color = ruyue.textTertiary,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun GuideLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalRuyueColors.current.textTertiary,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun GuideCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(LocalRuyueColors.current.card),
        content = content,
    )
}

@Composable
private fun GuideDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(LocalRuyueColors.current.track.copy(alpha = 0.45f)),
    )
}

@Composable
private fun GuideRow(title: String, desc: String, action: String?, onClick: () -> Unit) {
    val c = LocalRuyueColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (action != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            Spacer(Modifier.height(2.dp))
            Text(desc, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        }
        if (action != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = c.time)
            Text(" \u203a", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        }
    }
}

'''

i = s.index("fun GuideScreen(onBack: () -> Unit) {")
j = s.index("private fun openAppDetails(context: Context)")
s = s[:i] + BODY + s[j:]

# 补齐 import
need = [
    "androidx.compose.foundation.background",
    "androidx.compose.foundation.clickable",
    "androidx.compose.foundation.layout.ColumnScope",
    "androidx.compose.foundation.shape.RoundedCornerShape",
    "androidx.compose.ui.draw.clip",
    "com.ruyue.alarm.ui.design.RuyueBackground",
]
lines = s.split("\n")
missing = [x for x in need if ("import " + x) not in s]
last_import = max(n for n, l in enumerate(lines) if l.startswith("import "))
for imp in reversed(missing):
    lines.insert(last_import + 1, "import " + imp)
s = "\n".join(lines)
open(P, "w", encoding="utf-8", newline="\n").write(s)
print("已重写 GuideScreen 函数体；补", len(missing), "个 import")
