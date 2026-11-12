package com.yuzuki.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 图2 / 图1 用到的极简线性图标。
 *
 * material-icons-core 里没有「滑块」「圆靶」「时钟回退」「漏斗」「空心星」这些形状，
 * 引入 material-icons-extended 会让 APK 增大数 MB，因此这里用 Canvas 按 24dp 视口自绘，
 * 线宽与端点风格统一（圆头、1.8 单位），保证与 web 的 lucide 图标观感一致。
 */

private const val VIEW = 24f

/** 底部 Tab 图标：库 = 三条滑块；播放器 = 圆环 + 圆心；历史 = 时钟 + 逆时针箭头。 */
@Composable
fun TabBarIcon(tab: MainTab, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.7f * u
        val center = Offset(size.width / 2f, size.height / 2f)
        when (tab) {
            MainTab.Library -> {
                listOf(6.5f to 15.5f, 12f to 8.5f, 17.5f to 14f).forEach { (y, knobX) ->
                    drawLine(
                        color = tint,
                        start = Offset(3f * u, y * u),
                        end = Offset(21f * u, y * u),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(tint, radius = 2.2f * u, center = Offset(knobX * u, y * u))
                }
            }
            MainTab.Player -> {
                // 视觉稿：外圈 + 内圈，12 点方向一段短竖线把两个圆连起来
                val outer = 8.4f
                val inner = 3.4f
                drawCircle(tint, radius = outer * u, center = center, style = Stroke(stroke))
                drawCircle(tint, radius = inner * u, center = center, style = Stroke(stroke))
                drawLine(
                    color = tint,
                    start = Offset(center.x, center.y - outer * u),
                    end = Offset(center.x, center.y - inner * u),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            MainTab.History -> {
                // 视觉稿：圆环在左上留缺口，缺口处一个逆时针箭头（90° 张角），圈内保留时针分针
                val r = 8.4f
                val gapStart = 265f   // 缺口右端（12 点略偏右），箭头落在这里
                val gapEnd = 225f     // 缺口左端
                drawArc(
                    color = tint,
                    startAngle = gapStart,
                    sweepAngle = 360f - (gapStart - gapEnd),
                    useCenter = false,
                    topLeft = Offset((12f - r) * u, (12f - r) * u),
                    size = Size(2 * r * u, 2 * r * u),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                // 逆时针（角度递减）方向的切线
                val rad = gapStart * PI.toFloat() / 180f
                val tip = Offset(
                    (12f + r * cos(rad)) * u,
                    (12f + r * sin(rad)) * u,
                )
                val counterClock = rad - PI.toFloat() / 2f
                arrowHead(tint, tip, counterClock, 2.5f * u, stroke)
                // 时针：12 点；分针：4 点方向
                drawLine(
                    tint, center, Offset(center.x, center.y - 4.2f * u),
                    strokeWidth = stroke, cap = StrokeCap.Round,
                )
                drawLine(
                    tint, center,
                    Offset(center.x + 2.9f * u, center.y + 1.7f * u),
                    strokeWidth = stroke, cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** 在 [tip] 处画一个指向 [dir]（弧度）的箭头，两条边各张开 45°（合计 90°）。 */
private fun DrawScope.arrowHead(
    tint: Color,
    tip: Offset,
    dir: Float,
    length: Float,
    stroke: Float,
) {
    val back = dir + PI.toFloat()
    listOf(-PI.toFloat() / 4f, PI.toFloat() / 4f).forEach { spread ->
        val angle = back + spread
        drawLine(
            color = tint,
            start = Offset(tip.x + length * cos(angle), tip.y + length * sin(angle)),
            end = tip,
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** 顶栏左侧菜单按钮：三条横线。 */
@Composable
fun MenuIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.8f * u
        listOf(7f, 12f, 17f).forEach { y ->
            drawLine(
                color = tint,
                start = Offset(3.5f * u, y * u),
                end = Offset(20.5f * u, y * u),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** 顶栏右侧筛选入口：漏斗。 */
@Composable
fun FunnelIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val path = Path().apply {
            moveTo(3.5f * u, 5f * u)
            lineTo(20.5f * u, 5f * u)
            lineTo(13.5f * u, 12.6f * u)
            lineTo(13.5f * u, 19f * u)
            lineTo(10.5f * u, 17f * u)
            lineTo(10.5f * u, 12.6f * u)
            close()
        }
        drawPath(path, tint, style = Stroke(width = 1.7f * u, join = StrokeJoin.Round))
    }
}

/** 升降序切换：箭头 + 从长到短的横线（desc）/ 从短到长（asc）。 */
@Composable
fun SortOrderIcon(descending: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.7f * u
        if (descending) {
            drawLine(tint, Offset(7f * u, 4f * u), Offset(7f * u, 19f * u), stroke, StrokeCap.Round)
            drawLine(tint, Offset(3.5f * u, 15.5f * u), Offset(7f * u, 19f * u), stroke, StrokeCap.Round)
            drawLine(tint, Offset(10.5f * u, 19f * u), Offset(7f * u, 19f * u), stroke, StrokeCap.Round)
            drawLine(tint, Offset(12f * u, 5f * u), Offset(20.5f * u, 5f * u), stroke, StrokeCap.Round)
            drawLine(tint, Offset(12f * u, 10f * u), Offset(17.5f * u, 10f * u), stroke, StrokeCap.Round)
            drawLine(tint, Offset(12f * u, 15f * u), Offset(15f * u, 15f * u), stroke, StrokeCap.Round)
        } else {
            drawLine(tint, Offset(7f * u, 20f * u), Offset(7f * u, 5f * u), stroke, StrokeCap.Round)
            drawLine(tint, Offset(3.5f * u, 8.5f * u), Offset(7f * u, 5f * u), stroke, StrokeCap.Round)
            drawLine(tint, Offset(10.5f * u, 5f * u), Offset(7f * u, 5f * u), stroke, StrokeCap.Round)
            drawLine(tint, Offset(12f * u, 19f * u), Offset(20.5f * u, 19f * u), stroke, StrokeCap.Round)
            drawLine(tint, Offset(12f * u, 14f * u), Offset(17.5f * u, 14f * u), stroke, StrokeCap.Round)
            drawLine(tint, Offset(12f * u, 9f * u), Offset(15f * u, 9f * u), stroke, StrokeCap.Round)
        }
    }
}

/** 搜索框左端放大镜。 */
@Composable
fun SearchIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.7f * u
        drawCircle(tint, radius = 6.2f * u, center = Offset(10.5f * u, 10.5f * u), style = Stroke(stroke))
        drawLine(
            tint, Offset(15.2f * u, 15.2f * u), Offset(20f * u, 20f * u),
            strokeWidth = stroke, cap = StrokeCap.Round,
        )
    }
}

/** 搜索框右端清除。 */
@Composable
fun ClearIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.8f * u
        drawLine(tint, Offset(6f * u, 6f * u), Offset(18f * u, 18f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(18f * u, 6f * u), Offset(6f * u, 18f * u), stroke, StrokeCap.Round)
    }
}

/** 收藏星标：未收藏为空心描边，已收藏为实心。 */
@Composable
fun FavoriteStarIcon(favored: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = starPath(size)
        if (favored) {
            drawPath(path, tint)
        } else {
            drawPath(
                path,
                tint,
                style = Stroke(width = size.minDimension * 0.085f, join = StrokeJoin.Round),
            )
        }
    }
}

/** 五角星路径：外接圆铺满给定尺寸，从 12 点方向起笔。 */
private fun starPath(size: Size): Path {
    val radius = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val inner = radius * 0.42f
    return Path().apply {
        repeat(10) { index ->
            val r = if (index % 2 == 0) radius else inner
            val angle = (-PI / 2 + index * PI / 5).toFloat()
            val x = center.x + r * cos(angle)
            val y = center.y + r * sin(angle)
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

// ---------------------------------------------------------------- 播放器图标

@Composable
fun PlayIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val path = Path().apply {
            moveTo(8f * u, 5f * u)
            lineTo(19f * u, 12f * u)
            lineTo(8f * u, 19f * u)
            close()
        }
        drawPath(path, tint)
    }
}

@Composable
fun PauseIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        drawRoundRect(
            color = tint,
            topLeft = Offset(7f * u, 5f * u),
            size = Size(3.2f * u, 14f * u),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * u),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(13.8f * u, 5f * u),
            size = Size(3.2f * u, 14f * u),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * u),
        )
    }
}

@Composable
fun PrevIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        drawLine(tint, Offset(6f * u, 6f * u), Offset(6f * u, 18f * u), 1.9f * u, StrokeCap.Round)
        val path = Path().apply {
            moveTo(18f * u, 6.5f * u)
            lineTo(18f * u, 17.5f * u)
            lineTo(8.5f * u, 12f * u)
            close()
        }
        drawPath(path, tint)
    }
}

@Composable
fun NextIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        drawLine(tint, Offset(18f * u, 6f * u), Offset(18f * u, 18f * u), 1.9f * u, StrokeCap.Round)
        val path = Path().apply {
            moveTo(6f * u, 6.5f * u)
            lineTo(6f * u, 17.5f * u)
            lineTo(15.5f * u, 12f * u)
            close()
        }
        drawPath(path, tint)
    }
}

