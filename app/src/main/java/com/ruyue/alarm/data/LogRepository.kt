package com.ruyue.alarm.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor(private val dao: EventLogDao) {

    val recent: Flow<List<EventLog>> = dao.recent()

    suspend fun log(
        type: String,
        ssid: String? = null,
        bssid: String? = null,
        detail: String? = null,
    ): Long = dao.insert(EventLog(type = type, ssid = ssid, bssid = bssid, detail = detail))

    suspend fun lastOfType(type: String): EventLog? = dao.lastOfType(type)

    suspend fun listByTypeSince(type: String, since: Long): List<EventLog> =
        dao.listByTypeSince(type, since)

    suspend fun wifiEventsSince(since: Long): List<EventLog> = dao.wifiEventsSince(since)

    suspend fun count(): Int = dao.count()

    suspend fun clear() = dao.clear()
}
