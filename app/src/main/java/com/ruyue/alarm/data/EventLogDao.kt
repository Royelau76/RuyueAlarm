package com.ruyue.alarm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventLogDao {

    @Insert
    suspend fun insert(log: EventLog): Long

    /** 给 BroadcastReceiver 用的阻塞版（那边没有协程作用域） */
    @Insert
    fun insertBlocking(log: EventLog): Long

    @Query("SELECT * FROM event_logs ORDER BY timestamp DESC LIMIT :limit")
    fun recent(limit: Int = 2000): Flow<List<EventLog>>

    @Query("SELECT * FROM event_logs WHERE type = :type ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastOfType(type: String): EventLog?

    @Query("SELECT * FROM event_logs WHERE type = :type AND timestamp >= :since ORDER BY timestamp ASC")
    suspend fun listByTypeSince(type: String, since: Long): List<EventLog>

    /** 按类型集合按时间正序取（响铃页画"条件达成时间线"用） */
    @Query("SELECT * FROM event_logs WHERE timestamp >= :since AND type IN (:types) ORDER BY timestamp ASC")
    suspend fun listByTypesSince(types: List<String>, since: Long): List<EventLog>

    @Query(
        """SELECT * FROM event_logs
           WHERE timestamp >= :since AND type IN ('WIFI_CONNECT', 'WIFI_DISCONNECT')
           ORDER BY timestamp ASC"""
    )
    suspend fun wifiEventsSince(since: Long): List<EventLog>

    @Query("SELECT COUNT(*) FROM event_logs")
    suspend fun count(): Int

    @Query("DELETE FROM event_logs")
    suspend fun clear()
}
