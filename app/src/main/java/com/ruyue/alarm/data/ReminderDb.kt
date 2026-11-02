package com.ruyue.alarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 提醒数据单独一个库文件。
 *
 * 为什么不并进 `ruyue.db`：那个库是 M0 的**证据日志**（event_logs），已经跑了几轮实测；
 * 往里加表就得升 schema 版本 + 写迁移，迁移写错会让 App 启动即崩，代价太大。
 * 分开之后：
 *   · 日志库永远停在 version 1，实测证据不受影响
 *   · 提醒库自己演进，出问题也只影响提醒数据
 * 语义上也更清楚：一个是可丢弃的诊断日志，一个是不能丢的用户数据。
 */
@Database(
    entities = [Reminder::class, SsidHistory::class, SavedPlace::class, ExpectedFire::class],
    version = 6,
    exportSchema = false,
)
abstract class ReminderDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun ssidHistoryDao(): SsidHistoryDao
    abstract fun savedPlaceDao(): SavedPlaceDao
    abstract fun expectedFireDao(): ExpectedFireDao

    companion object {
        const val NAME = "ruyue_reminders.db"

        /**
         * v1 → v2：加地点触发字段 + SSID 历史表。
         *
         * 用 `ALTER TABLE ADD COLUMN`（带 NOT NULL DEFAULT）而不是重建表：
         * 不丢数据，列定义也最容易和实体对上（Room 会校验 schema，对不上就启动即崩）。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN placeEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminders ADD COLUMN placeName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reminders ADD COLUMN latitude REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminders ADD COLUMN longitude REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminders ADD COLUMN radiusMeters INTEGER NOT NULL DEFAULT 300")
                db.execSQL("ALTER TABLE reminders ADD COLUMN placeDwellSeconds INTEGER NOT NULL DEFAULT 60")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ssid_history (" +
                        "ssid TEXT NOT NULL PRIMARY KEY, lastSeenAt INTEGER NOT NULL)"
                )
            }
        }

        /** v2 → v3：加触发逻辑（或/且）+ 自定义铃声 + 渐强秒数 */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN matchMode TEXT NOT NULL DEFAULT 'ANY'")
                db.execSQL("ALTER TABLE reminders ADD COLUMN ringtoneUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reminders ADD COLUMN volumeRampSeconds INTEGER NOT NULL DEFAULT 15")
            }
        }

        /**
         * v3 → v4：新增「收藏的地点」表。
         *
         * ⚠️ 建表语句必须和 Room 由实体生成的期望 schema **逐字符对得上**（列名/类型/NOT NULL/
         * 主键 AUTOINCREMENT 都一样），否则 Room 校验失败 → **App 启动即崩**。
         * 这条 SQL 是对着构建产物里 `ReminderDatabase_Impl` 的 `createAllTables` 抄下来的，
         * 并且用离线 sqlite 跑过「v3 建库 → 插数据 → 迁移 → 与期望 schema 逐列比对」。
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_places` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`latitude` REAL NOT NULL, " +
                        "`longitude` REAL NOT NULL, " +
                        "`radiusMeters` INTEGER NOT NULL, " +
                        "`address` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`lastUsedAt` INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v4 → v5：新增「预期触发台账」表（自检对账用）。
         *
         * 同样：建表 + 建索引语句必须与 Room 由实体生成的期望 schema 逐字符一致，
         * 否则启动即崩。用 `tools/verify_migration_v4_v5.py` 离线验过。
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `expected_fires` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`reminderId` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`expectedAt` INTEGER NOT NULL, " +
                        "`state` TEXT NOT NULL, " +
                        "`settledAt` INTEGER NOT NULL, " +
                        "`reason` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_expected_fires_reminderId_expectedAt` " +
                        "ON `expected_fires` (`reminderId`, `expectedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_expected_fires_expectedAt` " +
                        "ON `expected_fires` (`expectedAt`)"
                )
            }
        }

        /**
         * v5 → v6：「仅一次」支持日历选点，加一列 `onceDate`（那天 00:00 的本地毫秒，0 = 未指定）。
         *
         * 沿用 v1→v2 / v2→v3 的写法：`ALTER TABLE ADD COLUMN ... NOT NULL DEFAULT`，
         * 不重建表、不丢数据。老提醒这一列是 0，行为与以前完全一致（按"下一个 hh:mm"算）。
         * 用 `tools/verify_migration_v5_v6.py` 离线验过。
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN onceDate INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}

object ReminderDb {
    @Volatile
    private var instance: ReminderDatabase? = null

    fun get(context: Context): ReminderDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ReminderDatabase::class.java,
                ReminderDatabase.NAME,
            )
                .addMigrations(
                    ReminderDatabase.MIGRATION_1_2,
                    ReminderDatabase.MIGRATION_2_3,
                    ReminderDatabase.MIGRATION_3_4,
                    ReminderDatabase.MIGRATION_4_5,
                    ReminderDatabase.MIGRATION_5_6,
                )
                .build()
                .also { instance = it }
        }
}
