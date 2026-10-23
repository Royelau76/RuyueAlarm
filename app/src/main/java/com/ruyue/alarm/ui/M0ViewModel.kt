package com.ruyue.alarm.ui

import android.Manifest
import android.content.Context
import com.ruyue.alarm.alarm.AlarmScheduler
import com.ruyue.alarm.data.EventLog
import com.ruyue.alarm.data.EventType
import com.ruyue.alarm.data.LogRepository
import com.ruyue.alarm.sentinel.SentinelController
import com.ruyue.alarm.sentinel.SentinelService
import com.ruyue.alarm.sentinel.SentinelWatchdog
import com.ruyue.alarm.sentinel.ServiceState
import com.ruyue.alarm.util.LogExporter
import com.ruyue.alarm.util.WifiReader
import com.ruyue.alarm.util.WifiSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

data class AlarmUiState(
    val canScheduleExact: Boolean = false,
    val canFullScreen: Boolean = true,
    val nextTriggerAt: Long? = null,
)

data class SsidStat(val ssid: String, val connects: Int, val sessionMinutes: Double)

data class DebounceStats(
    val windowHours: Int,
    val connects: Int,
    val disconnects: Int,
    val bySsid: List<SsidStat>,
    val heartbeatCount: Int,
    val maxHeartbeatGapSec: Long,
    val serviceStops: Int,
    val advice: String,
)

