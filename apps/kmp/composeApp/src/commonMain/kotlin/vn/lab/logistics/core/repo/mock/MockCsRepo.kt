package vn.lab.logistics.core.repo.mock

import kotlinx.coroutines.delay
import vn.lab.logistics.core.model.AssignOutcome
import vn.lab.logistics.core.model.CsDriver
import vn.lab.logistics.core.model.CsJob
import vn.lab.logistics.core.model.CsJobsPayload
import vn.lab.logistics.core.model.JobState
import vn.lab.logistics.core.repo.CsJobsRepo

/**
 * ⚠️ IMPL GIẢ LẬP — dữ liệu MẪU cho màn CS, không gọi mạng (Luật 8: demo chạy được cả khi
 * Odoo chưa bật). Đây là "flag bật mock" đúng luật: mock nằm sau interface [CsJobsRepo] và chỉ
 * được chọn ở nơi lắp ráp (`App.kt`), KHÔNG rải cờ trong màn hình.
 *
 * Dữ liệu bám nghiệp vụ thật (docs/00): shop nhỏ lẻ, khung giờ hẹn, số kiện dự kiến, và 3 việc
 * phủ 3 tình huống CS hay gặp:
 *  1. việc MỚI, chưa có tài xế (cần gán — cảnh chính của màn);
 *  2. việc đã gán tài xế (CS đổi tài xế khi tài xế kia nghỉ);
 *  3. việc chưa hẹn giờ + chưa có số kiện (payload thiếu trường ⇒ UI phải hiện câu "Chưa rõ …").
 *
 * Chuỗi tiếng Việt cố tình DÀI (tên công ty, địa chỉ) để kiểm tràn chữ ở 360–420dp.
 *
 * [session] là bộ nhớ dùng chung với [MockKhoRepo] ⇒ gán/nhận trong phiên mẫu ĐỔI THẬT dữ liệu,
 * giống hệt đường Odoo thật, chỉ khác nguồn.
 */
class MockCsRepo(
    private val scenario: DemoScenario = DemoScenario.Normal,
    private val session: MockOpsSession = MockOpsSession(),
) : CsJobsRepo {

    override suspend fun jobs(): Result<CsJobsPayload> {
        delay(if (scenario == DemoScenario.Slow) SLOW_DELAY_MS else NORMAL_DELAY_MS)

        if (scenario == DemoScenario.Error) {
            return Result.failure(
                IllegalStateException(
                    "Không tải được danh sách việc cần gán: máy chủ Odoo không phản hồi sau 15 giây. " +
                        "Kiểm tra Odoo đã chạy rồi bấm Tải lại.",
                ),
            )
        }
        if (scenario == DemoScenario.Empty) {
            return Result.success(CsJobsPayload(items = emptyList(), drivers = DRIVERS))
        }
        return Result.success(
            CsJobsPayload(
                items = JOBS.map { job -> job.withSession(session) },
                drivers = DRIVERS,
            ),
        )
    }

    override suspend fun assign(jobId: Int, driverId: Int): Result<AssignOutcome> {
        delay(if (scenario == DemoScenario.Slow) SLOW_DELAY_MS else NORMAL_DELAY_MS)
        if (scenario == DemoScenario.Error) {
            return Result.failure(
                IllegalStateException(
                    "Không gán được tài xế: máy chủ Odoo không phản hồi. Việc vẫn ở trạng thái cũ.",
                ),
            )
        }

        val driver = DRIVERS.firstOrNull { it.id == driverId }
            ?: return Result.failure(
                IllegalStateException("Không tìm thấy tài xế #$driverId trong danh sách của Odoo."),
            )

        session.assign(jobId, driver.name)
        return Result.success(
            AssignOutcome(
                state = JobState.ASSIGNED,
                stateLabel = "Đã gán tài xế",
                driverName = driver.name,
            ),
        )
    }

    /** Áp kết quả gán của phiên mẫu — bỏ `stateLabel` để nhãn được suy lại như server thật. */
    private fun CsJob.withSession(session: MockOpsSession): CsJob {
        val driver = session.driverOf(id) ?: return this
        return copy(
            state = JobState.ASSIGNED,
            stateLabel = null,
            driverName = driver,
        )
    }

    private companion object {
        const val NORMAL_DELAY_MS = 700L
        const val SLOW_DELAY_MS = 2_500L

        /**
         * 3 tài xế — ô chọn tài xế trên màn CS / PHÂN CÔNG lấy đúng danh sách này.
         * `login` có giá trị để cảnh demo offline cũng thấy đúng định dạng
         * "Trần Văn Hùng · taixe01" như dữ liệu thật từ `cs/jobs`.
         */
        val DRIVERS: List<CsDriver> = listOf(
            CsDriver(id = 1, name = "Trần Văn Hùng", login = "taixe01"),
            CsDriver(id = 2, name = "Nguyễn Thị Mai", login = "taixe02"),
            CsDriver(id = 3, name = "Lê Quốc Bảo", login = "taixe03"),
        )

        /**
         * 3 việc mẫu. `createDate` cố tình **KHÁC thứ tự `id`** (yêu cầu 2026-11-03):
         *   id 501 tạo 09:15 (mới nhất) → hiện ĐẦU danh sách
         *   id 503 tạo 08:40
         *   id 502 tạo 07:05 (cũ nhất)
         * Nếu ai đó vô tình đổi sort về `id desc` thì thứ tự hiển thị đổi thành 503 · 502 · 501 —
         * nhìn màn hình là biết sort hỏng, không cần đọc log.
         */
        val JOBS: List<CsJob> = listOf(
            CsJob(
                id = 501,
                ref = "LH/26/0101",
                shop = "Shop Thời trang Mộc Lan",
                address = "128 Nguyễn Đình Chiểu, Phường 6, Quận 3, TP. Hồ Chí Minh",
                contactPhone = "0903 111 222",
                windowFrom = "08:30",
                windowTo = "10:00",
                state = JobState.NEW,
                expectedPackages = 4,
                createDate = "2026-11-03 09:15:00",
            ),
            CsJob(
                id = 502,
                ref = "LH/26/0102",
                shop = "Công ty TNHH Sản xuất Thương mại Dịch vụ Bao bì Nhựa Tân Phú",
                address = "Lô C12, đường số 7, Khu công nghiệp Tân Bình, phường Tây Thạnh, quận Tân Phú, TP. Hồ Chí Minh",
                contactPhone = "028 3812 7745",
                windowFrom = "10:30",
                windowTo = "12:00",
                state = JobState.ASSIGNED,
                expectedPackages = 12,
                driverName = "Trần Văn Hùng",
                createDate = "2026-11-03 07:05:00",
            ),
            CsJob(
                id = 503,
                ref = "LH/26/0103",
                shop = "Nhà thuốc An Khang — chi nhánh Phú Nhuận",
                address = "202 Phan Đăng Lưu, Phường 3, Quận Phú Nhuận, TP. Hồ Chí Minh",
                contactPhone = null,
                windowFrom = "-",
                windowTo = "-",
                state = JobState.NEW,
                expectedPackages = null,
                createDate = "2026-11-03 08:40:00",
            ),
        )
    }
}
