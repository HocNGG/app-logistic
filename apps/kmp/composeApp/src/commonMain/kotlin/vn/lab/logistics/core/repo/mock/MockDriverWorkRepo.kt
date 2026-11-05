package vn.lab.logistics.core.repo.mock

import kotlinx.coroutines.delay
import vn.lab.logistics.core.model.JobState
import vn.lab.logistics.core.net.ApiException
import vn.lab.logistics.core.repo.CaptureSubmission
import vn.lab.logistics.core.repo.DriverWorkRepo
import vn.lab.logistics.core.repo.JobStateChange
import vn.lab.logistics.core.repo.isFailReasonValid
import vn.lab.logistics.feature.driver.stateLabelOf

/**
 * ⚠️ IMPL GIẢ LẬP của 3 thao tác ghi — **chỉ** dùng khi công tắc nguồn dữ liệu = **Mẫu**.
 *
 * Vì sao vẫn phải có (khi đã có API thật): đó là "đường lui" của Luật 8 — Odoo tắt, DB chưa
 * seed, mất mạng giữa buổi demo thì tài xế vẫn bấm được hết 4 màn và thấy state đổi. Màn hình
 * không biết đang ở impl nào; ai xem demo thấy nhãn **"Nguồn: Mẫu"** + dòng chữ "chế độ mẫu".
 *
 * Mô phỏng ĐÚNG ngữ nghĩa backend (không phải "làm cho xanh"):
 *  - `accept`   : `en_route`; đã `en_route`/`picked_up` thì trả nguyên (controller idempotent).
 *  - `capture`  : `picked_up`; **lần thử đầu ở kịch bản Lỗi sẽ hỏng** để demo cảnh "Thử lại"
 *                 với **cùng `clientUuid`** — bấm lại thành công và server trả `duplicate: true`
 *                 (đúng nhánh chống trùng của `driver.py::capture`).
 *  - `fail`     : `failed`; lý do < 5 ký tự bị chặn (cùng luật với màn hình).
 *  - Ảnh/chữ ký: chỉ kiểm **rỗng/không rỗng**, KHÔNG lưu, KHÔNG ghi đĩa.
 */
class MockDriverWorkRepo(
    private val scenario: DemoScenario = DemoScenario.Normal,
    private val session: MockDriverWorkSession = MockDriverWorkSession(),
) : DriverWorkRepo {

    /** Số lần đã thử capture — để kịch bản "Lỗi" hỏng đúng 1 lần rồi cho thử lại thành công. */
    private var captureAttempts: Int = 0

    override suspend fun accept(jobId: Int): Result<JobStateChange> {
        delay(delayMs())
        val hienTai = session.stateOf(jobId)
        if (hienTai != null &&
            (hienTai == JobState.EN_ROUTE || hienTai == JobState.PICKED_UP)
        ) {
            // Nhánh idempotent của controller: không đổi gì, trả state hiện tại.
            return Result.success(JobStateChange(jobId, hienTai, stateLabelOf(hienTai)))
        }
        session.apply(jobId, JobState.EN_ROUTE)
        return Result.success(
            JobStateChange(jobId, JobState.EN_ROUTE, stateLabelOf(JobState.EN_ROUTE)),
        )
    }

    override suspend fun capture(
        jobId: Int,
        submission: CaptureSubmission,
    ): Result<JobStateChange> {
        delay(delayMs())
        captureAttempts++

        // Kịch bản "Lỗi máy chủ": lần GỬI ĐẦU hỏng (giống mất mạng giữa đường).
        if (scenario == DemoScenario.Error && captureAttempts == 1) {
            return Result.failure(
                ApiException.Server(
                    "Chế độ mẫu: giả lập mất mạng khi gửi biên nhận. Dữ liệu vẫn còn trên máy — " +
                        "bấm \"Thử lại\" (giữ nguyên mã chống trùng) để gửi lại.",
                ),
            )
        }

        // Cùng bất biến an toàn với backend: thiếu bằng chứng/số liệu là 400, không ghi gì.
        if (submission.photoJpeg.isEmpty() || submission.signaturePng.isEmpty()) {
            return Result.failure(
                ApiException.Server("Thiếu ảnh hoặc chữ ký — không thể gửi biên nhận."),
            )
        }
        if (submission.packages <= 0) {
            return Result.failure(
                ApiException.Server(
                    "Phải nhập số kiện thực tế (lớn hơn 0) — không được để trống.",
                ),
            )
        }
        if (submission.weightKg <= 0.0) {
            return Result.failure(
                ApiException.Server("Khối lượng thực tế phải lớn hơn 0 kg."),
            )
        }

        val daLayTruoc = session.stateOf(jobId) == JobState.PICKED_UP
        val duplicate = daLayTruoc && session.uuidOf(jobId) == submission.clientUuid
        session.apply(jobId, JobState.PICKED_UP, submission.clientUuid)
        return Result.success(
            JobStateChange(
                jobId = jobId,
                state = JobState.PICKED_UP,
                stateLabel = stateLabelOf(JobState.PICKED_UP),
                duplicate = duplicate,
            ),
        )
    }

    override suspend fun fail(jobId: Int, reason: String): Result<JobStateChange> {
        delay(delayMs())
        if (!isFailReasonValid(reason)) {
            return Result.failure(
                ApiException.Server("Phải nhập lý do không lấy được hàng (ít nhất 5 ký tự)."),
            )
        }
        session.apply(jobId, JobState.FAILED)
        return Result.success(
            JobStateChange(jobId, JobState.FAILED, stateLabelOf(JobState.FAILED)),
        )
    }

    private fun delayMs(): Long = when (scenario) {
        DemoScenario.Slow -> 2_000L
        DemoScenario.Error -> 400L
        else -> 600L
    }
}
