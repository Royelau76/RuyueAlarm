"""离线验证提醒库 v5 -> v6 迁移（reminders 表加 onceDate 列，日历选点用）。

做法同前两次：用 v5 的建表语句搭 fixture → 插老数据 → 执行 MIGRATION_5_6 的 ALTER
→ PRAGMA table_info 与 Room 期望的 24 列逐项比对 → 确认老数据完好且 onceDate 默认 0。

期望的列定义取自构建产物 ReminderDatabase_Impl 的 createAllTables（逐字符一致）。

跑法：cd RuyueAlarm && python tools/verify_migration_v5_v6.py
"""
import sqlite3
import sys

# ---- v5 的 reminders（23 列，还没有 onceDate）----
REMINDERS_V5 = (
    "CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
    "`title` TEXT NOT NULL, `note` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `timeEnabled` INTEGER NOT NULL, "
    "`hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `repeatMask` INTEGER NOT NULL, "
    "`wifiEnabled` INTEGER NOT NULL, `targetSsids` TEXT NOT NULL, `wifiDwellSeconds` INTEGER NOT NULL, "
    "`placeEnabled` INTEGER NOT NULL, `placeName` TEXT NOT NULL, `latitude` REAL NOT NULL, "
    "`longitude` REAL NOT NULL, `radiusMeters` INTEGER NOT NULL, `placeDwellSeconds` INTEGER NOT NULL, "
    "`matchMode` TEXT NOT NULL, `ringtoneUri` TEXT NOT NULL, `volumeRampSeconds` INTEGER NOT NULL, "
    "`cooldownMinutes` INTEGER NOT NULL, `lastFiredAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
)
SSID_HISTORY = (
    "CREATE TABLE IF NOT EXISTS `ssid_history` (`ssid` TEXT NOT NULL, `lastSeenAt` INTEGER NOT NULL, "
    "PRIMARY KEY(`ssid`))"
)
SAVED_PLACES = (
    "CREATE TABLE IF NOT EXISTS `saved_places` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
    "`name` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `radiusMeters` INTEGER NOT NULL, "
    "`address` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL)"
)
EXPECTED_FIRES = (
    "CREATE TABLE IF NOT EXISTS `expected_fires` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
    "`reminderId` INTEGER NOT NULL, `title` TEXT NOT NULL, `expectedAt` INTEGER NOT NULL, "
    "`state` TEXT NOT NULL, `settledAt` INTEGER NOT NULL, `reason` TEXT NOT NULL)"
)

# ---- MIGRATION_5_6（与 ReminderDb.kt 逐字符一致）----
MIGRATION_5_6 = "ALTER TABLE reminders ADD COLUMN onceDate INTEGER NOT NULL DEFAULT 0"

# ---- Room 期望的 24 列（顺序 / 名字 / 类型 / notnull / pk）----
EXPECTED_COLUMNS = [
    ("id", "INTEGER", 1, 1),
    ("title", "TEXT", 1, 0),
    ("note", "TEXT", 1, 0),
    ("enabled", "INTEGER", 1, 0),
    ("timeEnabled", "INTEGER", 1, 0),
    ("hour", "INTEGER", 1, 0),
    ("minute", "INTEGER", 1, 0),
    ("repeatMask", "INTEGER", 1, 0),
    ("onceDate", "INTEGER", 1, 0),
    ("wifiEnabled", "INTEGER", 1, 0),
    ("targetSsids", "TEXT", 1, 0),
    ("wifiDwellSeconds", "INTEGER", 1, 0),
    ("placeEnabled", "INTEGER", 1, 0),
    ("placeName", "TEXT", 1, 0),
    ("latitude", "REAL", 1, 0),
    ("longitude", "REAL", 1, 0),
    ("radiusMeters", "INTEGER", 1, 0),
    ("placeDwellSeconds", "INTEGER", 1, 0),
    ("matchMode", "TEXT", 1, 0),
    ("ringtoneUri", "TEXT", 1, 0),
    ("volumeRampSeconds", "INTEGER", 1, 0),
    ("cooldownMinutes", "INTEGER", 1, 0),
    ("lastFiredAt", "INTEGER", 1, 0),
    ("createdAt", "INTEGER", 1, 0),
]

