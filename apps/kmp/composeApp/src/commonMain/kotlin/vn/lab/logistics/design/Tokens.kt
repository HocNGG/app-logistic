package vn.lab.logistics.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * =====================================================================================
 * TOKENS — nguồn DUY NHẤT của màu sắc / khoảng cách / kích thước của app.
 *
 * LUẬT (docs/04-kmp-app.md §10, agents/fe/skills/07 §6):
 *  1. Ngoài thư mục `design/` KHÔNG được viết `Color(0x…)`.
 *  2. Ngoài thư mục `design/` KHÔNG được viết số `dp` lẻ — phải dùng `Tokens.*`.
 *
 * Cách kiểm (RV-01 chạy được, không cần mở IDE):
 *     grep -rn "Color(0x" apps/kmp/composeApp/src     → chỉ ra file trong design/
 *     grep -rn "\.dp\b"   apps/kmp/composeApp/src     → chỉ ra design/ + vài chỗ `widthDp.dp`
 *
 * VÌ SAO 3 NGƯỠNG LÀ `Int` CHỨ KHÔNG PHẢI `Dp`:
 * `DuoScaffold` nhận tham số `widthDp: Int` (không đọc `LocalWindowInfo`). Nhờ vậy quyết định
 * layout là **hàm thuần** — unit test/self-check kiểm được khi offline, không cần dựng GUI thật.
 * =====================================================================================
 */
object Tokens {

    // ------------------------------------------------------------------ Spacing: thang 4dp
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 24.dp
    val s6 = 32.dp
    val s7 = 48.dp

    // --------------------------------------------------------------------------- Radius
    // Thang bán kính của ADR-009 §6 là 8 / 10 / 16 / 20 / 24 (`--lab-r-1..5`). App chỉ dùng
    // 3 bậc đầu — không có khối nào bo 20/24 nên KHÔNG khai thêm hằng chết:
    //   rSm = 8  (control: nút, ô nhập, chip lọc — `MaterialTheme.shapes.small`)
    //   rMd = 10 (card/panel nhỏ, vùng chọn)
    //   rLg = 16 (panel/khối lớn)
    // rPill = 999 chỉ cho avatar/pill; chip TRẠNG THÁI cố ý 4dp (spec §5 + self-check khoá lại).
    val rSm = 8.dp
    val rMd = 10.dp
    val rLg = 16.dp
    val rPill = 999.dp

    /** Bo góc card/panel theo `ui-dashboard-app-screens.md` §2 (`--lab-radius-card`). */
    val radiusCard = 10.dp

    /** Bo góc chip trạng thái theo spec (`--lab-radius-chip`): 4dp, không phải pill. */
    val radiusChip = 4.dp

    // --------------------------------- Kích thước điều khiển (docs/04 §5.1: tay đeo găng)
    /** Nút hành động chính (Bắt đầu đi, Đăng nhập, Xác nhận). */
    val touchPrimary = 56.dp
    /** Nút phụ, nút Quay lại, ô nhập liệu thấp nhất. */
    val touchSecondary = 48.dp
    /** Độ dày vòng focus (skill 10: focus ring phải thấy rõ). */
    val focusRing = 2.dp

    // ------------------------------------------------------------------------ Đường kẻ
    val dividerLine = 1.dp
    /**
     * Băng "reserved region" quanh đường giữa 2 pane (skill 03 §5).
     * Trên máy gập, đường gập rơi đúng vào đây → không đặt nút quan trọng trong băng này.
     */
    val reservedBand = 16.dp

    // ------------------------------------------------------- Layout duo (docs/04 §4, §5.2)
    val listPaneWide = 400.dp
    val listPaneNarrow = 300.dp
    val extraPane = 340.dp

    /** Ngưỡng 1: `>= 1280dp` → split rộng (danh sách + chi tiết + cột phụ). */
    const val BREAKPOINT_SPLIT_WIDE_DP = 1280

    /** Ngưỡng 2: `900–1280dp` → split hẹp. `< 900dp` → overlay 1 pane + nút Quay lại. */
    const val BREAKPOINT_SPLIT_NARROW_DP = 900

