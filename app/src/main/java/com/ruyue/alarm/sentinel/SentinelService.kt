package com.ruyue.alarm.sentinel

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.ruyue.alarm.MainActivity
import com.ruyue.alarm.R
import com.ruyue.alarm.RuyueApp
import com.ruyue.alarm.data.Db
import com.ruyue.alarm.data.EventLog
import com.ruyue.alarm.data.EventType
import com.ruyue.alarm.data.LogRepository
import com.ruyue.alarm.data.ReminderRepository
import com.ruyue.alarm.trigger.TriggerEngine
import com.ruyue.alarm.util.LocationHelper
import com.ruyue.alarm.util.WifiReader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * M0 的哨兵：一个常驻前台服务，做两件事
 *  1. 注册 WiFi 网络回调，记录连上/断开事件（拿 SSID、BSSID）
 *  2. 每 5 分钟写一条心跳
 *
 * 心跳的意义：如果服务被系统或 ROM 杀掉，心跳就断了。事后翻日志，
 * 「最后一条心跳」到「SERVICE_STOP」之间的空档就是它被杀的时刻 —— 这是
 * 判断保活策略有没有生效的唯一硬证据，比看电池统计靠得住。
 */
@AndroidEntryPoint
class SentinelService : Service() {

    @Inject lateinit var repository: LogRepository
    @Inject lateinit var reminders: ReminderRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var connectivity: ConnectivityManager? = null
    private var lastSsid: String? = null

    /** 本实例是否已经记过 SERVICE_START（放在 onStartCommand 里才能知道"为什么起来的"） */
    private var startLogged = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ServiceState.running.value = true
        ServiceState.startedAt.value = System.currentTimeMillis()
        startForegroundCompat(buildNotification())
        registerWifiCallback()
        refreshLocationUpdates()
        // 高德围栏探针（M0 第 4 项实验）：只有用户在验证页主动开启过才挂上。
        // 为什么放在服务里：高德明确要求围栏客户端跑在**常驻的本地服务**里，
        // 否则息屏/后台时测不到触发（而"后台能不能触发"正是这次要验的点之一）。
        if (com.ruyue.alarm.amap.AmapFenceProbe.isEnabled(this)) {
            com.ruyue.alarm.amap.AmapFenceProbe.attach(this)
        }
        // 服务活着的时候也把看门狗排上，避免自愈链因为某次漏排而断掉
        SentinelWatchdog.schedule(this)

