package com.ruyue.alarm.sentinel

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** 哨兵服务的实时状态，供 UI 观察（历史证据以数据库里的日志为准）。 */
object ServiceState {
    val running = MutableStateFlow(false)
    val startedAt = MutableStateFlow(0L)
    val eventCount = MutableStateFlow(0)
    val lastEvent = MutableStateFlow("—")

    const val HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000L
}

object SentinelController {

    fun start(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, SentinelService::class.java),
        )
    }

    fun stop(context: Context) {
        context.startService(
            Intent(context, SentinelService::class.java)
                .setAction(SentinelService.ACTION_STOP)
        )
    }

    /**
     * 提醒配置变化后调用，让服务重新评估「要不要开地点监听」。
     * 只在用户本来就开启了哨兵时才发（否则会违背用户「已停止」的意图）。
     */
    fun refresh(context: Context) {
        if (!SentinelService.isWanted(context)) return
        runCatching {
            context.startService(
                Intent(context, SentinelService::class.java)
                    .setAction(SentinelService.ACTION_REFRESH)
            )
        }
    }
}
