package com.ruyue.alarm.alarm

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.ruyue.alarm.data.EventType
import com.ruyue.alarm.data.LogRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
 * 现在声音由 [RingPlayer] 在**通知发出时**就开始播，本页只负责：
 *  · 显示是哪一个提醒、锁屏弹出还是横幅
 *  · 「我知道了 / 稍后提醒」两个动作时把声音停掉
 *
 * 页面上仍然明确告诉你这次是「锁屏全屏弹出」还是「只走了横幅」，这个区分是排查
 * 「为什么没弹出来」的关键（M0 第 3 项留下的判读习惯）。
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

        setContent {
            RingScreen(
                label = title,
                note = note,
                locked = locked,
                onDismiss = {
                    // 关掉响铃页的同时**必须撤掉通知**：否则它会一直留在状态栏，
                    // 之后点那条残留通知会再拉起本页 → 又响一遍（v0.6.1 修过）
                    ReminderNotifier.cancel(this, reminderId)
                    stopEverything()
                    finish()
                },
                onSnooze = {
                    if (reminderId > 0L) {
                        // 正式提醒：10 分钟后再响一次
                        ReminderScheduler.scheduleAt(
                            this,
                            System.currentTimeMillis() + 10 * 60 * 1000L,
                            reminderId,
                            title,
                            note,
                        )
                    } else {
                        // M0 的测试闹钟：1 分钟后，方便连续验证全屏意图
                        AlarmScheduler.scheduleAlarmClock(
                            this,
                            System.currentTimeMillis() + 60_000L,
                            "$title（稍后提醒）",
                        )
                    }
                    // 稍后提醒会重新发通知，旧的这条也要撤掉，否则状态栏会叠两条
                    ReminderNotifier.cancel(this, reminderId)
                    stopEverything()
                    finish()
                },
            )
        }
    }

    private fun stopEverything() {
        RingPlayer.stop()
    }

    override fun onDestroy() {
        // 只在"真的退出"时收尾；配置变化（比如旋转）不该把闹钟掐掉
        if (isFinishing) {
            // 兜底：不管是点「我知道了」还是返回键退出，都不应该把这条提醒的通知留在状态栏
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

@Composable
private fun RingScreen(
    label: String,
    note: String,
    locked: Boolean,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    Surface(
        color = Color(0xFF101418),
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("提醒", color = Color(0xFF8AB4F8), fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            if (note.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = note,
                    color = Color(0xFFCFD8DC),
                    fontSize = 17.sp,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = if (locked) {
                    "锁屏状态下弹出 ✅ 走的是全屏意图路径"
                } else {
                    "屏幕未锁定 → 系统只在通知栏出横幅（铃声已在后台播放）"
                },
                color = Color(0xFFB0BEC5),
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(36.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("我知道了")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) {
                Text("稍后提醒")
            }
        }
    }
}
