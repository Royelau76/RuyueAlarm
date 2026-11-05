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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import com.ruyue.alarm.ui.edit.EditCard
import com.ruyue.alarm.ui.edit.EditSectionLabel
import com.ruyue.alarm.ui.edit.HeroInputCard
import com.ruyue.alarm.ui.theme.LocalRuyueColors

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
fun ReminderEditScreen(vm: ReminderViewModel, id: Long, onBack: () -> Unit) {
    LaunchedEffect(id) { vm.startEdit(id) }

    val context = LocalContext.current
    val ruyue = LocalRuyueColors.current
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

    // 「按地址找」用全屏 Dialog（不换路由：换路由会触发 startEdit 重新读库，把未保存的改动冲掉）
    var showAddressSearch by remember { mutableStateOf(false) }

    // 日历选点：只对「仅一次」有意义（指定那一天，而不是"下一个 hh:mm"）
    var showDatePicker by remember { mutableStateOf(false) }

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

    // 提示消息改成**自动消失的 Snackbar**（v0.16.2）：
    // 原来是页面顶部一张卡片 + 「知道了」按钮，问题是 —— 消息存在 ViewModel 里，
    // 用户随手返回时没人点「知道了」，它就留在状态里，下次进编辑/新建页又冒出来
    // （"已保存 知道了" / "已删除 知道了" 就是这么来的）。
    // 现在：显示前先清空状态（即使页面当场被销毁也不会残留）+ 4 秒自动消失 + 离开页面再兜底清一次。
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        vm.consumeMessage()
        snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short)
    }
    DisposableEffect(Unit) {
        onDispose { vm.consumeMessage() }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        // ---- 固定顶部：返回 + 标题 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("取消", style = MaterialTheme.typography.labelLarge, color = ruyue.textSecondary)
            }
            Spacer(Modifier.width(2.dp))
            Text(
                text = if (id > 0L) "编辑约定" else "新建约定",
                style = MaterialTheme.typography.titleLarge,
                color = ruyue.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "保存",
                style = MaterialTheme.typography.labelLarge,
                color = ruyue.time,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(ruyue.time.copy(alpha = 0.14f))
                    .clickable {
                        vm.save {
                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        // ---- 可滚动内容 ----
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {

        // ---------------- 顶部输入卡（设计稿第一屏最显眼那块） ----------------
        HeroInputCard(
            title = draft.title,
            note = draft.note,
            onTitleChange = { v -> vm.updateDraft { it.copy(title = v) } },
            onNoteChange = { v -> vm.updateDraft { it.copy(note = v) } },
        )

        // ---------------- 什么时候 · 在哪儿 · 什么网 ----------------
        EditSectionLabel("什么时候 · 在哪儿 · 什么网")

        // ---------------- ① 时间 ----------------
        EditCard(
            title = "时间",
            summary = if (draft.timeEnabled) {
                draft.timeLabel()
            } else {
                "未启用"
            },
            initiallyExpanded = draft.timeEnabled,
            accent = ruyue.time,
            switchOn = draft.timeEnabled,
            onSwitch = { v -> vm.updateDraft { it.copy(timeEnabled = v) } },
            bigSummary = true,
        ) {

            if (draft.timeEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "%02d:%02d".format(draft.hour, draft.minute),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                // 四个按钮统一成小胶囊（原来 OutlinedButton 混在 pills 里，第 4 个还会被挤变形）
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetButton("-1 时", false) { vm.updateDraft { it.copy(hour = (it.hour + 23) % 24) } }
                    PresetButton("+1 时", false) { vm.updateDraft { it.copy(hour = (it.hour + 1) % 24) } }
                    PresetButton("-5 分", false) { vm.updateDraft { it.copy(minute = (it.minute + 55) % 60) } }
                    PresetButton("+5 分", false) { vm.updateDraft { it.copy(minute = (it.minute + 5) % 60) } }
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

                // 日历选点：只在「仅一次」时出现 —— 选了日期就是"只在那一天响"
                if (draft.repeatMask == Repeat.ONCE) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("指定日期", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                text = if (draft.onceDate > 0L) {
                                    java.text.SimpleDateFormat(
                                        "yyyy年M月d日",
                                        java.util.Locale.getDefault(),
                                    ).format(java.util.Date(draft.onceDate)) +
                                        " " + "%02d:%02d".format(draft.hour, draft.minute)
                                } else {
                                    "未指定 → 按「下一个 %02d:%02d」算（今天或明天）".format(draft.hour, draft.minute)
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { showDatePicker = true }) {
                            Text(if (draft.onceDate > 0L) "改日期" else "选日期")
                        }
                        if (draft.onceDate > 0L) {
                            TextButton(onClick = { vm.updateDraft { it.copy(onceDate = 0L) } }) {
                                Text("清除")
                            }
                        }
                    }
                }
            }
        }

        // ---------------- ② WiFi ----------------
        EditCard(
            title = "连上 Wi-Fi",
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
            accent = ruyue.wifi,
            switchOn = draft.wifiEnabled,
            onSwitch = { v -> vm.updateDraft { it.copy(wifiEnabled = v) } },
            asSheet = true,
            openLabel = "选网络 / 确认时长",
        ) {

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
        EditCard(
            title = "到某个地点",
            summary = if (!draft.placeEnabled) {
                "未启用"
            } else {
                draft.placeName.ifBlank { "已设地点" } +
                    " · ${draft.radiusMeters}m · 停留 ${draft.placeDwellSeconds}s"
            },
            initiallyExpanded = draft.placeEnabled,
            accent = ruyue.place,
            switchOn = draft.placeEnabled,
            onSwitch = { v -> vm.updateDraft { it.copy(placeEnabled = v) } },
            asSheet = true,
            openLabel = "改范围 / 停留时长",
        ) {

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
                        onClick = { showAddressSearch = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("按地址找") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
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
        EditCard(
            title = "条件之间",
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
        EditCard(
            title = "冷却",
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
        EditCard(
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

        // 保存已挪到顶栏（设计稿），底栏只留删除 —— 危险操作离手指远一点
        if (id > 0L) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "删除这条约定",
                    style = MaterialTheme.typography.labelLarge,
                    color = ruyue.danger,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(ruyue.danger.copy(alpha = 0.10f))
                        .clickable { confirmDelete = true }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
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

        // 日历选点
        if (showDatePicker) {
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = draft.onceDate
                    .takeIf { it > 0L }
                    ?.let { toUtcMidnight(it) },
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { utc ->
                            vm.updateDraft { it.copy(onceDate = toLocalMidnight(utc)) }
                        }
                        showDatePicker = false
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                },
            ) {
                DatePicker(state = pickerState)
            }
        }

        // 「按地址找」全屏弹窗
        if (showAddressSearch) {
            AddressSearchDialog(
                onDismiss = { showAddressSearch = false },
                onPick = { name, lat, lon, radius ->
                    vm.setPlaceFromAddress(name, lat, lon, radius)
                    showAddressSearch = false
                },
            )
        }
    }

        // Snackbar 浮在底栏上方（自动消失，不需要点"知道了"）
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 76.dp),
        )
    }
}

/**
 * Material3 的 `DatePicker` 用「所选日期的 **UTC 零点**」表示日期，而库里存的是**本地零点**。
 *
 * 不换算就会偏一天（东八区会偏到前一天 20:00）—— 这是 DatePicker 最常见的坑。
 * 用 `java.time` 而不是 `Calendar`：minSdk 26 直接可用，且不受时区偏移的四舍五入影响。
 */
private fun toLocalMidnight(utcMillis: Long): Long =
    java.time.Instant.ofEpochMilli(utcMillis)
        .atZone(java.time.ZoneOffset.UTC)
        .toLocalDate()
        .atStartOfDay(java.time.ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

/** 反向：本地零点 → DatePicker 期望的 UTC 零点（否则打开选择器时选中的日期会差一天） */
private fun toUtcMidnight(localMillis: Long): Long =
    java.time.Instant.ofEpochMilli(localMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(java.time.ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

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
