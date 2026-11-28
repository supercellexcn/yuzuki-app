package com.yuzuki.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.data.Work
import com.yuzuki.app.ui.theme.ShapePill
import com.yuzuki.app.ui.theme.WebColors
import com.yuzuki.app.ui.theme.pillTextStyle
import kotlinx.coroutines.launch

/**
 * 作品卡片（web `WorkCard.vue`）—— 媒体库网格、相关推荐、播放历史卡片三处共用。
 *
 * 尺寸/间距全部按 web 的 CSS px 原值落到 dp：卡片圆角 10、封面 1:1、
 * 信息区 padding 10 12 12 + gap 6、封面标签 11px、声优 chip 12px。
 *
 * 布局：库页 / 历史卡片是两列**瀑布流**（小红书式），每张卡按自身内容取自然高度，
 * 两列互不影响，短卡下方直接接下一张，不再被邻卡撑高留白。
 */
private val CardShape = RoundedCornerShape(10.dp)

/** 自定义标签的统一 category 值（与 DLsite/hvdb 原生标签区分；内置「全年龄」也属于此列）。 */
const val CUSTOM_TAG = "custom"

/** 播放历史卡片的附加信息（web `WorkCard` 的 history prop）。 */
data class HistoryMeta(
    /** 26-12-01 14:30 */
    val timeLabel: String,
    /** 26-12-01 */
    val shortTimeLabel: String,
    /** 45m / 1h30m / 30s */
    val durationLabel: String,
)

@Composable
fun WorkCard(
    work: Work,
    imageMode: Boolean,
    cardBg: Color,
    metaBg: Color,
    genreBg: Color,
    actorBg: Color,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    history: HistoryMeta? = null,
    onCond: (FilterCond) -> Unit = {},
    /**
     * 封面固定高度（图片模式用）。`StaggeredGridCells.Fixed` 会把除不尽的余数补给前几列
     * （实测 1280px 屏左列 557 / 右列 556），正方形卡片高度跟宽走就会每张差 1px，
     * 而瀑布流两列各自累加 y，落差会一直往下累积。由调用方传入统一列宽即可对齐。
     */
    coverHeight: Dp? = null,
) {
    val scope = rememberCoroutineScope()
    val fav = FavoritesState.workRjs.contains(work.rjCode)
    val isHistory = history != null
    // 封面地址只在 rj 变化时拼一次（authedUrl 里有字符串处理，别每帧算）
    val coverUrl = remember(work.rjCode, work.coverUrl) {
        if (work.coverUrl.startsWith("http")) work.coverUrl
        else AppContainer.api.authedUrl(work.coverUrl)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(cardBg)
            // 信息区底色铺满整张卡片（封面自带底色，覆盖上半部分）
            .then(if (!imageMode) Modifier.background(metaBg) else Modifier)
            .border(1.dp, WebColors.Border, CardShape)
            .clickable(onClick = onOpen),
    ) {
        // clipToBounds 等价于 web 的 overflow:hidden —— 标签再多也不会溢出到信息区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (coverHeight != null) Modifier.height(coverHeight)
                    else Modifier.aspectRatio(1f),
                )
                .background(Color(0xFF0D0F12))
                .clipToBounds(),
        ) {
            RemoteImage(
                url = coverUrl,
                modifier = Modifier.fillMaxSize(),
                // 与封面底色一致，加载中不会闪浅色块（视觉上也更不"卡"）
                placeholder = Color(0xFF0D0F12),
            )

            if (work.tags.isNotEmpty() || imageMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xB8000000)),
                            ),
                        ),
                )
            }

            if (work.tags.isNotEmpty()) {
                // 标签多时会换 3~4 行，行距压到 2dp 让整体高度更紧凑（声优 chip 仍是 4dp）
                ChipFlow(
                    gap = 2.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 4.dp),
                ) {
                    work.tags.forEach { tag ->
                        CoverTagChip(tag.name, tag.category, genreBg) { onCond(FilterCond("tag", tag.name)) }
                    }
                }
            }

            // 封面左上角：RJ 号 + 收藏星胶囊。彻底贴住卡片左上角 (0,0)，
            // 左上圆角取卡片圆角 10dp，与卡片边缘同心贴合；其余三角沿用同一幅度。
            // 黑底白字：封面明暗差异大，黑底在任何封面上都清晰。
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
                    .padding(start = 8.dp, end = 4.dp, top = 1.dp, bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    work.rjCode,
                    // RJ 胶囊同理：不再压行高，去掉字体留白后文字才在胶囊里居中
                    style = pillTextStyle(11.sp, FontWeight.SemiBold).copy(letterSpacing = 0.2.sp),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 88.dp),
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable { scope.launch { FavoritesState.toggleWorkWithToast(work.rjCode) } },
                    contentAlignment = Alignment.Center,
                ) {
                    FavoriteStarIcon(
                        favored = fav,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            // 图片模式右上角热度三角（web 的 flame）已按用户要求移除，App 暂不展示热度分档。
        }

        // 图片模式：历史卡片保留一行「播放时间 播放时长」，其余卡片整块收起
        if (imageMode && isHistory) {
            Text(
                "${history?.shortTimeLabel.orEmpty()} ${history?.durationLabel.orEmpty()}",
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = WebColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(metaBg)
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            )
        } else if (!imageMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    work.title.ifBlank { work.rjCode },
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WebColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (work.series.isNotBlank()) {
                    CardInfoRow(
                        "系列",
                        work.series,
                        onClick = if (isHistory) null else ({ onCond(FilterCond("series", work.series)) }),
                    )
                }
                if (isHistory) {
                    CardInfoRow("播放时间", history?.timeLabel.orEmpty())
                    CardInfoRow("播放时长", history?.durationLabel.orEmpty())
                } else {
                    if (work.circle.isNotBlank()) {
                        CardInfoRow("社团", work.circle) { onCond(FilterCond("circle", work.circle)) }
                    }
                    if (work.salesCount != null) {
                        CardInfoRow("销量", "%,d".format(work.salesCount))
                    }
                    // 播放次数：紧跟在销量下方独占一行；0 次不占位，免得整屏都是「播放 0」
                    if (work.playCount > 0) {
                        CardInfoRow("播放", "%,d".format(work.playCount))
                    }
                }
                if (work.actors.isNotEmpty()) {
                    // 自算换行（Column+Row）代替 FlowRow：换行结果与真实宽度一致，
                    // 卡片高度随内容自然增长，瀑布流里就是该卡片的自然高度。
                    ActorChips(
                        actors = work.actors.take(4),
                        background = actorBg,
                        onClick = { onCond(FilterCond("actor", it)) },
                    )
                }
            }
        }
    }
}

