package com.ruyue.alarm.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 用过的 WiFi 记录。
 *
 * 目的：配「到地点提醒」时不用每次手输 SSID —— 手机连过的网络自动进这张表，
 * 编辑页直接点选就行。哨兵服务每次收到 WiFi 连接事件时会顺手记一条。
 *
 * 只存 SSID 不存 BSSID：SSID 才是用户认识的名字，而 BSSID 在 mesh / 多 AP 环境下会变。
 */
@Entity(tableName = "ssid_history")
data class SsidHistory(
    @PrimaryKey val ssid: String,
    val lastSeenAt: Long = System.currentTimeMillis(),
)

@Dao
interface SsidHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SsidHistory)

    @Query("SELECT * FROM ssid_history ORDER BY lastSeenAt DESC LIMIT 50")
    fun observeAll(): Flow<List<SsidHistory>>

    @Query("DELETE FROM ssid_history WHERE ssid = :ssid")
    suspend fun forget(ssid: String)
}
