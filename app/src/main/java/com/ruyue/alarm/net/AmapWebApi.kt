package com.ruyue.alarm.net

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ruyue.alarm.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 高德 **Web 服务 API** 的最小客户端 —— 只为了「按地址找」这一个功能。
 *
 * ## 为什么不用地图 SDK
 *
 * 从"给没去过的地方设提醒"这个需求出发，我们只需要**一个坐标**。
 * 地图 SDK 的唯一不可替代能力是"渲染可交互地图"，代价是 2~3MB 体积 + 一个闭源组件 +
 * 隐私声明 + 不确定的 API 面。而地理编码/POI 搜索只是一个 HTTPS + JSON 请求：
 * 体积 0、出错可见（能直接显示高德的 `info`）、失败可降级（提示用户改用当前位置）。
 *
 * ## 接口要点（照官方文档 + 实测确认，别凭印象改）
 *
 * - POI 文本搜索：`https://restapi.amap.com/v3/place/text?key=&keywords=&city=&offset=&page=`
 *   → 对「某某医院 / 某某商场 / 某某小区」这类**地点名**最准
 * - 地理编码：`https://restapi.amap.com/v3/geocode/geo?key=&address=&city=`
 *   → 对「XX路XX号」这类**结构化地址**最准，同时作为 POI 搜不到的兜底
 * - 两者返回的 `location` 都是 **"经度,纬度"**（顺序别搞反！）
 * - 响应里 `status` 是**字符串**："1" 成功 / "0" 失败；失败时看 `info` + `infocode`
 */
object AmapWebApi {

    private const val TIMEOUT_MS = 8_000

    /** 一条搜索结果 */
    data class PlaceHit(
        val name: String,
        val address: String,
        val latitude: Double,
        val longitude: Double,
        /** 来源：POI 搜索 / 地理编码 —— 显示出来便于判断这条结果可不可信 */
        val source: String,
    )

    sealed interface Result {
        data class Ok(val hits: List<PlaceHit>) : Result
        data class Fail(val message: String) : Result
    }

    fun hasKey(): Boolean = BuildConfig.AMAP_WEB_KEY.isNotBlank()

    /**
     * 先 POI 文本搜索，没有结果再用地理编码兜底。
     *
     * @param city 可选：填了会显著提高准确度（「第一人民医院」每个城市都有）
     */
    suspend fun search(keywords: String, city: String = ""): Result = withContext(Dispatchers.IO) {
        val key = BuildConfig.AMAP_WEB_KEY
        if (key.isBlank()) {
            return@withContext Result.Fail(
                "没有配置「Web服务」Key：在 local.properties 里加 AMAP_WEB_KEY=… 后重新打包"
            )
        }
        val q = keywords.trim()
        if (q.isEmpty()) return@withContext Result.Fail("请输入要搜索的地点")

        var lastError = "没搜到「$q」，换个说法试试（比如加城市名）"

        // ---- 1) POI 文本搜索 ----
        val poiRaw = runCatching { httpGet(poiUrl(key, q, city)) }
        poiRaw.getOrNull()?.let { body ->
            when (val parsed = parsePoi(body)) {
                is Result.Ok -> if (parsed.hits.isNotEmpty()) return@withContext parsed
                is Result.Fail -> lastError = parsed.message
            }
        }
        poiRaw.exceptionOrNull()?.let { lastError = it.message ?: "网络异常" }

        // ---- 2) 地理编码兜底 ----
        val geoRaw = runCatching { httpGet(geoUrl(key, q, city)) }
        geoRaw.getOrNull()?.let { body ->
            when (val parsed = parseGeocode(body)) {
                is Result.Ok -> if (parsed.hits.isNotEmpty()) return@withContext parsed
                is Result.Fail -> lastError = parsed.message
            }
        }
        geoRaw.exceptionOrNull()?.let { lastError = it.message ?: "网络异常" }

        Result.Fail(lastError)
    }

    /**
     * 静态地图缩略图（用来确认"选中的点到底在哪"）。
     *
     * 拿不到就返回 null —— 缩略图是锦上添花，不能因为它让整个选点流程失败。
     */
    suspend fun staticMap(lat: Double, lon: Double, width: Int = 480, height: Int = 300): Bitmap? =
        withContext(Dispatchers.IO) {
            val key = BuildConfig.AMAP_WEB_KEY
            if (key.isBlank()) return@withContext null
            val loc = "$lon,$lat" // 注意顺序：经度在前
            val url = "https://restapi.amap.com/v3/staticmap" +
                "?key=${enc(key)}" +
                "&location=${enc(loc)}" +
                "&zoom=16" +
                "&size=${width}*${height}" +
                "&markers=${enc("mid,0x2F62B5,A:$loc")}"
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "GET"
                }
                try {
                    if (conn.responseCode != 200) return@runCatching null
                    conn.inputStream.use { BitmapFactory.decodeStream(it) }
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }

