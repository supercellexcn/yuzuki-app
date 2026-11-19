package com.yuzuki.app.data

import org.json.JSONArray
import org.json.JSONObject

// ---- 作品 ----

data class WorkTag(val name: String, val category: String)

data class Work(
    val id: Long,
    val rjCode: String,
    val title: String,
    val circle: String,
    val releaseDate: String,
    val series: String,
    val salesCount: Int?,
    val playCount: Int,
    val fileCount: Int,
    val folderPath: String,
    val addedAt: String,
    val updatedAt: String,
    val coverUrl: String,
    val actors: List<String>,
    val tags: List<WorkTag>,
)

data class MediaFile(
    val id: Long,
    val filename: String,
    val relPath: String,
    val ext: String,
    val kind: String,
    val isDir: Boolean,
    val size: Long,
    val sortOrder: Int,
) {
    val playable: Boolean get() = !isDir && (kind == "audio" || kind == "video")

    /**
     * 所在目录：rel_path 去掉文件名，根目录文件返回 ""。
     * 播放列表以「同目录」为边界（图片查看器的同目录切换也复用它），
     * 所以目录算法只能有这一处，别在外层再抄一遍 substringBeforeLast。
     */
    fun dirOf(): String = relPath.substringBeforeLast('/', "")
}

data class WorkDetail(
    val work: Work,
    val files: List<MediaFile>,
)

data class WorksResponse(
    val items: List<Work>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
)

data class CountItem(val name: String, val count: Int, val category: String = "")

// ---- 账号 / 偏好 ----

data class FilterCond(val type: String, val value: String) {
    fun encoded(): String = "$type:$value"
}

data class LibraryPrefs(
    val conditions: List<FilterCond> = emptyList(),
    val q: String = "",
    val sort: String = "added",
    val order: String = "desc",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("conditions", JSONArray().apply {
            conditions.forEach { put(JSONObject().put("type", it.type).put("value", it.value)) }
        })
        put("q", q)
        put("sort", sort)
        put("order", order)
    }
}

data class AuthUser(
    val id: Long,
    val username: String,
    val mustChangePassword: Boolean,
    val prefs: LibraryPrefs,
)

data class LoginResult(
    val token: String,
    val username: String,
    val mustChangePassword: Boolean,
    val prefs: LibraryPrefs,
)

// ---- 收藏 / 历史 ----

data class FavoritesGrouped(
    val works: List<Work> = emptyList(),
    val tags: List<WorkTag> = emptyList(),
    val circles: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
)

data class PlayRecord(
    val work: Work,
    val playId: Long,
    val playedAt: String,
    val startedAt: String,
    val groupCount: Int,
    val durationSeconds: Int,
)

data class PlaysResponse(
    val items: List<PlayRecord>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
)

// ---- 设置（全量，字段名与后端 AppSettings 一致）----