/** .row: flex / gap 8 / font-size 13；.label 用 muted，.value 用正文色。 */
@Composable
private fun CardInfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 显式 lineHeight：intrinsic 高度才与实际排版一致，等高行才不会算矮
        Text(label, fontSize = 13.sp, lineHeight = 17.sp, color = WebColors.Muted, maxLines = 1)
        Text(
            value.ifBlank { "—" },
            fontSize = 13.sp,
            lineHeight = 17.sp,
            color = WebColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * .cover-tag: 11px / padding 3 9 / radius 999 / 主题标签色 × 标签透明度。
 * 压紧版：行高 12sp + 上下 2dp、左右 7dp（单行 16dp），标签多行时不至于压掉半个封面；
 * 左右内边距收窄后每行还能多塞一个标签，常能少换一行。
 */
@Composable
private fun CoverTagChip(text: String, category: String, background: Color, onClick: () -> Unit) {
    // 自定义标签（category=='custom'，含内置「全年龄」）：与 web `.cover-tag.tag-custom` 一致的深绿配色
    val custom = category == CUSTOM_TAG
    Box(
        modifier = Modifier
            .clip(ShapePill)
            .background(if (custom) WebColors.CustomTagBg else background)
            .then(if (custom) Modifier.border(1.dp, WebColors.CustomTagBorder, ShapePill) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text,
            // 不再压 lineHeight：11sp 配 12sp 会让字形被行盒挤住、上下偏移
            style = pillTextStyle(11.sp),
            color = if (custom) WebColors.CustomTagFg else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 声优 chips：一次性测量的换行布局（`Layout`），不用 `BoxWithConstraints` / `FlowRow`。
 * 两者都是 SubcomposeLayout，滚动时每张卡片都要额外 subcompose 一遍，是列表卡顿的来源之一；
 * 这里直接测量所有 chip 再按宽度分行，一次测量搞定，4 个声优全部显示。
 *
 * `pack = true`：不按输入顺序分行，而是求「行数最少、各行尽量均衡」的排布
 * （`ChipPacking`）。名字长短不一时能省一到两行，卡片高度随之下降。
 */
@Composable
private fun ActorChips(
    actors: List<String>,
    background: Color,
    onClick: (String) -> Unit,
) {
    ChipFlow(gap = 3.dp, pack = true, modifier = Modifier.fillMaxWidth()) {
        actors.forEach { actor -> ActorChip(actor, background) { onClick(actor) } }
    }
}

/**
 * 贪心 / 最优换行的 chips 容器：按子项实测宽度分行，行高取行内最高。
 *
 * [pack] = false（默认）保持输入顺序贪心换行：封面类型标签有固定层级，不能打散。
 * [pack] = true 走 [ChipPacking.pack]，**只改摆放坐标**（`placeRelative` 可以按任意顺序落子），
 * 组合顺序、测量次数都不变，所以不会带来额外 subcompose 或重组。
 */
@Composable
private fun ChipFlow(
    gap: Dp,
    modifier: Modifier = Modifier,
    pack: Boolean = false,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }

        val groups = if (pack && maxWidth < Int.MAX_VALUE / 2) {
            ChipPacking.pack(placeables.map { it.width }, maxWidth, gapPx)
        } else {
            ChipPacking.greedy(placeables.map { it.width }, maxWidth, gapPx)
        }
        val rows: List<List<Placeable>> = groups.map { row -> row.map { placeables[it] } }

        val width = if (constraints.hasBoundedWidth) constraints.maxWidth
        else rows.maxOfOrNull { r -> r.sumOf { it.width } + gapPx * (r.size - 1).coerceAtLeast(0) } ?: 0
        val height = rows.sumOf { r -> r.maxOfOrNull { it.height } ?: 0 } +
            gapPx * (rows.size - 1).coerceAtLeast(0)

        layout(width, height) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                var rowHeight = 0
                row.forEach { p ->
                    p.placeRelative(x, y)
                    x += p.width + gapPx
                    rowHeight = maxOf(rowHeight, p.height)
                }
                y += rowHeight + gapPx
            }
        }
    }
}

