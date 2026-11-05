package vn.lab.logistics.core.model

/**
 * =====================================================================================
 * KẾT QUẢ MỘT LẦN QUÉT TEM KIỆN — hợp đồng với `POST /lab/api/v1/scan` (BE-01, 2026-11-02).
 *
 * Endpoint dùng chung cho 2 mốc (`moc = "nhan"` · `"xuat"`); app màn Kho hiện chỉ dùng `nhan`.
 * Server trả `data.ketQua` là MỘT trong bốn mã dưới đây — app KHÔNG tự suy ra kết quả từ HTTP
 * status, vì BE trả 404/409 nhưng envelope vẫn `ok: true` (kết quả nghiệp vụ nằm trong `ketQua`).
 *
 * Vì sao phải phân biệt `TRUNG` với `OK`: người quét cần biết "kiện này đã quét rồi" (không phải
 * lỗi, cũng KHÔNG tăng tiến độ). Gộp hai thứ này là nguyên nhân quen thuộc của "quét 12 kiện mà
 * hệ thống báo 15".
 * =====================================================================================
 */
enum class ScanOutcome {
    /** Kiện hợp lệ, đúng mốc — lần quét này được tính vào tiến độ. */
    OK,

    /** Kiện đã được quét trước đó (hoặc cùng `clientUuid` gửi lặp) — KHÔNG tăng tiến độ. */
    TRUNG,

    /** Quét sai mốc (vd tem xuất khi kiện chưa in tem xuất) — chặn, có câu giải thích. */
    SAI_MOC,

    /** Không có kiện nào mang mã vừa quét — gõ sai / tem của hệ thống khác. */
    KHONG_TIM_THAY,
    ;

    /** Mã do server trả (`ok` · `trung` · `sai_moc` · `khong_tim_thay`) → enum. */
    companion object {
        fun fromCode(code: String?): ScanOutcome = when (code?.trim()?.lowercase()) {
            "trung" -> TRUNG
            "sai_moc" -> SAI_MOC
            "khong_tim_thay" -> KHONG_TIM_THAY
            else -> OK
        }
    }
}

/**
 * Một lần quét — đủ dữ liệu để UI hiện chip kết quả + cập nhật tiến độ, không phải gọi lại API.
 *
 * @param maQuet mã người dùng quét/gõ (giữ nguyên để hiện lại trên dòng kết quả).
 * @param kien mã kiện server trả (`KIEN/26/000123`).
 * @param maXuat mã tem xuất (rỗng với mốc `nhan`).
 * @param job số yêu cầu lấy hàng mà kiện thuộc về (server lấy từ `origin` của phiếu).
 * @param tong tổng số kiện cần quét của mốc này (`0` = server chưa biết).
 * @param daQuet số kiện đã quét OK ở mốc này (server đếm trong `lab.scan.event`).
 */
data class ScanResult(
    val maQuet: String,
    val kien: String,
    val maXuat: String?,
    val job: String?,
    val chuyen: String?,
    val trangThai: String?,
    val outcome: ScanOutcome,
    val canhBao: String?,
    val daQuet: Int,
    val tong: Int,
) {
    /** Nhãn tiếng Việt hiện trên chip kết quả (UI không tự ghép chuỗi nghiệp vụ). */
    val nhanKetQua: String
        get() = when (outcome) {
            ScanOutcome.OK -> "Đã xác nhận"
            ScanOutcome.TRUNG -> "Đã quét trước đó"
            ScanOutcome.SAI_MOC -> "Sai mốc quét"
            ScanOutcome.KHONG_TIM_THAY -> "Không tìm thấy kiện này trong hệ thống"
        }

    /**
     * Câu giải thích thêm để hiện dưới chip. Với [ScanOutcome.TRUNG] mà server không kèm
     * `canhBao`, app nói rõ "không tính vào tiến độ" — người quét khỏi tưởng máy bỏ qua.
     */
    val giaiThich: String?
        get() = canhBao?.takeIf { it.isNotBlank() }
            ?: when (outcome) {
                ScanOutcome.TRUNG -> "Kiện này đã được quét ở mốc này — không tính vào tiến độ."
                ScanOutcome.SAI_MOC -> "Kiện không ở trạng thái cho phép quét mốc này."
                ScanOutcome.KHONG_TIM_THAY -> "Kiểm tra lại tem hoặc chọn đúng kho."
                ScanOutcome.OK -> null
            }

    /** Lần quét này có được tính vào tiến độ không (chỉ [ScanOutcome.OK]). */
    val tinhVaoTienDo: Boolean get() = outcome == ScanOutcome.OK
}
