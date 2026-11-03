package com.ruyue.alarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ruyue.alarm.alarm.ReminderScheduler
import com.ruyue.alarm.data.Reminder
import com.ruyue.alarm.data.Repeat
import com.ruyue.alarm.sentinel.SentinelController
import com.ruyue.alarm.sentinel.ServiceState
import com.ruyue.alarm.trigger.TriggerRules
import com.ruyue.alarm.ui.design.ConditionKind
import com.ruyue.alarm.ui.design.ConditionStep
import com.ruyue.alarm.ui.design.DogMascot
import com.ruyue.alarm.ui.design.DogPose
import com.ruyue.alarm.ui.design.NodeRail
import com.ruyue.alarm.ui.design.PathRail
import com.ruyue.alarm.ui.design.RuyueBackground
import com.ruyue.alarm.ui.design.StatusPill
import com.ruyue.alarm.ui.design.kindColor
import com.ruyue.alarm.ui.design.poseFor
import com.ruyue.alarm.ui.theme.LocalRuyueColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 首页：「条件就绪」方向的列表页（UI 迁移 Step 1 样张页）。
 *
 * 这一版要解决的四件事（设计稿的诊断）：
 *  ① 信息分级 —— 一条**主卡**回答"接下来会发生什么"，其余压成行
 *  ② 触发源有视觉身份 —— 颜色/路径/节点都编码"靠什么触发"
 *  ③ 小狗姿态 = 还差几个条件（不读字也知道急不急）
 *  ④ 用词统一成「约定」
 */
@Composable
fun ReminderListScreen(
    vm: ReminderViewModel,
    onEdit: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val list by vm.reminders.collectAsStateWithLifecycle()
    val currentSsid by vm.currentSsid.collectAsStateWithLifecycle()
    val running by ServiceState.running.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = LocalRuyueColors.current

    // 每条约定的条件步骤（时间/WiFi 能当场判；地点需要定位，列表页不做判定，只当"未达成"）
    val stepsOf: (Reminder) -> List<ConditionStep> = { r -> buildSteps(r, currentSsid) }

    val enabled = list.filter { it.enabled }
    val hero = enabled.minByOrNull { ReminderScheduler.nextTriggerAt(it) ?: Long.MAX_VALUE }
        ?: enabled.firstOrNull()
    val others = list.filter { it.id != hero?.id }

    RuyueBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // ---------------- 顶栏：日期 + 设置 ----------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 10.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = todayLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textTertiary,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenSettings) {
                    Text("设置", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                }
            }

            // ---------------- 哨兵未运行 ----------------
            if (!running) {
                SentinelBanner(
                    affected = list.count { it.enabled && (it.wifiEnabled || it.placeEnabled) },
                    onStart = { SentinelController.start(context) },
                )
            }

            if (list.isEmpty()) {
                EmptyState(onCreate = { onEdit(0L) })
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 12.dp),
                ) {
                    if (hero != null) {
                        item("hero") {
                            val steps = stepsOf(hero)
                            HeroSection(
                                reminder = hero,
                                steps = steps,
                                nextText = ReminderScheduler.describeNext(hero),
                                onClick = { onEdit(hero.id) },
                            )
                        }
                    }
                    item("others-head") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 22.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (others.isEmpty()) "没有其它约定" else "还有 ${others.size} 条",
                                style = MaterialTheme.typography.titleSmall,
                                color = c.textSecondary,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "新约定 ＋",
                                style = MaterialTheme.typography.labelLarge,
                                color = c.time,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onEdit(0L) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    items(others, key = { it.id }) { reminder ->
                        val steps = stepsOf(reminder)
                        ReminderRow(
                            reminder = reminder,
                            steps = steps,
                            subtitle = rowSubtitle(reminder, steps),
                            onClick = { onEdit(reminder.id) },
                        )
                    }
                }
            }

            // ---------------- 底栏：服务状态 + 新建约定 ----------------
            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text(
                    text = bottomStatus(list),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                    modifier = Modifier.padding(bottom = 8.dp, start = 2.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(c.time)
                            .clickable { onEdit(0L) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "＋ 新建约定",
                            style = MaterialTheme.typography.labelLarge,
                            color = c.onAccent,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(c.card)
                            .clickable(onClick = onOpenSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("›", fontSize = 22.sp, color = c.textSecondary)
                    }
                }
            }
        }
    }
}

// ==================== 主卡 ====================

