package com.ruyue.alarm.alarm

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.ruyue.alarm.data.Db
import com.ruyue.alarm.data.EventType
import com.ruyue.alarm.data.LogRepository
import com.ruyue.alarm.data.Reminder
import com.ruyue.alarm.data.ReminderDb
import com.ruyue.alarm.trigger.Ledger
import com.ruyue.alarm.ui.design.ConditionKind
import com.ruyue.alarm.ui.design.ConditionStep
import com.ruyue.alarm.ui.design.DogMascot
import com.ruyue.alarm.ui.design.CHASE_FRAMES
import com.ruyue.alarm.ui.design.DogFrameLoop
import com.ruyue.alarm.ui.design.RUN_FRAMES
import com.ruyue.alarm.ui.design.DogPose
import com.ruyue.alarm.ui.design.PathRail
import com.ruyue.alarm.ui.design.RuyueBackground
import com.ruyue.alarm.ui.design.SingleConditionRail
import com.ruyue.alarm.ui.design.StatusPill
import com.ruyue.alarm.ui.design.kindColor
import com.ruyue.alarm.ui.nextMidnight
import com.ruyue.alarm.ui.theme.LocalRuyueColors
import com.ruyue.alarm.ui.theme.RuyueTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 响铃页（只管界面）。
 *
 * ## 声音不在这里放（v0.7.0）
 *
 * Android 在**亮屏解锁**时会把全屏意图降级成横幅通知 —— 这是系统防打扰设计，改不了。
 * 所以「声音必须由界面播放」这个前提本身就是错的：用户在用手机时，界面根本不会自动弹出。
 * 实测 2026-10-27 09:55：`ALARM_FIRED 09:55:06` → `RING_OPENED 09:55:41`，**晚了 35 秒**。
 *
 * 现在声音由 [RingPlayer] 在**通知发出时**就开始播，本页只负责显示 + 三个动作。
 *
 * ## v0.19.0「条件就绪」重做（UI 迁移 Step 2）
 *
 * 设计稿 `docs/ui/screens/alert.png` 的主张：**被提醒的那一刻必须 1 秒读懂**。
 * 所以这页从"标题 + 三个按钮"变成了一条因果链：
 *
 * ```
 * 09:00（74sp）  该开工了  你说过「开工开工！」
 * 为什么现在响：WiFi 08:58 ── 公司 120m ── 09:00 ──●   三个都满足
 * 08:58:12 连上公司-5G / 08:59:40 进入公司 500 米 / 09:00:00 时间到
 * 下一次 明天 09:00 · 工作日              共响过 6 次
 * ```
 */
@AndroidEntryPoint
class RingActivity : ComponentActivity() {

    @Inject lateinit var repository: LogRepository

    /** 本页对应的提醒 id —— 退出时要用它撤掉通知 */
    private var currentReminderId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val keyguard = getSystemService(KeyguardManager::class.java)
        val locked = keyguard?.isKeyguardLocked == true
        if (locked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            keyguard?.requestDismissKeyguard(this, null)
        }

