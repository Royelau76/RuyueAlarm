# -*- coding: utf-8 -*-
"""分析过去 ~4 天的运行日志（11-06 起到现在）。"""
import os
import shutil
import sqlite3
import tempfile
import time
from collections import Counter, defaultdict
from datetime import datetime

SRC = r"D:\My Projects\_dbdump"
TMP = tempfile.mkdtemp()
for f in os.listdir(SRC):
    shutil.copy2(os.path.join(SRC, f), os.path.join(TMP, f))

NOW = int(time.time() * 1000)
DAYS = 4
FROM = NOW - DAYS * 24 * 3600_000


def f(ms):
    return datetime.fromtimestamp(ms / 1000).strftime("%m-%d %H:%M:%S")


def d(ms):
    return datetime.fromtimestamp(ms / 1000).strftime("%m-%d")


ev = sqlite3.connect(os.path.join(TMP, "ruyue.db"))
ev.row_factory = sqlite3.Row
rm = sqlite3.connect(os.path.join(TMP, "ruyue_reminders.db"))
rm.row_factory = sqlite3.Row

print("=" * 76)
print(f"时间基准 {f(NOW)} · 窗口 {DAYS} 天（{d(FROM)} 起）")
print("=" * 76)
print("\n【事件类型计数】")
for r in ev.execute(
    "SELECT type, COUNT(*) n FROM event_logs WHERE timestamp >= ? GROUP BY type ORDER BY n DESC", (FROM,)
):
    print(f"  {r['type']:<16} {r['n']}")

print("\n【每天事件数 / 心跳数】")
per_day = Counter()
hb = Counter()
for r in ev.execute("SELECT timestamp,type FROM event_logs WHERE timestamp >= ?", (FROM,)):
    per_day[d(r["timestamp"])] += 1
    if r["type"] == "HEARTBEAT":
        hb[d(r["timestamp"])] += 1
for k in sorted(per_day):
    print(f"  {k}  事件 {per_day[k]:<5} 心跳 {hb[k]}")

print("\n【每次响铃的完整链条（触发→弹页→响应）】")
rows = ev.execute(
    """SELECT timestamp,type,detail FROM event_logs
       WHERE timestamp >= ? AND type IN ('ALARM_FIRED','RING_OPENED','RING_ACK','COND_MET')
       ORDER BY timestamp""", (FROM,),
).fetchall()
for r in rows:
    t = r["type"]
    if t == "RING_ACK":
        det = (r["detail"] or "").replace("响铃响应：", "")
    elif t == "RING_OPENED":
        det = (r["detail"] or "").replace("响铃页弹出：", "")
    else:
        det = (r["detail"] or "")[:90]
    print(f"  {f(r['timestamp'])}  {t:<12} {det}")

print("\n【异常检查】")
# 1) 有 ALARM_FIRED 但 5 分钟内没有 RING_OPENED
fires = [(r["timestamp"], r["detail"]) for r in ev.execute(
    "SELECT timestamp,detail FROM event_logs WHERE timestamp>=? AND type='ALARM_FIRED' ORDER BY timestamp", (FROM,))]
opens = [r["timestamp"] for r in ev.execute(
    "SELECT timestamp FROM event_logs WHERE timestamp>=? AND type='RING_OPENED' ORDER BY timestamp", (FROM,))]
bad = []
for ts, det in fires:
    if not any(0 <= o - ts <= 300_000 for o in opens):
        bad.append((ts, det))
print(f"  · 响了但 5 分钟内没弹出响铃页：{len(bad)} 条")
for ts, det in bad[:5]:
    print(f"      {f(ts)}  {det}")

# 2) ERROR / 崩溃
errs = ev.execute("SELECT timestamp,detail FROM event_logs WHERE timestamp>=? AND type='ERROR' ORDER BY timestamp", (FROM,)).fetchall()
print(f"  · ERROR / 崩溃：{len(errs)} 条")
for r in errs[:5]:
    print(f"      {f(r['timestamp'])}  {(r['detail'] or '')[:120]}")

# 3) 同一个提醒在冷却期内响两次
rm_rows = {r["id"]: dict(r) for r in rm.execute("SELECT * FROM reminders")}
title2id = {v["title"]: k for k, v in rm_rows.items()}
by_rem = defaultdict(list)
for ts, det in fires:
    t = (det or "").split("：")[-1].strip()
    for title, rid in title2id.items():
        if title and title in (det or ""):
            by_rem[rid].append(ts)
            break
print("  · 同一条提醒的多次响铃（看是否都超过自己的冷却）：")
for rid, tss in by_rem.items():
    if len(tss) < 2:
        continue
    cd = rm_rows[rid]["cooldownMinutes"] * 60_000
    for a, b in zip(tss, tss[1:]):
        gap = (b - a) / 60000
        flag = "✓" if (b - a) >= cd else "⚠️ 冷却内又响了"
        print(f"      #{rid} {rm_rows[rid]['title'][:12]:<14} {f(a)[6:]} → {f(b)[6:]}  间隔 {gap:.0f} 分（冷却 {rm_rows[rid]['cooldownMinutes']}）{flag}")

# 4) 新加的验证日志有没有出现
for marker in ("同时满足·全部通过", "还在冷却期内", "同时满足"):
    n = ev.execute("SELECT COUNT(*) n FROM event_logs WHERE timestamp>=? AND detail LIKE ?", (FROM, f"%{marker}%")).fetchone()["n"]
    print(f"  · 含「{marker}」的日志行：{n}")

print("\n【对账台账】")
for r in rm.execute(
    "SELECT state, COUNT(*) n FROM expected_fires WHERE expectedAt >= ? GROUP BY state", (FROM,)
):
    print(f"  {r['state']:<10} {r['n']}")
missed = rm.execute(
    "SELECT reminderId,expectedAt,reason FROM expected_fires WHERE expectedAt>=? AND state='MISSED' ORDER BY expectedAt", (FROM,)
).fetchall()
for r in missed[:10]:
    print(f"  ⚠️ 漏响 {f(r['expectedAt'])} #{r['reminderId']} {r['reason']}")

print("\n【当前约定】")
for r in rm.execute("SELECT id,title,enabled,timeEnabled,hour,minute,wifiEnabled,placeEnabled,matchMode,cooldownMinutes,lastFiredAt,suppressUntilDay FROM reminders ORDER BY id"):
    fl = []
    if r["timeEnabled"]:
        fl.append(f"{r['hour']:02d}:{r['minute']:02d}")
    if r["wifiEnabled"]:
        fl.append("WiFi")
    if r["placeEnabled"]:
        fl.append("地点")
    print(f"  #{r['id']:<3} {'启用' if r['enabled'] else '暂停'} {r['title'][:16]:<18} {'+'.join(fl) or '-':<16} {r['matchMode']:<3} 冷却{r['cooldownMinutes']:<5} 上次响 {f(r['lastFiredAt'])[6:] if r['lastFiredAt'] else '无'}")

for f2 in os.listdir(TMP):
    try:
        os.remove(os.path.join(TMP, f2))
    except Exception:
        pass
