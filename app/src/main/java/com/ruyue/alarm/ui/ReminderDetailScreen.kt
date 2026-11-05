package com.ruyue.alarm.ui

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruyue.alarm.alarm.ReminderScheduler
import com.ruyue.alarm.data.Db
import com.ruyue.alarm.data.EventType
import com.ruyue.alarm.data.Reminder
import com.ruyue.alarm.data.ReminderDb
import com.ruyue.alarm.data.Repeat
import com.ruyue.alarm.trigger.Ledger
import com.ruyue.alarm.trigger.TriggerRules
import com.ruyue.alarm.ui.design.ConditionKind
import com.ruyue.alarm.ui.design.ConditionStep
import com.ruyue.alarm.ui.design.DogMascot
import com.ruyue.alarm.ui.design.PathRail
import com.ruyue.alarm.ui.design.SingleConditionRail
import com.ruyue.alarm.ui.design.RuyueBackground
import com.ruyue.alarm.ui.design.StatusPill
import com.ruyue.alarm.ui.design.kindColor
import com.ruyue.alarm.ui.design.poseFor
import com.ruyue.alarm.ui.theme.LocalRuyueColors
import com.ruyue.alarm.util.WifiReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 详情页（UI 迁移 Step 5b）。
 *
 * 为什么要有它：列表卡片上只够说"接下来会怎样"，「为什么这次没响 / 最近几次我怎么处理的」
 * 放不进去。设计稿 detail.png 的答案是 —— 点卡片进这一页，编辑再从这页进。
 *
 * 数据自己加载（不借 ViewModel 的编辑态）：这页是**只读快照**，
 * 动作只有三个：改这条 / 暂停·恢复 / 今天不再。做完动作就 reload 一次。
 */
