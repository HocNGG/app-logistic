package vn.lab.logistics.core.repo

import vn.lab.logistics.core.model.KpiRange
import vn.lab.logistics.core.model.SepKpi

/**
 * Hợp đồng của màn **Dashboard của sếp** (`manager` / `sep`).
 *
 * | Hàm | Endpoint | Ý nghĩa |
 * |---|---|---|
 * | [kpi] | `GET /lab/api/v1/sep/kpi?range=today\|7d\|30d` | 5 KPI + xu hướng theo ngày + cơ cấu trạng thái + 10 việc gần nhất |
 *
 * Màn hình chỉ phụ thuộc interface này; hai impl được chọn ở nơi lắp ráp (`App.kt`) theo công tắc
 * nguồn dữ liệu — KHÔNG có `if (demo)` trong UI:
 *  - [vn.lab.logistics.core.repo.mock.MockSepDashboardRepo] — dựng từ **chính dữ liệu mẫu** mà
 *    màn tài xế/CS/kho đang hiện, không bịa KPI;
 *  - [vn.lab.logistics.core.repo.real.RealSepDashboardRepo] — gọi THẬT (cookie + `X-Lab-Token`).
 */
interface SepDashboardRepo {

    /**
     * Số liệu cho khoảng thời gian [range].
     *
     * Lỗi được trả qua `Result.failure` mang câu tiếng Việt (như các repo khác) ⇒ ViewModel
     * không có `try/catch` nào.
     */
    suspend fun kpi(range: KpiRange): Result<SepKpi>
}
