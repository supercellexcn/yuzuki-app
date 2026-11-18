package com.yuzuki.app.data

/**
 * App 本机外观（颜色与透明度）。
 *
 * 只存本机 DataStore，**不读也不写服务端 `/api/settings`**：
 * 网页端改主题色不会改变 App 的配色，App 改色也不会影响 PC 浏览器与其他设备。
 * 纯 Kotlin（无 Android 依赖），JVM 单测可直接断言校验与规范化。
 */
data class AppearancePrefs(
    /** 主题色：顶栏、卡片底色（按 22% 混入）、强调色。 */
    val navColor: String = DefaultNav,
    /** 声优标签底色。 */
    val actorColor: String = DefaultActor,
    /** 类型标签底色。 */
    val tagColor: String = DefaultTag,
    /** 面板 / 卡片不透明度（0–100）。 */
    val panelOpacity: Int = 62,
    /** 控件（搜索框 / 胶囊 / 抽屉项）不透明度（0–100）。 */
    val controlOpacity: Int = 70,
    /** 声优标签不透明度（0–100）。 */
    val actorTagOpacity: Int = 100,
    /** 类型标签不透明度（0–100）。 */
    val genreTagOpacity: Int = 30,
) {
    /** 规范化：色值统一成 `#RRGGBB`，非法回退默认色；透明度夹到 0–100。 */
    fun sanitized(): AppearancePrefs = copy(
        navColor = normalizeHex(navColor, DefaultNav),
        actorColor = normalizeHex(actorColor, DefaultActor),
        tagColor = normalizeHex(tagColor, DefaultTag),
        panelOpacity = panelOpacity.coerceIn(0, 100),
        controlOpacity = controlOpacity.coerceIn(0, 100),
        actorTagOpacity = actorTagOpacity.coerceIn(0, 100),
        genreTagOpacity = genreTagOpacity.coerceIn(0, 100),
    )

    companion object {
        const val DefaultNav = "#A78BFA"
        const val DefaultActor = "#7BC86C"
        const val DefaultTag = "#F2A65A"

        /** 与改造前的默认外观完全一致，避免升级后配色突变。 */
        val Default = AppearancePrefs()
    }
}

/** 预设色板：主题色 / 声优 / 类型标签各一组，点一下即生效（6 色一行放得下）。 */
object AppearancePalette {
    val nav = listOf("#A78BFA", "#8AB4F8", "#6BD0C4", "#7BC86C", "#F2A65A", "#F27A9A")
    val actor = listOf("#7BC86C", "#6BD0C4", "#8AB4F8", "#C79BF2", "#F2A65A", "#F27A9A")
    val tag = listOf("#F2A65A", "#C9B458", "#7BC86C", "#6BD0C4", "#A78BFA", "#F27A9A")
}

/** `#a78bfa` / `a78bfa` / `#A78BFA` → `#A78BFA`；非法值回退 [fallback]（它自己也不合法时回退默认紫）。 */
fun normalizeHex(raw: String, fallback: String = AppearancePrefs.DefaultNav): String {
    val value = if (isValidHex(raw)) raw else fallback
    if (!isValidHex(value)) return AppearancePrefs.DefaultNav
    return "#" + value.trim().removePrefix("#").uppercase()
}

/** 用户输入是否是一个合法的 6 位色值（用于「自定义」弹窗的确认按钮）。 */
fun isValidHex(raw: String): Boolean {
    val s = raw.trim().removePrefix("#")
    return s.length == 6 && s.all { it.digitToIntOrNull(16) != null }
}
