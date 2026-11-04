# -*- coding: utf-8 -*-
"""Step 3b：给 EditCard 加「底部弹层」模式。

为什么这么做：WiFi / 地点这两张卡里的控件（SSID 增删、去抖档位、半径、停留）已经
工作得很好，搬进弹层只是重新排版，**搬一次就有 200 行出错风险**。
所以改成：卡片头（标题+副标题+开关）留在页面上，参数内容在 ModalBottomSheet 里，
控件代码**一行不动**。
"""
P = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm\ui\edit\EditSection.kt"
s = open(P, encoding="utf-8").read()

# 1) import
for imp in [
    "import androidx.compose.foundation.rememberScrollState",
    "import androidx.compose.foundation.verticalScroll",
    "import androidx.compose.material3.ExperimentalMaterial3Api",
    "import androidx.compose.material3.ModalBottomSheet",
]:
    if imp not in s:
        s = s.replace("import androidx.compose.material3.MaterialTheme", imp + "\nimport androidx.compose.material3.MaterialTheme", 1)

# 2) 签名加参数
s = s.replace(
    """    bigSummary: Boolean = false,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {""",
    """    bigSummary: Boolean = false,
    initiallyExpanded: Boolean = true,
    /** true = 参数内容放进**底部上滑弹层**（设计稿 sheet-wifi / sheet-place 的做法） */
    asSheet: Boolean = false,
    openLabel: String = "设置…",
    content: @Composable ColumnScope.() -> Unit,
) {""",
)

# 3) 弹层开关 + 卡片头右下角的"打开设置"
s = s.replace(
    """    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }""",
    """    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    var sheetOpen by remember(title) { mutableStateOf(false) }""",
)

# 4) 内容区：asSheet 时只放一个入口按钮，参数在弹层
s = s.replace(
    """        if (bodyVisible) {
            Spacer(Modifier.height(10.dp))
            content()
        }""",
    """        if (bodyVisible) {
            if (asSheet) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "$openLabel  ›",
                    style = MaterialTheme.typography.labelLarge,
                    color = (accent ?: c.time),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background((accent ?: c.time).copy(alpha = 0.12f))
                        .clickable { sheetOpen = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            } else {
                Spacer(Modifier.height(10.dp))
                content()
            }
        }

        // 底部上滑弹层：控件代码原样复用（不搬动 = 不引入新 bug）
        if (asSheet && sheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { sheetOpen = false },
                containerColor = c.sheet,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleLarge, color = c.text)
                            if (summary.isNotBlank()) {
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                )
                            }
                        }
                        Text(
                            text = "关闭",
                            style = MaterialTheme.typography.labelLarge,
                            color = c.textSecondary,
                            modifier = Modifier
                                .clickable { sheetOpen = false }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.verticalScroll(rememberScrollState())) { content() }
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(accent ?: c.time)
                            .clickable { sheetOpen = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "用这个设置",
                            style = MaterialTheme.typography.labelLarge,
                            color = c.onAccent,
                        )
                    }
                }
            }
        }""",
)

# 5) EditCard 需要 opt-in
s = s.replace(
    """@Composable
fun EditCard(""",
    """@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCard(""",
)
open(P, "w", encoding="utf-8", newline="\n").write(s)
print("EditSection.kt 已改：", "ModalBottomSheet" in s, "| asSheet 参数:", "asSheet: Boolean" in s)
