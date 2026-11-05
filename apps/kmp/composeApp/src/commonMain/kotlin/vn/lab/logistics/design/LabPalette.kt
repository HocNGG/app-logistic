package vn.lab.logistics.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * =====================================================================================
 * LAB COLORS — bảng màu "app screen" của design system, dùng cho BỘ KIT `ui/kit`.
 *
 * CHỈ CÒN **MỘT** BẢNG (2026-10-30): bảng `LightLabColors` (giấy ấm ADR-007) đã **BỎ** cùng
 * lúc với giao diện sáng — app chỉ có chế độ tối theo **ADR-009 §6** (dark navy + neon lime).
 * Trước đây file này có `LightLabColors` + `DarkLabColors` và chế độ sáng là mặc định; nay
 * mọi thứ đọc đúng một bảng, nên không còn chỗ nào có thể lệch tông giữa hai chế độ.
 *
 * Vì sao cần bảng này (bên cạnh `MaterialTheme.colors` và [SemanticColors]):
 *  - `MaterialTheme.colors` chỉ có 12 vai của Material, thiếu những vai mà spec
 *    `ui-dashboard-app-screens.md` §2 đòi: bề mặt bậc 2 (header bảng, sidebar), màu đường kẻ,
 *    thang màu của chart, màu chữ cảnh báo ĐỌC ĐƯỢC trên nền tint.
 *  - [SemanticColors] chỉ có 4 cặp ok/warn/bad/info cho chip — không có nền/chữ/mực.
 *
 * TƯƠNG PHẢN (đo bằng `contrastRatio`, in ra khi chạy self-check — không đoán):
 *  - chữ chính / card: 13,46:1 · chữ phụ / card: 5,20:1 · nhấn / card: 10,38:1
 *  - chữ trên chip (nền tint 12%): ok 6,09:1 · warn 5,37:1 · bad 5,37:1 · info 4,83:1
 *  - chữ trên nút nhấn (`onAccent` trên `accent`): 13,96:1
 *
 * Vì sao vẫn giữ [warnInk] (một trường riêng cho CHỮ cảnh báo) dù nay nó trùng [warn]: bảng cũ
 * cần nó vì hổ phách của bảng cũ chỉ đạt 3,50:1 trên giấy kem — đủ cho ĐỒ HOẠ (ngưỡng 3:1) nhưng
 * KHÔNG đủ cho chữ thường. Bảng mới không cần tách nữa (`#E8A33D` đạt 6,70:1 trên card), nhưng
 * giữ TÊN trường vì `ui/kit` + self-check đọc nó — đổi tên chỉ tạo diff rỗng nghĩa.
 * =====================================================================================
 */
@Immutable
data class LabColors(
    /** Nền toàn app — `--lab-bg` `#080D17`. */
    val ground: Color,
    /** Bề mặt bậc 1: sidebar, header bảng, nav ngang — `--lab-s1` `#101725`. */
    val panel: Color,
    /** Bề mặt bậc 2: khối nội dung, filter bar, header bảng — `--lab-s2` `#192231`. */
    val surface2: Color,
    /** Bề mặt nổi: card, dialog, thanh công cụ — `--lab-card` `#202A3A`. */
    val card: Color,
    /** Hover của card/dòng bảng — `--lab-card-2` `#263245`. */
    val cardHover: Color,
    /** Mực chữ chính. */
    val ink: Color,
    /** Mực chữ phụ, nhãn, ghi chú. */
    val ink2: Color,
    /** Mực mờ: nhãn phụ + chữ disabled (KHÔNG dùng cho câu chữ đọc liền — xem ADR-009 §7). */
    val ink3: Color,
    /** Đường kẻ 1px DUY NHẤT cho control/bảng — alpha trắng 8%. */
    val line: Color,
    /** Viền mạnh — alpha trắng 14%: input focus, khung chọn, viền nút phụ. */
    val lineStrong: Color,
    /** Màu nhấn DUY NHẤT (neon lime): hành động chính, nav active, series 1 của chart. */
    val accent: Color,
    /** Hover/pressed của nhấn. */
    val accentHover: Color,
    /** Mực TRÊN nền nhấn — chữ trên nút/CTA lime (13,96:1). Cấm chữ trắng trên lime. */
    val onAccent: Color,
    /**
     * Mực TRÊN nền MỰC — chữ trắng trên pill đen / chip lọc đang chọn (v5, 19,6:1 trên [ink]).
     * Tách khỏi [onAccent] vì hai cặp này KHÔNG thay thế nhau được: trắng trên lime chỉ ~1,5:1.
     */
    val onInk: Color,
    /** Hổ phách cho ĐỒ HOẠ (vạch, chấm, ngưỡng) — 6,70:1 trên card nên dùng được cả làm chữ. */
    val warn: Color,
    /** Hổ phách cho CHỮ cảnh báo (tên giữ nguyên từ bảng cũ — nay cùng giá trị với [warn]). */
    val warnInk: Color,
    /** Đỏ coral: lỗi, huỷ, thất bại. */
    val danger: Color,
    /** Mint: thành công, đã nhận, đã về kho. */
    val ok: Color,
    /** Xanh dương: thông tin, đang đi lấy, đang đồng bộ. */
    val info: Color,
    /** Chart series 1 (`--lab-c1`) — dữ liệu chính. */
    val chart1: Color,
    /** Chart series 2 (`--lab-c2`) — teal. */
    val chart2: Color,
    /** Chart series 3 (`--lab-c3`) — xanh dương. */
    val chart3: Color,
    /** Chart series 4 — lát thứ 4 của donut (xem KDoc `Brand.chart4`). */
    val chart4: Color,
    /** Phần dư của donut ("Khác") — trung tính, không phải màu dữ liệu mới. */
    val chartRest: Color,
    /** Lưới + đường lưới của chart — alpha trắng 6% (`--lab-grid`). */
    val grid: Color,
) {
    /** 4 màu của donut, đúng thứ tự (≤ 5 phần theo spec §4). */
    val donut: List<Color> get() = listOf(chart1, chart2, chart3, chart4)
}