    /**
     * Ngưỡng 3 (Wave C — ĐIỆN THOẠI): `< 700dp` = **compact**.
     *
     * Vì sao 700 chứ không 600 (ĐO THẬT trên Pixel_7, 2026-10-28): app tính bề rộng nội dung
     * ~**600dp** trên máy ảo này, nên với ngưỡng 600 nó rơi ĐÚNG vào nhánh desktop
     * (`compact = 600 < 600` = false) ⇒ thanh trên xếp hàng ngang làm tiêu đề bị cắt còn
     * "Việc h…" và hiện cả nút "Phím tắt (?)" (vô nghĩa trên điện thoại). Ảnh
     * `docs/review/app-02-driver-list.png` là bằng chứng. 700 vẫn dưới ngưỡng duo UI 900 nên
     * không đổi hành vi tablet/desktop.
     *
     * Dưới ngưỡng này app KHÔNG có nav dọc (nav dọc cần ~236dp, lấy mất 60% màn 390dp) mà dùng
     * thanh trên 2 hàng: wordmark + hàng tab chia đều bề rộng. Nội dung chiếm TRỌN bề rộng màn
     * hình — trên điện thoại, bề rộng thật CHÍNH LÀ bề rộng cần đo.
     *
     * Đây KHÔNG phải ngưỡng nghiệp vụ của duo UI (vẫn là 1280/900 ở trên): nó chỉ quyết định
     * "nav dọc hay nav ngang". Self-check vẫn đo 3 ngưỡng duo UI như cũ.
     */
    const val BREAKPOINT_COMPACT_DP = 700

    // ------------------------- Bề rộng giả lập (chỉ dùng trong màn "Cài đặt demo")
    const val DEMO_WIDTH_MIN_DP = 720
    const val DEMO_WIDTH_MAX_DP = 1600
    const val DEMO_WIDTH_STEP_DP = 20

    /** 3 nút đặt nhanh để demo 3 ngưỡng không cần kéo chuột chính xác. */
    const val DEMO_PRESET_WIDE_DP = 1440
    const val DEMO_PRESET_NARROW_DP = 1100
    const val DEMO_PRESET_OVERLAY_DP = 800

    // ------------------------------------------------------- Kích thước khối UI thông dụng
    val toolbarHeight = 64.dp
    val searchFieldWidth = 320.dp
    val formWidth = 520.dp
    val listRowMinHeight = 64.dp

    // ------------------------------------------------- Màn CAPTURE (ảnh · chữ ký · xác nhận)
    /** Chiều cao pad ký — đủ rộng cho ngón tay/chuột vẽ chữ ký thật (skill 06 §1). */
    val signaturePadHeight = 220.dp
    /** Độ dày nét chữ ký 3–4dp theo đặc tả C1 (đo bằng dp, không phải px). */
    val signatureStroke = 3.5.dp
    /** Khung xem trước ảnh trong mỗi slot (ảnh chụp 1 tay, găng tay → phải to). */
    val photoPreviewHeight = 132.dp
    /** Đường kính chấm số bước trên thanh tiến trình 3 bước. */
    val stepDot = 28.dp
    /** Độ dày thanh tiến trình bước. */
    val progressThickness = 6.dp

    // ------------------------------------------------------------------ Cửa sổ desktop
    /**
     * Cửa sổ mặc định: 1440dp — vừa laptop 14" và đủ rộng cho sidebar 236dp + nội dung (nội dung
     * còn ~1200dp ⇒ split hẹp; kéo rộng/maximize thì lên split rộng 1280dp).
     */
    val windowDefaultWidth = 1440.dp
    val windowDefaultHeight = 900.dp

    // --------------------------------------------------------------------- Elevation
    val elevNone = 0.dp
    val elevCard = 2.dp
    val elevOverlay = 8.dp

