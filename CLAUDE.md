# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

**如约**（RuyueAlarm）—— 事项提醒闹钟：到点提醒 + **到达指定地点提醒**。

地点确认有两条通路：**连上指定 WiFi**（读 SSID/BSSID）和**进入指定地理范围**。
地理围栏用系统 `LocationManager` **自研**（不依赖高德、不需要 key）。
目标机型 **小米 15 Ultra（HyperOS）**，先自用，成熟后再考虑上小米应用商店；不做多设备/云同步。

## 当前阶段

**M0（技术验证）全部完成** —— ①②③⑤ 通过，④ 按用户要求暂缓并改为自研方案（已在 M2 落地）。
App 里保留「M0 验证台」，随时可回归与排障。

**M1（自用可用版）已通过端到端验收**（v0.2.1-m1，2026-10-09/10 实测）：

| 实测项 | 结果 |
|---|---|
| 连接 → 响铃 | `WIFI_CONNECT` +17s = 去抖 15s + 2s；`ALARM_FIRED` → `RING_OPENED` 仅 1s |
| 锁屏弹出 | 响铃页 `锁屏=true`，全屏意图路径正常 |
| 保活 | 同一实例连续 17 小时（跨夜）零 `SERVICE_STOP`；心跳最大间隔 3.06h 经判读为深度睡眠 |
| 开机自启 | 重启后自动恢复「运行中」 |
| 精确闹钟 | `USE_EXACT_ALARM` 安装即授予，② 页显示「已授权」 |

M1 的能力（v0.2.0 起）：

| 能力 | 实现 |
|---|---|
| 提醒 CRUD | `data/Reminder.kt` · `ui/ReminderListScreen.kt` · `ui/ReminderEditScreen.kt` |
| 时间触发 | `alarm/ReminderScheduler.kt` —— `setAlarmClock` 排定，支持仅一次/每天/工作日/周末/自定义星期 |
| WiFi 触发 | `trigger/TriggerEngine.kt` —— 去抖（连上后确认 N 秒）+ 冷却 |
| 统一响铃 | `alarm/ReminderNotifier.kt` —— 三种触发共用同一套全屏意图 + 响铃页 |
| 跨重启 | `boot/BootReceiver.kt` 重排全部时间提醒；哨兵由看门狗自愈 |
| 排障入口 | 首页右上角「M0 验证台」→ 原来的 7 个验证页 |

**M2 第一批已完成（v0.3.0-m2）**：

