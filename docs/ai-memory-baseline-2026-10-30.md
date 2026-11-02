# 如约（RuyueAlarm）项目知识基线

> 生成：2026-10-30 ｜ 来源：`CLAUDE.md`（213 行 / 43 KB）、git 历史（19 次提交）、`docs/` 4 篇、`~/.claude/projects` 会话库
> 用途：给下一个 agent（Claude Code / DSH / 其他）一份 10 分钟读完的项目地图。**细节以 `CLAUDE.md` 为准**，本文只做压缩与校正。

## 1. 一句话

**如约**是个事项提醒闹钟：到点提醒 + **到达指定地点提醒**（连指定 WiFi / 进入指定地理范围）。
地理围栏用系统 `LocationManager` **自研**，零第三方定位 SDK、零 key。
目标机型小米 15 Ultra（HyperOS），先自用，不做多设备/云同步。

## 2. 事实快照

| 项 | 值 |
|---|---|
| 包名 | `com.ruyue.alarm` |
| 版本 | `0.16.2-m2`（versionCode 66），最近提交 2026-10-30 10:07 |
| SDK | compileSdk/targetSdk **34**（刻意与 BlinkNote 对齐）、minSdk 26 |
| 工具链 | AGP 8.2.0 / Kotlin 1.9.20 / Gradle 8.2 / Compose BOM 2024.09.00 / Room 2.6.1 / Hilt 2.48.1 |
| 模块 | 单 `:app`，Kotlin + Compose + Room + Hilt，无 Navigation 依赖 |
| 代码量 | `src/main` 50 个 .kt；`src/test` 1 个测试文件（18 个纯函数单测） |
| 仓库 | `github.com/Royelau76/RuyueAlarm`，`master`，19 次提交，工作区干净 |
| APK | ~9.83 MB（v0.13.0 移除高德定位 SDK 后） |
| 文档 | `docs/`：方案.md、M0-验证手册.md、M1-验收.md、M2-地点选点方案.md；**无 README.md、无 AGENTS.md** |

## 3. 架构

```
com.ruyue.alarm
├── RuyueApp.kt          通知渠道（sentinel_quiet 无感 / alarm_ring 响铃 / alarm_test）+ 首次启动自检
├── MainActivity.kt      Route 路由（自写，无 Navigation）
├── data/                ① ruyue.db 证据日志库（EventLog，永远 v1）
│                        ② ruyue_reminders.db 提醒库（Reminder/SavedPlace/SsidHistory/ExpectedFire，v5）
├── di/AppModule.kt      Hilt：两个库 + DAO；`Db.get()` 兜住全进程单例
├── util/                WifiReader / LocationHelper / LogExporter / Backup
├── sentinel/            常驻前台服务 + 看门狗自愈
├── trigger/             TriggerEngine（唯一触发入口）+ TriggerRules（纯函数）+ Ledger（对账）
├── alarm/               ReminderScheduler / AlarmReceiver / ReminderNotifier / RingPlayer / RingActivity
├── boot/BootReceiver.kt 开机/时间变更 → 重排提醒 + 拉回哨兵
├── net/AmapWebApi.kt    按地址找（Web 服务 API，HTTP+JSON，无 SDK）
└── ui/                  列表/编辑/设置 + M0 验证台
```

**为什么两个数据库**：`ruyue.db` 是 M0 证据日志（已承载实测数据，改 schema 有崩启动风险），所以提醒数据另起库文件。一个可丢弃，一个不可丢。

## 4. 五条必须守住的约定

> 原文标题写的是"三条"，实际列了五条 —— 以内容为准。

