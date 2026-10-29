package com.ruyue.alarm.data

import kotlinx.coroutines.flow.Flow

/**
 * 收藏地点的读写。
 *
 * 「存为收藏」是**按名字 upsert** 的：同名地点覆盖坐标与半径，而不是堆两条同名的 ——
 * 否则点几下就会攒出「公司」「公司」「公司」。
 */
class SavedPlaceRepository(private val dao: SavedPlaceDao) {

    val places: Flow<List<SavedPlace>> = dao.observeAll()

    suspend fun byId(id: Long): SavedPlace? = dao.byId(id)

    /**
     * 保存（同名覆盖）。返回 id。
     *
     * @param name 会 trim；空名字调用方应当先拦下
     */
    suspend fun save(
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        address: String = "",
    ): Long {
        val clean = name.trim()
        val existing = dao.byName(clean)
        val now = System.currentTimeMillis()
        val place = SavedPlace(
            id = existing?.id ?: 0L,
            name = clean,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            address = address.ifBlank { existing?.address.orEmpty() },
            createdAt = existing?.createdAt ?: now,
            lastUsedAt = now,
        )
        return dao.upsert(place)
    }

    /** 标记"最近用过"（决定列表排序） */
    suspend fun touch(id: Long) = dao.touch(id, System.currentTimeMillis())

    suspend fun delete(id: Long) = dao.delete(id)
}
