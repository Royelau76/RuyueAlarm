package com.ruyue.alarm.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ruyue.alarm.data.EventType
import com.ruyue.alarm.data.EventWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 响铃播放器（进程级单例）。
 *
 * ## 为什么从 RingActivity 搬出来（v0.7.0）
 *
 * Android 在**亮屏解锁**状态下会把全屏意图（full screen intent）降级成横幅通知 ——
 * 这是系统的防打扰设计，第三方 App 改不了。原来的实现里**声音是由响铃页播放的**，
 * 于是用户正在用手机时：只弹横幅 + 一声提示音，直到用户点开横幅才开始真正响铃。
 * 实测 2026-10-27 09:55 那次：`ALARM_FIRED 09:55:06` → `RING_OPENED 09:55:41`，**晚了 35 秒**。
 *
 * 现在声音由这个播放器负责：**一触发就开始响**，响铃页来不来都不影响体验；
 * 响铃页只负责界面（以及"我知道了/稍后提醒"这两个动作时把声音停掉）。
 *
 * 进程由常驻哨兵（前台服务）保着，所以播放器不会因为进程回收而中断。
 */
object RingPlayer {

    /** 渐强起始音量（占最大音量的比例）——25% 起步：再低容易被听成"根本没响" */
    private const val START_VOLUME = 0.25f

    /** 最长连续响铃时间：万一没人理它，5 分钟后自动停（省电，也避免"永远在响"） */
    private const val MAX_RING_MS = 5 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var player: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var rampJob: Job? = null
    private var autoStopJob: Job? = null
    private var appContext: Context? = null

    /**
     * 5 分钟超时自动停止后置为 true（**只有超时这一条路会置位**）。
     *
     * 为什么需要这个信号：`stop()` 只停了声音/震动/唤醒锁和通知，**页面不会自己关** ——
     * 而响铃页带着 `FLAG_KEEP_SCREEN_ON`，于是屏幕会一直亮着。
     * 实测 10-31 22:30 的「刮胡子」没人处理，日志只写着"响铃 5 分钟无人处理，已自动停止"，
     * 但屏幕亮了整夜（3 天耗电 2.65% 里最大的一笔）。
     * 响铃页据此把自己关掉 → 屏幕跟着熄灭，而"响铃期间亮屏"这个体验不变。
     */
    private val _autoStopped = MutableStateFlow(false)
    val autoStopped: StateFlow<Boolean> = _autoStopped.asStateFlow()

    /** 现在是否正在响（响铃页据此判断要不要补一次播放，避免双份声音） */
    @Synchronized
    fun isPlaying(): Boolean = player != null || ringtone != null

    /**
     * 开始响铃。已在响的话会先停掉旧的（比如两条提醒挨着触发）。
     *
     * @param reminderId 仅用于"自动停止时把那条通知也撤掉"
     * @param uriString 自定义铃声 URI；空 = 系统默认闹钟铃声
     * @param rampSeconds 渐强秒数；0 = 一上来就是最大音量
     */
    @Synchronized
    fun start(context: Context, reminderId: Long, uriString: String, rampSeconds: Int) {
        stop()
        _autoStopped.value = false

        val app = context.applicationContext
        appContext = app
        acquireWakeLock(app)

        val uri = resolveRingtoneUri(app, uriString)
        if (uri == null) {
            EventWriter.write(app, EventType.NOTE, detail = "响铃失败：拿不到任何铃声 URI")
            return
        }

        val byPlayer = runCatching { startWithMediaPlayer(uri, rampSeconds) }.isSuccess
        val path = if (byPlayer) {
            "MediaPlayer"
        } else {
            val byRingtone = runCatching { startWithRingtone(uri, rampSeconds) }.isSuccess
            if (byRingtone) "Ringtone 兜底" else "播放失败"
        }

        startVibration()

        // 把「用了哪条音频通路」写进证据日志 —— 下次「没声音 / 没渐强 / 太小声」就不用猜了。
        // 闹钟音量是「绝对音量」的唯一来源：MediaPlayer 的 setVolume 只是在这个音量上再缩放。
        val am = app.getSystemService(AudioManager::class.java)
        val volume = am?.getStreamVolume(AudioManager.STREAM_ALARM) ?: -1
        val max = am?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: -1
        val ramp = if (rampSeconds > 0) "渐强 ${rampSeconds}s" else "不渐强"
        EventWriter.write(
            app,
            EventType.NOTE,
            detail = "响铃音频：$path，$ramp，闹钟音量 $volume/$max，uri=$uri",
        )

        // 兜底自动停：没人点也没人关的情况下，5 分钟后自己停，并把通知一起撤掉
        //（通知是 ongoing 的，不撤掉会一直赖在状态栏）
        autoStopJob = scope.launch {
            delay(MAX_RING_MS)
            stop()
            runCatching { ReminderNotifier.cancel(app, reminderId) }
            // 置位后响铃页会自己 finish() —— 否则 KEEP_SCREEN_ON 会让屏幕一直亮着
            _autoStopped.value = true
            EventWriter.write(
                app,
                EventType.NOTE,
                detail = "响铃 ${MAX_RING_MS / 60_000} 分钟无人处理，已自动停止并关闭响铃页（屏幕随后熄灭）",
            )
        }
    }

