package com.ruyue.alarm.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ruyue.alarm.BuildConfig

/**
 * M0 第 1 项：能不能稳定读到 WiFi 的 SSID。
 *
 * 第一轮实测（小米 15 Ultra / HyperOS，2026-09）已经拿到一个反直觉的结论：
 * **NEARBY_WIFI_DEVICES 与 ACCESS_FINE_LOCATION 都给全了，SSID 依然被抹成
 * <unknown ssid>。** 原因是 manifest 里给 NEARBY_WIFI_DEVICES 加了
 * neverForLocation 标记（等于声明「本应用不通过 WiFi 推断位置」，系统便把
 * SSID/BSSID 当作位置性信息抹掉）。本版本已去掉该标记。
 *
 * 这一屏现在同时显示两条读取路径的结果和系统定位总开关，用来把剩下的
 * 可能性一次分清：
 *   ① 去掉 neverForLocation 后是否恢复（本版应该已恢复）
 *   ② 两条读取路径表现是否一致
 *   ③ 系统「定位」总开关关着是否也会导致抹除
 */
@Composable
fun WifiProbeScreen(vm: M0ViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val grants by vm.grants.collectAsStateWithLifecycle()
    val probe by vm.probe.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        vm.refreshGrants()
        vm.probeWifi(
            note = "刚申请了 " + result.keys.joinToString { it.substringAfterLast('.') } +
                "，结果=" + result.values.joinToString(),
        )
    }

    ScreenScaffold("① WiFi SSID 权限验证", onBack) {

        SectionCard("怎么测") {
            Hint(
                "1) 连上目标 WiFi（比如家里路由器），并确保系统「定位」开关是打开的\n" +
                    "2) 点下面的「读取一次」\n" +
                    "3) 看「诊断」那一行给的是什么结论\n" +
                    "4) 如果还读不到，把这一屏截图发我"
            )
        }

        SectionCard("权限与环境") {
            KV("App 版本", BuildConfig.VERSION_NAME)
            KV(
                "NEARBY_WIFI_DEVICES",
                if (grants[Manifest.permission.NEARBY_WIFI_DEVICES] == true) "已授予" else "未授予",
                grants[Manifest.permission.NEARBY_WIFI_DEVICES],
            )
            KV(
                "ACCESS_FINE_LOCATION",
                if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) "已授予" else "未授予",
                grants[Manifest.permission.ACCESS_FINE_LOCATION],
            )
            KV(
                "ACCESS_BACKGROUND_LOCATION",
                if (grants[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true) "已授予" else "未授予",
                grants[Manifest.permission.ACCESS_BACKGROUND_LOCATION],
            )
            KV(
                "系统定位总开关",
                if (probe?.locationEnabled == true) "开" else "关",
                probe?.locationEnabled,
            )
            KV(
                "manifest neverForLocation",
                "已移除（本版）",
                true,
            )
        }

        SectionCard("探测结果") {
            val snap = probe
            KV("是否连着 WiFi", if (snap?.connected == true) "是" else "否", snap?.connected)

            Spacer(Modifier.height(6.dp))
            Text("主路径 NetworkCapabilities")
            KV("  SSID", snap?.capsSsid ?: "（抹除/无）", snap?.capsSsid?.let { true })
            KV("  BSSID", snap?.capsBssid ?: "（抹除/无）")

            Spacer(Modifier.height(6.dp))
            Text("备用路径 WifiManager")
            KV("  SSID", snap?.legacySsid ?: "（抹除/无）", snap?.legacySsid?.let { true })
            KV("  BSSID", snap?.legacyBssid ?: "（抹除/无）")

            snap?.error?.let { KV("说明", it, false) }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.probeWifi(note = "手动读取") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("读取一次") }

            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { launcher.launch(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("申请 NEARBY_WIFI_DEVICES 并读取") }

            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("申请精确位置并读取") }

            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                    )
                },
            ) { Text("打开应用设置（手动撤销/授予权限）") }
        }

        SectionCard("诊断") {
            Text(
                diagnose(
                    snap = probe,
                    hasFineLocation = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true,
                )
            )
        }

        SectionCard("已确认的结论（2026-09）") {
            Hint(
                "· v0.1.0：权限齐全但声明了 neverForLocation → SSID 被抹除（已移除该标记）\n" +
                    "· v0.1.1：只有 NEARBY_WIFI_DEVICES、无位置权限 → 仍被抹除\n" +
                    "· 预判：必须「有位置权限」且「不声明 neverForLocation」两者同时满足才能读到，\n" +
                    "  正等最后一次确认\n" +
                    "· 锁屏全屏闹钟：可以全屏弹出 ✅（M0 第 3 项通过）\n\n" +
                    "这一项的最终结论决定正式版的权限申请流程：\n" +
                    "能只靠 NEARBY_WIFI_DEVICES 读到 → 不必索要定位权限；\n" +
                    "必须靠 ACCESS_FINE_LOCATION → 上架时要单独说明定位用途。"
            )
        }
    }
}

private fun diagnose(
    snap: com.ruyue.alarm.util.WifiSnapshot?,
    hasFineLocation: Boolean,
): String = when {
    snap == null -> "还没探测过，点上面「读取一次」。"
    !snap.connected -> "当前没连着 WiFi —— 先连上目标 WiFi 再读。"
    snap.readableSsid -> "✅ 读到真实 SSID「${snap.ssid}」（来源：${snap.source}）。" +
        "把这一行记下来，这就是 M0 第 1 项的答案。"
    !hasFineLocation ->
        "⚠️ 现在只有 NEARBY_WIFI_DEVICES、没有位置权限 → SSID 被抹除是预期行为。" +
            "点下面「申请精确位置并读取」再读一次，看会不会变成真名。"
    !snap.locationEnabled ->
        "⚠️ 权限齐全，但系统「定位」总开关是关的 —— Android 会因此抹掉 SSID。" +
            "打开系统定位再读一次。"
    else ->
        "⚠️ 权限齐全、定位也开着，SSID 仍被抹除 → 只剩 ROM 侧限制这一种可能，" +
            "请把这一屏截图发我。"
}
