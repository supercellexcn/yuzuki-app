package com.yuzuki.app.data

/**
 * 底部四个板块。声明顺序 = 默认顺序；[label] 同时用作底部导航文案。
 *
 * 放在 data 层是因为它现在是「可配置的页面顺序」的键：`TabOrderPrefs` 需要它做
 * 去重 / 补齐 / 序列化，而 `TabOrderPrefs` 要被 JVM 单测直接断言（见 `TabOrderPrefsTest`）。
 */
enum class MainTab(val label: String) {
    Library("库"),
    Favorites("收藏"),
    Player("播放器"),
    History("历史"),
}

/**
 * 板块顺序偏好（本机 DataStore，不碰服务端）。
 *
 * 顺序同时决定两件事：底部 4 个 Tab 的左右次序、横向 Pager 每页放的是哪块内容 ——
 * 两者共用同一份索引，所以换序后「点第 N 个 Tab」与「翻到第 N 页」永远指的是同一块面板。
 */
data class TabOrderPrefs(val order: List<MainTab> = DefaultOrder) {

    /**
     * 规范化：去重（保留首次出现的位置）、丢弃未知项（[decode] 已经滤过一次）、
     * 缺失的板块按默认顺序补到末尾。**结果恒为 4 项**，Pager 页数才不会随脏数据变化。
     */
    fun sanitized(): TabOrderPrefs {
        val seen = LinkedHashSet<MainTab>(order.size)
        order.forEach { seen.add(it) }
        MainTab.entries.forEach { seen.add(it) }
        return TabOrderPrefs(seen.toList())
    }

    /** 板块在 Pager 中的页码；脏数据兜底 0。 */
    fun indexOf(tab: MainTab): Int = sanitized().order.indexOf(tab).coerceAtLeast(0)

    /** 页码 → 板块；越界兜底库页。 */
    fun tabAt(page: Int): MainTab = sanitized().order.getOrElse(page) { MainTab.Library }

    /** 把 [from] 位置的板块挪到 [to]（其余项顺序不变）；越界原样返回。 */
    fun moved(from: Int, to: Int): TabOrderPrefs {
        val list = sanitized().order.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return TabOrderPrefs(list)
        val item = list.removeAt(from)
        list.add(to, item)
        return TabOrderPrefs(list)
    }

    /** 落盘格式：`Library,Favorites,Player,History`。 */
    fun encode(): String = sanitized().order.joinToString(",") { it.name }

    companion object {
        val DefaultOrder: List<MainTab> = MainTab.entries.toList()
        val Default = TabOrderPrefs()

        /**
         * 解析落盘字符串：未知名字直接丢、重复项去重、缺的补到末尾；
         * null / 空串 / 全脏 → 默认顺序。
         */
        fun decode(raw: String?): TabOrderPrefs {
            if (raw.isNullOrBlank()) return Default
            val parsed = raw.split(',')
                .mapNotNull { name ->
                    MainTab.entries.firstOrNull { it.name == name.trim() }
                }
            return TabOrderPrefs(parsed).sanitized()
        }
    }
}