@HiltViewModel
class M0ViewModel @Inject constructor(
    private val repository: LogRepository,
    @ApplicationContext private val app: Context,
) : ViewModel() {

    val logs: StateFlow<List<EventLog>> = repository.recent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val trackedPermissions = listOf(
        Manifest.permission.NEARBY_WIFI_DEVICES,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    private val _grants = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val grants: StateFlow<Map<String, Boolean>> = _grants.asStateFlow()

    private val _probe = MutableStateFlow<WifiSnapshot?>(null)
    val probe: StateFlow<WifiSnapshot?> = _probe.asStateFlow()

    private val _alarmState = MutableStateFlow(AlarmUiState())
    val alarmState: StateFlow<AlarmUiState> = _alarmState.asStateFlow()

    private val _stats = MutableStateFlow<DebounceStats?>(null)
    val stats: StateFlow<DebounceStats?> = _stats.asStateFlow()

    val serviceRunning: StateFlow<Boolean> = ServiceState.running.asStateFlow()
    val serviceStartedAt: StateFlow<Long> = ServiceState.startedAt.asStateFlow()
    val serviceEventCount: StateFlow<Int> = ServiceState.eventCount.asStateFlow()
    val serviceLastEvent: StateFlow<String> = ServiceState.lastEvent.asStateFlow()

    fun refreshAll() {
        refreshGrants()
        refreshAlarmState()
        probeWifi(logIt = false)
        // 每次打开 App 都把自愈链续上：闹钟可能因为强停/清理而丢失，这里是最后一道保险
        if (SentinelService.isWanted(app)) {
            SentinelWatchdog.schedule(app)
        }
    }

    fun refreshGrants() {
        _grants.value = trackedPermissions.associateWith { WifiReader.granted(app, it) }
    }

    fun trackedPermissionList(): List<String> = trackedPermissions

    fun probeWifi(logIt: Boolean = true, note: String? = null) {
        val snap = WifiReader.snapshot(app)
        _probe.value = snap
        if (!logIt) return
        viewModelScope.launch {
            repository.log(
                EventType.NOTE,
                ssid = snap.ssid,
                bssid = snap.bssid,
                detail = buildString {
                    append("WiFi 探测：")
                    append(if (snap.connected) "已连接" else "未连接 WiFi")
                    append("；主路径=").append(snap.capsSsid ?: "抹除")
                    append("；备用路径=").append(snap.legacySsid ?: "抹除")
                    append("；定位总开关=").append(if (snap.locationEnabled) "开" else "关")
                    snap.error?.let { append("；$it") }
                    note?.let { append("；$it") }
                },
            )
        }
    }

    fun startSentinel() = SentinelController.start(app)

    fun stopSentinel() = SentinelController.stop(app)

    fun sentinelWanted(): Boolean = SentinelService.isWanted(app)

    fun refreshAlarmState() {
        _alarmState.value = AlarmUiState(
            canScheduleExact = AlarmScheduler.canScheduleExact(app),
            canFullScreen = AlarmScheduler.canUseFullScreenIntent(app),
            nextTriggerAt = AlarmScheduler.nextTriggerTime(app),
        )
    }

    fun scheduleAlarm(seconds: Long) {
        val at = System.currentTimeMillis() + seconds * 1000L
        AlarmScheduler.scheduleAlarmClock(app, at, "M0 测试（${seconds} 秒后）")
        viewModelScope.launch {
            repository.log(EventType.NOTE, detail = "已排定闹钟：${seconds} 秒后（可锁屏等待）")
        }
        refreshAlarmState()
    }

    fun cancelAlarm() {
        AlarmScheduler.cancel(app)
        refreshAlarmState()
    }

    fun loadStats(windowHours: Int = 24) {
        viewModelScope.launch {
            val since = System.currentTimeMillis() - windowHours * 3_600_000L
            _stats.value = buildStats(
                windowHours = windowHours,
                wifi = repository.wifiEventsSince(since),
                beats = repository.listByTypeSince(EventType.HEARTBEAT, since),
                stops = repository.listByTypeSince(EventType.SERVICE_STOP, since),
            )
        }
    }

    fun clearLogs() {
        viewModelScope.launch { repository.clear() }
    }

    /** 导出运行日志并调起系统分享 —— 以后排障不用再一张张截图 */
    fun exportLogs() {
        val current = logs.value
        runCatching { LogExporter.share(app, current) }
            .onFailure { e ->
                viewModelScope.launch {
                    repository.log(EventType.ERROR, detail = "日志导出失败：${e.message}")
                }
            }
    }

    private fun buildStats(
        windowHours: Int,
        wifi: List<EventLog>,
        beats: List<EventLog>,
        stops: List<EventLog>,
    ): DebounceStats {
        var connects = 0
        var disconnects = 0
        val connectCount = mutableMapOf<String, Int>()
        val sessionMinutes = mutableMapOf<String, Double>()
        val openSince = mutableMapOf<String, Long>()

        for (event in wifi) {
            when (event.type) {
                EventType.WIFI_CONNECT -> {
                    connects++
                    val ssid = event.ssid ?: "(未知 SSID)"
                    connectCount[ssid] = (connectCount[ssid] ?: 0) + 1
                    openSince[ssid] = event.timestamp
                }
                EventType.WIFI_DISCONNECT -> {
                    disconnects++
                    val ssid = event.ssid ?: "(未知 SSID)"
                    openSince.remove(ssid)?.let { start ->
                        sessionMinutes[ssid] =
                            (sessionMinutes[ssid] ?: 0.0) + (event.timestamp - start) / 60_000.0
                    }
                }
            }
        }

        val now = System.currentTimeMillis()
        openSince.forEach { (ssid, start) ->
            sessionMinutes[ssid] = (sessionMinutes[ssid] ?: 0.0) + (now - start) / 60_000.0
        }

        val bySsid = connectCount
            .map { (ssid, count) -> SsidStat(ssid, count, sessionMinutes[ssid] ?: 0.0) }
            .sortedByDescending { it.connects }

        var maxGap = 0L
        for (i in 1 until beats.size) {
            val gap = (beats[i].timestamp - beats[i - 1].timestamp) / 1000
            if (gap > maxGap) maxGap = gap
        }

        val advice = buildString {
            append("窗口 $windowHours 小时内：WiFi 连上 $connects 次、断开 $disconnects 次。")
            if (connects >= 10) {
                append("连断频繁 → 冷却规则建议「同一条提醒每天只触发一次」，连接确认时间给到 30 秒以上。")
            } else if (connects > 0) {
                append("连断不频繁 → 去抖确认时间 10~15 秒就够。")
            } else {
                append("还没有 WiFi 事件，先把哨兵开着，正常用一天再回来看。")
            }
            // 关键区分：长时间没有心跳 ≠ 服务被杀。设备深度睡眠时连定时器都不会跑，
            // 只有出现 SERVICE_STOP 才说明它真的死过。这两者修法完全不同，不能混着说。
            if (maxGap > 600 && stops.isEmpty()) {
                append(" 心跳最大间隔 ${maxGap}s：**服务没死**（期间没有任何 SERVICE_STOP），")
                append("那段时间是设备在深度睡眠、系统把定时器推迟了，属正常现象。")
            }
            if (stops.isNotEmpty()) {
                append(" 心跳最大间隔 ${maxGap}s，且期间出现过 ${stops.size} 次 SERVICE_STOP")
                append(" —— 服务确实被杀过。")
            }
        }

        return DebounceStats(
            windowHours = windowHours,
            connects = connects,
            disconnects = disconnects,
            bySsid = bySsid,
            heartbeatCount = beats.size,
            maxHeartbeatGapSec = maxGap,
            serviceStops = stops.size,
            advice = advice,
        )
    }
}
