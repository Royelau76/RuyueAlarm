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
    /**
     * 某个触发条件**刚刚被满足**（WiFi 去抖通过 / 地点复核通过 / 时间到）。
     * 响铃页要回答「为什么现在响」，需要"条件逐个达成的时刻"来画时间线。
     */
    const val COND_MET = "COND_MET"
    /** 用户在响铃页做了什么（知道了 / 稍后 N 分钟）+ 响了多久才处理（详情页「最近三次」用） */
    const val RING_ACK = "RING_ACK"
    const val BOOT = "BOOT"
    const val NOTE = "NOTE"
    const val ERROR = "ERROR"
}
