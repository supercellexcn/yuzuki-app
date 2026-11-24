package com.yuzuki.app.data

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** 轻量服务定位：不引入 DI 框架，YuzukiApp.onCreate 里初始化一次。 */
object AppContainer {
    private var initialized = false

    lateinit var app: Application
        private set
    lateinit var serverConfig: ServerConfig
        private set
    lateinit var tokenStore: TokenStore
        private set
    lateinit var api: YuzukiApi
        private set

    val settings by lazy { SettingsRepository(api, offline) }
    /** App 本机外观（颜色 / 透明度），与服务端设置完全分离。 */
    val appearance by lazy { AppearanceStore(app) }

    /** App 本机交互偏好（手势时长等），同样不碰服务端。 */
    val interaction by lazy { InteractionStore(app) }

    /** App 本机记住的库页筛选条件（只写本机 DataStore，不碰服务端 `/api/prefs`）。 */
    val filter by lazy { FilterStore(app) }
    val offline by lazy { OfflineCache(app) }
    val library by lazy { LibraryRepository(api, offline) }
    val reporter by lazy { com.yuzuki.app.playback.PlayReporter(api, offline) }
    val http by lazy { OkHttpClient.Builder().followRedirects(false).build() }

    fun init(application: Application) {
        if (initialized) return
        initialized = true
        app = application
        serverConfig = ServerConfig(application)
        tokenStore = TokenStore(application)
        api = YuzukiApi(serverConfig)
    }

    /** 启动 / 登录后写入内存，供直链 URL 拼接，避免主线程读 DataStore。 */
    fun bindSession(baseUrl: String, token: String?) {
        api.token = token
        api.cachedBaseUrl = baseUrl
    }
}

class SettingsRepository(
    private val api: YuzukiApi,
    private val cache: OfflineCache,
) {
    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state
    val current: AppSettings get() = _state.value

    suspend fun load(): Result<AppSettings> {
        val online = api.settings()
        val value = online.getOrElse {
            cache.readJson("settings")?.let { runCatching { Models.settings(JSONObject(it)) }.getOrNull() }
                ?: return Result.failure(it)
        }
        _state.value = value
        cache.writeJson("settings", value.toJson().toString())
        return Result.success(value)
    }

    suspend fun save(value: AppSettings): Result<AppSettings> {
        val result = api.saveSettings(value)
        result.onSuccess { _state.value = it }
        return result
    }
}

/** 在线优先 + 本地 JSON 兜底的 Repository；离线数据由 OfflineCache 落盘。 */
class LibraryRepository(
    private val api: YuzukiApi,
    private val cache: OfflineCache,
) {
    private fun worksKey(
        q: String, conds: List<FilterCond>, sort: String, order: String, page: Int, pageSize: Int,
    ) = "works_" + sha("$q|${conds.joinToString { it.encoded() }}|$sort|$order|$page|$pageSize")

    suspend fun works(
        q: String = "",
        conds: List<FilterCond> = emptyList(),
        sort: String = "added",
        order: String = "desc",
        page: Int = 1,
        pageSize: Int = 20,
    ): Result<WorksResponse> = withContext(Dispatchers.IO) {
        // 组装缓存 JSON + 落盘 + 映射都在 IO 线程，避免分页时卡主线程
        val key = worksKey(q, conds, sort, order, page, pageSize)
        val online = api.works(q, conds, sort, order, page, pageSize)
        online.onSuccess { cache.writeJson(key, it.toCacheJson().toString()) }
        online.recoverCatching {
            val cached = cache.readJson(key) ?: throw it
            Models.worksResponse(JSONObject(cached))
        }
    }

    suspend fun work(rj: String): Result<WorkDetail> = withContext(Dispatchers.IO) {
        val key = "work_$rj"
        val online = api.work(rj)
        online.onSuccess { cache.writeJson(key, it.toCacheJson().toString()) }
        online.recoverCatching {
            val cached = cache.readJson(key) ?: throw it
            Models.workDetail(JSONObject(cached))
        }
    }

    suspend fun favorites(): Result<FavoritesGrouped> = withContext(Dispatchers.IO) {
        val online = api.favorites()
        online.onSuccess { cache.writeJson("favorites", it.toCacheJson().toString()) }
        online.recoverCatching {
            val cached = cache.readJson("favorites") ?: throw it
            Models.favorites(JSONObject(cached))
        }
    }

    suspend fun plays(page: Int, pageSize: Int): Result<PlaysResponse> = withContext(Dispatchers.IO) {
        val key = "plays_${page}_$pageSize"
        val online = api.plays(page, pageSize)
        online.onSuccess { cache.writeJson(key, it.toCacheJson().toString()) }
        online.recoverCatching {
            val cached = cache.readJson(key) ?: throw it
            Models.playsResponse(JSONObject(cached))
        }
    }

    private fun WorksResponse.toCacheJson(): JSONObject = JSONObject().apply {
        put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
        put("total", total)
        put("page", page)
        put("pageSize", pageSize)
    }

    private fun Work.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("rj_code", rjCode)
        put("title", title)
        put("circle", circle)
        put("release_date", releaseDate)
        put("series", series)
        if (salesCount != null) put("sales_count", salesCount) else put("sales_count", JSONObject.NULL)
        put("play_count", playCount)
        put("file_count", fileCount)
        put("folder_path", folderPath)
        put("added_at", addedAt)
        put("updated_at", updatedAt)
        put("cover_url", coverUrl)
        put("actors", JSONArray().apply { actors.forEach { put(it) } })
        put("tags", JSONArray().apply {
            tags.forEach { put(JSONObject().put("name", it.name).put("category", it.category)) }
        })
    }

    private fun WorkDetail.toCacheJson(): JSONObject = work.toJson().apply {
        put("files", JSONArray().apply {
            files.forEach {
                put(JSONObject()
                    .put("id", it.id)
                    .put("filename", it.filename)
                    .put("rel_path", it.relPath)
                    .put("ext", it.ext)
                    .put("kind", it.kind)
                    .put("is_dir", if (it.isDir) 1 else 0)
                    .put("size", it.size)
                    .put("sort_order", it.sortOrder))
            }
        })
    }

    private fun FavoritesGrouped.toCacheJson(): JSONObject = JSONObject().apply {
        put("works", JSONArray().apply { works.forEach { put(it.toJson()) } })
        put("tags", JSONArray().apply {
            tags.forEach { put(JSONObject().put("name", it.name).put("category", it.category)) }
        })
        put("circles", JSONArray().apply { circles.forEach { put(it) } })
        put("actors", JSONArray().apply { actors.forEach { put(it) } })
    }

    private fun PlaysResponse.toCacheJson(): JSONObject = JSONObject().apply {
        put("items", JSONArray().apply {
            items.forEach {
                put(it.work.toJson()
                    .put("play_id", it.playId)
                    .put("played_at", it.playedAt)
                    .put("started_at", it.startedAt)
                    .put("group_count", it.groupCount)
                    .put("duration_seconds", it.durationSeconds))
            }
        })
        put("total", total)
        put("page", page)
        put("pageSize", pageSize)
    }

    private fun sha(s: String): String = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(16)
}