| 能力 | 实现 | 要点 |
|---|---|---|
| **自研地理围栏** | `util/LocationHelper.kt` + `TriggerEngine.onLocationUpdate` | 系统 `LocationManager`，**不需要高德 key、不需要第三方 SDK**（少一大块隐私合规）。停留判定防误触，只在有启用的地点提醒时才注册定位（省电） |
| **SSID 历史** | `data/SsidHistory.kt` + 编辑页「用过的网络」 | 哨兵每次连上 WiFi 自动记录，配提醒时点一下就加，不用手输 |
| **运行日志导出** | `util/LogExporter.kt` + `LogsScreen` 的「导出」 | FileProvider 分享 txt（不申请存储权限），排障不用再截图 |
| 小修复 | `ui/Common.kt` 的 `PresetButton` 等 | 选中态不再看起来像禁用；冷却加「1 分钟」（测试用）；列表卡片折叠长 SSID |
| **v0.3.1 实测修正** | `trigger/TriggerEngine.kt` · `sentinel/SentinelWatchdog.kt` | ① **停留判定改为「到点主动取新位置复核」** —— 原来靠"等下一次定位回调"，而 `minDistance=100m` 时人站着不动系统根本不给回调（实测设 1 分钟停留拖到 6.5 分钟才触发）；② 半径加 **20% 滞环**（进 R、出 1.2R）防边界抖动；③ 看门狗阈值 12→**7 分钟**、间隔 15→**10 分钟**（10 分钟是 Doze 对 `setExactAndAllowWhileIdle` 限流 9 分钟的上限） |
| **v0.3.3 修正（v0.3.1 的回归）** | `util/LocationHelper.kt` · `trigger/TriggerEngine.kt` | v0.3.1 的复核函数**只挂了一个 provider 且优先 GPS**，室内十几秒出不来点 → 两天日志里 **49 次「复核取不到位置」**，触发要重试几分钟才成。改为**三层兜底**：① 优先复用系统刚推来的位置（≤90 秒）；② 需要新定位时 **GPS + 网络同时挂**，且**优先要精度 ≤150m 的点**；③ 都拿不到就回退到 **3 分钟内的缓存**。教训：**定位请求一定要挂齐 provider，室内只有网络定位可用** |
| **v0.4.0 省电版** | `sentinel/SentinelService.kt` · `trigger/TriggerEngine.kt` | 电池页实测如约占 **16.23%**（同一时段高德导航 49%、微信 10.8%）—— 严重超标。根因：**长期挂着的 GPS 请求会让系统维持一个 GNSS 跟踪会话**，60 秒间隔几乎等于持续定位（不是那 49 次短请求，那些加起来才几分钟）。改法：**连续监听只挂网络定位**（60s / 200m），GPS 只在「接近目标」时由停留复核**短暂开启**做精确确认；判定拆成两层 —— 粗定位定「候选区」（半径 + max(500m, 半径)）决定何时开始停留计时，GPS 精确复核决定「到底在不在半径内」。⚠️ 国行 HyperOS 没有 GMS，用不了会自动降功耗的 FusedLocationProvider，这个取舍只能自己做。**实测结果：16.23% → 0.35%（46 倍）**，CPU 13 小时只用了 9 秒 |
| **v0.4.1 语义修正** | `trigger/TriggerEngine.kt` | ① **每次到访只提醒一次**：原来只要人还在范围内停留计时就不断重新武装，冷却一过（1 小时）就再响 —— 实测 10-14 09:19 到访被冷却挡掉后 **14:31 同一趟到访又响了一次**；隐患是「在公司待 8 小时 + 冷却 1 小时 = 响 8 次」。现在响过**或被冷却挡掉**都算这次到访处理完，只有真的离开候选范围再回来才重置。② **精度不够不重置计时**：复核拿到精度 >150m 的粗点且判「在外面」时，不急着结论也不重置停留，25 秒后重试（上限 4 次），避免 10-15 那次「25 分钟才响」。**实测结果（10-15~16，23 小时）：复核失败从 49 次/2 天降到 2 次，到访→响铃 2m23s / 3m19s，下午一直待在公司没有再重复提醒**。|
| **v0.4.1 功耗复测（收官）** | — | 电池页：如约 **0.24%**、CPU 总使用时间 **10 秒**（13 小时窗口）—— 比 v0.4.0 的 0.35% / 9 秒还低，说明「精度重试」对功耗无影响。**常驻监听 + 时间/WiFi/地点三种触发的日常开销已降到测量噪声级别** |
| **v0.5.0 M2 第二批** | `data/Reminder.kt` · `trigger/TriggerEngine.kt` · `trigger/Repos.kt` · `alarm/` · `ui/ReminderEditScreen.kt` | ① **触发逻辑「或 / 且」**（`matchMode`）：默认「或」；选「且」时所有启用的条件必须同时成立，典型用法「到点提醒我，但只有我在公司时才提醒」。到点但条件不齐时**跳过本次**（重复提醒的下一次照常排，仅一次则自动关闭）<br>② **时间触发收进 `TriggerEngine.onTimeAlarm`** —— 原来写在 `AlarmReceiver` 里，等于绕过引擎（破坏了「触发只有一条入口」这条约定，也让「且」对时间触发失效）；Receiver 里用新增的 `trigger/Repos.kt` 手工拼仓库<br>③ **自定义铃声**（`ringtoneUri`，系统铃声选择器）<br>④ **响铃渐强**（`volumeRampSeconds`，0/10/20/30 秒）—— 用 `MediaPlayer.setVolume` **只缩放这一路输出**，不改系统音量；MediaPlayer 打不开的厂商铃声自动退回 `Ringtone`<br>⑤ **编辑页「试听」**：不用等触发，直接把草稿当临时提醒拉起响铃页<br>⑥ **擦边容差 5%**：实测 301m vs 300m 差 1 米被拒太可惜<br>⑦ 提醒库 v2→v3 迁移（3 个新列，`ALTER TABLE` + 离线实证与 Room 期望 schema 逐列一致）|
| **v0.5.1 日志质量修正** | `trigger/TriggerEngine.kt` · `sentinel/SentinelService.kt` | 真机跑 92 小时后从日志里抓到两条**误导性证据行**：① 服务重启后 `wasInside` 内存状态为空，第一帧"在外面"会凭空多记一条「离开公司（距离 12640m/16935m）」，读起来像半夜跑了 17 公里 —— 改为**首次观察不发日志**，只有状态真翻转才记；② 断开瞬间 `WifiInfo` 处于切换中间态，读出的 SSID 常是**别的网络**（实测「断开的是 TP-LINK_5G_EF2C」而前后心跳都在 TP-LINK_1602_5G）—— 改为断开事件只报「上次连接：X」。教训：**日志也会骗人，写给未来的自己看时要比写给人看更严谨** |
| **v0.5.2 渐强失效修正** | `alarm/RingActivity.kt` | 用户实测「设了渐强却一上来就最响」。根因：默认铃声用了 `RingtoneManager.getDefaultUri(TYPE_ALARM)`，它返回的是 `content://settings/system/alarm_alert` —— 一个**"指向默认值"的设置 URI**，SettingsProvider 不提供文件描述符，`MediaPlayer.setDataSource` 必然失败 → **静默掉进 `Ringtone` 兜底**，而兜底路径当时没有渐强。改法：① 用 `getActualDefaultRingtoneUri(context, TYPE_ALARM)` 拿真实媒体 URI；② **两条路径都做渐强**（`Ringtone.setVolume(float)` 自 API 21 起可用，javap 对 android-34 已确认）；③ **把「用了哪条音频通路」写进日志**（`响铃音频：MediaPlayer，渐强 15s，uri=…`）—— 这类"沉默降级"必须留证据，否则下次还得猜。教训：**任何 `runCatching` 兜底都要留下"我走了兜底"的痕迹** |
| **v0.5.3 音量可判读 + 震动** | `alarm/RingActivity.kt` · `AndroidManifest.xml` | 渐强修好后用户反馈「绝对音量偏小」，**实测确认是系统闹钟音量偏低**（不是 App 问题）。三处改进：① 日志记录**闹钟音量 X/Y** 与「渐强完成」，让"设备音量小"和"渐强没跑完"从此可分；② 渐强起始 15%→**25%**（再低容易被听成没响）；③ **响铃同时震动**（0.6s 响 / 0.8s 停循环，`USAGE_ALARM` 可穿透静音）。<br>**关键决策：不去偷偷改系统闹钟音量。** 改完必须恢复，而进程被杀就恢复不了 —— 把用户手机音量永久改掉是更坏的事故。震动是零副作用的补偿。理解这一点：**MediaPlayer.setVolume 只是"在系统音量上不衰减"，天花板永远是系统闹钟音量** |
| **v0.5.4 重复触发修正** | `trigger/TriggerEngine.kt` · `sentinel/SentinelService.kt` | 实测 10-20 09:38:55 **同一条提醒在同一秒被触发两次**（两行 `ALARM_FIRED`）。根因：地点触发有**两条**判定路径 —— 停留到点的复核任务，与 `onLocationUpdate` 里那句「兜底：回调来了也顺带判一次」；两者读到的都是同一份旧 `lastFiredAt`（冷却检查发生在 markFired 之前，且 `reminder` 是几秒前的快照），于是冷却双双放行。修法：① **删掉冗余兜底路径**；② `fireIfCooled` 判冷却前**重读库**。教训：**"兜底"一旦与主路径并行，就会变成"双份执行"** —— 兜底只该在主路径确定失效时接管。另：心跳每 5 分钟顺带刷新 `lastSsid`，解决断开事件偶尔报「上次连接：未知」|
| **v0.5.5 「且」模式时间闸门** | `trigger/TriggerEngine.kt` | 用户实测：「到公司 **且** 14:00」的提醒在**早上到公司时就响了**。根因：`unmetConditions()` 只检查了 WiFi 与地点，**从未把时间当条件** —— v0.5.0 的「且」只实现了"时间是主触发"这一个方向，反方向（地点/WiFi 触发时校验时间）漏了。修法：加 `timeGateUnmet()` —— ① 早于时刻 → 不满足；② 到点后 **1 小时内** → 满足（迟到也提醒）；③ 超过 1 小时 → 当作今天错过；④ 今天不在重复日内 → 不满足；⑤ 留 5 秒"早到"容差（闹钟可能略早于整分触发）。<br>**教训：「且」这类逻辑必须双向校验** —— 只把 A 当 B 的闸门不够，B 当 A 的闸门也得做，否则哪条路径先到就先放行 |
| **v0.5.5 实测通过（10-21）** | — | 「且」模式**双向**验证：① 到公司太早 —— `08:37:22「开工提醒」已满足 地点:公司(133m)，但缺少 时间（还没到 09:00），本次不触发`（不响）；② 到点在公司 —— `09:00:00 时间触发 → RING_OPENED 锁屏=true`（响）。另外「到点提醒」（21:30 + WiFi + 且）在 17:58/18:25/19:28 三次连家里 WiFi 时全部被 `缺少 时间（还没到 21:30）` 拦下，21:30 才响。v0.5.4 的重复触发也确认只剩单条 |
| **v0.5.6 启动原因留证** | `sentinel/SentinelService.kt` | 实测 10-20 出现两次 9 分钟内的静默重启（pid 由 23746 → 4557），日志里只有 `SERVICE_START`，**分不清是系统回收后 START_STICKY 拉回来的、还是用户点开 App** —— 而这正是排障第一要问的问题。改为把启动原因写进日志（START_STICKY 重建时 intent 为 null，据此区分）。教训：**"谁把它启动的"只能从 intent 看出来，所以埋点不能放在 onCreate** |
| **v0.6.0 常驻 + 无感（用户要求）** | `RuyueApp.kt` · `boot/BootReceiver.kt` · `sentinel/SentinelService.kt` · `ui/ReminderListScreen.kt` | 用户要求「只要软件启动就开始监听」+「状态栏别一直显示监听中」。① **哨兵改常驻自动**：开 App / 开机 / 装更新三个入口都**无条件**拉起（不再看 `wanted`，因此"你点过停止所以不生效"这个状态不存在了）；自动启动失败会记一条 NOTE（Android 12+ 从后台广播启动 FGS 会被拒）。② **通知降到无感**：新建 `sentinel_quiet` 渠道，`IMPORTANCE_MIN` + `VISIBILITY_SECRET` → 状态栏无图标、不响、不亮屏、锁屏不显示。⚠️ **渠道重要性创建后不可修改，必须换新 channel id**，改旧渠道参数完全无效；旧渠道顺手删掉。③ 首页横幅只剩"正在恢复中"一种状态。<br>**硬约束（对用户也要说明白）**：前台服务的通知**删不掉**（Android 强制），`IMPORTANCE_MIN` 就是"无感"能做到的极限 |
| **v0.6.1 通知残留修正 + 省白跑的 GPS** | `alarm/ReminderNotifier.kt` · `alarm/RingActivity.kt` · `trigger/TriggerEngine.kt` | ① **用户实测：点「我知道了」关掉响铃页后，通知仍留在状态栏，再点它会再响一遍**。根因：`setAutoCancel(true)` 只在**用户点通知**时生效，`onDismiss` 只 `finish()` 了 Activity，从没撤过通知。日志证据是两行「没有 `ALARM_FIRED` 的 `RING_OPENED`」（10-21 19:19、23:58）。修法：新增 `ReminderNotifier.cancel()`，`onDismiss` / `onSnooze` / `onDestroy` 三处都撤。② **省掉白跑的 GPS**：ALL 模式下时间条件明确不成立时（还没到点、或已过容差），`onLocationUpdate` 连停留计时都不起 —— 实测 10-23 08:35 到公司（09:00 的提醒）白跑两轮 GPS 到 08:38 才判出「缺少时间」。<br>**耗电结论（2 天数据）**：后台服务本身没变贵（3 次启动、零 `SERVICE_STOP`、GPS 复核共 ~4 次）；变大的是**响铃页次数从 5 次/2 天涨到 10 次/2 天** —— 用户新增 2 条提醒 + 「到家提醒」每次连回家都响 + 上述 bug 的 2 次误触。每次响铃 = 亮屏 + 循环音频 + 震动 + 最长 10 分钟唤醒锁，比后台监听高一到两个数量级 |
| **v0.6.1 四天实测通过（10-23~27）** | — | 电池页 **0.03%**（折合约 1.6mAh / 4 天 ≈ 平均 **0.02mA**，已低于手机自身测量精度）。日志三份证据：① **7 次触发 ↔ 7 次响铃页一一配对、零幽灵弹窗** —— 通知残留 bug 确认修好（修之前 2 天出现 2 次）；② 定位事件仅 3 次、GPS 复核仅 1 次；零 `SERVICE_STOP` / 零 `ERROR` / 看门狗一次没出手；③ **省电优化见效**：10-26 08:39 到公司附近（319m）→ 09:00 准点响，中间**没有跑任何 GPS 复核**（时间闸门当场判"还没到 9 点"直接跳过），对比 10-23 那次白跑两轮。<br>另：10-25 09:29 出现 `SERVICE_START 原因：系统拉起（进程曾被回收，START_STICKY）` —— **v0.5.6 新加的"启动原因"日志第一次派上用场**，3 分钟内自愈，回答了之前"两次静默重启是谁干的"那个悬案 |
| **v0.7.0 声音不再依赖界面** | `alarm/RingPlayer.kt`（新增）· `alarm/RingActivity.kt` · `alarm/ReminderNotifier.kt` · `RuyueApp.kt` | 用户实测「我在用手机时到点只弹横幅、不主动响」：10-27 09:55 `ALARM_FIRED 09:55:06` → `RING_OPENED 09:55:41`（**晚 35 秒**，且 `锁屏=false`）；其余 6 次都是锁屏状态，全屏意图立即生效（0~7 秒）。根因：**声音是由响铃页播放的**，而亮屏解锁时系统必然把全屏意图降级成横幅 → 界面不弹就没有声音。改法：把播放器从 Activity 提升为**进程级 `RingPlayer`**，**通知一发出就开始响**，响铃页只负责界面（进程由常驻哨兵保着，不怕界面缺席）。配套四项：① 声音/震动从通知渠道里关掉（换新 channel id `alarm_ring` —— 渠道设置创建后不可改），避免双重声音；② 通知改 `setOngoing(true)`：响铃中不能被划掉，否则"有声音却无处可停"；③ 5 分钟无人处理自动停 **并撤掉通知**；④ 响铃页保留「若无声音则补一次播放」的兜底（用户点的是已自动停止的旧通知时）。<br>**教训：闹钟的声音不能挂在 UI 上** —— UI 有可能根本不出现 |
| **v0.8.0~0.8.3 UI 打磨（用户："考虑优化一下UI"）** | `ui/theme/Theme.kt` · `ui/Common.kt` · `ui/ReminderEditScreen.kt` · `ui/ReminderListScreen.kt` · `ui/SettingsScreen.kt`（新增）· `MainActivity.kt` · `values-night/themes.xml`（新增） | 分四批交付：① **深色模式 + 品牌色**。原来只有裸的 `lightColorScheme()`（=默认紫），且平台主题写死 `Theme.Material.Light` → **系统切深色后状态栏是"深色图标压深色界面"，等于看不见**。深色背景对齐响铃页 `#101418`、主色沿用 `#8AB4F8`（浅色压深 `#2F62B5`），补 Typography/Shapes 层级与语义色（原来红绿硬编码，深色下不可读）。② **编辑页折叠 + 固定底栏**：7 个分区全展开要滑 3 屏才见"保存" → 新增 `CollapsibleSection`，标题带配置摘要（「① 到点提醒 · 09:00 工作日」），保存/删除常驻底部。③ **列表页重做**：触发方式改胶囊（`[09:00] 且 [地点 公司]`，长 SSID 不再撑爆卡片）、点整卡编辑、停用灰显、FAB 新建、"M0 验证台"从首页收进新增的**设置页**。④ **细节**：删除二次确认、页面转场动画、保存/删除 Toast 反馈。<br>**教训：UI 我看不到渲染结果** —— 必须小步交付、由用户截图校准；一次改四批等于赌运气 |
| **M0 ④ 高德围栏实测结案（v0.9.0，10-28/29）** | `amap/AmapFenceProbe.kt`（新增）· `ui/AmapFenceScreen.kt` · `app/libs/AMapLocation_11.3.101.aar` | 用户拿到 key 后接入高德定位 SDK。**接入本身踩了两个坑**：① 该 SDK 只在 jcenter 发布过，**jcenter 已关停**，`implementation 'com.amap.api:location:x.y.z'` 必然解析失败 —— 必须从官网下 AAR 放进 `app/libs/`；② 围栏类在 **`com.amap.api.fence`**（不是 `location`），`onGeoFenceCreateFinished` 是 **3 个参数**，触发走**广播**（`createPendingIntent` + `BUNDLE_KEY_FENCESTATUS`）而不是定位回调 —— 三条凭记忆全错，靠 `javap` 读真实签名才纠正。<br>**实测结果（约 12 小时）**：① **围栏一次都没触发**，日志里只有几百条 `AMAP_FENCE 围栏定位失败（定位错误码=4）`，白天夜里都在重试（有时 10 秒一次），把 500 条日志缓冲整个冲掉；② **耗电 9.8%**（对比自研围栏 0.03%，**300 倍**），其中相当部分是整夜失败重试。<br>**关键判读**：错误码 **4 = 请求服务器异常**（官方表），**不是鉴权失败**（鉴权失败是 7）→ 说明 **key / 包名 / SHA1 很可能是对的**，卡在网络链路（手机侧代理/VPN 最可疑）。<br>**结论：不采用**。理由不是"这次没跑通"，而是**功耗量级**：高德是持续高精度定位 + WiFi/基站扫描，自研方案只挂网络定位（60s/200m）、到点才短暂开 GPS。另确认：**地图选点与"用不用高德围栏"是两件不相干的事** —— 地图选点只是选坐标的 UI，围栏判定在哪跑是另一件事；"用当前位置"已经覆盖了选点需求 |
| **v0.10.0 稍后提醒可选 5 / 10 分钟 + 修两个静默失效的真 bug** | `alarm/ReminderScheduler.kt` · `alarm/AlarmReceiver.kt` · `trigger/TriggerEngine.kt` · `alarm/RingActivity.kt` | 用户要求「提醒跳出时增加稍后（5/10 分钟）的选择」。做的时候发现**原来的『稍后提醒』有两个 bug，而且都会静默失效**：① **覆盖掉"下一次"的正式闹钟** —— `scheduleAt` 与正式排期**共用同一个 `PendingIntent` request code**，而 `FLAG_UPDATE_CURRENT` + `setAlarmClock` 会把同一个 code 的旧闹钟替换掉，于是"点过稍后的重复提醒，第二天再也不响"，日志里完全看不出异常；修法：稍后改用**独立 request code**（`fireRequestCode + 200000`）。② **到点后被条件闸门二次拦截** —— 稍后也走 `TriggerEngine.onTimeAlarm`，该函数会按「且」模式重查条件、并重做 `markFired` / 重新排期；稍后 10 分钟后条件稍有变化（比如离开了公司）就被静默跳过。修法：`onTimeAlarm(snoozeMinutes)` 对稍后**直接放行** —— 人工指令不过闸门、也不重复排期。界面：响铃页把「稍后提醒」拆成「稍后 5 分钟 / 稍后 10 分钟」两个按钮（响铃时人在忙，少一次点击就少一分烦躁），并落 `NOTE 稍后提醒：N 分钟后` 作为证据。<br>**教训：复用 request code 等于共享同一个 PendingIntent** —— 一次性排期与长期排期必须分开编号 |
| **v0.11.0 地点收藏（方案第①步）** | `data/SavedPlace.kt`（新增）· `data/SavedPlaceRepository.kt`（新增）· `data/ReminderDb.kt` · `di/AppModule.kt` · `ui/ReminderViewModel.kt` · `ui/ReminderEditScreen.kt` · `tools/verify_migration_v3_v4.py` | 用户要「地图选点 + 自研围栏」，方案定为 **C+B**（地点收藏 + 地址搜索），**跳过地图 SDK** —— 理由：SDK 是"带负债的依赖"（2~3MB 体积 + 隐私声明 + 不确定的 API 面），而"给一个坐标"这件事用 HTTP+JSON 就能做（且刚因为高德定位 SDK 赔了 9.8% 的电）。这一步先做收藏：① 提醒库 **v3→v4** 新增 `saved_places` 表；② 编辑页 ③ 分区加收藏胶囊（点一下填入、**长按删除**带二次确认）+「存为收藏」（同名覆盖，不堆重复项）+ 显示「离你 1.2km」（取一次**缓存**位置，不额外开 GPS）；③ **收藏与提醒是快照关系** —— 提醒存自己的坐标，删改收藏不影响已有提醒（反过来也一样）。<br>**迁移怎么验的**：迁移 SQL 逐字符对着 Room 生成的 `createAllTables` 抄，再用**离线 sqlite** 跑「v3 建库 → 插数据 → 执行迁移 → `PRAGMA table_info` 逐列比对」+ 确认老数据完好，**PASS（8 列全一致）**；脚本留在 `tools/verify_migration_v3_v4.py`，下次迁移照抄改改就能用。<br>**教训：迁移语句必须和 Room 期望 schema 逐字符一致** —— 差一个 `NOT NULL` 或 `AUTOINCREMENT` 就是启动即崩 |
| **v0.12.0 ⚠️ 修一个更重要的错：INTERNET 权限缺失 → M0 ④ 结论作废** | `AndroidManifest.xml` · `net/AmapWebApi.kt`（新增）· `ui/AddressSearchDialog.kt`（新增）· `ui/ReminderEditScreen.kt` · `ui/ReminderViewModel.kt` · `app/build.gradle` | 做「按地址找」时顺手核对权限，发现 **App 从来没有 `INTERNET` 权限**（源清单、合并清单都没有）。这直接解释了 v0.9.0 高德围栏为什么"零触发 + 定位错误码 4"：**SDK 的所有网络请求都被系统拒绝**，然后在失败循环里整夜重试，把耗电烧到 9.8%。<br>**所以「高德围栏不行」这个结论作废** —— 那次测试环境本身是坏的（我们的锅，不是高德的）。修正后的判断：① 高德围栏到底行不行 **未知**（没测成）；② 自研方案仍是当前最优（0.03% 耗电 + 61 秒~3 分 19 秒延迟，都实测过），没必要为了一个未知去换；③ 若要给高德一个公平复测，现在权限补齐了，可以再试一次（可选）。<br>**教训：接任何联网 SDK 前先确认 `INTERNET` 权限** —— 否则会拿到一份"看起来像对方有问题"的假证据，而且会据此做出错误决策 |
| **v0.12.0 按地址找（方案第②步，零 SDK）** | `net/AmapWebApi.kt`（新增）· `ui/AddressSearchDialog.kt`（新增）· `ui/ReminderEditScreen.kt` · `ui/ReminderViewModel.kt` · `app/build.gradle` | 方案 C+B 的 B 部分：**POI 文本搜索为主 + 地理编码兜底**（都是 HTTPS + JSON，用系统自带的 `HttpURLConnection` + `org.json`，**零新依赖**）；选中后拉一张**静态地图缩略图**确认位置，再选半径。关键细节：① `location` 字段是 **"经度,纬度"**，顺序反了会跑到地球另一边（集中在 `parseLocation` 处理）；② 错误码翻译成人话 —— `10009` 就是「**用 Android 平台 key 调 Web 服务接口**」，这是新建 key 时最容易踩的；③ 「按地址找」做成**全屏 Dialog 而不是新路由**：编辑页草稿在 VM 里，换路由会触发 `startEdit(id)` 重新读库，**把用户没保存的改动冲掉**；④ 拿不到缩略图就降级为空占位，不影响选点；⑤ key 走 `local.properties` 的 `AMAP_WEB_KEY`（服务平台必须选「Web服务」）。 |
| **v0.13.0 撤下高德定位 SDK（方案第④步）** | `app/libs/`（已清空）· 删除 `amap/AmapFenceProbe.kt` · `ui/AmapFenceScreen.kt`（改成说明页）· `sentinel/SentinelService.kt` · `data/EventLog.kt` · `app/build.gradle` · `AndroidManifest.xml` · `app/libs/README.md` | 高德**定位** SDK 已无用途（围栏实验未测成、正式方案是自研），**整包移除**：APK **12.2MB → 9.83MB**，`libapssdk.so` 从 APK 里消失，少一个采集 WiFi 列表/BSSID/OAID 的闭源组件（上架时少一份第三方 SDK 隐私声明）。连带清理：`AmapFenceProbe` 整个包、`AMAP_FENCE` 事件类型、SentinelService 里的开启/停止接线、`AMAP_KEY` 的 buildConfigField 与 manifestPlaceholder、清单里的 `com.amap.api.v2.apikey` meta-data 与 `APSService`、`app/build.gradle` 里的 `fileTree(*.aar)` 依赖。<br>**M0 ④ 的页面保留成一张说明页**（写清"未测成 + 为什么 + 要复测需要什么"），而不是悄悄删掉 —— 一个"未结论"的验证项应该继续留在记录里，删掉就等于假装做过了。<br>**教训：删依赖要和删代码引用同步做** —— AAR 一删，所有 import 立刻编译不过，反而是好事（编译器帮你把残留全找出来） |
| **v0.14.0 升级前自动备份提醒库成 JSON（自用阶段 ①）** | `util/Backup.kt`（新增）· `RuyueApp.kt` · `ui/SettingsScreen.kt` · `res/xml/file_paths.xml` | 用户提出「自用阶段升级安全 > 一切」：每次改版最大的风险不是新功能不好用，是**提醒库被弄坏**。<br>**关键在时机**：Room 的迁移是在第一次访问数据库时跑掉的，等我们的代码有机会时库**已经被迁移过了** —— 那时导出的是"新版可能已经坏掉的库"。所以必须放在 `RuyueApp.onCreate` 的**第一件事**、任何 Room 访问之前，且**不能用 Room 打开**（那本身就触发迁移），要用裸 `SQLiteDatabase` 直接读文件、只做 SELECT。<br>**踩到的细节**：优先 `OPEN_READWRITE` 而不是 `OPEN_READONLY` —— Room 默认 WAL 模式，只读打开一个 WAL 库需要能写 `-shm`，否则报 `attempt to write a readonly database`。<br>触发条件 `prefs.lastVersionCode != BuildConfig.VERSION_CODE`；只备不可重建的用户数据（reminders / saved_places / ssid_history），日志库不备；顺手把 `PRAGMA user_version` 记进 JSON（以后迁移出问题能看出这份备份是什么结构）；最多留 5 份；设置页新增「数据备份」可手动备份并按 FileProvider 分享出去。 |
| **v0.15.0 触发判定抽成纯函数 + 18 个单测（自用阶段 ③）** | `trigger/TriggerRules.kt`（新增）· `trigger/TriggerEngine.kt` · `src/test/.../TriggerRulesTest.kt`（新增）· `app/build.gradle` | 用户要求「只测一件事：TriggerEngine 的纯逻辑」，但这个前提**当时不成立** —— 那些函数全是 `suspend`、吃 `Context`、读库、取系统时间，直接单测要 mock 一大堆（正是"全面覆盖"陷阱）。所以先把五条判定抽成**纯函数**（入参全是标量，`now` / `dayOfWeek` / `targetTodayMillis` 由调用方算好传进来）：`timeGateUnmet`（对应 v0.5.5 单向闸门）、`cooldownPassed`（v0.4.1/v0.5.4 重复触发）、`insideCandidate`（v0.3.1 边界抖动）、`withinRadiusTolerance`（v0.5.0 差 1 米被拒）、`maskMatches`。<br>`TriggerEngine` 只留编排，行为一字未改；`gradlew testDebugUnitTest` 本地跑，18 个用例全绿，不需要手机。<br>**明确它的边界**：这类单测挡不住我们真正踩过的那些坑（request code 覆盖、通知没撤、迁移 SQL 写错都是集成/运行时 bug）。它的价值是**防回归**，不是防故障 —— 真正防故障的是 ② 的对账机制。 |
| **踩坑记录：写 Jest/Kotlin 单测时用例名别带 `%`** | — | Windows 上 Gradle 会告警 `Name contains characters which can cause problems on Windows: %`（用例名会被拿去做文件名）。改成中文"两成/五个点"即可。另：`cooldownPassed(0L, …)` 这类"从未触发"的用例必须用**真实量级的时间戳**，用 epoch 附近的小数字会算成"刚响过"而假失败 —— 我自己先写错了一次。 |
| **v0.16.0 触发对账机制（自用阶段 ②）** | `data/ExpectedFire.kt`（新增）· `trigger/Ledger.kt`（新增）· `data/ReminderDb.kt`（v4→v5）· `alarm/ReminderScheduler.kt` · `trigger/TriggerEngine.kt` · `sentinel/SentinelWatchdog.kt` · `RuyueApp.kt` · `ui/SettingsScreen.kt` · `tools/verify_migration_v4_v5.py`（新增） | 用户提出"把「提醒会不会响」变成可检测的"。实现成四步：① **记账** —— `ReminderScheduler.schedule()` 排期时写一条 `EXPECTED`，`(reminderId, expectedAt)` 上唯一索引 + `INSERT OR IGNORE`，所以保存/开机/重排**天然幂等**；② **销账** —— 真响了 `FIRED`（`onTimeAlarm` 里，±2 分钟窗口匹配）；被「且」闸门故意拦下 `SKIPPED`；提醒被删/停用 `CANCELLED`；③ **判漏** —— 过 2 小时仍 `EXPECTED` → `MISSED`，**主动推通知**并附归因（同期日志里找 `SERVICE_STOP` / `BOOT`，直接写进通知正文）；④ **兜底** —— 最近 72 小时"有预期、零实际响铃且至少 2 条漏"→ 单独告警「整个 App 可能已失效」。<br>**三个关键设计**：<br>· **`SKIPPED` 必须和 `MISSED` 分开** —— 把"故意不响"报成"没响"，报表天天假警，三天后用户就不看了，机制等于白做；<br>· **对账器自己也可能死** —— `force-stop` 后定时链路全断，所以两腿走路：看门狗每 10 分钟顺带跑一次（内部 20 小时节流）+ **每次打开 App 补扫**；<br>· **记账不能阻塞排期** —— 排期发生在主线程/广播里，走独立单线程 `Executors`，失败静默（记账失败绝不能影响提醒本身）。<br>设置页新增「自检对账」：手动跑一次 + 显示「最近 7 天：应响 X · 实际响 Y · 按设计跳过 Z · 漏掉 W」。<br>迁移 v4→v5 用老办法离线验：列（7 个）/ 索引（2 个含唯一）/ **唯一索引真的能拒重复记账** / 老数据完好 —— **PASS**（`tools/verify_migration_v4_v5.py`）。 |