@Composable
private fun HeroSection(
    reminder: Reminder,
    steps: List<ConditionStep>,
    nextText: String?,
    onClick: () -> Unit,
) {
    val c = LocalRuyueColors.current
    val pending = steps.count { !it.met }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        // 标题：今晚 22:30 刮胡子   +   小狗
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = heroHeadline(reminder),
                style = MaterialTheme.typography.titleLarge,
                color = c.text,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            DogMascot(
                pose = poseFor(pending, paused = !reminder.enabled),
                size = 96.dp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(c.card)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (pending == 0) "条件就绪" else "还差 $pending 个条件",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textTertiary,
                    )
                    Text(
                        text = pendingSummary(steps),
                        style = MaterialTheme.typography.titleMedium,
                        color = c.text,
                    )
                }
                StatusPill(
                    text = when {
                        !reminder.enabled -> "已暂停"
                        pending == 0 -> "已就绪"
                        else -> "等待中"
                    },
                    color = when {
                        !reminder.enabled -> c.textTertiary
                        pending == 0 -> c.time
                        else -> c.wifi
                    },
                )
            }

            if (steps.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                PathRail(steps = steps, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = lastFiredLine(reminder, nextText),
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary,
            )
        }
    }
}

// ==================== 行 ====================

@Composable
private fun ReminderRow(
    reminder: Reminder,
    steps: List<ConditionStep>,
    subtitle: String,
    onClick: () -> Unit,
) {
    val c = LocalRuyueColors.current
    val pending = steps.count { !it.met }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NodeRail(steps = steps, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = reminder.title.ifBlank { "（未命名约定）" },
                style = MaterialTheme.typography.titleMedium,
                color = if (reminder.enabled) c.text else c.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        StatusPill(
            text = when {
                !reminder.enabled -> "已暂停"
                pending == 0 -> "已就绪"
                else -> "等待中"
            },
            color = when {
                !reminder.enabled -> c.textTertiary
                pending == 0 -> c.time
                else -> c.textSecondary
            },
        )
    }
}

// ==================== 空状态 / 哨兵提示 ====================

@Composable
private fun EmptyState(onCreate: () -> Unit) {
    val c = LocalRuyueColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DogMascot(pose = DogPose.LIE_DOWN, size = 140.dp)
        Spacer(Modifier.height(18.dp))
        Text("它不催你", style = MaterialTheme.typography.titleMedium, color = c.text)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "定一条约定：什么时间、连上哪个 WiFi、\n到了哪儿 —— 满足条件时它会来叫你。",
            style = MaterialTheme.typography.bodySmall,
            color = c.textTertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "＋ 新建第一条约定",
            style = MaterialTheme.typography.labelLarge,
            color = c.onAccent,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(c.time)
                .clickable(onClick = onCreate)
                .padding(horizontal = 22.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun SentinelBanner(affected: Int, onStart: () -> Unit) {
    val c = LocalRuyueColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.card)
            .padding(14.dp),
    ) {
        Text("地点监听正在恢复中…", style = MaterialTheme.typography.titleMedium, color = c.place)
        Spacer(Modifier.height(4.dp))
        Text(
            text = buildString {
                append("服务被系统停掉了，看门狗会在十几分钟内自动拉起；打开应用也会立刻重启它。")
                if (affected > 0) append("\n当前有 $affected 条约定依赖它。")
            },
            style = MaterialTheme.typography.bodySmall,
            color = c.textTertiary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "立即启动",
            style = MaterialTheme.typography.labelLarge,
            color = c.onAccent,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(c.time)
                .clickable(onClick = onStart)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

// ==================== 纯计算 ====================

/** 把一条约定拆成"路径上的几步"：时间 / WiFi / 地点，各自此刻达成与否 */
private fun buildSteps(reminder: Reminder, currentSsid: String?): List<ConditionStep> {
    if (!reminder.enabled) return buildList {
        if (reminder.timeEnabled) add(ConditionStep(ConditionKind.TIME, hm(reminder), false))
        if (reminder.wifiEnabled) add(ConditionStep(ConditionKind.WIFI, wifiLabel(reminder), false))
        if (reminder.placeEnabled) add(ConditionStep(ConditionKind.PLACE, placeLabel(reminder), false))
    }
    val now = System.currentTimeMillis()
    return buildList {
        if (reminder.timeEnabled) {
            // 复用对账用的那条纯规则：早于时刻 / 超出容差 / 今天不是重复日 → 未达成
            val unmet = TriggerRules.timeGateUnmet(
                hour = reminder.hour,
                minute = reminder.minute,
                repeatMask = reminder.repeatMask,
                now = now,
                dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK),
                targetTodayMillis = todayTarget(reminder),
                dateMatchesToday = true,
            )
            add(ConditionStep(ConditionKind.TIME, hm(reminder), unmet == null))
        }
        if (reminder.wifiEnabled) {
            val met = currentSsid != null && currentSsid in reminder.ssidList
            add(ConditionStep(ConditionKind.WIFI, wifiLabel(reminder), met))
        }
        if (reminder.placeEnabled) {
            // 列表页不做定位（省电），只标"未达成"——真正的判定在 TriggerEngine 里
            add(ConditionStep(ConditionKind.PLACE, placeLabel(reminder), false))
        }
    }
}

private fun hm(r: Reminder): String = "%02d:%02d".format(r.hour, r.minute)

private fun wifiLabel(r: Reminder): String {
    val names = r.ssidList
    return when {
        names.isEmpty() -> "WiFi（未设置）"
        names.size == 1 -> names.first()
        else -> "${names.first()} 等 ${names.size} 个"
    }
}

private fun placeLabel(r: Reminder): String = r.placeName.ifBlank { "已设地点" }

private fun todayTarget(r: Reminder): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, r.hour)
    set(Calendar.MINUTE, r.minute)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** 主卡标题：能算出"什么时候"就写在前面 —— 设计的主张是"什么时候"比"什么事"更早被看见 */
private fun heroHeadline(r: Reminder): String {
    if (!r.timeEnabled) return r.title.ifBlank { "（未命名约定）" }
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, r.hour)
        set(Calendar.MINUTE, r.minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val now = Calendar.getInstance()
    val dayWord = when {
        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> {
            if (r.hour < 12) "今早" else if (r.hour < 18) "今天" else "今晚"
        }
        else -> {
            now.add(Calendar.DAY_OF_YEAR, 1)
            if (cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
                if (r.hour < 12) "明早" else if (r.hour < 18) "明天" else "明晚"
            } else {
                SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(cal.timeInMillis))
            }
        }
    }
    return "$dayWord ${hm(r)} ${r.title.ifBlank { "（未命名约定）" }}"
}

