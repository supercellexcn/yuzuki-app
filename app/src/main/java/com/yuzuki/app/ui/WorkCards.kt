package com.yuzuki.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.data.Work
import com.yuzuki.app.ui.theme.GlassSpec
import com.yuzuki.app.ui.theme.ShapePill
import com.yuzuki.app.ui.theme.WebColors
import kotlinx.coroutines.launch

/**
 * 作品卡片（web `WorkCard.vue`）—— 媒体库网格、相关推荐、播放历史卡片三处共用。
 *
 * 尺寸/间距全部按 web 的 CSS px 原值落到 dp：卡片圆角 10、封面 1:1、
 * 信息区 padding 10 12 12 + gap 6、封面标签 11px、声优 chip 12px。
 *
 * 等高：卡片放进 `EqualHeightRow`，由「本行最高的那一张」统一整行高度；
 * 信息区用 `weight(1f)` 吃掉剩余空间，内容少的卡片多余空白落在底部（同 web 的 flex-grow）。
 */
private val CardShape = RoundedCornerShape(10.dp)

/** 播放历史卡片的附加信息（web `WorkCard` 的 history prop）。 */
data class HistoryMeta(
    /** 26-12-01 14:30 */
    val timeLabel: String,
    /** 26-12-01 */
    val shortTimeLabel: String,
    /** 45m / 1h30m / 30s */
    val durationLabel: String,
)

@OptIn(ExperimentalLayoutApi::class)
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
) {
    val scope = rememberCoroutineScope()
    val fav = FavoritesState.workRjs.contains(work.rjCode)
    val isHistory = history != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(cardBg)
            // 信息区底色铺满整张卡片：等高行把卡片拉高后，底部多出来的空白
            // 仍然是信息区的底色，看不出断层（等价 web 的 .meta { flex-grow: 1 }）
            .then(if (!imageMode) Modifier.background(metaBg) else Modifier)
            .border(1.dp, WebColors.Border, CardShape)
            .clickable(onClick = onOpen),
    ) {
        // clipToBounds 等价于 web 的 overflow:hidden —— 标签再多也不会溢出到信息区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color(0xFF0D0F12))
                .clipToBounds(),
        ) {
            RemoteImage(
                url = if (work.coverUrl.startsWith("http")) work.coverUrl
                else AppContainer.api.authedUrl(work.coverUrl),
                modifier = Modifier.fillMaxSize(),
            )

            if (work.tags.isNotEmpty() || imageMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xB8000000)),
                            ),
                        ),
                )
            }

            if (work.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    work.tags.forEach { tag ->
                        CoverTagChip(tag.name, genreBg) { onCond(FilterCond("tag", tag.name)) }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable { scope.launch { FavoritesState.toggleWork(work.rjCode) } },
                contentAlignment = Alignment.Center,
            ) {
                FavoriteStarIcon(
                    favored = fav,
                    tint = if (fav) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }

            if (imageMode) {
                val flame = GlassSpec.flame(work.salesCount)
                if (flame.count > 0) {
                    Text(
                        "▲".repeat(flame.count),
                        color = when (flame.kind) {
                            "red" -> Color(0xFFF05A4B)
                            "yellow" -> Color(0xFFF2C14E)
                            "rainbow" -> Color(0xFFB69AF6)
                            else -> Color(0xFF9AA3B0)
                        },
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    )
                }
            }
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
                }
                if (work.actors.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        work.actors.take(4).forEach { actor ->
                            ActorChip(actor, actorBg) { onCond(FilterCond("actor", actor)) }
                        }
                    }
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
        Text(label, fontSize = 13.sp, color = WebColors.Muted, maxLines = 1)
        Text(
            value.ifBlank { "—" },
            fontSize = 13.sp,
            color = WebColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** .cover-tag: 11px / padding 3 9 / radius 999 / 主题标签色 × 标签透明度。 */
@Composable
private fun CoverTagChip(text: String, background: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(ShapePill)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** .chip: 12px / padding 3 10 / radius 999；声优标签用独立配色。 */
@Composable
private fun ActorChip(text: String, background: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(ShapePill)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
