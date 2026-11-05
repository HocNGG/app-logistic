package vn.lab.logistics.core.model

/**
 * =====================================================================================
 * XUẤT KHO THEO CHUYẾN — hợp đồng với BE-01 (chốt 2026-11-02):
 *
 *  - `GET  /lab/api/v1/kho/cho-xuat`               → chuyến còn kiện CHƯA xuất
 *  - `GET  /lab/api/v1/kho/cho-xuat?chuyenId=<id>`  → chi tiết 1 chuyến
 *  - `POST /lab/api/v1/kho/chuyen/<id>/xuat`         → xác nhận xuất (validate `WH/OUT`)
 *
 * Nghiệp vụ (đã chốt với khách):
 *  - tem sinh TẠI KHO: nhận hàng → in tem nhận; **chốt booking** → in tem xuất (mỗi kiện 1 tem);
 *  - kho quét từng tem xuất khi đưa kiện lên xe;
 *  - thiếu kiện mà KHÔNG có lý do ⇒ server chặn (409 `CON_KIEN_CHUA_QUET`, kèm danh sách còn lại);
 *  - thiếu kiện + CÓ lý do ⇒ vẫn xuất được, kiện thiếu sang `cancelled` (quyết định M9).
 * =====================================================================================
 */

/** Một kiện trong chuyến, kèm trạng thái để chip hiện đúng màu. */
data class KhoXuatKien(
    val ma: String,
    val maXuat: String?,
    val trangThai: String?,
    val trangThaiLabel: String?,
) {
    /** Đã xuất kho chưa (chỉ khi `shipped` mới được tính vào tiến độ). */
    val daXuat: Boolean get() = trangThai == "shipped"

    /** Nhãn hiện trên chip — ưu tiên câu của server, thiếu thì suy từ mã trạng thái. */
    val nhanTrangThai: String
        get() = trangThaiLabel?.takeIf { it.isNotBlank() } ?: when (trangThai) {
            "in_stock" -> "Trong kho"
            "packed" -> "Đã in tem xuất"
            "shipped" -> "Đã xuất kho"
            "cancelled" -> "Không xuất"
            else -> "Chưa rõ trạng thái"
        }
}

/** Một chuyến chờ xuất kho. */
data class KhoXuatChuyen(
    val chuyenId: Int,
    val chuyen: String,
    val state: String?,
    val stateLabel: String?,
    val diemDen: String?,
    val soKien: Int,
    val daQuet: Int,
    val kien: List<KhoXuatKien>,
) {
    /** Số kiện còn chưa xuất. */
    val conLai: Int get() = (soKien - daQuet).coerceAtLeast(0)

    /** Tỉ lệ đã xuất 0f..1f dùng cho thanh tiến độ. */
    val tiLe: Float get() = if (soKien <= 0) 0f else (daQuet.toFloat() / soKien).coerceIn(0f, 1f)

    /** Đã xuất đủ kiện chưa. */
    val duKien: Boolean get() = soKien > 0 && daQuet >= soKien

    /** Câu tiến độ: `"Đã xuất 3/7 kiện"`. */
    val tienDoLabel: String get() = "Đã xuất $daQuet/$soKien kiện"
}

/**
 * Kết quả gọi `POST …/xuat`. Hai nhánh là **hai chuyện nghiệp vụ khác nhau**, không phải lỗi/kết
 * quả của cùng một việc — vì vậy dùng sealed thay vì một data class có cờ `thanhCong`.
 */
sealed interface XuatKetQua {

    /** Đã xuất xong (đủ kiện, hoặc thiếu nhưng có lý do — quyết định M9). */
    data class DaXuat(
        val phieu: String,
        val soKienXuat: Int,
        val soKienThieu: Int,
        val state: String?,
        val trung: Boolean,
    ) : XuatKetQua

    /**
     * Server CHẶN vì còn kiện chưa quét và chưa có lý do (409 `CON_KIEN_CHUA_QUET`).
     * UI phải hiện `danhSach` + mời nhập lý do — không được im lặng bỏ qua.
     */
    data class ConKienChuaQuet(
        val conLai: Int,
        val danhSach: List<String>,
        val message: String,
    ) : XuatKetQua
}
