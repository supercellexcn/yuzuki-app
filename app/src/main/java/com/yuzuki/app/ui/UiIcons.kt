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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import com.yuzuki.app.data.MainTab
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

/** 底部 Tab 图标：库 = 三条滑块；收藏 = 五角星；播放器 = 圆环 + 斜置八分音符；历史 = 时钟 + 逆时针箭头。 */
@Composable
fun TabBarIcon(tab: MainTab, tint: Color, modifier: Modifier = Modifier, active: Boolean = false) {
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
            MainTab.Favorites -> {
                // 五角星：未选中画空心（与「未收藏」同形的描边星），选中填实。
                // 外接圆缩到 0.84，与相邻 Tab 的圆环（半径 8.4/24）视觉等大，不会顶满一格。
                scale(0.84f, pivot = center) {
                    val star = starPath(size)
                    if (active) {
                        drawPath(star, tint)
                    } else {
                        drawPath(star, tint, style = Stroke(stroke, join = StrokeJoin.Round))
                    }
                }
            }
            MainTab.Player -> {
                // 2026-11-20 重绘：原为「外圈 + 内圈 + 连线」，像两个套在一起的圆、读不出播放语义。
                // 现按用户给的参考图描摹：圆环 + 圆内一个斜置八分音符（空心头、杆从圆周顶点穿过、杆顶一条顺下来的曲线旗）。
                // 全部参数来自对参考图（593×627）的最小二乘拟合，以圆半径 R 为单位：
                //   音符头 中心 (−0.046,+0.030)R、半轴 0.377R × 0.223R、倾角 −26.8°；杆 斜 17.6°；旗尖 (+0.454,−2.097)R。
                // 线宽取 0.8（相邻 Tab 是 1.7）：参考图线宽只有 0.029R，等比放大到 1.7 会把音符的细节全糊掉。
                //
                // 2026-11-20 二次调整：真机上看着比相邻 Tab 小 —— 音符太高（3.08R），为了把旗塞进 24 视口只能让圆环
                // 只占 62% 宽度，而「库 / 历史」的环占 70%。这里整体放大 1.135 倍（缩放基点是图形自身 bbox 中心
                // (12, 11.94)，不是画布中心，否则上下留白不均），放大后圆环直径 ≈ 16.1dp，与历史图标 r=8.4 的环等大；
                // 代价是图形超出 24 视口约 0.9 单位（≈0.9dp），正好落在图标与标签之间的空隙里。
                val thin = 0.8f * u
                scale(1.135f, pivot = Offset(12f * u, 11.94f * u)) {
                    val noteR = 7.4f
                    val noteCy = 16f
                    drawCircle(
                        tint,
                        radius = noteR * u,
                        center = Offset(12f * u, noteCy * u),
                        style = Stroke(thin),
                    )

                    // 音符头：空心斜椭圆，中心几乎就在圆心
                    val head = Offset(11.66f * u, 16.22f * u)
                    rotate(degrees = -26.8f, pivot = head) {
                        drawOval(
                            color = tint,
                            topLeft = Offset((11.66f - 2.79f) * u, (16.22f - 1.65f) * u),
                            size = Size(5.58f * u, 3.3f * u),
                            style = Stroke(thin),
                        )
                    }

                    // 杆：下端收在音符头右端尖点，上端穿出圆周顶点
                    drawLine(
                        tint,
                        Offset(14.15f * u, 14.95f * u),
                        Offset(10.0f * u, 2.5f * u),
                        thin,
                        StrokeCap.Round,
                    )

                    // 旗：一条顺下来的曲线（先平后扬，中途无接点、无折角），再从尖端一条弧收回杆身
                    val flag = Path().apply {
                        moveTo(10.0f * u, 2.5f * u)
                        cubicTo(12.4f * u, 2.75f * u, 14.5f * u, 1.85f * u, 15.36f * u, 0.48f * u)
                        cubicTo(13.9f * u, 2.1f * u, 12.2f * u, 3.5f * u, 10.56f * u, 4.43f * u)
                    }
                    drawPath(flag, tint, style = Stroke(thin, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
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

/**
 * 升降序切换（首页排序胶囊右侧）：左侧一枚完整箭头 + 右侧三条横线。
 *
 * 旧实现的问题：箭头只画了左撇，右半边误写成水平线段（`y` 没改成 15.5 / 8.5），
 * 放大后是个「一撇加一横」的畸形箭头，所以看着奇怪。这里重画为左右对称的箭头，
 * 横线统一左对齐、长度按 desc「上长下短」/ asc「上短下长」渐变，与参考图一致。
 */
@Composable
fun SortOrderIcon(descending: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        // 24 视口里塞了 6 条线，16dp 下 1.7 会显得过细，这里略加粗到 1.9 与其它图标观感齐平
        val stroke = 1.9f * u
        fun seg(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
            color = tint,
            start = Offset(x1 * u, y1 * u),
            end = Offset(x2 * u, y2 * u),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )

        // 箭头：竖杆 + 左右两撇等长（3.2 半宽 / 3.6 高），收在 24 视口内留 1.5 边距
        val shaft = 6.6f
        if (descending) {
            seg(shaft, 4.6f, shaft, 19.2f)
            seg(shaft - 3.2f, 15.6f, shaft, 19.2f)
            seg(shaft + 3.2f, 15.6f, shaft, 19.2f)
        } else {
            seg(shaft, 19.4f, shaft, 4.8f)
            seg(shaft - 3.2f, 8.4f, shaft, 4.8f)
            seg(shaft + 3.2f, 8.4f, shaft, 4.8f)
        }

        // 三条横线：左端对齐在 12.6，长度 8.4 / 5.6 / 2.8，行距 6.4 与箭头竖向居中对齐
        val left = 12.6f
        val lengths = if (descending) listOf(8.4f, 5.6f, 2.8f) else listOf(2.8f, 5.6f, 8.4f)
        listOf(5.2f, 11.6f, 18f).forEachIndexed { i, y -> seg(left, y, left + lengths[i], y) }
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

/** 更多操作（⋮）：三个实心圆点，图片查看器顶栏用。 */
@Composable
fun MoreVerticalIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val cx = size.width / 2f
        val cy = size.height / 2f
        listOf(-6.5f, 0f, 6.5f).forEach { dy ->
            drawCircle(tint, 1.8f * u, Offset(cx, cy + dy * u))
        }
    }
}

/** 收藏星标：未收藏为空心描边，已收藏为实心。 */@Composable
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

/** 设置页「板块顺序」用的上 / 下箭头（与目录箭头同一套线宽与端点）。 */
@Composable
fun ChevronVerticalIcon(up: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / VIEW
        val stroke = 1.7f * u
        val y0 = if (up) 15f else 9f
        val y1 = if (up) 9f else 15f
        drawLine(tint, Offset(6.5f * u, y0 * u), Offset(12f * u, y1 * u), stroke, StrokeCap.Round)
        drawLine(tint, Offset(12f * u, y1 * u), Offset(17.5f * u, y0 * u), stroke, StrokeCap.Round)
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