/**
 * .chip: 12px / padding 3 10 / radius 999；声优标签用独立配色。
 * 收紧版：左右 10→7dp、上下 3→2dp、行高 13sp、行距 3dp —— 两枚五字声优
 * 合计 74×2+3=151dp，能稳稳排在卡片内容宽 163dp 内，不再被挤到第二行。
 */
@Composable
private fun ActorChip(text: String, background: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(ShapePill)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text,
            style = pillTextStyle(12.sp),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 列表模式的一行：左侧 64dp 方形封面（左上角收藏星），右侧标题 + 「社团 · 销量」+ 声优 chips。
 * 行底色由调用方给（列表整片同色），行末 1px 分隔线由 [showDivider] 控制。
 */
@Composable
fun WorkRow(
    work: Work,
    rowBg: Color,
    actorBg: Color,
    onOpen: () -> Unit,
    onCond: (FilterCond) -> Unit = {},
    showDivider: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val fav = FavoritesState.workRjs.contains(work.rjCode)
    val coverUrl = remember(work.rjCode, work.coverUrl) {
        if (work.coverUrl.startsWith("http")) work.coverUrl
        else AppContainer.api.authedUrl(work.coverUrl)
    }

    Column(modifier = Modifier.fillMaxWidth().background(rowBg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0D0F12)),
            ) {
                RemoteImage(
                    url = coverUrl,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = Color(0xFF0D0F12),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xB3000000))
                        .clickable { scope.launch { FavoritesState.toggleWorkWithToast(work.rjCode) } },
                    contentAlignment = Alignment.Center,
                ) {
                    FavoriteStarIcon(
                        favored = fav,
                        tint = if (fav) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 列表模式不挂封面胶囊（64dp 小封面放不下），改为标题前缀 [RJ号]
                Text(
                    "[${work.rjCode}]" + work.title,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WebColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (work.circle.isNotBlank()) {
                        Text(
                            work.circle,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = WebColors.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable { onCond(FilterCond("circle", work.circle)) },
                        )
                    }
                    if (work.salesCount != null) {
                        Text(
                            "销量 ${"%,d".format(work.salesCount)}",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = WebColors.Muted,
                            maxLines = 1,
                        )
                    }
                    // 播放次数接在「社团 · 销量」之后的同一行：「· N」；
                    // 前面两者都没显示时不带分隔点，避免行首出现孤零零的「·」
                    if (work.playCount > 0) {
                        val plays = "%,d".format(work.playCount)
                        val sep = if (work.circle.isNotBlank() || work.salesCount != null) "· " else ""
                        Text(
                            "$sep$plays",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = WebColors.Muted,
                            maxLines = 1,
                        )
                    }
                }
                if (work.actors.isNotEmpty()) {
                    ActorChips(
                        actors = work.actors.take(3),
                        background = actorBg,
                        onClick = { onCond(FilterCond("actor", it)) },
                    )
                }
            }
        }
        if (showDivider) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(WebColors.Border))
        }
    }
}
