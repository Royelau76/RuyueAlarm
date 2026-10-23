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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ruyue.alarm.BuildConfig
import com.ruyue.alarm.data.Reminder
import com.ruyue.alarm.sentinel.SentinelController
import com.ruyue.alarm.sentinel.SentinelService
import com.ruyue.alarm.sentinel.ServiceState

@Composable
fun ReminderListScreen(
    vm: ReminderViewModel,
    onEdit: (Long) -> Unit,
    onOpenDev: () -> Unit,
) {
    val list by vm.reminders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 用真实运行状态而不是「用户期望状态」：服务被系统停掉时也要能立刻看出来
    val running by ServiceState.running.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("如约", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenDev) { Text("M0 验证台") }
        }

        if (!running) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("地点监听正在恢复中…", fontWeight = FontWeight.Bold)
                    val affected = list.count { it.enabled && (it.wifiEnabled || it.placeEnabled) }
                    Text(
                        text = buildString {
                            append("服务被系统停掉了，看门狗会在十几分钟内自动拉起；打开应用也会立刻重启它。")
                            if (affected > 0) append("\n当前有 $affected 条提醒依赖它。")
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { SentinelController.start(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("立即启动") }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        if (list.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "还没有提醒。\n点下面「新建提醒」，可以设时间、也可以设「连上某个 WiFi 时提醒」。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(list, key = { it.id }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        nextText = com.ruyue.alarm.alarm.ReminderScheduler.describeNext(reminder),
                        onToggle = { vm.toggleEnabled(reminder) },
                        onClick = { onEdit(reminder.id) },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Button(onClick = { onEdit(0L) }, modifier = Modifier.fillMaxWidth()) {
            Text("+ 新建提醒")
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: Reminder,
    nextText: String?,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = reminder.title.ifBlank { "（未命名）" },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = reminder.triggerSummary(),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (nextText != null && reminder.enabled) {
                    Text(
                        text = "下次：$nextText",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (reminder.note.isNotBlank()) {
                    Text(text = reminder.note, fontSize = 13.sp)
                }
            }
            Switch(checked = reminder.enabled, onCheckedChange = { onToggle() })
            TextButton(onClick = onClick) { Text("编辑") }
        }
    }
}