/**
 * BẢNG MÀU CỦA APP — chép nguyên token **ADR-009 §6**. Đây là bảng DUY NHẤT còn được dùng để
 * vẽ: `AppTheme` cắm nó vào `CompositionLocal` và mọi màn/kit đọc lại qua `AppTheme.lab`.
 *
 * Không có bảng thứ hai: giao diện sáng (giấy ấm ADR-007) đã bị bỏ theo yêu cầu "dark là chế
 * độ DUY NHẤT", nên `LightLabColors` **không còn tồn tại** — nếu còn, đó sẽ là chỗ để hai màn
 * lệch tông mà không ai thấy.
 */
val DarkLabColors = LabColors(
    ground = Brand.ground, // --lab-bg        #F3F2F8 (v5 sáng)
    panel = Brand.surface, // --lab-s1        #FFFFFF
    surface2 = Brand.surface2, // --lab-s2        #F6F6FB
    card = Brand.card, // --lab-card      #FFFFFF
    cardHover = Brand.cardHover, // --lab-card-2    #F6FBE8
    ink = Brand.ink, // --lab-ink       #0B0B12
    ink2 = Brand.inkMuted, // --lab-ink-2     #6B6B78
    ink3 = Brand.ink3, // --lab-ink-3     #9A9AA6
    line = Brand.line, // --lab-line      rgba(11,11,18,.10)
    lineStrong = Brand.lineStrong, // --lab-line-2    rgba(11,11,18,.12)
    accent = Brand.forest, // --lab-accent    #C2EE2F (NỀN nhấn)
    accentHover = Brand.forestHover, // --lab-accent-2  #D4F84E
    onAccent = Brand.onAccent, // --lab-on-accent #2B3300
    onInk = Brand.onInk, // chữ trên pill đen #FFFFFF (19,6:1 trên ink)
    // Chữ trạng thái = BẬC ĐẬM (v5): lime/mint/teal làm CHỮ trên nền sáng chỉ 1,2–3,4:1.
    warn = Brand.warnInk, // #7A5200 — chữ cảnh báo 6,61:1 trên card
    warnInk = DarkSemantic.warnFg, // #7A5200
    danger = Brand.dangerInk, // #7F1D17 — chữ lỗi/thất bại
    ok = DarkSemantic.okFg, // #1F7A47 — chữ "đã về kho"
    info = Brand.infoInk, // #1F5A8A — chữ "đang đi lấy"
    chart1 = Brand.chart1, // --lab-c1 #6F9600 (olive đậm, 3,48:1)
    chart2 = Brand.chart2, // --lab-c2 #12857A (teal đậm, 4,50:1)
    chart3 = Brand.chart3, // --lab-c3 #2A6EA8
    chart4 = Brand.chart4, // hổ phách #A06A00 — lát thứ 4 của donut
    chartRest = Brand.sand, // trung tính #6B6B78 — lát "Khác"
    grid = Brand.gridLine, // --lab-grid      rgba(11,11,18,.08)
)

/**
 * Trộn [foreground] lên [background] với độ đục [alpha] — đúng cách Compose vẽ nền tint 12%
 * của chip. Hàm THUẦN nên self-check đo được tương phản của cặp "chữ trên nền tint" **sau khi
 * trộn**, thay vì đo trên màu gốc rồi tin là đúng.
 */
fun blendOver(foreground: Color, background: Color, alpha: Float): Color {
    val a = alpha.coerceIn(0f, 1f)
    return Color(
        red = foreground.red * a + background.red * (1f - a),
        green = foreground.green * a + background.green * (1f - a),
        blue = foreground.blue * a + background.blue * (1f - a),
        alpha = 1f,
    )
}
