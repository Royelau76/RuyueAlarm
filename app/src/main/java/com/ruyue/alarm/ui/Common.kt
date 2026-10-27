package com.ruyue.alarm.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruyue.alarm.ui.theme.LocalRuyueSemanticColors

/** 所有验证页共用的外壳：返回按钮 + 标题 + 可滚动内容。 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Spacer(Modifier.width(4.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))
        content()
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/**
 * 可折叠分区 —— 标题右侧带一行**当前配置摘要**。
 *
 * 为什么需要（v0.8.1 编辑页重构）：编辑页有 7 个分区，全展开要滑 3 屏才够到"保存"。
 * 折叠后不展开也能看清这条提醒是怎么配的（比如「① 到点提醒 · 09:00 工作日」），
 * 要改哪一项就点开哪一项。
 */
@Composable
fun CollapsibleSection(
    title: String,
    summary: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(14.dp)) { content() }
            }
        }
    }
}

/** 一行「键：值」，good=true 绿色 / false 红色 / null 默认色。颜色走语义色，深色模式下也看得清。 */
@Composable
fun KV(key: String, value: String, good: Boolean? = null) {
    val semantic = LocalRuyueSemanticColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = key,
            modifier = Modifier.width(116.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = when (good) {
                true -> semantic.success
                false -> semantic.danger
                null -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/**
 * 选项按钮：**选中 = 实心，未选中 = 描边**。
 *
 * 之前 ⑤ 页用 `enabled = false` 表示「当前选中」，结果选中项看起来像禁用项 ——
 * 我自己看截图时都被骗了一次。统一用这个组件，视觉语义全 App 保持一致。
 */
@Composable
fun PresetButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val padding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
    if (selected) {
        Button(onClick = onClick, contentPadding = padding) { Text(label, fontSize = 13.sp) }
    } else {
        OutlinedButton(onClick = onClick, contentPadding = padding) { Text(label, fontSize = 13.sp) }
    }
}
