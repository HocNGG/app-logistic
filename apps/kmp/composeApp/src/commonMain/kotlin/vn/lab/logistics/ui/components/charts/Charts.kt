package vn.lab.logistics.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.lab.logistics.core.model.formatViInt
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Tokens

/**
 * =====================================================================================
 * CHART CHO MÀN HÌNH LÀM VIỆC — vẽ THẲNG bằng `Canvas` của Compose (không thêm dependency).
 *
 * Luật (spec `ui-dashboard-app-screens.md` §4 + ADR-009 §6):
 *  - Chỉ dùng những loại chart có nghĩa: **sparkline** trong KPI card, **area** cho xu hướng
 *    theo ngày, **donut** cho cơ cấu trạng thái. Không 3D, không chart cầu vồng, không animation.
 *  - **MỘT** bảng màu: series 1/2/3 = `--lab-c1/c2/c3` = `#A6F000` / `#45D4C4` / `#6EA8FF`;
 *    lưới + trục = `--lab-grid` `rgba(255,255,255,.06)`. Không màu thứ tư tự chế.
 *  - Trục có NHÃN + đơn vị; legend bằng CHỮ. Nhãn trục vẽ bằng `Text` (không `drawText`) để
 *    chữ tiếng Việt dùng đúng typography của app và không phụ thuộc `TextMeasurer`.
 *  - **Rỗng ⇒ "Chưa có dữ liệu"** qua [ChartEmptyState], không vẽ chart 0.
 *
 * ⚠️ VÌ SAO NHÃN TRỤC DÙNG `ink2` CHỨ KHÔNG PHẢI `ink3` (lệch có chủ đích so với câu
 * "trục = ink3" khi giao việc): `--lab-ink-3` `#687486` đo được **3,05:1** trên mặt card
 * `#202A3A` (và 4,10:1 trên nền `#080D17`) — dưới ngưỡng **4,5:1** mà ADR-009 §5 đặt cho MỌI
 * cặp chữ/nền, và §7 cũng ghi rõ ink3 chỉ dùng cho "chữ ≥ 14px đậm hoặc nhãn phụ". Nhãn số
 * của trục là chữ 12sp thường ⇒ dùng `ink2` `#929CAB` (5,20:1 trên card). `ink3` vẫn được
 * dùng đúng vai của nó: chữ **disabled** (`ui/kit/LabInput.kt`) — WCAG 1.4.3 miễn trừ chữ
 * của thành phần không hoạt động.
 * =====================================================================================
 */

/** Một lát của donut — luôn kèm nhãn CHỮ (spec §5: màu không bao giờ là tín hiệu duy nhất). */
data class ChartSlice(val label: String, val value: Int, val color: Color)

/**
 * Sparkline (polyline + chấm cuối) trong KPI card. Không trục, không nhãn.
 * < 2 điểm ⇒ không vẽ (spec §3: không có chuỗi thời gian thật thì bỏ sparkline).
 */
@Composable
fun Sparkline(
    points: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.lab.chart1,
) {
    if (points.size < 2) return
    val min = points.minOrNull() ?: 0f
    val max = points.maxOrNull() ?: 0f
    val span = (max - min).takeIf { it > 0f } ?: 1f
    Canvas(modifier.fillMaxWidth().height(SPARKLINE_HEIGHT)) {
        val stepX = size.width / (points.size - 1).toFloat()
        val usable = (size.height - STROKE * 2f).coerceAtLeast(1f)
        fun pointAt(index: Int): Offset = Offset(
            x = stepX * index,
            y = size.height - STROKE - ((points[index] - min) / span) * usable,
        )
        val line = Path().apply {
            val first = pointAt(0)
            moveTo(first.x, first.y)
            for (index in 1 until points.size) {
                val p = pointAt(index)
                lineTo(p.x, p.y)
            }
        }
        drawPath(path = line, color = color, style = Stroke(width = STROKE, cap = StrokeCap.Round))
        drawCircle(color = color, radius = STROKE * 1.6f, center = pointAt(points.lastIndex))
    }
}

/**
 * Area chart có TRỤC cho xu hướng theo ngày (1–2 series).
 *
 * @param labels nhãn ngày cho từng điểm (đã format `dd/MM`).
 * @param series mỗi chuỗi giá trị, cùng độ dài [labels].
 * @param seriesNames tên series cho legend.
 * @param unit đơn vị trục (spec §4 đòi trục có đơn vị) — hiện dưới chart.
 * @param chartHeight chiều cao vùng vẽ; điện thoại dùng 160 (spec §6).
 */
