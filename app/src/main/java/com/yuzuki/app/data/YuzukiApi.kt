package com.yuzuki.app.data

import com.yuzuki.app.SessionEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : Exception(message)

/**
 * 全量 REST 客户端：只调用现有 /api 接口，后端零改动（C1）。
 * 统一走 HostPolicy 运行时白名单；401 广播登出；Bearer 鉴权，
 * 图片/媒体 URL 仍用 ?token= 与 web 端保持一致。
 */
class YuzukiApi(
    private val config: ServerConfig,
    private val client: OkHttpClient = defaultClient(),
) {
    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /** 由 AppContainer 在启动 / 登录后写入，直链拼接不读磁盘、不阻塞主线程。 */
    @Volatile
    var cachedBaseUrl: String = ""

    @Volatile
    var token: String? = null

    suspend fun baseUrl(): String {
        val cached = cachedBaseUrl
        if (cached.isNotBlank()) return cached
        return config.currentBaseUrl()
            ?.also { cachedBaseUrl = it }
            ?: throw ApiException(0, "尚未配置服务地址")
    }

    // ---- 通用请求 ----

    suspend fun raw(
        method: String,
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        body: JSONObject? = null,
        auth: Boolean = true,
    ): Result<String> = withContext(Dispatchers.IO) {
        val base = runCatching { baseUrl() }.getOrElse { return@withContext Result.failure(it) }
        runCatching {
            val urlBuilder = (base.trimEnd('/') + path).toHttpUrl().newBuilder()
            query.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
            val url = urlBuilder.build().toString()
            if (!config.isAllowedUrl(url, base)) {
                throw ApiException(0, "请求地址不在白名单内")
            }
            val builder = Request.Builder().url(url)
            if (auth) token?.let { builder.header("Authorization", "Bearer $it") }
            val requestBody: RequestBody? = body?.toString()?.toRequestBody(JSON)
            when (method.uppercase()) {
                "GET" -> builder.get()
                "HEAD" -> builder.head()
                "DELETE" -> if (requestBody != null) builder.delete(requestBody) else builder.delete()
                "POST" -> builder.post(requestBody ?: "{}".toRequestBody(JSON))
                "PUT" -> builder.put(requestBody ?: "{}".toRequestBody(JSON))
                else -> error("不支持的 HTTP 方法: $method")
            }
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (response.code == 401 && auth) SessionEvents.logout.tryEmit(Unit)
                    val message = runCatching { JSONObject(text).optString("error") }.getOrDefault("")
                    throw ApiException(response.code, message.ifBlank { "HTTP ${response.code}" })
                }
                text
            }
        }
    }

    private suspend fun obj(
        method: String,
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        body: JSONObject? = null,
        auth: Boolean = true,
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        // JSON 解析必须在 IO 线程：分层页时若在主线程解析，滚动会明显掉帧
        raw(method, path, query, body, auth).mapCatching { JSONObject(it) }
    }

    private suspend fun arr(
        method: String,
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        auth: Boolean = true,
    ): Result<JSONArray> = withContext(Dispatchers.IO) {
        raw(method, path, query, auth = auth).mapCatching { JSONArray(it) }
    }

    private suspend fun ok(
        method: String,
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        body: JSONObject? = null,
    ): Result<Unit> = raw(method, path, query, body).map { Unit }

    // ---- 账号 ----

    suspend fun health(base: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val url = (base ?: runCatching { baseUrl() }.getOrElse { return@withContext Result.failure(it) })
            .trimEnd('/') + "/api/health"
        runCatching {
            if (!config.isAllowedUrl(url, base ?: config.currentBaseUrl().orEmpty())) {
                throw ApiException(0, "请求地址不在白名单内")
            }
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) throw ApiException(response.code, "HTTP ${response.code}")
            }
            Unit
        }
    }

    suspend fun login(base: String, username: String, password: String): Result<LoginResult> = withContext(Dispatchers.IO) {
        val url = base.trimEnd('/') + "/api/auth/login"
        runCatching {
            if (!config.isAllowedUrl(url, base)) throw ApiException(0, "请求地址不在白名单内")
            val body = JSONObject().put("username", username).put("password", password)
                .toString().toRequestBody(JSON)
            client.newCall(Request.Builder().url(url).post(body).build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ApiException(response.code, "HTTP ${response.code}")
                Models.loginResult(JSONObject(text))
            }
        }
    }

    suspend fun me(): Result<AuthUser> = obj("GET", "/api/auth/me").mapCatching(Models::authUser)

    suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> = ok(
        "POST", "/api/auth/change-password",
        body = JSONObject().put("old_password", oldPassword).put("new_password", newPassword),
    )

    // ---- 媒体库 ----

    suspend fun works(
        q: String = "",
        conds: List<FilterCond> = emptyList(),
        sort: String = "added",
        order: String = "desc",
        page: Int = 1,
        pageSize: Int = 20,
    ): Result<WorksResponse> {
        val query = buildList {
            if (q.isNotBlank()) add("q" to q)
            conds.take(5).forEach { add("cond" to it.encoded()) }
            add("sort" to sort)
            add("order" to order)
            add("page" to page.toString())
            add("pageSize" to pageSize.toString())
        }
        return obj("GET", "/api/works", query).mapCatching(Models::worksResponse)
    }

    suspend fun randomWorks(
        q: String = "",
        conds: List<FilterCond> = emptyList(),
        count: Int = 20,
    ): Result<List<Work>> {
        val query = buildList {
            if (q.isNotBlank()) add("q" to q)
            conds.take(5).forEach { add("cond" to it.encoded()) }
            add("count" to count.toString())
        }
        return obj("GET", "/api/works/random", query)
            .mapCatching { Models.objects(it.optJSONArray("items")).map(Models::work) }
    }

    suspend fun work(rj: String): Result<WorkDetail> =
        obj("GET", "/api/works/${encode(rj)}").mapCatching(Models::workDetail)

    suspend fun recommendations(rj: String, limit: Int = 10): Result<List<Work>> =
        obj("GET", "/api/works/${encode(rj)}/recommendations", listOf("limit" to limit.toString()))
            .mapCatching { Models.objects(it.optJSONArray("recommendations")).map(Models::work) }

    suspend fun setWorkCover(rj: String, fileId: Long): Result<Unit> = ok(
        "POST", "/api/works/${encode(rj)}/cover",
        body = JSONObject().put("file_id", fileId),
    )

    suspend fun tags(): Result<List<CountItem>> =
        arr("GET", "/api/tags").mapCatching(Models::countItems)

    suspend fun actors(): Result<List<CountItem>> =
        arr("GET", "/api/actors").mapCatching(Models::countItems)

    suspend fun circles(): Result<List<CountItem>> =
        arr("GET", "/api/circles").mapCatching(Models::countItems)

    suspend fun series(): Result<List<CountItem>> =
        arr("GET", "/api/series").mapCatching(Models::countItems)

    // ---- 收藏 ----

    suspend fun favorites(): Result<FavoritesGrouped> =
        obj("GET", "/api/favorites").mapCatching(Models::favorites)

    suspend fun addFavorite(type: String, value: String, extra: String = ""): Result<Unit> = ok(
        "POST", "/api/favorites",
        body = JSONObject().put("type", type).put("value", value).put("extra", extra),
    )

    suspend fun removeFavorite(type: String, value: String): Result<Unit> =
        ok("DELETE", "/api/favorites", listOf("type" to type, "value" to value))

    // ---- 播放与历史 ----

    suspend fun recordPlay(rj: String, fileId: Long?): Result<Long> = obj(
        "POST", "/api/plays",
        body = JSONObject().put("rj", rj).put("file_id", fileId ?: JSONObject.NULL),
    ).mapCatching { it.optLong("play_id", 0) }

    suspend fun updateDuration(playId: Long, seconds: Int): Result<Unit> = ok(
        "POST", "/api/plays/$playId/duration",
        body = JSONObject().put("seconds", seconds),
    )

    suspend fun plays(page: Int, pageSize: Int): Result<PlaysResponse> =
        obj("GET", "/api/plays", listOf("page" to page.toString(), "pageSize" to pageSize.toString()))
            .mapCatching(Models::playsResponse)

    suspend fun recentPlays(limit: Int = 10, offset: Int = 0): Result<List<Work>> =
        arr("GET", "/api/plays/recent", listOf("limit" to limit.toString(), "offset" to offset.toString()))
            .mapCatching { Models.objects(it).map(Models::work) }

    // ---- 偏好 ----
    // App 不再读写服务端 /api/prefs：库页的搜索 / 筛选 / 排序只活在本次会话内存里，
    // 既不记住自己上次的条件，也不会带出或覆盖网页端保存的搜索条件。

    // ---- 设置 ----

    suspend fun settings(): Result<AppSettings> =
        obj("GET", "/api/settings").mapCatching(Models::settings)

    suspend fun saveSettings(settings: AppSettings): Result<AppSettings> =
        obj("PUT", "/api/settings", body = settings.toJson()).mapCatching(Models::settings)

    suspend fun uploadImage(path: String, base64: String, ext: String): Result<Unit> = ok(
        "POST", path,
        body = JSONObject().put("data", base64).put("ext", ext),
    )

    suspend fun deleteImage(path: String): Result<Unit> = ok("DELETE", path)

    suspend fun hasWallpaper(kind: String): Boolean =
        raw("HEAD", "/api/settings/wallpaper/$kind").isSuccess

    // ---- 扫描 / 推荐 ----

    suspend fun scanStart(): Result<Unit> = ok("POST", "/api/scan")
    suspend fun scanPause(): Result<Unit> = ok("POST", "/api/scan/pause")
    suspend fun scanResume(): Result<Unit> = ok("POST", "/api/scan/resume")
    suspend fun scanReset(): Result<Unit> = ok("POST", "/api/scan/reset")
    suspend fun scanStatus(): Result<JobStatus> =
        obj("GET", "/api/scan/status").mapCatching(Models::jobStatus)
    suspend fun redownloadCovers(): Result<Unit> = ok("POST", "/api/scan/redownload-covers")
    suspend fun coverStatus(): Result<JobStatus> =
        obj("GET", "/api/scan/cover-status").mapCatching(Models::jobStatus)
    suspend fun calcRecommendations(): Result<Unit> = ok("POST", "/api/recommendations/calc")
    suspend fun recommendationStatus(): Result<JobStatus> =
        obj("GET", "/api/recommendations/status").mapCatching(Models::jobStatus)

    // ---- 带 token 的直链（ExoPlayer / 图片加载用）----

    fun authedUrl(path: String): String {
        val url = if (path.startsWith("http")) path else cachedBaseUrl.trimEnd('/') + path
        val t = token
        if (t.isNullOrBlank()) return url
        val sep = if (url.contains("?")) "&" else "?"
        return "$url${sep}token=${URLEncoder.encode(t, "UTF-8")}"
    }

    fun mediaUrl(rj: String, fileId: Long): String = authedUrl("/api/media/$rj/$fileId")
    fun coverUrl(rj: String): String = authedUrl("/api/cover/$rj")
    fun wallpaperUrl(kind: String = "mobile", version: Long? = null): String {
        val base = authedUrl("/api/settings/wallpaper/$kind")
        return if (version == null) base else "$base&v=$version"
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
}