1. **日志即证据**：`event_logs` 是判断"服务被杀/闹钟没响"的唯一硬证据，比电池统计可靠。新功能必须留可判读事件，不许只打 Logcat。
2. **`Db` 单例而不是全靠 Hilt**：BroadcastReceiver 里直接拿 DAO（`EventWriter.write`）最可靠；给 Receiver 做 Hilt 注入会引入编译期风险。
3. **区分"没触发"和"触发了没弹出来"**：`ALARM_FIRED`（Receiver 落）与 `RING_OPENED`（RingActivity 落）必须分开。
4. **触发只有一条入口**：时间/WiFi/地点最终都走 `TriggerEngine.fire()` → `ReminderNotifier.show()`，不许另开响铃逻辑。
5. **WiFi 触发只有两道闸门**：去抖（15s）+ 冷却；参数由 M0 ⑤ 实测定，不许拍脑袋调小（家里 WiFi 一天重连十几次）。

## 5. 已闭环的技术结论（别再重测）

- **SSID 读取**：需要 `NEARBY_WIFI_DEVICES` + `ACCESS_FINE_LOCATION`（后台再加 `ACCESS_BACKGROUND_LOCATION`），且**不能**声明 `neverForLocation`；必须走 `WifiManager.getConnectionInfo()`，`NetworkCapabilities.getTransportInfo()` 拿不到真值。`WifiReader` 的 `capsSsid ?: legacySsid` 是刻意的，不要简化。
- **精确闹钟**：用 `USE_EXACT_ALARM`（Android 13+ 安装即授予）；`SCHEDULE_EXACT_ALARM` 在 Android 14 默认拒绝且非闹钟类应用无处授权，只作 12~12L 兼容保留。
- **自研围栏实测**：功耗 16.23% → **0.35%**（v0.4.0）→ **0.03%**（4 天窗口，≈0.02mA）；到访→响铃 61s~3m19s；复核失败从 49 次/2 天降到 2 次/2 天。连续监听只挂网络定位（60s/200m），GPS 只在接近目标时短暂开启。
- **高德定位 SDK 已整包移除**（v0.13.0，APK 12.2→9.83MB）。M0 ④「高德围栏不行」的结论**作废**：当时 App 缺 `INTERNET` 权限，SDK 请求全被拒 → 整夜重试烧到 9.8% 电，是假证据（v0.12.0 才发现）。
- **保活**：常驻 FGS + 看门狗（精确闹钟+广播，不用 WorkManager）+ `sentinel_quiet`（IMPORTANCE_MIN + VISIBILITY_SECRET）实现"无感"；FGS 通知删不掉，这就是极限。
- **HyperOS 约束**：自启动、省电无限制、后台弹出界面要手动开（adb 授不了）；"强行停止"后一切失效（Android 硬限制）。

## 6. 演进时间线（压缩）

| 版本 | 关键点 |
|---|---|
| v0.1–0.2 | M0 五项验证 + M1 自用可用版（时间闹钟 + WiFi 触发 + 响铃管线） |
| v0.3–0.4 | 自研地理围栏；省电重构（16.23% → 0.35%）；到访只提醒一次 |
| v0.5 | 「或/且」双条件、自定义铃声、渐强、擦边容差 5%、库 v2→v3 |
| v0.6–0.7 | 常驻无感、通知残留修复、**声音从 UI 提升为进程级 `RingPlayer`** |
| v0.8 | 深色模式/品牌色、编辑页折叠、列表重做、设置页 |
| v0.9 | 高德围栏实验（失败，后证实是自身 INTERNET 权限缺失） |
| v0.10–0.12 | 稍后提醒 5/10 分钟（修 PendingIntent 冲突）；地点收藏 v3→v4；按地址找（零 SDK）；补 INTERNET |
| v0.13–0.14 | 撤高德 SDK；升级前自动备份提醒库 JSON |
| v0.15 | 触发判定抽纯函数 + 18 个单测 |
| v0.16 | 触发对账（EXPECTED/FIRED/SKIPPED/MISSED）、留证据、幽灵提示修复；库 v4→v5 |

## 7. 复用价值最高的 10 条教训

