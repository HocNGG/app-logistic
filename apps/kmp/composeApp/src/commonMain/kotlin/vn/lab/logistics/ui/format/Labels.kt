package vn.lab.logistics.ui.format

import kotlin.math.abs
import kotlin.math.round

/**
 * =====================================================================================
 * CÂU CHỮ DÙNG CHUNG cho nhiều màn (hàm THUẦN — không Compose, không mạng).
 *
 * Vì sao tách ra đây thay vì chép ở từng màn: ba câu dưới đây là chỗ dễ "mỗi màn nói một
 * kiểu" nhất, mà màn tài xế và màn kho đang hiển thị CÙNG dữ liệu từ CÙNG model
 * (`lab.yeu.cau.lay_hang`):
 *  - khung giờ hẹn (`windowFrom`/`windowTo`) — server trả chuỗi `HH:mm` hoặc `"-"`;
 *  - số kiện — có thể vắng trong payload, KHÔNG được hiện "0 kiện" (bịa số);
 *  - khối lượng — người Việt đọc dấu phẩy ("12,5 kg"), không phải "12.5 kg".
 *
 * Thuật ngữ theo glossary docs/00 §10: **kiện** (không phải thùng/hộp), **phiếu nhập kho**.
 * =====================================================================================
 */

/**
 * `"08:30–10:00"` · `"Từ 08:30"` · `"Trước 10:00"` · `"Chưa hẹn giờ"`.
 *
 * Server gửi `"-"` khi việc chưa đặt khung giờ — đó KHÔNG phải giờ, phải hiện câu tiếng Việt
 * thay vì dấu gạch trần trên màn demo.
 */
fun formatWindow(from: String?, to: String?): String {
    val batDau = from.windowPart()
    val ketThuc = to.windowPart()
    return when {
        batDau != null && ketThuc != null -> "$batDau–$ketThuc"
        batDau != null -> "Từ $batDau"
        ketThuc != null -> "Trước $ketThuc"
        else -> "Chưa hẹn giờ"
    }
}

/** `4` → `"4 kiện"`; `null` → `"Chưa rõ số kiện"` (KHÔNG bịa 0 kiện). */
fun formatPackages(count: Int?): String = count?.let { "$it kiện" } ?: "Chưa rõ số kiện"

/** `12.5` → `"12,5 kg"`; `null` → `"Chưa rõ khối lượng"`. */
fun formatWeightKg(kg: Double?): String =
    kg?.let { "${formatDecimal(it)} kg" } ?: "Chưa rõ khối lượng"

/**
 * Số thập phân theo cách người Việt đọc: `12.5` → `"12,5"`, `12.0` → `"12"`.
 * Cùng quy ước với `formatKg` của màn capture (làm tròn 1 chữ số thập phân).
 */
fun formatDecimal(value: Double): String {
    val rounded = round(value * 10.0) / 10.0
    val whole = rounded.toLong()
    val decimal = abs(round((rounded - whole) * 10.0).toInt())
    return if (decimal == 0) whole.toString() else "$whole,$decimal"
}

/** Chuỗi giờ hợp lệ; `null`/rỗng/`"-"` ⇒ không có khung giờ. */
private fun String?.windowPart(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() && it != "-" && it != "—" && it != "--" }
