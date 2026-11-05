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

/**
 * HyperOS / MIUI 上让提醒真正能响的开关清单。
 *
 * 这些设置**没法用 adb 一键搞定**（自启动、后台弹出界面都是小米私有权限），
 * 只能手点。所以这里尽量提供直达入口，点不进去的就写清楚路径。
 */
@Composable
fun GuideScreen(onBack: () -> Unit) {
    val ruyue = LocalRuyueColors.current
    val context = LocalContext.current

    ScreenScaffold("⑥ HyperOS 设置引导", onBack) {

        // 设计语言：大标题 + 一句为什么必须开（和设置页同一套排版）
        Text(
            text = "先把这四样开开",
            style = MaterialTheme.typography.headlineLarge,
            color = ruyue.text,
        )
        Text(
            text = "不打开「后台弹出界面」，锁屏闹钟只会变成一条通知。",
            style = MaterialTheme.typography.bodyMedium,
            color = ruyue.textSecondary,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )

        SectionCard("必须开的 4 项") {
            Text("1. 自启动")
            Hint("手机管家 → 应用管理 → 权限 → 自启动管理 → 允许「如约」")
            TextButton(onClick = { openMiuiAutostart(context) }) { Text("尝试直达自启动管理页") }

            Spacer(Modifier.height(6.dp))
            Text("2. 省电策略 → 无限制")
            Hint("设置 → 应用设置 → 应用管理 → 如约 → 省电策略 → 无限制")
            TextButton(onClick = { openAppDetails(context) }) { Text("打开应用详情页") }

            Spacer(Modifier.height(6.dp))
            Text("3. 后台弹出界面（最关键的一项）")
            Hint(
                "设置 → 应用设置 → 应用管理 → 权限管理 → 其他权限 → 后台弹出界面 → 允许\n" +
                    "不开这一项，锁屏时全屏闹钟弹不出来，只会变成一条通知。"
            )
            TextButton(onClick = { openAppDetails(context) }) { Text("打开应用详情页去找权限") }

            Spacer(Modifier.height(6.dp))
            Text("4. 通知权限 + 锁屏显示通知")
            TextButton(onClick = { openNotificationSettings(context) }) { Text("打开通知设置") }
        }

        SectionCard("建议一起开的") {
            Text("电池优化白名单")
            Hint("选「不优化」，让系统不要在深度休眠时把进程冻住。")
            TextButton(onClick = { openBatteryOptimization(context) }) { Text("打开电池优化设置") }

            Spacer(Modifier.height(6.dp))
            Text("全屏通知开关（Android 14+）")
            TextButton(onClick = { openFullScreenIntent(context) }) { Text("打开全屏通知开关") }

            Spacer(Modifier.height(6.dp))
            Text("始终保持 WiFi 连接")
            Hint("部分 ROM 息屏后会关 WiFi 省电，会导致「到家提醒」延迟。")
        }

        SectionCard("adb 能帮上的部分") {
            Hint(
                "Doze 白名单可以直接用命令行加，省得手点：\n\n" +
                    "  adb shell dumpsys deviceidle whitelist +com.ruyue.alarm\n" +
                    "  adb shell dumpsys deviceidle whitelist | findstr ruyue\n\n" +
                    "但自启动和「后台弹出界面」是小米私有权限，adb 授不了，只能手点。"
            )
        }

        SectionCard("强停 = 全部失效（Android 硬限制）") {
            Hint(
                "在「应用信息」里点「强行停止」，系统会清掉该应用的所有闹钟与广播监听，\n" +
                    "直到用户下次手动打开应用为止。这是 Android 的硬限制，任何保活手段都绕不过。\n" +
                    "所以正式版里要尽量避免让用户去点强行停止，并做好「打开应用即重新排定」的逻辑。"
            )
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
