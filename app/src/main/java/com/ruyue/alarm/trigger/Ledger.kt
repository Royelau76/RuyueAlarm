package com.ruyue.alarm.trigger

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ruyue.alarm.MainActivity
import com.ruyue.alarm.R
import com.ruyue.alarm.RuyueApp
import com.ruyue.alarm.data.Db
import com.ruyue.alarm.data.EventType
import com.ruyue.alarm.data.ExpectedFire
import com.ruyue.alarm.data.ExpectedState
import com.ruyue.alarm.data.ReminderDb
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 触发对账（自检）。
 *
 * ## 解决什么问题
 *
 * 「提醒到底会不会响」是这个产品唯一不能出错的事，但原来只能靠人事后翻日志发现。
 * 这里把它变成**可对账**的：
 *
 * 1. **记账**：排期时写一条 `EXPECTED`（幂等，见 `ExpectedFire` 的唯一索引）
 * 2. **销账**：真响了 → `FIRED`；被「且」闸门故意拦下 → `SKIPPED`；提醒被删 → `CANCELLED`
 * 3. **对账**：过了宽限期还没销账的 → `MISSED`，并**主动推一条通知**
 * 4. **归因**：把同期日志里的「服务被停 / 设备重启」翻出来，直接写进通知
 *
 * ## 为什么"跳过"和"没响"必须分开
 *
 * 「到公司且 9:00」的提醒，9:00 时不在公司 → 系统**故意**不响。这不能报成故障，
 * 否则报表天天假警，三天之后用户就再也不看了 —— 那这套机制等于白做。
 */
object Ledger {

    /** 过了这么久还没销账才判漏（时间闸门本身允许迟到 1 小时，所以留 2 小时） */
    private const val GRACE_MS = 2 * 60 * 60 * 1000L

    /** 销账时允许的时钟误差：闹钟可能略早/略晚于整分 */
    private const val MATCH_WINDOW_MS = 2 * 60 * 1000L

    /** 台账保留 30 天（比日志库的 500 条长，因为它很小） */
    private const val KEEP_MS = 30L * 24 * 60 * 60 * 1000

    /** 自检最短间隔 20 小时：每天只开一次 App 也补得上 */
    private const val CHECK_INTERVAL_MS = 20L * 60 * 60 * 1000

    private const val PREF = "ruyue_ledger"
    private const val KEY_LAST_CHECK = "last_check_at"
    private const val NOTIFY_ID = 7301

    /** 记账/销账走独立单线程：排期可能发生在主线程或广播里，绝不能阻塞 */
    private val io = Executors.newSingleThreadExecutor()

    // ---------------- 记账 / 销账 ----------------

    /** 排期成功时调用（幂等：同一 (提醒, 时刻) 只记一条） */
    fun recordExpected(context: Context, reminderId: Long, title: String, expectedAt: Long) {
        val app = context.applicationContext
        runCatching {
            io.execute {
                runCatching {
                    runBlocking {
                        ReminderDb.get(app).expectedFireDao().insertIgnore(
                            ExpectedFire(
                                reminderId = reminderId,
                                title = title,
                                expectedAt = expectedAt,
                                state = ExpectedState.EXPECTED,
                            )
                        )
                    }
                }
            }
        }
    }

    /** 真的响了 → 销账。返回是否找到了对应的预期（找不到也不算错：可能是老版本排的期） */
    suspend fun settleFired(
        context: Context,
        reminderId: Long,
        at: Long = System.currentTimeMillis(),
    ): Boolean {
        val dao = ReminderDb.get(context).expectedFireDao()
        val row = dao.findPendingNear(
            reminderId = reminderId,
            near = at,
            from = at - MATCH_WINDOW_MS,
            to = at + MATCH_WINDOW_MS,
        ) ?: return false
        dao.settle(row.id, ExpectedState.FIRED, at, "")
        return true
    }

    /** 被条件闸门故意拦下 → 记成「跳过」，不算故障 */
    suspend fun settleSkipped(
        context: Context,
        reminderId: Long,
        at: Long = System.currentTimeMillis(),
        reason: String,
    ) {
        val dao = ReminderDb.get(context).expectedFireDao()
        val row = dao.findPendingNear(
            reminderId = reminderId,
            near = at,
            from = at - MATCH_WINDOW_MS,
            to = at + MATCH_WINDOW_MS,
        ) ?: return
        dao.settle(row.id, ExpectedState.SKIPPED, at, reason)
    }

    /** 提醒被删/停用 → 这批预期作废，不参与对账 */
    fun settleCancelled(context: Context, reminderId: Long) {
        val app = context.applicationContext
        runCatching {
            io.execute {
                runCatching {
                    runBlocking {
                        ReminderDb.get(app).expectedFireDao().settleAllPendingOf(
                            reminderId = reminderId,
                            state = ExpectedState.CANCELLED,
                            at = System.currentTimeMillis(),
                            reason = "提醒已删除或停用",
                        )
                    }
                }
            }
        }
    }

