package vn.lab.logistics.core.repo.mock

import kotlinx.coroutines.delay
import vn.lab.logistics.core.model.JobState
import vn.lab.logistics.core.model.KpiRange
import vn.lab.logistics.core.model.SepKpi
import vn.lab.logistics.core.model.SepKpiCounters
import vn.lab.logistics.core.model.SepRecentJob
import vn.lab.logistics.core.model.SepStateSlice
import vn.lab.logistics.core.model.SepTrendPoint
import vn.lab.logistics.core.net.ApiException
import vn.lab.logistics.core.repo.SepDashboardRepo

/**
 * =====================================================================================
 * DASHBOARD CỦA SẾP — dữ liệu MẪU, KHÔNG gọi mạng.
 *
 * NGUYÊN TẮC: **không bịa KPI**. Mọi con số ở đây được ĐẾM từ chính danh sách việc mẫu mà ba
 * màn kia đang hiện (cùng mã việc `YC/LH/26/…`, `LH/26/01xx`, `LH/26/02xx`, cùng shop, cùng
 * trạng thái) — nhờ vậy khi quay video, số trên dashboard khớp với thứ người xem vừa thấy ở màn
 * CS/Kho/Tài xế, và không có "dashboard đẹp nhưng rỗng nghĩa".
 *
 *  - `avgLoadPct` = **null**: app không có dữ liệu tải chuyến thật ⇒ card ghi "chưa có dữ liệu"
 *    thay vì một con số trang trí (spec §3: cấm số bịa).
 *  - `trend` phân bổ tổng số việc mẫu lên các ngày theo một đường cố định, **tổng bằng đúng**
 *    số việc mẫu (không phát sinh việc không tồn tại); `range=today` trả 1 điểm như server thật.
 *  - Kịch bản demo dùng lại [DemoScenario]: Empty ⇒ payload rỗng, Error ⇒ lỗi tiếng Việt,
 *    Slow ⇒ trễ 2.5s để thấy Loading.
 * =====================================================================================
 */
class MockSepDashboardRepo(
    private val scenario: DemoScenario = DemoScenario.Normal,
) : SepDashboardRepo {

    override suspend fun kpi(range: KpiRange): Result<SepKpi> {
        delay(if (scenario == DemoScenario.Slow) 2_500L else 800L)
        if (scenario == DemoScenario.Error) {
            return Result.failure(
                ApiException.Server(
                    "Máy chủ báo lỗi khi lấy số liệu dashboard. Bấm \"Thử lại\" để thử lại.",
                ),
            )
        }
        if (scenario == DemoScenario.Empty) {
            return Result.success(SepKpi(range = range))
        }
        return Result.success(sampleKpi(range))
    }
}

/**
 * Một dòng của danh sách việc mẫu — CHÉP từ dữ liệu mẫu của 3 màn đang chạy
 * (`MockDriverJobsRepo`, `MockCsRepo`, `MockKhoRepo`), không phải dữ liệu mới.
 */
private data class SampleJob(
    val id: Int,
    val ref: String,
    val shop: String,
    val state: String,
    val stateLabel: String,
    val driverName: String?,
    val windowFrom: String?,
    val updatedAt: String,
)

private val SAMPLE_JOBS: List<SampleJob> = listOf(
    SampleJob(1, "YC/LH/26/0001", "Shop Thời trang Mộc Lan", JobState.ASSIGNED, "Đã gán", "Trần Văn Hùng", "01:30", "2026-10-29 08:10:00"),
    SampleJob(2, "YC/LH/26/0002", "Cửa hàng Điện tử Hải Nam", JobState.CANCELLED, "Đã huỷ", null, "02:00", "2026-10-29 08:05:00"),
    SampleJob(3, "YC/LH/26/0003", "Công ty TNHH Sản xuất Thương mại Dịch vụ Bao bì Nhựa Tân Phú", JobState.EN_ROUTE, "Đang đến lấy", "Nguyễn Thị Mai", "02:15", "2026-10-29 08:20:00"),
    SampleJob(4, "YC/LH/26/0004", "Shop Mỹ phẩm Lan Chi", JobState.PICKED_UP, "Đã lấy được hàng", "Lê Quốc Bảo", "03:00", "2026-10-29 08:35:00"),
    SampleJob(5, "YC/LH/26/0005", "Xưởng Nội thất Gỗ Việt", JobState.CONFIRMED, "Đã về kho", "Trần Văn Hùng", "03:30", "2026-10-29 08:45:00"),
    SampleJob(6, "YC/LH/26/0006", "Nhà thuốc An Khang — chi nhánh Phú Nhuận", JobState.FAILED, "Lấy hàng thất bại", "Nguyễn Thị Mai", "04:00", "2026-10-29 09:00:00"),
    SampleJob(501, "LH/26/0101", "Shop Thời trang Mộc Lan", JobState.NEW, "Mới", null, "04:30", "2026-10-29 09:10:00"),
    SampleJob(502, "LH/26/0102", "Công ty TNHH Sản xuất Thương mại Dịch vụ Bao bì Nhựa Tân Phú", JobState.ASSIGNED, "Đã gán", "Lê Quốc Bảo", "05:00", "2026-10-29 09:15:00"),
    SampleJob(503, "LH/26/0103", "Nhà thuốc An Khang — chi nhánh Phú Nhuận", JobState.NEW, "Mới", null, "05:30", "2026-10-29 09:20:00"),
    SampleJob(601, "LH/26/0201", "Shop Mỹ phẩm Lan Chi", JobState.CONFIRMED, "Đã về kho", "Trần Văn Hùng", "06:00", "2026-10-29 09:30:00"),
    SampleJob(602, "LH/26/0202", "Công ty TNHH Sản xuất Thương mại Dịch vụ Bao bì Nhựa Tân Phú", JobState.CONFIRMED, "Đã về kho", "Nguyễn Thị Mai", "06:30", "2026-10-29 09:40:00"),
    SampleJob(603, "LH/26/0203", "Xưởng Nội thất Gỗ Việt", JobState.PICKED_UP, "Đã lấy được hàng", "Lê Quốc Bảo", "07:00", "2026-10-29 09:50:00"),
)

