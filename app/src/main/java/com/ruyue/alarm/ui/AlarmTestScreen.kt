package com.ruyue.alarm.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * M0 第 3 项：锁屏状态下闹钟能不能全屏弹出来。
 *
 * 这里用 setAlarmClock（不需要精确闹钟权限、免 Doze），再靠通知的 fullScreenIntent
 * 拉起 RingActivity。响铃页会直接告诉你这次是「锁屏弹出」还是「只走了普通通知」。
 */
@Composable
fun AlarmTestScreen(vm: M0ViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by vm.alarmState.collectAsStateWithLifecycle()

    ScreenScaffold("③ 全屏闹钟", onBack) {

        SectionCard("权限与状态") {
            KV(
                "可设置精确闹钟",
                if (state.canScheduleExact) "可以" else "被拒绝",
                state.canScheduleExact,
            )
            KV(
                "可使用全屏意图",
                if (state.canFullScreen) "可以" else "被拒绝",
                state.canFullScreen,
            )
            KV(
                "下一个闹钟",
                state.nextTriggerAt?.let {
                    SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
                } ?: "无",
            )
            Hint(
                "注：setAlarmClock 本身不需要「精确闹钟」权限，这两项显示出来是为了对照 ——\n" +
                    "如果全屏意图被拒绝，锁屏时就只会出横幅通知，不会全屏弹窗。"
            )
        }

        SectionCard("发起测试") {
            Button(
                onClick = { vm.scheduleAlarm(5) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("5 秒后提醒（先看前台效果）") }

            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { vm.scheduleAlarm(60) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("1 分钟后提醒（点了就锁屏）") }

            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { vm.scheduleAlarm(300) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("5 分钟后提醒") }

            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { vm.cancelAlarm() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("取消已排定的闹钟") }

            Hint(
                "测试动作：点「1 分钟后提醒」→ 立刻按电源键锁屏 → 等它响。\n" +
                    "响铃页会写明本次是「锁屏弹出 ✅」还是「只走了普通通知 ⚠️」。"
            )
        }

        SectionCard("相关系统设置") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    Uri.fromParts("package", context.packageName, null),
                                )
                            )
                        }
                    },
                ) { Text("打开「全屏通知」系统开关") }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !state.canScheduleExact) {
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.fromParts("package", context.packageName, null),
                                )
                            )
                        }
                    },
                ) { Text("打开「闹钟和提醒」权限页") }
                Hint(
                    "本应用已声明 USE_EXACT_ALARM（Android 13+ 安装即自动授予，无需手动操作）。\n" +
                        "若这里仍显示未授权：Android 14 起这类权限的列表**不一定会列出本应用**，\n" +
                        "找不到属正常现象 —— 时间闹钟走的是 setAlarmClock，本身不依赖这个权限。"
                )
            }
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APP_NOTIFICATION_SETTINGS
                            ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }
                },
            ) { Text("打开通知设置") }
        }

        SectionCard("判定标准") {
            Hint(
                "✅ 通过：锁屏状态下屏幕自动亮起、全屏响铃页弹出、闹钟音量响铃\n" +
                    "❌ 只出横幅：全屏意图被拒（去上面那个系统开关打开）；\n" +
                    "❌ 完全没动静：看日志里有没有 ALARM_FIRED —— 有则是没弹出来，\n" +
                    "   没有则是闹钟本身没触发（多半是 HyperOS 的自启动/省电策略）"
            )
        }
    }
}
