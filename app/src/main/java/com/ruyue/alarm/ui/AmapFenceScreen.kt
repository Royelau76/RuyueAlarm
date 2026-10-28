package com.ruyue.alarm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ruyue.alarm.BuildConfig
import com.ruyue.alarm.amap.AmapFenceProbe
import com.ruyue.alarm.util.LocationHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * M0 第 4 项：高德地理围栏验证。
 *
 * 这一页**只做实验**，不参与正式提醒（正式的地点触发仍然是自研 `LocationManager` 围栏）。
 * 目的只有一个：拿真实数据回答"要不要换成高德围栏"。
 *
 * 要测的三件事：
 *  ① 进入 → 事件延迟（自研实测 61 秒 ~ 3 分 19 秒）
 *  ② 息屏 + 锁屏下还能不能触发
 *  ③ 跑一天的耗电差（自研实测 0.03%）
 */
@Composable
fun AmapFenceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val probe by AmapFenceProbe.state.collectAsStateWithLifecycle()

    ScreenScaffold("④ 高德围栏验证", onBack) {

        SectionCard("当前状态") {
            KV(
                "AMAP_KEY",
                if (BuildConfig.AMAP_KEY.isBlank()) "未配置（写进 local.properties）"
                else "已配置（${BuildConfig.AMAP_KEY.take(4)}…）",
                BuildConfig.AMAP_KEY.isNotBlank(),
            )
            KV("探针", if (probe.enabled) "已开启" else "未开启", probe.enabled)
            KV("客户端", if (probe.attached) "已附着在哨兵服务" else "未附着", probe.attached)
            KV(
                "围栏",
                if (probe.fenceCreated) "创建成功（${probe.fenceCount} 个）" else "未创建 / 失败",
                probe.fenceCreated,
            )
            if (probe.lat != 0.0 || probe.lon != 0.0) {
                KV("中心", "%.5f, %.5f".format(probe.lat, probe.lon))
                KV("半径", "${probe.radius.toInt()} 米")
            }
            if (probe.lastEvent.isNotBlank()) {
                val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(probe.lastEventAt))
                KV("最近事件", "${probe.lastEvent}（$time）")
            }
            if (probe.error.isNotBlank()) {
                KV("错误", probe.error, false)
            }
        }

        SectionCard("操作") {
            Hint(
                "「用当前位置建围栏」= 取一次系统定位作为圆心，半径 200 米，进出都会触发。\n" +
                    "建好后探针挂在哨兵服务里持续运行，**锁屏/息屏也会继续检测**。"
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val loc = LocationHelper.current(context, cacheFirst = false)
                            if (loc == null) {
                                AmapFenceProbe.reportLocationFailure(
                                    "取不到当前位置：确认系统定位已开、权限给的是「精确位置」，" +
                                        "再到窗边或室外重试"
                                )
                                return@launch
                            }
                            AmapFenceProbe.start(
                                context,
                                loc.latitude,
                                loc.longitude,
                                200f,
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("用当前位置建围栏") }

                OutlinedButton(onClick = { AmapFenceProbe.stop(context) }) { Text("停止") }
            }
        }

        SectionCard("怎么判读（这才是重点）") {
            Text(
                "建好围栏后，去外面走一圈再回来，然后到「设置 → 运行日志」里对比：",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Hint(
                "· 高德的记录是  AMAP_FENCE   —— 「进入围栏 / 离开围栏」\n" +
                    "· 自研的记录是  NOTE         —— 「接近「公司」（粗定位距离 …m）」/「离开「公司」」\n\n" +
                    "两条记录的时间戳相减，就是两者的延迟差 —— 这是这次实验最硬的数据。"
            )
            Spacer(Modifier.height(10.dp))
            Hint(
                "如果建围栏就失败，看「错误」那一行：\n" +
                    "· 鉴权失败 → key / 包名 / SHA1 有一个对不上（最常见的是 SHA1 填错栏位）\n" +
                    "· 参数错误 → 坐标或半径不合法（0,0 说明还没取到位置）\n" +
                    "· 网络连接失败 → 高德服务器连不上（挂代理/断网时会出现）"
            )
        }

        SectionCard("已知的硬约束（官方文档明确写的）") {
            Hint(
                "· 定位权限被设成「模糊定位」时，围栏**直接不工作** —— 必须「精确位置」\n" +
                    "· 必须调过 updatePrivacyShow / updatePrivacyAgree（代码里已处理）\n" +
                    "· 围栏客户端要跑在常驻的本地服务里才能后台检测（已挂在哨兵服务里）\n" +
                    "· 高德会收集 WiFi 列表/BSSID/OAID 等设备信息 —— 上架前需要隐私合规声明"
            )
        }
    }
}
