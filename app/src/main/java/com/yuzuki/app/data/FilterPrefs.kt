package com.yuzuki.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 库页的筛选条件（**本机记忆**）：搜索词 / 已选条件 / 排序 / 升降序。
 *
 * 只写本机 DataStore（`app_library_filter`），**不读写服务端 `/api/prefs`**：
 * 网页端有它自己的一份，App 既不把上次的条件带出来，也不会覆盖网页端，
 * 两个方向都不互通。视图形态（列表 / 详情 / 图片）另存 SharedPreferences。
 */
data class LibraryFilterPrefs(
    val query: String = "",
    val conds: List<FilterCond> = emptyList(),
    val sort: String = DEFAULT_SORT,
    val order: String = "desc",
) {
    /** 坏数据兜底：排序白名单、升降序两值、条件类型白名单、去重并夹到 5 条（与 addCond 上限一致）。 */
    fun sanitized(): LibraryFilterPrefs = LibraryFilterPrefs(
        query = query.trim(),
        conds = conds.asSequence()
            .filter { it.type in COND_TYPES && it.value.isNotBlank() }
            .distinct()
            .take(MAX_CONDS)
            .toList(),
        sort = sort.takeIf { it in SORTS } ?: DEFAULT_SORT,
        order = if (order == "asc") "asc" else "desc",
    )

    fun encode(): String = JSONObject().apply {
        put("q", query)
        put("conditions", JSONArray().apply {
            conds.forEach { put(JSONObject().put("type", it.type).put("value", it.value)) }
        })
        put("sort", sort)
        put("order", order)
    }.toString()

    companion object {
        const val DEFAULT_SORT = "added"
        const val MAX_CONDS = 5
        val SORTS = setOf("added", "release", "sales")
        val COND_TYPES = setOf("circle", "series", "actor", "tag")
        val Default = LibraryFilterPrefs()

        /** 解析：空串 / 坏 JSON / 字段缺失都退化为默认值，绝不抛异常。 */
        fun decode(raw: String?): LibraryFilterPrefs {
            if (raw.isNullOrBlank()) return Default
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return Default
            val conds = runCatching {
                Models.objects(obj.optJSONArray("conditions")).mapNotNull { item ->
                    val type = item.optString("type", "")
                    val value = item.optString("value", "")
                    if (type.isBlank() || value.isBlank()) null else FilterCond(type, value)
                }
            }.getOrDefault(emptyList())
            return LibraryFilterPrefs(
                query = obj.optString("q", ""),
                conds = conds,
                sort = obj.optString("sort", DEFAULT_SORT),
                order = obj.optString("order", "desc"),
            ).sanitized()
        }
    }
}