    /**
     * Mức bóng của lớp NỔI — **chỉ** dùng cho dialog/menu (`--lab-shadow-1` của ADR-009 §6).
     *
     * Card thì **không** dùng hằng này: §6 chốt "card dùng BỀ MẶT phân tầng, KHÔNG bóng" — card
     * `#202A3A` trên nền `#080D17` đã tách bằng 4 bậc tông, thêm bóng đen trên nền navy gần như
     * vô hình mà vẫn tốn một lớp composite mỗi khung hình (`ui/kit/LabCard.kt`).
     */
    val cardShadow = 6.dp

    // --------------------------------------------------------- Giá trị "không" của viền/màu
    val borderNone = 0.dp
    val transparent = Color(0x00000000)

    // ⚠️ ĐÃ BỎ 4 hằng `ink900` / `ink600` / `ink200` / `ink050` (giấy ấm của ADR-007) cùng lúc
    // với việc bỏ giao diện SÁNG: app chỉ còn MỘT chế độ màu (tối, ADR-009 §6) nên không còn
    // cặp "màu cho chế độ sáng" nào để giữ. Màu mực/nền nay chỉ đến từ `Brand` (bảng token tối)
    // và `LabColors`.

    // ------------------------------------------------------------------------------ Kích thước
    /** Logo ở thanh trên cùng (24–32dp vẫn đọc được — tiêu chí 6 của docs/11 §5). */
    val logoNavSize = 30.dp
    /** Logo vẽ trong card hero của màn Welcome. */
    val logoHeroSize = 96.dp
    /** Khung chứa logo ở hero (card gọn hơn bản Wave B: 176dp → 132dp). */
    val logoHeroCard = 132.dp

    // ------------------------------------------------- Cỡ chữ hero (màn Welcome — Wave B)
    /** Tiêu đề hero 40sp, dòng 56sp → 1.40× (luật tiếng Việt: lineHeight >= 1.4 × fontSize). */
    val heroTitleSize = 40.sp
    val heroTitleLine = 56.sp
    /** Headline hero ở điện thoại (< 600dp) — 40sp không vừa 360dp mà không cắt quá 2 dòng. */
    val heroTitleSizeCompact = 30.sp
    val heroTitleLineCompact = 42.sp
    /** Dòng giá trị 18sp, dòng 26sp → 1.44×. */
    val heroLedeSize = 18.sp
    val heroLedeLine = 26.sp
}

/**
 * =====================================================================================
 * TOKEN CỦA BỘ KIT (`ui/kit`) — mọi con số mà spec `ui-dashboard-app-screens.md` §2–§6 chốt
 * cứng đều nằm ở ĐÂY, không rải trong file composable.
 *
 * Vì sao gom vào `design/`: luật 2 của [Tokens] ("ngoài `design/` không viết số `dp` lẻ").
 * Gom thêm một chỗ để self-check (khối 11) khẳng định "kit vẫn đúng spec" bằng MÁY: đổi bo góc
 * chip thành 8dp, đổi hàng bảng thành 40dp hay đổi tint thành 20% là self-check đỏ ngay.
 *
 * Nguồn từng số:
 *  - `CHIP_TINT 0.12` + `chipRadius 4dp` + `radiusCard 10dp` + `cardShadow` → spec §2, §5.
 *  - `tableRowMinHeight 44dp` + `kpiNumberSize 32sp` + `kpiLabelSize 13sp` + `sparklineHeight 40dp`
 *    → spec §3, §5.
 *  - `NAV_ACTIVE_TINT 0.10` + vạch trái 3dp → `ui-modern-industrial.md` §5 (nav dọc).
 *  - `ENTER_MS 180` + dời 8dp → §6 ("160–220ms") và hạng mục motion của brief.
 * =====================================================================================
 */
object Lab {

    // ------------------------------------------------------------------ nav dọc / nav ngang
    /** Bề rộng sidebar desktop — đủ cho nhãn tiếng Việt dài nhất ("Kho — nhận hàng"). */
    const val NAV_WIDTH_DP = 236

    /** Nav dọc chỉ xuất hiện từ ngưỡng này (cùng ngưỡng SplitNarrow của duo UI). */
    const val SIDEBAR_MIN_WIDTH_DP = Tokens.BREAKPOINT_SPLIT_NARROW_DP