con = sqlite3.connect(":memory:")
db = con.cursor()
for ddl in (REMINDERS_V5, SSID_HISTORY, SAVED_PLACES, EXPECTED_FIRES):
    db.execute(ddl)

# 老数据：一条「每天 09:00」的提醒（v5 里没有 onceDate 这一列）
db.execute(
    "INSERT INTO reminders (title,note,enabled,timeEnabled,hour,minute,repeatMask,wifiEnabled,"
    "targetSsids,wifiDwellSeconds,placeEnabled,placeName,latitude,longitude,radiusMeters,"
    "placeDwellSeconds,matchMode,ringtoneUri,volumeRampSeconds,cooldownMinutes,lastFiredAt,createdAt) "
    "VALUES ('开工提醒','带工牌',1,1,9,0,31,1,'TP-LINK_1602_5G',15,1,'公司',31.23105,121.47321,300,60,"
    "'ALL','',15,1440,0,1700000000000)"
)
print("v5 fixture：reminders 1 行（每天 09:00，末列 createdAt=1700000000000）")

db.execute(MIGRATION_5_6)
print("已执行 MIGRATION_5_6")

ok = True

# --- 列定义 ---
# ⚠️ 按**列名**比对，不按顺序：`ALTER TABLE ADD COLUMN` 只能把新列加在末尾，
#    而实体会把 onceDate 写在 repeatMask 后面 —— 位置不同但 Room 不在乎
#    （TableInfo 内部是 Map<列名, Column>，比对不看顺序；v1→v2 / v2→v3 也是这样，线上跑了很久）。
cols = db.execute("PRAGMA table_info(reminders)").fetchall()
actual_map = {c[1]: (c[2].upper(), c[3], c[5]) for c in cols}
expected_map = {name: (type_, notnull, pk) for name, type_, notnull, pk in EXPECTED_COLUMNS}
if actual_map != expected_map:
    ok = False
    print("✗ 列定义与 Room 期望不一致")
    for name in expected_map:
        if name not in actual_map:
            print(f"   缺少列 {name}")
        elif expected_map[name] != actual_map[name]:
            print(f"   {name}: 期望 {expected_map[name]} / 实际 {actual_map[name]}")
    for name in actual_map:
        if name not in expected_map:
            print(f"   多出列 {name}")
else:
    print(f"✓ 列定义完全一致（{len(expected_map)} 列，按列名比对）")
    print(f"   实际顺序里 onceDate 在末位（ALTER 的必然结果，Room 按名字匹配，无影响）")

# --- 老数据完好 + 新列默认 0 ---
row = db.execute(
    "SELECT title, hour, minute, repeatMask, onceDate, createdAt FROM reminders"
).fetchone()
print(f"迁移后老行: {row}")
if row is None or row[0] != "开工提醒" or row[1] != 9 or row[3] != 31:
    ok = False
    print("✗ 老数据被改动")
elif row[4] != 0:
    ok = False
    print(f"✗ 老行的 onceDate 应为 0（未指定日期），实际 {row[4]}")
else:
    print("✓ 老数据完好，onceDate 默认 0（= 行为与以前一致：按「下一个 hh:mm」算）")

# --- 新值能写进去（指定日期）---
db.execute("UPDATE reminders SET onceDate = 1763203200000 WHERE id = 1")
v = db.execute("SELECT onceDate FROM reminders WHERE id = 1").fetchone()[0]
if v != 1763203200000:
    ok = False
    print(f"✗ onceDate 写入失败：{v}")
else:
    print("✓ 指定日期能存能取（1763203200000）")

con.close()
print("\n=== 结论：" + ("PASS" if ok else "FAIL") + " ===")
sys.exit(0 if ok else 1)
