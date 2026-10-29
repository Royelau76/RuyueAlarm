package com.ruyue.alarm.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * 「本应触发」的一条账。
 *
 * ## 为什么要有这张表
 *
 * 这个产品 80% 的成败不在功能，而在**提醒到底会不会响**。靠人翻日志发现"没响"太晚，
 * 所以要把它变成**可对账**的：排期时记一笔「预期」，真响了销账，没销的自己就浮出来了。
 *
 * ## 状态机
 *
 * ```
 * EXPECTED ─┬─→ FIRED      真的响了（时间触发走到响铃）
 *           ├─→ SKIPPED   条件不满足被「且」闸门拦下（**按设计跳过，不算故障**）
 *           ├─→ CANCELLED 提醒被删/停用 → 这批预期作废（不算故障）
 *           └─→ MISSED    过了宽限期还没销账 ← 这才是真故障
 * ```
 *
 * ⚠️ `SKIPPED` 与 `MISSED` 必须分开：把"故意不响"报成"没响"，报表天天假警，
 * 三天之后用户就再也不看了 —— 那这套机制就白做了。
 */
@Entity(
    tableName = "expected_fires",
    indices = [
        Index(value = ["reminderId", "expectedAt"], unique = true),
        Index("expectedAt"),
    ],
)
data class ExpectedFire(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val title: String,
    /** 预期触发时刻（就是闹钟排的那个时刻） */
    val expectedAt: Long,
    val state: String = ExpectedState.EXPECTED,
    val settledAt: Long = 0,
    /** 人话原因（为什么跳过 / 为什么判定漏掉） */
    val reason: String = "",
)

object ExpectedState {
    const val EXPECTED = "EXPECTED"
    const val FIRED = "FIRED"
    const val SKIPPED = "SKIPPED"
    const val CANCELLED = "CANCELLED"
    const val MISSED = "MISSED"
}

@Dao
interface ExpectedFireDao {

    /**
     * 记账。`(reminderId, expectedAt)` 上有唯一索引 + IGNORE ——
     * 保存、开机、每次重排都会调 `schedule()`，不幂等的话同一时刻会记出好几条。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(row: ExpectedFire): Long

    /** 找离 [near] 最近的一条未销账预期（用于"响了"销账，容忍几秒误差） */
    @Query(
        "SELECT * FROM expected_fires WHERE reminderId = :reminderId AND state = 'EXPECTED' " +
            "AND expectedAt BETWEEN :from AND :to ORDER BY ABS(expectedAt - :near) LIMIT 1"
    )
    suspend fun findPendingNear(reminderId: Long, near: Long, from: Long, to: Long): ExpectedFire?

    @Query("UPDATE expected_fires SET state = :state, settledAt = :at, reason = :reason WHERE id = :id")
    suspend fun settle(id: Long, state: String, at: Long, reason: String)

    @Query(
        "UPDATE expected_fires SET state = :state, settledAt = :at, reason = :reason " +
            "WHERE reminderId = :reminderId AND state = 'EXPECTED'"
    )
    suspend fun settleAllPendingOf(reminderId: Long, state: String, at: Long, reason: String)

    @Query("SELECT * FROM expected_fires WHERE state = 'EXPECTED' AND expectedAt < :before ORDER BY expectedAt")
    suspend fun pendingBefore(before: Long): List<ExpectedFire>

    @Query(
        "UPDATE expected_fires SET state = :state, settledAt = :at, reason = :reason " +
            "WHERE state = 'EXPECTED' AND expectedAt < :before"
    )
    suspend fun settleAllPendingBefore(before: Long, state: String, at: Long, reason: String)

    /** 某个时刻之后的所有账（首页"最近 7 天"用） */
    @Query("SELECT * FROM expected_fires WHERE expectedAt >= :since ORDER BY expectedAt DESC")
    suspend fun since(since: Long): List<ExpectedFire>

    @Query("DELETE FROM expected_fires WHERE expectedAt < :before")
    suspend fun purgeBefore(before: Long)
}
