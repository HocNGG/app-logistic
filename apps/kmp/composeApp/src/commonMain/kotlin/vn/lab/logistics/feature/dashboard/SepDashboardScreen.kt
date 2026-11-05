package vn.lab.logistics.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.lab.logistics.core.model.KpiRange
import vn.lab.logistics.core.model.SepKpi
import vn.lab.logistics.core.model.SepRecentJob
import vn.lab.logistics.core.model.formatAxisDate
import vn.lab.logistics.core.model.formatDateTimeVi
import vn.lab.logistics.core.model.formatViInt
import vn.lab.logistics.core.model.formatViPercent
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Lab
import vn.lab.logistics.design.Tokens
import vn.lab.logistics.ui.components.CalloutBox
import vn.lab.logistics.ui.components.Tone
import vn.lab.logistics.ui.components.UiState
import vn.lab.logistics.ui.components.charts.AreaTrendChart
import vn.lab.logistics.ui.components.charts.ChartEmptyState
import vn.lab.logistics.ui.components.charts.ChartSlice
import vn.lab.logistics.ui.components.charts.DonutChart
import vn.lab.logistics.ui.components.charts.Sparkline
import vn.lab.logistics.ui.kit.LabCard
import vn.lab.logistics.ui.kit.LabDelta
import vn.lab.logistics.ui.kit.LabEnter
import vn.lab.logistics.ui.kit.LabFilterChip
import vn.lab.logistics.ui.kit.LabHeaderCell
import vn.lab.logistics.ui.kit.LabKpiCard
import vn.lab.logistics.ui.kit.LabNumCell
import vn.lab.logistics.ui.kit.LabRowDivider
import vn.lab.logistics.ui.kit.LabStatusChip
import vn.lab.logistics.ui.kit.LabTable
import vn.lab.logistics.ui.kit.LabTableRow
import vn.lab.logistics.ui.kit.LabToolbar
import vn.lab.logistics.ui.kit.labToneOfStatusLabel

/**
 * =====================================================================================
 * MÀN **SẾP · TỔNG QUAN** (`manager` / `sep`) — spec `ui-dashboard-app-screens.md` §3–§6.
 *
 * Bố cục (đúng thứ tự spec, không tự thêm khối):
 *   [toolbar] tiêu đề + chip khoảng thời gian (Hôm nay · 7 ngày · 30 ngày) + xuất CSV (chưa hỗ trợ)
 *   [4 KPI]   Việc chờ gán · Đang lấy hàng · Đã về kho hôm nay · Tải chuyến trung bình
 *   [2 chart] trái: xu hướng việc theo ngày (area) · phải: cơ cấu trạng thái (donut)
 *   [bảng]    10 việc gần nhất (điện thoại: chỉ 3 cột mã việc · shop · chip trạng thái)
 *
 * NGUYÊN TẮC SỐ LIỆU (Luật 6 + spec §3, §7) — không đổi sau khi làm lại UI:
 *  - Không có số thì ghi "Chưa có dữ liệu" — không hiện 0 giả, không vẽ chart rỗng.
 *  - Chip Δ chỉ hiện khi tính được từ chuỗi thời gian thật (`SepKpi.confirmedDelta`).
 *  - Mọi trạng thái việc là **chip có CHỮ** tiếng Việt, màu chỉ là phụ (spec §5).
 *
 * GIAO DIỆN (đã làm lại): dùng `AppTheme.lab` thay vì hard-code `Brand` — trước đây màn này luôn
 * tối kể cả khi app đang ở chế độ sáng, lệch hẳn 3 màn còn lại. KPI/chart/bảng đều qua bộ kit:
 * [LabKpiCard] (nhãn 13sp + số 32sp tabular + chip Δ + sparkline 40dp), [LabCard], [LabTable]
 * (hàng ≥ 44dp, chỉ kẻ ngang, số căn phải).
 * =====================================================================================
 */