/** 循环：只有上下两条带箭头的横线（→ / ←），不带任何折角。 */
@Composable
fun RepeatIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.8f * u
        // 上：左 → 右
        drawLine(tint, Offset(4.2f * u, 8.2f * u), Offset(19.2f * u, 8.2f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(16.2f * u, 5.2f * u), Offset(19.4f * u, 8.2f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(16.2f * u, 11.2f * u), Offset(19.4f * u, 8.2f * u), stroke, StrokeCap.Round)
        // 下：右 → 左
        drawLine(tint, Offset(19.8f * u, 15.8f * u), Offset(4.8f * u, 15.8f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(7.8f * u, 12.8f * u), Offset(4.6f * u, 15.8f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(7.8f * u, 18.8f * u), Offset(4.6f * u, 15.8f * u), stroke, StrokeCap.Round)
    }
}

/** 顺序 / 随机：两条交叉箭头。 */
@Composable
fun ShuffleIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.8f * u
        drawLine(tint, Offset(3.8f * u, 7f * u), Offset(8.6f * u, 7f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(8.6f * u, 7f * u), Offset(15.4f * u, 17f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(15.4f * u, 17f * u), Offset(20.2f * u, 17f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(17.2f * u, 14f * u), Offset(20.4f * u, 17f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(17.2f * u, 20f * u), Offset(20.4f * u, 17f * u), stroke, StrokeCap.Round)

        drawLine(tint, Offset(3.8f * u, 17f * u), Offset(8.6f * u, 17f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(8.6f * u, 17f * u), Offset(15.4f * u, 7f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(15.4f * u, 7f * u), Offset(20.2f * u, 7f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(17.2f * u, 4f * u), Offset(20.4f * u, 7f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(17.2f * u, 10f * u), Offset(20.4f * u, 7f * u), stroke, StrokeCap.Round)
    }
}

/** 圆圈内一个 i：跳转作品详情。 */
@Composable
fun InfoCircleIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.7f * u
        drawCircle(tint, radius = 9.2f * u, center = Offset(12f * u, 12f * u), style = Stroke(stroke))
        drawCircle(tint, radius = 1.15f * u, center = Offset(12f * u, 8.1f * u))
        drawLine(tint, Offset(12f * u, 11.2f * u), Offset(12f * u, 16.4f * u), stroke, StrokeCap.Round)
    }
}

/** 快退：双三角朝左（竖边在右、尖端在左）。 */
@Composable
fun RewindIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        listOf(10.4f, 19.6f).forEach { base ->
            val tri = Path().apply {
                moveTo(base * u, 5.4f * u)
                lineTo(base * u, 18.6f * u)
                lineTo((base - 6.6f) * u, 12f * u)
                close()
            }
            drawPath(tri, tint)
        }
    }
}

/** 快进：双三角朝右（竖边在左、尖端在右）。 */
@Composable
fun ForwardIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        listOf(4.4f, 13.6f).forEach { base ->
            val tri = Path().apply {
                moveTo(base * u, 5.4f * u)
                lineTo(base * u, 18.6f * u)
                lineTo((base + 6.6f) * u, 12f * u)
                close()
            }
            drawPath(tri, tint)
        }
    }
}

/** 均衡器：四根高低不同的竖条（播放器右下角，预留 EQ 入口）。 */
@Composable
fun EqualizerIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val w = 2.6f * u
        val bars = listOf(4.4f to 13f, 8.8f to 6.5f, 13.2f to 10f, 17.6f to 4f)
        bars.forEach { (x, top) ->
            drawRoundRect(
                color = tint,
                topLeft = Offset(x * u - w / 2, top * u),
                size = Size(w, 19.6f * u - top * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2),
            )
        }
    }
}

