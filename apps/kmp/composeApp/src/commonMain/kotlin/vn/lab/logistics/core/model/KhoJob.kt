package vn.lab.logistics.core.model

/**
 * Một việc ĐÃ LẤY HÀNG đang chờ kho nhận — hình dạng ĐÚNG theo hợp đồng
 * `GET /lab/api/v1/kho/cho-nhan`:
 *
 * ```json
 * {"items":[{"id":..,"ref":"LH/26/0005","shop":"…","packages":4,"weightKg":12.5,
 *            "driverName":"Trần Văn Hùng","pickingName":"WH/IN/00042"}]}
 * ```
 *
 * Hai trường có thể VẮNG (app không bịa số):
 *  - `packages` / `weightKg` — tài xế chưa nhập số kiện/kg ⇒ "Chưa rõ số kiện" / "Chưa rõ
 *    khối lượng";
 *  - `driverName` — không xác định được tài xế ⇒ "Chưa rõ tài xế".
 *
 * `pickingName` là **số phiếu nhập kho** do Odoo sinh khi kho bấm "Nhận hàng"
 * (`POST /lab/api/v1/kho/jobs/<id>/nhan`). Trước khi nhận, trường này vắng.
 */
data class KhoJob(
    val id: Int,
    val ref: String,
    val shop: String,
    /** Số kiện THỰC TẾ tài xế đã nhập ở màn capture; `null` nếu server chưa có. */
    val packages: Int?,
    /** Khối lượng thực tế (kg); `null` nếu server chưa có. */
    val weightKg: Double?,
    val driverName: String?,
    /** Số phiếu nhập kho — chỉ có sau khi kho nhận hàng. */
    val pickingName: String? = null,
    /** Mã trạng thái từ server (`picked_up` hoặc `confirmed`). Mặc định `picked_up` cho fixture cũ. */
    val state: String = JobState.PICKED_UP,
    /** Nhãn trạng thái tiếng Việt từ server (`"Đã lấy được hàng"` / `"Đã về kho"`). */
    val stateLabel: String? = null,
    /** Mã đơn bán (`S0xxxx`) gắn với yêu cầu lấy hàng — dùng để CS gom vào chuyến ở Bước 8. */
    val saleOrderName: String? = null,
    /** Số kiện đã quét tem nhận (`loai='nhan'`, `ket_qua='ok'`) từ server. */
    val scannedPackages: Int = 0,
    /** Tổng số kiện đã sinh trên phiếu nhập kho từ server. */
    val totalPackages: Int = 0,
)

/**
 * `data` server trả sau `POST /lab/api/v1/kho/jobs/<id>/nhan`:
 * `{state, stateLabel, pickingName, saleOrderName?}`. Server cũ trả thiếu trường nào thì UI fallback trường đó.
 */
data class ReceiveOutcome(
    val state: String?,
    val stateLabel: String?,
    val pickingName: String?,
    val saleOrderName: String? = null,
)
