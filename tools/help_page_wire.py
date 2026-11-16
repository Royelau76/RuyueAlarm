# -*- coding: utf-8 -*-
"""设置页减字 + 加「使用帮助」入口 + 路由。"""
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
            lines = s.split("\n")
            last = max(i for i, l in enumerate(lines) if l.startswith("import "))
            lines.insert(last + 1, imp)
            s = "\n".join(lines)
            print("  ✓ import", imp.split(".")[-1])
    open(p, "w", encoding="utf-8", newline="\n").write(s)


# ---------- 设置页：文案瘦身 + 新增使用帮助 ----------
patch("ui/SettingsScreen.kt", [
    ("""    onOpenGuide: () -> Unit,""",
     """    onOpenGuide: () -> Unit,
    onOpenHelp: () -> Unit,""",
     "签名加 onOpenHelp"),
    ("""            SettingsItem(
                title = "通知与后台权限",
                desc = "自启动 / 省电无限制 / 后台弹出界面 —— 不开的话锁屏闹钟弹不出来",
                onClick = onOpenGuide,
            )""",
     """            SettingsItem(
                title = "使用帮助",
                desc = "三种触发怎么配 · 权限为什么必须开 · 不响怎么查",
                onClick = onOpenHelp,
            )
            SettingsItem(
                title = "通知与后台权限",
                desc = "不开的话锁屏闹钟弹不出来",
                onClick = onOpenGuide,
            )""",
     "加使用帮助 + 权限项减字"),
    ("""                title = "运行日志",
                desc = "按时间倒序看所有事件（排障用），可一键导出 txt",""",
     """                title = "运行日志",
                desc = "排障用，可导出 txt",""",
     "运行日志减字"),
    ("""                title = "数据备份",
                desc = "每次升级会自动备份提醒库；点这里可立即备份并分享出去留档",""",
     """                title = "数据备份",
                desc = "升级前自动备份；这里可手动导出",""",
     "数据备份减字"),
    ("""                title = "自检对账",
                desc = "核一遍「该响的提醒是不是都响了」；后台每天也会自动跑一次",""",
     """                title = "自检对账",
                desc = "核对该响的是不是都响了",""",
     "自检对账减字"),
    ("""                title = "M0 验证台",
                desc = "WiFi 权限 / 保活 / 全屏闹钟 / 连断统计 / 高德 —— 回归与排障入口",""",
     """                title = "M0 验证台",
                desc = "开发者验证入口",""",
     "验证台减字"),
])

# ---------- 路由 ----------
patch("MainActivity.kt", [
    ("""    /** 自检诊断：这台手机准备好了吗（阶段 1，引导 + 自检） */
    data object Diagnostics : Route""",
     """    /** 自检诊断：这台手机准备好了吗（阶段 1，引导 + 自检） */
    data object Diagnostics : Route

    /** 使用帮助：把设置页那些说明集中到这里 */
    data object Help : Route""",
     "Route.Help"),
    ("""                onOpenDiagnostics = { route = Route.Diagnostics },""",
     """                onOpenDiagnostics = { route = Route.Diagnostics },
                onOpenHelp = { route = Route.Help },""",
     "Settings 传 onOpenHelp"),
    ("""            Route.Diagnostics -> DiagnosticsScreen(onBack = { route = Route.Settings })""",
     """            Route.Diagnostics -> DiagnosticsScreen(onBack = { route = Route.Settings })
            Route.Help -> HelpScreen(onBack = { route = Route.Settings })""",
     "Help 路由分支"),
], add_imports=["import com.ruyue.alarm.ui.HelpScreen"])
print("完成")
