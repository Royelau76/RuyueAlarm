# 参与贡献

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