data class AppSettings(
    val sourcePaths: List<String> = emptyList(),
    val scanDepth: Int = 2,
    val scanConcurrency: Int = 5,
    val pageSize: Int = 20,
    val seekSeconds: Int = 15,
    val proxy: String = "",
    val dlsiteBase: String = "",
    val dlsitePath: String = "",
    val dlsiteLocale: String = "zh-cn",
    val coverDir: String = "",
    val recommendationEnabled: Boolean = true,
    val recommendationAutoCalc: Boolean = true,
    val recommendationTopN: Int = 10,
    val recommendationApiLimit: Int = 10,
    val recommendationMmrLambda: Double = 0.5,
    val recommendationWeightVoice: Double = 1.0,
    val recommendationWeightTag: Double = 1.0,
    val recommendationWeightTitle: Double = 1.0,
    val recommendationWeightCircle: Double = 1.0,
    val recommendationQualitySalesWeight: Double = 1.0,
    val recommendationMinScore: Double = 0.0,
    val recommendationCandidateLimit: Int = 200,
    val recommendationCoreTagWeight: Double = 1.0,
    val recommendationCoreTagCount: Int = 3,
    val recommendationQualityImpact: Double = 1.0,
    val recommendationSeriesPriorityEnabled: Boolean = true,
    val recommendationSeriesMaxCount: Int = 3,
    val recommendationSeriesBypassMinScore: Boolean = false,
    val wallpaperDesktopEnabled: Boolean = true,
    val wallpaperMobileEnabled: Boolean = true,
    val wallpaperBlur: Int = 0,
    val panelBlur: Int = 0,
    val tagBlur: Int = 0,
    val panelOpacity: Int = 85,
    val filelistOpacity: Int = 85,
    val controlOpacity: Int = 85,
    val cardSize: String = "medium",
    val cardWidth: Int = 160,
    val themeColorNav: String = "",
    val themeColorActor: String = "",
    val themeColorTag: String = "",
    val actorTagOpacity: Int = 80,
    val genreTagOpacity: Int = 80,
    val animationDuration: Double = 0.25,
    val hasDefaultCover: Boolean = false,
) {
    /** PUT /api/settings 的完整 body。has_default_cover 是只读派生字段，不回写。 */
    fun toJson(): JSONObject = JSONObject().apply {
        put("source_paths", JSONArray().apply { sourcePaths.forEach { put(it) } })
        put("scan_depth", scanDepth)
        put("scan_concurrency", scanConcurrency)
        put("page_size", pageSize)
        put("seek_seconds", seekSeconds)
        put("proxy", proxy)
        put("dlsite_base", dlsiteBase)
        put("dlsite_path", dlsitePath)
        put("dlsite_locale", dlsiteLocale)
        put("cover_dir", coverDir)
        put("recommendation_enabled", recommendationEnabled)
        put("recommendation_auto_calc", recommendationAutoCalc)
        put("recommendation_top_n", recommendationTopN)
        put("recommendation_api_limit", recommendationApiLimit)
        put("recommendation_mmr_lambda", recommendationMmrLambda)
        put("recommendation_weight_voice", recommendationWeightVoice)
        put("recommendation_weight_tag", recommendationWeightTag)
        put("recommendation_weight_title", recommendationWeightTitle)
        put("recommendation_weight_circle", recommendationWeightCircle)
        put("recommendation_quality_sales_weight", recommendationQualitySalesWeight)
        put("recommendation_min_score", recommendationMinScore)
        put("recommendation_candidate_limit", recommendationCandidateLimit)
        put("recommendation_core_tag_weight", recommendationCoreTagWeight)
        put("recommendation_core_tag_count", recommendationCoreTagCount)
        put("recommendation_quality_impact", recommendationQualityImpact)
        put("recommendation_series_priority_enabled", recommendationSeriesPriorityEnabled)
        put("recommendation_series_max_count", recommendationSeriesMaxCount)
        put("recommendation_series_bypass_min_score", recommendationSeriesBypassMinScore)
        put("wallpaper_desktop_enabled", wallpaperDesktopEnabled)
        put("wallpaper_mobile_enabled", wallpaperMobileEnabled)
        put("wallpaper_blur", wallpaperBlur)
        put("panel_blur", panelBlur)
        put("tag_blur", tagBlur)
        put("panel_opacity", panelOpacity)
        put("filelist_opacity", filelistOpacity)
        put("control_opacity", controlOpacity)
        put("card_size", cardSize)
        put("card_width", cardWidth)
        put("theme_color_nav", themeColorNav)
        put("theme_color_actor", themeColorActor)
        put("theme_color_tag", themeColorTag)
        put("actor_tag_opacity", actorTagOpacity)
        put("genre_tag_opacity", genreTagOpacity)
        put("animation_duration", animationDuration)
    }
}

data class JobStatus(
    val status: String = "idle",
    val total: Int = 0,
    val done: Int = 0,
    val current: String = "",
    val paused: Boolean = false,
    val errors: List<String> = emptyList(),
    val removed: Int = 0,
    val downloaded: Int = 0,
    val skipped: Int = 0,
    val rows: Int = 0,
)

// ---- JSON 解析（与 frontend/src/api/types.ts 字段逐一对应）----

