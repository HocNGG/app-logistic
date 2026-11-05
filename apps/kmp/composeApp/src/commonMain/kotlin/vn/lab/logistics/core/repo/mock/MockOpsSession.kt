package vn.lab.logistics.core.repo.mock

/**
 * =====================================================================================
 * BỘ NHỚ CỦA CHẾ ĐỘ MẪU cho 2 màn MỚI (CS điều phối · Kho nhận hàng).
 *
 * Vấn đề thật (giống [MockDriverWorkSession]): nếu repo mẫu trả danh sách tĩnh thì bấm "Gán tài
 * xế" / "Nhận hàng" xong danh sách KHÔNG đổi → cảnh demo "server đổi trạng thái" biến thành
 * giả. Lớp này là chỗ ghi của chế độ mẫu: repo đọc (danh sách) và repo ghi (thao tác) dùng CHUNG
 * một instance, đúng như một backend thật dùng chung một DB.
 *
 * Hai thứ được nhớ:
 *  - `driverName` theo việc — kết quả của "Gán tài xế";
 *  - `pickingName` theo việc — số phiếu nhập kho server "sinh" khi "Nhận hàng".
 *
 * ⚠️ Chỉ tồn tại khi công tắc nguồn dữ liệu = **Mẫu**. Đường Odoo thật không chạm lớp này.
 * =====================================================================================
 */
class MockOpsSession {

    private val drivers: MutableMap<Int, String> = mutableMapOf()
    private val pickings: MutableMap<Int, String> = mutableMapOf()

    /** Tài xế đã gán cho việc này trong phiên mẫu (null = chưa gán). */
    fun driverOf(jobId: Int): String? = drivers[jobId]

    /** Số phiếu nhập kho đã "sinh" cho việc này trong phiên mẫu (null = chưa nhận). */
    fun pickingOf(jobId: Int): String? = pickings[jobId]

    fun assign(jobId: Int, driverName: String) {
        drivers[jobId] = driverName
    }

    fun receive(jobId: Int, pickingName: String) {
        pickings[jobId] = pickingName
    }
}