> **地点触发为什么不用第三方围栏 SDK**：不需要 key、不需要在隐私政策里声明第三方 SDK 收集信息，
> 自用场景精度足够（半径 200~1000 米，室外 10~30 米、室内 50~200 米）。
> 地图选点也刻意不做 —— 站在要提醒的地方按一下「用当前位置」最省事。

M0 的 5 个验证项原文保留在下表：

| # | 验证项 | 页面 | 对应实现 | 结果 |
|---|---|---|---|---|
| ① | 能不能读到 SSID（需要哪些权限） | `ui/WifiProbeScreen.kt` | `util/WifiReader.kt` | ✅ **结案**：需三权限 + 去 `neverForLocation` + 只用 `WifiManager` 读 |
| ② | 前台服务保活（锁屏 8 小时 / 重启手机） | `ui/SentinelScreen.kt` | `sentinel/` 整个包 | ✅ **通过**：8 小时心跳连续、零 `SERVICE_STOP`；开机自启 + 后台读 SSID + 看门狗精确闹钟均已实测 |
| ③ | 锁屏下全屏闹钟能不能弹出 | `ui/AlarmTestScreen.kt` | `alarm/` 整个包 | ✅ **通过** |
| ④ | 高德围栏触发延迟与耗电 | `ui/AmapFenceScreen.kt` | `amap/AmapFenceProbe.kt`（v0.9.0 接入） | ⚠️ **未测成（v0.12.0 才找到原因）**：v0.9.0 那次「零触发 + 耗电 9.8%」的真实原因是 **App 缺 `INTERNET` 权限**（已补），SDK 的请求全被系统拒绝 → 整夜失败重试。所以**高德围栏的真实延迟与耗电仍然未知**。自研方案实测 **0.03% + 61 秒~3 分 19 秒**，仍是当前选择；要不要给高德一次公平复测 = 可选项 |
| ⑤ | WiFi 连断频率 → 去抖参数 | `ui/DebounceStatsScreen.kt` | 数据来自 `sentinel` 写的日志 | ✅ 72 小时仅连断 2~3 次 → **去抖定 15s、冷却一天** |