    val navItemHeight = 44.dp
    val navActiveBar = 3.dp
    /** Vạch dưới của tab đang mở ở nav NGANG (điện thoại) — 2dp, mảnh hơn vạch trái sidebar. */
    val navUnderline = 2.dp
    val navGroupGap = Tokens.s5

    /**
     * Bo góc của khối nền "mục đang mở" (8dp — MASTER §8).
     * Vì sao không phải pill 999dp: pill chỉ dành cho avatar; nav item là khối chữ nhật mềm.
     * Vì sao không phải 0dp (full-bleed như bản cũ): nền tràn hết bề rộng sidebar đọc như
     * "dòng bị tô màu", trong khi khối bo góc + thụt lề đọc như "mục đang chọn".
     */
    val navItemRadius = 8.dp

    /** Thụt lề ngang của khối nền nav (để khối không dính sát mép sidebar). */
    val navItemInset = Tokens.s2

    /** Nền của mục đang mở Ở NAV NGANG (điện thoại): nhấn 14% + bo góc — không gạch chân. */
    const val NAV_PILL_TINT = 0.14f

    /** Nền item đang mở = nhấn 10% (spec §5); vạch trái 3dp cùng màu nhấn. */
    const val NAV_ACTIVE_TINT = 0.10f

    // ------------------------------------------------------------------------- chip trạng thái
    val chipRadius = Tokens.radiusChip
    const val CHIP_TINT = 0.12f
    val chipPadH = Tokens.s2
    val chipPadV = 2.dp
    val chipTextSize = 13.sp
    val chipTextLine = 19.sp

    // -------------------------------------------------------------------------------- bảng
    val tableRowMinHeight = 44.dp
    val tableCellPadH = Tokens.s3
    val tableCellPadV = Tokens.s2
    val tableTextSize = 13.sp
    val tableTextLine = 19.sp
    val tableHeaderPadV = Tokens.s2

    // ----------------------------------------------------------------------------- KPI card
    val kpiLabelSize = 13.sp
    val kpiLabelLine = 19.sp
    val kpiNumberSize = 32.sp
    val kpiNumberLine = 40.sp
    val sparklineHeight = 40.dp

    // ------------------------------------------------------------------------------ khung trang
    /** Nội dung không kéo hết màn 27" — quá 1320dp là mắt phải quét ngang, chữ dài khó đọc. */
    const val CONTENT_MAX_WIDTH_DP = 1320

    /**
     * Bề rộng tối đa của một cột CHỮ (hero, form đăng nhập, màn thông báo): 980dp để dòng chữ
     * vẫn trong khoảng 45–75 ký tự — đọc được, không phải quét mắt.
     */
    const val READING_MAX_WIDTH_DP = 980

    val toolbarMinHeight = 60.dp
    val sectionGap = Tokens.s5
    val emptyIconSize = 44.dp

    // -------------------------------------------------------------------------- chuyển động
    /** Entrance 180ms nằm trong khoảng 160–220ms của `ui-modern-industrial.md` §6. */
    const val ENTER_MS = 180
    val enterOffset = 8.dp
    const val HOVER_MS = 160
}

/**
 * Bốn cặp màu NGHIỆP VỤ. Mọi màn hình lấy màu trạng thái **chỉ** qua `AppTheme.semantic`,
 * không tự định nghĩa màu mới (skill 07 §6).
 *
 * Quy ước dùng:
 *  - `ok`   — đã duyệt, đã lấy hàng, đã về kho
 *  - `warn` — cần chú ý: sắp quá tải, kết nối không mã hoá, chờ gán quá lâu
 *  - `bad`  — quá tải, lỗi, không lấy được hàng
 *  - `info` — đang đồng bộ, gợi ý, trạng thái chờ
 *
 * Bảng màu: **ADR-009 §6** (dark navy + neon lime) — `ok` mint `#5FD68A`, `warn` hổ phách
 * `#E8A33D`, `bad` đỏ coral `#FF6B5E`, `info` xanh dương `#6EA8FF`. Mỗi cặp là **hai** màu
 * của MỘT chip: `xxBg` = màu trạng thái pha 12% lên `--lab-card` (đúng giá trị `LabChip` vẽ),
 * `xxFg` = màu chữ đo trên chính nền đó. Mọi cặp ở chế độ TỐI đã đo ≥ 4.5:1 và in ra khi chạy
 * `selfCheck` (khối 1 + khối 1b) — đổi hex mà quên đo là self-check đỏ ngay.
 */