        val reminderId = intent?.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, 0L) ?: 0L
        currentReminderId = reminderId
        val title = intent?.getStringExtra(ReminderScheduler.EXTRA_TITLE)
            ?: intent?.getStringExtra(AlarmScheduler.EXTRA_LABEL)
            ?: "提醒"
        val note = intent?.getStringExtra(ReminderScheduler.EXTRA_NOTE).orEmpty()

        // 正常情况下声音**已经**在通知发出时由 RingPlayer 开始播了。
        // 只有一种情况需要在这里补一次：用户点开的是"响铃已自动停止"的旧通知。
        if (!RingPlayer.isPlaying()) {
            RingPlayer.start(
                this,
                reminderId,
                intent?.getStringExtra(EXTRA_RINGTONE).orEmpty(),
                intent?.getIntExtra(EXTRA_RAMP_SECONDS, 0) ?: 0,
            )
        }

        lifecycleScope.launch {
            repository.log(
                EventType.RING_OPENED,
                detail = "响铃页弹出：锁屏=$locked，提醒=$title",
            )
        }

        val openedAt = System.currentTimeMillis()

        setContent {
            RuyueTheme {
                var reminder by remember { mutableStateOf<Reminder?>(null) }
                var steps by remember { mutableStateOf<List<ConditionStep>>(emptyList()) }
                var timeline by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
                var firedCount by remember { mutableIntStateOf(0) }
                var nextText by remember { mutableStateOf<String?>(null) }
                var acked by remember { mutableStateOf(false) }
                // 按下「稍后」后 → 小狗追着球跑开（位置 A）
                var snoozing by remember { mutableStateOf(false) }
                var elapsedSec by remember { mutableIntStateOf(0) }

                // 条件达成时间线：从 COND_MET 事件里捡**这条提醒**的（detail 里带标题）
                LaunchedEffect(reminderId) {
                    val now = System.currentTimeMillis()
                    reminder = runCatching {
                        ReminderDb.get(this@RingActivity).reminderDao().byId(reminderId)
                    }.getOrNull()
                    nextText = reminder?.let { ReminderScheduler.describeNext(it) }
                    firedCount = Ledger.firedCount(this@RingActivity, reminderId)
                    val events = runCatching {
                        Db.get(this@RingActivity).eventLogDao()
                            .listByTypesSince(listOf(EventType.COND_MET), now - 20 * 60_000L)
                    }.getOrDefault(emptyList())
                        .filter { it.detail?.contains("「$title」") == true && it.timestamp <= now }

                    val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    timeline = events.map { it.timestamp to (fmt.format(Date(it.timestamp)) + "  " + shortCond(it.detail.orEmpty())) }

                    // 条件步骤：以**这条约定启用的条件**为准，而不是以 COND_MET 事件为准。
                    // 否则「同时满足」时只会画出触发它的那一个条件 ——
                    // 用户问「为什么现在响」，答案应该是三个条件的全貌（2026-11-06 用户反馈）。
                    // 「同时满足」能把闸门走完就说明其他条件此刻都成立 → 全部标 met；
                    // 「任一满足」只有真正达成过的那些才算 met。
                    if (reminder != null) {
                        steps = buildStepsFrom(reminder!!, events)
                    } else {
                        steps = emptyList()
                    }
                }

                // 「已响 N 秒」：每秒刷新。玩家已停（点了旧通知）就按打开页面算
                LaunchedEffect(Unit) {
                    while (true) {
                        val played = RingPlayer.elapsedMs()
                        elapsedSec = ((if (played > 0) played else System.currentTimeMillis() - openedAt) / 1000)
                            .toInt()
                        delay(1000)
                    }
                }

                // 5 分钟无人处理：RingPlayer 自动停了声音，这里把页面也关掉（否则亮屏整夜）
                LaunchedEffect(Unit) {
                    RingPlayer.autoStopped.collect { stopped ->
                        if (stopped) {
                            ReminderNotifier.cancel(this@RingActivity, reminderId)
                            finish()
                        }
                    }
                }

                RingScreen(
                    title = title,
                    note = note,
                    // 非时间提醒没有「时刻」——hour/minute 只是实体默认值 9:00，
                    // 无条件传过去会让响铃页永远顶着假的 09:00（2026-11-08 用户报）
                    hour = reminder?.takeIf { it.timeEnabled }?.hour,
                    minute = reminder?.takeIf { it.timeEnabled }?.minute,
                    sourceLabel = when {
                        reminder == null -> null
                        reminder!!.placeEnabled -> "到达 " + reminder!!.placeName.ifBlank { "地点" }
                        reminder!!.wifiEnabled ->
                            "连上 " + (reminder!!.ssidList.firstOrNull() ?: "WiFi")
                        else -> null
                    },
                    steps = steps,
                    timeline = timeline,
                    nextText = nextText,
                    firedCount = firedCount,
                    elapsedSec = elapsedSec,
                    acked = acked,
                    snoozing = snoozing,
                    onDismiss = {
                        // 声音立刻停、通知立刻撤；页面停 640ms 让小狗从"冲过来"变"坐好看你"
                        stopEverything()
                        ReminderNotifier.cancel(this, reminderId)
                        logAck("知道了", title)
                        acked = true
                        lifecycleScope.launch {
                            delay(640)
                            finish()
                        }
                    },
                    onSnooze = { minutes ->
                        logAck("稍后 $minutes 分钟", title)
                        if (reminderId > 0L) {
                            ReminderScheduler.scheduleAt(
                                this,
                                System.currentTimeMillis() + minutes * 60_000L,
                                reminderId,
                                title,
                                note,
                                snoozeMinutes = minutes,
                            )
                            lifecycleScope.launch {
                                repository.log(
                                    EventType.NOTE,
                                    detail = "稍后提醒：$minutes 分钟后（提醒：$title）",
                                )
                            }
                        } else {
                            AlarmScheduler.scheduleAlarmClock(
                                this,
                                System.currentTimeMillis() + minutes * 60_000L,
                                "$title（稍后 $minutes 分钟）",
                            )
                        }
                        ReminderNotifier.cancel(this, reminderId)
                        stopEverything()
                        // 不立刻关页：让「追球跑开」的动画播完一轮（约 1.3 秒）
                        snoozing = true
                        lifecycleScope.launch {
                            delay(1300)
                            finish()
                        }
                    },
                    onSuppressToday = {
                        // 「今天不再」：抑制到明天 0 点（数据闸门在 TriggerRules.suppressGateUnmet）
                        val id = reminderId
                        val t = title
                        lifecycleScope.launch {
                            runCatching {
                                ReminderDb.get(this@RingActivity).reminderDao()
                                    .setSuppressUntil(id, nextMidnight())
                            }
                            runCatching { ReminderScheduler.cancel(this@RingActivity, id) }
                            logAck("今天不再", t)
                            ReminderNotifier.cancel(this@RingActivity, id)
                            stopEverything()
                            finish()
                        }
                    },
                )
            }
        }
    }

    /** 记一条「用户在响铃页做了什么」——详情页的「最近三次」要用 */
    private fun logAck(action: String, title: String) {
        val seconds = RingPlayer.elapsedMs() / 1000
        lifecycleScope.launch {
            repository.log(
                EventType.RING_ACK,
                detail = "响铃响应：$action（响了 $seconds 秒）· $title",
            )
        }
    }

    private fun stopEverything() {
        RingPlayer.stop()
    }

    override fun onDestroy() {
        // 只在"真的退出"时收尾；配置变化（比如旋转）不该把闹钟掐掉
        if (isFinishing) {
            // 兜底：不管是点「知道了」还是返回键退出，都不应该把这条提醒的通知留在状态栏
            ReminderNotifier.cancel(this, currentReminderId)
            stopEverything()
        }
        super.onDestroy()
    }

    companion object {
        /** 自定义铃声 URI（字符串）。空 = 系统默认闹钟铃声 */
        const val EXTRA_RINGTONE = "ruyue.extra.ringtone"

        /** 渐强秒数；0 = 不渐强，一上来就是最大音量 */
        const val EXTRA_RAMP_SECONDS = "ruyue.extra.ramp_seconds"
    }
}