    // ---------------- 内部 ----------------

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun poiUrl(key: String, keywords: String, city: String): String = buildString {
        append("https://restapi.amap.com/v3/place/text")
        append("?key=").append(enc(key))
        append("&keywords=").append(enc(keywords))
        append("&offset=10&page=1")
        if (city.isNotBlank()) append("&city=").append(enc(city))
    }

    private fun geoUrl(key: String, address: String, city: String): String = buildString {
        append("https://restapi.amap.com/v3/geocode/geo")
        append("?key=").append(enc(key))
        append("&address=").append(enc(address))
        if (city.isNotBlank()) append("&city=").append(enc(city))
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
        }
        return try {
            val code = conn.responseCode
            if (code != 200) error("HTTP $code")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** 把 "经度,纬度" 解析成 (lat, lon) —— 顺序反了会跑到地球另一边，这里集中处理一次 */
    private fun parseLocation(raw: String?): Pair<Double, Double>? {
        val parts = raw?.split(',') ?: return null
        if (parts.size != 2) return null
        val lon = parts[0].trim().toDoubleOrNull() ?: return null
        val lat = parts[1].trim().toDoubleOrNull() ?: return null
        if (lat == 0.0 && lon == 0.0) return null
        return lat to lon
    }

    private fun parsePoi(body: String): Result = runCatching {
        val root = JSONObject(body)
        if (root.optString("status") != "1") return@runCatching Result.Fail(explain(root))
        val arr = root.optJSONArray("pois") ?: return@runCatching Result.Ok(emptyList())
        val hits = mutableListOf<PlaceHit>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val loc = parseLocation(o.optString("location")) ?: continue
            hits += PlaceHit(
                name = o.optString("name").ifBlank { "（无名）" },
                address = o.optString("address").takeIf { it.isNotBlank() && it != "[]" }.orEmpty(),
                latitude = loc.first,
                longitude = loc.second,
                source = "POI 搜索",
            )
        }
        Result.Ok(hits)
    }.getOrElse { Result.Fail("解析失败：${it.message}") }

    private fun parseGeocode(body: String): Result = runCatching {
        val root = JSONObject(body)
        if (root.optString("status") != "1") return@runCatching Result.Fail(explain(root))
        val arr = root.optJSONArray("geocodes") ?: return@runCatching Result.Ok(emptyList())
        val hits = mutableListOf<PlaceHit>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val loc = parseLocation(o.optString("location")) ?: continue
            val formatted = o.optString("formatted_address")
            hits += PlaceHit(
                name = formatted.ifBlank { "（地址）" },
                address = listOf(
                    o.optString("province"),
                    o.optString("city"),
                    o.optString("district"),
                ).filter { it.isNotBlank() && it != "[]" }.joinToString(""),
                latitude = loc.first,
                longitude = loc.second,
                source = "地理编码",
            )
        }
        Result.Ok(hits)
    }.getOrElse { Result.Fail("解析失败：${it.message}") }

    /**
     * 把高德的 `info` / `infocode` 翻译成人话。
     *
     * 最常见的坑就在这几个里：**用 Android 平台的 key 去调 Web 服务接口**（10009 平台不匹配）
     * —— 用户新建 key 时选错「服务平台」就会撞上，必须说清楚。
     */
    private fun explain(root: JSONObject): String {
        val info = root.optString("info").ifBlank { "未知错误" }
        val code = root.optString("infocode")
        val hint = when (code) {
            "10001", "10002" -> "Key 无效或已过期"
            "10003", "10004" -> "调用额度用完了（明天恢复，或去控制台看配额）"
            "10005", "10006" -> "请求过于频繁，稍后再试"
            "10009" -> "Key 类型不匹配：这个接口要「Web服务」平台的 key（不是 Android 平台）"
            "10044", "10045" -> "账号还没完成认证，去高德控制台做一下实名/企业认证"
            else -> info
        }
        return "$hint（高德返回 info=$info，infocode=$code）"
    }
}
