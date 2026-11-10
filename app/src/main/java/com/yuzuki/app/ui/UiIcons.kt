package com.yuzuki.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
                drawCircle(tint, radius = 8.6f * u, center = center, style = Stroke(stroke))
                drawCircle(tint, radius = 3.1f * u, center = center)
            }
            MainTab.History -> {
                drawCircle(tint, radius = 8.6f * u, center = center, style = Stroke(stroke))
                // 指针：指向 12 点与 3 点方向
                drawLine(
                    tint, center, Offset(center.x, center.y - 4.4f * u),
                    strokeWidth = stroke, cap = StrokeCap.Round,
                )
                drawLine(
                    tint, center, Offset(center.x + 3.6f * u, center.y),
                    strokeWidth = stroke, cap = StrokeCap.Round,
                )
                // 左上角逆时针箭头
                drawLine(
                    tint, Offset(2.6f * u, 3.2f * u), Offset(2.6f * u, 7.4f * u),
                    strokeWidth = stroke, cap = StrokeCap.Round,
                )
                drawLine(
                    tint, Offset(2.6f * u, 3.2f * u), Offset(6.8f * u, 3.2f * u),
                    strokeWidth = stroke, cap = StrokeCap.Round,
                )
            }
        }
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

/** 重复播放：两条环形箭头。 */
@Composable
fun RepeatIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.7f * u
        drawLine(tint, Offset(5f * u, 9f * u), Offset(17f * u, 9f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(14.5f * u, 6.5f * u), Offset(17.5f * u, 9f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(14.5f * u, 11.5f * u), Offset(17.5f * u, 9f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(19f * u, 15f * u), Offset(7f * u, 15f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(9.5f * u, 12.5f * u), Offset(6.5f * u, 15f * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(9.5f * u, 17.5f * u), Offset(6.5f * u, 15f * u), stroke, StrokeCap.Round)
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

/** 实心五角星（侧栏「我的收藏」用）。 */
@Composable
fun SolidStarIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawPath(starPath(size), tint, style = Stroke(width = size.minDimension * 0.085f, join = StrokeJoin.Round))
    }
}