    /** 停掉声音、震动、唤醒锁（幂等） */
    @Synchronized
    fun stop() {
        rampJob?.cancel(); rampJob = null
        autoStopJob?.cancel(); autoStopJob = null

        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null

        runCatching { ringtone?.stop() }
        ringtone = null

        runCatching { vibrator?.cancel() }
        vibrator = null

        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    // ---------------- 内部 ----------------

    /**
     * ⚠️ 默认铃声必须用 `getActualDefaultRingtoneUri`：
     * `getDefaultUri(TYPE_ALARM)` 返回的是 `content://settings/system/alarm_alert` 这种
     * 「指向默认值」的 URI，SettingsProvider 不提供文件描述符，MediaPlayer 打不开 →
     * 会静默掉进 Ringtone 兜底（v0.5.2 修过的坑）。
     */
    private fun resolveRingtoneUri(context: Context, uriString: String): Uri? =
        uriString.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    /** USAGE_ALARM：走闹钟音量通道，默认能穿透「勿扰」 */
    private fun alarmAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun startWithMediaPlayer(uri: Uri, rampSeconds: Int) {
        val context = appContext ?: error("appContext 丢失")
        val mp = MediaPlayer().apply {
            setAudioAttributes(alarmAttributes())
            setDataSource(context, uri)
            isLooping = true
            prepare()
        }
        val startVolume = if (rampSeconds > 0) START_VOLUME else 1f
        mp.setVolume(startVolume, startVolume)
        mp.start()
        player = mp

        if (rampSeconds > 0) {
            rampJob = scope.launch {
                val steps = (rampSeconds * 4).coerceAtLeast(1) // 每 250ms 一步
                repeat(steps) { i ->
                    delay(250)
                    val volume = START_VOLUME + (1f - START_VOLUME) * (i + 1) / steps
                    runCatching { player?.setVolume(volume, volume) }
                }
            }
        }
    }

    /** 兜底通路：MediaPlayer 打不开这个 URI 时用 Ringtone，**同样做渐强** */
    private fun startWithRingtone(uri: Uri, rampSeconds: Int) {
        val context = appContext ?: error("appContext 丢失")
        val rt = RingtoneManager.getRingtone(context, uri) ?: error("getRingtone 返回 null")
        rt.audioAttributes = alarmAttributes()
        val startVolume = if (rampSeconds > 0) START_VOLUME else 1f
        rt.setVolume(startVolume)
        rt.play()
        ringtone = rt

        if (rampSeconds > 0) {
            rampJob = scope.launch {
                val steps = (rampSeconds * 4).coerceAtLeast(1)
                repeat(steps) { i ->
                    delay(250)
                    val volume = START_VOLUME + (1f - START_VOLUME) * (i + 1) / steps
                    runCatching { ringtone?.setVolume(volume) }
                }
            }
        }
    }

    /**
     * 响铃时同时震动。
     *
     * 闹钟音量是用户自己的设置，本应用不该偷偷改；但「音量小 = 感知不到」是真实风险，
     * 震动是最便宜的补偿 —— 不依赖音量、也不需要额外授权弹窗，且 USAGE_ALARM 能穿透静音。
     */
    private fun startVibration() {
        val context = appContext ?: return
        runCatching {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            } ?: return

            // 响 600ms、停 800ms，从头循环（repeat = 0）
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 600, 800), 0)
            v.vibrate(effect, alarmAttributes())
            vibrator = v
        }
    }

    private fun acquireWakeLock(context: Context) {
        runCatching {
            val pm = context.getSystemService(PowerManager::class.java) ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ruyue:ring").apply {
                setReferenceCounted(false)
                acquire(MAX_RING_MS + 60_000L)
            }
        }
    }
}