@Composable
fun AreaTrendChart(
    labels: List<String>,
    series: List<List<Float>>,
    seriesNames: List<String>,
    unit: String,
    modifier: Modifier = Modifier,
    chartHeight: Int = 180,
    // Thứ tự series theo ĐÚNG bảng token: c1 cho series chính, c2 cho series so sánh.
    colors: List<Color> = listOf(AppTheme.lab.chart1, AppTheme.lab.chart2),
) {
    // CompositionLocal chỉ đọc được trong ngữ cảnh @Composable — Canvas { } là DrawScope
    // (không phải composable), nên phải đọc palette ở đây rồi dùng lại bên trong.
    val palette = AppTheme.lab
    val maxValue = series.flatMap { it }.maxOrNull() ?: 0f
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            // Trục Y: 3 mốc (max · giữa · 0) bằng `Text` thật ⇒ luôn cùng typography với app.
            Column(
                modifier = Modifier.width(Y_AXIS_WIDTH).height(chartHeight.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text(formatViInt(maxValue.toInt()), style = MaterialTheme.typography.caption, color = AppTheme.lab.ink2)
                Text(formatViInt((maxValue / 2f).toInt()), style = MaterialTheme.typography.caption, color = AppTheme.lab.ink2)
                Text("0", style = MaterialTheme.typography.caption, color = AppTheme.lab.ink2)
            }
            Spacer(Modifier.width(Tokens.s2))
            Canvas(Modifier.weight(1f).height(chartHeight.dp)) {
                // Lưới ngang 3 mức — `--lab-grid` rgba(255,255,255,.06): mảnh hơn cả `line`,
                // vì lưới là thông tin NỀN, không được tranh mắt với dữ liệu.
                repeat(3) { level ->
                    val y = size.height * level / 2f
                    drawLine(color = palette.grid, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
                }
                val safeMax = if (maxValue <= 0f) 1f else maxValue
                series.forEachIndexed { seriesIndex, values ->
                    if (values.size < 2) return@forEachIndexed
                    val stepX = size.width / (values.size - 1).toFloat()
                    fun pointAt(index: Int): Offset = Offset(
                        x = stepX * index,
                        y = size.height - (values[index] / safeMax) * size.height,
                    )
                    val first = pointAt(0)
                    val line = Path().apply {
                        moveTo(first.x, first.y)
                        for (index in 1 until values.size) {
                            val p = pointAt(index)
                            lineTo(p.x, p.y)
                        }
                    }
                    val color = colors[seriesIndex % colors.size]
                    // Chỉ series chính được tô vùng (area); series phụ là đường tham chiếu.
                    if (seriesIndex == 0) {
                        val area = Path().apply {
                            moveTo(first.x, size.height)
                            lineTo(first.x, first.y)
                            for (index in 1 until values.size) {
                                val p = pointAt(index)
                                lineTo(p.x, p.y)
                            }
                            lineTo(pointAt(values.lastIndex).x, size.height)
                            close()
                        }
                        drawPath(
                            path = area,
                            brush = Brush.verticalGradient(
                                colors = listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.02f)),
                            ),
                        )
                    }
                    drawPath(
                        path = line,
                        color = color,
                        style = Stroke(width = if (seriesIndex == 0) 2.4f else 1.6f, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Spacer(Modifier.height(Tokens.s1))
        // Nhãn trục X: đầu / giữa / cuối — không nhồi 30 nhãn vào 320dp.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Y_AXIS_WIDTH + Tokens.s2),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOfNotNull(
                labels.firstOrNull(),
                labels.getOrNull(labels.size / 2),
                labels.lastOrNull(),
            ).distinct().forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.caption,
                    color = AppTheme.lab.ink2,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Spacer(Modifier.height(Tokens.s1))
        Row(Modifier.fillMaxWidth().padding(start = Y_AXIS_WIDTH + Tokens.s2)) {
            ChartLegend(names = seriesNames, colors = colors)
        }
        Spacer(Modifier.height(Tokens.s1))
        Text(
            text = "Đơn vị: $unit",
            style = MaterialTheme.typography.caption,
            color = AppTheme.lab.ink2,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Donut một vòng (không 3D, không nổ) + **legend chữ kèm số và %**.
 *
 * @param diameter đường kính vòng vẽ (dp).
 * @param centerTitle dòng phụ trong tâm (vd "việc").
 * @param centerValue số lớn trong tâm (tổng số việc).
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    centerTitle: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    diameter: Int = 148,
) {
    val total = slices.sumOf { it.value }
    if (total <= 0) return
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(diameter.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(diameter.dp)) {
                var startAngle = -90f
                val ringWidth = size.minDimension * DONUT_STROKE_RATIO
                slices.forEach { slice ->
                    val sweep = 360f * slice.value / total.toFloat()
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = ringWidth),
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = centerValue, style = MaterialTheme.typography.h5, color = AppTheme.lab.ink)
                Text(
                    text = centerTitle,
                    style = MaterialTheme.typography.caption,
                    color = AppTheme.lab.ink2,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Spacer(Modifier.width(Tokens.s3))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tokens.s1)) {
            slices.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(LEGEND_DOT)
                            .clip(RoundedCornerShape(2.dp))
                            .background(slice.color),
                    )
                    Spacer(Modifier.width(Tokens.s2))
                    Text(
                        text = slice.label,
                        style = MaterialTheme.typography.body2,
                        color = AppTheme.lab.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${formatViInt(slice.value)} · ${formatViInt(slice.value * 100 / total)}%",
                        style = MaterialTheme.typography.caption,
                        color = AppTheme.lab.ink2,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** Legend chữ của chart đường (chấm màu + tên series). */
@Composable
fun ChartLegend(
    names: List<String>,
    colors: List<Color> = listOf(AppTheme.lab.chart1, AppTheme.lab.chart2),
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.s3)) {
        names.forEachIndexed { index, name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(LEGEND_DOT)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors[index % colors.size]),
                )
                Spacer(Modifier.width(Tokens.s1))
                Text(
                    text = name,
                    style = MaterialTheme.typography.caption,
                    color = AppTheme.lab.ink2,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** Empty state của một khối chart — spec §4: "Chưa có dữ liệu" thay vì chart 0. */
@Composable
fun ChartEmptyState(message: String, modifier: Modifier = Modifier, height: Int = 180) {
    Box(
        modifier = modifier.fillMaxWidth().height(height.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.body2,
            color = AppTheme.lab.ink2,
        )
    }
}

// ------------------------------------------------------------------------------- hằng số
private val SPARKLINE_HEIGHT = 40.dp
private val Y_AXIS_WIDTH = 34.dp
private val LEGEND_DOT = 8.dp
private const val STROKE = 1.6f
private const val DONUT_STROKE_RATIO = 0.18f
