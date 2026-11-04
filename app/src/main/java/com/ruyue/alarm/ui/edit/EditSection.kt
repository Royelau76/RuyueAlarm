package com.ruyue.alarm.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruyue.alarm.ui.theme.LocalRuyueColors

// ============================================================================
// 编辑页的卡片（「条件就绪」设计语言 Step 3）
//
// 和旧的 `CollapsibleSection`（折叠区）的区别：
//  · **开关驱动的渐进披露**：卡片右上角就是这一项的开关，关掉就整体收起、副标题说
//    「不靠时间触发」。设计稿的核心主张 —— 参数不该一上来就全摊开
//  · 左侧一条**触发源色**竖条（时间薄荷 / WiFi 薰衣草 / 地点蜜桃）
//  · 大时间可以放大显示（34sp）
//
// 注意：@Composable 里**不要提前 return**，if 分支也不要改变创建的 composable 数量
// （v0.19.3 那次启动闪退就是这么来的，见 docs/版本历史.md）。
// ============================================================================

/** 分区小标题 */
@Composable
fun EditSectionLabel(text: String) {
    val c = LocalRuyueColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = c.textTertiary,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
    )
}

/**
 * 一张设置卡。
 *
 * @param switchOn 非 null 时右上角是开关：**关掉就隐藏内容**（渐进披露）
 * @param bigSummary 大号显示副标题（时间卡用）
 */
@Composable
fun EditCard(
    title: String,
    summary: String = "",
    accent: Color? = null,
    switchOn: Boolean? = null,
    onSwitch: ((Boolean) -> Unit)? = null,
    bigSummary: Boolean = false,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalRuyueColors.current
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    val active = switchOn ?: true
    // 有开关时：开=展开、关=收起；没开关时：靠点击标题切换
    val bodyVisible = if (switchOn != null) active else expanded

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(c.card)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(if (bigSummary && active) 36.dp else 24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (active) (accent ?: c.track) else c.track
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clickable(enabled = switchOn == null && summary.isEmpty()) { expanded = !expanded },
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (active) c.text else c.textTertiary,
                )
                if (summary.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = summary,
                        style = if (bigSummary && active) {
                            MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp)
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                        fontWeight = if (bigSummary && active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) {
                            if (bigSummary) c.text else c.textSecondary
                        } else {
                            c.textTertiary
                        },
                    )
                }
            }
            if (switchOn != null && onSwitch != null) {
                Switch(
                    checked = switchOn,
                    onCheckedChange = onSwitch,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = c.onAccent,
                        checkedTrackColor = accent ?: c.time,
                        uncheckedThumbColor = c.textTertiary,
                        uncheckedTrackColor = c.track,
                        uncheckedBorderColor = c.track,
                    ),
                )
            }
        }

        if (bodyVisible) {
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** 顶部输入卡：标题 + 备注（设计稿第一屏最显眼的那块） */
@Composable
fun HeroInputCard(
    title: String,
    note: String,
    onTitleChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
) {
    val c = LocalRuyueColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(c.card)
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            placeholder = { Text("要提醒什么事（必填）", color = c.textTertiary) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = c.text),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            placeholder = { Text("响的时候显示这句备注", color = c.textTertiary) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
