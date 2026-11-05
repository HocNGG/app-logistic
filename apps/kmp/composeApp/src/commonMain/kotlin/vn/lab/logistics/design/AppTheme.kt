package vn.lab.logistics.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.pow

/**
 * =====================================================================================
 * AppTheme — Material **2** (ADR-006: Material 3 còn alpha ở CMP 1.12.2) + CompositionLocal.
 *
 * CHỈ CÒN **MỘT** CHẾ ĐỘ MÀU: TỐI (ADR-009 §6, dark navy + neon lime). Yêu cầu 2026-10-30:
 * "dark là chế độ DUY NHẤT — bỏ hẳn giao diện sáng". Vì vậy hàm [AppTheme] **không còn** tham
 * số `darkTheme`, không còn nhánh `lightColors(...)`, và bảng `LightLabColors` đã bị xoá
 * (không phải "để đó cho vui" — để đó là còn chỗ cho hai màn lệch tông).
 *
 * Ba đường lấy style, không có đường thứ tư:
 *   - `MaterialTheme.colors / typography / shapes`  → màu & chữ chuẩn Material
 *   - `AppTheme.semantic`                            → 4 cặp màu nghiệp vụ ok/warn/bad/info
 *   - `AppTheme.lab`                                 → bảng màu "app screen" đầy đủ (kit dùng cái này)
 * =====================================================================================
 */

private val LocalSemantic = staticCompositionLocalOf { DarkSemantic }
private val LocalLabColors = staticCompositionLocalOf { DarkLabColors }

object AppTheme {
    /** Màu nghiệp vụ ok/warn/bad/info — đường DUY NHẤT để lấy 4 màu này. */
    val semantic: SemanticColors
        @Composable @ReadOnlyComposable get() = LocalSemantic.current

    /**
     * Bảng màu "app screen" đầy đủ của design system (nền / card / mực / đường / nhấn / chart).
     * Đây là đường BẮT BUỘC của bộ kit `ui/kit` và của mọi khối mới:
     * `MaterialTheme.colors` thiếu vai "bề mặt bậc 2" và "đường kẻ", còn hard-code `Brand` thì
     * màn đó không đi theo token khi theme đổi.
     */
    val lab: LabColors
        @Composable @ReadOnlyComposable get() = LocalLabColors.current
}

/**
 * Bọc toàn bộ app bằng bảng màu **duy nhất** của dự án (tối, ADR-009 §6).
 *
 * Không có tham số: nếu một ngày cần thêm chế độ sáng trở lại thì tạo một ADR mới rồi thêm
 * đúng MỘT bảng nữa — không thêm nhánh `if` rải rác ở 12 màn.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSemantic provides DarkSemantic,
        LocalLabColors provides DarkLabColors,
    ) {
        MaterialTheme(
            colors = AppDarkColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/**
 * Bảng màu nền/chữ của Material — khai báo một lần, dùng cho `darkColors(...)` và cho self-check
 * đo tương phản (tránh chép hex ra ngoài `design/`).
 *
 * 9 vai Material theo **ADR-009 §6**:
 *  - `primary` = nhấn lime `#A6F000` · `primaryVariant` = hover `#B8FF28`
 *  - `onPrimary` = **`#080D17`** (chữ trên nền lime — luật §7: cấm chữ trắng trên lime)
 *  - `background` = `#080D17` · `surface` = card `#202A3A`
 *  - `secondary` = info `#6EA8FF` (màu phụ duy nhất còn lại của bảng) · `error` = `#FF6B5E`
 *  - `onBackground` / `onSurface` = `#F5F7FA` · `onError` = `#080D17` (mực tối trên nền đỏ sáng)
 */
object AppPalette {
    /** Nền trang — `--lab-bg`. */
    val background = Brand.ground
    /** Mặt thẻ/panel — `--lab-card`. */
    val surface = Brand.card
    /** Chữ chính trên nền và trên thẻ — `--lab-ink`. */
    val on = Brand.ink

    /** Nhấn: lime (một nhấn duy nhất). */
    val primary = Brand.forest
    val primaryVariant = Brand.forestHover

