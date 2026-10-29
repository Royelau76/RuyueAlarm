package com.ruyue.alarm.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ruyue.alarm.BuildConfig
import com.ruyue.alarm.util.Backup

/**
 * 设置页。
 *
 * 为什么要有它（v0.8.2）：原来「M0 验证台」的入口直接挂在**首页右上角** ——
 * 一个日常使用的 App，最显眼的位置不该是开发入口。现在收进这里，
 * 首页只留"提醒"这件事本身。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenDev: () -> Unit,
) {
    val context = LocalContext.current
    // 备份小结点这里存一份：点完"立即备份"要能立刻看到变化
    var backupSummary by remember { mutableStateOf(Backup.summary(context)) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Spacer(Modifier.width(4.dp))
            Text("设置", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SettingsItem(
                title = "通知与后台权限",
                desc = "自启动 / 省电无限制 / 后台弹出界面 —— 不开的话锁屏闹钟弹不出来",
                onClick = onOpenGuide,
            )
            SettingsItem(
                title = "运行日志",
                desc = "按时间倒序看所有事件（排障用），可一键导出 txt",
                onClick = onOpenLogs,
            )
            // 升级安全：提醒库是"不可重建"的数据，每次换版本前自动存一份 JSON
            SettingsItem(
                title = "数据备份",
                desc = "每次升级会自动备份提醒库；点这里可立即备份并分享出去留档",
                onClick = {
                    val file = Backup.backupNow(context)
                    if (file == null) {
                        Toast.makeText(context, "还没有提醒数据可备份", Toast.LENGTH_SHORT).show()
                    } else {
                        backupSummary = Backup.summary(context)
                        Backup.share(context, file)
                    }
                },
            )
            Hint(backupSummary)
            SettingsItem(
                title = "M0 验证台",
                desc = "WiFi 权限 / 保活 / 全屏闹钟 / 连断统计 / 高德 —— 回归与排障入口",
                onClick = onOpenDev,
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = "如约 v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Hint("自用版本：不做多设备同步、不上云。所有数据只在本机（两个 Room 库文件）。")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsItem(title: String, desc: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
