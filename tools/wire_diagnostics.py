# -*- coding: utf-8 -*-
"""阶段 1 接线：路由 + 设置入口 + 引导页品牌提示。"""
import os

AK = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm"


def patch(rel, pairs, add_imports=()):
    p = os.path.join(AK, rel)
    s = open(p, encoding="utf-8").read()
    for old, new, label in pairs:
        if old in s:
            s = s.replace(old, new, 1)
            print("  ✓", label)
        else:
            print("  ✗ 没匹配到:", label)
    for imp in add_imports:
        if imp not in s:
            # 插到最后一个 import 后面
            lines = s.split("\n")
            last = max(i for i, l in enumerate(lines) if l.startswith("import "))
            lines.insert(last + 1, imp)
            s = "\n".join(lines)
            print("  ✓ import", imp.split(".")[-1])
    open(p, "w", encoding="utf-8", newline="\n").write(s)


# ---------- MainActivity：路由 ----------
patch("MainActivity.kt", [
    ("""    data object Guide : Route""",
     """    data object Guide : Route

    /** 自检诊断：这台手机准备好了吗（阶段 1，引导 + 自检） */
    data object Diagnostics : Route""",
     "Route.Diagnostics"),
    ("""                onOpenGuide = { route = Route.Guide },""",
     """                onOpenGuide = { route = Route.Guide },
                onOpenDiagnostics = { route = Route.Diagnostics },""",
     "Settings 传 onOpenDiagnostics"),
    ("""            Route.Guide -> GuideScreen(onBack = { route = Route.Settings })""",
     """            Route.Guide -> GuideScreen(onBack = { route = Route.Settings })
            Route.Diagnostics -> DiagnosticsScreen(onBack = { route = Route.Settings })""",
     "Diagnostics 路由分支"),
], add_imports=["import com.ruyue.alarm.ui.DiagnosticsScreen"])

# ---------- SettingsScreen：入口 ----------
patch("ui/SettingsScreen.kt", [
    ("""    onOpenGuide: () -> Unit,""",
     """    onOpenGuide: () -> Unit,
    onOpenDiagnostics: () -> Unit,""",
     "签名加 onOpenDiagnostics"),
    ("""            SettingsItem(
                title = "运行日志",""",
     """            SettingsItem(
                title = "自检诊断",
                desc = diagnosticsSummary(context),
                onClick = onOpenDiagnostics,
            )
            SettingsItem(
                title = "运行日志",""",
     "加自检诊断条目"),
])

# ---------- GuideScreen：最后那句「小米私有权限」改成按品牌 ----------
patch("ui/GuideScreen.kt", [
    ("""                Text(
                    text = "自启动和「后台弹出界面」是小米私有权限，adb 授不了，只能手动点。",""",
     """                Text(
                    text = com.ruyue.alarm.util.OemPermission.current().note +
                        " 具体路径见「设置 → 自检诊断」。",""",
     "品牌提示改为通用"),
])
print("完成")
