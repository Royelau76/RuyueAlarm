package com.ruyue.alarm.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ruyue.alarm.net.AmapWebApi
import kotlinx.coroutines.launch

/**
 * 「按地址找地点」—— 给**没去过的地方**设提醒用的。
 *
 * ## 为什么是一个全屏 Dialog 而不是新页面
 *
 * 编辑页的草稿存在 `ReminderViewModel` 里，返回编辑页会触发 `startEdit(id)` 重新读库 ——
 * 如果走路由跳转，**用户在编辑页还没保存的改动会被冲掉**。用 Dialog 就没有这个问题：
 * 草稿一直是同一份，选完直接写进去。
 *
 * ## 流程
 *
 * 输入关键词（建议带上城市）→ 搜索（POI 文本搜索为主、地理编码兜底）→ 点一条结果 →
 * 看静态地图缩略图确认位置 → 选半径 → 「用它」。
 */
@Composable
fun AddressSearchDialog(
    onDismiss: () -> Unit,
    onPick: (name: String, latitude: Double, longitude: Double, radiusMeters: Int) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            val scope = rememberCoroutineScope()

            var keywords by remember { mutableStateOf("") }
            var city by remember { mutableStateOf("") }
            var searching by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf("") }
            var hits by remember { mutableStateOf<List<AmapWebApi.PlaceHit>>(emptyList()) }
            var selected by remember { mutableStateOf<AmapWebApi.PlaceHit?>(null) }

            // 选中后：可改的名字 + 半径 + 缩略图
            var pickedName by remember { mutableStateOf("") }
            var radius by remember { mutableStateOf(300) }
            var preview by remember { mutableStateOf<ImageBitmap?>(null) }
            var previewLoading by remember { mutableStateOf(false) }

            LaunchedEffect(selected) {
                preview = null
                val s = selected ?: return@LaunchedEffect
                previewLoading = true
                pickedName = s.name
                preview = AmapWebApi.staticMap(s.latitude, s.longitude)?.asImageBitmap()
                previewLoading = false
            }

            Column(Modifier.fillMaxSize()) {
                // ---- 顶部 ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("← 返回") }
                    Spacer(Modifier.width(4.dp))
                    Text("按地址找地点", style = MaterialTheme.typography.titleLarge)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    if (!AmapWebApi.hasKey()) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text("还没配置高德「Web服务」Key", style = MaterialTheme.typography.titleMedium)
                                Hint(
                                    "在 RuyueAlarm/local.properties 里加一行：\n" +
                                        "    AMAP_WEB_KEY=你的key\n" +
                                        "然后重新打包安装。\n\n" +
                                        "注意：这个 key 的「服务平台」必须选 **Web服务**，\n" +
                                        "选 Android 平台会报「平台不匹配」（infocode 10009）。"
                                )
                            }
                        }
                        return@Column
                    }

                    // ---- 搜索输入 ----
                    OutlinedTextField(
                        value = keywords,
                        onValueChange = { keywords = it },
                        label = { Text("地点名或地址（比如「第一人民医院」「XX路8号」）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text("城市（建议填）") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    searching = true
                                    error = ""
                                    hits = emptyList()
                                    selected = null
                                    when (val r = AmapWebApi.search(keywords, city)) {
                                        is AmapWebApi.Result.Ok -> hits = r.hits
                                        is AmapWebApi.Result.Fail -> error = r.message
                                    }
                                    searching = false
                                }
                            },
                            enabled = !searching && keywords.isNotBlank(),
                        ) { Text(if (searching) "搜索中…" else "搜索") }
                    }
                    Hint("「第一人民医院」这类名字每个城市都有，填了城市结果才准。")

                    if (error.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Card(Modifier.fillMaxWidth()) {
                            Text(
                                text = error,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    // ---- 结果列表 ----
                    if (hits.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("搜索结果（点一条看位置）", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        hits.forEach { hit ->
                            val isSelected = selected == hit
                            Card(
                                onClick = { selected = hit },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = hit.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        if (hit.address.isNotBlank()) {
                                            Text(
                                                text = hit.address,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            text = "${hit.source} · %.5f, %.5f".format(hit.latitude, hit.longitude),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (isSelected) {
                                        Text("✓", style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    // ---- 选中后的确认区 ----
                    selected?.let { hit ->
                        Spacer(Modifier.height(16.dp))
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text("确认位置", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))

                                if (preview != null) {
                                    Image(
                                        bitmap = preview!!,
                                        contentDescription = "位置预览",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .clip(MaterialTheme.shapes.medium),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(90.dp)
                                            .clip(MaterialTheme.shapes.medium)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (previewLoading) "正在加载地图缩略图…" else "缩略图不可用（不影响使用）",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = pickedName,
                                    onValueChange = { pickedName = it },
                                    label = { Text("地点名称（会显示在提醒卡片上）") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                Spacer(Modifier.height(10.dp))
                                Text("触发半径：${radius} 米", style = MaterialTheme.typography.bodyMedium)
                                Hint("地图/地址只能定位到大致位置，半径别设太小 —— 200 米以下容易漏判。")
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(200, 300, 500, 1000).forEach { r ->
                                        PresetButton("${r}m", radius == r) { radius = r }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        onPick(
                                            pickedName.ifBlank { hit.name },
                                            hit.latitude,
                                            hit.longitude,
                                            radius,
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("用它") }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
