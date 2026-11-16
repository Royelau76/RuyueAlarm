package com.ruyue.alarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ruyue.alarm.ui.design.RuyueBackground
import com.ruyue.alarm.ui.theme.LocalRuyueColors

/**
 * 使用帮助（2026-11-16 新增）。
 *
 * 起因：设置页每条都挂着两三行说明文字，界面显得很啰嗦（用户原话："注释性文字太多"）。
 * 处理办法不是删掉信息，而是**把说明集中到这一页**：
 * 设置页每项只留标题 + 一句短说明，想细看的人进这一页。
 */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val c = LocalRuyueColors.current

    RuyueBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "‹ 返回",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.time,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
            ) {
                Text(
                    text = "使用帮助",
                    style = MaterialTheme.typography.headlineLarge,
                    color = c.text,
                )
                Text(
                    text = "一条约定 = 一件事 + 一组触发条件。条件满足才叫你。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )

                // ---------------- 三种触发 ----------------
                HelpLabel("三种触发条件")
                HelpCard {
                    HelpRow(
                        "时间",
                        "仅一次（可指定具体日期）/ 每天 / 工作日 / 周末 / 自定义星期。",
                        c.time,
                        c,
                    )
                    HelpDivider(c)
                    HelpRow(
                        "连上指定 WiFi",
                        "读到目标 SSID 并保持「去抖 N 秒」才算成立 —— 电梯口、地铁站 WiFi 一闪而过不算。",
                        c.wifi,
                        c,
                    )
                    HelpDivider(c)
                    HelpRow(
                        "进入指定范围",
                        "进入半径范围后还要「停留 N 秒」才算到过 —— 开车路过不算。围栏在本机自己算，不联网。",
                        c.place,
                        c,
                    )
                }

                // ---------------- 多条件 ----------------
                HelpLabel("两个以上条件怎么组合")
                HelpCard {
                    Column(Modifier.padding(16.dp)) {
                        HelpPara("「任一满足」（默认）", "任意一个条件成立就响。", c)
                        Spacer(Modifier.height(10.dp))
                        HelpPara(
                            "「同时满足」",
                            "所有条件同时成立才响。典型用法：时间 09:00 + 地点公司 = " +
                                "「到公司之后，到 9 点才提醒我交周报」。\n" +
                                "如果到点了但其他条件不满足（比如人还没到公司），这次会跳过，" +
                                "下一次照常排定 —— 运行日志里能看到跳过原因。",
                            c,
                        )
                    }
                }

                // ---------------- 冷却 ----------------
                HelpLabel("冷却")
                HelpCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "只有带 WiFi / 地点的约定才有冷却：冷却期内同一条约定最多响一次，" +
                                "防止「家里 WiFi 一天重连十几次」被反复吵。\n" +
                                "纯时间提醒不需要冷却（一天到点只响一次），所以编辑页里不会出现这一项。",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                }

                // ---------------- 权限 ----------------
                HelpLabel("为什么必须开四样权限")
                HelpCard {
                    HelpRow("自启动", "开机或重启后，App 能自己回来继续守着。", c.time, c)
                    HelpDivider(c)
                    HelpRow("省电策略 → 无限制", "否则息屏后会被系统冻结，闹钟可能延后。", c.time, c)
                    HelpDivider(c)
                    HelpRow(
                        "后台弹出界面",
                        "最关键的一项：不开的话锁屏闹钟只会变成一条普通通知，不会全屏弹出。",
                        c.place,
                        c,
                    )
                    HelpDivider(c)
                    HelpRow("通知 + 锁屏通知", "没开通知权限，触发时就只剩日志，什么都看不到。", c.time, c)
                }
                Text(
                    text = "在系统设置里点「强行停止」会让所有闹钟与监听失效（Android 硬限制）。" +
                        "不用慌 —— 重新打开一次 App，它会自动重新排定全部提醒。",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )

                // ---------------- 排障 ----------------
                HelpLabel("不响的时候怎么查")
                HelpCard {
                    HelpRow("① 自检诊断", "先看权限有没有缺口、哨兵最近有没有心跳。", c.wifi, c)
                    HelpDivider(c)
                    HelpRow(
                        "② 运行日志",
                        "每次触发、跳过、异常都记在这里（默认隐藏心跳噪音）。报问题时导出 txt 最有用。",
                        c.wifi,
                        c,
                    )
                    HelpDivider(c)
                    HelpRow(
                        "③ 自检对账",
                        "每天自动核对「该响的是不是都响了」，漏了会推送通知并说明可能原因。",
                        c.wifi,
                        c,
                    )
                }

                // ---------------- 数据 ----------------
                HelpLabel("数据")
                HelpCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "所有数据只在本机：两个数据库，一个是提醒，一个是运行日志。" +
                                "没有账号、没有云同步、没有统计 SDK、没有广告。\n\n" +
                                "每次升级前会自动把提醒库备份成 JSON；也可以在「设置 → 数据备份」里手动导出一份留档。",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun HelpLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalRuyueColors.current.textTertiary,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun HelpCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(LocalRuyueColors.current.card),
        content = content,
    )
}

@Composable
private fun HelpDivider(c: com.ruyue.alarm.ui.theme.RuyueColors) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(c.track.copy(alpha = 0.45f)),
    )
}

@Composable
private fun HelpRow(
    title: String,
    desc: String,
    accent: androidx.compose.ui.graphics.Color,
    c: com.ruyue.alarm.ui.theme.RuyueColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            Spacer(Modifier.height(3.dp))
            Text(desc, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        }
    }
}

@Composable
private fun HelpPara(title: String, body: String, c: com.ruyue.alarm.ui.theme.RuyueColors) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
    Spacer(Modifier.height(3.dp))
    Text(body, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
}
