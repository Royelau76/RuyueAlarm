# -*- coding: utf-8 -*-
"""开源准备：README / LICENSE / CONTRIBUTING / issue 模板。"""
import os

ROOT = r"D:\My Projects\apps\RuyueAlarm"


def w(rel, text):
    p = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, "w", encoding="utf-8", newline="\n").write(text)
    print("  ✓", rel, len(text), "字符")


README = """# 如约 · RuyueAlarm

> 约定一件事：**什么时间**、**连上哪个 WiFi**、**到了哪儿** —— 条件都满足了，它才来叫你。

一个 Android 事项提醒 App，把「时间 / WiFi / 地点」三种触发方式做成同一种东西：**触发条件**。

## 它解决什么问题

系统日历和闹钟只认时间。但生活里很多"提醒"是**有前提**的：

- 到家了再提醒我把快递拿上来（前提：连上家里 WiFi / 进入小区范围）
- 到公司了提醒我交周报（前提：到了公司）
- 每天 9:00 提醒开工，但**必须人已经在公司**才算（时间 + 地点同时满足）

如约把这类需求做成一条「约定」：

| 条件 | 说明 |
|---|---|
| **时间** | 仅一次（**可指定具体日期**）/ 每天 / 工作日 / 周末 / 自定义星期 |
| **连上指定 WiFi** | 可加多个 SSID，连上后**再去抖 N 秒**才确认（防电梯口、地铁站 Wi-Fi 抖动误触发） |
| **进入指定地点范围** | 半径 200~2000m + **停留 N 秒**（开车路过不算到过）；**自研地理围栏**，零第三方 SDK、零 key |
| **条件之间** | 「任一满足」或「同时满足」 |

除条件触发外还有：全屏响铃页（锁屏也能弹）、稍后 5/10 分钟、**今天不再**（抑制到第二天 0 点自动恢复）、暂停/恢复、冷却时间、自定义铃声与渐强。

## 三个设计选择

**1. 颜色 = 触发源。** 薄荷=时间、薰衣草=WiFi、蜜桃=地点。列表主卡上有一条「路径」：段数 = 启用条件数，达成的段用该条件的颜色，白点是"此刻"。不用读字就知道靠什么触发、缺哪一环。

**2. 日志即证据。** 所有事件（连上 WiFi、进入范围、闹钟触发、响铃页弹出…）都写进本地事件表。因此 App 内置**对账自检**：每天核一遍「该响的是不是都响了」，漏了会推送通知并说明可能原因。也是 issue 排障的主要依据。

**3. 不靠云。** 没有账号、没有同步、没有分析 SDK、没有广告。数据只在两个本地 Room 数据库里。

## 状态

- **v0.27.0**，自用阶段（**测试版，可能有 bug**）
- 目标机型：**小米 15 Ultra / HyperOS**（其他机型未验证；`minSdk 26`，理论上 Android 8.0+ 可跑）
- `targetSdk 34`：升级到 35/36 后 **WiFi 触发与地理围栏必须重新回归**（已知待办）
- 单元测试：27 个（触发规则 / 时间排期）

## 安装

到 [Releases](../../releases) 下载 `app-debug.apk`（**debug 签名，仅供测试**），然后：

```bash
adb install -r app-debug.apk
```

或直接把 apk 传到手机上安装。

## ⚠️ 必开的系统权限（不开启就"闹钟不响"）

HyperOS / MIUI 上这四样必须手动开（App 内有引导页，可一键跳转）：

1. **自启动**
2. **省电策略 → 无限制**
3. **后台弹出界面** ← 最关键，不开的话锁屏闹钟只会变成一条普通通知
4. **通知权限 + 锁屏显示通知**

另外：在系统设置里点过「强行停止」会让所有闹钟与监听失效（Android 硬限制），重新打开一次 App 即可自动重排。

## 从源码构建

需要 JDK 17 与 Android SDK（compileSdk 34）：

```bash
# local.properties（不入库）
sdk.dir=/path/to/Android/Sdk
# 可选：只有「按地址找」需要高德 Web 服务 key，没有也能正常使用其他功能
AMAP_WEB_KEY=your_key

./gradlew assembleDebug      # 产物 app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest  # 27 个单测
```

技术栈：Kotlin 1.9.20 / AGP 8.2.0 / Jetpack Compose（BOM 2024.09.00）/ Room 2.6.1 / Hilt 2.48.1。

## 隐私

- **不上传任何数据**。没有账号、没有云同步、没有统计 SDK、没有广告。
- 位置只在**本机**判断（`LocationManager` 自己算距离），不发给任何服务器。
- 唯一的外发请求：「按地址找」会把关键词发给**你自己的**高德 Web 服务 key。不用这个功能就完全不联网。

## 已知限制

- 小米私有权限（自启动 / 后台弹出界面）**adb 授不了**，只能手点
- 用户点「强行停止」后所有闹钟失效（Android 硬限制）
- `targetSdk 34` 未升级；Android 15/16 上以兼容模式运行
- 没有「每月某日」这类复杂重复规则（欢迎提 issue）
- 仅在 HyperOS 上实测过

## 参与

- 遇到问题请按 **Bug 模板** 提 issue。模板要求贴「设置 → 运行日志 → 导出」的 txt —— 这个 App 的核心诊断手段就是日志，没有它基本只能靠猜。
- 想改代码请看 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 许可

[MIT](LICENSE)
"""