@Composable
fun VolumeIcon(on: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.7f * u
        val body = Path().apply {
            moveTo(4f * u, 9.5f * u)
            lineTo(7.5f * u, 9.5f * u)
            lineTo(11.5f * u, 6f * u)
            lineTo(11.5f * u, 18f * u)
            lineTo(7.5f * u, 14.5f * u)
            lineTo(4f * u, 14.5f * u)
            close()
        }
        drawPath(body, tint)
        if (on) {
            drawArc(
                color = tint,
                startAngle = -50f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(10f * u, 8f * u),
                size = Size(6f * u, 8f * u),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = tint,
                startAngle = -50f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(11f * u, 5.5f * u),
                size = Size(9.5f * u, 13f * u),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        } else {
            drawLine(tint, Offset(15f * u, 9f * u), Offset(20f * u, 15f * u), stroke, StrokeCap.Round)
            drawLine(tint, Offset(20f * u, 9f * u), Offset(15f * u, 15f * u), stroke, StrokeCap.Round)
        }
    }
}

/** 播放列表：三条线 + 左侧符号。 */
@Composable
fun PlaylistIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.7f * u
        listOf(7f, 12f, 17f).forEach { y ->
            drawLine(tint, Offset(9f * u, y * u), Offset(20f * u, y * u), stroke, StrokeCap.Round)
            drawCircle(tint, radius = 1.3f * u, center = Offset(5.5f * u, y * u))
        }
    }
}

