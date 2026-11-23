# -*- coding: utf-8 -*-
"""排查：WiFi+时间「同时满足」却在连上 WiFi 时就响了。"""
import os
import shutil
import sqlite3
import tempfile
from datetime import datetime, timedelta

SRC = r"D:\My Projects\_archive\2026-11-19"
TMP = tempfile.mkdtemp()
for f in os.listdir(SRC):
    shutil.copy2(os.path.join(SRC, f), os.path.join(TMP, f))

ev = sqlite3.connect(os.path.join(TMP, "ev.db"))
ev.row_factory = sqlite3.Row
rm = sqlite3.connect(os.path.join(TMP, "rm.db"))
rm.row_factory = sqlite3.Row


def f(t):
    return datetime.fromtimestamp(t / 1000).strftime("%m-%d %H:%M:%S")


now_ms = int(datetime.now().timestamp() * 1000)
since = now_ms - 36 * 3600_000

print("=== 过去 36 小时：触发 / 弹页 / 条件达成 / 跳过说明 ===")
for r in ev.execute(
    "SELECT timestamp,type,detail,ssid FROM event_logs "
    "WHERE timestamp >= ? AND type IN ('ALARM_FIRED','RING_OPENED','COND_MET','NOTE','WIFI_CONNECT') "
    "ORDER BY timestamp",
    (since,),
):
    d = (r["detail"] or "")[:150]
    print(f"{f(r['timestamp'])}  {r['type']:<13} {d}")

print("\n=== 全部约定（重点看 matchMode 与时间）===")
for r in rm.execute(
    "SELECT id,title,enabled,matchMode,timeEnabled,hour,minute,repeatMask,onceDate,"
    "wifiEnabled,targetSsids,placeEnabled,cooldownMinutes,lastFiredAt FROM reminders ORDER BY lastFiredAt DESC"
):
    print(
        f"#{r['id']:<3} {r['title'][:14]:<16} {'启用' if r['enabled'] else '暂停'} "
        f"mode={r['matchMode']:<3} 时间={int(r['timeEnabled'])}({r['hour']:02d}:{r['minute']:02d}"
        f" mask={r['repeatMask']} once={r['onceDate']}) "
        f"WiFi={int(r['wifiEnabled'])}({r['targetSsids']}) 地点={int(r['placeEnabled'])} "
        f"冷却={r['cooldownMinutes']} 上次={f(r['lastFiredAt']) if r['lastFiredAt'] else '无'}"
    )

print("\n=== 「同时满足·全部通过」证据行（v0.27.5 加的）===")
rows = ev.execute(
    "SELECT timestamp,detail FROM event_logs WHERE timestamp >= ? AND detail LIKE '%同时满足%' ORDER BY timestamp",
    (since,),
).fetchall()
if not rows:
    print("  ⚠️ 一条都没有 —— 说明这段时间的触发都没走 ALL 闸门（或没触发过 ALL 模式）")
for r in rows:
    print(f"  {f(r['timestamp'])}  {r['detail']}")

print("\n=== 「缺少 …/跳过」类的说明行 ===")
rows = ev.execute(
    "SELECT timestamp,detail FROM event_logs WHERE timestamp >= ? "
    "AND (detail LIKE '%缺少%' OR detail LIKE '%跳过%' OR detail LIKE '%本次不触发%') ORDER BY timestamp",
    (since,),
).fetchall()
if not rows:
    print("  （没有）")
for r in rows[:20]:
    print(f"  {f(r['timestamp'])}  {(r['detail'] or '')[:140]}")
