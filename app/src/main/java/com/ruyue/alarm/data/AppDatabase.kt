package com.ruyue.alarm.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [EventLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventLogDao(): EventLogDao

    companion object {
        const val NAME = "ruyue.db"
    }
}

/**
 * 单一数据库实例。
 *
 * 为什么不全靠 Hilt：BroadcastReceiver 里落日志最可靠的方式是直接拿 DAO，
 * 而给 Receiver 做 Hilt 注入会引入额外的编译期风险。这里用同一份单例，
 * Hilt 的 AppModule 也从这个入口取库，保证全进程只有一个 Room 实例。
 */
object Db {
    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: android.content.Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                AppDatabase.NAME,
            ).build().also { instance = it }
        }
}

/** 给 BroadcastReceiver 用的落库入口（开线程，不阻塞 onReceive）。 */
object EventWriter {
    fun write(
        context: android.content.Context,
        type: String,
        ssid: String? = null,
        bssid: String? = null,
        detail: String? = null,
        onDone: (() -> Unit)? = null,
    ) {
        val appContext = context.applicationContext
        Thread {
            runCatching {
                Db.get(appContext).eventLogDao().insertBlocking(
                    EventLog(type = type, ssid = ssid, bssid = bssid, detail = detail)
                )
            }
            onDone?.invoke()
        }.start()
    }
}
