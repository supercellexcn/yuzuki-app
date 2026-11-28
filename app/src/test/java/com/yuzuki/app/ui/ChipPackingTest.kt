package com.yuzuki.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 声优胶囊换行：行数最少优先、均衡次之，且每一项只出现一次。 */
class ChipPackingTest {

    @Test
    fun `按原顺序贪心会分成三行，最优解只要两行`() {
        // 60 + 50 + 50 + 40，容器 100：顺序贪心得 [60][50,50][40] 三行，
        // 打散后 [60,40] + [50,50] 两行刚刚好 —— 就是「1+4、2+3 能并一行」的场景。
        val widths = listOf(60, 50, 50, 40)
        assertEquals(3, ChipPacking.greedy(widths, maxWidth = 100, gap = 0).size)
        assertEquals(
            listOf(listOf(0, 3), listOf(1, 2)),
            ChipPacking.pack(widths, maxWidth = 100, gap = 0),
        )
    }

    @Test
    fun `四个等宽胶囊排成两行而不是四行`() {
        val widths = listOf(45, 45, 45, 45)
        val rows = ChipPacking.pack(widths, maxWidth = 100, gap = 3)
        assertEquals(2, rows.size)
        assertEquals(listOf(0, 1, 2, 3), rows.flatten().sorted())
        assertEquals(listOf(2, 2), rows.map { it.size })
    }

    @Test
    fun `胶囊装得下时只占一行`() {
        val widths = listOf(20, 20, 20)
        assertEquals(listOf(listOf(0, 1, 2)), ChipPacking.pack(widths, maxWidth = 100, gap = 3))
    }

    @Test
    fun `超宽胶囊独占一行且不被丢弃`() {
        val rows = ChipPacking.pack(listOf(200, 30, 30), maxWidth = 100, gap = 3)
        assertEquals(listOf(0, 1, 2), rows.flatten().sorted())
        assertTrue(rows.any { it == listOf(0) })
        assertEquals(2, rows.size)
    }

    @Test
    fun `空列表返回空`() {
        assertTrue(ChipPacking.pack(emptyList(), maxWidth = 100, gap = 3).isEmpty())
        assertTrue(ChipPacking.greedy(emptyList(), maxWidth = 100, gap = 3).isEmpty())
    }

    @Test
    fun `宽度不受限时全部摆一行`() {
        val rows = ChipPacking.pack(listOf(500, 500, 500), maxWidth = Int.MAX_VALUE, gap = 3)
        assertEquals(1, rows.size)
        assertEquals(listOf(0, 1, 2), rows.first())
    }

    @Test
    fun `各项只出现一次且行按最小下标排序`() {
        val widths = listOf(33, 12, 47, 25, 60)
        val rows = ChipPacking.pack(widths, maxWidth = 90, gap = 2)
        assertEquals(widths.indices.toList(), rows.flatten().sorted())
        assertEquals(rows.size, rows.map { it.first() }.distinct().size)
        assertEquals(rows.map { it.first() }.sorted(), rows.map { it.first() })
        // 行内下标升序，保证从左到右还是原来的先后关系
        rows.forEach { row -> assertEquals(row.sorted(), row) }
        // 每行实际宽度都不超容器
        rows.forEach { row ->
            val w = row.sumOf { widths[it] } + 2 * (row.size - 1)
            assertTrue("行宽 $w 超过 90", w <= 90 || row.size == 1)
        }
    }

    @Test
    fun `超过精确解上限时退回贪心且仍覆盖全部`() {
        val widths = List(9) { 20 }
        val rows = ChipPacking.pack(widths, maxWidth = 100, gap = 3)
        assertEquals(widths.indices.toList(), rows.flatten().sorted())
        assertEquals(ChipPacking.greedy(widths, maxWidth = 100, gap = 3), rows)
    }
}