    // ---------------- 对账 ----------------

    data class Report(
        val missed: List<ExpectedFire>,
        val attribution: List<String>,
        val silentApp: Boolean,
        val ranNow: Boolean,
    ) {
        val hasMissed: Boolean get() = missed.isNotEmpty()
    }

    /**
     * 跑一次对账。
     *
     * @param force 忽略 20 小时节流（设置页手动触发用）
     */
    suspend fun runCheck(
        context: Context,
        now: Long = System.currentTimeMillis(),
        force: Boolean = false,
    ): Report {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (!force && now - last < CHECK_INTERVAL_MS) {
            return Report(emptyList(), emptyList(), false, ranNow = false)
        }

        val dao = ReminderDb.get(app).expectedFireDao()

        // 1) 过了宽限期还是 EXPECTED 的，判漏
        val expired = dao.pendingBefore(now - GRACE_MS)
        if (expired.isNotEmpty()) {
            dao.settleAllPendingBefore(
                before = now - GRACE_MS,
                state = ExpectedState.MISSED,
                at = now,
                reason = "超过 ${GRACE_MS / 3_600_000} 小时仍未响",
            )
        }
        val attribution = expired.map { attribute(app, it) }

        // 2) 整体静默告警：最近 72 小时有预期、却一条都没响 —— 那不是单条漏了，是整个 App 死了
        val recent = runCatching { dao.since(now - 72 * 3_600_000L) }.getOrDefault(emptyList())
        val fired = recent.count { it.state == ExpectedState.FIRED }
        val silentApp = recent.size >= 3 && fired == 0 && expired.size >= 2

        // 3) 通知 + 清旧账 + 记时间
        if (expired.isNotEmpty()) {
            runCatching { postNotification(app, expired, attribution, silentApp) }
        }
        runCatching { dao.purgeBefore(now - KEEP_MS) }
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

        return Report(expired, attribution, silentApp, ranNow = true)
    }

    data class Stats(val expected: Int, val fired: Int, val skipped: Int, val missed: Int)

    /** 最近 N 天的对账小结（首页展示用） */
    suspend fun stats(context: Context, days: Int = 7, now: Long = System.currentTimeMillis()): Stats {
        val rows = runCatching {
            ReminderDb.get(context).expectedFireDao().since(now - days * 24 * 3_600_000L)
        }.getOrDefault(emptyList())
        return Stats(
            expected = rows.size,
            fired = rows.count { it.state == ExpectedState.FIRED },
            skipped = rows.count { it.state == ExpectedState.SKIPPED },
            missed = rows.count { it.state == ExpectedState.MISSED },
        )
    }

    // ---------------- 归因 / 通知 ----------------

    /**
     * 猜「为什么没响」。
     *
     * 不是精确结论，但比"未知"有用得多：服务被停、设备重启这两类占真故障的绝大多数，
     * 而且它们都能在日志里找到确凿证据。
     */
    private suspend fun attribute(context: Context, missed: ExpectedFire): String {
        val from = missed.expectedAt - 2 * 3_600_000L
        val to = missed.expectedAt + GRACE_MS
        return runCatching {
            val log = Db.get(context).eventLogDao()
            val stops = log.listByTypeSince(EventType.SERVICE_STOP, from).filter { it.timestamp <= to }
            val boots = log.listByTypeSince(EventType.BOOT, from).filter { it.timestamp <= to }
            when {
                boots.isNotEmpty() -> "设备在 ${clock(boots.last().timestamp)} 重启过"
                stops.isNotEmpty() -> "服务在 ${clock(stops.last().timestamp)} 被停止（同期 ${stops.size} 次）"
                else -> "未找到明显原因（可能被系统限流或清掉了闹钟）"
            }
        }.getOrElse { "原因查询失败：${it.message}" }
    }

    private fun clock(at: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(at))

    private fun postNotification(
        context: Context,
        missed: List<ExpectedFire>,
        attribution: List<String>,
        silentApp: Boolean,
    ) {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val lines = missed.take(3).mapIndexed { i, m ->
            "· ${clock(m.expectedAt)}「${m.title}」 — ${attribution.getOrElse(i) { "" }}"
        }
        val more = if (missed.size > 3) "\n… 还有 ${missed.size - 3} 条" else ""
        val head = if (silentApp) {
            "如约自检：最近 3 天一条都没响，App 可能已经失效"
        } else {
            "如约自检：有 ${missed.size} 条提醒该响没响"
        }

        val notification = NotificationCompat.Builder(context, RuyueApp.CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(head)
            .setContentText(lines.firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n") + more))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFY_ID, notification)
    }
}
