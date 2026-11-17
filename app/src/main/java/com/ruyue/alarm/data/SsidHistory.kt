package com.ruyue.alarm.data

import androidx.room.ColumnInfo
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
    /**
     * 用户给这个网络起的备注（如「家里网」「公司」）。
     * 响铃页优先显示备注 —— TP-LINK_1602_5G 这种名字又长又难认（2026-11-16 加入）。
     */
    @ColumnInfo(defaultValue = "") val alias: String = "",
)

@Dao
interface SsidHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SsidHistory)

    @Query("SELECT * FROM ssid_history ORDER BY lastSeenAt DESC LIMIT 50")
    fun observeAll(): Flow<List<SsidHistory>>

    @Query("DELETE FROM ssid_history WHERE ssid = :ssid")
    suspend fun forget(ssid: String)

    /** 只改备注，不动 lastSeenAt（否则改个备注会把它顶到列表最前面） */
    @Query("UPDATE ssid_history SET alias = :alias WHERE ssid = :ssid")
    suspend fun updateAlias(ssid: String, alias: String)

    @Query("SELECT alias FROM ssid_history WHERE ssid = :ssid")
    suspend fun aliasOf(ssid: String): String?
}
