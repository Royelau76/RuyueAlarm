# -*- coding: utf-8 -*-
"""给 ReminderEditScreen 补 import。

为什么单独做：`Hint` 和编辑页在同一个包（com.ruyue.alarm.ui）里，本来就不需要 import，
所以我上一版脚本拿 `import com.ruyue.alarm.ui.Hint` 当锚点等于没匹配上。
这里改成"插在 import 块的末尾"。
"""
P = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm\ui\ReminderEditScreen.kt"
s = open(P, encoding="utf-8").read()

needed = [
    "import com.ruyue.alarm.ui.edit.EditCard",
    "import com.ruyue.alarm.ui.edit.EditSectionLabel",
    "import com.ruyue.alarm.ui.edit.HeroInputCard",
    "import com.ruyue.alarm.ui.theme.LocalRuyueColors",
    "import androidx.compose.foundation.background",
    "import androidx.compose.foundation.clickable",
    "import androidx.compose.foundation.shape.RoundedCornerShape",
    "import androidx.compose.ui.draw.clip",
]
missing = [i for i in needed if i not in s]
print("缺的 import：", len(missing))

lines = s.split("\n")
last_import = max(i for i, l in enumerate(lines) if l.startswith("import "))
for imp in reversed(missing):
    lines.insert(last_import + 1, imp)

s = "\n".join(lines)
open(P, "w", encoding="utf-8", newline="\n").write(s)
print("已插入，import 块行数：", sum(1 for l in lines if l.startswith("import ")))