    /** Màu phụ = info (xanh dương); bậc đậm của nó cho trạng thái pressed (UI chưa dùng). */
    val secondary = Brand.info
    val secondaryVariant = Color(0xFF3F6EA8)

    val error = Brand.danger
    val onError = Brand.ground
}

/**
 * Cặp màu phải qua WCAG AA — nguồn cho self-check/unit test.
 * (Mỗi cặp: màu chữ `first` trên màu nền `second`.)
 *
 * Chỉ còn cặp của chế độ TỐI: app không còn chế độ sáng nên cặp "light: …" đã bỏ cùng bảng màu
 * sáng — không phải "nới ngưỡng", chỉ là không còn gì để đo.
 *
 * Cát (sand) / chart / accentHover cố ý KHÔNG nằm ở đây vì chúng là màu ĐỒ HOẠ/CHART — ngưỡng
 * áp dụng là 3:1, xem [GraphicPairs]. Đây là "assert cho palette": đổi hex trong `Brand` mà
 * quên đo là selfCheck đỏ ngay.
 */
object ContrastPairs {
    /**
     * Nền tint 12% của chip đã TRỘN SẴN lên card — đúng thứ mà mắt nhìn thấy, nên đó là nền dùng
     * để đo tương phản của chữ trên chip (không đo trên màu gốc). Công thức trộn và tỉ lệ là
     * CÙNG một cặp hàm/hằng mà `ui/kit` dùng để vẽ (`blendOver` + [Lab.CHIP_TINT]).
     */
    private val okTint = blendOver(DarkLabColors.ok, DarkLabColors.card, Lab.CHIP_TINT)
    private val warnTint = blendOver(DarkLabColors.warn, DarkLabColors.card, Lab.CHIP_TINT)
    private val badTint = blendOver(DarkLabColors.danger, DarkLabColors.card, Lab.CHIP_TINT)
    private val infoTint = blendOver(DarkLabColors.info, DarkLabColors.card, Lab.CHIP_TINT)

    val ALL: List<Triple<String, Color, Color>> = listOf(
        // ---- nền / mực / bề mặt phân tầng ----
        Triple("chữ chính / nền", DarkLabColors.ink, DarkLabColors.ground),
        Triple("chữ chính / bề mặt 1", DarkLabColors.ink, DarkLabColors.panel),
        Triple("chữ chính / card", DarkLabColors.ink, DarkLabColors.card),
        Triple("chữ chính / card hover", DarkLabColors.ink, DarkLabColors.cardHover),
        Triple("chữ phụ / nền", DarkLabColors.ink2, DarkLabColors.ground),
        Triple("chữ phụ / bề mặt 1", DarkLabColors.ink2, DarkLabColors.panel),
        Triple("chữ phụ / bề mặt 2", DarkLabColors.ink2, DarkLabColors.surface2),
        Triple("chữ phụ / card", DarkLabColors.ink2, DarkLabColors.card),
        // ---- nhấn: v5 (nền sáng) DÙNG MỰC ĐẬM LÀM CHỮ, lime chỉ làm NỀN (chip/CTA/tiến độ).
        //      Lime làm chữ trên nền sáng chỉ 1,35:1 — đây là gốc của 36 mục [HỎNG] ngày 02/11.
        Triple("chữ nhấn (mực đậm) / nền", Brand.accentInk, DarkLabColors.ground),
        Triple("chữ nhấn (mực đậm) / card", Brand.accentInk, DarkLabColors.card),
        Triple("chữ trên nút nhấn", DarkLabColors.onAccent, DarkLabColors.accent),
        Triple("chữ trên nút nhấn (hover)", DarkLabColors.onAccent, DarkLabColors.accentHover),
        // "Đang chọn" ở v5 = PILL ĐEN + chữ trắng (nav active, chip lọc). Lime làm chữ trên nền
        // sáng chỉ ~1,35:1 nên KHÔNG được dùng cho trạng thái chọn — cặp này khoá đúng luật đó.
        Triple("chữ trên pill chọn (chip lọc/nav)", DarkLabColors.onInk, DarkLabColors.ink),
        // ---- 4 màu trạng thái làm CHỮ trên bề mặt: dùng bậc ĐẬM của cùng họ màu ----
        Triple("cảnh báo / card", Brand.warnInk, DarkLabColors.card),
        Triple("nguy hiểm / card", Brand.dangerInk, DarkLabColors.card),
        Triple("ok / card", Brand.okInk, DarkLabColors.card),
        Triple("thông tin / card", Brand.infoInk, DarkLabColors.card),
        // ---- Nền semantic của chip (bảng cố định) — chữ trên đó phải đọc được ----
        Triple("chip ok", DarkSemantic.okFg, DarkSemantic.okBg),
        Triple("chip cảnh báo", DarkSemantic.warnFg, DarkSemantic.warnBg),
        Triple("chip nguy hiểm", DarkSemantic.badFg, DarkSemantic.badBg),
        Triple("chip thông tin", DarkSemantic.infoFg, DarkSemantic.infoBg),
        // ---- và ĐÚNG nền tint 12% mà LabChip vẽ (giá trị thật, không phải bảng) ----
        Triple("chữ trên chip ok (tint 12%)", DarkSemantic.okFg, okTint),
        Triple("chữ trên chip cảnh báo (tint 12%)", DarkSemantic.warnFg, warnTint),
        Triple("chữ trên chip nguy hiểm (tint 12%)", DarkSemantic.badFg, badTint),
        Triple("chữ trên chip thông tin (tint 12%)", DarkSemantic.infoFg, infoTint),
    )
}

