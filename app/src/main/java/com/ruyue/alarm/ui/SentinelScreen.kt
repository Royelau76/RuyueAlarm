package com.ruyue.alarm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

/**
 * M0 第 2 项：前台服务到底会不会被杀。
 *
 * 判读方法（这是关键）：点「启动哨兵」→ 按电源键锁屏 → 正常用手机 8 小时以上 →
 * 回来看这一页和「全部日志」：
 *   · 心跳每 5 分钟一条、连续不断 → 保活有效
 *   · 心跳断档、或出现 SERVICE_STOP 且之后没有 SERVICE_START → 被系统/ROM 杀了
 */
@Composable
fun SentinelScreen(vm: M0ViewModel, onBack: () -> Unit) {
    val running by vm.serviceRunning.collectAsStateWithLifecycle()
    val startedAt by vm.serviceStartedAt.collectAsStateWithLifecycle()
    val eventCount by vm.serviceEventCount.collectAsStateWithLifecycle()
    val lastEvent by vm.serviceLastEvent.collectAsStateWithLifecycle()
    val alarmState by vm.alarmState.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    ScreenScaffold("② 前台服务保活", onBack) {

        SectionCard("服务状态") {
            KV("是否运行", if (running) "运行中" else "已停止", running)
            KV("已运行", if (running && startedAt > 0) formatDuration(now - startedAt) else "—")
            KV("WiFi 事件数", eventCount.toString())
            KV("最后事件", lastEvent)
            KV("用户期望状态", if (vm.sentinelWanted()) "开机后自动拉起" else "不自动拉起")
            KV(
                "看门狗精确闹钟",
                if (alarmState.canScheduleExact) "已授权（被杀后能精确自愈）"
                else "未授权（自愈只能尽力而为，建议去 ③ 页授权）",
                alarmState.canScheduleExact,
            )
        }

        SectionCard("操作") {
            Button(
                onClick = { vm.startSentinel() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !running,
            ) { Text("启动哨兵") }

            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { vm.stopSentinel() },
                modifier = Modifier.fillMaxWidth(),
                enabled = running,
            ) { Text("停止哨兵") }

            Hint(
                "启动后请直接按电源键锁屏，然后正常用手机。要验证「重启手机也能自动起来」，\n" +
                    "重启后回这一页看是否自动变成「运行中」。"
            )
        }

        SectionCard("保活验收标准") {
            Hint(
                "✅ 通过：锁屏 8 小时后心跳仍然每 5 分钟一条，且重启手机后服务自动恢复\n" +
                    "❌ 不通过：心跳断档 / 出现 SERVICE_STOP 后没有 SERVICE_START / 重启后没起来\n\n" +
                    "不通过时按顺序检查：⑥ 里的 HyperOS 设置 → 通知权限 → 电池优化白名单。"
            )
        }
    }
}

internal fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分"
}
