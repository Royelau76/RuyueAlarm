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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.ruyue.alarm.BuildConfig
import com.ruyue.alarm.ui.theme.LocalRuyueColors
import com.ruyue.alarm.trigger.Ledger
import com.ruyue.alarm.util.Backup
import kotlinx.coroutines.launch

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
    val ruyue = LocalRuyueColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 备份小结点这里存一份：点完"立即备份"要能立刻看到变化
    var backupSummary by remember { mutableStateOf(Backup.summary(context)) }

    // 对账小结：最近 7 天该响几条、实际响了几条
    var ledgerLine by remember { mutableStateOf("正在读对账数据…") }
    LaunchedEffect(Unit) {
        val s = Ledger.stats(context)
        ledgerLine = "最近 7 天：应响 ${s.expected} · 实际响 ${s.fired}" +
            " · 按设计跳过 ${s.skipped} · 漏掉 ${s.missed}"
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Spacer(Modifier.width(4.dp))
            Text("设置", style = MaterialTheme.typography.headlineLarge)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // 设计稿：大标题下一句话就把"为什么可以放心用"说完
            Text(
                text = "🔒 数据只在本机。不同步、不上云。",
                style = MaterialTheme.typography.bodyMedium,
                color = ruyue.textSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 8.dp),
            )
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

            // 触发对账：「提醒到底会不会响」这件事的可检测版本
            SettingsItem(
                title = "自检对账",
                desc = "核一遍「该响的提醒是不是都响了」；后台每天也会自动跑一次",
                onClick = {
                    scope.launch {
                        val report = Ledger.runCheck(context, force = true)
                        val stats = Ledger.stats(context)
                        ledgerLine = "最近 7 天：应响 ${stats.expected} · 实际响 ${stats.fired}" +
                            " · 按设计跳过 ${stats.skipped} · 漏掉 ${stats.missed}"
                        val text = if (report.hasMissed) {
                            "⚠️ 发现 ${report.missed.size} 条该响没响：" +
                                report.missed.take(2).joinToString("、") { it.title } +
                                "（详情已推送到通知栏）"
                        } else {
                            "✓ 对账正常：最近 7 天应响 ${stats.expected} 条，实际响 ${stats.fired} 条"
                        }
                        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                    }
                },
            )
            Hint(ledgerLine)
            SettingsItem(
                title = "M0 验证台",
                desc = "WiFi 权限 / 保活 / 全屏闹钟 / 连断统计 / 高德 —— 回归与排障入口",
                onClick = onOpenDev,
            )

            Spacer(Modifier.height(26.dp))
            Text(
                text = "这个版本只有你一个人用。\n所以没有账号、没有同步、没有订阅。",
                style = MaterialTheme.typography.bodySmall,
                color = ruyue.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "如约 v${BuildConfig.VERSION_NAME} · 数据只在本机（两个 Room 库）",
                style = MaterialTheme.typography.labelSmall,
                color = ruyue.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsItem(title: String, desc: String, onClick: () -> Unit) {
    val c = LocalRuyueColors.current
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = c.card),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = c.textSecondary,
            )
        }
    }
}
