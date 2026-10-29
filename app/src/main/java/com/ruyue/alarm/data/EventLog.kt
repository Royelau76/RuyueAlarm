package com.ruyue.alarm.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条事件日志。这是 M0 阶段最重要的东西：
 * 服务有没有被杀、闹钟有没有准时响、WiFi 一天连断几次，全靠这张表说话。
 */
@Entity(tableName = "event_logs", indices = [Index("timestamp")])
data class EventLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 见 [EventType] */
    val type: String,
    val ssid: String? = null,
    val bssid: String? = null,
    val detail: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

object EventType {
    const val SERVICE_START = "SERVICE_START"
    const val SERVICE_STOP = "SERVICE_STOP"
    const val HEARTBEAT = "HEARTBEAT"
    const val WIFI_CONNECT = "WIFI_CONNECT"
    const val WIFI_DISCONNECT = "WIFI_DISCONNECT"
    const val ALARM_FIRED = "ALARM_FIRED"
    const val RING_OPENED = "RING_OPENED"
    const val BOOT = "BOOT"
    const val NOTE = "NOTE"
    const val ERROR = "ERROR"
}
