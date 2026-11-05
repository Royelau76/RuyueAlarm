# -*- coding: utf-8 -*-
"""打印这次要改的几处代码片段。"""
import io, os, re

AK = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm"


def show(path, pattern, ctx=0, title=None, maxhits=3):
    print("=" * 70)
    print("###", title or (os.path.basename(path) + " :: " + pattern))
    full = os.path.join(AK, path)
    if not os.path.exists(full):
        print("  (文件不存在)", full)
        return
    lines = open(full, encoding="utf-8").read().split("\n")
    hits = 0
    for i, l in enumerate(lines):
        if re.search(pattern, l):
            hits += 1
            lo, hi = max(0, i - ctx), min(len(lines), i + ctx + 1)
            for j in range(lo, hi):
                print(f"{j+1:4d}| {lines[j]}")
            print("    ---")
            if hits >= maxhits:
                break
    if hits == 0:
        print("  (没匹配到)")


show("ui/ReminderViewModel.kt", r"fun save\(", 40, "ReminderViewModel.save")
show("ui/ReminderViewModel.kt", r"fun setEnabled|fun toggle", 4, "setEnabled?")
show("ui/ReminderEditScreen.kt", r"5 分|5分|add\(Calendar\.MINUTE", 8, "+5 分按钮", 2)
