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

    /**
     * 高德围栏的进出事件（M0 第 4 项实验）。
     *
     * 单独一个类型而不是混进 NOTE，是为了**能和自研围栏的「接近/离开」直接对着比时间差** ——
     * 这正是这次实验唯一要回答的问题（延迟差多少）。
     */
    const val AMAP_FENCE = "AMAP_FENCE"
    const val NOTE = "NOTE"
    const val ERROR = "ERROR"
}
