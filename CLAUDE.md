# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

**如约**（RuyueAlarm）—— 事项提醒闹钟：到点提醒 + **到达指定地点提醒**。

地点确认有两条通路：**连上指定 WiFi**（读 SSID/BSSID）和**进入指定地理范围**。
地理围栏用系统 `LocationManager` **自研**（不依赖高德、不需要 key）。
目标机型 **小米 15 Ultra（HyperOS）**，先自用，成熟后再考虑上小米应用商店；不做多设备/云同步。

## 当前状态

**版本 v0.16.3-m2（2026-11-02）**：自用可用，四种能力齐了 ——
**时间触发 / WiFi 触发 / 自研地理围栏** 三种触发 + 统一全屏响铃 + 常驻哨兵自愈。

| 能力 | 位置 |
|---|---|
| 提醒 CRUD + 时间触发（仅一次/每天/工作日/周末/自定义） | `ui/` · `alarm/ReminderScheduler` |
| WiFi 触发（去抖 15s + 冷却 1440min） | `trigger/TriggerEngine` |
| 自研地理围栏（`LocationManager`，**零 key、零第三方 SDK**） | `trigger/TriggerEngine` · `util/LocationHelper` |
| 地点收藏 / 按地址找（高德 **Web 服务 API**，无 SDK） | `data/SavedPlace` · `net/AmapWebApi` · `ui/AddressSearchDialog` |
| 稍后提醒 5 / 10 分钟 | `alarm/RingActivity` · `ReminderScheduler` |
| 升级前自动 JSON 备份 | `util/Backup`（在 `RuyueApp.onCreate` 第一件事） |
| 触发对账自检（预期 vs 实际，漏了会推送） | `trigger/Ledger` · `data/ExpectedFire` |
| 触发纯逻辑单测 18 个 | `trigger/TriggerRules` · `src/test/` |

**M0 验证**：①②③⑤ 通过；④（高德围栏）**未测成** —— 当时 App 缺 `INTERNET` 权限（已修），
所以那次的"高德不可用"结论已作废。要不要复测是可选项，不影响当前方案。

> ⚠️ **别往本文档追加历史行。** 每次改版的实测细节、踩坑记录、M0/M1 结果表都在
> [`docs/版本历史.md`](docs/版本历史.md) 里（那份不会被自动加载）。
> 本文档每改一次，harness 就会整份重新注入上下文一次 —— 保持在 100 行以内。

## 构建

```bash
cd RuyueAlarm
./gradlew assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # 手机连上后直接装
```

本机环境（已实测可编译）：

- Android SDK：`D:\Program Files\Android\Sdk`（见 `local.properties`，该文件不入库）
- JDK：PATH 上的 Java 17.0.12；也可 `JAVA_HOME=C:\Users\royel\.jdks\jbr-17.0.14`
- `GRADLE_USER_HOME=D:\Android_Data\.gradle`（gradle-8.2-bin 已缓存，不需要重新下载）
- 工具链版本**刻意与同机的 BlinkNote 保持一致**（AGP 8.2.0 / Kotlin 1.9.20 / Gradle 8.2 /
  Compose BOM 2024.09.00 / Room 2.6.1 / Hilt 2.48.1），避免踩工具链的坑

⚠️ **targetSdk 34 的取舍**：M0 用 compileSdk/targetSdk 34（与已验证的 BlinkNote 一致）。
代价是 Android 15/16 上部分限制会以「兼容模式」运行，所以 ②③ 这两项的结论
**在上架前升到 targetSdk 35/36 后必须复核一次**。升级工具链（AGP 8.9+ / Gradle 8.11+ / Kotlin 2.x）
是 M3 的任务。

## 架构

单 `:app` 模块，Kotlin + Compose + Room + Hilt。

```
com.ruyue.alarm
├── RuyueApp.kt            通知渠道（sentinel 低优先级 / alert 高优先级 / alarm_test）
├── MainActivity.kt        Route 路由 + RuyueRoot 入口（无 Navigation 依赖，够用了）
├── data/                  Room：两个独立库（见下）
│   ├── AppDatabase.kt     ① 证据日志库 ruyue.db（EventLog + Db 单例 + EventWriter）
│   ├── ReminderDb.kt      ② 提醒库 ruyue_reminders.db（Reminder + ReminderDb 单例）
│   ├── Reminder.kt        提醒实体 + Repeat 重复规则常量
│   └── *Repository.kt     LogRepository / ReminderRepository
├── di/AppModule.kt        Hilt：两个库 + 各自 DAO
├── util/WifiReader.kt     读 SSID/BSSID，严格区分「没连」与「读不到」
├── util/LocationHelper.kt 取当前位置 + 算距离（自研围栏的坐标来源）
├── util/LogExporter.kt    日志导出成 txt 并分享（FileProvider）
├── sentinel/              常驻前台服务（WiFi 回调 + 地点监听）+ 看门狗自愈
├── trigger/TriggerEngine   WiFi 去抖 / 地点停留 / 统一触发入口
├── alarm/                 时间触发：ReminderScheduler / AlarmReceiver / ReminderNotifier / RingActivity
├── boot/BootReceiver.kt   开机/时间变更 → 重排提醒 + 拉回哨兵
└── ui/                    提醒列表与编辑 + M0 验证台
```

### 为什么是两个数据库

