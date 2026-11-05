# -*- coding: utf-8 -*-
import os, re
AK = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm"

def dump(path, patterns, ctx=3, maxhits=4, title=None):
    print("=" * 72)
    print("###", title or path)
    full = os.path.join(AK, path)
    lines = open(full, encoding="utf-8").read().split("\n")
    for pat in patterns:
        hits = 0
        for i, l in enumerate(lines):
            if re.search(pat, l):
                hits += 1
                lo, hi = max(0, i - ctx), min(len(lines), i + ctx + 1)
                for j in range(lo, hi):
                    print(f"{j+1:4d}| {lines[j]}")
                print("    ---")
                if hits >= maxhits:
                    break

dump("MainActivity.kt", [r"sealed|object Route|data class|Route\.|when \(route|onOpenSettings|ReminderEditScreen|ReminderListScreen"], 3, 3, "MainActivity 路由")
dump("data/ReminderDb.kt", [r"version =|MIGRATION_|addMigrations"], 4, 4, "ReminderDb 迁移")
dump("data/Reminder.kt", [r"val onceDate|val enabled"], 2, 2, "Reminder 字段")