private fun kindName(kind: ConditionKind): String = when (kind) {
    ConditionKind.TIME -> "时间"
    ConditionKind.WIFI -> "WiFi"
    ConditionKind.PLACE -> "地点"
}

/** COND_MET 的 detail 太长，时间线里压成一句话 */
private fun shortCond(detail: String): String = when {
    detail.contains("WiFi 条件已满足") -> "连上 Wi-Fi"
    detail.contains("地点条件已满足") -> "进入范围"
    else -> "时间到"
}

private fun buildStepsFrom(r: Reminder, happened: List<com.ruyue.alarm.data.EventLog>): List<ConditionStep> {
    val allMet = r.matchMode == com.ruyue.alarm.data.MatchMode.ALL
    fun met(marker: String): Boolean = allMet || happened.any { it.detail?.contains(marker) == true }
    return buildList {
        if (r.timeEnabled) {
            add(ConditionStep(ConditionKind.TIME, "%02d:%02d".format(r.hour, r.minute), met("时间条件")))
        }
        if (r.wifiEnabled) {
            add(ConditionStep(ConditionKind.WIFI, r.ssidList.firstOrNull() ?: "WiFi", met("WiFi 条件")))
        }
        if (r.placeEnabled) {
            add(ConditionStep(ConditionKind.PLACE, r.placeName.ifBlank { "地点" }, met("地点条件")))
        }
    }
}

// ==================== 界面 ====================

