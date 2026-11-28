package com.yuzuki.app.ui

/**
 * 胶囊换行的「少行数」分组（纯函数，JVM 可测）。
 *
 * 卡片上的声优胶囊原来是按输入顺序贪心换行：`1 2 3 4` 一旦名字长短不齐，
 * 就会出现「一个占一行、共四行」的浪费；其实 `1 + 4`、`2 + 3` 两行就能放下。
 * 这里做一个真正的**分行最优解**：先只保证行数最少（能塞两行就绝不用三行），
 * 行数相同时再让各行尽量均衡（剩余宽度平方和最小），避免「一行挤满、一行空一半」。
 *
 * 采用「枚举集合划分」的精确解：每项依次尝试放进任一已有行（放不下就换新行），
 * n = 4 时只有 15 种划分、n = 6 时 203 种，纯整数比较，开销可以忽略；
 * 超过 [MAX_EXACT] 个胶囊直接退回贪心（那种情况下一屏也放不下，均衡没有意义）。
 */
object ChipPacking {

    /** 精确解的项数上限；再多就走贪心，避免组合爆炸。 */
    const val MAX_EXACT = 6

    /**
     * 把 [widths]（每个胶囊的实测像素宽）分到若干行，每行宽度 + 间距不超过 [maxWidth]。
     *
     * @return 每行一组下标；行按「行内最小下标」升序排列，行内下标升序 ——
     *         摆放时就从左到右、从上到下的自然阅读顺序，用户看到的还是原来的胶囊。
     */
    fun pack(widths: List<Int>, maxWidth: Int, gap: Int): List<List<Int>> {
        if (widths.isEmpty()) return emptyList()
        // 宽度不受限（外层没给上界）：一行摆完，不用算
        if (maxWidth >= Int.MAX_VALUE / 2) return listOf(widths.indices.toList())
        if (widths.size > MAX_EXACT || maxWidth <= 0) return greedy(widths, maxWidth, gap)

        val best = ArrayList<ArrayList<Int>>()
        var bestRows = Int.MAX_VALUE
        var bestCost = Long.MAX_VALUE
        val current = ArrayList<ArrayList<Int>>()

        fun rowWidth(row: List<Int>): Int {
            if (row.isEmpty()) return 0
            var sum = 0
            row.forEachIndexed { i, idx ->
                if (i > 0) sum += gap
                sum += widths[idx]
            }
            return sum
        }

        fun search(index: Int) {
            // 剪枝：行数已经多于当前最优，后面每项各占一行也不会更好
            if (current.size > bestRows) return
            if (index == widths.size) {
                var cost = 0L
                current.forEach { row ->
                    val rest = (maxWidth - rowWidth(row)).toLong()
                    cost += rest * rest
                }
                if (current.size < bestRows || cost < bestCost) {
                    bestRows = current.size
                    bestCost = cost
                    best.clear()
                    current.forEach { best.add(ArrayList(it)) }
                }
                return
            }

            val need = widths[index]
            // 只尝试「本项之前已经存在的行」：这里必须按下标遍历 —— 递归里会临时给 current
            // 追加新行，用迭代器遍历会被判成并发修改
            val rowCount = current.size
            for (i in 0 until rowCount) {
                val row = current[i]
                // 只有间距 + 自身宽度还能塞得下才并入这一行
                if (rowWidth(row) + gap + need <= maxWidth) {
                    row.add(index)
                    search(index + 1)
                    row.removeAt(row.size - 1)
                }
            }
            // 单独开一行（自身超宽也只能独占一行，由外层裁切处理）
            current.add(arrayListOf(index))
            search(index + 1)
            current.removeAt(current.size - 1)
        }

        search(0)

        return best
            .map { row -> row.sorted() }
            .sortedBy { row -> row.first() }
    }

    /** 原顺序贪心换行（旧行为），也用于项数超过 [MAX_EXACT] 的兜底。 */
    fun greedy(widths: List<Int>, maxWidth: Int, gap: Int): List<List<Int>> {
        if (widths.isEmpty()) return emptyList()
        val rows = mutableListOf<MutableList<Int>>()
        var rowWidth = 0
        widths.forEachIndexed { index, w ->
            val row = rows.lastOrNull()
            if (row.isNullOrEmpty()) {
                rows.add(mutableListOf(index))
                rowWidth = w
                return@forEachIndexed
            }
            val need = rowWidth + gap + w
            if (maxWidth > 0 && need > maxWidth) {
                rows.add(mutableListOf(index))
                rowWidth = w
            } else {
                row.add(index)
                rowWidth = need
            }
        }
        return rows
    }
}