LICENSE = """MIT License

Copyright (c) 2026 Royelau76

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
"""

CONTRIBUTING = """# 参与贡献

## 提 issue 之前

1. 确认已经开了四样系统权限（自启动 / 省电无限制 / 后台弹出界面 / 通知+锁屏显示）——
   90% 的「闹钟不响」都是这四样没开
2. 到「设置 → 自检对账」点一次，看有没有「该响没响」
3. **导出日志**：设置 → 运行日志 → 导出。贴到 issue 里，没有日志我基本只能靠猜

## 代码约定（改之前请先看）

- `docs/版本历史.md` 里几乎每一行判定都有前因（时间闸门、冷却、滞回、PendingIntent request code、
  迁移 SQL…）。**动 `trigger/` · `alarm/` · `data/` 之前先 grep 一遍**，否则很容易改回老 bug。
- **触发只有一条入口**：`TriggerEngine.fire()` / `ReminderNotifier.show()`。不要在别处另开响铃逻辑。
- **两个数据库**：`ruyue.db`（事件日志，冻结在 v1）与 `ruyue_reminders.db`（用户数据，当前 v7）。
  改提醒表要写迁移，并且**迁移 SQL 必须和实体定义逐列对齐**（对不上 = 启动即崩）。
- **Compose 硬规则**：`@Composable` 里不要提前 `return`；`if` 分支不要改变创建的 composable/动画数量。
  这两条都会破坏 Compose 的组合槽表，表现为莫名其妙的 `ArrayIndexOutOfBoundsException`（详见版本历史）。
- 纯逻辑（触发判定、时间排期）请写成**纯函数**放进 `trigger/TriggerRules`、`alarm/ReminderScheduler`，并补单测。
- 提交前跑 `./gradlew testDebugUnitTest`，27 个用例应全绿。

## 提交 PR

- 一个 PR 一件事，说明「为什么」而不只是「改了什么」
- UI 改动请附截图
- 如果修的是 issue，标题里带上 `#编号`
"""

BUG = """name: Bug 报告
description: 提醒没响 / 响错 / 闪退 / 界面问题
title: "[Bug] "
labels: ["bug"]
body:
  - type: markdown
    attributes:
      value: |
        先做个自查：**四样系统权限**（自启动 / 省电无限制 / 后台弹出界面 / 通知+锁屏显示）都开了吗？
        90% 的「闹钟不响」都是这里。
  - type: input
    id: device
    attributes:
      label: 机型与系统
      placeholder: 小米 15 Ultra / HyperOS 2.0.5.0 / Android 15
    validations:
      required: true
  - type: input
    id: version
    attributes:
      label: App 版本
      description: 设置页最下方或关于里能看到
      placeholder: v0.27.0 (versionCode 84)
    validations:
      required: true
  - type: textarea
    id: what
    attributes:
      label: 发生了什么
      description: 期望什么、实际什么
      placeholder: 期望 9:00 响，实际没响；或者响了两次…
    validations:
      required: true
  - type: textarea
    id: steps
    attributes:
      label: 复现步骤
      placeholder: |
        1. 新建一条约定：时间 9:00 + 连上家里 WiFi，同时满足
        2. …
  - type: textarea
    id: log
    attributes:
      label: 运行日志（必需）
      description: |
        设置 → 运行日志 → 导出，把 txt 内容贴在这里。
        日志里带每一条触发/跳过/异常的原始记录，是排障的唯一依据。
      render: text
    validations:
      required: true
  - type: checkboxes
    id: perms
    attributes:
      label: 自查
      options:
        - label: 四样系统权限都已开启
        - label: 最近没有在系统设置里点过「强行停止」
"""

FEATURE = """name: 功能建议
description: 想要新的触发方式 / 新的参数 / 交互改进
title: "[Feature] "
labels: ["enhancement"]
body:
  - type: textarea
    id: scene
    attributes:
      label: 你遇到的实际场景
      description: 先说场景再说方案 —— 场景比方案重要
      placeholder: 我想让提醒只在周末生效，而且必须是到家之后…
    validations:
      required: true
  - type: textarea
    id: idea
    attributes:
      label: 你希望它怎么工作
  - type: input
    id: device
    attributes:
      label: 机型与系统（可选）
      placeholder: 小米 15 Ultra / HyperOS
"""

w("README.md", README)
w("LICENSE", LICENSE)
w("CONTRIBUTING.md", CONTRIBUTING)
w(".github/ISSUE_TEMPLATE/bug_report.yml", BUG)
w(".github/ISSUE_TEMPLATE/feature_request.yml", FEATURE)
print("完成")