验证步骤、判读标准、结果记录表见 `docs/M0-验证手册.md`。

### ① 踩到的坑（务必记住）

**不要给 `NEARBY_WIFI_DEVICES` 加 `android:usesPermissionFlags="neverForLocation"`。**

M0 实测（小米 15 Ultra / HyperOS）三轮结果：

| # | 条件 | SSID |
|---|---|---|
| 1 | 两权限齐全，**带** `neverForLocation`（v0.1.0） | ❌ `<unknown ssid>` |
| 2 | 只有 `NEARBY_WIFI_DEVICES`，不带标记，**无**位置权限（v0.1.1） | ❌ `<unknown ssid>` |
| 3 | 三权限齐全，不带标记（v0.1.1） | ✅ **读到真名** |

**结案规则**：读 SSID 需要 `NEARBY_WIFI_DEVICES` + `ACCESS_FINE_LOCATION`
（后台读还需 `ACCESS_BACKGROUND_LOCATION`），且**不能**声明 `neverForLocation`。

### ⚠️ 第 3 轮最重要的发现：两条读取路径结果不同

| 读取路径 | 权限齐全时的结果 |
|---|---|
| `NetworkCapabilities.getTransportInfo()` | ❌ 仍返回脱敏值（SSID/BSSID 都拿不到） |
| `WifiManager.getConnectionInfo()` | ✅ 返回真实 SSID / BSSID |

