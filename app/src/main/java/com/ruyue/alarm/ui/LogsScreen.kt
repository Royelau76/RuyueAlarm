package com.ruyue.alarm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ruyue.alarm.data.EventLog
import com.ruyue.alarm.data.EventType
import com.ruyue.alarm.ui.theme.LocalRuyueSemanticColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全部事件日志。排障就靠这一页：
 * 「为什么没提醒」的答案基本都能在这条时间线里找到。
 *
 * 注意：这里不能用 ScreenScaffold（它是 verticalScroll 的），
 * 竖向滚动容器里不能再套 LazyColumn，否则会因为无限高度约束直接崩。
 */
@Composable
fun LogsScreen(vm: M0ViewModel, onBack: () -> Unit) {
    val logs by vm.logs.collectAsStateWithLifecycle()

    // 默认隐藏心跳：2026-11-11 实测 HEARTBEAT 占日志 841/1236（68%），
    // 会把真正要排查的触发/响铃事件冲掉 —— 500 条里只剩一百多条有用信息。
    // 想看心跳时点右上角切回来。
    var importantOnly by remember { mutableStateOf(true) }
    val shown = if (importantOnly) logs.filter { it.type != EventType.HEARTBEAT } else logs
    val hidden = logs.size - shown.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Spacer(Modifier.width(4.dp))
            Text("全部日志", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { importantOnly = !importantOnly }) {
                Text(if (importantOnly) "显示心跳" else "只看重要")
            }
            TextButton(
                onClick = { vm.exportLogs() },
                enabled = logs.isNotEmpty(),
            ) { Text("导出") }
            TextButton(onClick = { vm.clearLogs() }) { Text("清空") }
        }
        Text(
            // 是「显示」不是「保留」—— 更早的日志一直在库里，只是这一页有上限
            text = if (importantOnly) {
                "显示最近 ${shown.size} 条重要事件" +
                    if (hidden > 0) "（已隐藏 $hidden 条心跳）" else ""
            } else {
                "显示最近 ${shown.size} 条（含心跳，更早的仍在库里，导出时会一并带上）"
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        if (shown.isEmpty()) {
            Text("还没有日志。走一遍 ①②③ 就会留下记录。")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(shown, key = { it.id }) { log ->
                    LogRow(log)
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: EventLog) {
    val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = time,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = log.type,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = typeColor(log.type),
                )
            }
            if (!log.ssid.isNullOrBlank()) {
                Text("SSID: ${log.ssid}", fontSize = 13.sp)
            }
            log.detail?.let { Text(it, fontSize = 13.sp) }
        }
    }
}

/** 事件类型配色走语义色 —— 硬编码的红绿在深色背景上几乎看不清 */
@Composable
private fun typeColor(type: String): Color {
    val semantic = LocalRuyueSemanticColors.current
    return when (type) {
        EventType.SERVICE_START, EventType.WIFI_CONNECT, EventType.RING_OPENED -> semantic.success
        EventType.SERVICE_STOP, EventType.ERROR -> semantic.danger
        EventType.ALARM_FIRED, EventType.BOOT -> semantic.info
        else -> semantic.neutral
    }
}