/** 跳转详情：方框 + 右上箭头。 */
@Composable
fun ExternalIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.7f * u
        val box = Path().apply {
            moveTo(18f * u, 13.5f * u)
            lineTo(18f * u, 19f * u)
            lineTo(5f * u, 19f * u)
            lineTo(5f * u, 6f * u)
            lineTo(10.5f * u, 6f * u)
        }
        drawPath(box, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(tint, Offset(13f * u, 4.5f * u), Offset(19.5f * u, 4.5f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(19.5f * u, 4.5f * u), Offset(19.5f * u, 11f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(19f * u, 5f * u), Offset(12f * u, 12f * u), stroke, StrokeCap.Round)
    }
}

// ---------------------------------------------------------------- 侧栏图标

/** 侧栏条目图标：社团 / 系列 / 声优 / 标签 / 历史 / 设置 / 收藏。 */
@Composable
fun SideIcon(kind: String, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.7f * u
        val s = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            "circle" -> {
                drawCircle(tint, 3.2f * u, Offset(9f * u, 8f * u), style = s)
                drawCircle(tint, 2.6f * u, Offset(16.5f * u, 9.5f * u), style = s)
                drawArc(
                    color = tint, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(3.5f * u, 11f * u), size = Size(11f * u, 9f * u), style = s,
                )
                drawArc(
                    color = tint, startAngle = 210f, sweepAngle = 120f, useCenter = false,
                    topLeft = Offset(12.5f * u, 12f * u), size = Size(8f * u, 8f * u), style = s,
                )
            }
            "series" -> {
                listOf(7f, 12f, 17f).forEach { y ->
                    drawLine(tint, Offset(4f * u, y * u), Offset(20f * u, y * u), stroke, StrokeCap.Round)
                    drawCircle(tint, 1.4f * u, Offset(8f * u, y * u))
                }
            }
            "actor" -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(9.5f * u, 3f * u),
                    size = Size(5f * u, 10f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * u),
                    style = s,
                )
                drawArc(
                    color = tint, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(6f * u, 8f * u), size = Size(12f * u, 10f * u), style = s,
                )
                drawLine(tint, Offset(12f * u, 18f * u), Offset(12f * u, 21f * u), stroke, StrokeCap.Round)
            }
            "tag" -> {
                val p = Path().apply {
                    moveTo(4f * u, 11f * u)
                    lineTo(11f * u, 4f * u)
                    lineTo(20f * u, 4f * u)
                    lineTo(20f * u, 13f * u)
                    lineTo(13f * u, 20f * u)
                    close()
                }
                drawPath(p, tint, style = s)
                drawCircle(tint, 1.5f * u, Offset(16f * u, 8f * u))
            }
            "history" -> {
                drawCircle(tint, 8.4f * u, Offset(12f * u, 12f * u), style = s)
                drawLine(tint, Offset(12f * u, 12f * u), Offset(12f * u, 7.5f * u), stroke, StrokeCap.Round)
                drawLine(tint, Offset(12f * u, 12f * u), Offset(15.5f * u, 12f * u), stroke, StrokeCap.Round)
            }
            "settings" -> {
                drawCircle(tint, 3.2f * u, Offset(12f * u, 12f * u), style = s)
                drawCircle(tint, 7.6f * u, Offset(12f * u, 12f * u), style = s)
            }
            else -> {
                drawCircle(tint, 8.4f * u, Offset(12f * u, 12f * u), style = s)
            }
        }
    }
}