@Composable
fun ReminderDetailScreen(
    reminderId: Long,
    vm: ReminderViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val context = LocalContext.current
    val c = LocalRuyueColors.current

    var reminder by remember { mutableStateOf<Reminder?>(null) }
    var recent by remember { mutableStateOf<List<String>>(emptyList()) }
    var firedCount by remember { mutableIntStateOf(0) }
    var steps by remember { mutableStateOf<List<ConditionStep>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reminderId, reload) {
        val r = runCatching { ReminderDb.get(context).reminderDao().byId(reminderId) }.getOrNull()
        reminder = r
        if (r != null) {
            firedCount = Ledger.firedCount(context, reminderId)
            steps = stepsOf(r, runCatching { WifiReader.snapshot(context).ssid }.getOrNull())
            // 最近三次响应：从 RING_ACK 事件里挑这条提醒的（detail 里带标题）
            val fmt = SimpleDateFormat("M-d HH:mm", Locale.getDefault())
            recent = runCatching {
                Db.get(context).eventLogDao()
                    .listByTypesSince(
                        listOf(EventType.RING_ACK),
                        System.currentTimeMillis() - 60L * 24 * 3_600_000L,
                    )
            }.getOrDefault(emptyList())
                .filter { it.detail?.contains("· ${r.title}") == true }
                .takeLast(3)
                .reversed()
                .map { ev ->
                    val body = ev.detail.orEmpty().substringAfter("响铃响应：")
                        .substringBefore("（响了")
                    fmt.format(Date(ev.timestamp)) + "  " + body
                }
        }
    }

    val r = reminder
    RuyueBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // 顶栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "‹ 全部",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.time,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                if (r != null) {
                    Text(
                        text = "改这条",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.onAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.time)
                            .clickable { onEdit(r.id) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            if (r == null) {
                Text(
                    text = "这条约定已经找不到了",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textTertiary,
                    modifier = Modifier.padding(18.dp),
                )
            } else {

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
            ) {
                // 标题 + 备注 + 小狗
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = r.title.ifBlank { "（未命名约定）" },
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp),
                            color = c.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (r.note.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "「${r.note}」",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textSecondary,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = buildString {
                                append(if (r.enabled) "生效中" else "已暂停")
                                ReminderScheduler.describeNext(r)?.let { append(" · 下次 $it") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textTertiary,
                        )
                    }
                    DogMascot(
                        pose = poseFor(steps.count { !it.met }, paused = !r.enabled),
                        size = 82.dp,
                    )
                }

                // 路径
                if (steps.size >= 2) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(c.card)
                            .padding(16.dp),
                    ) {
                        PathRail(steps = steps, modifier = Modifier.fillMaxWidth())
                    }
                } else if (steps.size == 1) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(c.card)
                            .padding(16.dp),
                    ) {
                        SingleConditionRail(step = steps.first(), color = kindColor(steps.first().kind, c))
                    }
                }

                // 条件清单
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "条件",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textTertiary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(c.card)
                        .padding(vertical = 4.dp),
                ) {
                    if (r.timeEnabled) {
                        DetailRow(
                            label = "时间",
                            value = "%02d:%02d".format(r.hour, r.minute),
                            extra = if (r.repeatMask == Repeat.ONCE && r.onceDate > 0L) {
                                SimpleDateFormat("M-d", Locale.getDefault()).format(Date(r.onceDate))
                            } else {
                                Repeat.label(r.repeatMask)
                            },
                            accent = c.time,
                        )
                    }
                    if (r.wifiEnabled) {
                        DetailRow(
                            label = "Wi-Fi",
                            value = r.ssidList.joinToString(" / ").ifBlank { "未设置" },
                            extra = "确认 ${r.wifiDwellSeconds}s",
                            accent = c.wifi,
                        )
                    }
                    if (r.placeEnabled) {
                        DetailRow(
                            label = "地点",
                            value = r.placeName.ifBlank { "已设地点" },
                            extra = "${r.radiusMeters}m · 停留 ${r.placeDwellSeconds}s",
                            accent = c.place,
                        )
                    }
                    DetailRow(
                        label = "逻辑",
                        value = if (r.matchMode == com.ruyue.alarm.data.MatchMode.ALL) "同时满足" else "任一满足",
                        extra = cooldownText(r.cooldownMinutes),
                        accent = c.track,
                    )
                }

                // 最近三次
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (firedCount > 0) "最近三次（共响过 $firedCount 次）" else "最近三次",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textTertiary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(c.card)
                        .padding(16.dp),
                ) {
                    if (recent.isEmpty()) {
                        Text(
                            text = "还没有记录。响过之后这里会显示你当时怎么处理的。",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textTertiary,
                        )
                    } else {
                        recent.forEachIndexed { index, line ->
                            if (index > 0) Spacer(Modifier.height(8.dp))
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                        }
                    }
                }

                // 暂停 / 今天不再
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.cardHigh)
                            .clickable {
                                vm.toggleEnabled(r)
                                reload++
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (r.enabled) "暂停这条约定" else "恢复这条约定",
                            style = MaterialTheme.typography.labelLarge,
                            color = c.textSecondary,
                        )
                    }
                    if (r.enabled) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(c.cardHigh)
                                .clickable {
                                    vm.suppressToday(r)
                                    reload++
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "今天不再",
                                style = MaterialTheme.typography.labelLarge,
                                color = c.textSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, extra: String, accent: androidx.compose.ui.graphics.Color) {
    val c = LocalRuyueColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            Text(value, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 2)
        }
        Text(extra, style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
    }
}

private fun cooldownText(minutes: Int): String = when {
    minutes < 60 -> "冷却 ${minutes} 分钟"
    minutes == 60 -> "冷却 1 小时"
    minutes < 1440 -> "冷却 ${minutes / 60} 小时"
    else -> "冷却一天"
}

/** 把一条约定拆成路径上的几步（与列表页同口径：地点在列表/详情里不做定位判定，算未达成） */
private fun stepsOf(r: Reminder, currentSsid: String?): List<ConditionStep> {
    if (!r.enabled) return emptyList()
    val now = System.currentTimeMillis()
    return buildList {
        if (r.timeEnabled) {
            val unmet = TriggerRules.timeGateUnmet(
                hour = r.hour,
                minute = r.minute,
                repeatMask = r.repeatMask,
                now = now,
                dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK),
                targetTodayMillis = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, r.hour)
                    set(Calendar.MINUTE, r.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis,
            )
            add(ConditionStep(ConditionKind.TIME, "%02d:%02d".format(r.hour, r.minute), unmet == null))
        }
        if (r.wifiEnabled) {
            add(
                ConditionStep(
                    ConditionKind.WIFI,
                    r.ssidList.firstOrNull() ?: "WiFi",
                    currentSsid != null && currentSsid in r.ssidList,
                )
            )
        }
        if (r.placeEnabled) {
            add(ConditionStep(ConditionKind.PLACE, r.placeName.ifBlank { "已设地点" }, false))
        }
    }
}