**所以 M1 必须走 `WifiManager` 这条路。** `util/WifiReader.kt` 目前的
`ssid = capsSsid ?: legacySsid` 逻辑正好覆盖了这一点（caps 拿不到就落回 legacy），
保留两条路是对的 —— 不要简化成只用 caps，那会导致「权限齐全却读不到 SSID」这种
极难排查的故障。

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

### 三条必须守住的约定

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

## 小米 / HyperOS 的现实约束

- 必须在 HyperOS 里手动开：**自启动**、**省电策略→无限制**、**后台弹出界面**（不开则锁屏闹钟弹不出来）、
  通知权限 + 锁屏显示通知。`ui/GuideScreen.kt` 提供了跳转入口。
- 自启动、后台弹出界面是小米私有权限，**adb 授不了**；只有 Doze 白名单能用
  `adb shell dumpsys deviceidle whitelist +com.ruyue.alarm`。
- **用户点「强行停止」后，所有闹钟与监听失效**（Android 硬限制，绕不过）。
  正式版必须有「打开应用即重新排定」的逻辑。
- 国行 HyperOS 默认无 Google 服务框架 → 地理围栏统一走**高德**，不用系统 Geofencing API。
  （④ 已暂缓；推荐改用系统 `LocationManager` 自研围栏，零 key、零第三方 SDK、少一大块合规。）