// ---------------------------------------------------------------- 详情页 / 悬浮控件图标

/** 返回箭头：web `BackButton.vue` 的 lucide arrow-left（M19 12H5 / M12 19l-7-7 7-7）。 */
@Composable
fun BackArrowIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 2f * u
        drawLine(tint, Offset(19f * u, 12f * u), Offset(5f * u, 12f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(12f * u, 19f * u), Offset(5f * u, 12f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(5f * u, 12f * u), Offset(12f * u, 5f * u), stroke, StrokeCap.Round)
    }
}

/** 文件列表类型图标：音频 / 视频 / 图片 / 其它（web FileList 的 SidebarIcon）。 */
@Composable
fun FileKindIcon(kind: String, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.7f * u
        val s = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            // music：八分音符
            "music" -> {
                drawLine(tint, Offset(9f * u, 18f * u), Offset(9f * u, 7f * u), stroke, StrokeCap.Round)
                drawLine(tint, Offset(9f * u, 7f * u), Offset(18f * u, 5f * u), stroke, StrokeCap.Round)
                drawLine(tint, Offset(18f * u, 5f * u), Offset(18f * u, 15f * u), stroke, StrokeCap.Round)
                drawCircle(tint, 2.6f * u, Offset(6.4f * u, 18f * u), style = s)
                drawCircle(tint, 2.6f * u, Offset(15.4f * u, 15f * u), style = s)
            }
            // video：方框 + 播放三角
            "video" -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(2.5f * u, 5f * u),
                    size = Size(19f * u, 14f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * u),
                    style = s,
                )
                val tri = Path().apply {
                    moveTo(10f * u, 9f * u)
                    lineTo(15.5f * u, 12f * u)
                    lineTo(10f * u, 15f * u)
                    close()
                }
                drawPath(tri, tint)
            }
            // image：相框 + 山 + 太阳
            "image" -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(3f * u, 4.5f * u),
                    size = Size(18f * u, 15f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * u),
                    style = s,
                )
                drawCircle(tint, 1.6f * u, Offset(8.5f * u, 9f * u))
                val hill = Path().apply {
                    moveTo(4.5f * u, 17f * u)
                    lineTo(10f * u, 12f * u)
                    lineTo(14f * u, 16f * u)
                    lineTo(16.5f * u, 13.5f * u)
                    lineTo(20f * u, 17f * u)
                }
                drawPath(hill, tint, style = s)
            }
            // file：折角文档
            else -> {
                val p = Path().apply {
                    moveTo(13f * u, 3f * u)
                    lineTo(6f * u, 3f * u)
                    lineTo(6f * u, 21f * u)
                    lineTo(18f * u, 21f * u)
                    lineTo(18f * u, 8f * u)
                    lineTo(13f * u, 3f * u)
                    close()
                }
                drawPath(p, tint, style = s)
                drawLine(tint, Offset(13f * u, 3f * u), Offset(13f * u, 8f * u), stroke, StrokeCap.Round)
                drawLine(tint, Offset(13f * u, 8f * u), Offset(18f * u, 8f * u), stroke, StrokeCap.Round)
            }
        }
    }
}

