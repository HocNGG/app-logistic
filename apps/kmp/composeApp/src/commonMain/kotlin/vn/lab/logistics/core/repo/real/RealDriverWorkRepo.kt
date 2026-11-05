package vn.lab.logistics.core.repo.real

import vn.lab.logistics.core.media.Base64Codec
import vn.lab.logistics.core.net.ApiException
import vn.lab.logistics.core.net.CaptureRequest
import vn.lab.logistics.core.net.LabApi
import vn.lab.logistics.core.net.SubmitResult
import vn.lab.logistics.core.net.toVietnameseMessage
import vn.lab.logistics.core.repo.CaptureSubmission
import vn.lab.logistics.core.repo.DriverWorkRepo
import vn.lab.logistics.core.repo.JobStateChange
import vn.lab.logistics.core.settings.AppSettings

/**
 * =====================================================================================
 * GHI THẬT lên Odoo: `accept` · `capture` · `fail` (kèm cookie phiên + `X-Lab-Token`).
 *
 * ÁNH XẠ ENDPOINT ↔ HÀNH ĐỘNG MODEL (chép từ `addons/lab_api/controllers/driver.py`):
 *
 * | App gọi | Controller | Hàm model chạy | Kết quả state |
 * |---|---|---|---|
 * | [accept]  | `accept()`  | `action_assign()` (chỉ khi `new`) + `action_start()` | `en_route` |
 * | [capture] | `capture()` | `write({chu_ky, so_kien_thuc_te, khoi_luong_uoc_kg, lab_client_uuid})` + `action_start()` (chỉ khi `assigned`) + `action_pick()` | `picked_up` |
 * | [fail]    | `fail()`    | `action_fail(ly_do=…)` | `failed` |
 *
 * Ngữ nghĩa lỗi (đã đo + đọc controller), repo giữ nguyên câu tiếng Việt của backend:
 *  - `401` / `TOKEN_*` / HTML login ⇒ [ApiException.SessionExpired] + **xoá token** (buộc đăng nhập lại);
 *  - `409 INVALID_STATE` (việc đã huỷ/đã lấy rồi) ⇒ giữ câu của BE, KHÔNG retry;
 *  - `400 VALIDATION` (ảnh sai định dạng, thiếu số kiện) ⇒ giữ câu của BE;
 *  - không kết nối được ⇒ [ApiException.Network] nêu địa chỉ máy chủ.
 *
 * ⚠️ **KHÔNG retry tự động** cho cả 3 hàm: đây là POST ghi (skill 04 §6). Riêng `capture`,
 * nút "Thử lại" của người dùng mới là đường retry — và nó dùng lại **cùng `clientUuid`**
 * nên backend trả `duplicate: true` thay vì tạo bộ ảnh thứ hai.
 * =====================================================================================
 */
class RealDriverWorkRepo(
    private val api: LabApi,
    private val settings: AppSettings,
) : DriverWorkRepo {

    override suspend fun accept(jobId: Int): Result<JobStateChange> =
        ghi(jobId) { api.acceptJob(jobId, settings.token) }

    override suspend fun capture(jobId: Int, submission: CaptureSubmission): Result<JobStateChange> =
        ghi(jobId) {
            api.captureJob(
                jobId = jobId,
                request = CaptureRequest(
                    // Đúng 5 khoá controller đọc — xem `core/net/LabDtos.captureRequestBody`.
                    photoBase64 = Base64Codec.encode(submission.photoJpeg),
                    signatureBase64 = Base64Codec.encode(submission.signaturePng),
                    packages = submission.packages,
                    weightKg = submission.weightKg,
                    clientUuid = submission.clientUuid,
                ),
                token = settings.token,
            )
        }

    override suspend fun fail(jobId: Int, reason: String): Result<JobStateChange> =
        ghi(jobId) { api.failJob(jobId, reason, settings.token) }

    // ------------------------------------------------------------------ nội bộ

    /**
     * Bọc một lời gọi ghi: kiểm token trước, đổi lỗi thành [Result.failure] với câu tiếng Việt.
     * Nhờ vậy ViewModel không có `try/catch` nào — UI chỉ đọc `Result`.
     */
    private suspend fun ghi(jobId: Int, block: suspend () -> SubmitResult): Result<JobStateChange> {
        if (!settings.hasToken) {
            return Result.failure(
                ApiException.SessionExpired(
                    "Chưa có mã truy cập của tài xế. Bấm \"Đăng nhập\" bằng tài khoản tài xế " +
                        "rồi làm lại.",
                ),
            )
        }
        return try {
            val result = block()
            Result.success(
                JobStateChange(
                    jobId = jobId,
                    state = result.state,
                    stateLabel = result.stateLabel,
                    duplicate = result.duplicate,
                ),
            )
        } catch (e: ApiException) {
            // Phiên chết thì token cũng vô dụng (cookie session_id chỉ sống trong RAM).
            if (e is ApiException.SessionExpired) settings.clearSession()
            Result.failure(e)
        } catch (e: Throwable) {
            Result.failure(ApiException.Server(e.toVietnameseMessage(), e))
        }
    }
}