/** 离线缓存：JSON 快照、封面、音频文件、播放记录补报队列。 */
class OfflineCache(private val app: Application) {
    private val root = File(app.filesDir, "offline").apply { mkdirs() }
    private val images = File(root, "images").apply { mkdirs() }
    private val audio = File(root, "audio").apply { mkdirs() }
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun writeJson(key: String, value: String) = runCatching {
        File(root, "$key.json").writeText(value)
    }.isSuccess

    fun readJson(key: String): String? = runCatching {
        File(root, "$key.json").takeIf { it.exists() }?.readText()
    }.getOrNull()

    fun coverFile(rj: String): File = File(images, "cover_$rj.bin")
    fun audioFile(rj: String, fileId: Long): File = File(audio, "${rj}_$fileId.bin")
    fun hasAudio(rj: String, fileId: Long): Boolean = audioFile(rj, fileId).length() > 0

    /** 下载媒体到离线目录；返回本地文件（供 ExoPlayer FileDataSource）。 */
    suspend fun downloadAudio(url: String, rj: String, fileId: Long): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!HostPolicy.isAllowedUrl(url, apiBase())) throw ApiException(0, "地址不在白名单内")
            val target = audioFile(rj, fileId)
            val tmp = File(target.parentFile, "${target.name}.part")
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) throw ApiException(response.code, "下载失败")
                val body = response.body ?: throw ApiException(0, "空响应")
                body.byteStream().use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            target
        }
    }

    suspend fun downloadCover(url: String, rj: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!HostPolicy.isAllowedUrl(url, apiBase())) throw ApiException(0, "地址不在白名单内")
            val target = coverFile(rj)
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) throw ApiException(response.code, "下载失败")
                target.writeBytes(response.body?.bytes() ?: throw ApiException(0, "空响应"))
            }
            target
        }
    }

    // ---- 播放记录补报队列 ----

    data class PendingPlay(
        val rj: String,
        val fileId: Long?,
        val startedAt: String,
        val seconds: Int,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("rj", rj)
            .put("file_id", fileId ?: JSONObject.NULL)
            .put("started_at", startedAt)
            .put("seconds", seconds)

        companion object {
            fun from(o: JSONObject) = PendingPlay(
                rj = o.optString("rj", ""),
                fileId = o.opt("file_id")?.takeIf { it != JSONObject.NULL }?.toString()?.toLongOrNull(),
                startedAt = o.optString("started_at", ""),
                seconds = o.optInt("seconds", 0),
            )
        }
    }

    fun pending(): MutableList<PendingPlay> = runCatching {
        val arr = JSONArray(readJson("pending_plays") ?: "[]")
        Models.objects(arr).map(PendingPlay::from).toMutableList()
    }.getOrDefault(mutableListOf())

    fun savePending(list: List<PendingPlay>) {
        writeJson("pending_plays", JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
    }

    fun enqueue(item: PendingPlay) {
        val list = pending()
        val key = Triple(item.rj, item.fileId, item.startedAt)
        val index = list.indexOfFirst { Triple(it.rj, it.fileId, it.startedAt) == key }
        if (index >= 0) list[index] = item.copy(seconds = maxOf(list[index].seconds, item.seconds))
        else list.add(item)
        savePending(list)
    }

    suspend fun flush(api: YuzukiApi): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val list = pending()
            if (list.isEmpty()) return@runCatching 0
            var sent = 0
            val remain = mutableListOf<PendingPlay>()
            for (item in list) {
                val playId = api.recordPlay(item.rj, item.fileId).getOrNull()
                if (playId == null || playId <= 0) {
                    remain.add(item)
                    continue
                }
                if (item.seconds > 0) api.updateDuration(playId, item.seconds)
                sent++
            }
            savePending(remain)
            sent
        }
    }

    fun clearAll() {
        runCatching {
            root.listFiles()?.forEach { if (it.isFile) it.delete() }
            images.listFiles()?.forEach { it.delete() }
            audio.listFiles()?.forEach { it.delete() }
        }
    }

    private fun apiBase(): String = AppContainer.api.cachedBaseUrl
}