/** 目录展开箭头（默认朝右，展开后旋转 90°）。 */
@Composable
fun ChevronRightIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.7f * u
        drawLine(tint, Offset(9.5f * u, 5f * u), Offset(16.5f * u, 12f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(16.5f * u, 12f * u), Offset(9.5f * u, 19f * u), stroke, StrokeCap.Round)
    }
}

/** 文件夹。 */
@Composable
fun FolderIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.7f * u
        val p = Path().apply {
            moveTo(3f * u, 7f * u)
            lineTo(3f * u, 19f * u)
            lineTo(21f * u, 19f * u)
            lineTo(21f * u, 7f * u)
            lineTo(12f * u, 7f * u)
            lineTo(10f * u, 4.5f * u)
            lineTo(3f * u, 4.5f * u)
            close()
        }
        drawPath(p, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** 悬浮控件的「返回首页」：房子。 */
@Composable
fun HomeIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.8f * u
        val s = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val roof = Path().apply {
            moveTo(3.5f * u, 10.5f * u)
            lineTo(12f * u, 3.5f * u)
            lineTo(20.5f * u, 10.5f * u)
        }
        drawPath(roof, tint, style = s)
        val body = Path().apply {
            moveTo(5.5f * u, 9.5f * u)
            lineTo(5.5f * u, 20f * u)
            lineTo(18.5f * u, 20f * u)
            lineTo(18.5f * u, 9.5f * u)
        }
        drawPath(body, tint, style = s)
        drawLine(tint, Offset(10f * u, 20f * u), Offset(10f * u, 14f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(10f * u, 14f * u), Offset(14f * u, 14f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(14f * u, 14f * u), Offset(14f * u, 20f * u), stroke, StrokeCap.Round)
    }
}

/** 实心五角星（侧栏「我的收藏」用）。 */
@Composable
fun SolidStarIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawPath(starPath(size), tint, style = Stroke(width = size.minDimension * 0.085f, join = StrokeJoin.Round))
    }
}

/** 视图切换：列表 = 三条等长横线（与汉堡图标同形，表达「逐行列表」）。 */
@Composable
fun ViewListIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.8f * u
        listOf(7f, 12f, 17f).forEach { y ->
            drawLine(tint, Offset(3.5f * u, y * u), Offset(20.5f * u, y * u), stroke, StrokeCap.Round)
        }
    }
}

/** 视图切换：详情 = 长方形框 + 下三分之一处的分隔线（封面在上、信息在下）。 */
@Composable
fun ViewDetailIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.7f * u
        val left = 3.4f * u
        val top = 3.6f * u
        val side = 17.2f * u
        drawRoundRect(
            color = tint,
            topLeft = Offset(left, top),
            size = Size(side, side),
            cornerRadius = CornerRadius(2.8f * u),
            style = Stroke(stroke),
        )
        // 下 1/3 的分割线：3.6 + 17.2 * 0.66 ≈ 15
        drawLine(
            tint,
            Offset(left, 15f * u),
            Offset(left + side, 15f * u),
            stroke,
            StrokeCap.Round,
        )
    }
}

/** 视图切换：图片 = 实心圆角方块（只显示封面）。 */
@Composable
fun ViewImageIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        drawRoundRect(
            color = tint,
            topLeft = Offset(3.6f * u, 3.6f * u),
            size = Size(16.8f * u, 16.8f * u),
            cornerRadius = CornerRadius(3.2f * u),
        )
    }
}
