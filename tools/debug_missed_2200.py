# -*- coding: utf-8 -*-
"""排查：11-23 22:00 的提醒为什么没响。"""
import os
import shutil
import sqlite3
import tempfile
from datetime import datetime, timedelta

SRC = r"D:\My Projects\_archive\2026-11-24"
TMP = tempfile.mkdtemp()
for f in os.listdir(SRC):
    shutil.copy2(os.path.join(SRC, f), os.path.join(TMP, f))

ev = sqlite3.connect(os.path.join(TMP, "ruyue.db"))
ev.row_factory = sqlite3.Row
rm = sqlite3.connect(os.path.join(TMP, "ruyue_reminders.db"))
rm.row_factory = sqlite3.Row


def f(t):
    return datetime.fromtimestamp(t / 1000).strftime("%m-%d %H:%M:%S")


# 目标窗口：11-23 20:00 → 11-24 01:00
start = int(datetime(2026, 11, 23, 20, 0).timestamp() * 1000)
end = int(datetime(2026, 11, 24, 1, 0).timestamp() * 1000)

print("=== 11-23 20:00 → 11-24 01:00 的全部事件 ===")
for r in ev.execute(
    "SELECT timestamp,type,detail,ssid FROM event_logs WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp",
    (start, end),
):
    print(f"{f(r['timestamp'])}  {r['type']:<16} {(r['detail'] or '')[:130]}")

print("\n=== 22:00 那条提醒的当前配置 ===")
for r in rm.execute("SELECT * FROM reminders WHERE id=33"):
    d = dict(r)
    for k in ("id", "title", "enabled", "matchMode", "timeEnabled", "hour", "minute", "repeatMask",
              "wifiEnabled", "targetSsids", "wifiDwellSeconds", "placeEnabled", "cooldownMinutes",
              "lastFiredAt", "suppressUntilDay"):
        v = d.get(k)
        if k in ("lastFiredAt", "suppressUntilDay") and v:
            v = f"{v}（{f(v)}）"
        print(f"  {k:<18} {v}")

print("\n=== 对账台账：11-23 21:00 → 23:00 的预期 ===")
for r in rm.execute(
    "SELECT reminderId,expectedAt,state,reason FROM expected_fires "
    "WHERE expectedAt BETWEEN ? AND ? ORDER BY expectedAt",
    (int(datetime(2026, 11, 23, 21, 0).timestamp() * 1000),
     int(datetime(2026, 11, 23, 23, 59).timestamp() * 1000)),
):
    print(f"  {f(r['expectedAt'])}  reminder#{r['reminderId']}  state={r['state']}  reason={r['reason']}")

print("\n=== 11-23 全天的 ALARM_FIRED / SKIPPED / MISSED ===")
day0 = int(datetime(2026, 11, 23).timestamp() * 1000)
day1 = int(datetime(2026, 11, 24).timestamp() * 1000)
for r in ev.execute(
    "SELECT timestamp,type,detail FROM event_logs WHERE timestamp BETWEEN ? AND ? "
    "AND type IN ('ALARM_FIRED','RING_OPENED','RING_ACK') ORDER BY timestamp", (day0, day1)
):
    print(f"  {f(r['timestamp'])}  {r['type']:<12} {(r['detail'] or '')[:90]}")
print("  --- 台账 ---")
for r in rm.execute(
    "SELECT reminderId,expectedAt,state,reason FROM expected_fires WHERE expectedAt BETWEEN ? AND ? ORDER BY expectedAt",
    (day0, day1),
):
    print(f"  {f(r['expectedAt'])}  #{r['reminderId']}  {r['state']:<10} {r['reason'] or ''}")
