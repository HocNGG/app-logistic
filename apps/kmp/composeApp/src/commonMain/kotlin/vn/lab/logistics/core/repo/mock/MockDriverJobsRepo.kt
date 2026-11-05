package vn.lab.logistics.core.repo.mock

import kotlinx.coroutines.delay
import vn.lab.logistics.core.model.DriverJob
import vn.lab.logistics.core.model.JobState
import vn.lab.logistics.core.repo.DriverJobsRepo

/**
 * ⚠️ IMPL GIẢ LẬP — 6 việc lấy hàng mẫu để CHỨNG MINH duo UI + 4 trạng thái trước khi có backend.
 *
 * Dữ liệu bám nghiệp vụ thật (docs/00): shop nhỏ lẻ, địa chỉ trong TP.HCM, khung giờ hẹn,
 * số kiện dự kiến, ghi chú hiện trường. Tên/địa chỉ cố tình có chuỗi DÀI để test tràn chữ
 * (skill 07 §4: "test bằng chuỗi tiếng Việt dài nhất").
 *
 * Trạng thái dùng ĐÚNG 7 mã của model BE-01 (`lab.yeu.cau.lay_hang.state`, đối chiếu 2026-10-28):
 * `new` · `assigned` · `en_route` · `picked_up` · `confirmed` · `cancelled` · `failed`.
 * 6 việc dưới đây phủ 6/7 mã (thiếu `new` — việc chưa gán tài xế không nằm trong danh sách
 * của tài xế; mapping + self-check vẫn kiểm đủ 7 mã).
 * App KHÔNG tự đổi state — chỉ hiển thị.
 *
 * CHẾ ĐỘ MẪU CHẠY ĐƯỢC CẢ VÒNG (màn CAPTURE): [session] là bộ nhớ dùng chung với
 * [MockDriverWorkRepo] ⇒ bấm "Bắt đầu đi" / "Gửi & xác nhận" xong tải lại danh sách thì state
 * của việc ĐỔI THẬT trong kịch bản mẫu (giống hệt đường Odoo thật, chỉ khác nguồn dữ liệu).
 *
 * ⚠️ ASSUMPTION: trạng thái đồng bộ với docs/02 §3.2 + model BE-01; verify lại khi có API.
 */
