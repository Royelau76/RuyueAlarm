package com.ruyue.alarm.alarm

import android.app.KeyguardManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 响铃页。M0 第 3 项：验证锁屏状态下的提醒能不能全屏弹出、能不能响。
 *
 * 页面上会明确告诉你「这次是锁屏弹出的（走全屏意图）」还是「只走了普通通知」，
 * 这个区分是后面排查「为什么没弹出来」的关键。
 */
@AndroidEntryPoint
class RingActivity : ComponentActivity() {

    @Inject lateinit var repository: LogRepository

    private var ringtone: Ringtone? = null
    private var player: MediaPlayer? = null
    private var rampJob: Job? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /** 本页对应的提醒 id —— onDestroy 时要用它撤掉通知 */
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

        acquireWakeLock()
        playAlarmSound(
            intent?.getStringExtra(EXTRA_RINGTONE).orEmpty(),
            intent?.getIntExtra(EXTRA_RAMP_SECONDS, 0) ?: 0,
        )
        startVibration()

        val reminderId = intent?.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, 0L) ?: 0L
        currentReminderId = reminderId
        val title = intent?.getStringExtra(ReminderScheduler.EXTRA_TITLE)
            ?: intent?.getStringExtra(AlarmScheduler.EXTRA_LABEL)
            ?: "提醒"
        val note = intent?.getStringExtra(ReminderScheduler.EXTRA_NOTE).orEmpty()

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
                    // 之后点那条残留通知会再拉起本页 → 又响一遍（v0.6.0 及之前的 bug）
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

    /**
     * 播放闹钟铃声（带可选渐强）。
     *
     * ⚠️ 踩过的坑（"渐强没生效"的真实原因，v0.5.2 修）：
     * 默认铃声必须用 `getActualDefaultRingtoneUri` —— `getDefaultUri(TYPE_ALARM)` 返回的是
     * `content://settings/system/alarm_alert` 这种**指向默认值**的 URI，SettingsProvider
     * 不提供文件描述符，`MediaPlayer.setDataSource` 必然失败 → 静默掉进 Ringtone 兜底。
     * 而当时兜底路径没有渐强，于是「设了渐强却一上来就最响」。
     *
     * 现在两条路径**都能渐强**：MediaPlayer 用 `setVolume`，Ringtone 也有 `setVolume`
     * （javap 对 android-34 确认过，API 21 起可用）。
     */
    private fun playAlarmSound(uriString: String, rampSeconds: Int) {
        val uri = resolveRingtoneUri(uriString) ?: return

        val byPlayer = runCatching { startWithMediaPlayer(uri, rampSeconds) }.isSuccess
        val path = if (byPlayer) {
            "MediaPlayer"
        } else {
            val byRingtone = runCatching { startWithRingtone(uri, rampSeconds) }.isSuccess
            if (byRingtone) "Ringtone 兜底" else "播放失败"
        }

        logAudioPath(path, rampSeconds, uri)
    }

    private fun resolveRingtoneUri(uriString: String): Uri? =
        uriString.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    /** USAGE_ALARM：走闹钟音量通道，默认能穿透「勿扰」 */
    private fun alarmAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun startWithMediaPlayer(uri: Uri, rampSeconds: Int) {
        val mp = MediaPlayer().apply {
            setAudioAttributes(alarmAttributes())
            setDataSource(this@RingActivity, uri)
            isLooping = true
            prepare()
        }
        val startVolume = if (rampSeconds > 0) START_VOLUME else 1f
        mp.setVolume(startVolume, startVolume)
        mp.start()
        player = mp

        if (rampSeconds > 0) {
            rampJob = lifecycleScope.launch {
                val steps = (rampSeconds * 4).coerceAtLeast(1) // 每 250ms 一步
                repeat(steps) { i ->
                    delay(250)
                    val volume = START_VOLUME + (1f - START_VOLUME) * (i + 1) / steps
                    runCatching { player?.setVolume(volume, volume) }
                }
                logRampDone(rampSeconds)
            }
        }
    }

    private fun startWithRingtone(uri: Uri, rampSeconds: Int) {
        val rt = RingtoneManager.getRingtone(this, uri) ?: error("getRingtone 返回 null")
        rt.audioAttributes = alarmAttributes()
        val startVolume = if (rampSeconds > 0) START_VOLUME else 1f
        rt.setVolume(startVolume)
        rt.play()
        ringtone = rt

        if (rampSeconds > 0) {
            rampJob = lifecycleScope.launch {
                val steps = (rampSeconds * 4).coerceAtLeast(1)
                repeat(steps) { i ->
                    delay(250)
                    val volume = START_VOLUME + (1f - START_VOLUME) * (i + 1) / steps
                    runCatching { ringtone?.setVolume(volume) }
                }
                logRampDone(rampSeconds)
            }
        }
    }

    /** 把「用了哪条音频通路」写进证据日志 —— 下次「没声音 / 没渐强 / 太小声」就不用猜了 */
    private fun logAudioPath(path: String, rampSeconds: Int, uri: Uri) {
        val ramp = if (rampSeconds > 0) "渐强 ${rampSeconds}s" else "不渐强"
        // 闹钟音量是「绝对音量」的唯一来源：MediaPlayer 的 setVolume 只是在这个音量上再缩放，
        // 所以「听起来小」十有八九是系统闹钟音量本身不高。把数值记下来，省得下次又靠猜。
        val am = getSystemService(AudioManager::class.java)
        val volume = am?.getStreamVolume(AudioManager.STREAM_ALARM) ?: -1
        val max = am?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: -1
        lifecycleScope.launch {
            repository.log(
                EventType.NOTE,
                detail = "响铃音频：$path，$ramp，闹钟音量 $volume/$max，uri=$uri",
            )
        }
    }

    /** 渐强跑完再记一条 —— 用来区分「设备音量小」和「渐强没跑完」 */
    private fun logRampDone(rampSeconds: Int) {
        lifecycleScope.launch {
            repository.log(
                EventType.NOTE,
                detail = "渐强完成（${rampSeconds}s），已到满音量（绝对值仍受系统闹钟音量限制）",
            )
        }
    }

    /**
     * 响铃时同时震动。
     *
     * 为什么加：闹钟音量是用户自己的设置，本应用不该偷偷改（而且改完要恢复，进程被杀就恢复不了）。
     * 但「音量小 = 感知不到」是真实风险，震动是最便宜的补偿 —— 不依赖音量、也不需要额外授权弹窗。
     */
    private fun startVibration() {
        runCatching {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            } ?: return

            // 响 600ms、停 800ms，从头循环（repeat = 0）
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 600, 800), 0)
            v.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
            vibrator = v
        }
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(PowerManager::class.java) ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ruyue:ring").apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L)
            }
        }
    }

    private fun stopEverything() {
        rampJob?.cancel()
        rampJob = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        // 兜底：不管是点「我知道了」还是返回键退出，都不应该把这条提醒的通知留在状态栏
        ReminderNotifier.cancel(this, currentReminderId)
        stopEverything()
        super.onDestroy()
    }

    companion object {
        /** 自定义铃声 URI（字符串）。空 = 系统默认闹钟铃声 */
        const val EXTRA_RINGTONE = "ruyue.extra.ringtone"

        /** 渐强秒数；0 = 不渐强，一上来就是最大音量 */
        const val EXTRA_RAMP_SECONDS = "ruyue.extra.ramp_seconds"

        /** 渐强的起始音量（占最大音量的比例）——25% 起步：再低容易听成"根本没响" */
        private const val START_VOLUME = 0.25f
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
                    "屏幕未锁定，只走了普通通知 ⚠️ 系统不会全屏弹窗"
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
