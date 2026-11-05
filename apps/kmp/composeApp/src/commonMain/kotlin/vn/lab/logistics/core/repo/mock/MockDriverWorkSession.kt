package vn.lab.logistics.core.repo.mock

/**
 * =====================================================================================
 * BỘ NHỚ CỦA CHẾ ĐỘ MẪU — để demo chạy được **cả vòng** accept → capture → refresh.
 *
 * Vấn đề thật: [MockDriverJobsRepo] trả danh sách tĩnh, nên nếu không có chỗ ghi lại trạng
 * thái thì bấm "Gửi & xác nhận" xong danh sách KHÔNG đổi → cảnh demo "job đổi state theo
 * server" biến thành giả. Lớp này là chỗ ghi đó: hai repo mẫu (danh sách + thao tác) dùng
 * CHUNG một instance, đúng như một backend thật dùng chung một DB.
 *
 * Ba thứ được nhớ:
 *  - `state`     — trạng thái sau thao tác (accept → en_route, capture → picked_up, fail → failed);
 *  - `clientUuid`— để mô phỏng đúng nhánh **chống trùng** của backend (`duplicate: true`);
 *  - (không nhớ gì khác — ảnh/chữ ký KHÔNG bao giờ lưu, kể cả trong RAM của chế độ mẫu.)
 *
 * ⚠️ Chỉ tồn tại khi công tắc nguồn dữ liệu = **Mẫu**. Đường Odoo thật không chạm lớp này.
 * =====================================================================================
 */
class MockDriverWorkSession {

    private val states: MutableMap<Int, String> = mutableMapOf()
    private val uuids: MutableMap<Int, String> = mutableMapOf()

    /** State đã bị đổi trong phiên demo (null = giữ state gốc của kịch bản). */
    fun stateOf(jobId: Int): String? = states[jobId]

    /** `clientUuid` của lần capture gần nhất cho việc này. */
    fun uuidOf(jobId: Int): String? = uuids[jobId]

    fun apply(jobId: Int, state: String, clientUuid: String? = null) {
        states[jobId] = state
        if (clientUuid != null) uuids[jobId] = clientUuid
    }
}
