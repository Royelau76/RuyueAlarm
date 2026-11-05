# -*- coding: utf-8 -*-
import os
P = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm\data\ReminderDb.kt"
lines = open(P, encoding="utf-8").read().split("\n")
for j in range(140, min(175, len(lines))):
    print(f"{j+1:4d}| {lines[j]}")

print("\n### Reminder.kt imports")
P2 = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm\data\Reminder.kt"
for j, l in enumerate(open(P2, encoding="utf-8").read().split("\n")[:20]):
    print(f"{j+1:4d}| {l}")
