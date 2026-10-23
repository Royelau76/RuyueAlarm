package com.ruyue.alarm.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** 精简后的位置，避免把 Android 的 Location 对象在整个 App 里传来传去 */
data class SimpleLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val provider: String,
)

/**
 * 定位读取。用于两件事：
 *  1. 编辑页「用当前位置」——取一次坐标就够；
 *  2. 自研地理围栏的坐标来源（实际注册在哨兵服务里，见 SentinelService）。
 *
 * 刻意不引入任何地图/围栏 SDK：不需要 key，也不需要额外的隐私合规声明。
 */
object LocationHelper {

    fun hasFinePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /** 后台定位（Android 10+ 要单独授「始终允许」）—— 围栏在后台跑全靠它 */
    fun hasBackgroundPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * 取位置。
     *
     * @param cacheFirst 编辑页用 `true`（立刻返回；室内常常只有缓存可用）。
     *   **围栏停留到点后的复核传 `false`** —— 复核要尽力拿新位置，不能直接吃缓存。
     * @param maxCacheAgeMs 允许的缓存年龄上限。复核时给 3 分钟：站着没动的话 3 分钟前的点
     *   仍然能代表「还在范围内」；而拿 10 分钟前的点就可能误判。
     *
     * ⚠️ 两个坑都是实测踩出来的（2026-10-12~14，两天日志里 45 次复核失败）：
     *  1. **只挂一个 provider** —— 原来优先 GPS，室内十几秒都出不来点，复核几乎必然失败；
     *     现在 GPS 与网络**同时挂**，谁先给结果用谁（室内通常网络定位秒回）。
     *  2. **没有缓存兜底** —— 新定位拿不到时直接返回 null，等于白白放弃一次判定；
     *     现在会回退到「不超过 maxCacheAgeMs 的缓存」。
     */
    suspend fun current(
        context: Context,
        timeoutMs: Long = 15_000L,
        cacheFirst: Boolean = true,
        maxCacheAgeMs: Long = 10 * 60_000L,
    ): SimpleLocation? {
        if (!hasFinePermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null

        if (cacheFirst) lastKnown(lm, maxCacheAgeMs)?.let { return it }

        freshFix(lm, timeoutMs)?.let { return it }

        return if (cacheFirst) null else lastKnown(lm, maxCacheAgeMs)
    }

    /**
     * 同时挂 GPS 与网络两个 provider，**先到先用，但优先要精度够的那个**。
     *
     * 为什么不能"谁先来用谁"：网络定位常常秒回，却可能误差几百米；拿它判「在不在半径内」
     * 会直接把判定带偏。所以精度差于 [goodAccuracyMeters] 的点先记下来继续等，
     * 等到超时都没等到好点，才退而用「拿到的最好的那个」。
     *
     * 这个函数只在**停留到点复核**时调用，是短请求（默认 12 秒），不会长期占着 GNSS。
     */
    private suspend fun freshFix(
        lm: LocationManager,
        timeoutMs: Long,
        goodAccuracyMeters: Float = 150f,
    ): SimpleLocation? {
        var best: SimpleLocation? = null

        val good = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val providers = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                ).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

                if (providers.isEmpty()) {
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }

                lateinit var listener: LocationListener
                listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        val fix = location.simplify()
                        if (fix.accuracyMeters < (best?.accuracyMeters ?: Float.MAX_VALUE)) best = fix

                        if (fix.accuracyMeters <= goodAccuracyMeters) {
                            providers.forEach { runCatching { lm.removeUpdates(listener) } }
                            if (cont.isActive) cont.resume(fix)
                        }
                    }
                }

                cont.invokeOnCancellation {
                    providers.forEach { runCatching { lm.removeUpdates(listener) } }
                }
                providers.forEach { provider ->
                    runCatching {
                        lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    }
                }
            }
        }

        return good ?: best
    }

    /** 两点间距离（米） */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val out = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, out)
        return out[0]
    }

    private fun lastKnown(lm: LocationManager, maxAgeMs: Long): SimpleLocation? = runCatching {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.takeIf { System.currentTimeMillis() - it.time < maxAgeMs }
            ?.simplify()
    }.getOrNull()

    private fun Location.simplify() = SimpleLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        provider = provider ?: "?",
    )
}
