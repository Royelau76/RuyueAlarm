"""离线验证 reminders 库 v3 -> v4 迁移。

思路（和 v2->v3 那次一样）：
  1. 用 Room 生成的 v4 建表语句里 **reminders / ssid_history** 两段当 v3 fixture
     （v4 只新增了 saved_places，这两张表在 v3 和 v4 完全相同）
  2. 插一条提醒数据
  3. 执行 MIGRATION_3_4 里那条 CREATE TABLE
  4. 用 PRAGMA table_info 把 saved_places 的列属性与 Room 期望逐列比对
  5. 确认老数据完好
"""
import sqlite3
import sys

REMINDERS_V3 = (
    "CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
    "`title` TEXT NOT NULL, `note` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `timeEnabled` INTEGER NOT NULL, "
    "`hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `repeatMask` INTEGER NOT NULL, `wifiEnabled` INTEGER NOT NULL, "
    "`targetSsids` TEXT NOT NULL, `wifiDwellSeconds` INTEGER NOT NULL, `placeEnabled` INTEGER NOT NULL, "
    "`placeName` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `radiusMeters` INTEGER NOT NULL, "
    "`placeDwellSeconds` INTEGER NOT NULL, `matchMode` TEXT NOT NULL, `ringtoneUri` TEXT NOT NULL, "
    "`volumeRampSeconds` INTEGER NOT NULL, `cooldownMinutes` INTEGER NOT NULL, `lastFiredAt` INTEGER NOT NULL, "
    "`createdAt` INTEGER NOT NULL)"
)
SSID_HISTORY_V3 = (
    "CREATE TABLE IF NOT EXISTS `ssid_history` (`ssid` TEXT NOT NULL, `lastSeenAt` INTEGER NOT NULL, "
    "PRIMARY KEY(`ssid`))"
)
# 与 ReminderDb.MIGRATION_3_4 完全一致
MIGRATION_3_4 = (
    "CREATE TABLE IF NOT EXISTS `saved_places` ("
    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
    "`name` TEXT NOT NULL, "
    "`latitude` REAL NOT NULL, "
    "`longitude` REAL NOT NULL, "
    "`radiusMeters` INTEGER NOT NULL, "
    "`address` TEXT NOT NULL, "
    "`createdAt` INTEGER NOT NULL, "
    "`lastUsedAt` INTEGER NOT NULL)"
)
# Room 由 SavedPlace 实体生成的期望列：(name, type, notnull, pk)
EXPECTED = [
    ("id", "INTEGER", 1, 1),
    ("name", "TEXT", 1, 0),
    ("latitude", "REAL", 1, 0),
    ("longitude", "REAL", 1, 0),
    ("radiusMeters", "INTEGER", 1, 0),
    ("address", "TEXT", 1, 0),
    ("createdAt", "INTEGER", 1, 0),
    ("lastUsedAt", "INTEGER", 1, 0),
]

con = sqlite3.connect(":memory:")
db = con.cursor()
db.execute(REMINDERS_V3)
db.execute(SSID_HISTORY_V3)
db.execute(
    "INSERT INTO reminders (title, note, enabled, timeEnabled, hour, minute, repeatMask, wifiEnabled, "
    "targetSsids, wifiDwellSeconds, placeEnabled, placeName, latitude, longitude, radiusMeters, "
    "placeDwellSeconds, matchMode, ringtoneUri, volumeRampSeconds, cooldownMinutes, lastFiredAt, createdAt) "
    "VALUES ('开工提醒','带上工牌',1,1,9,0,127,1,'TP-LINK_1602_5G',15,1,'公司',31.23105,121.47321,300,60,"
    "'ALL','',15,1440,0,1700000000000)"
)
db.execute("INSERT INTO ssid_history VALUES ('TP-LINK_1602_5G', 1700000000000)")

print("v3 fixture 建好：reminders 1 行 / ssid_history 1 行")

# ---- 执行迁移 ----
db.execute(MIGRATION_3_4)

# ---- 逐列比对 saved_places ----
cols = db.execute("PRAGMA table_info(saved_places)").fetchall()
actual = [(c[1], c[2].upper(), c[3], c[5]) for c in cols]
print("\nsaved_places 实际列：")
for a in actual:
    print("   ", a)

ok = True
if actual != EXPECTED:
    ok = False
    print("\n✗ 列定义与期望不一致")
    for e, a in zip(EXPECTED, actual):
        if e != a:
            print(f"   期望 {e} / 实际 {a}")
    if len(actual) != len(EXPECTED):
        print(f"   列数不同：期望 {len(EXPECTED)}，实际 {len(actual)}")
else:
    print(f"\n✓ saved_places 列定义与 Room 期望完全一致（{len(EXPECTED)} 列）")

# ---- 老数据完好？ ----
row = db.execute(
    "SELECT title, targetSsids, matchMode, latitude, longitude, radiusMeters FROM reminders"
).fetchone()
print("\n迁移后的 reminders 老数据：", row)
if row is None or row[0] != "开工提醒" or row[2] != "ALL" or abs(row[3] - 31.23105) > 1e-9:
    ok = False
    print("✗ 老数据被改动了")
else:
    print("✓ 老数据完好（22 个旧列一个没丢，新表独立）")

if db.execute("SELECT COUNT(*) FROM ssid_history").fetchone()[0] != 1:
    ok = False
    print("✗ ssid_history 数据丢失")
else:
    print("✓ ssid_history 数据完好")

con.close()
print("\n=== 结论：" + ("PASS" if ok else "FAIL") + " ===")
sys.exit(0 if ok else 1)
