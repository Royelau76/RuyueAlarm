package com.ruyue.alarm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ruyue.alarm.BuildConfig
import com.ruyue.alarm.alarm.ReminderScheduler
import com.ruyue.alarm.data.MatchMode
import com.ruyue.alarm.data.Reminder
import com.ruyue.alarm.sentinel.SentinelController
import com.ruyue.alarm.sentinel.ServiceState

/**
 * 首页：提醒列表。
 *
 * v0.8.2 重做，解决三处：
 *  · 原来右上角是「M0 验证台」—— 开发入口不该占日常界面最显眼的位置，已收进设置页
 *  · 原来卡片里塞了 4 行字 + Switch + "编辑"按钮（一行三个可点区域），现在**点整张卡编辑**，
 *    右侧只留开关；触发方式改成小胶囊，扫一眼就知道靠什么触发、是「且」还是「或」
 *  · 底部通栏「新建提醒」换成右下角 FAB
 */
@Composable
fun ReminderListScreen(
    vm: ReminderViewModel,
    onEdit: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val list by vm.reminders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 用真实运行状态而不是「用户期望状态」：服务被系统停掉时也要能立刻看出来
    val running by ServiceState.running.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        // ---------------- 顶部 ----------------
        Row(
            modifier = Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("如约", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenSettings) { Text("设置") }
        }

        // ---------------- 哨兵未运行时的提示 ----------------
        if (!running) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("地点监听正在恢复中…", style = MaterialTheme.typography.titleMedium)
                    val affected = list.count { it.enabled && (it.wifiEnabled || it.placeEnabled) }
                    Hint(
                        buildString {
                            append("服务被系统停掉了，看门狗会在十几分钟内自动拉起；打开应用也会立刻重启它。")
                            if (affected > 0) append("\n当前有 $affected 条提醒依赖它。")
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { SentinelController.start(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("立即启动") }
                }
            }
        }

        // ---------------- 列表 + FAB ----------------
        Box(Modifier.weight(1f)) {
            if (list.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // 底部留白：内容不要被右下角 FAB 压住
                    contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp),
                ) {
                    items(list, key = { it.id }) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            nextText = ReminderScheduler.describeNext(reminder),
                            onToggle = { vm.toggleEnabled(reminder) },
                            onClick = { onEdit(reminder.id) },
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = { onEdit(0L) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 20.dp),
            ) {
                Text("+", fontSize = 26.sp)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 60.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("还没有提醒", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "点右下角「+」新建。\n可以设时间，也可以设「连上某个 WiFi 时」\n或「到某个地点时」提醒。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReminderCard(
    reminder: Reminder,
    nextText: String?,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = reminder.title.ifBlank { "（未命名）" },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (reminder.enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (!reminder.enabled) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "已停用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                TriggerChips(reminder)

                if (nextText != null && reminder.enabled) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "下次 · $nextText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (reminder.note.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = reminder.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
            Switch(checked = reminder.enabled, onCheckedChange = { onToggle() })
        }
    }
}

/**
 * 触发方式小胶囊：一眼看出这条提醒靠什么触发，以及多个条件之间是「且」还是「或」。
 *
 * 之前是一行纯文字摘要（「连上 TP-LINK_1602_5G 或 到 公司（300m 内）」），
 * 长 SSID 会把卡片撑成三四行 —— 这里改成固定短标签，宽度可控。
 */
@Composable
private fun TriggerChips(reminder: Reminder) {
    val labels = buildList {
        if (reminder.timeEnabled) {
            add("%02d:%02d".format(reminder.hour, reminder.minute))
        }
        if (reminder.wifiEnabled) {
            val count = reminder.ssidList.size
            add(if (count > 1) "WiFi ×$count" else "WiFi")
        }
        if (reminder.placeEnabled) {
            add("地点 " + reminder.placeName.ifBlank { "已设" }.take(6))
        }
    }

    if (labels.isEmpty()) {
        Text(
            text = "还没设置触发条件",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            if (index > 0) {
                Text(
                    text = if (reminder.matchMode == MatchMode.ALL) "且" else "或",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
