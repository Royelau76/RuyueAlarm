package com.ruyue.alarm.ui

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ruyue.alarm.Route

@Composable
fun HomeScreen(vm: M0ViewModel, onOpen: (Route) -> Unit) {
    val grants by vm.grants.collectAsStateWithLifecycle()
    val probe by vm.probe.collectAsStateWithLifecycle()
    val running by vm.serviceRunning.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("如约 · M0 验证台", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Hint(
            "这个页面只有一个目的：把「到底会不会响」验证死。\n" +
                "建议顺序：先做 ①②③，再做 ⑥ 的 HyperOS 设置，最后开一整天哨兵看 ⑤ 的统计。"
        )
        Spacer(Modifier.height(10.dp))

        SectionCard("当前状态") {
            KV("App 版本", com.ruyue.alarm.BuildConfig.VERSION_NAME)
            KV(
                "WiFi 权限",
                if (grants[Manifest.permission.NEARBY_WIFI_DEVICES] == true) "已授予" else "未授予",
                grants[Manifest.permission.NEARBY_WIFI_DEVICES],
            )
            KV(
                "位置权限",
                if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) "已授予" else "未授予",
                grants[Manifest.permission.ACCESS_FINE_LOCATION],
            )
            KV("哨兵服务", if (running) "运行中" else "已停止", running)
            KV(
                "当前 SSID",
                probe?.ssid ?: if (probe?.connected == true) "(读不到)" else "未连接 WiFi",
                probe?.readableSsid,
            )
        }

        ItemCard(1, "WiFi SSID 权限验证", "SSID 能不能读到（两条读取路径 + 定位开关对比）", onOpen, Route.Wifi)
        ItemCard(2, "前台服务保活", "开哨兵锁屏挂 8 小时，事后从日志看它有没有断", onOpen, Route.Sentinel)
        ItemCard(3, "全屏闹钟", "锁屏下 setAlarmClock + 全屏意图能不能弹出来", onOpen, Route.Alarm)
        ItemCard(4, "高德围栏", "到达指定位置触发（需先申请高德 key）", onOpen, Route.Amap)
        ItemCard(5, "连断统计与去抖", "一天连断几次、心跳有没有断档", onOpen, Route.Stats)
        ItemCard(6, "HyperOS 设置引导", "自启动 / 省电无限制 / 后台弹出界面", onOpen, Route.Guide)
        ItemCard(7, "全部日志", "按时间倒序看所有事件（排障用）", onOpen, Route.Logs)

        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { vm.clearLogs() }) { Text("清空全部日志") }
    }
}

@Composable
private fun ItemCard(
    index: Int,
    title: String,
    desc: String,
    onOpen: (Route) -> Unit,
    route: Route,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$index",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(26.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    text = desc,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { onOpen(route) }) { Text("进入") }
        }
    }
}
