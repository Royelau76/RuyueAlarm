# -*- coding: utf-8 -*-
"""Step 5a：提醒库 v6 → v7，加「今天不再」的抑制语义。

设计：`suppressUntilDay` 存"抑制截止时刻"（本地明天 0 点）。到点自然失效，
     不需要任何清理任务；比"存布尔 + 每天清零"简单且不会漏清。
"""
import os
import re

AK = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm"


def load(rel):
    return open(os.path.join(AK, rel), encoding="utf-8").read()


def save(rel, s):
    open(os.path.join(AK, rel), "w", encoding="utf-8", newline="\n").write(s)


def rep(txt, old, new, label):
    if old in txt:
        print("  ✓", label)
        return txt.replace(old, new)
    print("  ✗ 没匹配到:", label)
    return txt


# ---------------- 1) 实体 ----------------
s = load("data/Reminder.kt")
s = rep(s, """    val onceDate: Long = 0L,

    // ---------------- WiFi 触发 ----------------""",
        """    val onceDate: Long = 0L,

    /**
     * 「今天不再」的抑制截止时刻（本地明天 00:00 的毫秒数）。0 = 没有抑制。
     *
     * 为什么存"截止时刻"而不是布尔：到点自动失效，不需要任何每天清零的任务
     * （清零任务一旦漏跑，提醒就永久哑掉）。
     */
    @ColumnInfo(defaultValue = "0")
    val suppressUntilDay: Long = 0L,

    // ---------------- WiFi 触发 ----------------""", "Reminder 加 suppressUntilDay")
save("data/Reminder.kt", s)

# ---------------- 2) 数据库版本 + 迁移 ----------------
s = load("data/ReminderDb.kt")
s = rep(s, "    version = 6,", "    version = 7,", "版本 6 → 7")
mig = """
        /** v6 → v7：「今天不再」的抑制截止时刻（0 = 没抑制） */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE reminders ADD COLUMN suppressUntilDay INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
"""
s = rep(s, "        val MIGRATION_5_6: Migration", mig + "\n        val MIGRATION_5_6: Migration", "插入 MIGRATION_6_7")
# 注册：在 addMigrations( ... ) 的右括号前插入
i = s.index("addMigrations(")
depth = 0
j = i + len("addMigrations")
for k in range(j, len(s)):
    if s[k] == "(":
        depth += 1
    elif s[k] == ")":
        depth -= 1
        if depth == 0:
            j = k
            break
print("  ✓ addMigrations 右括号位置", j)
s = s[:j] + ", MIGRATION_6_7" + s[j:]
save("data/ReminderDb.kt", s)

# ---------------- 3) DAO + 仓储 ----------------
s = load("data/ReminderDao.kt")
s = rep(s, "    suspend fun setEnabled(", """    @Query("UPDATE reminders SET suppressUntilDay = :until WHERE id = :id")
    suspend fun setSuppressUntil(id: Long, until: Long)

    suspend fun setEnabled(""", "DAO setSuppressUntil")
save("data/ReminderDao.kt", s)

s = load("data/ReminderRepository.kt")
s = rep(s, "    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)",
        """    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    /** 「今天不再」：把抑制截止写成明天 0 点（到点自动失效） */
    suspend fun setSuppressUntil(id: Long, until: Long) = dao.setSuppressUntil(id, until)""",
        "仓储 setSuppressUntil")
save("data/ReminderRepository.kt", s)

# ---------------- 4) 纯规则 ----------------
s = load("trigger/TriggerRules.kt")
s = rep(s, "    fun cooldownPassed(", """    /**
     * 「今天不再」闸门：抑制截止时刻之前，这条提醒一律不触发。
     * 返回未通过的原因（通过时返回 null），措辞直接给用户看。
     */
    fun suppressGateUnmet(suppressUntilDay: Long, now: Long): String? =
        if (suppressUntilDay > 0L && now < suppressUntilDay) "今天不再提醒它" else null

    fun cooldownPassed(""", "TriggerRules.suppressGateUnmet")
save("trigger/TriggerRules.kt", s)

# ---------------- 5) 引擎：fire() 单入口加闸门 ----------------
s = load("trigger/TriggerEngine.kt")
s = rep(s, """    ) {
        // 「且」模式：本次触发只是把条件凑齐了一半，还得看其他启用的条件此刻是否也成立""",
        """    ) {
        // 「今天不再」：抑制截止之前一律不触发（到点自动失效，不用清理任务）
        TriggerRules.suppressGateUnmet(reminder.suppressUntilDay, System.currentTimeMillis())
            ?.let { why ->
                log.log(EventType.NOTE, detail = "「${reminder.title}」$why，本次不触发")
                Ledger.settleSkipped(context, reminder.id, reason = why)
                return
            }

        // 「且」模式：本次触发只是把条件凑齐了一半，还得看其他启用的条件此刻是否也成立""",
        "fire() 加抑制闸门")
save("trigger/TriggerEngine.kt", s)

# ---------------- 6) ViewModel：suppressToday ----------------
s = load("ui/ReminderViewModel.kt")
anchor = "    fun toggleEnabled(reminder: Reminder) {"
s = rep(s, anchor, """    /**
     * 「今天不再」：抑制到明天 0 点，同时撤掉已排的闹钟。
     * 响铃页的「今天不再」按钮和详情页都会用它。
     */
    fun suppressToday(reminder: Reminder) {
        viewModelScope.launch {
            repo.setSuppressUntil(reminder.id, nextMidnight())
            ReminderScheduler.cancel(app, reminder.id)
            _message.value = "今天不再提醒「${reminder.title}」"
        }
    }

""" + anchor, "ViewModel.suppressToday")
if "fun nextMidnight(" not in s:
    s = s.rstrip() + """

/** 本地明天 0 点（「今天不再」的抑制截止） */
internal fun nextMidnight(): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
"""
save("ui/ReminderViewModel.kt", s)
print("  ✓ ViewModel.suppressToday + nextMidnight")

print("完成")
