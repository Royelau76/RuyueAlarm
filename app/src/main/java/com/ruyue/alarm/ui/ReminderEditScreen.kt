package com.ruyue.alarm.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ruyue.alarm.alarm.AlarmScheduler
import com.ruyue.alarm.alarm.ReminderScheduler
import com.ruyue.alarm.alarm.RingActivity
import com.ruyue.alarm.data.MatchMode
import com.ruyue.alarm.data.Repeat
import com.ruyue.alarm.data.SavedPlace
import com.ruyue.alarm.util.LocationHelper

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ReminderEditScreen(vm: ReminderViewModel, id: Long, onBack: () -> Unit) {
    LaunchedEffect(id) { vm.startEdit(id) }

    val context = LocalContext.current
    val draft by vm.draft.collectAsStateWithLifecycle()
    val currentSsid by vm.currentSsid.collectAsStateWithLifecycle()
    val history by vm.ssidHistory.collectAsStateWithLifecycle()
    val savedPlaces by vm.savedPlaces.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val locating by vm.locating.collectAsStateWithLifecycle()

    var manualSsid by remember { mutableStateOf("") }

    // 删除前二次确认：底部「删除」现在是常驻按钮，误触就再也找不回这条提醒了
    var confirmDelete by remember { mutableStateOf(false) }

    // 收藏地点的长按删除确认
    var pendingDeletePlace by remember { mutableStateOf<SavedPlace?>(null) }

    // 收藏列表要显示「离你多远」：取一次**缓存**位置（cacheFirst，不会额外开 GPS）
    var myLat by remember { mutableStateOf<Double?>(null) }
    var myLon by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) {
        val loc = LocationHelper.current(context, cacheFirst = true) ?: return@LaunchedEffect
        myLat = loc.latitude
        myLon = loc.longitude
    }

    // 系统铃声选择器，选完直接写进草稿
    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = result.data?.let {
                IntentCompat.getParcelableExtra(
                    it,
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java,
                )
            }
            // 尽量把读权限持久化，否则设备重启后这个 URI 可能读不到（拿不到就静默忽略）
            if (picked != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        picked,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            vm.setRingtone(picked?.toString())
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ---- 固定顶部：返回 + 标题 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (id > 0L) "编辑提醒" else "新建提醒",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        // ---- 可滚动内容 ----
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {

        message?.let {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, Modifier.weight(1f), fontSize = 14.sp)
                    TextButton(onClick = { vm.consumeMessage() }) { Text("知道了") }
                }
            }
        }

        // ---------------- 基本信息 ----------------
        CollapsibleSection(
            title = "基本信息",
            summary = draft.title.ifBlank { "还没填标题" },
            initiallyExpanded = true,
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { v -> vm.updateDraft { it.copy(title = v) } },
                label = { Text("提醒什么（必填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.note,
                onValueChange = { v -> vm.updateDraft { it.copy(note = v) } },
                label = { Text("备注（可选，响铃时会显示）") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ---------------- ① 时间 ----------------
        CollapsibleSection(
            title = "① 到点提醒（时间）",
            summary = if (draft.timeEnabled) {
                "%02d:%02d".format(draft.hour, draft.minute) + " · " + Repeat.label(draft.repeatMask)
            } else {
                "未启用"
            },
            initiallyExpanded = draft.timeEnabled,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用", Modifier.weight(1f))
                Switch(
                    checked = draft.timeEnabled,
                    onCheckedChange = { v -> vm.updateDraft { it.copy(timeEnabled = v) } },
                )
            }

            if (draft.timeEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "%02d:%02d".format(draft.hour, draft.minute),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { vm.updateDraft { it.copy(hour = (it.hour + 23) % 24) } }) { Text("-1 时") }
                    OutlinedButton(onClick = { vm.updateDraft { it.copy(hour = (it.hour + 1) % 24) } }) { Text("+1 时") }
                    OutlinedButton(onClick = { vm.updateDraft { it.copy(minute = (it.minute + 55) % 60) } }) { Text("-5 分") }
                    OutlinedButton(onClick = { vm.updateDraft { it.copy(minute = (it.minute + 5) % 60) } }) { Text("+5 分") }
                }

                Spacer(Modifier.height(12.dp))
                Text("重复：${Repeat.label(draft.repeatMask)}", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetButton("仅一次", draft.repeatMask == Repeat.ONCE) {
                        vm.updateDraft { it.copy(repeatMask = Repeat.ONCE) }
                    }
                    PresetButton("每天", draft.repeatMask == Repeat.EVERY_DAY) {
                        vm.updateDraft { it.copy(repeatMask = Repeat.EVERY_DAY) }
                    }
                    PresetButton("工作日", draft.repeatMask == Repeat.WEEKDAYS) {
                        vm.updateDraft { it.copy(repeatMask = Repeat.WEEKDAYS) }
                    }
                    PresetButton("周末", draft.repeatMask == Repeat.WEEKEND) {
                        vm.updateDraft { it.copy(repeatMask = Repeat.WEEKEND) }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Repeat.DAY_NAMES.forEachIndexed { index, name ->
                        val on = draft.repeatMask and (1 shl index) != 0
                        Box(Modifier.weight(1f)) {
                            PresetButton(name, on) {
                                vm.updateDraft { it.copy(repeatMask = it.repeatMask xor (1 shl index)) }
                            }
                        }
                    }
                }
                Hint("点「一~日」可以自定义星期；全不选等于「仅一次」。")
            }
        }

        // ---------------- ② WiFi ----------------
        CollapsibleSection(
            title = "② 到地点提醒（连上指定 WiFi）",
            summary = if (!draft.wifiEnabled) {
                "未启用"
            } else {
                val names = draft.ssidList
                when {
                    names.isEmpty() -> "已启用 · 还没选网络"
                    names.size <= 2 -> names.joinToString(" / ")
                    else -> "${names.first()} 等 ${names.size} 个"
                }
            },
            initiallyExpanded = draft.wifiEnabled,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用", Modifier.weight(1f))
                Switch(
                    checked = draft.wifiEnabled,
                    onCheckedChange = { v -> vm.updateDraft { it.copy(wifiEnabled = v) } },
                )
            }

            if (draft.wifiEnabled) {
                Spacer(Modifier.height(8.dp))
                Text("当前 WiFi：${currentSsid ?: "未连接／读不到"}", fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { vm.addCurrentSsid() }) { Text("用当前 WiFi") }
                    OutlinedButton(onClick = { vm.refreshCurrentSsid() }) { Text("刷新") }
                }

                Spacer(Modifier.height(10.dp))
                Text("目标 WiFi（可以加多个）：", fontWeight = FontWeight.Medium)
                if (draft.ssidList.isEmpty()) {
                    Hint("还没有目标。一台路由器常有 2.4G/5G 两个名字，建议都加上。")
                } else {
                    draft.ssidList.forEach { ssid ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ssid, Modifier.weight(1f), fontSize = 14.sp)
                            TextButton(onClick = { vm.removeSsid(ssid) }) { Text("删除") }
                        }
                    }
                }

                // 用过的 WiFi：自动记录，点一下就能加入，不用每次手输
                val unused = history.map { it.ssid }.filter { it !in draft.ssidList }
                if (unused.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("用过的网络（点「加入」即可）：", fontWeight = FontWeight.Medium)
                    Hint("哨兵每次连上 WiFi 都会自动记下来，换机/重装前一直有效。")
                    unused.take(8).forEach { ssid ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ssid, Modifier.weight(1f), fontSize = 14.sp)
                            TextButton(onClick = { vm.addSsid(ssid) }) { Text("加入") }
                            TextButton(onClick = { vm.forgetSsid(ssid) }) { Text("移除记录") }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manualSsid,
                        onValueChange = { manualSsid = it },
                        label = { Text("手动添加 SSID") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = {
                        vm.addSsid(manualSsid)
                        manualSsid = ""
                    }) { Text("添加") }
                }

                Spacer(Modifier.height(12.dp))
                Text("连上后确认 ${draft.wifiDwellSeconds} 秒才触发", fontWeight = FontWeight.Medium)
                Hint("WiFi 会抖动、路过也会蹭一下，确认时间太短会误触发。")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 10, 15, 30).forEach { seconds ->
                        PresetButton(
                            if (seconds == 0) "立即" else "${seconds}s",
                            draft.wifiDwellSeconds == seconds,
                        ) { vm.updateDraft { it.copy(wifiDwellSeconds = seconds) } }
                    }
                }
            }
        }

        // ---------------- ③ 地点（自研围栏）----------------
        CollapsibleSection(
            title = "③ 到地点提醒（进入范围）",
            summary = if (!draft.placeEnabled) {
                "未启用"
            } else {
                draft.placeName.ifBlank { "已设地点" } +
                    " · ${draft.radiusMeters}m · 停留 ${draft.placeDwellSeconds}s"
            },
            initiallyExpanded = draft.placeEnabled,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用", Modifier.weight(1f))
                Switch(
                    checked = draft.placeEnabled,
                    onCheckedChange = { v -> vm.updateDraft { it.copy(placeEnabled = v) } },
                )
            }

            if (draft.placeEnabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.placeName,
                    onValueChange = { v -> vm.updateDraft { it.copy(placeName = v) } },
                    label = { Text("地点名称（比如「公司」「超市」）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                val hasPoint = draft.latitude != 0.0 || draft.longitude != 0.0
                Text(
                    text = if (hasPoint) {
                        "已记录坐标：%.5f, %.5f".format(draft.latitude, draft.longitude)
                    } else {
                        "还没记录坐标 —— 请站在要提醒的地方，点下面的按钮"
                    },
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.pickCurrentLocation() },
                        enabled = !locating,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (locating) "定位中…" else "用当前位置") }
                    OutlinedButton(
                        onClick = { vm.saveDraftPlaceAsFavorite() },
                        enabled = hasPoint,
                    ) { Text("存为收藏") }
                }

                // ---- 收藏的地点：点一下就用，长按删除 ----
                if (savedPlaces.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("收藏的地点（点一下就用 · 长按删除）", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        savedPlaces.forEach { place ->
                            val distance = if (myLat != null && myLon != null) {
                                LocationHelper.distanceMeters(
                                    myLat!!, myLon!!, place.latitude, place.longitude,
                                )
                            } else {
                                null
                            }
                            PlaceChip(
                                name = place.name,
                                detail = buildString {
                                    append("${place.radiusMeters}m")
                                    if (distance != null) {
                                        append(" · ")
                                        append(
                                            if (distance >= 1000f) "离你 %.1fkm".format(distance / 1000f)
                                            else "离你 ${distance.toInt()}m"
                                        )
                                    }
                                },
                                onClick = { vm.applySavedPlace(place.id) },
                                onLongClick = { pendingDeletePlace = place },
                            )
                        }
                    }
                }

                Hint(
                    "站在要提醒的地方按一下「用当前位置」最准；常去的地方存成收藏，以后点一下就行。\n" +
                        "精度：室外 10~30 米，室内靠 WiFi/基站约 50~200 米 —— 所以半径别设太小。"
                )

                if (!LocationHelper.hasBackgroundPermission(context)) {
                    Hint(
                        "⚠️ 后台定位现在是「仅在使用时允许」，锁屏后围栏可能不生效；" +
                            "去系统设置改成「始终允许」再测。"
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("触发半径：${draft.radiusMeters} 米", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(200, 300, 500, 1000).forEach { r ->
                        PresetButton("${r}m", draft.radiusMeters == r) {
                            vm.updateDraft { it.copy(radiusMeters = r) }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (draft.placeDwellSeconds == 0) "进入后立即触发"
                    else "进入后停留 ${draft.placeDwellSeconds / 60} 分钟才触发",
                    fontWeight = FontWeight.Medium,
                )
                Hint("防止开车/骑车路过就被判定成「到达」。")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 60, 180, 300).forEach { s ->
                        PresetButton(if (s == 0) "立即" else "${s / 60}分", draft.placeDwellSeconds == s) {
                            vm.updateDraft { it.copy(placeDwellSeconds = s) }
                        }
                    }
                }
            }
        }

        // ---------------- 触发逻辑（或 / 且）----------------
        CollapsibleSection(
            title = "触发逻辑（多个条件之间）",
            summary = MatchMode.label(draft.matchMode),
        ) {
            val triggerCount = listOf(draft.timeEnabled, draft.wifiEnabled, draft.placeEnabled)
                .count { it }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PresetButton("任一满足（或）", draft.matchMode == MatchMode.ANY) {
                    vm.updateDraft { it.copy(matchMode = MatchMode.ANY) }
                }
                PresetButton("同时满足（且）", draft.matchMode == MatchMode.ALL) {
                    vm.updateDraft { it.copy(matchMode = MatchMode.ALL) }
                }
            }
            Hint(
                when {
                    triggerCount <= 1 -> "只启用了一个触发条件，这里选什么都一样。"
                    draft.matchMode == MatchMode.ALL ->
                        "「且」：所有启用的条件同时成立才响。" +
                            "典型用法「到点提醒我，但只有我在公司时才提醒」——" +
                            "到点时不在公司就跳过这次（重复提醒的下一次照常排）。"
                    else -> "「或」：任一条件成立就响（默认）。"
                }
            )
        }

        // ---------------- 冷却（三种触发共用）----------------
        CollapsibleSection(
            title = "冷却（避免反复响）",
            summary = "${cooldownLabel(draft.cooldownMinutes)}内只响一次",
        ) {
            Text("${cooldownLabel(draft.cooldownMinutes)}内只响一次", fontWeight = FontWeight.Medium)
            Hint("家里 WiFi 一天重连十几次很正常，没有冷却会被吵死。测试时可以选「1 分钟」。")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 60, 360, 24 * 60).forEach { minutes ->
                    PresetButton(cooldownLabel(minutes), draft.cooldownMinutes == minutes) {
                        vm.updateDraft { it.copy(cooldownMinutes = minutes) }
                    }
                }
            }
        }

        // ---------------- 响铃 ----------------
        // 铃声名要显示在分区标题上，所以在这里先算出来
        val ringtoneLabel = remember(draft.ringtoneUri) {
            draft.ringtoneUri.takeIf { it.isNotBlank() }?.let { saved ->
                runCatching {
                    RingtoneManager.getRingtone(context, Uri.parse(saved))?.getTitle(context)
                }.getOrNull()
            } ?: "系统默认闹钟铃声"
        }
        CollapsibleSection(
            title = "响铃",
            summary = ringtoneLabel + " · " +
                (if (draft.volumeRampSeconds > 0) "渐强 ${draft.volumeRampSeconds}s" else "不渐强"),
        ) {
            Text("铃声：$ringtoneLabel", fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = {
                    val current = draft.ringtoneUri.takeIf { it.isNotBlank() }
                        ?.let { runCatching { Uri.parse(it) }.getOrNull() }
                    ringtonePicker.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择提醒铃声")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
                        }
                    )
                }) { Text("选择铃声") }
                OutlinedButton(onClick = { vm.setRingtone(null) }) { Text("恢复默认") }
                OutlinedButton(onClick = {
                    // 试听：把草稿当成一条临时提醒，直接拉起响铃页（铃声与渐强都会生效）
                    context.startActivity(
                        Intent(context, RingActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra(AlarmScheduler.EXTRA_LABEL, draft.title.ifBlank { "测试提醒" })
                            putExtra(ReminderScheduler.EXTRA_NOTE, draft.note)
                            putExtra(RingActivity.EXTRA_RINGTONE, draft.ringtoneUri)
                            putExtra(RingActivity.EXTRA_RAMP_SECONDS, draft.volumeRampSeconds)
                        }
                    )
                }) { Text("试听") }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = if (draft.volumeRampSeconds == 0) {
                    "渐强：关（一上来就是最大音量）"
                } else {
                    "渐强：${draft.volumeRampSeconds} 秒内从 15% 升到最大"
                },
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 10, 20, 30).forEach { seconds ->
                    PresetButton(
                        if (seconds == 0) "关" else "${seconds}s",
                        draft.volumeRampSeconds == seconds,
                    ) { vm.updateDraft { it.copy(volumeRampSeconds = seconds) } }
                }
            }
            Hint("渐强只缩放这一路输出，不会改动你手机的闹钟音量。")
        }

        // ---------------- 操作（固定在底部，不用滑到底）----------------
            Spacer(Modifier.height(16.dp))
        }

        Surface(
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (id > 0L) {
                    OutlinedButton(onClick = { confirmDelete = true }) { Text("删除") }
                }
                Button(
                    onClick = {
                        vm.save {
                            // 保存后立刻退回列表，VM 里那条「已保存」消息来不及看见 —— 用 Toast 补上反馈
                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("删除这条提醒？") },
                text = {
                    Text("「${draft.title.ifBlank { "这条提醒" }}」删除后无法恢复。")
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        vm.delete {
                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    }) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text("取消") }
                },
            )
        }

        // 收藏地点的长按删除确认
        pendingDeletePlace?.let { place ->
            AlertDialog(
                onDismissRequest = { pendingDeletePlace = null },
                title = { Text("删除收藏「${place.name}」？") },
                text = { Text("只删这条收藏；已经用它的提醒不受影响（提醒里存的是自己的坐标）。") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deleteSavedPlace(place.id)
                        pendingDeletePlace = null
                    }) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeletePlace = null }) { Text("取消") }
                },
            )
        }
    }
}

/**
 * 收藏地点的小胶囊：点一下填入，长按删除。
 *
 * 用 `combinedClickable` 而不是 `Button` —— 需要长按，而 `Button` 只有单击。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PlaceChip(
    name: String,
    detail: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private fun cooldownLabel(minutes: Int): String = when {
    minutes < 60 -> "${minutes} 分钟"
    minutes == 60 -> "1 小时"
    minutes < 24 * 60 -> "${minutes / 60} 小时"
    else -> "一天"
}
