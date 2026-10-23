package com.ruyue.alarm.trigger

import android.content.Context
import com.ruyue.alarm.alarm.ReminderNotifier
import com.ruyue.alarm.alarm.ReminderScheduler
import com.ruyue.alarm.data.EventType
import com.ruyue.alarm.data.LogRepository
import com.ruyue.alarm.data.MatchMode
import com.ruyue.alarm.data.Reminder
import com.ruyue.alarm.data.ReminderRepository
import com.ruyue.alarm.data.Repeat
import com.ruyue.alarm.util.LocationHelper
import com.ruyue.alarm.util.SimpleLocation
import com.ruyue.alarm.util.WifiReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 触发判定引擎 —— 时间、WiFi、地点三种触发最终都汇到 [fire]。
 *
 * WiFi 与地点各自有两道闸门：
 *  1. **去抖 / 停留**：WiFi 是「连上后还要在线 N 秒」，地点是「进入范围后还要停留 N 秒」——
 *     否则路过家门口蹭一下、开车经过目标点都会误触发。
 *  2. **冷却**：同一提醒默认 24 小时内只触发一次 —— 家里 WiFi 一天重连十几次是常态。
 */
object TriggerEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ---------------- WiFi ----------------

    suspend fun onWifiConnected(
        context: Context,
        ssid: String?,
        reminders: ReminderRepository,
        log: LogRepository,
    ) {
        if (ssid.isNullOrBlank()) return

        // 顺手记进「用过的 WiFi」，以后配提醒直接点选
        reminders.rememberSsid(ssid)

        val now = System.currentTimeMillis()
        val matched = reminders.enabledWifi().filter { reminder ->
            ssid in reminder.ssidList &&
                now - reminder.lastFiredAt >= reminder.cooldownMinutes * 60_000L
        }
        if (matched.isEmpty()) return

        val dwellSeconds = matched.minOf { it.wifiDwellSeconds }
        log.log(
            EventType.NOTE,
            ssid = ssid,
            detail = "WiFi「$ssid」匹配到 ${matched.size} 条提醒，去抖 ${dwellSeconds}s 后再确认",
        )

        if (dwellSeconds > 0) delay(dwellSeconds * 1000L)

        val snap = WifiReader.snapshot(context)
        if (snap.ssid != ssid) {
            log.log(
                EventType.NOTE,
                ssid = ssid,
                detail = "去抖未通过：${dwellSeconds}s 后已不在「$ssid」" +
                    "（当前=${snap.ssid ?: "未连接"}），本次不触发",
            )
            return
        }

        matched.forEach { fire(context, it, "WiFi:$ssid", reminders, log) }
    }

    // ---------------- 地点 ----------------

    /** 出去判定要比进来判定宽 20%：站在半径边缘时定位会抖，不设滞环会反复「进出」 */
    private const val EXIT_HYSTERESIS = 1.2f

    /**
     * 判定「到了」时给半径留 5% 容差。
     * 实测 10-16 那次：半径 300m、复核距离 301m —— **差 1 米被拒**，白等了一轮（2.5 分钟后才响）。
     * 5% 的宽松换来的是「擦边不再可惜」，而 300m 的围栏放到 315m 对到达判定毫无实质影响。
     */
    private const val FIRE_TOLERANCE = 1.05f

    /**
     * 粗定位（网络）的误差补偿：候选区 = 半径 + max(这个值, 半径)。
     * 宁可早一点开始计时（后面还有 GPS 精确复核兜着），也不要因为网络定位偏了而漏掉进入。
     */
    private const val CANDIDATE_EXTRA_METERS = 500f

    /** 系统推来的位置多久之内可以直接用于停留复核（不必再自己发起一次定位请求） */
    private const val FRESH_FIX_REUSE_MS = 90_000L

    /**
     * 复核时认为「够准」的精度阈值（米）。粗于它的点只当参考，**不用来下定论**：
     * 网络定位秒回但误差常有好几百米，拿它判「在不在半径内」会把判定带偏。
     */
    private const val GOOD_ACCURACY_METERS = 150f

    /** 精度不够时的重试上限与间隔 —— 有上限才不会在拿不到好卫星信号的地方一直重试耗电 */
    private const val MAX_ACCURACY_RETRIES = 4
    private const val ACCURACY_RETRY_DELAY_MS = 25_000L

    /** 「且」模式下时间闸门的容差：到点后这么久之内，地点/WiFi 触发仍然算数 */
    private const val TIME_GATE_GRACE_MS = 60 * 60 * 1000L

    /** 允许"早到"的容差：闹钟可能略早于整分触发，不能因此把时间条件判成不满足 */
    private const val TIME_GATE_EARLY_MS = 5_000L

    /** 最近一次系统推来的位置 */
    private var lastFixLat = 0.0
    private var lastFixLon = 0.0
    private var lastFixAccuracy = Float.MAX_VALUE
    private var lastFixAt = 0L

    /** 本次到访因为「精度不够」重试了几次 */
    private val accuracyRetries = mutableMapOf<Long, Int>()

    /** 每条提醒「何时进入范围」 */
    private val insideSince = mutableMapOf<Long, Long>()

    /** 上一次是否在范围内，只用来写「进入/离开」这两条边界日志 */
    private val wasInside = mutableMapOf<Long, Boolean>()

    /** 停留到点后的复核任务 */
    private val dwellJobs = mutableMapOf<Long, Job>()

    /**
     * 这次「到访」是否已经处理过（响过、或被冷却挡掉）。
     *
     * 为什么需要它（2026-10-14 实测）：原来只要人还在范围内，停留计时就会不停重新武装 ——
     * 早上 09:19 到公司时被冷却挡掉，结果**同一趟到访**在 14:31 又响了一次。
     * 加了这个标记之后，一次到访最多提醒一次，只有**真的离开过候选范围再回来**才会重新提醒
     * （否则在公司待一下午、冷却 1 小时，就会每小时响一次）。
     */
    private val visitHandled = mutableSetOf<Long>()

    suspend fun onLocationUpdate(
        context: Context,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        reminders: ReminderRepository,
        log: LogRepository,
    ) {
        val armed = reminders.enabledPlace()
        if (armed.isEmpty()) return

        val now = System.currentTimeMillis()

        // 记住系统刚推给我们的位置：停留到点时如果它足够新，就不必再单独发起一次定位请求
        lastFixLat = latitude
        lastFixLon = longitude
        lastFixAccuracy = accuracyMeters
        lastFixAt = now

        for (reminder in armed) {
            // 没设坐标的忽略（用户在编辑页还没点「用当前位置」）
            if (reminder.latitude == 0.0 && reminder.longitude == 0.0) continue

            val distance = LocationHelper.distanceMeters(
                latitude, longitude, reminder.latitude, reminder.longitude,
            )

            // 连续监听只挂网络定位，误差可能有几百米 —— 所以先用一个"候选区"决定要不要
            // 开始停留计时，真正的「到了没有」交给停留到点后的 GPS 精确复核
            // （见 scheduleDwellCheck）。宁可早开始计时，也不能因为粗定位偏了而漏掉进入。
            val candidateLimit = reminder.radiusMeters.toFloat() +
                maxOf(CANDIDATE_EXTRA_METERS, reminder.radiusMeters.toFloat())
            val wasIn = wasInside[reminder.id] == true
            val limit = if (wasIn) candidateLimit * EXIT_HYSTERESIS else candidateLimit
            val inside = distance <= limit

            // 只在「进入/离开」时写日志：定位每 60 秒来一次，全记会把日志表刷爆。
            // 另外**首次观察不发日志** —— `wasInside` 是内存状态，服务重启后为空，
            // 那时第一次看到「在外面」会凭空多出一条「离开」记录（实测 10-18 11:42 与
            // 10-19 07:47 各一条，读起来像"半夜离开公司 17 公里"这种假事件，会把排查带偏）。
            val previous = wasInside[reminder.id]
            if (previous != inside) {
                wasInside[reminder.id] = inside
                if (previous != null) {
                    log.log(
                        EventType.NOTE,
                        detail = if (inside) {
                            "接近「${reminder.placeName.ifBlank { "地点" }}」（粗定位距离 ${distance.toInt()}m，" +
                                "半径 ${reminder.radiusMeters}m），开始停留计时；到点用 GPS 精确确认"
                        } else {
                            "离开「${reminder.placeName.ifBlank { "地点" }}」候选范围（距离 ${distance.toInt()}m）"
                        },
                    )
                }
            }

            if (!inside) {
                insideSince.remove(reminder.id)
                dwellJobs.remove(reminder.id)?.cancel()
                // 真的离开了，这次到访结束 —— 下次再进来可以重新提醒
                visitHandled.remove(reminder.id)
                continue
            }

            // 「且」模式下，如果**时间条件此刻明确不成立**（还没到点、或已过去超过容差），
            // 这次地点触发注定会被 fire() 拦下 —— 连停留计时和 GPS 复核都不必起，
            // 直接跳过，省掉一整套定位请求。
            // 实测浪费：10-23 08:35:22 到公司（09:00 的提醒），白跑两轮 GPS 到 08:38:41 才判出
            // 「缺少 时间（还没到 09:00）」。到点那一刻时间触发会自己再检查地点，不会被漏掉。
            if (reminder.matchMode == MatchMode.ALL &&
                reminder.timeEnabled &&
                timeGateUnmet(reminder) != null
            ) {
                insideSince.remove(reminder.id)
                dwellJobs.remove(reminder.id)?.cancel()
                continue
            }

            val since = insideSince[reminder.id]
            if (since == null) {
                // 本次到访已经处理过（响过或被冷却挡过）就不再武装，避免一趟到访反复提醒
                if (!visitHandled.contains(reminder.id)) {
                    insideSince[reminder.id] = now
                    accuracyRetries[reminder.id] = 0
                    scheduleDwellCheck(context, reminder.id, reminder.placeDwellSeconds, reminders, log)
                }
            }
            // ⚠️ 这里**不要**再自己判一次"到点了没"。
            // 停留复核任务（scheduleDwellCheck）已经在 since + dwell 时主动取位置复核并触发；
            // 若这里再加一条"回调来了就判"，两条路径会在同一秒各自通过 —— 它们读到的都是
            // 同一份旧的 `lastFiredAt`，冷却双双放行。
            // 实测 2026-10-20 09:38:55 就是这么把同一条提醒**响了两遍**（两行 ALARM_FIRED 同秒）。
        }
    }

    /**
     * 停留到点后**主动取一次新位置复核**。
     *
     * 为什么必须这样：注册定位更新用的是 `minDistance = 100m`，**人站着不动时系统根本不给回调**，
     * 只靠「等下一次回调」来判停留会一直等不到。M2 实测就是这样：09:41:14 进入范围，
     * 设的是 1 分钟停留，却到 09:47:46（6.5 分钟后，有人走动产生回调）才触发。
     */
    private fun scheduleDwellCheck(
        context: Context,
        reminderId: Long,
        dwellSeconds: Int,
        reminders: ReminderRepository,
        log: LogRepository,
    ) {
        dwellJobs.remove(reminderId)?.cancel()
        dwellJobs[reminderId] = scope.launch {
            delay(dwellSeconds * 1000L + 2_000L)
            // 如果这条提醒的停留任务已经被「离开范围」或新一轮进入替换掉，我这个任务就是过期的
            if (dwellJobs[reminderId] !== coroutineContext[Job]) return@launch
            verifyAndMaybeFire(context, reminderId, reminders, log)
        }
    }

    /**
     * 精度不够时的快速重试（**不重置停留计时**）。
     *
     * 为什么需要（2026-10-15 实测）：08:25:10 就检测到「接近」，却到 08:31:15 才响 ——
     * 中间两次复核拿到的都是粗定位点（793m / 376m，网络定位误差几百米），
     * 判成「在外面」后把停留计时**重置**了，只能干等下一次定位回调（网络定位好几分钟才来一次）。
     * 现在改成：精度不够就先不下结论，25 秒后再确认一次 —— 通常等 GPS 锁定就够了。
     */
    private fun scheduleAccuracyRecheck(
        context: Context,
        reminderId: Long,
        reminders: ReminderRepository,
        log: LogRepository,
    ) {
        dwellJobs.remove(reminderId)?.cancel()
        dwellJobs[reminderId] = scope.launch {
            delay(ACCURACY_RETRY_DELAY_MS)
            if (dwellJobs[reminderId] !== coroutineContext[Job]) return@launch
            verifyAndMaybeFire(context, reminderId, reminders, log)
        }
    }

    private suspend fun verifyAndMaybeFire(
        context: Context,
        reminderId: Long,
        reminders: ReminderRepository,
        log: LogRepository,
    ) {
        val reminder = reminders.byId(reminderId)
        if (reminder == null || !reminder.enabled || !reminder.placeEnabled) {
            insideSince.remove(reminderId)
            return
        }

        // 复核用哪个位置？优先「系统刚推过来的那一个」（≤90 秒）：本来就是真实定位结果，
        // 既省一次定位请求，也比单独发起请求更容易拿到点；没有新鲜的才去请求一次新定位，
        // 再不行回退到 3 分钟内的缓存。
        val located: SimpleLocation? =
            if (System.currentTimeMillis() - lastFixAt <= FRESH_FIX_REUSE_MS) {
                SimpleLocation(lastFixLat, lastFixLon, lastFixAccuracy, "listener")
            } else {
                runCatching {
                    LocationHelper.current(
                        context = context,
                        timeoutMs = 20_000L,
                        cacheFirst = false,
                        maxCacheAgeMs = 3 * 60_000L,
                    )
                }.getOrNull()
            }

        if (located == null) {
            insideSince.remove(reminderId)
            accuracyRetries.remove(reminderId)
            log.log(
                EventType.NOTE,
                detail = "停留到点但取不到位置（新定位超时、缓存也过期），本次不触发",
            )
            return
        }

        val distance = LocationHelper.distanceMeters(
            located.latitude, located.longitude, reminder.latitude, reminder.longitude,
        )
        val accurate = located.accuracyMeters <= GOOD_ACCURACY_METERS

        when {
            // 精度够或已在半径内 → 按结论走
            distance <= reminder.radiusMeters * FIRE_TOLERANCE -> {
                fireIfCooled(context, reminder, distance, reminders, log)
            }

            // 位置太粗 + 判「在外面」：很可能只是粗定位偏了，别急着放弃，也别重置停留
            !accurate && (accuracyRetries[reminderId] ?: 0) < MAX_ACCURACY_RETRIES -> {
                val attempt = (accuracyRetries[reminderId] ?: 0) + 1
                accuracyRetries[reminderId] = attempt
                log.log(
                    EventType.NOTE,
                    detail = "定位精度只有 ${located.accuracyMeters.toInt()}m（粗判距离 ${distance.toInt()}m），" +
                        "${ACCURACY_RETRY_DELAY_MS / 1000} 秒后再确认（第 $attempt 次）",
                )
                scheduleAccuracyRecheck(context, reminderId, reminders, log)
            }

            else -> {
                insideSince.remove(reminderId)
                accuracyRetries.remove(reminderId)
                log.log(
                    EventType.NOTE,
                    detail = "停留复核未通过（距离 ${distance.toInt()}m，精度 " +
                        "${located.accuracyMeters.toInt()}m），本次不触发",
                )
            }
        }
    }

    /** 冷却检查 + 触发。冷却没过就静默跳过（不写日志，避免刷屏）。 */
    private suspend fun fireIfCooled(
        context: Context,
        reminder: Reminder,
        distance: Float,
        reminders: ReminderRepository,
        log: LogRepository,
    ) {
        insideSince.remove(reminder.id)
        // 只从表里摘掉、**不能 cancel**：本函数有可能就是在这个 job 里被调用的，
        // 取消自己会让后面的 fire() 在挂起点抛 CancellationException，提醒就静默丢了。
        dwellJobs.remove(reminder.id)
        // 不管最后响没响（冷却没过也算），这次到访都算处理过了
        visitHandled.add(reminder.id)

        // ⚠️ 必须**重新读一次库**再判冷却：传进来的 `reminder` 可能是几秒前的快照，
        // 而 markFired 才刚把它改掉。不重读的话，"读旧值→双双通过"的竞态就还在。
        val fresh = reminders.byId(reminder.id) ?: return
        if (System.currentTimeMillis() - fresh.lastFiredAt < fresh.cooldownMinutes * 60_000L) return
        fire(
            context,
            fresh,
            "地点:${fresh.placeName.ifBlank { "已设地点" }}(${distance.toInt()}m)",
            reminders,
            log,
        )
    }

    /** 提醒被删/停用时清掉内存状态，避免 id 复用后误判 */
    fun forget(reminderId: Long) {
        insideSince.remove(reminderId)
        wasInside.remove(reminderId)
        visitHandled.remove(reminderId)
        accuracyRetries.remove(reminderId)
        dwellJobs.remove(reminderId)?.cancel()
    }

    // ---------------- 统一触发 ----------------

    /** 时间、WiFi、地点三种触发都汇到这里，保证响铃体验完全一致 */
    suspend fun fire(
        context: Context,
        reminder: Reminder,
        source: String,
        reminders: ReminderRepository,
        log: LogRepository,
    ) {
        // 「且」模式：本次触发只是把条件凑齐了一半，还得看其他启用的条件此刻是否也成立
        if (reminder.matchMode == MatchMode.ALL) {
            val unmet = unmetConditions(context, reminder)
            if (unmet.isNotEmpty()) {
                // 注意：这里**不** markFired（冷却不消耗）。调用方已经把「这趟到访」标记成处理过，
                // 所以不会每来一次定位就刷一条日志；等条件补齐时由**另一个触发**接手 ——
                // 例如「到了公司 且 连上公司 WiFi」：先到公司时缺 WiFi，WiFi 一连上就由 WiFi 触发通过。
                log.log(
                    EventType.NOTE,
                    detail = "「${reminder.title}」已满足 $source，但缺少 ${unmet.joinToString("；")}，本次不触发",
                )
                return
            }
        }

        reminders.markFired(reminder.id)
        log.log(
            EventType.ALARM_FIRED,
            detail = "触发提醒「${reminder.title}」（来源：$source）",
        )
        // 「仅一次」的时间提醒响过就自动关闭
        if (reminder.timeEnabled && reminder.repeatMask == Repeat.ONCE) {
            reminders.disable(reminder.id)
        }
        ReminderNotifier.show(
            context,
            reminder.id,
            reminder.title,
            reminder.note,
            reminder.ringtoneUri,
            reminder.volumeRampSeconds,
        )
    }

    // ---------------- 时间触发（统一入口）----------------

    /**
     * 时间触发的统一入口。
     *
     * 以前这段逻辑写在 `AlarmReceiver` 里，等于**绕过了本引擎** —— 既破坏了「触发只有一条入口」
     * 这条约定，也让「且」模式对时间触发失效（v0.5.0 收回来）。
     * 这里用 [Repos] 手工拼仓库，因为它是在 BroadcastReceiver 里被调用的。
     */
    suspend fun onTimeAlarm(
        context: Context,
        reminderId: Long,
        fallbackTitle: String,
        fallbackNote: String,
    ) {
        val reminders = Repos.reminders(context)
        val log = Repos.logs(context)
        val reminder = reminders.byId(reminderId)
        val title = reminder?.title?.ifBlank { fallbackTitle } ?: fallbackTitle
        val note = reminder?.note ?: fallbackNote

        if (reminder != null && reminder.matchMode == MatchMode.ALL) {
            val unmet = unmetConditions(context, reminder)
            if (unmet.isNotEmpty()) {
                log.log(
                    EventType.NOTE,
                    detail = "到点了，但缺少 ${unmet.joinToString("；")}，本次跳过「$title」",
                )
                if (reminder.repeatMask == Repeat.ONCE) {
                    reminders.disable(reminderId)
                    log.log(EventType.NOTE, detail = "「$title」是仅一次，条件没齐，已自动关闭")
                } else {
                    ReminderScheduler.schedule(context, reminder)
                    log.log(EventType.NOTE, detail = "「$title」的下一次已照常排定")
                }
                return
            }
        }

        if (reminder != null) {
            reminders.markFired(reminderId)
            if (reminder.repeatMask == Repeat.ONCE) {
                reminders.disable(reminderId)
            } else {
                // 闹钟是一次性的：重复提醒必须马上把下一次排上
                ReminderScheduler.schedule(context, reminder)
            }
        }

        // 先落证据再弹 UI：这样「没响」和「响了没弹出来」能分得清
        log.log(EventType.ALARM_FIRED, detail = "时间触发：$title")
        ReminderNotifier.show(
            context,
            reminderId,
            title,
            note,
            reminder?.ringtoneUri.orEmpty(),
            reminder?.volumeRampSeconds ?: 0,
        )
    }

    // ---------------- 「且」模式的实时条件检查 ----------------

    /**
     * 检查「且」模式下其他启用的条件是否成立。
     * 触发本身已经成立，但重查一遍自身也无害（WiFi/地点都是一次廉价读取），逻辑反而更简单。
     */
    private suspend fun unmetConditions(context: Context, reminder: Reminder): List<String> {
        val unmet = mutableListOf<String>()

        // 时间条件是「闸门」，不是「触发器」。
        // ⚠️ v0.5.4 及之前这里**完全漏了时间**：所以「到公司 且 14:00」在早上到公司时就响了
        // （用户 2026-10-20 实测反馈）。必须早于时刻、或超出容差时判为不满足。
        if (reminder.timeEnabled) {
            timeGateUnmet(reminder)?.let { unmet += it }
        }

        if (reminder.wifiEnabled) {
            val ssid = WifiReader.snapshot(context).ssid
            if (ssid == null || ssid !in reminder.ssidList) {
                val target = reminder.ssidList.joinToString(" / ").ifBlank { "目标网络" }
                unmet += "WiFi（需在 $target 上，当前 ${ssid ?: "未连接"}）"
            }
        }

        if (reminder.placeEnabled && !isInsidePlace(context, reminder)) {
            unmet += "地点（需在「${reminder.placeName.ifBlank { "已设地点" }}」${reminder.radiusMeters}m 内）"
        }

        return unmet
    }

    /**
     * 「且」模式下**时间条件**是否成立。
     *
     * 语义按「到那一刻必须同时满足」理解：
     *  · **还没到点** → 不成立（例：设 14:00，早上 09:38 到公司 → 不响，只记日志）
     *  · 已到点且未超过 [TIME_GATE_GRACE_MS] → 成立（14:10 才进公司，仍然提醒你）
     *  · 超过容差 → 当作今天的这次错过了（免得晚上 8 点进公司还被提醒下午两点的事）
     *  · 今天不在重复日内 → 不成立（"工作日"的提醒不会因为周六到了公司就响）
     */
    private fun timeGateUnmet(reminder: Reminder): String? {
        val label = hhmm(reminder)
        if (!maskMatchesToday(reminder.repeatMask)) {
            return "时间（今天不是重复日：${Repeat.label(reminder.repeatMask)}）"
        }
        val now = System.currentTimeMillis()
        val target = todayTargetMillis(reminder)
        // 允许早几秒：闹钟可能略早于整分触发，不能让它把自己挡掉
        if (now < target - TIME_GATE_EARLY_MS) return "时间（还没到 $label）"
        if (now > target + TIME_GATE_GRACE_MS) {
            return "时间（$label 已过去超过 ${TIME_GATE_GRACE_MS / 60_000} 分钟）"
        }
        return null
    }

    private fun hhmm(reminder: Reminder): String = "%02d:%02d".format(reminder.hour, reminder.minute)

    private fun todayTargetMillis(reminder: Reminder): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, reminder.hour)
        set(Calendar.MINUTE, reminder.minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** 重复规则是否包含今天（`Repeat.ONCE` 不做星期限制） */
    private fun maskMatchesToday(mask: Int): Boolean {
        if (mask == Repeat.ONCE) return true
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) // 1 = 周日
        val index = (dayOfWeek + 5) % 7 // 0 = 周一，与 Repeat.DAY_NAMES 的顺序一致
        return mask and (1 shl index) != 0
    }

    /**
     * 只用于「且」模式的实时判断：优先吃缓存，拿不到再短请求一次。
     * 预算刻意压到 5 秒 —— 时间触发是在 BroadcastReceiver 里跑的，`goAsync` 只有约 10 秒。
     */
    private suspend fun isInsidePlace(context: Context, reminder: Reminder): Boolean {
        if (reminder.latitude == 0.0 && reminder.longitude == 0.0) return false
        val fix = runCatching {
            LocationHelper.current(
                context = context,
                timeoutMs = 5_000L,
                cacheFirst = true,
                maxCacheAgeMs = 5 * 60_000L,
            )
        }.getOrNull() ?: return false
        val distance = LocationHelper.distanceMeters(
            fix.latitude, fix.longitude, reminder.latitude, reminder.longitude,
        )
        return distance <= reminder.radiusMeters * FIRE_TOLERANCE
    }
}
