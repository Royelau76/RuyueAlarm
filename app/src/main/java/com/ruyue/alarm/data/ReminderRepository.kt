package com.ruyue.alarm.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepository @Inject constructor(
    private val dao: ReminderDao,
    private val ssidDao: SsidHistoryDao,
    private val log: LogRepository,
) {
    val reminders: Flow<List<Reminder>> = dao.observeAll()

    /** 用过的 WiFi（最近 50 个），给编辑页做「点一下加入」 */
    val ssidHistory: Flow<List<SsidHistory>> = ssidDao.observeAll()

    suspend fun byId(id: Long): Reminder? = dao.byId(id)

    suspend fun enabled(): List<Reminder> = dao.enabled()

    suspend fun enabledWifi(): List<Reminder> = dao.enabledWifi()

    suspend fun enabledPlace(): List<Reminder> = dao.enabledPlace()

    suspend fun save(reminder: Reminder): Long =
        if (reminder.id == 0L) {
            dao.insert(reminder)
        } else {
            dao.update(reminder)
            reminder.id
        }

    suspend fun delete(reminder: Reminder) = dao.delete(reminder)

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    /** 「今天不再」：把抑制截止写成明天 0 点（到点自动失效） */
    suspend fun setSuppressUntil(id: Long, until: Long) = dao.setSuppressUntil(id, until)

    suspend fun markFired(id: Long, at: Long = System.currentTimeMillis()) = dao.markFired(id, at)

    suspend fun disable(id: Long) = dao.disable(id)

    /** 哨兵每次连上 WiFi 时调用；写失败也不该影响主流程，所以吃掉异常。 */
    suspend fun rememberSsid(ssid: String?) {
        val name = ssid?.trim().orEmpty()
        if (name.isEmpty()) return
        runCatching { ssidDao.upsert(SsidHistory(ssid = name)) }
    }

    suspend fun forgetSsid(ssid: String) = ssidDao.forget(ssid)

    suspend fun hasPlaceReminder(): Boolean = dao.enabledPlace().isNotEmpty()
}