private fun pendingSummary(steps: List<ConditionStep>): String {
    val unmet = steps.filter { !it.met }
    return when {
        steps.isEmpty() -> "还没设触发条件"
        unmet.isEmpty() -> "条件都齐了，等它响"
        unmet.size == 1 -> "还差一个：${kindName(unmet.first().kind)}"
        else -> "还差 ${unmet.size} 个：" + unmet.joinToString("、") { kindName(it.kind) }
    }
}

private fun kindName(kind: ConditionKind): String = when (kind) {
    ConditionKind.TIME -> "时间"
    ConditionKind.WIFI -> "WiFi"
    ConditionKind.PLACE -> "地点"
}

private fun rowSubtitle(r: Reminder, steps: List<ConditionStep>): String {
    val next = ReminderScheduler.describeNext(r)
    if (!r.enabled) return "已暂停 · 不会响"
    val unmet = steps.filter { !it.met }
    return when {
        unmet.isNotEmpty() -> "等 " + unmet.joinToString("、") { kindName(it.kind).let { n -> if (n == "WiFi") "WiFi" else n } }
        next != null -> "已就绪 · $next"
        else -> "已就绪"
    }
}

private fun lastFiredLine(r: Reminder, nextText: String?): String {
    val fired = if (r.lastFiredAt > 0) {
        "最近 ${SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(Date(r.lastFiredAt))} 响过"
    } else {
        "还没响过"
    }
    val nextPart = nextText?.let { " · 下次 $it" } ?: ""
    return fired + nextPart
}

private fun bottomStatus(list: List<Reminder>): String {
    val enabled = list.count { it.enabled }
    val waiting = list.count { it.enabled && (it.wifiEnabled || it.placeEnabled) }
    return if (waiting > 0) {
        "地点监听中 · $enabled 条约定生效，其中 $waiting 条在等条件"
    } else {
        "地点监听中 · $enabled 条约定生效"
    }
}

private fun todayLabel(): String {
    val cal = Calendar.getInstance()
    val dow = arrayOf("日", "一", "二", "三", "四", "五", "六")[cal.get(Calendar.DAY_OF_WEEK) - 1]
    return SimpleDateFormat("M 月 d 日", Locale.getDefault()).format(cal.time) + " · 星期$dow"
}
