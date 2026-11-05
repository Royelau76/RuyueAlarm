# -*- coding: utf-8 -*-
import os
P = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm\trigger\TriggerEngine.kt"
lines = open(P, encoding="utf-8").read().split("\n")
for i, l in enumerate(lines):
    if "suspend fun fire(" in l:
        for j in range(i, min(i + 26, len(lines))):
            print(f"{j+1:4d}| {lines[j]}")
        break

print("\n### ReminderRepository save/setEnabled")
P2 = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm\data\ReminderRepository.kt"
lines2 = open(P2, encoding="utf-8").read().split("\n")
for i, l in enumerate(lines2):
    if "suspend fun setEnabled" in l or "suspend fun save" in l:
        for j in range(i, min(i + 8, len(lines2))):
            print(f"{j+1:4d}| {lines2[j]}")
        print("    ---")
