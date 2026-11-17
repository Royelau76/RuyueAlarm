# -*- coding: utf-8 -*-
"""Step 1/3：只改数据层 —— SsidHistory 加 alias、DAO 加两个方法、数据库 v7→v8。"""
import os
import re

AK = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm"


def patch(rel, pairs):
    p = os.path.join(AK, rel)
    s = open(p, encoding="utf-8").read()
    for old, new, label in pairs:
        if old in s:
            s = s.replace(old, new, 1)
            print("  ✓", label)
        else:
            print("  ✗ 没匹配到:", label)
    open(p, "w", encoding="utf-8", newline="\n").write(s)


# ---------------- 1) 实体 ----------------
patch("data/SsidHistory.kt", [(
    """data class SsidHistory(
    @PrimaryKey val ssid: String,
    val lastSeenAt: Long = System.currentTimeMillis(),
)""",
    """data class SsidHistory(
    @PrimaryKey val ssid: String,
    val lastSeenAt: Long = System.currentTimeMillis(),
    /**
     * 用户给这个网络起的备注（如「家里网」「公司」）。
     * 响铃页优先显示备注 —— TP-LINK_1602_5G 这种名字又长又难认（2026-11-16 加入）。
     */
    @ColumnInfo(defaultValue = "") val alias: String = "",
)""",
    "实体加 alias",
), (
    "import androidx.room.Dao",
    "import androidx.room.ColumnInfo\nimport androidx.room.Dao",
    "import ColumnInfo",
), (
    """    @Query("DELETE FROM ssid_history WHERE ssid = :ssid")
    suspend fun forget(ssid: String)""",
    """    @Query("DELETE FROM ssid_history WHERE ssid = :ssid")
    suspend fun forget(ssid: String)

    /** 只改备注，不动 lastSeenAt（否则改个备注会把它顶到列表最前面） */
    @Query("UPDATE ssid_history SET alias = :alias WHERE ssid = :ssid")
    suspend fun updateAlias(ssid: String, alias: String)

    @Query("SELECT alias FROM ssid_history WHERE ssid = :ssid")
    suspend fun aliasOf(ssid: String): String?""",
    "DAO 加 updateAlias / aliasOf",
)])

# ---------------- 2) 数据库 ----------------
P = os.path.join(AK, "data/ReminderDb.kt")
s = open(P, encoding="utf-8").read()

# 版本 7 → 8
s, n = re.subn(r"version = 7,", "version = 8,", s, count=1)
print("  ✓ version 7 → 8" if n else "  ✗ version 没改到")

# 插入 MIGRATION_7_8（按 MIGRATION_6_7 的真实声明形式匹配，带类型注解）
pat = re.compile(r"(\n(\s*)val MIGRATION_6_7[^\n=]*= object : Migration\(6, 7\) \{)")
m = pat.search(s)
if m:
    indent = m.group(2)
    block = (
        f"\n{indent}val MIGRATION_7_8: Migration = object : Migration(7, 8) {{\n"
        f"{indent}    override fun migrate(db: SupportSQLiteDatabase) {{\n"
        f"{indent}        // 给「用过的 WiFi」加备注列；旧数据空串（响铃页会回退显示 SSID 本身）\n"
        f"{indent}        db.execSQL(\n"
        f"{indent}            \"ALTER TABLE ssid_history ADD COLUMN alias TEXT NOT NULL DEFAULT ''\"\n"
        f"{indent}        )\n"
        f"{indent}    }}\n"
        f"{indent}}}"
    )
    s = s[:m.start()] + block + s[m.start():]
    print("  ✓ 插入 MIGRATION_7_8")
else:
    print("  ✗ 没找到 MIGRATION_6_7 的声明")

# 注册
if "ReminderDatabase.MIGRATION_7_8," not in s:
    s = s.replace(
        "                    ReminderDatabase.MIGRATION_5_6,\n                )",
        "                    ReminderDatabase.MIGRATION_5_6,\n                    ReminderDatabase.MIGRATION_7_8,\n                )",
        1,
    )
    print("  ✓ 注册 MIGRATION_7_8")
else:
    print("  · 已注册")
open(P, "w", encoding="utf-8", newline="\n").write(s)

# 校验：三个关键点都在
check = open(P, encoding="utf-8").read()
for key in ("version = 8,", "val MIGRATION_7_8", "ReminderDatabase.MIGRATION_7_8,"):
    print(f"  {'✓' if key in check else '✗'} 校验: {key}")
print("完成")