object Models {
    fun stringArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optString(it, null) }
    }

    fun work(o: JSONObject): Work = Work(
        id = o.optLong("id", -1),
        rjCode = o.optString("rj_code", ""),
        title = o.optString("title", ""),
        circle = o.optString("circle", ""),
        releaseDate = o.optString("release_date", ""),
        series = o.optString("series", ""),
        salesCount = o.opt("sales_count")?.takeIf { it != JSONObject.NULL }
            ?.let { runCatching { it.toString().toDouble().toInt() }.getOrNull() },
        playCount = o.optInt("play_count", 0),
        fileCount = o.optInt("file_count", 0),
        folderPath = o.optString("folder_path", ""),
        addedAt = o.optString("added_at", ""),
        updatedAt = o.optString("updated_at", ""),
        coverUrl = o.optString("cover_url", ""),
        actors = stringArray(o.optJSONArray("actors")),
        tags = objects(o.optJSONArray("tags")).map { WorkTag(it.optString("name", ""), it.optString("category", "")) },
    )

    fun worksResponse(o: JSONObject): WorksResponse = WorksResponse(
        items = objects(o.optJSONArray("items")).map(::work),
        total = o.optInt("total", 0),
        page = o.optInt("page", 1),
        pageSize = o.optInt("pageSize", 20),
    )

    fun mediaFile(o: JSONObject): MediaFile = MediaFile(
        id = o.optLong("id", -1),
        filename = o.optString("filename", ""),
        relPath = o.optString("rel_path", ""),
        ext = o.optString("ext", ""),
        kind = o.optString("kind", ""),
        isDir = o.optInt("is_dir", 0) == 1,
        size = o.optLong("size", 0),
        sortOrder = o.optInt("sort_order", 0),
    )

    fun workDetail(o: JSONObject): WorkDetail = WorkDetail(
        work = work(o),
        files = objects(o.optJSONArray("files")).map(::mediaFile),
    )

    fun countItems(arr: JSONArray?): List<CountItem> = objects(arr).map {
        CountItem(it.optString("name", ""), it.optInt("count", 0), it.optString("category", ""))
    }

    fun prefs(o: JSONObject?): LibraryPrefs {
        if (o == null) return LibraryPrefs()
        val conds = objects(o.optJSONArray("conditions")).map {
            FilterCond(it.optString("type", ""), it.optString("value", ""))
        }.filter { it.type.isNotBlank() && it.value.isNotBlank() }
        return LibraryPrefs(
            conditions = conds,
            q = o.optString("q", ""),
            sort = o.optString("sort", "added"),
            order = o.optString("order", "desc"),
        )
    }

    fun authUser(o: JSONObject): AuthUser = AuthUser(
        id = o.optLong("id", -1),
        username = o.optString("username", ""),
        mustChangePassword = o.optBoolean("must_change_password", false),
        prefs = prefs(o.optJSONObject("prefs")),
    )

    fun loginResult(o: JSONObject): LoginResult = LoginResult(
        token = o.optString("token", ""),
        username = o.optString("username", ""),
        mustChangePassword = o.optBoolean("must_change_password", false),
        prefs = prefs(o.optJSONObject("prefs")),
    )

    fun favorites(o: JSONObject): FavoritesGrouped = FavoritesGrouped(
        works = objects(o.optJSONArray("works")).map(::work),
        tags = objects(o.optJSONArray("tags")).map {
            WorkTag(it.optString("name", ""), it.optString("category", ""))
        },
        circles = stringArray(o.optJSONArray("circles")),
        actors = stringArray(o.optJSONArray("actors")),
    )

    fun playsResponse(o: JSONObject): PlaysResponse = PlaysResponse(
        items = objects(o.optJSONArray("items")).map {
            PlayRecord(
                work = work(it),
                playId = it.optLong("play_id", 0),
                playedAt = it.optString("played_at", ""),
                startedAt = it.optString("started_at", ""),
                groupCount = it.optInt("group_count", 0),
                durationSeconds = it.optInt("duration_seconds", 0),
            )
        },
        total = o.optInt("total", 0),
        page = o.optInt("page", 1),
        pageSize = o.optInt("pageSize", 20),
    )

    fun settings(o: JSONObject): AppSettings = AppSettings(
        sourcePaths = stringArray(o.optJSONArray("source_paths")),
        scanDepth = o.optInt("scan_depth", 2),
        scanConcurrency = o.optInt("scan_concurrency", 5),
        pageSize = o.optInt("page_size", 20),
        seekSeconds = o.optInt("seek_seconds", 15),
        proxy = o.optString("proxy", ""),
        dlsiteBase = o.optString("dlsite_base", ""),
        dlsitePath = o.optString("dlsite_path", ""),
        dlsiteLocale = o.optString("dlsite_locale", "zh-cn"),
        coverDir = o.optString("cover_dir", ""),
        recommendationEnabled = o.optBoolean("recommendation_enabled", true),
        recommendationAutoCalc = o.optBoolean("recommendation_auto_calc", true),
        recommendationTopN = o.optInt("recommendation_top_n", 10),
        recommendationApiLimit = o.optInt("recommendation_api_limit", 10),
        recommendationMmrLambda = o.optDouble("recommendation_mmr_lambda", 0.5),
        recommendationWeightVoice = o.optDouble("recommendation_weight_voice", 1.0),
        recommendationWeightTag = o.optDouble("recommendation_weight_tag", 1.0),
        recommendationWeightTitle = o.optDouble("recommendation_weight_title", 1.0),
        recommendationWeightCircle = o.optDouble("recommendation_weight_circle", 1.0),
        recommendationQualitySalesWeight = o.optDouble("recommendation_quality_sales_weight", 1.0),
        recommendationMinScore = o.optDouble("recommendation_min_score", 0.0),
        recommendationCandidateLimit = o.optInt("recommendation_candidate_limit", 200),
        recommendationCoreTagWeight = o.optDouble("recommendation_core_tag_weight", 1.0),
        recommendationCoreTagCount = o.optInt("recommendation_core_tag_count", 3),
        recommendationQualityImpact = o.optDouble("recommendation_quality_impact", 1.0),
        recommendationSeriesPriorityEnabled = o.optBoolean("recommendation_series_priority_enabled", true),
        recommendationSeriesMaxCount = o.optInt("recommendation_series_max_count", 3),
        recommendationSeriesBypassMinScore = o.optBoolean("recommendation_series_bypass_min_score", false),
        wallpaperDesktopEnabled = o.optBoolean("wallpaper_desktop_enabled", true),
        wallpaperMobileEnabled = o.optBoolean("wallpaper_mobile_enabled", true),
        wallpaperBlur = o.optInt("wallpaper_blur", 0),
        panelBlur = o.optInt("panel_blur", 0),
        tagBlur = o.optInt("tag_blur", 0),
        panelOpacity = o.optInt("panel_opacity", 85),
        filelistOpacity = o.optInt("filelist_opacity", 85),
        controlOpacity = o.optInt("control_opacity", 85),
        cardSize = o.optString("card_size", "medium"),
        cardWidth = o.optInt("card_width", 160),
        themeColorNav = o.optString("theme_color_nav", ""),
        themeColorActor = o.optString("theme_color_actor", ""),
        themeColorTag = o.optString("theme_color_tag", ""),
        actorTagOpacity = o.optInt("actor_tag_opacity", 80),
        genreTagOpacity = o.optInt("genre_tag_opacity", 80),
        animationDuration = o.optDouble("animation_duration", 0.25),
        hasDefaultCover = o.optBoolean("has_default_cover", false),
    )

    fun jobStatus(o: JSONObject): JobStatus = JobStatus(
        status = o.optString("status", "idle"),
        total = o.optInt("total", 0),
        done = o.optInt("done", 0),
        current = o.optString("current", ""),
        paused = o.optBoolean("paused", false),
        errors = stringArray(o.optJSONArray("errors")),
        removed = o.optInt("removed", 0),
        downloaded = o.optInt("downloaded", 0),
        skipped = o.optInt("skipped", 0),
        rows = o.optInt("rows", 0),
    )

    fun objects(arr: JSONArray?): List<JSONObject> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }
}