/**
 * Cặp màu dùng cho ĐỒ HOẠ (viền, khối, gradient, nét vẽ) — WCAG AA cho thành phần đồ hoạ
 * là **3:1**, không phải 4.5:1 (skill 07 §5). Tách riêng để không ai "nới" ngưỡng chữ.
 */
object GraphicPairs {
    val ALL: List<Triple<String, Color, Color>> = listOf(
        Triple("series 1 (olive) / card", DarkLabColors.chart1, DarkLabColors.card),
        Triple("series 2 (teal đậm) / card", DarkLabColors.chart2, DarkLabColors.card),
        Triple("series 3 (xanh) / card", DarkLabColors.chart3, DarkLabColors.card),
        Triple("series 4 (donut) / card", DarkLabColors.chart4, DarkLabColors.card),
        Triple("dữ liệu nền (sand) / nền", DarkLabColors.chartRest, DarkLabColors.ground),
        Triple("series 1 / nền", DarkLabColors.chart1, DarkLabColors.ground),
        // Vòng focus + vạch nav active: v5 dùng MỰC (đen) — lime trên nền sáng chỉ 1,35:1 nên
        // không còn mang thông tin "đang ở đây" được (đúng cách web đã sửa: nav active = pill đen).
        Triple("vòng focus (mực) / nền", DarkLabColors.ink, DarkLabColors.ground),
        Triple("vạch nav active (mực) / bề mặt 1", DarkLabColors.ink, DarkLabColors.panel),
        // Nền kẻ mảnh (line 10%, grid 8%) là đường PHÂN TÁCH, không mang thông tin khi thiếu nó
        // ⇒ không nằm trong bảng này (WCAG 1.4.11 chỉ áp cho thành phần cần thiết để nhận biết).
    )
}

private val AppDarkColors = darkColors(
    primary = AppPalette.primary, // lime #A6F000
    primaryVariant = AppPalette.primaryVariant, // #B8FF28
    secondary = AppPalette.secondary, // info #6EA8FF
    secondaryVariant = AppPalette.secondaryVariant,
    background = AppPalette.background, // #080D17
    surface = AppPalette.surface, // #202A3A
    error = AppPalette.error, // #FF6B5E
    onPrimary = Brand.onAccent, // #080D17 — chữ trên nền lime
    onSecondary = Brand.onAccent,
    onBackground = AppPalette.on, // #F5F7FA
    onSurface = AppPalette.on,
    onError = AppPalette.onError, // #080D17 — mực tối trên nền đỏ sáng
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(Tokens.rSm),
    medium = RoundedCornerShape(Tokens.rMd),
    large = RoundedCornerShape(Tokens.rLg),
)

