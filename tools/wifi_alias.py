# -*- coding: utf-8 -*-
"""WiFi 备注：数据库 v7→v8 + 编辑页可改备注 + 响铃页优先显示备注。"""
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


# 1) 实体 + DAO
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

# 2) 数据库 v7 → v8
P = os.path.join(AK, "data/ReminderDb.kt")
s = open(P, encoding="utf-8").read()
s = re.sub(r"version = 7,", "version = 8,", s, count=1)
s = s.replace(
    """    val MIGRATION_6_7 = object : Migration(6, 7) {""",
    """    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 给用过的 WiFi 加备注列；旧数据空串（响铃页会回退显示 SSID 本身）
            db.execSQL("ALTER TABLE ssid_history ADD COLUMN alias TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {""",
    1,
)
if "ReminderDatabase.MIGRATION_7_8," not in s:
    s = s.replace(
        "                    ReminderDatabase.MIGRATION_6_7,\n                )",
        "                    ReminderDatabase.MIGRATION_6_7,\n                    ReminderDatabase.MIGRATION_7_8,\n                )",
        1,
    )
open(P, "w", encoding="utf-8", newline="\n").write(s)
print("  ✓ 数据库 v8 + MIGRATION_7_8 + 注册")

# 3) 编辑页：备注按钮 + 弹窗
patch("ui/ReminderEditScreen.kt", [(
    """    // 删除前二次确认：底部「删除」现在是常驻按钮，误触就再也找不回这条提醒了
    var confirmDelete by remember { mutableStateOf(false) }""",
    """    // 删除前二次确认：底部「删除」现在是常驻按钮，误触就再也找不回这条提醒了
    var confirmDelete by remember { mutableStateOf(false) }

    // 给「用过的网络」起备注（响铃页会显示这个短名字）
    var aliasEditFor by remember { mutableStateOf<String?>(null) }
    var aliasDraft by remember { mutableStateOf("") }
    val aliasScope = rememberCoroutineScope()""",
    "加备注状态",
), (
    """                // 用过的 WiFi：自动记录，点一下就能加入，不用每次手输
                val unused = history.map { it.ssid }.filter { it !in draft.ssidList }
                if (unused.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("用过的网络（点「加入」即可）：", fontWeight = FontWeight.Medium)
                    Hint("哨兵每次连上 WiFi 都会自动记下来，换机/重装前一直有效。")
                    unused.take(8).forEach { ssid ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ssid, Modifier.weight(1f), fontSize = 14.sp)
                            TextButton(onClick = { vm.addSsid(ssid) }) { Text("加入") }
                            TextButton(onClick = { vm.forgetSsid(ssid) }) { Text("移除记录") }
                        }
                    }
                }""",
    """                // 用过的 WiFi：自动记录；「备注」给它起短名字（响铃页优先显示备注）
                val unused = history.filter { it.ssid !in draft.ssidList }
                if (unused.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("用过的网络：", fontWeight = FontWeight.Medium)
                    unused.take(8).forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.ssid, fontSize = 14.sp)
                                if (item.alias.isNotBlank()) {
                                    Text(
                                        "备注：${item.alias}",
                                        fontSize = 12.sp,
                                        color = ruyue.textTertiary,
                                    )
                                }
                            }
                            TextButton(onClick = { vm.addSsid(item.ssid) }) { Text("加入") }
                            TextButton(onClick = {
                                aliasDraft = item.alias
                                aliasEditFor = item.ssid
                            }) { Text("备注") }
                        }
                    }
                }""",
    "用过的网络加备注按钮",
)])

# 弹窗（放在 composable 末尾前，用状态驱动）
patch("ui/ReminderEditScreen.kt", [(
    """    val context = LocalContext.current
    val ruyue = LocalRuyueColors.current""",
    """    val context = LocalContext.current
    val ruyue = LocalRuyueColors.current
    aliasEditFor?.let { target ->
        AlertDialog(
            onDismissRequest = { aliasEditFor = null },
            title = { Text("给这个网络起个备注") },
            text = {
                Column {
                    Text(target, fontSize = 12.sp, color = ruyue.textTertiary)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = aliasDraft,
                        onValueChange = { aliasDraft = it },
                        singleLine = true,
                        placeholder = { Text("例如：家里网 / 公司") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = target
                    val a = aliasDraft.trim()
                    aliasEditFor = null
                    aliasScope.launch {
                        runCatching {
                            ReminderDb.get(context).ssidHistoryDao().updateAlias(t, a)
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { aliasEditFor = null }) { Text("取消") }
            },
        )
    }""",
    "备注弹窗",
)],)

# import 补齐
p = os.path.join(AK, "ui/ReminderEditScreen.kt")
s = open(p, encoding="utf-8").read()
need = {
    "import androidx.compose.material3.AlertDialog": None,
    "import androidx.compose.material3.OutlinedTextField": None,
    "import androidx.compose.runtime.rememberCoroutineScope": None,
    "import kotlinx.coroutines.launch": None,
    "import com.ruyue.alarm.data.ReminderDb": None,
}
lines = s.split("\n")
last = max(i for i, l in enumerate(lines) if l.startswith("import "))
missing = [k for k in need if k not in s]
for imp in reversed(missing):
    lines.insert(last + 1, imp)
if missing:
    s = "\n".join(lines)
    open(p, "w", encoding="utf-8", newline="\n").write(s)
    print("  ✓ 补 import:", len(missing), "个")

# 4) 响铃页：优先显示备注
patch("alarm/RingActivity.kt", [(
    """                    sourceLabel = when {
                        reminder == null -> null
                        reminder!!.placeEnabled -> "到达 " + reminder!!.placeName.ifBlank { "地点" }
                        reminder!!.wifiEnabled ->
                            "连上 " + (reminder!!.ssidList.firstOrNull() ?: "WiFi")
                        else -> null
                    },""",
    """                    sourceLabel = when {
                        reminder == null -> null
                        reminder!!.placeEnabled -> "到达 " + reminder!!.placeName.ifBlank { "地点" }
                        reminder!!.wifiEnabled -> {
                            // 有备注就显示备注（「连上 家里网」），没有才显示 SSID
                            val ssid = reminder!!.ssidList.firstOrNull()
                            val alias = ssid?.let {
                                runCatching {
                                    ReminderDb.get(this@RingActivity).ssidHistoryDao().aliasOf(it)
                                }.getOrNull()
                            }
                            "连上 " + (alias?.takeIf { a -> a.isNotBlank() } ?: ssid ?: "WiFi")
                        }
                        else -> null
                    },""",
    "响铃页优先显示备注",
)])
print("完成")
