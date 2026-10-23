package com.ruyue.alarm.util

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 一次 WiFi 探测的快照。
 *
 * 为什么两条读取路径都留着：M0 第一次实测发现「权限给全了 SSID 仍被抹成
 * <unknown ssid>」，这时必须能区分到底是
 *   ① manifest 里的 neverForLocation 标记（系统把 SSID 当位置信息抹掉）
 *   ② 读取路径问题（NetworkCapabilities 与 WifiManager 表现可能不同）
 *   ③ 系统「定位」总开关关着（Android 会因此抹掉 SSID）
 * 所以两条路径的结果 + 定位总开关状态都要能直接在界面上看到。
 */
data class WifiSnapshot(
    val connected: Boolean,
    /** 主路径：ConnectivityManager + NetworkCapabilities.transportInfo（API 29+） */
    val capsSsid: String?,
    val capsBssid: String?,
    /** 备用路径：WifiManager.connectionInfo（已废弃，但个别 ROM 上反而读得到） */
    val legacySsid: String?,
    val legacyBssid: String?,
    /** 系统级「定位」开关（注意：这不是权限） */
    val locationEnabled: Boolean,
    val error: String? = null,
) {
    /** 两条路径里能读到真名的那个 */
    val ssid: String? get() = capsSsid ?: legacySsid

    val bssid: String? get() = capsBssid ?: legacyBssid

    /** 连着 WiFi 但两条路径都读不到名字 = 被系统抹除 */
    val redacted: Boolean get() = connected && ssid == null

    val readableSsid: Boolean get() = connected && ssid != null

    /** 读到名字时是走哪条路径读到的 */
    val source: String? get() = when {
        capsSsid != null -> "主路径 NetworkCapabilities"
        legacySsid != null -> "备用路径 WifiManager"
        else -> null
    }
}

object WifiReader {

    const val UNKNOWN_SSID = "<unknown ssid>"

    /** 被抹除时系统会回这个假 BSSID */
    const val FAKE_BSSID = "02:00:00:00:00:00"

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** 系统「定位」总开关。关着的话即使权限齐全，SSID 也可能被抹除。 */
    fun locationServicesEnabled(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        }
    }

    fun snapshot(context: Context): WifiSnapshot {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return WifiSnapshot(
                connected = false,
                capsSsid = null,
                capsBssid = null,
                legacySsid = null,
                legacyBssid = null,
                locationEnabled = locationServicesEnabled(context),
                error = "ConnectivityManager 不可用",
            )

        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val connected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        // ---- 主路径 ----
        var capsSsid: String? = null
        var capsBssid: String? = null
        if (connected && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val info = caps?.transportInfo as? WifiInfo
            capsSsid = normalizeSsid(info?.ssid)
            capsBssid = normalizeBssid(info?.bssid)
        }

        // ---- 备用路径（无条件读，便于两条路对比）----
        @Suppress("DEPRECATION")
        val legacy = context.getSystemService(WifiManager::class.java)?.connectionInfo
        val legacySsid = normalizeSsid(legacy?.ssid)
        val legacyBssid = normalizeBssid(legacy?.bssid)

        return WifiSnapshot(
            connected = connected,
            capsSsid = capsSsid,
            capsBssid = capsBssid,
            legacySsid = legacySsid,
            legacyBssid = legacyBssid,
            locationEnabled = locationServicesEnabled(context),
        )
    }

    private fun normalizeSsid(raw: String?): String? {
        val value = raw?.trim('"')?.trim() ?: return null
        return if (value.isEmpty() || value == UNKNOWN_SSID) null else value
    }

    private fun normalizeBssid(raw: String?): String? {
        val value = raw?.trim() ?: return null
        return if (value.isEmpty() || value == FAKE_BSSID) null else value
    }
}
