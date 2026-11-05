package vn.lab.logistics.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Lab
import vn.lab.logistics.design.blendOver
import vn.lab.logistics.ui.components.Tone

/**
 * =====================================================================================
 * GIỌNG MÀU CỦA KIT — chỗ DUY NHẤT map "trạng thái nghiệp vụ" → màu, và map NHÃN tiếng Việt
 * → [Tone] (spec `ui-dashboard-app-screens.md` §5).
 *
 * Vì sao map theo NHÃN chứ không phải theo mã state: màn Sếp (`Dashboard`) chỉ nhận
 * `stateLabel` từ server ở bảng "việc gần nhất" — nó không có cột `state`. Nếu mỗi màn tự
 * `if (state == …)` thì ba màn sẽ tô ba màu khác nhau cho cùng một việc (đã từng như vậy giữa
 * dashboard và màn tài xế). Quy tắc ở đây dùng CHUNG cho cả 3 màn + bảng của sếp.
 *
 * Nền chip = màu trạng thái **12%** pha trên mặt card (spec §5). Đó cũng là nền mà self-check
 * dùng để đo tương phản của chữ trên chip — đo sau khi pha, không đo trên màu gốc.
 * =====================================================================================
 */

/** Màu gốc của giọng — lấy từ [Lab] (token ADR-009 §6), không pha tay ở màn hình. */
@Composable
@ReadOnlyComposable
fun toneTint(tone: Tone): Color = when (tone) {
    Tone.Ok -> AppTheme.lab.ok
    Tone.Warn -> AppTheme.lab.warn
    Tone.Bad -> AppTheme.lab.danger
    Tone.Info -> AppTheme.lab.info
}

/**
 * Màu CHỮ của giọng: màu đã đo ≥ 4,5:1 trên nền tint 12% tương ứng (`selfCheck` khối 1).
 *
 *  - ok/warn/info dùng ĐÚNG token (`#5FD68A`, `#E8A33D`, `#6EA8FF`) — đo được 6,09 / 5,37 /
 *    4,83:1 trên nền tint của chính nó.
 *  - `bad` dùng `#FF8A80` (bậc sáng hơn của cùng họ đỏ) thay cho `--lab-bad` `#FF6B5E`: trên
 *    nền tint 12% của nó, `#FF6B5E` chỉ đạt 4,39:1 — dưới ngưỡng chữ 4,5:1. Lý do chi tiết ở
 *    KDoc `DarkSemantic`.
 */
@Composable
@ReadOnlyComposable
fun toneText(tone: Tone): Color = when (tone) {
    Tone.Ok -> AppTheme.semantic.okFg
    Tone.Warn -> AppTheme.semantic.warnFg
    Tone.Bad -> AppTheme.semantic.badFg
    Tone.Info -> AppTheme.semantic.infoFg
}

/** Nền tint 12% của giọng trên mặt card hiện tại (một hàm, mọi chip dùng chung). */
@Composable
@ReadOnlyComposable
fun toneFill(tone: Tone): Color = blendOver(toneTint(tone), AppTheme.lab.card, Lab.CHIP_TINT)

/**
 * NHÃN trạng thái (tiếng Việt, do server trả hoặc do app dịch) → [Tone].
 *
 * Bảy nhãn nghiệp vụ của `lab.yeu.cau.lay_hang` và kết quả:
 *  - "Đã về kho"             → Ok
 *  - "Đã lấy được hàng"      → Ok  (chặng lấy hàng đã XONG — xem ghi chú bên dưới)
 *  - "Đã nhận" (màn kho)     → Ok
 *  - "Lấy hàng thất bại"     → Bad
 *  - "Đã huỷ"                → Warn
 *  - "Mới" · "Đã gán tài xế" · "Đang đến lấy" · "Chờ nhận" → Info
 *
 * ⚠️ VÌ SAO "Đã lấy được hàng" LÀ **Ok** Ở ĐÂY NHƯNG `stateToneOf(PICKED_UP)` LÀ **Info**:
 * hai hàm trả lời hai câu khác nhau. Theo NHÃN, đây là chặng đã hoàn tất (bằng chứng pháp lý đã
 * gửi lên Odoo) nên chip phải xanh. Theo MÃ state, `picked_up` vẫn là "việc đang chạy" (hàng chưa
 * về kho) nên phải trung tính — và `JobStateTest` của dự án khoá đúng hành vi đó. Hai quy ước
 * cùng tồn tại có chủ đích; self-check khối 11 in ra CẢ HAI để RV thấy, không để lệch âm thầm.
 *
 * Nhãn lạ (BE đổi chữ) ⇒ Info: chip vẫn đọc được, không thành ô trống, không nổ.
 */
fun labToneOfStatusLabel(label: String): Tone {
    val text = label.trim().lowercase()
    return when {
        // Chặng đã XONG: hàng đã lấy, đã về kho, đã nhận, hoàn tất, thành công.
        text.contains("về kho") ||
            text.contains("đã lấy") ||
            text.contains("đã nhận") ||
            text.contains("hoàn tất") ||
            text.contains("hoàn thành") ||
            text.contains("thành công") ||
            text.contains("đã duyệt") -> Tone.Ok
        // Lỗi THẬT: phải báo CS xử lý ngoại lệ.
        text.contains("thất bại") ||
            text.contains("không lấy") ||
            text.contains("lỗi") ||
            text.contains("từ chối") -> Tone.Bad
        // Bị huỷ / cần để ý: không phải lỗi của tài xế nhưng CS phải biết.
        text.contains("huỷ") || text.contains("hủy") || text.contains("quá hạn") -> Tone.Warn
        else -> Tone.Info
    }
}

/**
 * MÃ trạng thái (thô của model) → [Tone] — bản song song của `stateToneOf` trong
 * `feature/driver`, để kit không phải import tầng `feature`. Self-check khối 11 so hai bảng
 * với nhau: lệch là đỏ, nhờ vậy không có chuyện "sửa một bên".
 */
fun labToneOfState(state: String): Tone = when (state.trim().lowercase()) {
    "confirmed" -> Tone.Ok
    "failed" -> Tone.Bad
    "cancelled" -> Tone.Warn
    else -> Tone.Info
}