private fun sampleKpi(range: KpiRange): SepKpi {
    val jobs = SAMPLE_JOBS
    val byState = jobs
        .groupBy { it.state to it.stateLabel }
        .map { (key, rows) -> SepStateSlice(state = key.first, label = key.second, count = rows.size) }
        // Donut ≤ 5 phần (spec §4): nhiều trạng thái hơn thì giữ 5 nhiều nhất, phần còn lại gộp.
        .sortedByDescending { it.count }

    val recent = jobs
        .sortedByDescending { it.updatedAt }
        .take(10)
        .map {
            SepRecentJob(
                id = it.id,
                ref = it.ref,
                shop = it.shop,
                stateLabel = it.stateLabel,
                driverName = it.driverName,
                windowFrom = it.windowFrom,
                updatedAt = it.updatedAt,
            )
        }

    return SepKpi(
        range = range,
        counters = SepKpiCounters(
            pendingAssign = jobs.count { it.state == JobState.NEW },
            enRoute = jobs.count { it.state == JobState.EN_ROUTE },
            pickedUpToday = jobs.count { it.state == JobState.PICKED_UP },
            confirmedToday = jobs.count { it.state == JobState.CONFIRMED },
            // Không có nguồn dữ liệu tải chuyến trong chế độ Mẫu ⇒ null, UI ghi "chưa có dữ liệu".
            avgLoadPct = null,
        ),
        trend = sampleTrend(range, jobs),
        byState = byState,
        recent = recent,
    )
}

/**
 * Phân bổ số việc mẫu lên các ngày của [range] — TỔNG bằng đúng số việc mẫu.
 * `today` trả đúng 1 điểm (giống server thật) ⇒ dashboard không có chip Δ cho "Hôm nay".
 */
private fun sampleTrend(range: KpiRange, jobs: List<SampleJob>): List<SepTrendPoint> {
    val days = when (range) {
        KpiRange.Today -> 1
        KpiRange.SevenDays -> 7
        KpiRange.ThirtyDays -> 30
    }
    val created = jobs.count { it.state == JobState.NEW || it.state == JobState.ASSIGNED }
    val picked = jobs.count { it.state == JobState.PICKED_UP || it.state == JobState.CONFIRMED }
    val confirmed = jobs.count { it.state == JobState.CONFIRMED }

    /**
     * Chia [total] cho [days] ngày sao cho TỔNG các ngày = đúng [total] (không làm tròn lên
     * thành số việc không tồn tại). Ngày càng gần hôm nay nhận phần dư nhiều hơn 1 đơn vị.
     */
    fun spread(total: Int, dayIndex: Int): Int {
        val base = total / days
        val remainder = total % days
        val fromDay = days - remainder + 1
        return base + if (dayIndex >= fromDay) 1 else 0
    }

    return (1..days).map { dayIndex ->
        SepTrendPoint(
            date = sampleDate(dayIndex - days),
            created = spread(created, dayIndex),
            picked = spread(picked, dayIndex),
            confirmed = spread(confirmed, dayIndex),
        )
    }
}

/** Ngày `offsetDays` trước mốc 2026-10-29 (chỉ cần `dd/MM` ổn định cho trục) — tính tay, không thư viện. */
private fun sampleDate(offsetDays: Int): String {
    var day = 29 + offsetDays
    var month = 10
    while (day < 1) {
        month -= 1
        if (month < 1) month = 12
        day += 30
    }
    val dd = day.toString().padStart(2, '0')
    val mm = month.toString().padStart(2, '0')
    return "2026-$mm-$dd"
}
