# 本目录现在**不需要任何第三方 SDK**

「按地址找」用的是高德 **Web 服务 API**（HTTPS + JSON），直接在代码里请求，
没有任何本地 SDK 依赖 —— 见 `app/src/main/java/com/ruyue/alarm/net/AmapWebApi.kt`。

## 曾经放过什么（2026-10-28 ~ 10-29）

这里曾经放过 `AMapLocation_11.3.101_20260924.aar`（高德**定位** SDK，2.8MB），
用于 M0 第 4 项的「高德地理围栏」实验。那次实验失败（零触发 + 耗电 9.8%），
后来查明**真实原因是 App 缺 `INTERNET` 权限**（v0.12.0 已补），跟高德本身无关。

SDK 已于 v0.13.0 移除：

- APK 从 12.2MB 降到约 9.9MB
- 少一个采集 WiFi 列表/BSSID/OAID 的闭源组件，上架时少一份第三方 SDK 隐私声明
- 想复测的话，完整实现还在 git 历史里（v0.9.0 的 `amap/AmapFenceProbe.kt`）

## 如果以后要加本地 AAR

`app/build.gradle` 里原来有一行 `implementation fileTree(dir: 'libs', include: ['*.aar', '*.jar'])`，
加 SDK 时把它加回来即可。注意：

- 高德 SDK **不在 Maven 上**（jcenter 已关停），只能从官网下 AAR 手动放进来
- 用之前先 `javap -classpath classes.jar <类名>` 读出真实签名，**别凭记忆写**
  —— v0.9.0 那次凭记忆写，包名/参数三条全错
