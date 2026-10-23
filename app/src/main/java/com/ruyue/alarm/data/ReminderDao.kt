package com.ruyue.alarm.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("SELECT * FROM reminders ORDER BY enabled DESC, hour, minute, id")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun byId(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun enabled(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE enabled = 1 AND wifiEnabled = 1")
    suspend fun enabledWifi(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE enabled = 1 AND placeEnabled = 1")
    suspend fun enabledPlace(): List<Reminder>

    @Query("UPDATE reminders SET lastFiredAt = :at WHERE id = :id")
    suspend fun markFired(id: Long, at: Long)

    @Query("UPDATE reminders SET enabled = 0 WHERE id = :id")
    suspend fun disable(id: Long)

    @Query("UPDATE reminders SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
