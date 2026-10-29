package com.ruyue.alarm.util

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import com.ruyue.alarm.BuildConfig
import com.ruyue.alarm.data.ReminderDatabase
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 升级前的数据备份。
 *
 * ## 为什么"升级前"这件事比想象中麻烦
 *
 * Room 的迁移是在**第一次访问数据库时**跑掉的（`databaseBuilder().build()` 内部就会校验并迁移）。
 * 等我们的代码有机会执行时，库**已经被迁移过了** —— 那时再导出，导出的是"新版可能已经坏掉的库"，
 * 作为回滚依据毫无意义。
 *
 * 所以只能在 **`Application.onCreate` 里、任何数据库访问之前**做事，并且
 * **不能用 Room 打开库**（用 Room 打开就等于自己触发了迁移）——
 * 这里用裸 `SQLiteDatabase.openDatabase(..., OPEN_READONLY)` 直接读文件。
 *
 * ## 触发条件
 *
 * 比对 `prefs.lastVersionCode` 与 `BuildConfig.VERSION_CODE`：
 *  · 不同 → 说明这次是新版本第一次启动（或第一次装这段代码）→ 先备份再继续
 *  · 相同 → 什么都不做
 *
 * 注意首次安装时 `lastVersionCode` 也是 0、库还不存在 → `backupNow` 返回 null，
 * 只是把版本号记下来（无害）。
 *
 * ## 备份什么
 *
 * 只备**不可重建**的用户数据：`reminders` / `saved_places` / `ssid_history`。
 * 日志库 `ruyue.db` 是消耗性的诊断证据，不备（备了只会撑大占用）。
 */
object Backup {

    private const val TAG = "RuyueBackup"
    private const val PREF = "ruyue_backup"
    private const val KEY_LAST_VERSION = "last_version_code"
    private const val DIR_NAME = "backups"

    /** 最多留 5 份：够回滚到近几个版本，又不会无限增长 */
    private const val MAX_KEEP = 5

    /** 不可重建的用户数据；日志库刻意不在此列 */
    private val TABLES = listOf("reminders", "saved_places", "ssid_history")

    /**
     * 在 `RuyueApp.onCreate` 里**第一件事**调用 —— 必须早于任何 Room 访问。
     */
    fun maybeBackupOnUpgrade(context: Context) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val last = prefs.getInt(KEY_LAST_VERSION, 0)
        val current = BuildConfig.VERSION_CODE
        if (last == current) return

        runCatching { backupNow(context) }
            .onSuccess { file ->
                if (file != null) Log.i(TAG, "升级前备份完成：${file.name}")
            }
            .onFailure { Log.w(TAG, "升级前备份失败：${it.message}") }

        // 无论备份成功与否都记版本号：备份失败不应该让每次启动都重试一遍
        prefs.edit().putInt(KEY_LAST_VERSION, current).apply()
    }

    /**
     * 立刻备份一份。没有库文件（全新安装）时返回 null。
     */
    fun backupNow(context: Context): File? {
        val dbFile = context.getDatabasePath(ReminderDatabase.NAME)
        if (!dbFile.exists()) return null

        val json = runCatching { dumpJson(dbFile) }
            .onFailure { Log.w(TAG, "读取数据库失败：${it.message}") }
            .getOrNull() ?: return null

        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        // 文件名把时间放前面，这样按名字排序 == 按时间排序（版本号放最后仅供阅读）
        val out = File(dir, "reminders-$stamp-v${BuildConfig.VERSION_CODE}.json")
        return runCatching {
            out.writeText(json)
            rotate(dir)
            out
        }.onFailure { Log.w(TAG, "写备份文件失败：${it.message}") }.getOrNull()
    }

    /** 备份文件列表，**新的在前** */
    fun list(context: Context): List<File> =
        File(context.filesDir, DIR_NAME)
            .listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    fun latest(context: Context): File? = list(context).firstOrNull()

    /** 设置页那一行的小结，例如「最近备份 2026-10-29 14:20 · v13 · 共 3 份」 */
    fun summary(context: Context): String {
        val all = list(context)
        val last = all.firstOrNull() ?: return "还没有备份（升级前会自动生成）"
        // reminders-20261029-142000-v13.json
        val name = last.name
        val at = runCatching {
            val s = name.substringAfter("reminders-").substringBefore("-v")
            val d = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).parse(s)
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(d!!)
        }.getOrNull() ?: "时间未知"
        val v = name.substringAfter("-v", "").substringBefore(".json")
        return "最近备份 $at · v$v · 共 ${all.size} 份（最多留 $MAX_KEEP 份）"
    }

    /** 分享某份备份（走 FileProvider，不申请存储权限） */
    fun share(context: Context, file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "如约数据备份 ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "分享数据备份").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrElse { false }

    // ---------------- 内部 ----------------

    private fun rotate(dir: File) {
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.name }
            ?.drop(MAX_KEEP)
            ?.forEach { runCatching { it.delete() } }
    }

    /**
     * 裸 SQLite 打开、直接把三张表 dump 成 JSON。
     *
     * **绝不能换成 `ReminderDb.get()`** —— 那会触发 Room 的迁移校验，
     * 等于"备份动作本身把库升到了新版本"，备份也就失去了"迁移前快照"的意义。
     * 裸 SQLite 只做 SELECT，不存在迁移这回事。
     *
     * ⚠️ 为什么优先 `OPEN_READWRITE` 而不是 `OPEN_READONLY`：
     * Room 默认启用 **WAL** 模式，读一个 WAL 库需要能创建/写 `-shm` 文件；
     * 用 `OPEN_READONLY` 打开时 SQLite 可能直接报 `attempt to write a readonly database`。
     * 读写打开只是"允许写"，我们仍然只执行 SELECT。真打不开再退回只读兜一次。
     */
    private fun openDb(dbFile: File): SQLiteDatabase {
        runCatching {
            return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
        }
        return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    private fun dumpJson(dbFile: File): String {
        val db = openDb(dbFile)
        try {
            val root = JSONObject()
            root.put("app", "RuyueAlarm")
            root.put("appVersion", BuildConfig.VERSION_NAME)
            root.put("appVersionCode", BuildConfig.VERSION_CODE)
            // 记录"备份这一瞬间库的 schema 版本"：下次迁移出问题时，能看出这份备份是什么结构
            root.put("schemaVersion", db.version)
            root.put("exportedAt", System.currentTimeMillis())
            root.put(
                "exportedAtText",
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            )
            val tables = JSONObject()
            TABLES.forEach { table ->
                if (hasTable(db, table)) tables.put(table, dumpTable(db, table))
            }
            root.put("tables", tables)
            return root.toString(2)
        } finally {
            runCatching { db.close() }
        }
    }

    private fun hasTable(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table),
        ).use { it.moveToFirst() }

    private fun dumpTable(db: SQLiteDatabase, table: String): org.json.JSONArray {
        val rows = org.json.JSONArray()
        db.rawQuery("SELECT * FROM `$table`", null).use { c ->
            while (c.moveToNext()) {
                val row = JSONObject()
                for (i in 0 until c.columnCount) {
                    val col = c.getColumnName(i)
                    row.put(
                        col,
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                            Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                            Cursor.FIELD_TYPE_STRING -> c.getString(i)
                            // BLOB 目前用不到（三个表都是标量），但别留幻觉：真的遇到就带出来
                            Cursor.FIELD_TYPE_BLOB -> Base64.encodeToString(c.getBlob(i), Base64.NO_WRAP)
                            else -> JSONObject.NULL
                        },
                    )
                }
                rows.put(row)
            }
        }
        return rows
    }
}
