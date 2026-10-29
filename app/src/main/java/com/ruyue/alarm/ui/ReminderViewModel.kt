package com.ruyue.alarm.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruyue.alarm.alarm.ReminderScheduler
import com.ruyue.alarm.data.Reminder
import com.ruyue.alarm.data.ReminderRepository
import com.ruyue.alarm.data.SavedPlace
import com.ruyue.alarm.data.SavedPlaceRepository
import com.ruyue.alarm.data.SsidHistory
import com.ruyue.alarm.sentinel.SentinelController
import com.ruyue.alarm.sentinel.SentinelService
import com.ruyue.alarm.trigger.TriggerEngine
import com.ruyue.alarm.util.LocationHelper
import com.ruyue.alarm.util.WifiReader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val repo: ReminderRepository,
    private val placeRepo: SavedPlaceRepository,
    @ApplicationContext private val app: Context,
) : ViewModel() {

    val reminders: StateFlow<List<Reminder>> = repo.reminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 用过的 WiFi，给编辑页「点一下加入」 */
    val ssidHistory: StateFlow<List<SsidHistory>> = repo.ssidHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 收藏的地点，给编辑页「点一下就用」 */
    val savedPlaces: StateFlow<List<SavedPlace>> = placeRepo.places
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 编辑草稿；id == 0 表示新建 */
    private val _draft = MutableStateFlow(Reminder())
    val draft: StateFlow<Reminder> = _draft.asStateFlow()

    private val _currentSsid = MutableStateFlow<String?>(null)
    val currentSsid: StateFlow<String?> = _currentSsid.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

    fun startEdit(id: Long) {
        viewModelScope.launch {
            _draft.value = if (id > 0L) repo.byId(id) ?: Reminder() else Reminder()
            refreshCurrentSsid()
        }
    }

    fun refreshCurrentSsid() {
        _currentSsid.value = WifiReader.snapshot(app).ssid
    }

    fun updateDraft(transform: (Reminder) -> Reminder) {
        _draft.value = transform(_draft.value)
    }

    // ---------------- WiFi ----------------

    fun addCurrentSsid() {
        val ssid = _currentSsid.value
        if (ssid.isNullOrBlank()) {
            _message.value = "当前没连 WiFi（或读不到 SSID），请先连上目标 WiFi"
            return
        }
        addSsid(ssid)
    }

    fun addSsid(raw: String) {
        val ssid = raw.trim()
        if (ssid.isEmpty()) return
        updateDraft { r ->
            if (ssid in r.ssidList) r else r.copy(targetSsids = (r.ssidList + ssid).joinToString("\n"))
        }
    }

    fun removeSsid(ssid: String) {
        updateDraft { r -> r.copy(targetSsids = r.ssidList.filterNot { it == ssid }.joinToString("\n")) }
    }

    /** 从「用过的 WiFi」里删掉一条记录 */
    fun forgetSsid(ssid: String) {
        viewModelScope.launch { repo.forgetSsid(ssid) }
    }

    // ---------------- 响铃 ----------------

    /** 设置铃声；传 null = 恢复系统默认闹钟铃声 */
    fun setRingtone(uri: String?) {
        updateDraft { it.copy(ringtoneUri = uri.orEmpty()) }
    }

    // ---------------- 地点 ----------------

    /**
     * 把收藏的地点填进草稿。
     *
     * ⚠️ 是**快照**：填完之后提醒和这条收藏就没有关系了 ——
     * 以后改动/删除收藏，不会悄悄改变这条提醒的触发范围（反过来也一样）。
     */
    fun applySavedPlace(id: Long) {
        viewModelScope.launch {
            val place = placeRepo.byId(id) ?: return@launch
            updateDraft {
                it.copy(
                    placeEnabled = true,
                    placeName = place.name,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    radiusMeters = place.radiusMeters,
                )
            }
            placeRepo.touch(id)
            _message.value = "已填入「${place.name}」（${place.radiusMeters} 米）"
        }
    }

    /** 把草稿里当前的地点存为收藏（同名覆盖，不堆重复项） */
    fun saveDraftPlaceAsFavorite() {
        val d = _draft.value
        if (d.latitude == 0.0 && d.longitude == 0.0) {
            _message.value = "还没有坐标：先点「用当前位置」记录一下，再存为收藏"
            return
        }
        val name = d.placeName.trim()
        if (name.isBlank()) {
            _message.value = "先给这个地点起个名字（比如「公司」），再存为收藏"
            return
        }
        viewModelScope.launch {
            placeRepo.save(name, d.latitude, d.longitude, d.radiusMeters)
            _message.value = "已收藏「$name」，下次点一下就能用"
        }
    }

    fun deleteSavedPlace(id: Long) {
        viewModelScope.launch { placeRepo.delete(id) }
    }

    /**
     * 「按地址找」选中的地点写进草稿。
     *
     * 和收藏一样是**快照**：之后就与那次在线搜索无关了，不依赖网络也能正常触发。
     */
    fun setPlaceFromAddress(name: String, latitude: Double, longitude: Double, radiusMeters: Int) {
        updateDraft {
            it.copy(
                placeEnabled = true,
                placeName = name,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters,
            )
        }
        _message.value = "已填入「$name」，记得点保存"
    }

    /**
     * 记录当前位置作为目标地点。
     * 刻意不做地图选点：自用场景「站在要提醒的地方按一下」最省事，也省掉一整个地图 SDK 和 key。
     */
    fun pickCurrentLocation() {
        if (!LocationHelper.hasFinePermission(app)) {
            _message.value = "缺少精确定位权限，无法取当前位置"
            return
        }
        if (_locating.value) return
        _locating.value = true
        viewModelScope.launch {
            val loc = runCatching { LocationHelper.current(app) }.getOrNull()
            _locating.value = false
            if (loc == null) {
                _message.value = "定位失败：确认系统「定位」开关已打开，并在窗边或室外再试"
                return@launch
            }
            updateDraft {
                it.copy(
                    placeEnabled = true,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    placeName = it.placeName.ifBlank { "已设地点" },
                )
            }
            _message.value = "已记录当前位置（精度约 ${loc.accuracyMeters.toInt()} 米）"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    // ---------------- 保存 / 删除 ----------------

    fun save(onDone: () -> Unit) {
        val current = _draft.value
        when {
            current.title.isBlank() -> {
                _message.value = "请先填提醒标题"
                return
            }
            !current.hasAnyTrigger() -> {
                _message.value = "至少要启用一种触发方式（时间 / WiFi / 地点）"
                return
            }
            current.wifiEnabled && current.ssidList.isEmpty() -> {
                _message.value = "启用了 WiFi 触发，但还没添加任何 SSID"
                return
            }
            current.placeEnabled && current.latitude == 0.0 && current.longitude == 0.0 -> {
                _message.value = "启用了地点触发，请先点「用当前位置」记录坐标"
                return
            }
        }

        viewModelScope.launch {
            val id = repo.save(current)
            val saved = current.copy(id = id)

            // 时间触发：先撤旧的再排新的，避免改时间后残留旧闹钟
            ReminderScheduler.cancel(app, id)
            if (saved.enabled && saved.timeEnabled) ReminderScheduler.schedule(app, saved)

            // WiFi / 地点触发都依赖哨兵在跑 —— 配了就该顺手拉起来，否则配了也不会触发
            val needSentinel = saved.enabled && (saved.wifiEnabled || saved.placeEnabled)
            if (needSentinel && !SentinelService.isWanted(app)) {
                SentinelController.start(app)
                _message.value = "已保存，并自动开启了地点监听"
            } else {
                // 配置变了（例如新加了地点提醒）→ 让服务重新评估要不要开始定位
                SentinelController.refresh(app)
                _message.value = "已保存"
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val current = _draft.value
        viewModelScope.launch {
            if (current.id > 0L) {
                ReminderScheduler.cancel(app, current.id)
                TriggerEngine.forget(current.id)
                repo.delete(current)
                SentinelController.refresh(app)
                _message.value = "已删除"
            }
            onDone()
        }
    }

    fun toggleEnabled(reminder: Reminder) {
        viewModelScope.launch {
            val next = !reminder.enabled
            repo.setEnabled(reminder.id, next)
            ReminderScheduler.cancel(app, reminder.id)
            if (next && reminder.timeEnabled) {
                ReminderScheduler.schedule(app, reminder.copy(enabled = true))
            }
            if (!next) TriggerEngine.forget(reminder.id)
            SentinelController.refresh(app)
        }
    }
}