class MockDriverJobsRepo(
    private val scenario: DemoScenario = DemoScenario.Normal,
    private val session: MockDriverWorkSession = MockDriverWorkSession(),
) : DriverJobsRepo {

    override suspend fun todayJobs(): Result<List<DriverJob>> {
        delay(if (scenario == DemoScenario.Slow) SLOW_DELAY_MS else NORMAL_DELAY_MS)

        if (scenario == DemoScenario.Error) {
            return Result.failure(
                IllegalStateException(
                    "Không tải được danh sách việc hôm nay: máy chủ Odoo không phản hồi sau 15 giây. " +
                        "Danh sách đang lưu trên máy vẫn xem được.",
                ),
            )
        }
        if (scenario == DemoScenario.Empty) {
            return Result.success(emptyList())
        }
        // CHẾ ĐỘ MẪU CHẠY ĐƯỢC CẢ VÒNG: [session] là bộ nhớ dùng chung với
        // `MockDriverWorkRepo` ⇒ bấm "Bắt đầu đi" / "Gửi & xác nhận" xong tải lại danh sách thì
        // state của việc ĐỔI THẬT trong kịch bản mẫu (giống hệt đường Odoo thật, chỉ khác nguồn).
        return Result.success(JOBS.map { job -> job.withSessionState(session) })
    }

    /** Ghi đè state bằng giá trị đã đổi trong phiên mẫu (null = giữ nguyên state gốc). */
    private fun DriverJob.withSessionState(session: MockDriverWorkSession): DriverJob {
        val stateMoi = session.stateOf(id) ?: return this
        // Bỏ `stateLabel` để nhãn tiếng Việt được suy lại từ mã state mới (giống server thật).
        return copy(state = stateMoi, stateLabel = null)
    }

    private companion object {
        const val NORMAL_DELAY_MS = 800L
        const val SLOW_DELAY_MS = 2_500L

        /**
         * 6 việc — phủ 6 trạng thái thật của model:
         * `assigned` · `cancelled` · `en_route` · `picked_up` · `failed` · `confirmed`
         * (thiếu `new`: việc chưa gán tài xế không tới tay tài xế).
         * Đủ 4 giọng màu: Info (assigned/en_route/picked_up) · Warn (cancelled) ·
         * Bad (failed) · Ok (confirmed) — để video thấy được cả bảng màu nghiệp vụ.
         * Việc #3 và #5 có chuỗi dài nhất để kiểm tràn chữ.
         */
        val JOBS: List<DriverJob> = listOf(
            DriverJob(
                id = 1,
                ref = "YC/LH/26/0001",
                shop = "Shop Thời trang Mộc Lan",
                address = "128 Nguyễn Đình Chiểu, Phường 6, Quận 3, TP. Hồ Chí Minh",
                contactName = "Chị Ngọc Anh",
                contactPhone = "0903 111 222",
                windowFrom = "08:30",
                windowTo = "10:00",
                expectedPackages = 4,
                state = JobState.ASSIGNED,
                note = "Hàng dễ vỡ: 2 thùng gốm sứ, cần lót xốp.",
            ),
            DriverJob(
                id = 2,
                ref = "YC/LH/26/0002",
                shop = "Cửa hàng Điện tử Hải Nam",
                address = "45 Trần Hưng Đạo, Phường Cầu Ông Lãnh, Quận 1, TP. Hồ Chí Minh",
                contactName = "Anh Hải",
                contactPhone = "0918 447 991",
                windowFrom = "09:00",
                windowTo = "11:00",
                expectedPackages = 6,
                state = JobState.CANCELLED,
                note = "Shop huỷ lấy hàng lúc 8:40 (hàng chưa đóng gói xong). CS đã xác nhận huỷ.",
            ),
            DriverJob(
                id = 3,
                ref = "YC/LH/26/0003",
                shop = "Công ty TNHH Sản xuất Thương mại Dịch vụ Bao bì Nhựa Tân Phú",
                address = "Lô C12, đường số 7, Khu công nghiệp Tân Bình, phường Tây Thạnh, quận Tân Phú, TP. Hồ Chí Minh",
                contactName = "Chú Bảy",
                contactPhone = "028 3812 7745",
                windowFrom = "10:30",
                windowTo = "12:00",
                expectedPackages = 12,
                state = JobState.EN_ROUTE,
                note = "Cổng phụ hướng đường Tây Thạnh, gọi trước 10 phút để mở cổng.",
            ),
            DriverJob(
                id = 4,
                ref = "YC/LH/26/0004",
                shop = "Shop Mỹ phẩm Lan Chi",
                address = "72 Lê Lợi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh",
                contactName = "Chị Lan Chi",
                contactPhone = "0932 006 118",
                windowFrom = "13:00",
                windowTo = "14:30",
                expectedPackages = 2,
                state = JobState.PICKED_UP,
                note = "Gửi tại quầy lễ tân nếu shop đông khách.",
            ),
            DriverJob(
                id = 5,
                ref = "YC/LH/26/0005",
                shop = "Xưởng Nội thất Gỗ Việt",
                address = "Số 9 đường số 7, Khu công nghiệp Vĩnh Lộc, huyện Bình Chánh, TP. Hồ Chí Minh",
                contactName = null,
                contactPhone = "0907 555 010",
                windowFrom = "15:00",
                windowTo = "16:30",
                expectedPackages = 9,
                state = JobState.FAILED,
                note = "Shop đóng cửa, gọi 3 lần không liên hệ được. Đã báo CS.",
            ),
            DriverJob(
                id = 6,
                ref = "YC/LH/26/0006",
                shop = "Nhà thuốc An Khang — chi nhánh Phú Nhuận",
                address = "202 Phan Đăng Lưu, Phường 3, Quận Phú Nhuận, TP. Hồ Chí Minh",
                contactName = "Dược sĩ Trâm",
                contactPhone = "0911 224 668",
                windowFrom = "16:00",
                windowTo = "17:30",
                expectedPackages = 3,
                state = JobState.CONFIRMED,
                note = "Đã lấy đủ 3 kiện, hàng khô để riêng.",
            ),
        )
    }
}
