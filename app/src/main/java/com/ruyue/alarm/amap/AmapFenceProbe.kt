package com.ruyue.alarm.amap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.amap.api.fence.GeoFence
import com.amap.api.fence.GeoFenceClient
import com.amap.api.fence.GeoFenceListener
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.DPoint
import com.ruyue.alarm.data.EventType
import com.ruyue.alarm.data.EventWriter
import com.ruyue.alarm.sentinel.SentinelController
import com.ruyue.alarm.sentinel.SentinelService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * 高德地理围栏探针 —— M0 第 4 项的验证工具（**只做实验，不参与正式提醒**）。
 *
 * 目的：拿高德本地围栏和我们自研的 `LocationManager` 围栏对比三件事 ——
 *   ① 进入 → 事件延迟（自研实测 61 秒 ~ 3 分 19 秒）
 *   ② 息屏 + 锁屏下还能不能触发
 *   ③ 跑一天耗电差多少（自研 0.03%）
 *
 * ## 几个必须记住的点（都是查文档 + javap 读 SDK 确认过的，别照记忆写）
 *
 * 1. 围栏的类在 **`com.amap.api.fence`**，不是 `com.amap.api.location`
 *    （`GeoFenceClient` / `GeoFence` / `GeoFenceListener` 都在 fence 包）。
 * 2. 触发**不是**通过定位回调，而是 **广播**：`createPendingIntent(action)` 之后
 *    自己注册一个接收器，从 Bundle 里取 `GeoFence.BUNDLE_KEY_FENCESTATUS`。
 * 3. 定位权限若被设成「模糊定位」，围栏**直接不工作**（官方明确说明）。
 * 4. 初始化前必须调 `updatePrivacyShow` / `updatePrivacyAgree`，否则 SDK 拒绝工作。
 * 5. 高德要求围栏客户端跑在**常驻的本地服务**里才能后台持续检测 ——
 *    所以真正的创建动作交给 [SentinelService]（本应用已经有一个常年活着的前台服务）。
 */
object AmapFenceProbe {

    /** 围栏触发广播的 action（自定义，仅本应用使用） */
    const val ACTION_FENCE = "com.ruyue.alarm.action.AMAP_FENCE_EVENT"

    private const val PREFS = "amap_probe"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val KEY_RADIUS = "radius"
    private const val CUSTOM_ID = "ruyue_probe"

    /** 页面要显示的状态。进程内单例，服务和 UI 在同一个进程，所以能直接共享。 */
    data class ProbeState(
        val enabled: Boolean = false,
        val attached: Boolean = false,
        val fenceCreated: Boolean = false,
        val lat: Double = 0.0,
        val lon: Double = 0.0,
        val radius: Float = 200f,
        val fenceCount: Int = 0,
        val lastEvent: String = "",
        val lastEventAt: Long = 0L,
        val error: String = "",
    )

    val state = MutableStateFlow(ProbeState())

    @Volatile private var client: GeoFenceClient? = null
    @Volatile private var receiver: BroadcastReceiver? = null

    // ---------------- 参数持久化 ----------------

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun lat(context: Context): Double = prefs(context).getString(KEY_LAT, null)?.toDoubleOrNull() ?: 0.0

    fun lon(context: Context): Double = prefs(context).getString(KEY_LON, null)?.toDoubleOrNull() ?: 0.0

    fun radius(context: Context): Float = prefs(context).getFloat(KEY_RADIUS, 200f)

    // ---------------- 对外操作 ----------------