@Composable
private fun RingScreen(
    title: String,
    note: String,
    hour: Int?,
    minute: Int?,
    /** 非时间提醒时，大标题位置改显示触发源（如「到达 公司」） */
    sourceLabel: String?,
    steps: List<ConditionStep>,
    timeline: List<Pair<Long, String>>,
    nextText: String?,
    firedCount: Int,
    elapsedSec: Int,
    acked: Boolean,
    /** 按下「稍后」后为 true → 小狗改播「追球跑开」 */
    snoozing: Boolean,
    onDismiss: () -> Unit,
    onSnooze: (Int) -> Unit,
    onSuppressToday: () -> Unit,
) {
    val c = LocalRuyueColors.current
    RuyueBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            // ---- 顶部：现在 · 星期X ｜ 已响 N 秒 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "现在 · " + weekday(),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textSecondary,
                )
                Spacer(Modifier.weight(1f))
                StatusPill(text = "已响 ${elapsedSec} 秒", color = c.time)
            }

            Spacer(Modifier.height(10.dp))

            // ---- 大时间 + 小狗 ----
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (hour != null && minute != null) {
                        "%02d:%02d".format(hour, minute)
                    } else {
                        sourceLabel.orEmpty()
                    },
                    style = if (hour != null) {
                        MaterialTheme.typography.displayLarge
                    } else {
                        // 中文用 74sp 会溢出，降两档
                        MaterialTheme.typography.headlineLarge
                    },
                    color = c.text,
                )
                Spacer(Modifier.weight(1f))
                if (acked) {
                    // 点完「知道了」→ 切回静态的「坐好看你」（设计稿的"被回应"感）
                    DogMascot(pose = DogPose.SIT_B, size = 120.dp)
                } else {
                    // 同一个 composable，只换帧数组：响铃=奔跑，按了稍后=追球跑开
                    DogFrameLoop(
                        frames = if (snoozing) CHASE_FRAMES else RUN_FRAMES,
                        frameMillis = if (snoozing) 180 else 140,
                        height = 110.dp,
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = c.text,
            )
            if (note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "你说过「$note」",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                )
            }

            Spacer(Modifier.height(16.dp))

            // ---- 可滚动中段（小屏也不会挤掉按钮） ----
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (steps.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(c.card)
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "为什么现在响",
                                style = MaterialTheme.typography.labelMedium,
                                color = c.textTertiary,
                                modifier = Modifier.weight(1f),
                            )
                            StatusPill(
                                text = when (steps.size) {
                                    1 -> "这一个条件满足了"
                                    2 -> "两个都满足"
                                    else -> "三个都满足"
                                },
                                color = c.time,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        if (steps.size == 1) {
                            SingleConditionRail(step = steps.first(), color = kindColor(steps.first().kind, c))
                        } else {
                            PathRail(steps = steps, modifier = Modifier.fillMaxWidth())
                        }
                        if (timeline.size >= 2) {
                            Spacer(Modifier.height(12.dp))
                            val first = timeline.first().first
                            val used = ((timeline.last().first - first) / 1000).coerceAtLeast(0)
                            Text(
                                text = "从第一个条件满足到响，用了 $used 秒",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textTertiary,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (timeline.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(c.card.copy(alpha = 0.55f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        timeline.forEach { (_, line) ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = nextText?.let { "下一次 $it" } ?: "不会再自动响",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    if (firedCount > 0) {
                        Text(
                            text = "共响过 $firedCount 次",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textTertiary,
                        )
                    }
                }
            }

            // ---- 三个动作 ----
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.time)
                    .clickableNoRipple(onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "知道了",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.onAccent,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SnoozeButton("稍后 5 分钟", Modifier.weight(1f)) { onSnooze(5) }
                SnoozeButton("稍后 10 分钟", Modifier.weight(1f)) { onSnooze(10) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "今天不再提醒它",
                style = MaterialTheme.typography.labelLarge,
                color = c.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickableNoRipple(onSuppressToday)
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "点「知道了」停止响铃；稍后提醒到点会再响一次",
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SnoozeButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalRuyueColors.current
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.card)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
    }
}

/** 响铃页要立刻响应，涟漪动画没意义（而且闹钟页面不需要"按下感"） */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = null, indication = null, onClick = onClick)

private fun weekday(): String {
    val cal = Calendar.getInstance()
    val dow = arrayOf("日", "一", "二", "三", "四", "五", "六")[cal.get(Calendar.DAY_OF_WEEK) - 1]
    return "星期$dow"
}
