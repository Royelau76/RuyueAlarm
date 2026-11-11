package com.ruyue.alarm.ui

import android.Manifest
import android.app.AlarmManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ruyue.alarm.data.Db
import com.ruyue.alarm.data.EventType
import com.ruyue.alarm.ui.design.RuyueBackground
import com.ruyue.alarm.ui.theme.LocalRuyueColors
import com.ruyue.alarm.util.OemPermission
import com.ruyue.alarm.util.WifiReader
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自检诊断页（2026-11-11，阶段 1：引导 + 自检）。
 *
 * 为什么要有这一页：如约能不能响，取决于「四样系统权限 + 哨兵不被杀」，
 * 而其中**自启动、后台弹出界面没有任何 API 能检测**，只能让用户自查。
 * 这一页把**能自动查的全查出来**，查不了的就摆出路径让他对一遍 ——
 * 于是用户报 issue 时，一张截图就能定位是哪一环断了（和「日志即证据」同一思路）。
 */
private enum class CheckState { OK, BAD, UNKNOWN }

private data class CheckRow(
    val title: String,
    val detail: String,
    val state: CheckState,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = LocalRuyueColors.current
    val brand = remember { OemPermission.current() }

    var rows by remember { mutableStateOf<List<CheckRow>>(emptyList()) }
    var sentinelText by remember { mutableStateOf("查询中…") }
    var wifiText by remember { mutableStateOf("—") }

    fun open(intent: android.content.Intent?) {
        val target = intent ?: OemPermission.appDetails(context)
        runCatching { context.startActivity(target) }
            .onFailure { runCatching { context.startActivity(OemPermission.appDetails(context)) } }
    }

    LaunchedEffect(Unit) {
        // ---- 能自动检测的 ----
        val notifOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val bg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            }.getOrDefault(false)
        } else {
            true
        }
        val ignoreBat = runCatching {
            context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)

        val list = buildList {
            add(
                CheckRow(
                    "通知权限",
                    if (notifOn) "已开启" else "没开 → 触发时只会留日志，不会弹任何东西",
                    if (notifOn) CheckState.OK else CheckState.BAD,
                    "去设置",
                ) { open(OemPermission.notificationSettings(context)) },
            )
            add(
                CheckRow(
                    "精确闹钟",
                    if (exact) "已授予（闹钟不受 Doze 影响）" else "未授予 → 到点会晚几分钟甚至不响",
                    if (exact) CheckState.OK else CheckState.BAD,
                    "去设置",
                ) { open(OemPermission.appDetails(context)) },
            )
            add(
                CheckRow(
                    "电池优化白名单",
                    if (ignoreBat) "已在白名单" else "不在白名单 → 息屏后可能被冻结，闹钟延后",
                    if (ignoreBat) CheckState.OK else CheckState.BAD,
                    "去设置",
                ) { open(OemPermission.batteryOptimization(context)) },
            )
            add(
                CheckRow(
                    "定位权限",
                    when {
                        !fine -> "缺前台定位 → 地点触发完全不可用"
                        !bg -> "有前台、缺后台 → 息屏后地点监听可能停"
                        else -> "前台 + 后台都已授予"
                    },
                    if (fine && bg) CheckState.OK else CheckState.BAD,
                    "去设置",
                ) { open(OemPermission.appDetails(context)) },
            )
            // ---- 检测不了、必须让用户自己对的两项 ----
            add(
                CheckRow(
                    "自启动（无法自动检测）",
                    brand.autostart,
                    CheckState.UNKNOWN,
                    "去设置",
                ) { open(OemPermission.autostart(context)) },
            )
            add(
                CheckRow(
                    "后台弹出界面（无法自动检测）",
                    brand.popup,
                    CheckState.UNKNOWN,
                    "去设置",
                ) { open(OemPermission.overlay(context)) },
            )
        }
        rows = list

        wifiText = runCatching { WifiReader.snapshot(context).ssid }.getOrNull() ?: "未连接"

        // ---- 哨兵状态：从事件日志读（不引入新的系统 API）----
        val events = runCatching { Db.get(context).eventLogDao().recent(2000).first() }.getOrDefault(emptyList())
        val now = System.currentTimeMillis()
        val lastHb = events.firstOrNull { it.type == EventType.HEARTBEAT }?.timestamp
        val starts24 = events.count {
            it.type == EventType.SERVICE_START && now - it.timestamp <= 24 * 3600_000L
        }
        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        sentinelText = buildString {
            if (lastHb == null) {
                append("没查到心跳记录 —— 哨兵可能从未启动过")
            } else {
                val mins = (now - lastHb) / 60000
                append("最近心跳 ")
                append(fmt.format(Date(lastHb)))
                append("（")
                append(if (mins < 20) "$mins 分钟前 ✓" else "$mins 分钟前 ⚠️ 可能已被杀")
                append("）")
            }
            append("\n近 24 小时服务启动 $starts24 次")
            if (starts24 >= 3) append("（偏多：系统在反复重启它，检查省电策略）")
        }
    }

    RuyueBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "‹ 返回",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.time,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
            ) {
                Text(
                    text = "这台手机准备好\n让如约响了吗",
                    style = MaterialTheme.typography.headlineLarge,
                    color = c.text,
                )
                Text(
                    text = "能自动查的都查了；查不了的（自启动 / 后台弹出界面）请对着路径看一眼。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                )

                // 机型
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(c.card)
                        .padding(16.dp),
                ) {
                    Text(
                        text = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.text,
                    )
                    Text(
                        text = "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）· 识别为 ${brand.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "权限自检",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textTertiary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(c.card)
                        .padding(vertical = 4.dp),
                ) {
                    rows.forEachIndexed { i, row ->
                        if (i > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp)
                                    .height(1.dp)
                                    .background(c.track.copy(alpha = 0.45f)),
                            )
                        }
                        CheckRowView(row, c)
                    }
                }

                // 哨兵
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "哨兵（后台监听服务）",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textTertiary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(c.card)
                        .padding(16.dp),
                ) {
                    Text(
                        text = sentinelText,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "当前 WiFi：$wifiText",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                }

                // 品牌提示
                Spacer(Modifier.height(12.dp))
                Text(
                    text = brand.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun CheckRowView(row: CheckRow, c: com.ruyue.alarm.ui.theme.RuyueColors) {
    val (mark, markColor) = when (row.state) {
        CheckState.OK -> "✓" to c.time
        CheckState.BAD -> "✗" to c.place
        CheckState.UNKNOWN -> "?" to c.textTertiary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (row.action != null) Modifier.clickable { row.action.invoke() } else Modifier,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(mark, style = MaterialTheme.typography.titleMedium, color = markColor)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.titleSmall, color = c.text)
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.detail,
                style = MaterialTheme.typography.labelSmall,
                color = if (row.state == CheckState.BAD) c.place else c.textTertiary,
            )
        }
        if (row.actionLabel != null && row.action != null) {
            Text(row.actionLabel, style = MaterialTheme.typography.labelLarge, color = c.time)
            Text(" ›", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        }
    }
}

/** 给设置页的副标题用：一句话概括当前检测结果（只查能自动查的） */
fun diagnosticsSummary(context: android.content.Context): String {
    val notifOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val ignoreBat = runCatching {
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)
    val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        }.getOrDefault(false)
    } else {
        true
    }
    val bad = listOf(notifOn, ignoreBat, exact).count { !it }
    return if (bad == 0) "自动检测项全部正常 ✓" else "有 $bad 项没开（点进去看是哪一项）"
}