@Composable
fun SepDashboardScreen(
    vm: SepDashboardViewModel,
    widthDp: Int,
    dataSourceLabel: String,
    onExitToLogin: () -> Unit,
    onFallbackToSample: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    // Mở màn là tải số liệu của kỳ đang chọn (giống các màn khác: dữ liệu đến sau, có Loading).
    LaunchedEffect(vm) { vm.load(scope) }

    BoxWithConstraints(modifier.fillMaxSize().background(AppTheme.lab.ground)) {
        val compact = maxWidth.value < Tokens.BREAKPOINT_COMPACT_DP
        Column(Modifier.fillMaxSize()) {
            LabToolbar(
                title = "Tổng quan",
                subtitle = "Nguồn: $dataSourceLabel · $widthDp dp",
                stacked = compact,
                filters = {
                    KpiRange.entries.forEach { item ->
                        LabFilterChip(
                            text = item.label,
                            selected = item == vm.range,
                            onClick = { vm.selectRange(next = item, scope = scope) },
                        )
                    }
                },
                actions = {
                    // Xuất CSV: BE chưa có endpoint ⇒ nút BỊ KHOÁ + nói rõ lý do (không nút chết
                    // im lặng).
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.height(Tokens.touchSecondary),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = AppTheme.lab.panel,
                            contentColor = AppTheme.lab.ink2,
                            disabledBackgroundColor = AppTheme.lab.panel,
                            disabledContentColor = AppTheme.lab.ink2,
                        ),
                    ) {
                        Text("Xuất CSV", maxLines = 1, softWrap = false)
                    }
                    TextButton(
                        onClick = onExitToLogin,
                        modifier = Modifier.height(Tokens.touchSecondary),
                    ) {
                        Text(
                            text = "Đăng xuất",
                            style = MaterialTheme.typography.body2,
                            color = AppTheme.lab.accent,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                },
            )

            val state = vm.state
            if (state is UiState.Loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AppTheme.lab.accent,
                    backgroundColor = AppTheme.lab.panel,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Tokens.s5),
            ) {
                val error = state as? UiState.Error
                if (error != null) {
                    CalloutBox(
                        title = "Không tải được số liệu",
                        body = error.message,
                        tone = Tone.Bad,
                        actionLabel = "Thử lại",
                        onAction = { vm.load(scope) },
                    )
                    if (onFallbackToSample != null) {
                        Spacer(Modifier.height(Tokens.s2))
                        TextButton(onClick = onFallbackToSample) {
                            Text("Về dữ liệu mẫu", color = AppTheme.lab.accent)
                        }
                    }
                    Spacer(Modifier.height(Tokens.s4))
                }

                // `UiState.Content<T>` là generic; ở đây state khai báo dạng UiState<*> nên
                // `.data` suy ra `Any`. Phải ép về đúng kiểu dữ liệu của màn này.
                val data = (state as? UiState.Content<*>)?.data as? SepKpi
                if (data == null) {
                    if (error == null) {
                        ChartEmptyState(message = "Chưa có dữ liệu", height = 120)
                    }
                    return@Column
                }

                LabEnter {
                    KpiGrid(kpi = data, compact = compact)
                    Spacer(Modifier.height(Tokens.s4))
                    ChartRow(kpi = data, compact = compact)
                    Spacer(Modifier.height(Tokens.s4))
                    RecentJobsTable(jobs = data.recent, compact = compact)
                    Spacer(Modifier.height(Tokens.s5))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// 4 KPI card (spec §3) — 2×2 khi hẹp
// ---------------------------------------------------------------------------------------
@Composable
private fun KpiGrid(kpi: SepKpi, compact: Boolean) {
    val counters = kpi.counters
    val trendConfirmed = kpi.trend.map { it.confirmed.toFloat() }
    val trendCreated = kpi.trend.map { it.created.toFloat() }
    val cards: List<@Composable (Modifier) -> Unit> = listOf(
        { m ->
            LabKpiCard(
                label = "Việc chờ gán",
                value = counters?.pendingAssign?.let { formatViInt(it) },
                sparkline = if (trendCreated.size >= 2) trendCreated else emptyList(),
                modifier = m,
            )
        },
        { m ->
            LabKpiCard(
                label = "Đang lấy hàng",
                value = counters?.enRoute?.let { formatViInt(it) },
                modifier = m,
            )
        },
        { m ->
            LabKpiCard(
                label = "Đã về kho hôm nay",
                value = counters?.confirmedToday?.let { formatViInt(it) },
                // Δ chỉ khi có mốc thật (≥ 2 điểm trong chuỗi thời gian) — spec §3.
                delta = kpi.confirmedDelta()?.let { delta ->
                    LabDelta(
                        text = if (delta >= 0) {
                            "+${formatViInt(delta)} so với hôm qua"
                        } else {
                            "${formatViInt(delta)} so với hôm qua"
                        },
                        tone = if (delta >= 0) Tone.Ok else Tone.Warn,
                    )
                },
                sparkline = trendConfirmed,
                modifier = m,
            )
        },
        { m ->
            LabKpiCard(
                label = "Tải chuyến trung bình",
                value = counters?.avgLoadPct?.let { formatViPercent(it) },
                hint = "chưa có dữ liệu tải chuyến",
                modifier = m,
            )
        },
    )
    val rows = if (compact) cards.chunked(2) else listOf(cards)
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.s3)) {
        rows.forEach { rowCards ->
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.s3)) {
                rowCards.forEach { card ->
                    Box(Modifier.weight(1f)) { card(Modifier.fillMaxWidth()) }
                }
                // Hàng lẻ còn 1 card ⇒ giữ chỗ để 2 card không bị kéo giãn khác nhau.
                if (rowCards.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// 2 chart (spec §4)
// ---------------------------------------------------------------------------------------
@Composable
private fun ChartRow(kpi: SepKpi, compact: Boolean) {
    val trendCard: @Composable (Modifier) -> Unit = { m -> TrendCard(kpi = kpi, compact = compact, modifier = m) }
    val donutCard: @Composable (Modifier) -> Unit = { m -> DonutCard(kpi = kpi, compact = compact, modifier = m) }
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.s3)) {
            trendCard(Modifier.fillMaxWidth())
            donutCard(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.s3)) {
            Box(Modifier.weight(1.6f)) { trendCard(Modifier.fillMaxWidth()) }
            Box(Modifier.weight(1f)) { donutCard(Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun TrendCard(kpi: SepKpi, compact: Boolean, modifier: Modifier = Modifier) {
    LabCard(
        modifier = modifier,
        title = "Xu hướng việc theo ngày",
        subtitle = "Việc tạo · Đã lấy, đơn vị: việc/ngày",
    ) {
        if (kpi.trend.size < 2) {
            ChartEmptyState(
                message = "Chưa có dữ liệu xu hướng cho kỳ này",
                height = if (compact) 160 else 180,
            )
        } else {
            AreaTrendChart(
                labels = kpi.trend.map { formatAxisDate(it.date) },
                series = listOf(
                    kpi.trend.map { it.created.toFloat() },
                    kpi.trend.map { it.picked.toFloat() },
                ),
                seriesNames = listOf("Việc tạo", "Đã lấy"),
                unit = "việc/ngày",
                chartHeight = if (compact) 160 else 180,
            )
        }
    }
}

@Composable
private fun DonutCard(kpi: SepKpi, compact: Boolean, modifier: Modifier = Modifier) {
    val palette = AppTheme.lab.donut
    LabCard(
        modifier = modifier,
        title = "Cơ cấu trạng thái",
        subtitle = "Tối đa 4 trạng thái lớn nhất, phần còn lại gộp \"Khác\"",
    ) {
        val slices = aggregateSlices(kpi)
        if (slices.isEmpty() || kpi.stateTotal <= 0) {
            ChartEmptyState(
                message = "Chưa có dữ liệu trạng thái",
                height = if (compact) 160 else 180,
            )
        } else {
            DonutChart(
                slices = slices.mapIndexed { index, slice ->
                    ChartSlice(
                        label = slice.label,
                        value = slice.count,
                        color = if (index < palette.size) {
                            palette[index]
                        } else {
                            AppTheme.lab.chartRest
                        },
                    )
                },
                centerTitle = "việc",
                centerValue = formatViInt(kpi.stateTotal),
                diameter = if (compact) 132 else 148,
            )
        }
    }
}

/** Một lát donut sau khi gộp. */
private data class SliceAgg(val label: String, val count: Int)

/**
 * Gộp `byState` thành ≤ 5 lát: 4 trạng thái lớn nhất + "Khác".
 * Spec §4: donut chỉ 1 vòng, ≤ 5 phần, cùng một họ màu — không cầu vồng.
 */
private fun aggregateSlices(kpi: SepKpi): List<SliceAgg> {
    val sorted = kpi.byState.sortedByDescending { it.count }
    if (sorted.size <= MAX_DONUT_SLICES) {
        return sorted.map { SliceAgg(it.label, it.count) }
    }
    val head = sorted.take(MAX_DONUT_SLICES - 1).map { SliceAgg(it.label, it.count) }
    val rest = sorted.drop(MAX_DONUT_SLICES - 1).sumOf { it.count }
    return head + SliceAgg("Khác", rest)
}

// ---------------------------------------------------------------------------------------
// Bảng 10 việc gần nhất (spec §5, §6)
// ---------------------------------------------------------------------------------------
@Composable
private fun RecentJobsTable(jobs: List<SepRecentJob>, compact: Boolean) {
    LabCard(
        modifier = Modifier.fillMaxWidth(),
        title = "10 việc gần nhất",
        subtitle = if (compact) "Điện thoại: mã việc · shop · trạng thái" else null,
        padding = androidx.compose.foundation.layout.PaddingValues(Tokens.borderNone),
    ) {
        if (jobs.isEmpty()) {
            ChartEmptyState(message = "Chưa có việc nào trong kỳ này", height = 96)
            return@LabCard
        }
        LabTable(
            modifier = Modifier.fillMaxWidth(),
            bordered = false,
            header = {
                if (compact) {
                    LabHeaderCell("Mã việc", weight = 1.2f)
                    LabHeaderCell("Shop", weight = 1.6f)
                    LabHeaderCell("Trạng thái", weight = 1.2f, alignEnd = true)
                } else {
                    LabHeaderCell("Mã việc", weight = 0.9f)
                    LabHeaderCell("Shop", weight = 1.5f)
                    LabHeaderCell("Tài xế", weight = 0.9f)
                    LabHeaderCell("Khung giờ", weight = 0.7f)
                    LabHeaderCell("Cập nhật", weight = 1.0f)
                    LabHeaderCell("Trạng thái", weight = 1.0f, alignEnd = true)
                }
            },
        ) {
            jobs.forEachIndexed { index, job ->
                RecentJobRow(job = job, compact = compact)
                if (index != jobs.lastIndex) LabRowDivider()
            }
        }
    }
}

@Composable
private fun RecentJobRow(job: SepRecentJob, compact: Boolean) {
    // Hàng focus được (bàn phím đi hết bảng) + viền focus nhìn thấy rõ — a11y, spec §6.
    var focused by remember { mutableStateOf(false) }
    LabTableRow(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) Tokens.focusRing else Tokens.borderNone,
                color = AppTheme.lab.accent,
                shape = RoundedCornerShape(Tokens.radiusChip),
            )
            .focusable(),
    ) {
        Text(
            text = job.ref,
            style = MaterialTheme.typography.body2,
            color = AppTheme.lab.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(if (compact) 1.2f else 0.9f),
        )
        Text(
            text = job.shop,
            style = MaterialTheme.typography.body2,
            color = AppTheme.lab.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(if (compact) 1.6f else 1.5f).padding(horizontal = Tokens.s2),
        )
        if (!compact) {
            Text(
                text = job.driverName ?: "—",
                style = MaterialTheme.typography.body2,
                color = AppTheme.lab.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.9f),
            )
            Text(
                text = job.windowFrom ?: "—",
                style = MaterialTheme.typography.body2,
                color = AppTheme.lab.ink2,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(0.7f),
            )
            LabNumCell(
                text = formatDateTimeVi(job.updatedAt) ?: "—",
                weight = 1.0f,
                color = AppTheme.lab.ink2,
            )
        }
        Box(
            Modifier.weight(if (compact) 1.2f else 1.0f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            LabStatusChip(label = job.stateLabel)
        }
    }
}

/** Donut ≤ 5 phần, cùng một họ màu (spec §2, §4). */
private const val MAX_DONUT_SLICES = 5

/**
 * Nhãn trạng thái (tiếng Việt, server trả) → [Tone] để tô chip của bảng "việc gần nhất".
 *
 * GIỮ NGUYÊN TÊN + HÀNH VI vì `SepKpiTest` khoá nó (test không được đổi — hợp đồng của dự án).
 * Nay thân hàm chỉ chuyển tiếp sang bảng DUY NHẤT ở bộ kit (`labToneOfStatusLabel`), nhờ vậy
 * dashboard và 3 màn làm việc không còn tô hai màu khác nhau cho cùng một việc.
 */
internal fun toneOfStateLabel(label: String): Tone = labToneOfStatusLabel(label)