        scope.launch {
            heartbeatLoop()
        }
    }

    /**
     * 为什么服务会起来？
     *
     * 实测（2026-10-20）出现过两次 9 分钟内的静默重启，日志里只有 `SERVICE_START`，
     * **分不清是系统回收后 START_STICKY 拉回来的、还是用户点开 App** —— 这正是排查最需要的信息。
     * START_STICKY 重新创建服务时传入的 intent 是 null，据此可以明确区分。
     */
    private fun launchReason(intent: Intent?): String = when {
        intent == null -> "系统拉起（进程曾被回收，START_STICKY）"
        intent.action == ACTION_REFRESH -> "刷新地点监听"
        intent.action == ACTION_STOP -> "用户停止请求"
        else -> "正常启动"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startLogged) {
            startLogged = true
            scope.launch {
                repository.log(
                    EventType.SERVICE_START,
                    detail = "哨兵启动 pid=${android.os.Process.myPid()}，原因：${launchReason(intent)}",
                )
            }
        }
        if (intent?.action == ACTION_STOP) {
            setWanted(this, false)
            // 用户主动停用 → 看门狗不要把它拉回来
            SentinelWatchdog.cancel(this)
            stopSelf()
            return Service.START_NOT_STICKY
        }
        if (intent?.action == ACTION_REFRESH) {
            // 提醒配置变了：重新评估要不要开/关地点监听
            refreshLocationUpdates()
            return Service.START_STICKY
        }
        // 高德围栏探针的开/关（由 AmapFenceProbe 从验证页发指令过来）
        if (intent?.action == ACTION_AMAP_START) {
            com.ruyue.alarm.amap.AmapFenceProbe.attach(this)
            return Service.START_STICKY
        }
        if (intent?.action == ACTION_AMAP_STOP) {
            com.ruyue.alarm.amap.AmapFenceProbe.detach(this)
            return Service.START_STICKY
        }
        setWanted(this, true)
        return Service.START_STICKY
    }

    override fun onDestroy() {
        ServiceState.running.value = false
        callback?.let { cb -> connectivity?.let { runCatching { it.unregisterNetworkCallback(cb) } } }
        detachLocationUpdates()
        // 高德围栏客户端也跟着服务一起收（进程没了它本来也活不成，这里只是干净收尾）
        runCatching { com.ruyue.alarm.amap.AmapFenceProbe.detach(this) }

        // onDestroy 之后 scope 就要被取消了，所以另起一个短命线程把「服务停止」写进库，
        // 保证这条最关键的证据不会因为协程被取消而丢掉。
        val appContext = applicationContext
        Thread {
            runCatching {
                Db.get(appContext).eventLogDao().insertBlocking(
                    EventLog(type = EventType.SERVICE_STOP, detail = "哨兵停止（可能是被手动停/被系统杀）")
                )
            }
        }.start()

        scope.cancel()
        super.onDestroy()
    }

    // ---------------- WiFi 回调 ----------------

    private fun registerWifiCallback() {
        val cm = getSystemService(ConnectivityManager::class.java)
        if (cm == null) {
            scope.launch { repository.log(EventType.ERROR, detail = "ConnectivityManager 不可用") }
            return
        }
        connectivity = cm

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handleWifiEvent(EventType.WIFI_CONNECT)
            }

            override fun onLost(network: Network) {
                handleWifiEvent(EventType.WIFI_DISCONNECT)
            }
        }
        callback = cb

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        runCatching { cm.registerNetworkCallback(request, cb) }
            .onFailure { e ->
                scope.launch {
                    repository.log(EventType.ERROR, detail = "注册 WiFi 回调失败：${e.message}")
                }
            }
    }

    private fun handleWifiEvent(type: String) {
        // 注意：回调触发的那一刻网络状态可能已经翻转（connect 时还没真正连上、disconnect 时
        // 快照还停在旧网络）。所以**文案一律按事件类型写**，快照状态只当附加信息 ——
        // M0 实测里正是因为按快照写，出现过「WIFI_CONNECT 显示已断开」这种自相矛盾的记录。
        val isConnect = type == EventType.WIFI_CONNECT
        val previousSsid = lastSsid
        val snap = WifiReader.snapshot(this)
        if (snap.connected && snap.ssid != null) lastSsid = snap.ssid

        // 断开事件**不读「现在的」SSID**：那一刻 WifiInfo 处在切换中间态，读出来的往往是
        // 另一个网络的名字（实测出现过「断开的是 TP-LINK_5G_EF2C」这种误导 —— 而前后心跳
        // 明明都在 TP-LINK_1602_5G 上）。所以断开时只报「上次连的是谁」。
        val ssid = if (isConnect) (snap.ssid ?: lastSsid) else previousSsid

        val detail = if (isConnect) {
            buildString {
                append("已连上")
                append("（读取时快照：").append(if (snap.connected) "已连接" else "未连接").append("）")
                // 断开瞬间读到的快照很可能是「切换中间态」—— 这**不是**权限问题，
                // 文案不能写成「检查权限」误导排查方向。
                if (snap.redacted) append("；SSID 读不到（切换中间态属正常，若常态如此才查权限）")
                snap.error?.let { append("；$it") }
            }
        } else {
            "已断开（上次连接：${previousSsid ?: "未知"}）" + (snap.error?.let { "；$it" } ?: "")
        }

        ServiceState.eventCount.value += 1
        ServiceState.lastEvent.value = "${type.substringAfter('_')} ${ssid ?: "-"}"

        scope.launch {
            repository.log(type, ssid = ssid, bssid = snap.bssid, detail = detail)
            updateNotification()
        }

        // M1：把「连上了」交给触发引擎判定（去抖与冷却都在引擎里，不在服务里）
        if (isConnect && ssid != null) {
            scope.launch {
                runCatching {
                    TriggerEngine.onWifiConnected(this@SentinelService, ssid, reminders, repository)
                }.onFailure { e ->
                    repository.log(EventType.ERROR, detail = "WiFi 触发判定失败：${e.message}")
                }
            }
        }
    }

    // ---------------- 心跳 ----------------

    private suspend fun heartbeatLoop() {
        var last = System.currentTimeMillis()
        while (scope.isActive) {
            delay(ServiceState.HEARTBEAT_INTERVAL_MS)
            val now = System.currentTimeMillis()
            val gapSec = (now - last) / 1000
            last = now

            // 心跳里带上当前 WiFi 读取结果：这样「后台到底能不能读 SSID」每 5 分钟就有一条
            // 干净的证据，不受 connect/disconnect 回调竞态的影响。
            val wifi = WifiReader.snapshot(this)

            // 顺带维护 lastSsid：断开事件要靠它回答「上次连的是谁」。只在 connect 回调里更新的话，
            // 一旦那段内存丢了（服务重启）就会退化成「未知」—— 心跳每 5 分钟补一次，很快自愈。
            if (wifi.connected && wifi.ssid != null) lastSsid = wifi.ssid

            repository.log(
                EventType.HEARTBEAT,
                ssid = wifi.ssid,
                detail = buildString {
                    append("心跳，间隔 ${gapSec}s")
                    if (gapSec > 600) append("（超过 10 分钟，说明进过 Doze 或被挂起）")
                    append("；当前WiFi=")
                    append(
                        when {
                            !wifi.connected -> "未连接"
                            wifi.ssid != null -> wifi.ssid
                            else -> "(抹除)"
                        }
                    )
                    append("；定位总开关=").append(if (wifi.locationEnabled) "开" else "关")
                },
            )
            updateNotification()
        }
    }

    // ---------------- 地点（自研围栏）----------------

    private var locationListener: LocationListener? = null
    private var locationManager: LocationManager? = null

    /**
     * 只在「存在启用的地点提醒」时才注册定位更新 —— 纯 WiFi 触发的用户不该被白耗电。
     * 60 秒 / 100 米：够用来判断「有没有到达」，又不至于太费电。
     */
    private fun refreshLocationUpdates() {
        scope.launch {
            val needed = runCatching { reminders.hasPlaceReminder() }.getOrDefault(false)

            if (!needed) {
                if (locationListener != null) {
                    detachLocationUpdates()
                    repository.log(EventType.NOTE, detail = "已停止地点监听（没有启用的地点提醒）")
                }
                return@launch
            }
            if (!LocationHelper.hasFinePermission(this@SentinelService)) {
                repository.log(EventType.ERROR, detail = "配了地点提醒但缺少精确定位权限，围栏无法工作")
                return@launch
            }
            if (locationListener != null) return@launch // 已经在跑

            val lm = getSystemService(LocationManager::class.java) ?: return@launch
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    scope.launch {
                        runCatching {
                            TriggerEngine.onLocationUpdate(
                                this@SentinelService,
                                location.latitude,
                                location.longitude,
                                location.accuracy,
                                reminders,
                                repository,
                            )
                        }.onFailure { e ->
                            repository.log(EventType.ERROR, detail = "地点判定失败：${e.message}")
                        }
                    }
                }
            }

            val ok = runCatching {
                // ⚠️ 长期只挂**网络定位**，不挂 GPS。
                //
                // 实测教训（2026-10-14 电池页）：GPS + 网络一起挂时，系统会为这个请求维持一个
                // GNSS 跟踪会话，60 秒的间隔几乎等于持续定位 —— 如约占了 **16.23%**
                // （同一时段高德导航 49%）。国行 HyperOS 没有 GMS，用不了会自动降功耗的
                // FusedLocationProvider，这个取舍只能自己做：
                // **GPS 只在「接近目标、需要精确确认」时短暂开启**（见 TriggerEngine 的停留复核）。
                //
                // 网络定位的代价是精度粗（城市里几十到几百米），所以判定拆成两层：
                // 粗定位判断"接近了没有"，GPS 短请求确认"到底在不在半径内"。
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 60_000L, 200f, listener, Looper.getMainLooper(),
                )
            }.isSuccess

            if (ok) {
                locationManager = lm
                locationListener = listener
                repository.log(
                    EventType.NOTE,
                    detail = "已启动地点监听（网络定位 60s / 200m；接近目标时才短暂开 GPS 精确确认）",
                )
            } else {
                repository.log(EventType.ERROR, detail = "注册定位更新失败，地点提醒不会触发")
            }
        }
    }

    /** 纯清理、不写日志（onDestroy 里协程马上要被取消，写日志会丢） */
    private fun detachLocationUpdates() {
        val lm = locationManager
        val listener = locationListener
        if (lm != null && listener != null) {
            runCatching { lm.removeUpdates(listener) }
        }
        locationManager = null
        locationListener = null
    }

    // ---------------- 通知 ----------------

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // 只有拿到定位权限才带 location 类型：声明了类型却没权限，
            // startForeground 会直接抛 SecurityException 把服务打死，所以必须动态决定。
            val special = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            val type = if (LocationHelper.hasFinePermission(this)) {
                special or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                special
            }
            ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // 走 IMPORTANCE_MIN 的静音渠道：状态栏不出图标，只在通知栏折叠区里（"无感"的极限）
        return NotificationCompat.Builder(this, RuyueApp.CHANNEL_SENTINEL_QUIET)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("如约")
            .setContentText("后台监听中 · ${uptimeText()}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(tap)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification() {
        if (!ServiceState.running.value) return
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification())
        }
    }

    private fun uptimeText(): String {
        val minutes = (System.currentTimeMillis() - ServiceState.startedAt.value) / 60_000
        return if (minutes < 60) "${minutes}分" else "${minutes / 60}小时${minutes % 60}分"
    }

    companion object {
        const val ACTION_STOP = "com.ruyue.alarm.action.STOP_SENTINEL"
        const val ACTION_REFRESH = "com.ruyue.alarm.action.REFRESH_TRIGGERS"

        /** 高德围栏探针：开启 / 停止（M0 第 4 项实验用） */
        const val ACTION_AMAP_START = "com.ruyue.alarm.action.AMAP_PROBE_START"
        const val ACTION_AMAP_STOP = "com.ruyue.alarm.action.AMAP_PROBE_STOP"
        private const val NOTIF_ID = 1001
        private const val PREFS = "sentinel_prefs"
        private const val KEY_WANTED = "wanted"

        /** 用户是否「想要」哨兵运行 —— 开机/更新后靠这个决定要不要拉回来。 */
        fun isWanted(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_WANTED, false)

        fun setWanted(context: Context, wanted: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_WANTED, wanted).apply()
        }
    }
}