1. **复用 request code 等于共享 PendingIntent** —— 一次性排期与长期排期必须分开编号（稍后提醒曾覆盖正式闹钟，静默失效）。
2. **通知渠道重要性创建后不可改** —— 要改行为必须换新 channel id。
3. **"兜底"与主路径并行 = 双份执行** —— 兜底只该在主路径确定失效时接管（同秒双触发事故）。
4. **任何 `runCatching` 兜底都要留下"我走了兜底"的痕迹**（静音降级曾让渐强失效查不出来）。
5. **迁移 SQL 必须与 Room 期望 schema 逐字符一致**，并用离线 sqlite 逐列比对（`tools/verify_migration_v*.py`）。
6. **UI 看不到渲染结果就必须小步交付**，由用户截图校准。
7. **日志写给自己看要比写给人看更严谨**（首次观察不发日志、断开只报上次连接）。
8. **记账要幂等**：`(reminderId, expectedAt)` 唯一索引 + `INSERT OR IGNORE`。
9. **任何自动判定机制都要把自己的判定过程写成证据**，否则出事时帮不上忙、也证明不了自己无辜。
10. **一次性提示不要用"状态 + 用户手动消费"实现**（会跨页面残留）。

## 8. 本次审计发现的漂移与缺口

| 类型 | 内容 | 建议 |
|---|---|---|
| 文档过期 | `docs/方案.md` L4/L39/L64-69 仍把**高德**写成位置触发方案 | ✅ 已修（2026-10-30：加"文档状态"免责段 + 位置触发改写为自研，保留高德实验历史） |
| 文档过期 | `CLAUDE.md` HyperOS 小节首句"地理围栏统一走高德"（后半句已有括号修正） | ✅ 已修（2026-10-30：改为自研围栏，并说明高德结论已作废） |
| 结构瑕疵 | 标题"三条必须守住的约定"实际五条 | ✅ 已修（2026-10-30：标题改为"五条"） |
| 可维护性 | `CLAUDE.md` 43 KB / 213 行，被 13 次提交持续追加；版本演进表占据一半篇幅 | 拆成 `CLAUDE.md`（规则+架构）+ `docs/CHANGELOG.md`（历史） |
| 缺失 | 无 `README.md`、无 `AGENTS.md` | 至少补一个 10 行 README（是什么/怎么编/装到手机） |
| 记忆缺口 | `~/.claude/projects` 里**没有** RuyueAlarm 的会话目录：Claude Code 从未在该目录下启动过；唯一一次"提到"是 workspace 根目录的健康检查扫描（6 条消息，全是路径列举） | 在该目录下正常使用 Claude Code，让 hooks 开始积累 |

## 9. 怎么验证"某个 agent 到底知道如约多少"

```powershell
cd "D:\My Projects\apps\RuyueAlarm"
ai-memory doctor          # 捕获覆盖：本地会话 vs 已捕获
ai-memory list-projects   # 记忆库里有没有这个 project
```

再让它自述并标注来源：

```powershell
claude -p "列出你对如约项目的了解，逐条标注来源：CLAUDE.md / 仓库代码 / 记忆(memory_query)。不知道的明确说不知道。"
```

判据：**回答全部来自 CLAUDE.md = 记忆系统还没参与**。本目录已放 `.ai-memory.toml`
（`workspace=default`、`project=royelau76-ruyuealarm`），把项目坐标钉死，避免以后改远端导致记忆"换身份证"。

## 10. 下一步建议（按价值排序）

1. ~~修 `docs/方案.md` 的高德段落~~ ✅ 2026-10-30 已完成（顺带修了 `USE_EXACT_ALARM` 权限行）。
2. 拆分 `CLAUDE.md`：演进表搬到 `docs/CHANGELOG.md`，主文件留规则+架构+坑（目标 < 15 KB）。
3. 在 `RuyueAlarm` 目录下用 Claude Code 干活几次，让记忆开始积累；之后 `memory_briefing` 才有东西可看。
4. 可选：`.ai-memory.toml` 加 `[briefing] inject_on_session_start = "true"`，让每次会话开头自动带项目简介（代价是每次多花 token）。
5. M3 前必须做：targetSdk 升 35/36 后复核 ②③ 结论（服务保活、全屏闹钟）。