/**
 * Typography tối ưu cho TIẾNG VIỆT CÓ DẤU (docs/04 §10, skill 07 §4):
 *  - `lineHeight >= 1.4 × fontSize` ở MỌI style — dấu mũ/móc (ế, ộ, ằ) cao hơn chữ Latin,
 *    lineHeight chật là **cắt dấu**. Vì vậy `button` dùng 21sp chứ không phải 20sp.
 *  - Không `letterSpacing` âm, không `textAllCaps` (nhãn có dấu sẽ dày và xấu).
 *  - body 15sp/23sp (1.53×) theo docs/04 §10.
 *  - Font: **giữ nguyên font hệ thống hiện tại** của Material 2 (`Inter`/`Segoe UI`/`system-ui`
 *    do Windows/Android cung cấp) — không nhúng font, không thêm dependency.
 */
val AppTypography = Typography(
    h4 = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp),
    h5 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    h6 = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    subtitle1 = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    subtitle2 = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp),
    body1 = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 23.sp),
    body2 = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp),
    button = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp),
    caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    overline = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
)

// =====================================================================================
// WCAG AA — đo bằng công thức, không đoán bằng mắt
// =====================================================================================
//
// Chuẩn WCAG 2.1 cấp AA:
//   - chữ thường                          → tương phản >= 4.5 : 1
//   - chữ lớn (>= 24px, hoặc >= 18.66px đậm), và đồ hoạ/biểu tượng → >= 3 : 1
//
// Công thức tương phản:  contrast = (L_sáng + 0.05) / (L_tối + 0.05)
// với L là "độ chói tương đối" (relative luminance) tính từ RGB đã giải gamma sRGB.
//
// Cách kiểm trong dự án này:
//   1) TỰ ĐỘNG khi chạy app: `desktopMain/SelfCheck.kt` in bảng contrast của theme
//      (duy nhất — chế độ tối) + 6 mốc ngưỡng duo UI. Xem output trong báo cáo FE-01.
//      Đây là "kịch bản smoke" offline — không cần GUI, không cần kotlin-test.
//   2) Khi có `kotlin-test` (phải chạy `gradlew :composeApp:build` ONLINE để tải):
//      chuyển các assert đó thành `desktopTest/design/ContrastTest.kt` (skill 07 §5).
//
// Contrast < 4.5 : 1 ở chữ thường = FAIL review. Không có "tạm ổn".

private fun linearize(v: Float): Double {
    val c = v.toDouble()
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

/** Độ chói tương đối theo WCAG 2.1 (0 = đen, 1 = trắng). */
fun relativeLuminance(color: Color): Double =
    0.2126 * linearize(color.red) + 0.7152 * linearize(color.green) + 0.0722 * linearize(color.blue)

/** Tỷ lệ tương phản giữa màu chữ [fg] và màu nền [bg], trả về trong khoảng 1.0 … 21.0. */
fun contrastRatio(fg: Color, bg: Color): Double {
    val a = relativeLuminance(fg)
    val b = relativeLuminance(bg)
    val light = if (a > b) a else b
    val dark = if (a > b) b else a
    return (light + 0.05) / (dark + 0.05)
}

/**
 * ARGB 32-bit của một [Color] — dùng cho self-check so hex trong docs/ADR với mã.
 *
 * Vì sao dùng `toArgb()` chứ không đọc thẳng `value`: `Color.value` là dạng ĐÓNG GÓI 64-bit
 * (có cả id không gian màu), chi tiết đó là nội bộ của Compose và đã đổi giữa các phiên bản;
 * `toArgb()` là API công khai, luôn trả đúng 4 byte ARGB mà mắt nhìn thấy (với màu sRGB).
 * Nhờ vậy assert hex ở self-check không phụ thuộc bản Compose.
 */
fun argbOf(color: Color): ULong = color.toArgb().toLong().toULong() and 0xFFFFFFFFUL

/** Ngưỡng WCAG AA — dùng trong self-check/unit test, không dùng để vẽ UI. */
object WcagAa {
    const val NORMAL_TEXT = 4.5
    const val LARGE_TEXT = 3.0

    fun passesNormalText(fg: Color, bg: Color): Boolean = contrastRatio(fg, bg) >= NORMAL_TEXT
}
