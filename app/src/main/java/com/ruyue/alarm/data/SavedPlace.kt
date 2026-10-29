package com.ruyue.alarm.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 收藏的地点（家 / 公司 / 医院…）。
 *
 * ## 为什么是"收藏"而不是"地点库"
 *
 * 需求是「给常去的地方设提醒不想每次都重新取位置」。所以它只是一份**坐标备忘录**：
 *  - 和提醒**没有引用关系**：提醒里存的是坐标快照（`Reminder.latitude/longitude`），
 *    删掉或改名一个收藏，不会悄悄改变已有提醒的触发范围 —— 这是刻意的
 *  - 半径也一起存：同一个地方"公司 300m / 家 1000m"是常态
 */
@Entity(tableName = "saved_places")
data class SavedPlace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 显示名：公司 / 家 / 医院 */
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 300,
    /** 可选：地图/地址搜索带回来的地址文本 */
    val address: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** 最近一次使用时间：列表按它排序，常用的排前面 */
    val lastUsedAt: Long = 0L,
)

@Dao
interface SavedPlaceDao {

    @Query("SELECT * FROM saved_places ORDER BY lastUsedAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<SavedPlace>>

    @Query("SELECT * FROM saved_places WHERE id = :id")
    suspend fun byId(id: Long): SavedPlace?

    @Query("SELECT * FROM saved_places WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): SavedPlace?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(place: SavedPlace): Long

    @Query("UPDATE saved_places SET lastUsedAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("DELETE FROM saved_places WHERE id = :id")
    suspend fun delete(id: Long)
}