- **精确闹钟权限别用 `SCHEDULE_EXACT_ALARM`**：Android 14 起它默认拒绝，而且**非闹钟类应用
  不会出现在「闹钟和提醒」列表里**，用户无处授权（v0.1.2 实测踩到）。
  本应用核心就是闹钟/提醒，直接用 `USE_EXACT_ALARM`（Android 13+ 普通权限，安装即授予）；
  `SCHEDULE_EXACT_ALARM` 只作 Android 12~12L 的兼容声明保留。
- 自愈机制（`sentinel/SentinelWatchdog.kt`）用**闹钟+广播**而不是 WorkManager：Android 12+ 禁止
  后台启动前台服务，而「精确闹钟触发的广播」是官方豁免之一；WorkManager 没这个豁免。
  自动拉起失败时降级为一条高优先级通知（用户点击＝用户操作，同样豁免）。

## 版本与工具链升级路径

M1 做自用可用版（时间闹钟 + WiFi 触发 + 响铃管线 + 引导页）；
M2 已完成两批：第一批（自研地理围栏 + SSID 历史 + 日志导出）、第二批（触发逻辑或/且 + 自定义铃声 + 渐强 + 擦边容差）；
**地图选点已明确不做**（用户决定）；再往后的候选：每月某日重复、响铃页界面打磨；
M3 隐私合规 + release 签名 + targetSdk 升级复核（②③ 必须复核）+ 小米商店提审。
