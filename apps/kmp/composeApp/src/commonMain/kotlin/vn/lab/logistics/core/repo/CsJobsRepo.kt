package vn.lab.logistics.core.repo

import vn.lab.logistics.core.model.AssignOutcome
import vn.lab.logistics.core.model.CsJobsPayload

/**
 * Hợp đồng của màn **CS — điều phối** (2 endpoint, đọc theo hợp đồng đã chốt với BE-01):
 *
 * | Hàm | Endpoint | Ý nghĩa |
 * |---|---|---|
 * | [jobs] | `GET /lab/api/v1/cs/jobs` | việc cần gán tài xế + danh sách tài xế để chọn |
 * | [assign] | `POST /lab/api/v1/cs/jobs/<id>/assign` | gán tài xế, server trả state mới |
 *
 * Màn hình chỉ phụ thuộc interface này; hai impl được chọn ở nơi lắp ráp (`App.kt`) theo công
 * tắc nguồn dữ liệu, KHÔNG có `if (demo)` trong UI:
 *  - [vn.lab.logistics.core.repo.mock.MockCsRepo] — dữ liệu MẪU, không gọi mạng;
 *  - [vn.lab.logistics.core.repo.real.RealCsRepo] — gọi THẬT (cookie phiên + `X-Lab-Token`).
 *
 * App KHÔNG tự đổi trạng thái việc: state mới luôn nằm trong [AssignOutcome] do server trả.
 */
interface CsJobsRepo {

    /** Việc cần gán tài xế + danh sách tài xế. */
    suspend fun jobs(): Result<CsJobsPayload>

    /** Gán tài xế `driverId` cho việc `jobId`. */
    suspend fun assign(jobId: Int, driverId: Int): Result<AssignOutcome>
}
