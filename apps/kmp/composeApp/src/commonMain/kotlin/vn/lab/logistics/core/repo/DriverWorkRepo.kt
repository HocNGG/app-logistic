package vn.lab.logistics.core.repo

/**
 * =====================================================================================
 * Hợp đồng GHI của tài xế: nhận việc → gửi ảnh/chữ ký → xác nhận, hoặc báo không lấy được.
 *
 * Ba endpoint THẬT của `lab_api` (đọc `addons/lab_api/controllers/driver.py`, không đoán):
 *
 * | Hàm | Endpoint | Controller gọi | Ràng buộc state |
 * |---|---|---|---|
 * | [accept]  | `POST …/jobs/<id>/accept`  | `new →` `action_assign()` + `action_start()`; `assigned →` `action_start()` | đã `en_route`/`picked_up` thì trả nguyên state (idempotent) |
 * | [capture] | `POST …/jobs/<id>/capture` | `assigned →` `action_start()` rồi **`action_pick()`** | chỉ nhận `assigned`/`en_route`; khác ⇒ **409** `INVALID_STATE` |
 * | [fail]    | `POST …/jobs/<id>/fail`    | `action_fail(ly_do=…)` | chỉ nhận `assigned`/`en_route`/`picked_up` |
 *
 * Vì sao có interface: màn hình KHÔNG được biết đang ở nguồn Mẫu hay Odoo thật
 * (agents/fe/AGENT.md §3.2). Hai impl được chọn ở nơi lắp ráp (`App.kt`):
 *  - [vn.lab.logistics.core.repo.real.RealDriverWorkRepo] — gọi API thật;
 *  - [vn.lab.logistics.core.repo.mock.MockDriverWorkRepo] — mô phỏng, ghi rõ "chế độ mẫu".
 *
 * App **không tự đổi state**: mọi trạng thái mới phải do SERVER trả về ([JobStateChange]).
 * =====================================================================================
 */

/** Trạng thái server trả sau một thao tác ghi — nguồn sự thật duy nhất cho UI. */
data class JobStateChange(
    val jobId: Int,
    /** Mã state mới (vd `en_route`, `picked_up`, `failed`); `null` nếu server không trả. */
    val state: String?,
    /** Nhãn tiếng Việt của server (`stateLabel`) — ưu tiên hơn bảng nhãn cứng của app. */
    val stateLabel: String?,
    /** `true` khi server nhận ra `clientUuid` trùng ⇒ KHÔNG tạo bộ ảnh thứ hai. */
    val duplicate: Boolean = false,
)

/**
 * Bằng chứng tài xế gửi lên `capture`.
 *
 * ⚠️ `photoJpeg` PHẢI đã nén ≤ 5 MB (`core/media/ImageCodec.compressJpeg`) và giữ magic
 * bytes JPEG; `signaturePng` là PNG nền trong suốt từ `SignaturePad.toPngBytes()`.
 * Backend kiểm magic bytes nên sai định dạng là 400 ngay.
 */
data class CaptureSubmission(
    val photoJpeg: ByteArray,
    val signaturePng: ByteArray,
    val packages: Int,
    val weightKg: Double,
    /** Idempotency key của phiên capture (xem `feature/capture/CaptureSession.kt`). */
    val clientUuid: String,
    /**
     * Ghi chú hiện trường (tuỳ chọn).
     *
     * ⚠️ Endpoint `capture` hiện chỉ đọc 5 khoá (`photoBase64`, `signatureBase64`, `packages`,
     * `weightKg`, `clientUuid`) ⇒ ghi chú **chưa** tới được CS qua API này; app giữ nó trong
     * phiên trên máy. Đã ghi yêu cầu BE bổ sung khoá `note` (xem báo cáo FE-01).
     */
    val note: String? = null,
)

/**
 * Độ dài tối thiểu của lý do "không lấy được".
 *
 * Backend chỉ kiểm **không rỗng** (`driver.py::fail`: `if not ly_do: 400`). App đặt sàn 5 ký tự
 * để lý do có nghĩa nghiệp vụ ("đóng cửa", "sai địa chỉ") thay vì "a" — CS đọc được lý do,
 * và docs/04 §3.2 yêu cầu lý do là dữ liệu thật, không phải cờ đánh dấu.
 */
const val MIN_FAIL_REASON_LENGTH: Int = 5

/** Lý do đã đủ dài để gửi (bỏ khoảng trắng đầu/cuối). */
fun isFailReasonValid(reason: String): Boolean =
    reason.trim().length >= MIN_FAIL_REASON_LENGTH

interface DriverWorkRepo {

    /** Tài xế nhận việc / bắt đầu đi lấy hàng. */
    suspend fun accept(jobId: Int): Result<JobStateChange>

    /** Gửi ảnh + chữ ký + số kiện + kg ⇒ server chuyển việc sang "Đã lấy được hàng". */
    suspend fun capture(jobId: Int, submission: CaptureSubmission): Result<JobStateChange>

    /** Báo không lấy được hàng — lý do bắt buộc (≥ 5 ký tự ở phía app). */
    suspend fun fail(jobId: Int, reason: String): Result<JobStateChange>
}
