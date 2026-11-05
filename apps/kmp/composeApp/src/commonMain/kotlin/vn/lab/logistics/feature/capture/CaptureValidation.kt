package vn.lab.logistics.feature.capture

import kotlin.math.abs
import kotlin.math.round

/**
 * =====================================================================================
 * C3/C6 — LUẬT VALIDATE của màn capture (hàm THUẦN, không Compose, không mạng).
 *
 * Vì sao tách khỏi Composable: đây là **bất biến an toàn nghiệp vụ** — "không cho gửi khi
 * thiếu ảnh/chữ ký/số kiện" (C6-a). Hàm thuần ⇒ `commonTest` chứng minh được mà không dựng UI,
 * và `SelfCheck` in lại cùng bộ luật đó mỗi lần mở app.
 *
 * Số liệu đối chiếu backend (`addons/lab_api/controllers/driver.py::capture`):
 *  - `packages` phải > 0 (`so_nguyen` + `if not so_kien: raise UserError`);
 *  - `weightKg` là số thực (`so_thuc`), app yêu cầu > 0 vì 0 kg là vô nghĩa nghiệp vụ;
 *  - `photoBase64` + `signatureBase64` **bắt buộc** (`giai_ma_anh` ném lỗi khi rỗng).
 * =====================================================================================
 */

/** Ba ô ảnh của bước 1 — nhãn đúng thuật ngữ nghiệp vụ (docs/00 §10). */
enum class PhotoSlot(
    val label: String,
    val hint: String,
    val required: Boolean,
) {
    HangHoa(
        label = "Hàng hoá",
        hint = "Toàn cảnh hàng đã lấy trên xe — bắt buộc",
        required = true,
    ),
    KienHang(
        label = "Kiện hàng",
        hint = "Nhãn / niêm phong kiện — bắt buộc",
        required = true,
    ),
    BienNhan(
        label = "Biên nhận giấy",
        hint = "Biên nhận shop ký tay (nếu shop có) — tuỳ chọn",
        required = false,
    ),
}

/** Ảnh đã nén, giữ trong RAM của phiên (không ghi ra đĩa). */
data class PickedPhoto(
    val slot: PhotoSlot,
    val jpegBytes: ByteArray,
    val sourceName: String?,
) {
    /** Kích thước KB để hiện dưới ảnh xem trước. */
    val sizeKb: Int get() = (jpegBytes.size + 1023) / 1024
}

/** Toàn bộ dữ liệu người dùng đã nhập ở 3 bước — đủ để quyết định "gửi được chưa". */
data class CaptureDraft(
    val hasHangHoa: Boolean = false,
    val hasKienHang: Boolean = false,
    val hasBienNhan: Boolean = false,
    val hasSignature: Boolean = false,
    val packagesText: String = "",
    val weightText: String = "",
) {
    /** Đã có bất kỳ bằng chứng nào chưa (dùng để phân biệt trạng thái Empty / Content). */
    val hasAnyEvidence: Boolean
        get() = hasHangHoa || hasKienHang || hasBienNhan || hasSignature ||
            packagesText.isNotBlank() || weightText.isNotBlank()
}

/** Số liệu đã parse hợp lệ — chỉ tồn tại khi qua được [CaptureValidation.blockers]. */
data class CaptureNumbers(val packages: Int, val weightKg: Double)

object CaptureValidation {

    /** Số kiện thực tế tối thiểu (0 kiện = chưa lấy được gì). */
    const val MIN_PACKAGES = 1

    /** Khối lượng tối thiểu — 0 kg là vô nghĩa nghiệp vụ nên app chặn luôn. */
    const val MIN_WEIGHT_KG = 0.01

    /** Số kiện: số nguyên > 0. Chuỗi rỗng / chữ / 0 / số âm ⇒ `null`. */
    fun packages(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it >= MIN_PACKAGES }

    /**
     * Khối lượng (kg): số thực > 0.
     *
     * Chấp nhận **dấu phẩy thập phân** ("12,5") vì bàn phím tiếng Việt và thói quen người
     * dùng gõ phẩy — app tự đổi sang dấu chấm trước khi gửi JSON (server chỉ nhận `12.5`).
     */
    fun weightKg(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= MIN_WEIGHT_KG }

    /** Số liệu đã chuẩn hoá, `null` nếu còn thiếu/sai. */
    fun numbers(draft: CaptureDraft): CaptureNumbers? {
        val soKien = packages(draft.packagesText) ?: return null
        val khoiLuong = weightKg(draft.weightText) ?: return null
        return CaptureNumbers(packages = soKien, weightKg = khoiLuong)
    }

    /**
     * Danh sách LÝ DO chưa gửi được — rỗng nghĩa là gửi được.
     *
     * Trả về **nhiều** lý do (không dừng ở lý do đầu) để UI hiện hết dưới nút bị khoá:
     * tài xế đeo găng tay không nên phải bấm thử 4 lần mới biết còn thiếu gì.
     */
    fun blockers(draft: CaptureDraft): List<String> {
        val reasons = mutableListOf<String>()
        if (!draft.hasHangHoa) reasons += "Thiếu ảnh Hàng hoá (bắt buộc)."
        if (!draft.hasKienHang) reasons += "Thiếu ảnh Kiện hàng (bắt buộc)."
        if (!draft.hasSignature) reasons += "Chưa có chữ ký — ký vào pad ở bước 2."
        if (packages(draft.packagesText) == null) {
            reasons += "Số kiện thực tế phải là số nguyên lớn hơn 0."
        }
        if (weightKg(draft.weightText) == null) {
            reasons += "Khối lượng thực tế (kg) phải là số lớn hơn 0."
        }
        return reasons
    }

    /** Rút gọn: đủ điều kiện gửi hay không. */
    fun canSubmit(draft: CaptureDraft): Boolean = blockers(draft).isEmpty()
}

/**
 * Hiện khối lượng theo cách người Việt đọc: "12,5" (không phải "12.5"), và bỏ ",0" thừa.
 * Hàm thuần — dùng cho Snackbar "Đã gửi biên nhận cho LH/… — 4 kiện, 12,5 kg".
 */
fun formatKg(value: Double): String {
    val rounded = round(value * 10.0) / 10.0
    val whole = rounded.toLong()
    val decimal = abs(round((rounded - whole) * 10.0).toInt())
    return if (decimal == 0) whole.toString() else "$whole,$decimal"
}
