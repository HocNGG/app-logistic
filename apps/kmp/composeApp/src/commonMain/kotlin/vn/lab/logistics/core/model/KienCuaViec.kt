package vn.lab.logistics.core.model

/**
 * =====================================================================================
 * KIỆN CỦA MỘT VIỆC ĐÃ NHẬN — hợp đồng `GET /lab/api/v1/kho/jobs/<id>/kien`
 * (BE-01 ship 2026-11-03; đã gọi thật để xác nhận shape):
 *
 * ```json
 * {"ok": true, "data": {"job": "LH/26/4484", "soKien": 3, "daQuet": 2, "kien": [
 *   {"ma":"KIEN/26/000003","maXuat":"","trangThai":"in_stock","trangThaiLabel":"Trong kho",
 *    "daQuet":true,"quetLuc":"2026-11-03 04:54:01"}, …]}}
 * ```
 * Việc CHƯA nhận ⇒ `{soKien: 0, daQuet: 0, kien: []}` (không phải lỗi).
 *
 * ⚠️ `quetLuc` là **giờ UTC**, dạng `yyyy-MM-dd HH:mm:ss` (hoặc `null`). commonMain KHÔNG có
 * `java.time`/kotlinx-datetime nên app không tự đổi múi giờ — hiển thị kèm hậu tố "UTC" để
 * không nói sai giờ (đúng hơn là im lặng hiện giờ UTC như giờ máy).
 * =====================================================================================
 */
data class KhoKien(
    val ma: String,
    val maXuat: String?,
    val trangThai: String?,
    val trangThaiLabel: String?,
    /** Đã quét tem NHẬN cho kiện này chưa (server đếm từ `lab.scan.event`). */
    val daQuet: Boolean,
    /** Giờ quét (UTC, `yyyy-MM-dd HH:mm:ss`) hoặc `null`. */
    val quetLuc: String?,
) {
    /** Nhãn chip trạng thái quét. */
    val nhanQuet: String get() = if (daQuet) "Đã quét" else "Chưa quét"

    /**
     * Giờ quét rút gọn `"14:54 UTC"` (chỉ giờ:phút, kèm nhãn UTC).
     * Chuỗi server trả `"2026-11-03 04:54:01"` ⇒ lấy phần `HH:mm`.
     */
    val gioQuetLabel: String?
        get() {
            val raw = quetLuc?.trim().orEmpty()
            if (raw.length < 16) return null
            val phanGio = raw.substring(11, 16) // "HH:mm"
            return "$phanGio UTC"
        }
}

/** Toàn bộ kiện của một việc + tiến độ quét do SERVER đếm. */
data class KienCuaViec(
    val job: String?,
    val soKien: Int,
    val daQuet: Int,
    val kien: List<KhoKien>,
) {
    /** Việc chưa được kho nhận ⇒ chưa có kiện nào (empty state, KHÔNG phải lỗi). */
    val chuaNhan: Boolean get() = soKien <= 0 && kien.isEmpty()

    /** Đã quét đủ kiện chưa. */
    val duKien: Boolean get() = soKien > 0 && daQuet >= soKien

    /** Tỉ lệ 0f..1f cho thanh tiến độ. */
    val tiLe: Float get() = if (soKien <= 0) 0f else (daQuet.toFloat() / soKien).coerceIn(0f, 1f)
}