`ruyue.db` 是 M0 的**证据日志**，已经承载了几轮实测数据；往里加表就要升 schema 版本 + 写迁移，
迁移写错会让 App 启动即崩。所以 M1 的提醒数据另起一个库文件：
日志库永远停在 version 1（证据不受影响），提醒库从 1 开始（将来改结构只影响提醒数据）。
语义上也更清楚：一个是可丢弃的诊断日志，一个是不能丢的用户数据。

### 六条必须守住的约定

1. **日志即证据**。`event_logs` 表是判断「服务有没有被杀 / 闹钟有没有响」的唯一硬证据，
   比电池统计可靠。任何新功能都要往这张表里留可判读的事件，不要只 log 到 Logcat。
2. **`Db` 单例而不是全靠 Hilt**。`BroadcastReceiver` 里落日志最可靠的方式是直接拿 DAO
   （`EventWriter.write`），给 Receiver 做 Hilt 注入会引入额外编译期风险。
   `AppModule` 也从 `Db.get()` 取库，保证全进程只有一个 Room 实例。
3. **区分「没触发」和「触发了没弹出来」**。这两种故障修法完全不同：
   前者查权限/保活/闹钟，后者查全屏意图与 HyperOS 的「后台弹出界面」。
   所以 `AlarmReceiver` 先落 `ALARM_FIRED`，`RingActivity` 再落 `RING_OPENED`。
4. **触发只有一条入口**。时间触发（`AlarmReceiver`）和 WiFi 触发（`TriggerEngine.onWifiConnected`）
   最终都走 `TriggerEngine.fire()` / `ReminderNotifier.show()` —— 不要在别处另开一套响铃逻辑，
   否则两种来源的体验会慢慢分叉，排障时也要查两遍。
5. **WiFi 触发有且只有两道闸门**：`TriggerEngine` 里的**去抖**（连上后确认 N 秒仍在线）
   与**冷却**（`cooldownMinutes` 内不重复触发）。这两个参数应当由 M0 第 5 项的实测统计来定，
   不要拍脑袋调小 —— 家里 WiFi 一天重连十几次是常态，去掉冷却会被吵死。
6. **动 `trigger/` · `alarm/` · `data/` 之前，先 grep 一遍 [`docs/版本历史.md`](docs/版本历史.md)。**
   这三块几乎每一行判定都有前因（时间闸门、冷却、滞环、`PendingIntent` request code、迁移 SQL…），
   不知道"为什么"就会改回老 bug。查法只需 `grep "关键词" docs/版本历史.md` 拿相关几行，
   不要通读（113 行 / 2 万字符），也不要跳过 —— 跳过就会第二次踩同一个坑。

## 小米 / HyperOS 的现实约束

- 必须在 HyperOS 里手动开：**自启动**、**省电策略→无限制**、**后台弹出界面**（不开则锁屏闹钟弹不出来）、
  通知权限 + 锁屏显示通知。`ui/GuideScreen.kt` 提供了跳转入口。
- 自启动、后台弹出界面是小米私有权限，**adb 授不了**；只有 Doze 白名单能用
  `adb shell dumpsys deviceidle whitelist +com.ruyue.alarm`。
- **用户点「强行停止」后，所有闹钟与监听失效**（Android 硬限制，绕不过）。
  正式版必须有「打开应用即重新排定」的逻辑。
- 国行 HyperOS 默认无 Google 服务框架 → 不用系统 Geofencing API，也用不了 FusedLocationProvider；
  地理围栏用系统 `LocationManager` **自研**（零 key、零第三方 SDK、少一大块合规）。
  高德定位 SDK 只在 v0.9.0 做过 M0 ④ 实验、已于 v0.13.0 整包移除；「高德不可行」的结论**已作废**
  （当时 App 缺 `INTERNET` 权限，是假证据）。
- **精确闹钟权限别用 `SCHEDULE_EXACT_ALARM`**：Android 14 起它默认拒绝，而且**非闹钟类应用
  不会出现在「闹钟和提醒」列表里**，用户无处授权（v0.1.2 实测踩到）。
  本应用核心就是闹钟/提醒，直接用 `USE_EXACT_ALARM`（Android 13+ 普通权限，安装即授予）；
  `SCHEDULE_EXACT_ALARM` 只作 Android 12~12L 的兼容声明保留。
- 自愈机制（`sentinel/SentinelWatchdog.kt`）用**闹钟+广播**而不是 WorkManager：Android 12+ 禁止
  后台启动前台服务，而「精确闹钟触发的广播」是官方豁免之一；WorkManager 没这个豁免。
  自动拉起失败时降级为一条高优先级通知（用户点击＝用户操作，同样豁免）。

## 版本与工具链升级路径

- **M0 / M1 / M2 全部完成** —— 每次改版的技术细节与实测数据见 [`docs/版本历史.md`](docs/版本历史.md)
- **自用加固轮（v0.11 ~ v0.16）已完成**：地点收藏 / 按地址找 / 稍后提醒 5·10 分钟 /
  升级前 JSON 备份 / 触发对账自检 / 触发纯逻辑单测 18 个
- **下一步候选**：
  1. **个性化 UI** —— 先只做一页「风格样张页」定设计语言，再逐页迁移（不要一次改多屏）
  2. 「每月某日」重复（房租这类）
  3. **M3**：隐私合规 + release 签名 + targetSdk 35/36 复核（②③ 必须复测）+ 小米商店提审
