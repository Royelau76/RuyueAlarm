package com.ruyue.alarm.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ruyue.alarm.ui.theme.LocalRuyueColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.ruyue.alarm.ui.design.RuyueBackground
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment

/**
 * HyperOS / MIUI 上让提醒真正能响的开关清单。
 *
 * 这些设置**没法用 adb 一键搞定**（自启动、后台弹出界面都是小米私有权限），
 * 只能手点。所以这里尽量提供直达入口，点不进去的就写清楚路径。
 */
@Composable
fun GuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val ruyue = LocalRuyueColors.current

    RuyueBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "‹ 返回",
                    style = MaterialTheme.typography.labelLarge,
                    color = ruyue.time,
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
                    text = "先把这四样开开",
                    style = MaterialTheme.typography.headlineLarge,
                    color = ruyue.text,
                )
                Text(
                    text = "不打开「后台弹出界面」，锁屏闹钟只会变成一条通知。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ruyue.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )

                GuideLabel("必须开的")
                GuideCard {
                    GuideRow("自启动", "手机管家 → 应用管理 → 权限 → 自启动管理", "去设置") { openMiuiAutostart(context) }
                    GuideDivider()
                    GuideRow("省电策略 → 无限制", "设置 → 应用管理 → 如约 → 省电策略", "去设置") { openAppDetails(context) }
                    GuideDivider()
                    GuideRow("后台弹出界面", "最关键的一项；不开就只有通知，没有全屏闹钟", "去设置") { openAppDetails(context) }
                    GuideDivider()
                    GuideRow("通知权限 + 锁屏通知", "允许通知，并勾上「锁屏显示」", "去设置") { openNotificationSettings(context) }
                }

                GuideLabel("建议一起开的")
                GuideCard {
                    GuideRow("电池优化白名单", "防止 Doze 把后台闹钟延后", "去设置") { openBatteryOptimization(context) }
                    GuideDivider()
                    GuideRow("全屏通知开关（Android 14+）", "部分 ROM 默认拦截全屏意图", "去设置") { openFullScreenIntent(context) }
                    GuideDivider()
                    GuideRow("始终保持 WiFi 连接", "息屏后被关 WiFi，会让「到家提醒」延迟", null) {}
                }

                GuideLabel("强停 = 全部失效")
                GuideCard {
                    Text(
                        text = "Android 的硬限制：在系统设置里点「强行停止」，所有闹钟与监听都会失效。" +
                            "不用慌 —— 重新打开一次「如约」，它会自动重新排定全部提醒。",
                        style = MaterialTheme.typography.bodySmall,
                        color = ruyue.textTertiary,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                Text(
                    text = "自启动和「后台弹出界面」是小米私有权限，adb 授不了，只能手动点。",
                    style = MaterialTheme.typography.labelSmall,
                    color = ruyue.textTertiary,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun GuideLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalRuyueColors.current.textTertiary,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun GuideCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(LocalRuyueColors.current.card),
        content = content,
    )
}

@Composable
private fun GuideDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(LocalRuyueColors.current.track.copy(alpha = 0.45f)),
    )
}

@Composable
private fun GuideRow(title: String, desc: String, action: String?, onClick: () -> Unit) {
    val c = LocalRuyueColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (action != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            Spacer(Modifier.height(2.dp))
            Text(desc, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        }
        if (action != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = c.time)
            Text(" ›", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        }
    }
}

private fun openAppDetails(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
        )
    }
}

private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    }
}

private fun openBatteryOptimization(context: Context) {
    // 先尝试直接针对本应用弹「是否忽略电池优化」，失败则退到电池优化列表页
    val direct = runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.fromParts("package", context.packageName, null),
            )
        )
    }.isSuccess
    if (!direct) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

private fun openFullScreenIntent(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.fromParts("package", context.packageName, null),
            )
        )
    }
}

/** MIUI/HyperOS 的自启动页是私有 Activity，类名可能随版本变化，所以必须 try-catch 兜底。 */
private fun openMiuiAutostart(context: Context) {
    val candidates = listOf(
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
        ),
    )
    for (component in candidates) {
        val ok = runCatching {
            context.startActivity(Intent().setComponent(component))
        }.isSuccess
        if (ok) return
    }
    openAppDetails(context)
}
