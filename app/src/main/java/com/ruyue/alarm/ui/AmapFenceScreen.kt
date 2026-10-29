package com.ruyue.alarm.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * M0 第 4 项：高德围栏 —— **未测成，已撤下**。
 *
 * 这里只留一张说明页，理由必须记清楚：
 *
 * 2026-10-28 接入高德定位 SDK 做围栏实验，12 小时结果是"零触发 + 几百条
 * 「定位错误码 4 = 请求服务器异常」+ 耗电 9.8%"。当时判断成"高德不行"，
 * **但那个结论是错的** —— 2026-10-29 做「按地址找」时才发现 App 里
 * **从来没有 INTERNET 权限**，SDK 的所有网络请求都被系统拒绝，然后在失败循环里整夜重试。
 *
 * 所以：**高德围栏的真实延迟与耗电仍然未知**。要复测得先把定位 SDK 重新接回来
 * （代码在 git 历史 v0.9.0），并且这次别忘了权限。
 *
 * 为什么现在不测了：自研 `LocationManager` 围栏已经实测达标（0.03% 耗电、
 * 到访→响铃 61 秒~3 分 19 秒），没必要为了一个未知去换；而且定位 SDK 是 2.3MB 的
 * 闭源组件，还要额外的隐私声明。
 */
@Composable
fun AmapFenceScreen(onBack: () -> Unit) {
    ScreenScaffold("④ 高德围栏（已撤下，未测成）", onBack) {

        SectionCard("现在的状态") {
            Text("这一项**没有结论**：既不能说高德行，也不能说不行。")
            Hint(
                "2026-10-28 那次实验是**测试环境坏了**，不是高德的问题 —— 详见下面。"
            )
        }

        SectionCard("那次实验为什么失败（重要）") {
            Hint(
                "接入高德定位 SDK 后跑了 12 小时：\n" +
                    "· 围栏一次都没触发\n" +
                    "· 日志里几百条「围栏定位失败（定位错误码=4）」\n" +
                    "· 耗电 9.8%（自研围栏同期 0.03%）\n\n" +
                    "当时的判断是「高德不可用」。**但这个判断是错的**：\n" +
                    "10-29 做「按地址找」时才发现，App 的清单里**从来没有 INTERNET 权限** ——\n" +
                    "SDK 的所有网络请求都被系统直接拒绝（错误码 4 正是「请求服务器异常」），\n" +
                    "然后 SDK 在失败循环里整夜重试，把电烧掉了。"
            )
        }

        SectionCard("要复测需要什么") {
            Hint(
                "1. 把高德定位 SDK 的 AAR 放回 app/libs/（历史版本 v0.9.0 里有完整实现）\n" +
                    "2. INTERNET 权限已经不缺了（v0.12.0 补上）\n" +
                    "3. 手机侧关掉代理/VPN，再确认「精确位置」权限\n\n" +
                    "复测要回答的还是那三个数：进入→事件延迟、息屏锁屏能不能触发、跑一天耗电。"
            )
        }

        SectionCard("为什么现在不测") {
            Hint(
                "自研方案已经实测达标：\n" +
                    "· 耗电 0.03%（≈ 平均 0.02mA，低于手机测量精度）\n" +
                    "· 到访→响铃 61 秒 ~ 3 分 19 秒\n" +
                    "· 零第三方 SDK、零 key、零隐私声明\n\n" +
                    "而高德定位 SDK 是 2.3MB 闭源组件，还要在隐私政策里单列第三方 SDK 采集的设备信息。\n" +
                    "为一个未知的收益去背这些负债，不划算。"
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}
