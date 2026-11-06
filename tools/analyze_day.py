# -*- coding: utf-8 -*-
"""分析拉下来的两个库：过去 ~36 小时的运行情况。"""
import os
import shutil
import sqlite3
import tempfile
import time
from datetime import datetime

SRC = r"D:\My Projects\_dbdump"
TMP = tempfile.mkdtemp()
for f in os.listdir(SRC):
    shutil.copy2(os.path.join(SRC, f), os.path.join(TMP, f))

NOW = int(time.time() * 1000)
H36 = NOW - 36 * 3600_000
H24 = NOW - 24 * 3600_000


def fmt(ms):
    return datetime.fromtimestamp(ms / 1000).strftime("%m-%d %H:%M:%S")


def conn(name):
    c = sqlite3.connect(os.path.join(TMP, name))
    c.row_factory = sqlite3.Row
    return c


print("=" * 78)
print("时间基准：现在", fmt(NOW), " / 统计窗口 过去 36 小时")
print("=" * 78)

# ---------- 事件日志 ----------
ev = conn("ruyue.db")
print("\n【事件日志 · 过去 36 小时按类型计数】")
rows = ev.execute(
    "SELECT type, COUNT(*) n FROM event_logs WHERE timestamp >= ? GROUP BY type ORDER BY n DESC",
    (H36,),
).fetchall()
for r in rows:
    print(f"  {r['type']:<18} {r['n']}")

total24 = ev.execute("SELECT COUNT(*) n FROM event_logs WHERE timestamp >= ?", (H24,)).fetchone()["n"]
print(f"\n  过去 24 小时共 {total24} 条事件")

print("\n【过去 36 小时·关键事件（触发 / 响铃 / 条件达成 / 异常）】")
rows = ev.execute(
    """SELECT timestamp, type, detail, ssid FROM event_logs
       WHERE timestamp >= ? AND type IN
         ('ALARM_FIRED','RING_OPENED','RING_ACK','COND_MET','ERROR','BOOT')
       ORDER BY timestamp""",
    (H36,),
).fetchall()
if not rows:
    print("  （没有任何触发/响铃/条件达成事件）")
for r in rows:
    d = (r["detail"] or "").replace("\n", " ][ ")[:150]
    print(f"  {fmt(r['timestamp'])}  {r['type']:<12} {d}")

print("\n【自检 / 服务启停 / 异常 NOTE】")
rows = ev.execute(
    """SELECT timestamp, type, detail FROM event_logs
       WHERE timestamp >= ? AND type IN ('NOTE','SERVICE_START','SERVICE_STOP')
         AND (detail LIKE '%自检%' OR detail LIKE '%哨兵%' OR type IN ('SERVICE_START','SERVICE_STOP'))
       ORDER BY timestamp""",
    (H36,),
).fetchall()
for r in rows[:40]:
    d = (r["detail"] or "").replace("\n", " ")[:130]
    print(f"  {fmt(r['timestamp'])}  {r['type']:<14} {d}")
print(f"  （共 {len(rows)} 条）")

# 每 6 小时的事件密度（看有没有"死一段时间"）
print("\n【事件密度（每 3 小时一格，■=有事件）】")
import collections
buckets = collections.Counter()
for r in ev.execute("SELECT timestamp FROM event_logs WHERE timestamp >= ?", (H36,)):
    buckets[(r["timestamp"] - H36) // (3 * 3600_000)] += 1
for i in range(12):
    n = buckets.get(i, 0)
    t = fmt(H36 + i * 3 * 3600_000)[6:]
    print(f"  {t}  {'■' * min(n, 40)}{'' if n else '（空）'}  {n}")

# ---------- 提醒库 ----------
rm = conn("ruyue_reminders.db")
print("\n【约定清单】")
for r in rm.execute(
    "SELECT id,title,enabled,timeEnabled,hour,minute,wifiEnabled,placeEnabled,"
    "lastFiredAt,cooldownMinutes,suppressUntilDay FROM reminders ORDER BY id"
):
    flags = []
    if r["timeEnabled"]:
        flags.append(f"时间{r['hour']:02d}:{r['minute']:02d}")
    if r["wifiEnabled"]:
        flags.append("WiFi")
    if r["placeEnabled"]:
        flags.append("地点")
    print(
        f"  #{r['id']:<3} {'启用' if r['enabled'] else '暂停'}  {r['title'][:22]:<24}"
        f" {'+'.join(flags) or '无条件':<20} 上次响 {fmt(r['lastFiredAt']) if r['lastFiredAt'] else '无'}"
        f"  冷却{r['cooldownMinutes']}m  抑制至{fmt(r['suppressUntilDay']) if r['suppressUntilDay'] else '无'}"
    )

print("\n【对账台账 · 过去 36 小时】")
rows = rm.execute(
    "SELECT state, COUNT(*) n FROM expected_fires WHERE expectedAt >= ? GROUP BY state", (H36,)
).fetchall()
for r in rows:
    print(f"  {r['state']:<10} {r['n']}")
missed = rm.execute(
    "SELECT reminderId, expectedAt, reason FROM expected_fires "
    "WHERE expectedAt >= ? AND state='MISSED' ORDER BY expectedAt", (H36,)
).fetchall()
if missed:
    print("  ⚠️ 漏掉的：")
    for r in missed[:10]:
        print(f"    {fmt(r['expectedAt'])} reminder#{r['reminderId']} 原因={r['reason']}")
else:
    print("  ✓ 没有 MISSED")

for f in os.listdir(TMP):
    os.remove(os.path.join(TMP, f))
os.rmdir(TMP)
