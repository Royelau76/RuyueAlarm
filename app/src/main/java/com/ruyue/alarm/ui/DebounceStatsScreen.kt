package com.ruyue.alarm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * M0 第 5 项：用一天的实跑数据决定「去抖」参数。
 *
 * 这里的数据全部来自哨兵服务写下的日志，所以前提是第 2 项已经开着跑了一段时间。
 */
@Composable
fun DebounceStatsScreen(vm: M0ViewModel, onBack: () -> Unit) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    var window by remember { mutableIntStateOf(24) }

    LaunchedEffect(window) { vm.loadStats(window) }

    ScreenScaffold("⑤ 连断统计与去抖", onBack) {

        SectionCard("统计窗口") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetButton("近 24 小时", window == 24) { window = 24 }
                PresetButton("近 72 小时", window == 72) { window = 72 }
            }
        }

        val s = stats
        if (s == null) {
            SectionCard("统计结果") { Text("统计中…") }
        } else {
            SectionCard("总体") {
                KV("WiFi 连上次数", "${s.connects} 次")
                KV("WiFi 断开次数", "${s.disconnects} 次")
                KV("心跳条数", "${s.heartbeatCount} 条（每 5 分钟一条为正常）")
                KV(
                    "心跳最大间隔",
                    "${s.maxHeartbeatGapSec} 秒" + if (s.maxHeartbeatGapSec > 600) "（有断档）" else "",
                    s.maxHeartbeatGapSec in 1..600,
                )
                KV(
                    "服务停止次数",
                    "${s.serviceStops} 次",
                    if (s.serviceStops == 0) true else false,
                )
            }

            if (s.bySsid.isNotEmpty()) {
                SectionCard("按 SSID 分组") {
                    s.bySsid.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                        ) {
                            Text(
                                text = item.ssid,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Medium,
                            )
                            Text("连 ${item.connects} 次 · 共 ${item.sessionMinutes.toInt()} 分钟")
                        }
                    }
                }
            }

            SectionCard("建议参数") {
                Text(s.advice)
                Hint("把这两个参数定了：① 连上 WiFi 后确认多少秒才认为「到达」② 同一条提醒多久内只触发一次。")
            }
        }
    }
}