    /**
     * 用给定坐标建围栏并开启。
     *
     * 真正的创建放到哨兵服务里做（见类注释第 5 点）：这里只保存参数 + 通知服务。
     */
    fun start(context: Context, lat: Double, lon: Double, radiusMeters: Float) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_LAT, lat.toString())
            .putString(KEY_LON, lon.toString())
            .putFloat(KEY_RADIUS, radiusMeters)
            .apply()

        state.update {
            it.copy(enabled = true, lat = lat, lon = lon, radius = radiusMeters, error = "")
        }

        // 先把哨兵拉起来（它是常驻 FGS，也是高德要求的"常驻本地服务"）
        SentinelController.start(context)
        runCatching {
            context.startService(
                Intent(context, SentinelService::class.java)
                    .setAction(SentinelService.ACTION_AMAP_START)
            )
        }.onFailure {
            state.update { s -> s.copy(error = "通知哨兵服务失败：${it.message}") }
        }
    }

    /** 页面取当前位置失败时把原因显示出来 —— 否则按钮点了没反应，很困惑 */
    fun reportLocationFailure(message: String) {
        state.update { it.copy(error = message) }
    }

    fun stop(context: Context) {        prefs(context).edit().putBoolean(KEY_ENABLED, false).apply()
        runCatching {
            context.startService(
                Intent(context, SentinelService::class.java)
                    .setAction(SentinelService.ACTION_AMAP_STOP)
            )
        }
        // 服务没在跑的话上面那条不会执行，本地也顺手收一下
        detach(context)
        EventWriter.write(context, EventType.NOTE, detail = "高德围栏探针已停止")
    }

    // ---------------- 生命周期（由 SentinelService 调用）----------------

    /** 在服务里创建围栏客户端 + 注册广播接收器（幂等） */
    @Synchronized
    fun attach(context: Context) {
        if (client != null) return
        val app = context.applicationContext

        if (!isEnabled(app)) return

        // ⚠️ 隐私合规：不调这两个 SDK 会直接拒绝工作（自用也躲不掉）
        runCatching {
            AMapLocationClient.updatePrivacyShow(app, true, true)
            AMapLocationClient.updatePrivacyAgree(app, true)
        }.onFailure { e ->
            state.update { it.copy(error = "隐私合规接口调用失败：${e.message}") }
        }

        runCatching {
            val option = AMapLocationClientOption().apply {
                setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy)
                setNeedAddress(false)
                setOnceLocation(false)
                setInterval(2000L)
                setLocationCacheEnable(true)
                setMockEnable(false)
            }

            val fenceClient = GeoFenceClient(app)
            fenceClient.setActivateAction(
                GeoFenceClient.GEOFENCE_IN or
                    GeoFenceClient.GEOFENCE_OUT or
                    GeoFenceClient.GEOFENCE_STAYED
            )
            fenceClient.setGeoFenceListener(GeoFenceListener { list, errorCode, _ ->
                // 这个回调同时也是**鉴权诊断口**：key/包名/SHA1 不匹配会以
                // ERROR_CODE_FAILURE_AUTH 的形式出现在这里
                val ok = errorCode == GeoFence.ADDGEOFENCE_SUCCESS
                val msg = errorText(errorCode)
                state.update {
                    it.copy(
                        fenceCreated = ok,
                        fenceCount = list?.size ?: 0,
                        error = if (ok) "" else "建围栏失败：$msg",
                    )
                }
                EventWriter.write(
                    app,
                    if (ok) EventType.NOTE else EventType.ERROR,
                    detail = if (ok) {
                        "高德围栏创建成功（${list?.size ?: 0} 个）"
                    } else {
                        "高德围栏创建失败：$msg（错误码 $errorCode）"
                    },
                )
            })
            fenceClient.createPendingIntent(ACTION_FENCE)
            fenceClient.setLocationClientOption(option)

            val lat = lat(app)
            val lon = lon(app)
            val radius = radius(app)
            if (lat != 0.0 || lon != 0.0) {
                fenceClient.addGeoFence(DPoint(lat, lon), radius, CUSTOM_ID)
            } else {
                state.update { it.copy(error = "还没有围栏坐标，先点「用当前位置建围栏」") }
            }

            registerFenceReceiver(app)
            client = fenceClient

            state.update {
                it.copy(
                    enabled = true,
                    attached = true,
                    lat = lat,
                    lon = lon,
                    radius = radius,
                )
            }
            EventWriter.write(
                app,
                EventType.NOTE,
                detail = "高德围栏探针已启动（中心 $lat,$lon，半径 ${radius.toInt()}m）",
            )
        }.onFailure { e ->
            state.update {
                it.copy(attached = false, fenceCreated = false, error = "初始化失败：${e.message}")
            }
            EventWriter.write(app, EventType.ERROR, detail = "高德围栏初始化失败：${e.message}")
        }
    }

    /** 撤掉围栏 + 注销接收器（服务销毁 / 用户停止时调用） */
    @Synchronized
    fun detach(context: Context) {
        val app = context.applicationContext

        receiver?.let { r -> runCatching { app.unregisterReceiver(r) } }
        receiver = null

        client?.let { c -> runCatching { c.removeGeoFence() } }
        client = null

        state.update {
            it.copy(
                enabled = isEnabled(app),
                attached = false,
                fenceCreated = false,
                fenceCount = 0,
            )
        }
    }

    // ---------------- 内部 ----------------

    private fun registerFenceReceiver(app: Context) {
        if (receiver != null) return

        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null || intent.action != ACTION_FENCE) return
                val extras = intent.extras ?: return

                val status = extras.getInt(GeoFence.BUNDLE_KEY_FENCESTATUS, -1)
                val customId = extras.getString(GeoFence.BUNDLE_KEY_CUSTOMID).orEmpty()
                val locationError = extras.getInt(GeoFence.BUNDLE_KEY_LOCERRORCODE, 0)

                val label = when (status) {
                    GeoFence.STATUS_IN -> "进入围栏"
                    GeoFence.STATUS_OUT -> "离开围栏"
                    GeoFence.STATUS_STAYED -> "围栏内停留 10 分钟"
                    GeoFence.STATUS_LOCFAIL -> "围栏定位失败"
                    else -> "未知状态($status)"
                }

                val appContext = (ctx ?: app).applicationContext
                state.update {
                    it.copy(lastEvent = label, lastEventAt = System.currentTimeMillis())
                }
                // 这一行就是用来和自研围栏的「接近/离开」直接对着比时间差的
                EventWriter.write(
                    appContext,
                    EventType.AMAP_FENCE,
                    detail = "高德围栏：$label（custom=$customId，定位错误码=$locationError）",
                )
            }
        }

        val filter = IntentFilter(ACTION_FENCE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(r, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(r, filter)
        }
        receiver = r
    }

    /**
     * `GeoFence` 的错误码 → 人话。
     *
     * `ERROR_CODE_FAILURE_AUTH` 尤其重要：它 = **鉴权失败**，
     * 也就是 key / 包名 / SHA1 三者中有一个对不上 —— 这正是之前 key 一直不成功的表现。
     */
    private fun errorText(code: Int): String = when (code) {
        GeoFence.ADDGEOFENCE_SUCCESS -> "成功"
        GeoFence.ERROR_CODE_INVALID_PARAMETER -> "参数错误"
        GeoFence.ERROR_CODE_FAILURE_CONNECTION -> "网络连接失败"
        GeoFence.ERROR_CODE_FAILURE_PARSER -> "数据解析失败"
        GeoFence.ERROR_CODE_FAILURE_AUTH -> "鉴权失败（Key / 包名 / SHA1 不匹配）"
        GeoFence.ERROR_CODE_EXISTS -> "围栏已存在"
        GeoFence.ERROR_NO_VALIDFENCE -> "没有有效围栏"
        else -> "未知错误"
    }
}