@Immutable
data class SemanticColors(
    val okBg: Color,
    val okFg: Color,
    val warnBg: Color,
    val warnFg: Color,
    val badBg: Color,
    val badFg: Color,
    val infoBg: Color,
    val infoFg: Color,
)

/**
 * BỐN CẶP MÀU NGHIỆP VỤ — chỉ còn MỘT bảng (app chỉ có một chế độ, ADR-009 §6).
 *
 * `xxBg` là nền chip TĨNH (bản sáng v5: tint rất nhạt của chính họ màu), `xxFg` là **MỰC ĐẬM**
 * cùng họ — vì chữ lime/mint trên nền sáng chỉ đạt 1,2–3,4:1 (self-check đo được, 36 mục [HỎNG]
 * ngày 2026-11-02).
 *
 * Số đo (in ra khi chạy `selfCheck`, không đoán): xem khối 1 — mọi cặp phải ≥ 4,5:1.
 *
 * Ba giọng ok/warn/info dùng **ĐÚNG** token `Brand.*Ink` làm màu chữ (không chép hex lại), nên
 * đổi bậc đậm ở một chỗ là đổi cả chip — self-check khối 5 khoá lại điều đó.
 */
val DarkSemantic = SemanticColors(
    okBg = Color(0xFFE7F4EC), okFg = Brand.okInk,
    warnBg = Color(0xFFF7EFDF), warnFg = Brand.warnInk,
    badBg = Color(0xFFF9E7E4), badFg = Brand.dangerInk,
    infoBg = Color(0xFFE6EEF6), infoFg = Brand.infoInk,
)

/**
 * =====================================================================================
 * BRAND PALETTE — **bảng token TỐI của ADR-009 §6** (dark navy + neon lime).
 * Đây là nguồn màu DUY NHẤT cho màn hình app; ngoài `design/` không được viết `Color(0x…)`.
 *
 * Vì sao giữ tên `Brand` (thay vì đổi thành `Tokens.Dark`): 6 file ngoài `design/`
 * (`LogoMark`, `SignaturePad`) đọc trực tiếp `Brand.ink` / `Brand.ground` / `Brand.forest` —
 * đổi tên là phải sửa cả 6 file mà không được lợi gì. Chỉ GIÁ TRỊ thay đổi; tên giữ nguyên.
 *
 * NGỮ NGHĨA (kể được trong demo, không phải trang trí):
 *  - `ground` / `surface` / `surface2` / `card` / `cardHover` — 5 bậc nền: nền trang → nav →
 *    khối nội dung → card → hover. Đây là "bề mặt phân tầng" thay cho bóng đổ.
 *  - `forest` (lime `#A6F000`) — nhấn DUY NHẤT: CTA, nav active, series 1 của chart.
 *  - `amber` — hổ phách: cảnh báo, cần chú ý. `danger` — đỏ coral: lỗi, huỷ, thất bại.
 *  - `ok` / `info` — mint và xanh dương cho 2 trạng thái còn lại của chip.
 *
 * ⚠️ RÀNG BUỘC TƯƠNG PHẢN (đo bằng `contrastRatio` trong `selfCheck`, không đoán):
 *  - trên `ground`: `ink` 18.11:1 · `inkMuted` 7.00:1 · `forest` 13.96:1 · `amber` 9.01:1.
 *  - trên `card`: `ink` 13.46:1 · `inkMuted` 5.20:1 · `forest` 10.38:1.
 *  - chữ trên nền nhấn: `onAccent` 13.96:1 — **cấm** chữ trắng trên lime.
 *  - đồ hoạ (viền/lưới/nét) ngưỡng 3:1: `chart2` 7.89:1 · `chart3` 5.99:1 · `sand` 7.00:1.
 *  - `ink3` 4.10:1 trên nền / 3.05:1 trên card ⇒ **chỉ** cho nhãn phụ và chữ disabled
 *    (ADR-009 §7), KHÔNG dùng cho câu chữ đọc liền.
 * =====================================================================================
 */
