"""离线验证 reminders 库 v4 -> v5 迁移（新增 expected_fires 台账表）。

和 v3->v4 那次同样的方法：
  1. 用 v4 的建表语句搭 fixture（reminders / ssid_history / saved_places），插数据
  2. 执行 MIGRATION_4_5 的建表 + 建索引语句
  3. PRAGMA table_info / index_list / index_info 逐项比对 Room 的期望
  4. 确认老数据完好

跑法：cd RuyueAlarm && python tools/verify_migration_v4_v5.py
"""
import sqlite3
import sys

# --- v4 fixture（与 Room 生成的 v4 schema 一致；reminders/saved_places 在 v5 里没变） ---
REMINDERS = (
    "CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
    "`title` TEXT NOT NULL, `note` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `timeEnabled` INTEGER NOT NULL, "
    "`hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `repeatMask` INTEGER NOT NULL, `wifiEnabled` INTEGER NOT NULL, "
    "`targetSsids` TEXT NOT NULL, `wifiDwellSeconds` INTEGER NOT NULL, `placeEnabled` INTEGER NOT NULL, "
    "`placeName` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `radiusMeters` INTEGER NOT NULL, "
    "`placeDwellSeconds` INTEGER NOT NULL, `matchMode` TEXT NOT NULL, `ringtoneUri` TEXT NOT NULL, "
    "`volumeRampSeconds` INTEGER NOT NULL, `cooldownMinutes` INTEGER NOT NULL, `lastFiredAt` INTEGER NOT NULL, "
    "`createdAt` INTEGER NOT NULL)"
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

# --- MIGRATION_4_5（与 ReminderDb.kt 里逐字符一致） ---
MIGRATION_4_5 = [
    "CREATE TABLE IF NOT EXISTS `expected_fires` ("
    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
    "`reminderId` INTEGER NOT NULL, "
    "`title` TEXT NOT NULL, "
    "`expectedAt` INTEGER NOT NULL, "
    "`state` TEXT NOT NULL, "
    "`settledAt` INTEGER NOT NULL, "
    "`reason` TEXT NOT NULL)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_expected_fires_reminderId_expectedAt` "
    "ON `expected_fires` (`reminderId`, `expectedAt`)",
    "CREATE INDEX IF NOT EXISTS `index_expected_fires_expectedAt` "
    "ON `expected_fires` (`expectedAt`)",
]

EXPECTED_COLUMNS = [
    ("id", "INTEGER", 1, 1),
    ("reminderId", "INTEGER", 1, 0),
    ("title", "TEXT", 1, 0),
    ("expectedAt", "INTEGER", 1, 0),
    ("state", "TEXT", 1, 0),
    ("settledAt", "INTEGER", 1, 0),
    ("reason", "TEXT", 1, 0),
]
EXPECTED_INDEXES = {
    "index_expected_fires_reminderId_expectedAt": (1, ["reminderId", "expectedAt"]),
    "index_expected_fires_expectedAt": (0, ["expectedAt"]),
}

con = sqlite3.connect(":memory:")
db = con.cursor()
db.execute(REMINDERS)
db.execute(SSID_HISTORY)
db.execute(SAVED_PLACES)
db.execute(
    "INSERT INTO reminders (title, note, enabled, timeEnabled, hour, minute, repeatMask, wifiEnabled, "
    "targetSsids, wifiDwellSeconds, placeEnabled, placeName, latitude, longitude, radiusMeters, "
    "placeDwellSeconds, matchMode, ringtoneUri, volumeRampSeconds, cooldownMinutes, lastFiredAt, createdAt) "
    "VALUES ('开工提醒','带上工牌',1,1,9,0,127,1,'TP-LINK_1602_5G',15,1,'公司',31.23105,121.47321,300,60,"
    "'ALL','',15,1440,0,1700000000000)"
)
db.execute("INSERT INTO ssid_history VALUES ('TP-LINK_1602_5G', 1700000000000)")
db.execute("INSERT INTO saved_places (name,latitude,longitude,radiusMeters,address,createdAt,lastUsedAt) "
           "VALUES ('公司',31.23105,121.47321,300,'',1700000000000,1700000000000)")
print("v4 fixture 建好：reminders 1 / ssid_history 1 / saved_places 1")

for sql in MIGRATION_4_5:
    db.execute(sql)

ok = True

# --- 列 ---
cols = db.execute("PRAGMA table_info(expected_fires)").fetchall()
actual_cols = [(c[1], c[2].upper(), c[3], c[5]) for c in cols]
print("\nexpected_fires 实际列：")
for c in actual_cols:
    print("   ", c)
if actual_cols != EXPECTED_COLUMNS:
    ok = False
    print("✗ 列定义与 Room 期望不一致")
    for e, a in zip(EXPECTED_COLUMNS, actual_cols):
        if e != a:
            print(f"   期望 {e} / 实际 {a}")
    if len(actual_cols) != len(EXPECTED_COLUMNS):
        print(f"   列数不同：期望 {len(EXPECTED_COLUMNS)}，实际 {len(actual_cols)}")
else:
    print(f"✓ 列定义完全一致（{len(EXPECTED_COLUMNS)} 列）")

# --- 索引 ---
raw_indexes = db.execute("PRAGMA index_list(expected_fires)").fetchall()
# (seq, name, unique, origin, partial)
actual_indexes = {}
for row in raw_indexes:
    name, unique = row[1], row[2]
    if name.startswith("sqlite_autoindex"):
        continue
    idx_cols = [r[2] for r in db.execute(f"PRAGMA index_info(`{name}`)").fetchall()]
    actual_indexes[name] = (unique, idx_cols)
print("\nexpected_fires 实际索引：")
for name, info in actual_indexes.items():
    print(f"    {name}: unique={info[0]} cols={info[1]}")
if actual_indexes != EXPECTED_INDEXES:
    ok = False
    print("✗ 索引与 Room 期望不一致")
else:
    print(f"✓ 索引完全一致（{len(EXPECTED_INDEXES)} 个，含唯一索引）")

# --- 唯一索引真的生效？ ---
row = ("INSERT INTO expected_fires (reminderId,title,expectedAt,state,settledAt,reason) "
       "VALUES (1,'开工提醒',1700000000000,'EXPECTED',0,'')")
db.execute(row)
try:
    db.execute(row)  # 同一 (reminderId, expectedAt) 再来一条应当失败
    ok = False
    print("✗ 唯一索引没生效：重复记账竟然插进去了")
except sqlite3.IntegrityError:
    print("✓ 唯一索引生效：同一 (reminderId, expectedAt) 重复记账被拒绝（幂等）")

# --- 老数据 ---
r = db.execute("SELECT title, matchMode, radiusMeters FROM reminders").fetchone()
s = db.execute("SELECT name, radiusMeters FROM saved_places").fetchone()
print(f"\n迁移后 reminders: {r}")
print(f"迁移后 saved_places: {s}")
if r is None or r[0] != "开工提醒" or r[1] != "ALL":
    ok = False
    print("✗ reminders 老数据被改动")
if s is None or s[0] != "公司":
    ok = False
    print("✗ saved_places 老数据被改动")
if ok:
    print("✓ 老数据完好（两张表都没被动过）")

con.close()
print("\n=== 结论：" + ("PASS" if ok else "FAIL") + " ===")
sys.exit(0 if ok else 1)
