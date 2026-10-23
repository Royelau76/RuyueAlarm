package com.ruyue.alarm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ruyue.alarm.BuildConfig

/**
 * M0 第 4 项：高德地理围栏。
 *
 * 这一项默认**不可运行** —— 因为它必须先有一个高德开放平台的 key（绑定包名+SHA1），
 * 而且 SDK 依赖也要装进来。硬编码一个猜的 SDK 版本号会让整个工程编译不过，
 * 所以这里先做成引导页 + 明确的接入步骤，等你拿到 key 再按下面三步启用。
 */
@Composable
fun AmapFenceScreen(onBack: () -> Unit) {
    val key = BuildConfig.AMAP_KEY

    ScreenScaffold("④ 高德围栏验证", onBack) {

        SectionCard("当前状态") {
            KV(
                "AMAP_KEY",
                if (key.isBlank()) "未配置" else "已配置（${key.take(6)}…）",
                key.isNotBlank(),
            )
            KV("SDK 依赖", "未启用（app/build.gradle 里被注释）", false)
            Hint("没有 key 就不会有围栏数据，所以这里先不写假的验证代码。")
        }

        SectionCard("启用步骤") {
            Text("1. 申请 key")
            Hint(
                "高德开放平台 → 控制台 → 应用管理 → 创建应用 → 添加 Key，\n" +
                    "服务平台选「Android 平台」，绑定：\n" +
                    "   包名：com.ruyue.alarm\n" +
                    "   SHA1：debug 签名（终端执行下面命令拿）\n" +
                    "     keytool -list -v -keystore %USERPROFILE%\\.android\\debug.keystore -alias androiddebugkey -storepass android -keypass android"
            )

            Spacer(Modifier.height(10.dp))
            Text("2. 填入本地配置")
            Hint("把 key 写进 RuyueAlarm/gradle.properties 的 AMAP_KEY=（该文件不提交 git）。")

            Spacer(Modifier.height(10.dp))
            Text("3. 打开依赖")
            Hint(
                "在 app/build.gradle 里把\n" +
                    "    // implementation 'com.amap.api:location:6.4.5'\n" +
                    "的注释去掉，版本号换成高德当前推荐的版本。"
            )
        }

        SectionCard("接进来之后要测什么") {
            Hint(
                "· 围栏最小半径与触发延迟（圆形围栏一般要 200 米量级才稳）\n" +
                    "· 进/出各走一遍，记录从进入到响铃的实际延迟\n" +
                    "· 息屏 + 锁屏状态下还能不能触发（这是关键）\n" +
                    "· 跑一天看 HyperOS 的耗电排行\n\n" +
                    "注意：高德 SDK 初始化前必须调用 updatePrivacyShow / updatePrivacyAgree，\n" +
                    "否则 SDK 会直接拒绝工作 —— 自用也躲不掉这一步。"
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { }, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("开始围栏测试（待接入 SDK 后启用）")
            }
        }
    }
}