object Brand {
    // ------------------------------------------------------- Nền: 5 bậc "bề mặt phân tầng"
    /** Nền trang — `--lab-bg` `#080D17`. */
    val ground = Color(0xFFF3F2F8)
    /** Bề mặt 1: sidebar, top nav — `--lab-s1` `#101725`. */
    val surface = Color(0xFFFFFFFF)
    /** Bề mặt 2: khối nội dung, header bảng, filter bar — `--lab-s2` `#192231`. */
    val surface2 = Color(0xFFF6F6FB)
    /** Card KPI / bảng / panel — `--lab-card` `#202A3A`. */
    val card = Color(0xFFFFFFFF)
    /** Hover của card/dòng — `--lab-card-2` `#263245`. */
    val cardHover = Color(0xFFF6FBE8)

    // ------------------------------------------------------------------------------- Mực chữ
    /** Chữ chính — 18.11:1 trên `ground`. */
    val ink = Color(0xFF0B0B12)
    /** Chữ phụ, nhãn — 7.00:1 trên `ground`. */
    val inkMuted = Color(0xFF6B6B78)
    /** Mực mờ: CHỈ nhãn phụ + chữ disabled (3.05:1 trên card — dưới ngưỡng chữ thường). */
    val ink3 = Color(0xFF9A9AA6)

    /**
     * Đường kẻ mảnh — `rgba(255,255,255,.08)`. Khai bằng ALPHA (không phải hex đục) đúng token
     * §6, nên nó đúng trên MỌI bề mặt (ground, sidebar, card) mà không cần 3 biến riêng.
     */
    val line = Color(0x1A101020)

    /** Viền mạnh — `rgba(255,255,255,.14)`: input focus, khung chọn, viền nút phụ. */
    val lineStrong = Color(0x1F101020)

    // --------------------------------------------------------------------- MỘT màu nhấn
    /** Nhấn chính (lime) — NỀN của CTA/nav active/series (KHÔNG dùng làm CHỮ trên nền sáng). */
    val forest = Color(0xFFC2EE2F)
    /** Hover/pressed của nhấn — `--lab-accent-2`. */
    val forestHover = Color(0xFFD4F84E)
    /** Bậc tối của nhấn — mặt trái khối logo (ĐỒ HOẠ, không dùng làm chữ). */
    val forestDeep = Color(0xFF8FBF00)
    /** Lime rất nhạt — chấm hành trình trong logo (ĐỒ HOẠ, không dùng làm chữ). */
    val forestPale = Color(0xFFE9FCC4)
    /**
     * Mực TRÊN nền nhấn — `--lab-on-accent` `#2B3300`: 9,87:1 trên lime. Quy tắc ADR-009 §7:
     * trên lime **bắt buộc** chữ tối; chữ trắng trên lime chỉ ~1,5:1.
     */
    val onAccent = Color(0xFF2B3300)

    /**
     * Chữ TRÊN nền MỰC — pill CTA và chip lọc ĐANG CHỌN (v5: "chọn" = pill đen, chữ trắng).
     * Trắng `#FFFFFF` trên `ink` = **19,6:1**. Không có token trắng nào khác cho chữ.
     */
    val onInk = Color(0xFFFFFFFF)

    // ------------------------------------------------- BỘ "MỰC ĐẬM" CHO NỀN SÁNG (v5, 2026-11-02)
    //
    // VÌ SAO CẦN: app đã đổi sang nền SÁNG (v5) nhưng nhiều chỗ vẫn dùng lime/mint/teal làm
    // **CHỮ** — đúng với nền tối cũ, sai hoàn toàn với nền sáng: lime `#C2EE2F` trên trắng chỉ
    // **1,35:1** (self-check đo, 36 mục [HỎNG]). Bộ dưới là "bậc đậm của CÙNG họ màu" để dùng cho
    // chữ/chip; lime/mint vẫn giữ vai **nền** (chip, thanh tiến độ, khối nhấn).
    // Đây đúng cách đã sửa ở web (`--lab-accent-ink`, `--lab-ok-ink`, …) ⇒ hai bề mặt cùng tông.
    /** Chữ trên chip lime / chữ nhấn — olive đậm, 9,01:1 trên trắng. */
    val accentInk = Color(0xFF3F4F00)
    /** Chữ trạng thái "đã về kho / đã lấy" — xanh lá đậm, 4,80:1 trên trắng. */
    val okInk = Color(0xFF1F7A47)
    /** Chữ trạng thái "chờ gán / sát ngưỡng" — nâu hổ phách đậm, 6,61:1 trên trắng. */
    val warnInk = Color(0xFF7A5200)
    /** Chữ trạng thái "thất bại / huỷ" — đỏ đậm, 6,54:1 trên trắng. */
    val dangerInk = Color(0xFF7F1D17)
    /** Chữ trạng thái "đang đi lấy" — xanh dương đậm, 7,06:1 trên trắng. */
    val infoInk = Color(0xFF1F5A8A)

    // ------------------------------------------------------------------------ Cảnh báo/nguy hiểm
    /** Hổ phách — CHỈ cho "cần chú ý" (nền/chấm), không dùng làm chữ trên nền sáng. */
    val amber = Color(0xFFA06A00)
    /** Bậc tối của hổ phách (mặt phải khối logo). */
    val amberDeep = Color(0xFF6B4600)
    /** Đỏ — lỗi, huỷ, thất bại (nền/chấm). */
    val danger = Color(0xFFB3261E)
    /** Xanh lá — đã về kho, đã nhận (nền/chấm). */
    val ok = Color(0xFF2F9E5F)
    /** Xanh dương — đang đi lấy, thông tin, đang đồng bộ (nền/chấm). */
    val info = Color(0xFF2A6EA8)

    // ---------------------------------------------------------------------- Chart (một họ màu)
    /**
     * Trung tính cho dữ liệu nền/so sánh và lát "Khác" của donut — không phải màu dữ liệu mới.
     * `#6B6B78` = 4,72:1 trên trắng ⇒ dùng được cả cho chữ phụ.
     */
    val sand = Color(0xFF6B6B78)
    /**
     * Series chính (`--lab-c1`) — OLIVE ĐẬM `#6F9600`, 3,48:1 trên trắng.
     *
     * Vì sao không dùng lime `#C2EE2F`: nét biểu đồ là ĐỒ HOẠ cần ≥ 3:1 (WCAG 1.4.11); lime trên
     * trắng chỉ **1,35:1** ⇒ người xem không thấy đường. Đây đúng cách web đã sửa (`--lab-c1`).
     */
    val chart1 = Color(0xFF6F9600)
    /**
     * Series 2 (`--lab-c2`) — TEAL ĐẬM `#12857A`, 4,50:1 trên trắng.
     * (Bản tối cũ `#63D8C6` chỉ 1,73:1 trên nền sáng.)
     */
    val chart2 = Color(0xFF12857A)
    /** Series 3 (`--lab-c3`) — xanh dương đậm `#2A6EA8`, 5,39:1 trên trắng. */
    val chart3 = info
    /**
     * Lát thứ 4 của donut. Bảng §6 chỉ có 3 màu series, nên lát thứ 4 dùng hổ phách — màu còn
     * lại của bảng, tách hẳn 3 sắc lạnh. Donut LUÔN có legend chữ nên màu không phải tín hiệu
     * duy nhất (ADR-009 §7).
     */
    val chart4 = amber
    /** CHỈ cho ngưỡng vượt/cần chú ý trong chart. */
    val chartWarn = amber

    // ------------------------------------------------------------------- Viền/lưới mờ trên nền
    /** Lưới biểu đồ — `rgba(255,255,255,.06)` (`--lab-grid`). */
    val gridLine = Color(0x14101020)
    /** Viền mảnh cho khối nội dung — alpha của `inkMuted`. */
    val hairline = Color(0x1A101020)
}
