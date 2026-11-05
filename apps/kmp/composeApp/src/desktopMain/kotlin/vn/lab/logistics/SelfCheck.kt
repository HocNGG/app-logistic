package vn.lab.logistics

import vn.lab.logistics.core.media.ImageKind
import vn.lab.logistics.core.media.MAX_IMAGE_BYTES
import vn.lab.logistics.core.media.compressJpeg
import vn.lab.logistics.core.media.isSupportedImage
import vn.lab.logistics.core.media.sniffImageKind
import vn.lab.logistics.core.model.JobState
import vn.lab.logistics.core.model.Workspace
import vn.lab.logistics.core.net.assignRequestBody
import vn.lab.logistics.core.net.parseAdminAccountsPayload
import vn.lab.logistics.core.net.parseAssignResult
import vn.lab.logistics.core.net.parseCsJobsPayload
import vn.lab.logistics.core.net.parseDriverJobsPayload
import vn.lab.logistics.core.net.parseKhoChoNhanPayload
import vn.lab.logistics.core.net.parseKhoReceiveResult
import vn.lab.logistics.core.net.parseSwitchSessionPayload
import vn.lab.logistics.core.settings.AppSettings
import vn.lab.logistics.core.settings.InMemorySettingsStorage
import vn.lab.logistics.design.AppPalette
import vn.lab.logistics.design.AppTypography
import vn.lab.logistics.design.Brand
import vn.lab.logistics.design.ContrastPairs
import vn.lab.logistics.design.DarkLabColors
import vn.lab.logistics.design.GraphicPairs
import vn.lab.logistics.design.Lab
import vn.lab.logistics.design.Tokens
import vn.lab.logistics.design.WcagAa
import vn.lab.logistics.design.argbOf
import vn.lab.logistics.design.contrastRatio
import vn.lab.logistics.design.relativeLuminance
import vn.lab.logistics.feature.admin.accountLabel
import vn.lab.logistics.feature.admin.shouldShowRoleSwitcher
import vn.lab.logistics.feature.admin.switcherLabel
import vn.lab.logistics.feature.capture.CaptureDraft
import vn.lab.logistics.feature.capture.CaptureValidation
import vn.lab.logistics.feature.capture.isValidClientUuid
import vn.lab.logistics.feature.capture.newClientUuid
import vn.lab.logistics.feature.capture.openCaptureSession
import vn.lab.logistics.feature.cs.accountLine
import vn.lab.logistics.feature.cs.displayLabel
import vn.lab.logistics.feature.driver.packagesLabel
import vn.lab.logistics.feature.driver.stateLabelOf
import vn.lab.logistics.feature.driver.stateToneOf
import vn.lab.logistics.feature.driver.statusLabel
import vn.lab.logistics.feature.driver.windowLabel
import vn.lab.logistics.ui.format.formatPackages
import vn.lab.logistics.ui.format.formatWeightKg
import vn.lab.logistics.ui.format.formatWindow
import vn.lab.logistics.ui.components.DuoArrangement
import vn.lab.logistics.ui.components.LOGO_APEX_X
import vn.lab.logistics.ui.components.LOGO_APEX_Y
import vn.lab.logistics.ui.components.LOGO_BOTTOM_Y
import vn.lab.logistics.ui.components.LOGO_LEFT_X
import vn.lab.logistics.ui.components.LOGO_RIGHT_X
import vn.lab.logistics.ui.components.LOGO_SHOULDER_Y
import vn.lab.logistics.ui.components.LOGO_VIEWBOX
import vn.lab.logistics.ui.components.Tone
import vn.lab.logistics.ui.kit.labToneOfState
import vn.lab.logistics.ui.kit.labToneOfStatusLabel
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Random
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream

/**
 * =====================================================================================
 * TỰ KIỂM TRA KHI KHỞI ĐỘNG (desktopMain — chỉ dùng ở đây, không vào commonMain).
 *
 * VÌ SAO CẦN (bên cạnh `commonTest`): đây là "kịch bản smoke" chạy được **không cần GUI** và
 * **không cần framework test** — in ra console mỗi lần mở app. Nó kiểm cả những thứ unit test
 * khó kiểm: đọc chính mã nguồn (`src/`) để chống hardcode, và parse payload THẬT của API.
 *
 *   1. WCAG AA (>= 4.5:1) cho mọi cặp màu CHỮ của theme duy nhất (tối, ADR-009 §6): mực/nền,
 *      mực/bề mặt, nhấn/chữ-trên-nút-nhấn, 4 màu trạng thái + chữ trên chip tint 12%.
 *   2. Ba ngưỡng duo UI đúng biên 1280/900 (gồm cả giá trị ngay dưới biên).
 *   3. Luật typography tiếng Việt: `lineHeight >= 1.4 × fontSize` ở mọi TextStyle.
 *   4. Ánh xạ 7 trạng thái `lab.yeu.cau.lay_hang` (mã của model BE-01) → nhãn tiếng Việt + giọng màu,
 *      và fallback cho state lạ. Đây là "hợp đồng" dễ lệch nhất giữa app và Odoo.
 *   5. Brand palette khớp docs/11 §3 · 6. Hình học logo (vẽ bằng Canvas).
 *   7. **Wave B — parse payload THẬT** của `GET /lab/api/v1/driver/jobs`: `windowFrom == "01:30"`,
 *      `expectedPackages` VẮNG ⇒ `null` không nổ, `window "-"` ⇒ "Chưa hẹn giờ", `stateLabel`
 *      của server được ưu tiên, chuỗi rỗng ⇒ `null`.
 *   8. **Wave B — không hardcode URL/token**: grep chính thư mục `src/` (chỉ `AppSettings.kt`
 *      được chứa địa chỉ mặc định; không file CODE nào được chứa token/mật khẩu literal).
 *   9. **Bất biến an toàn CAPTURE**: không gửi khi thiếu ảnh/chữ ký/số kiện/kg; ảnh nén ≤ 5 MB
 *      và vẫn là JPEG (nén THẬT một ảnh nhiễu lớn); không hardcode token; `clientUuid` là UUID
 *      v4 hợp lệ và **giữ nguyên khi Thử lại** (idempotency — `driver.py::capture`).
 *  10. **Chọn màn theo vai trò** (ưu tiên admin → driver → driver_leader → cs → kho → dashboard;
 *      vai trò khác/rỗng ⇒ màn thông báo) + parse payload `cs/jobs` và `kho/cho-nhan` của 2 màn
 *      mới, gồm cả trường VẮNG. Payload `cs/jobs` mang `drivers[].login` (hợp đồng mới với BE-01)
 *      và phải hiện đúng "Trần Văn Hùng · taixe01" trong ô chọn tài xế.
 *  10b. **ADMIN — chuyển tài khoản**: parse `admin/accounts` (thiếu `name`/`roles` không vỡ),
 *      parse `admin/switch` (shape login + `login`), và **ghi phiên mới** vào `AppSettings`:
 *      token · uid · roles · login · driverName · jobsToday · mốc hạn — rồi mở đúng màn vai trò.
 *  11. **Bộ kit giao diện** (`ui/kit`): mọi con số spec dashboard (`ui-dashboard-app-screens.md`
 *      §2–§6) khớp token, và ánh xạ trạng thái → giọng màu không lệch giữa hai bảng. Đây là
 *      "preview" của bộ kit: chạy được offline, in ra từng dòng [ĐẠT]/[HỎNG].
 *
 * Phần assert "thuần" của các khối 4/7/10 đã có bản song song trong `commonTest`
 * (`JobStateTest`, `LabApiParsingTest`, `RoleRoutingTest`, `CsKhoPayloadTest`, `AdminAccountsTest`)
 * — self-check giữ vai trò smoke + kiểm mã nguồn.
 * =====================================================================================
 */
fun runSelfCheck(): Boolean {
    var failures = 0

    fun report(name: String, ok: Boolean, detail: String) {
        if (!ok) failures++
        println((if (ok) "  [ĐẠT ] " else "  [HỎNG] ") + name.padEnd(40) + detail)
    }

    println("=== AppLogictics · tự kiểm tra design + duo UI + vai trò + an toàn CAPTURE ===")

    println("-- 1. Tương phản WCAG AA (>= 4.5:1) — CHỮ trên nền thật (ADR-009 §6) --")
    ContrastPairs.ALL.forEach { (name, fg, bg) ->
        val ratio = contrastRatio(fg, bg)
        report(name, ratio >= WcagAa.NORMAL_TEXT, "%.2f:1".format(ratio))
    }

    println("-- 1b. Đồ hoạ: WCAG AA cho thành phần đồ hoạ (>= 3:1) --")
    GraphicPairs.ALL.forEach { (name, fg, bg) ->
        val ratio = contrastRatio(fg, bg)
        report(name, ratio >= WcagAa.LARGE_TEXT, "%.2f:1".format(ratio))
    }

    println("-- 2. Ba ngưỡng duo UI (biên 1280 / 900 dp) --")
    listOf(
        Tokens.DEMO_WIDTH_MAX_DP to DuoArrangement.SplitWide, // 1600
        Tokens.BREAKPOINT_SPLIT_WIDE_DP to DuoArrangement.SplitWide, // 1280 = biên dưới wide
        Tokens.BREAKPOINT_SPLIT_WIDE_DP - 1 to DuoArrangement.SplitNarrow, // 1279
        Tokens.BREAKPOINT_SPLIT_NARROW_DP to DuoArrangement.SplitNarrow, // 900 = biên dưới narrow
        Tokens.BREAKPOINT_SPLIT_NARROW_DP - 1 to DuoArrangement.Overlay, // 899
        Tokens.DEMO_WIDTH_MIN_DP to DuoArrangement.Overlay, // 720
    ).forEach { (width, expected) ->
        val actual = DuoArrangement.fromWidth(width)
        report(
            "widthDp=$width",
            actual == expected,
            "kỳ vọng ${expected.name}, nhận ${actual.name}",
        )
    }

    println("-- 3. Typography tiếng Việt: lineHeight >= 1.4 x fontSize --")
    listOf(
        "h4" to AppTypography.h4,
        "h5" to AppTypography.h5,
        "h6" to AppTypography.h6,
        "subtitle1" to AppTypography.subtitle1,
        "subtitle2" to AppTypography.subtitle2,
        "body1" to AppTypography.body1,
        "body2" to AppTypography.body2,
        "button" to AppTypography.button,
        "caption" to AppTypography.caption,
    ).forEach { (name, style) ->
        val size = style.fontSize.value
        val line = style.lineHeight.value
        val ok = size.isFinite() && line.isFinite() && size > 0f && line / size >= MIN_LINE_HEIGHT_RATIO
        report(name, ok, "%.0fsp / %.0fsp = %.2fx".format(size, line, line / size))
    }

    checkJobStates { name, ok, detail -> report(name, ok, detail) }
    checkBrandPalette { name, ok, detail -> report(name, ok, detail) }
    checkLogoGeometry { name, ok, detail -> report(name, ok, detail) }
    checkDriverJobPayload { name, ok, detail -> report(name, ok, detail) }
    checkNoHardcodedEndpoint { name, ok, detail -> report(name, ok, detail) }
    checkCaptureSafety { name, ok, detail -> report(name, ok, detail) }
    checkRoleRoutingAndOpsPayloads { name, ok, detail -> report(name, ok, detail) }
    checkLabKit { name, ok, detail -> report(name, ok, detail) }

    println(
        if (failures == 0) {
            "=== KẾT QUẢ: ĐẠT — 0 lỗi ==="
        } else {
            "=== KẾT QUẢ: HỎNG — $failures mục không đạt (xem dòng [HỎNG]) ==="
        },
    )
    return failures == 0
}

/** Luật tiếng Việt: dấu mũ/móc bị cắt nếu lineHeight chật hơn 1.4 lần cỡ chữ. */
private const val MIN_LINE_HEIGHT_RATIO = 1.4f

/**
 * Khối 5 — BẢNG TOKEN (ADR-009 §6): mọi hex phải khớp TỪNG DÒNG của bảng đã chốt.
 *
 * Vì sao kiểm cả hex: palette được chép vào 2 nơi (bảng §6 trong ADR + `design/Tokens.kt`).
 * Nếu ai "chỉnh cho đẹp" một bên, app lệch tông so với web/ảnh tham chiếu mà mắt thường khó
 * thấy trên máy chiếu — nhưng self-check thấy ngay. Đây là assert cho palette chứ không phải
 * checkbox trang trí: đổi một hex trong `Brand` mà quên cập nhật là đỏ.
 *
 * `line` / `lineStrong` / `gridLine` là màu **có alpha** (`rgba(255,255,255,...)` của §6) nên
 * hex kỳ vọng giữ nguyên phần alpha (0x14 = 8%, 0x24 = 14%, 0x0F = 6%).
 *
 * Đọc ARGB bằng [argbOf] (`Color.toArgb()`) chứ không đọc `Color.value`: `value` là dạng đóng
 * gói 64-bit có cả id không gian màu — chi tiết nội bộ của Compose, không phải hợp đồng.
 */
private fun checkBrandPalette(report: (String, Boolean, String) -> Unit) {
    println("-- 5. Bảng token ADR-009 §6 khớp hex trong docs --")
    listOf(
        "nền --lab-bg #F3F2F8" to (argbOf(Brand.ground) to 0xFFF3F2F8UL),
        "bề mặt 1 --lab-s1 #FFFFFF" to (argbOf(Brand.surface) to 0xFFFFFFFFUL),
        "bề mặt 2 --lab-s2 #F6F6FB" to (argbOf(Brand.surface2) to 0xFFF6F6FBUL),
        "card --lab-card #FFFFFF" to (argbOf(Brand.card) to 0xFFFFFFFFUL),
        "card hover --lab-card-2 #F6FBE8" to (argbOf(Brand.cardHover) to 0xFFF6FBE8UL),
        "viền --lab-line 10% mực" to (argbOf(Brand.line) to 0x1A101020UL),
        "viền mạnh --lab-line-2 12% mực" to (argbOf(Brand.lineStrong) to 0x1F101020UL),
        "mực --lab-ink #0B0B12" to (argbOf(Brand.ink) to 0xFF0B0B12UL),
        "mực phụ --lab-ink-2 #6B6B78" to (argbOf(Brand.inkMuted) to 0xFF6B6B78UL),
        "mực mờ --lab-ink-3 #9A9AA6" to (argbOf(Brand.ink3) to 0xFF9A9AA6UL),
        "nhấn --lab-accent #C2EE2F" to (argbOf(Brand.forest) to 0xFFC2EE2FUL),
        "nhấn hover --lab-accent-2 #D4F84E" to (argbOf(Brand.forestHover) to 0xFFD4F84EUL),
        "mực trên nhấn --lab-on-accent #2B3300" to (argbOf(Brand.onAccent) to 0xFF2B3300UL),
        "ok --lab-ok #2F9E5F" to (argbOf(Brand.ok) to 0xFF2F9E5FUL),
        "cảnh báo --lab-warn #A06A00" to (argbOf(Brand.amber) to 0xFFA06A00UL),
        "nguy hiểm --lab-bad #B3261E" to (argbOf(Brand.danger) to 0xFFB3261EUL),
        "thông tin --lab-info #2A6EA8" to (argbOf(Brand.info) to 0xFF2A6EA8UL),
        "chart c2 --lab-c2 #12857A" to (argbOf(Brand.chart2) to 0xFF12857AUL),
        "chart c3 --lab-c3 #2A6EA8" to (argbOf(Brand.chart3) to 0xFF2A6EA8UL),
        "lưới --lab-grid 8% mực" to (argbOf(Brand.gridLine) to 0x14101020UL),
    ).forEach { (name, pair) ->
        val (actual, expected) = pair
        report(name, actual == expected, "0x" + actual.toString(16).uppercase().padStart(8, '0'))
    }

    // Bảng của KIT phải LẤY TỪ `Brand`, không chép hex: lệch một dòng là hai nơi vẽ hai màu
    // (đúng lỗi cũ "chip còn xanh rừng trong khi token đã lime").
    listOf(
        "kit.card = Brand.card" to (DarkLabColors.card to Brand.card),
        "kit.surface2 = Brand.surface2" to (DarkLabColors.surface2 to Brand.surface2),
        "kit.ink3 = Brand.ink3" to (DarkLabColors.ink3 to Brand.ink3),
        "kit.line = Brand.line" to (DarkLabColors.line to Brand.line),
        "kit.accent = Brand.forest" to (DarkLabColors.accent to Brand.forest),
        "kit.onAccent = Brand.onAccent" to (DarkLabColors.onAccent to Brand.onAccent),
        "kit.info = Brand.infoInk" to (DarkLabColors.info to Brand.infoInk),
        "kit.ok = Brand.okInk" to (DarkLabColors.ok to Brand.okInk),
        "kit.chart1 = Brand.chart1" to (DarkLabColors.chart1 to Brand.chart1),
        "kit.grid = Brand.gridLine" to (DarkLabColors.grid to Brand.gridLine),
        "material primary = lime" to (AppPalette.primary to Brand.forest),
    ).forEach { (name, pair) ->
        val (actual, expected) = pair
        report(name, actual == expected, if (actual == expected) "khớp" else "LỆCH hex")
    }

    // onPrimary KHÔNG còn trùng `ground` như bảng tối cũ (#080D17 ở cả hai). Bảng sáng dùng mực
    // olive #2B3300 trên nền lime — nên kiểm bằng ĐỘ SÁNG (phải là mực tối, không phải chữ trắng),
    // thay vì so hex với `ground`. Luật "chữ tối trên lime" vẫn được khoá bởi cặp tương phản
    // "chữ trên nút nhấn" ở khối 1.
    val lumOnPrimary = relativeLuminance(Brand.onAccent)
    report(
        "material onPrimary = mực tối (L <= 0.05)",
        lumOnPrimary <= 0.05,
        "L=%.3f (trắng = 1.000)".format(lumOnPrimary),
    )

    // Cỡ chữ hero cũng phải theo luật tiếng Việt (lineHeight >= 1.4 × fontSize).
    listOf(
        "hero title" to (Tokens.heroTitleSize.value to Tokens.heroTitleLine.value),
        "hero lede" to (Tokens.heroLedeSize.value to Tokens.heroLedeLine.value),
        "hero title (điện thoại)" to (
            Tokens.heroTitleSizeCompact.value to Tokens.heroTitleLineCompact.value
            ),
    ).forEach { (name, pair) ->
        val (size, line) = pair
        report(name, size > 0f && line / size >= MIN_LINE_HEIGHT_RATIO, "%.0fsp / %.0fsp = %.2fx".format(size, line, line / size))
    }
}

/**
 * Khối 6 — HÌNH HỌC LOGO: logo vẽ bằng Canvas nên không có "file đúng" để đối chiếu.
 * Kiểm 3 bất biến hình học của `assets/brand/logo-mark.svg`:
 *   (a) đỉnh và đáy khối đối xứng qua tâm viewBox → logo không bị lệch lên/xuống;
 *   (b) hai vai trái/phải đối xứng qua trục giữa → khối isometric cân;
 *   (c) mọi toạ độ nằm trong viewBox → không bị cắt khi vẽ ở 30dp.
 */
private fun checkLogoGeometry(report: (String, Boolean, String) -> Unit) {
    println("-- 6. Hình học logo (viewBox ${LOGO_VIEWBOX.toInt()}) --")

    report(
        "đỉnh + đáy cân trong khung",
        LOGO_APEX_Y + LOGO_BOTTOM_Y == LOGO_VIEWBOX,
        "%.0f + %.0f = %.0f".format(LOGO_APEX_Y, LOGO_BOTTOM_Y, LOGO_APEX_Y + LOGO_BOTTOM_Y),
    )
    report(
        "hai vai đối xứng",
        LOGO_LEFT_X + LOGO_RIGHT_X == LOGO_VIEWBOX && LOGO_APEX_X == LOGO_VIEWBOX / 2f,
        "trái %.0f · phải %.0f · trục giữa %.0f".format(LOGO_LEFT_X, LOGO_RIGHT_X, LOGO_APEX_X),
    )
    report(
        "vai nằm dưới đỉnh, trên đáy",
        LOGO_SHOULDER_Y > LOGO_APEX_Y && LOGO_SHOULDER_Y < LOGO_BOTTOM_Y,
        "đỉnh %.0f < vai %.0f < đáy %.0f".format(LOGO_APEX_Y, LOGO_SHOULDER_Y, LOGO_BOTTOM_Y),
    )
    report(
        "mọi toạ độ nằm trong viewBox",
        listOf(LOGO_APEX_X, LOGO_APEX_Y, LOGO_LEFT_X, LOGO_RIGHT_X, LOGO_SHOULDER_Y, LOGO_BOTTOM_Y)
            .all { it in 0f..LOGO_VIEWBOX },
        "không có điểm nào bị cắt",
    )
}

/**
 * Khối 4 — ánh xạ trạng thái `lab.yeu.cau.lay_hang` (7 mã của model BE-01, đối chiếu 2026-10-28).
 *
 * Vì sao phải kiểm: đây là chỗ app dễ lệch Odoo nhất (mã state đổi mà app vẫn build xanh).
 * Kiểm 3 thứ: (a) đủ 7 mã đều có nhãn riêng (không hiện mã thô), (b) giọng màu đúng nghĩa
 * nghiệp vụ, (c) state lạ không làm app vỡ — trả nguyên chuỗi.
 */
private fun checkJobStates(report: (String, Boolean, String) -> Unit) {
    println("-- 4. Trạng thái lab.yeu.cau.lay_hang (7 mã model BE-01) --")

    val expectedTones = mapOf(
        JobState.CONFIRMED to Tone.Ok,
        JobState.FAILED to Tone.Bad,
        JobState.CANCELLED to Tone.Warn,
        JobState.NEW to Tone.Info,
        JobState.ASSIGNED to Tone.Info,
        JobState.EN_ROUTE to Tone.Info,
        JobState.PICKED_UP to Tone.Info,
    )

    report(
        "đủ 7 mã state",
        JobState.ALL.size == 7 && JobState.ALL.toSet() == expectedTones.keys,
        JobState.ALL.joinToString(" "),
    )

    JobState.ALL.forEach { state ->
        val label = stateLabelOf(state)
        val tone = stateToneOf(state)
        val hasLabel = label.isNotBlank() && label != state
        report(
            "state=$state",
            hasLabel && tone == expectedTones[state],
            "\"$label\" · giọng ${tone.name}",
        )
    }

    // Fallback: BE thêm state mới → app cũ vẫn chạy, hiện nguyên mã (không ném exception).
    val unknown = "state_moi_cua_be"
    report(
        "state lạ (fallback)",
        stateLabelOf(unknown) == unknown && stateToneOf(unknown) == Tone.Info,
        "\"${stateLabelOf(unknown)}\" · giọng ${stateToneOf(unknown).name}",
    )
}

/**
 * =====================================================================================
 * Khối 7 — WAVE B: PARSE PAYLOAD THẬT của `GET /lab/api/v1/driver/jobs`.
 *
 * Payload dưới đây CHÉP từ lần gọi thật (2026-10-28, Odoo 17, DB `odoo17_lab`) — giữ nguyên
 * cả 3 "cái bẫy" đã gặp, vì đây là 3 chỗ dễ vỡ nhất khi nối API:
 *   1. `windowFrom`/`windowTo` là **chuỗi `HH:mm`** ("01:30"), job không hẹn giờ trả **`"-"`**;
 *   2. `expectedPackages` **VẮNG** ở job (OQ-22) ⇒ model phải `Int?`, UI phải "Chưa rõ số kiện";
 *   3. `contactName`/`note`/`address` trả **chuỗi rỗng** (controller dùng `or ""`), không phải null.
 *
 * Kiểm thêm: `stateLabel` của SERVER được ưu tiên; khi server không trả thì fallback về bảng
 * nhãn cứng của app (để app cũ vẫn hiện tiếng Việt khi BE thêm state mới).
 * =====================================================================================
 */
private const val REAL_JOBS_PAYLOAD: String = """
{"ok":true,"data":[
{"id":241,"ref":"LH/26/0005","shop":"Sen Đá Shop","address":"45 Nguyễn Trãi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh","contactName":"Chị Hạnh","contactPhone":"0903 221 447","windowFrom":"01:30","windowTo":"03:00","state":"assigned","stateLabel":"Đã gán tài xế","note":"Gọi trước khi tới 10 phút."},
{"id":242,"ref":"LH/26/0006","shop":"Shop Hoa Sài Gòn","address":"12 Lý Tự Trọng, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh","contactName":"","contactPhone":"","windowFrom":"-","windowTo":"-","state":"en_route","stateLabel":"Đang đến lấy","note":""}
]}
"""

/** Payload tối giản: server KHÔNG trả `stateLabel` ⇒ app phải tự dịch mã state. */
private const val REAL_JOBS_PAYLOAD_KHONG_NHAN: String =
    """{"ok":true,"data":[{"id":7,"ref":"LH/26/0007","shop":"Shop Mới","state":"picked_up"}]}"""

private fun checkDriverJobPayload(report: (String, Boolean, String) -> Unit) {
    println("-- 7. Parse payload THẬT của GET /lab/api/v1/driver/jobs (Wave B) --")

    val jobs = try {
        parseDriverJobsPayload(REAL_JOBS_PAYLOAD)
    } catch (e: Throwable) {
        report("parse payload", false, "ném lỗi: ${e.message}")
        return
    }
    report("parse được 2 việc", jobs.size == 2, "nhận ${jobs.size} việc")
    if (jobs.size != 2) return

    val coGio = jobs[0]
    val chuaHen = jobs[1]

    report(
        "windowFrom là chuỗi HH:mm",
        coGio.windowFrom == "01:30",
        "\"${coGio.windowFrom}\" (không parse Datetime — OQ-23)",
    )
    report(
        "windowLabel ghép khung giờ",
        coGio.windowLabel().contains("01:30") && coGio.windowLabel().contains("03:00"),
        "\"${coGio.windowLabel()}\"",
    )
    report(
        "window \"-\" ⇒ \"Chưa hẹn giờ\"",
        chuaHen.windowLabel() == "Chưa hẹn giờ",
        "\"${chuaHen.windowLabel()}\"",
    )
    report(
        "expectedPackages VẮNG ⇒ null (không nổ)",
        coGio.expectedPackages == null && coGio.packagesLabel() == "Chưa rõ số kiện",
        "expectedPackages=${coGio.expectedPackages} · \"${coGio.packagesLabel()}\"",
    )
    report(
        "stateLabel của server được ưu tiên",
        chuaHen.statusLabel() == "Đang đến lấy",
        "\"${chuaHen.statusLabel()}\"",
    )
    report(
        "chuỗi rỗng ⇒ null (không hiện khoảng trắng)",
        chuaHen.contactName == null && chuaHen.note == null,
        "contactName=${chuaHen.contactName} · note=${chuaHen.note}",
    )

    val khongNhan = try {
        parseDriverJobsPayload(REAL_JOBS_PAYLOAD_KHONG_NHAN).firstOrNull()
    } catch (e: Throwable) {
        null
    }
    report(
        "thiếu stateLabel ⇒ fallback stateLabelOf",
        khongNhan?.statusLabel() == "Đã lấy được hàng",
        "\"${khongNhan?.statusLabel()}\"",
    )
}

/**
 * =====================================================================================
 * Khối 8 — KHÔNG HARDCODE URL/TOKEN.
 *
 * Luật cứng (agents/fe/AGENT.md §7 "Không hardcode URL/DB/token"): địa chỉ máy chủ chỉ được
 * là **default trong Settings** (`AppSettings.DEFAULT_BASE_URL`) để người dùng nhìn thấy và
 * sửa được; token/mật khẩu thì **không bao giờ** có literal trong mã.
 *
 * Cách kiểm: grep chính thư mục `src/` (bỏ `commonTest` — fixture payload được phép chứa
 * token mẫu). Chạy được cả trong CI vì là đọc tệp, không cần mạng.
 * =====================================================================================
 */
private fun checkNoHardcodedEndpoint(report: (String, Boolean, String) -> Unit) {
    println("-- 8. Không hardcode URL/token trong mã nguồn (grep src/) --")

    val root = listOf(File("src"), File("composeApp/src"), File("apps/kmp/composeApp/src"))
        .firstOrNull { it.isDirectory }
    if (root == null) {
        report("thấy thư mục nguồn", true, "(bỏ qua: không thấy src/ — chạy selfCheck từ apps/kmp)")
        return
    }

    val sources = root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .filterNot { it.path.replace('\\', '/').contains("commonTest") }
        .toList()
    if (sources.isEmpty()) {
        report("đọc được mã nguồn", false, "không thấy file .kt nào dưới ${root.path}")
        return
    }

    // Chỉ kiểm CODE, không kiểm comment: KDoc được phép nhắc địa chỉ lab để giải thích
    // (vd "device gọi http://…"), cái bị cấm là **literal trong code** (giá trị chạy thật).
    val code = sources.associateWith { file -> file.codeLines() }

    // Lấy ĐÚNG giá trị default từ Settings (không viết lại literal ở đây — nếu viết, chính
    // file này sẽ bị chính nó bắt lỗi).
    val diaChiMacDinh = vn.lab.logistics.core.settings.AppSettings.DEFAULT_BASE_URL
    val coDiaChi = code.filterValues { lines -> lines.any { it.contains(diaChiMacDinh) } }.keys
    report(
        "địa chỉ lab chỉ trong AppSettings",
        coDiaChi.size == 1 && coDiaChi.first().name == "AppSettings.kt",
        if (coDiaChi.isEmpty()) {
            "không tệp nào chứa — mất default của Settings?"
        } else {
            coDiaChi.joinToString(", ") { it.name }
        },
    )

    val coBiMat = code.filterValues { lines -> lines.any { SECRET_LITERAL.containsMatchIn(it) } }.keys
    report(
        "không có token/mật khẩu hardcode",
        coBiMat.isEmpty(),
        if (coBiMat.isEmpty()) "0 tệp vi phạm / ${sources.size} tệp" else coBiMat.joinToString(", ") { it.name },
    )
}

/** Các dòng CODE của một file .kt (bỏ dòng comment `//`, `/*`, `*`, `*/`). */
private fun File.codeLines(): List<String> = readLines().filterNot { line ->
    val trimmed = line.trimStart()
    trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")
}

/** `token = "…"` / `password = "…"` / `apiKey = "…"` với literal dài ≥ 16 ký tự. */
private val SECRET_LITERAL = Regex("(?i)(token|password|apikey)\\s*=\\s*\".{16,}")

// =====================================================================================
// Khối 9 — BẤT BIẾN AN TOÀN CAPTURE (C1–C6)
//
// Vì sao có khối này: màn CAPTURE gửi bằng chứng pháp lý lên Odoo. Bốn bất biến phải đúng
// MỌI LÚC, không chỉ khi có người review:
//   (a) KHÔNG gửi khi thiếu ảnh / chữ ký / số kiện / kg — kiểm bằng ĐÚNG hàm mà nút Gửi dùng
//       (`CaptureValidation.blockers`) chứ không phải bản sao dễ lệch;
//   (b) ảnh nén ra ≤ 5 MB và VẪN là JPEG thật (magic bytes) — nén một ảnh lớn bằng ImageIO
//       ngay tại đây, không "tin" vào code đọc bằng mắt;
//   (c) không hardcode token/mật khẩu trong mã nguồn (grep `src/`, bỏ `commonTest`);
//   (d) `clientUuid` là UUID v4 hợp lệ VÀ giữ nguyên khi bấm "Thử lại" (idempotency — đọc
//       `driver.py::capture`, gửi lại cùng uuid thì server không tạo bộ ảnh thứ hai).
// =====================================================================================
private fun checkCaptureSafety(report: (String, Boolean, String) -> Unit) {
    println("-- 9. Bất biến an toàn CAPTURE (C1–C6) --")

    // (a) Chặn gửi khi thiếu bằng chứng — 4 lý do ứng với 4 trường bắt buộc.
    val trongRong = CaptureDraft()
    val chanRong = CaptureValidation.blockers(trongRong)
    report(
        "(a) rỗng ⇒ chặn gửi",
        chanRong.size >= 4 && !CaptureValidation.canSubmit(trongRong),
        "${chanRong.size} lý do · \"${chanRong.firstOrNull()}\"",
    )

    val du = CaptureDraft(
        hasHangHoa = true,
        hasKienHang = true,
        hasSignature = true,
        packagesText = "4",
        weightText = "12,5",
    )
    report(
        "(a) đủ bằng chứng ⇒ cho gửi",
        CaptureValidation.blockers(du).isEmpty(),
        "0 lý do · kiện=${CaptureValidation.packages(du.packagesText)} " +
            "kg=${CaptureValidation.weightKg(du.weightText)}",
    )
    listOf(
        "thiếu ảnh Hàng hoá" to du.copy(hasHangHoa = false),
        "thiếu ảnh Kiện hàng" to du.copy(hasKienHang = false),
        "chưa ký biên nhận" to du.copy(hasSignature = false),
        "số kiện = 0" to du.copy(packagesText = "0"),
        "khối lượng = 0" to du.copy(weightText = "0"),
    ).forEach { (ten, draft) ->
        report("(a) $ten ⇒ chặn", !CaptureValidation.canSubmit(draft), "không cho gửi")
    }

    // (b) Nén ảnh THẬT: ảnh nhiễu lớn (nén kém nhất) → JPEG ≤ 5 MB, magic bytes còn đúng.
    val anhGoc = taoAnhNhieuJpeg(rong = 2600, cao = 1800)
    val anhNen = try {
        compressJpeg(anhGoc)
    } catch (e: Throwable) {
        report("(b) nén ảnh", false, "ném lỗi: ${e.message}")
        null
    }
    if (anhNen != null) {
        report(
            "(b) ảnh nén ≤ 5 MB",
            anhNen.size <= MAX_IMAGE_BYTES,
            "gốc ${anhGoc.size / 1024} KB → nén ${anhNen.size / 1024} KB",
        )
        report(
            "(b) nén thật sự giảm dung lượng",
            anhNen.size < anhGoc.size,
            "${anhGoc.size / 1024} KB → ${anhNen.size / 1024} KB",
        )
        report(
            "(b) vẫn là JPEG (magic bytes)",
            sniffImageKind(anhNen) == ImageKind.Jpeg,
            sniffImageKind(anhNen)?.mime ?: "không nhận ra",
        )
        report("(b) isSupportedImage nhận ảnh nén", isSupportedImage(anhNen), "giải mã được")
    }
    report(
        "(b) isSupportedImage từ chối dữ liệu rác",
        !isSupportedImage(ByteArray(64) { 7 }),
        "64 byte rác ⇒ false",
    )

    // (c) Không hardcode token — cùng luật với khối 8, nói riêng cho đường capture.
    val files = sourceFiles()
    if (files.isEmpty()) {
        report("(c) đọc được mã nguồn", true, "(bỏ qua: không thấy src/ — chạy selfCheck từ apps/kmp)")
    } else {
        val viPham = files.filter { file ->
            file.codeLines().any { line -> SECRET_LITERAL.containsMatchIn(line) }
        }
        report(
            "(c) không hardcode token/mật khẩu",
            viPham.isEmpty(),
            if (viPham.isEmpty()) "0 tệp vi phạm / ${files.size} tệp" else viPham.joinToString { it.name },
        )
    }

    // (d) clientUuid: UUID v4 hợp lệ + bất biến retry (C5).
    val uuid = newClientUuid()
    report("(d) clientUuid là UUID v4 hợp lệ", isValidClientUuid(uuid), uuid)

    val phien = openCaptureSession(current = null, jobId = 241) { uuid }
    val thuLai = openCaptureSession(current = phien, jobId = 241) {
        "00000000-0000-4000-8000-000000000000" // nếu hàm sinh uuid bị gọi ⇒ uuid SAI
    }
    report(
        "(d) \"Thử lại\" GIỮ NGUYÊN clientUuid",
        thuLai.clientUuid == uuid,
        thuLai.clientUuid,
    )

    val phienMoi = openCaptureSession(current = phien, jobId = 242) {
        "11111111-1111-4111-8111-111111111111"
    }
    report(
        "(d) capture mới ⇒ clientUuid mới",
        phienMoi.clientUuid != uuid,
        phienMoi.clientUuid,
    )
}

/** Danh sách file `.kt` của mã sản phẩm (bỏ `commonTest`) — dùng cho các khối grep. */
private fun sourceFiles(): List<File> {
    val root = listOf(File("src"), File("composeApp/src"), File("apps/kmp/composeApp/src"))
        .firstOrNull { it.isDirectory } ?: return emptyList()
    return root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .filterNot { it.path.replace('\\', '/').contains("commonTest") }
        .toList()
}

// =====================================================================================
// Khối 10 — CHỌN MÀN THEO VAI TRÒ + PARSE PAYLOAD CS/KHO (2 màn mới)
//
// Vì sao phải có khối này: quy tắc "ưu tiên driver → cs → kho" là thứ dễ lệch nhất khi BE đổi
// cách trả `roles` — sai quy tắc thì tài xế mở app ra thấy màn CS và không làm được việc. Kiểm
// bằng ĐÚNG hàm app dùng (`Workspace.fromRoles`), không phải bản sao.
//
// Payload dưới đây chép theo HỢP ĐỒNG đã chốt với BE-01, giữ cả 3 "cái bẫy" đã biết từ
// `driver/jobs`: khung giờ có thể là `"-"`, `expectedPackages` có thể VẮNG, chuỗi có thể rỗng.
// =====================================================================================
private const val REAL_CS_JOBS_PAYLOAD: String = """
{"ok":true,"data":{"items":[
{"id":501,"ref":"LH/26/0101","shop":"Sen Đá Shop","address":"45 Nguyễn Trãi, Phường Bến Thành, Quận 1","contactPhone":"0903 221 447","windowFrom":"08:30","windowTo":"10:00","state":"new","stateLabel":"Mới","expectedPackages":4},
{"id":502,"ref":"LH/26/0102","shop":"Shop Hoa Sài Gòn","address":"12 Lý Tự Trọng, Quận 1","contactPhone":"","windowFrom":"-","windowTo":"-","state":"assigned","stateLabel":"Đã gán tài xế","driverName":"Trần Văn Hùng"}
],"drivers":[{"id":12,"name":"Trần Văn Hùng","login":"taixe01"},{"id":13,"name":"Nguyễn Thị Mai"}]}}
"""

private const val REAL_KHO_CHO_NHAN_PAYLOAD: String = """
{"ok":true,"data":{"items":[
{"id":601,"ref":"LH/26/0201","shop":"Sen Đá Shop","packages":4,"weightKg":12.5,"driverName":"Trần Văn Hùng"},
{"id":602,"ref":"LH/26/0202","shop":"Shop Hoa Sài Gòn"}
]}}
"""

/**
 * `GET /lab/api/v1/admin/accounts` — chép theo hợp đồng với BE-01, GIỮ đúng 2 cái bẫy:
 * dòng thiếu `name` (phải rơi về `login`) và dòng thiếu `roles` (phải thành "chưa có vai trò").
 * Dòng thiếu `login` cũng có mặt để chứng minh app BỎ nó (không chuyển được nếu thiếu login).
 */
private const val REAL_ADMIN_ACCOUNTS_PAYLOAD: String = """
{"ok":true,"data":{"items":[
{"login":"taixe01","name":"Trần Văn Hùng (tài xế)","roles":["driver"]},
{"login":"nhomtruong01","name":"Phạm Minh Nhóm (trưởng nhóm tài xế)","roles":["driver_leader"]},
{"login":"cs01"},
{"name":"Dòng hỏng: thiếu login","roles":["cs"]}
]}}
"""

/**
 * `POST /lab/api/v1/admin/switch` — ĐÚNG shape login: KHÔNG có `driver_name`, CÓ `login` và
 * `canSwitch` (phiên do admin chuyển vẫn được chuyển vai trò tiếp).
 */
private const val REAL_SWITCH_PAYLOAD: String = """
{"ok":true,"data":{"token":"tok-doi-phien","tokenType":"LabToken","expiresAt":"2026-10-29 09:00:00",
 "uid":12,"name":"Trần Văn Hùng (tài xế)","login":"taixe01","roles":["driver"],"jobsToday":3,
 "canSwitch":true}}
"""

private fun checkRoleRoutingAndOpsPayloads(report: (String, Boolean, String) -> Unit) {
    println("-- 10. Chọn màn theo vai trò (admin → driver → driver_leader → kho) + CS trên web --")

    val kyVong = listOf(
        listOf("driver") to Workspace.Driver,
        listOf("driver", "cs") to Workspace.Driver,
        // VAI TRÒ MỚI của BE-01: trưởng nhóm tài xế (người phân công) có màn riêng trên app.
        listOf("driver_leader") to Workspace.DriverLeader,
        // CHỈ QUẢN TRỊ VIÊN: công cụ chuyển tài khoản để test nhanh 8 vai trò.
        listOf("admin") to Workspace.Admin,
        listOf("admin", "driver") to Workspace.Admin,
        // D1 (2026-11-02): CS KHÔNG còn màn app ⇒ `cs` rơi vào màn "làm việc trên web";
        // `cs` + `kho` thì Kho thắng (kho vẫn là việc tay chân trên app).
        listOf("cs") to Workspace.Unsupported,
        listOf("kho", "cs") to Workspace.Kho,
        listOf("kho") to Workspace.Kho,
        // SPEC MỚI (dashboard Sếp): manager/sep có màn Dashboard trong app.
        listOf("manager", "sep") to Workspace.Dashboard,
        listOf("sep") to Workspace.Dashboard,
        listOf("cs", "manager") to Workspace.Dashboard,   // cs không còn màn app => rơi xuống dashboard
        listOf("ketoan") to Workspace.Unsupported,
        emptyList<String>() to Workspace.Unsupported,
    )
    kyVong.forEach { (roles, expected) ->
        val actual = Workspace.fromRoles(roles)
        report(
            "roles=" + (if (roles.isEmpty()) "(rỗng)" else roles.joinToString("+")),
            actual == expected,
            "kỳ vọng ${expected.name}, nhận ${actual.name}",
        )
    }

    // ---- THỨ TỰ ƯU TIÊN có vai trò MỚI `driver_leader` (trưởng nhóm tài xế) ----
    // Quy tắc: driver → driver_leader → cs → kho → dashboard. Kiểm CẢ HAI chiều của danh sách
    // vì thứ tự server trả KHÔNG được quyết định màn hình; app tự áp thứ tự nghiệp vụ của mình.
    listOf<Triple<List<String>, Workspace, String>>(
        Triple(listOf("driver_leader", "cs"), Workspace.DriverLeader, "trưởng nhóm trước cs"),
        Triple(listOf("cs", "driver_leader"), Workspace.DriverLeader, "server trả cs trước"),
        Triple(listOf("kho", "driver_leader"), Workspace.DriverLeader, "trưởng nhóm trước kho"),
        Triple(listOf("driver", "driver_leader"), Workspace.Driver, "tài xế trước trưởng nhóm"),
        Triple(
            listOf("driver_leader", "manager", "sep"),
            Workspace.DriverLeader,
            "trưởng nhóm trước dashboard",
        ),
        // ADMIN THẮNG MỌI VAI TRÒ — kể cả khi server trả admin ở cuối danh sách.
        Triple(listOf("driver", "admin"), Workspace.Admin, "admin đứng sau driver"),
        Triple(listOf("cs", "kho", "admin"), Workspace.Admin, "admin đứng sau cs/kho"),
        Triple(listOf("admin", "manager"), Workspace.Admin, "admin trước dashboard"),
    ).forEach { (roles, expected, ghiChu) ->
        val actual = Workspace.fromRoles(roles)
        report(
            "ưu tiên: " + roles.joinToString("+"),
            actual == expected,
            "$ghiChu ⇒ kỳ vọng ${expected.name}, nhận ${actual.name}",
        )
    }

    // ---- CS: GET /lab/api/v1/cs/jobs ----
    val cs = try {
        parseCsJobsPayload(REAL_CS_JOBS_PAYLOAD)
    } catch (e: Throwable) {
        report("parse cs/jobs", false, "ném lỗi: ${e.message}")
        return
    }
    report(
        "cs/jobs: 2 việc + 2 tài xế",
        cs.items.size == 2 && cs.drivers.size == 2,
        "items=${cs.items.size} · drivers=${cs.drivers.size}",
    )
    // Chặn truy cập chỉ số trước khi dùng: payload hỏng mà văng lỗi ở đây sẽ làm app chết NGAY
    // khi khởi động (self-check chạy trước khi mở cửa sổ) — hỏng nặng hơn một dòng [HỎNG].
    if (cs.items.size < 2 || cs.drivers.size < 2) return
    report(
        "cs/jobs: số kiện dự kiến",
        cs.items[0].expectedPackages == 4 &&
            formatPackages(cs.items[0].expectedPackages) == "4 kiện" &&
            formatPackages(cs.items[1].expectedPackages) == "Chưa rõ số kiện",
        "việc 1 = ${formatPackages(cs.items[0].expectedPackages)} · " +
            "việc 2 = ${formatPackages(cs.items[1].expectedPackages)}",
    )
    report(
        "cs/jobs: khung giờ \"-\" ⇒ Chưa hẹn giờ",
        formatWindow(cs.items[1].windowFrom, cs.items[1].windowTo) == "Chưa hẹn giờ",
        "\"${formatWindow(cs.items[1].windowFrom, cs.items[1].windowTo)}\"",
    )
    report(
        "cs/jobs: chuỗi rỗng ⇒ null",
        cs.items[1].contactPhone == null && cs.items[1].driverName == "Trần Văn Hùng",
        "contactPhone=${cs.items[1].contactPhone} · driverName=${cs.items[1].driverName}",
    )
    // ---- TÀI KHOẢN của tài xế trong ô chọn (hợp đồng MỚI: `drivers[].login`) ----
    report(
        "cs/jobs: nhãn tài xế có TÀI KHOẢN",
        cs.drivers[0].login == "taixe01" &&
            cs.drivers[0].displayLabel() == "Trần Văn Hùng · taixe01" &&
            cs.drivers[0].accountLine() == "Tài khoản: taixe01",
        "\"${cs.drivers[0].displayLabel()}\" · \"${cs.drivers[0].accountLine()}\"",
    )
    report(
        "cs/jobs: thiếu login ⇒ chỉ hiện tên",
        cs.drivers[1].login == null &&
            cs.drivers[1].displayLabel() == "Nguyễn Thị Mai" &&
            cs.drivers[1].accountLine() == null,
        "\"${cs.drivers[1].displayLabel()}\" (không có dấu · treo)",
    )

    // ---- CS: POST …/assign ----
    val body = assignRequestBody(driverId = 12)
    report("assign: body có driverId", body.contains("\"driverId\"") && body.contains("12"), body)
    val gan = try {
        parseAssignResult(
            """{"ok":true,"data":{"state":"assigned","stateLabel":"Đã gán tài xế","driverName":"Trần Văn Hùng"}}""",
        )
    } catch (e: Throwable) {
        report("parse assign", false, "ném lỗi: ${e.message}")
        return
    }
    report(
        "assign: state + nhãn do server trả",
        gan.state == "assigned" && gan.stateLabel == "Đã gán tài xế" && gan.driverName == "Trần Văn Hùng",
        "${gan.state} · \"${gan.stateLabel}\" · ${gan.driverName}",
    )

    // ---- Kho: GET /lab/api/v1/kho/cho-nhan ----
    val kho = try {
        parseKhoChoNhanPayload(REAL_KHO_CHO_NHAN_PAYLOAD)
    } catch (e: Throwable) {
        report("parse kho/cho-nhan", false, "ném lỗi: ${e.message}")
        return
    }
    report("kho: 2 việc chờ nhận", kho.size == 2, "nhận ${kho.size} việc")
    if (kho.size < 2) return
    report(
        "kho: kg đọc theo kiểu Việt Nam",
        formatWeightKg(kho[0].weightKg) == "12,5 kg",
        "\"${formatWeightKg(kho[0].weightKg)}\"",
    )
    report(
        "kho: thiếu packages/kg ⇒ không bịa 0",
        kho[1].packages == null && kho[1].weightKg == null &&
            formatPackages(kho[1].packages) == "Chưa rõ số kiện" &&
            formatWeightKg(kho[1].weightKg) == "Chưa rõ khối lượng",
        "\"${formatPackages(kho[1].packages)}\" · \"${formatWeightKg(kho[1].weightKg)}\"",
    )

    // ---- Kho: POST …/nhan ----
    val nhan = try {
        parseKhoReceiveResult(
            """{"ok":true,"data":{"state":"confirmed","stateLabel":"Đã về kho","pickingName":"WH/IN/00042"}}""",
        )
    } catch (e: Throwable) {
        report("parse nhan", false, "ném lỗi: ${e.message}")
        return
    }
    report(
        "nhan: có số phiếu nhập kho",
        nhan.state == "confirmed" && nhan.pickingName == "WH/IN/00042",
        "${nhan.state} · ${nhan.pickingName}",
    )

    checkAdminSwitch(report)
}

/**
 * =====================================================================================
 * Khối 10b — ADMIN: CHUYỂN TÀI KHOẢN (công cụ test nhanh 8 vai trò).
 *
 * Kiểm đúng ba thứ dễ sai nhất và đều KHÔNG nhìn thấy được bằng mắt trên màn hình:
 *   (a) parse `admin/accounts` — dòng thiếu `name`/`roles` phải thành câu đọc được / danh sách
 *       rỗng chứ không làm nổ cả màn (payload thật của BE sẽ còn thiếu trường khi seed dở);
 *   (b) parse `admin/switch` — phản hồi đúng shape login, và `login` phải được đọc ra (nếu không,
 *       sau khi chuyển, dòng "Tài khoản: …" ở màn tài xế vẫn ghi tài khoản CŨ);
 *   (c) **ghi phiên mới vào `AppSettings`** — token · uid · roles · login · driverName ·
 *       jobsToday · mốc hạn, VÀ phiên mới phải sống qua lần đọc lại từ nơi lưu bền (đây là thứ
 *       quyết định "mở app lần sau vào đúng màn của tài khoản vừa chuyển");
 *   (d) **`canSwitch`** — quyền chuyển vai trò: phiên sau switch phải `true` (nút dropdown còn
 *       hiện khi đang là `taixe01`), payload không có khoá phải `false`, đăng xuất phải xoá cờ.
 *
 * Dùng `InMemorySettingsStorage` (không đụng tệp/SharedPreferences của người dùng) nhưng đi qua
 * ĐÚNG hàm mà màn hình dùng ([vn.lab.logistics.core.settings.AppSettings.saveSession]).
 * =====================================================================================
 */
private fun checkAdminSwitch(report: (String, Boolean, String) -> Unit) {
    println("-- 10b. Admin — chuyển tài khoản: parse accounts/switch + ghi phiên mới --")

    // (a) GET /lab/api/v1/admin/accounts — có dòng đủ trường và dòng thiếu name/roles.
    val accounts = try {
        parseAdminAccountsPayload(REAL_ADMIN_ACCOUNTS_PAYLOAD)
    } catch (e: Throwable) {
        report("parse admin/accounts", false, "ném lỗi: ${e.message}")
        return
    }
    report(
        "accounts: giữ 3 dòng có login",
        accounts.size == 3,
        "nhận ${accounts.size} dòng (dòng thiếu login bị bỏ)",
    )
    if (accounts.size < 3) return
    report(
        "accounts: nhãn 'tên · tài khoản · vai trò'",
        accounts[0].accountLabel() == "Trần Văn Hùng (tài xế) · taixe01 · driver",
        "\"${accounts[0].accountLabel()}\"",
    )
    report(
        "accounts: thiếu name/roles ⇒ không vỡ",
        accounts[2].name == "cs01" && accounts[2].roles.isEmpty() &&
            accounts[2].accountLabel() == "cs01 · cs01 · chưa có vai trò",
        "\"${accounts[2].accountLabel()}\"",
    )
    report(
        "accounts: đích đến đúng quy tắc vai trò",
        accounts[0].targetWorkspace == Workspace.Driver &&
            accounts[1].targetWorkspace == Workspace.DriverLeader,
        "${accounts[0].targetWorkspace.name} · ${accounts[1].targetWorkspace.name}",
    )

    // (b) POST /lab/api/v1/admin/switch — đúng shape login, có `login`.
    val session = try {
        parseSwitchSessionPayload(REAL_SWITCH_PAYLOAD)
    } catch (e: Throwable) {
        report("parse admin/switch", false, "ném lỗi: ${e.message}")
        return
    }
    report(
        "switch: token + vai trò + login của tài khoản đích",
        session.token.isNotBlank() && session.roles == listOf("driver") &&
            session.login == "taixe01" && session.uid == 12 && session.jobsToday == 3,
        "login=${session.login} · roles=${session.roles} · uid=${session.uid}",
    )
    report(
        "switch: thiếu driver_name ⇒ lấy name",
        session.driverName == "Trần Văn Hùng (tài xế)",
        "\"${session.driverName}\"",
    )
    report(
        "switch: mở đúng màn theo vai trò",
        Workspace.fromRoles(session.roles) == Workspace.Driver,
        "roles=${session.roles} ⇒ ${Workspace.fromRoles(session.roles).name}",
    )

    // (c) Ghi phiên mới — KHÔNG đụng tệp của người dùng (bản chỉ trong RAM).
    val storage = InMemorySettingsStorage()
    val settings = AppSettings(storage)
    val truocKhiChuyen = settings.hasValidSession
    settings.saveSession(session)
    report(
        "(c) ghi đủ khoá phiên: token/uid/roles/login/tên/jobs",
        settings.token == session.token &&
            settings.uid == 12 &&
            settings.roles == listOf("driver") &&
            settings.login == "taixe01" &&
            settings.driverName == "Trần Văn Hùng (tài xế)" &&
            settings.jobsToday == 3,
        "login=${settings.login} · roles=${settings.roles} · tên=${settings.driverName}",
    )
    report(
        "(c) có mốc hạn ⇒ phiên dùng được ngay",
        !truocKhiChuyen && settings.hasValidSession,
        "trước: $truocKhiChuyen · sau: ${settings.hasValidSession}",
    )
    // Đọc LẠI từ nơi lưu bền: mở app lần sau phải vào đúng màn của tài khoản vừa chuyển.
    val docLai = AppSettings(storage)
    report(
        "(c) phiên sống qua lần đọc lại (mở app lần sau)",
        docLai.login == "taixe01" && docLai.workspace == Workspace.Driver &&
            docLai.hasValidSession,
        "login=${docLai.login} · màn=${docLai.workspace.name}",
    )

    // (d) CAN SWITCH — nút chuyển vai trò ở thanh trên cùng.
    // Vì sao phải kiểm: sếp yêu cầu nút LUÔN hiện khi phiên được admin chuyển sang (đang là
    // `taixe01` vẫn thấy), chỉ mất khi đăng nhập thẳng bằng tài khoản thường. Sai chiều nào cũng
    // hỏng buổi demo: mất nút giữa lúc chuyển vai trò, hoặc hiện nút rồi bấm vào ăn 403.
    report(
        "(d) phiên sau switch có canSwitch ⇒ nút còn hiện",
        session.canSwitch && settings.canSwitch && docLai.canSwitch,
        "session=${session.canSwitch} · settings=${settings.canSwitch} · đọc lại=${docLai.canSwitch}",
    )
    report(
        "(d) đang có phiên ⇒ điều kiện hiện nút đúng",
        shouldShowRoleSwitcher(canSwitch = true, hasValidSession = true) &&
            !shouldShowRoleSwitcher(canSwitch = false, hasValidSession = true),
        "CÓ quyền + có phiên ⇒ hiện · KHÔNG quyền ⇒ ẩn",
    )
    report(
        "(d) login thường (thiếu khoá) ⇒ canSwitch false",
        !parseSwitchSessionPayload(
            """{"ok":true,"data":{"token":"tok-selfcheck","roles":["driver"]}}""",
        ).canSwitch,
        "payload cũ không có khoá ⇒ false, không nổ",
    )
    report(
        "(d) nhãn nút theo vai trò",
        switcherLabel(listOf("driver")) == "Tài xế ▾" &&
            switcherLabel(listOf("admin")) == "Quản trị viên ▾",
        "\"${switcherLabel(listOf("driver"))}\" · \"${switcherLabel(listOf("admin"))}\"",
    )
    // Hết khối: xoá cờ lưu bền (không đụng tệp của người dùng — storage ở đây chỉ trong RAM).
    settings.clearSession()
    report(
        "(d) đăng xuất ⇒ xoá canSwitch",
        !settings.canSwitch && !AppSettings(storage).canSwitch,
        "trong RAM + trong kho lưu bền đều false",
    )
}

/**
 * Một con số spec của bộ kit: giá trị thực trong token, giá trị spec đòi, đơn vị.
 * Khai ở cấp file (không phải cấp hàm) — Kotlin không cho `data class` cục bộ trong hàm.
 */
private data class KitSpec(val name: String, val actual: Float, val expected: Float, val unit: String)

/**
 * =====================================================================================
 * Khối 11 — BỘ KIT GIAO DIỆN (`ui/kit`): "preview bằng assert" chạy được khi offline.
 *
 * Vì sao là "preview" mà không phải ảnh: self-check chạy TRƯỚC khi mở cửa sổ và chỉ in ra
 * console, nên nó không thể render Compose. Nhưng nó kiểm được đúng những thứ dễ trôi nhất của
 * một design system — đó là giá trị thật của một preview:
 *   (a) mọi con số spec `ui-dashboard-app-screens.md` §2–§6 (bo góc, chiều cao hàng, cỡ số KPI,
 *       tint 12%, vạch nav 3dp, dời 8dp, thời lượng 160–220ms) khớp token;
 *   (b) ánh xạ NHÃN tiếng Việt → giọng màu của kit khớp bảng giọng theo MÃ state — trừ đúng MỘT
 *       chỗ có chủ đích (`picked_up`: theo nhãn là Ok vì chặng đã xong, theo mã là Info vì việc
 *       chưa kết thúc), và chỗ đó được IN RA chứ không giấu;
 *   (c) nhãn lạ (BE đổi chữ) ⇒ Info, không nổ, không thành ô trống.
 *
 * Đây cũng là lưới an toàn cho RV-01: sửa `Lab.CHIP_TINT` thành 0.2 hay đổi hàng bảng thành
 * 40dp (cho "đẹp hơn") là self-check đỏ ngay, không cần mở app ra đo.
 * =====================================================================================
 */
private fun checkLabKit(report: (String, Boolean, String) -> Unit) {
    println("-- 11. Bộ kit giao diện (ui/kit) — token + ánh xạ trạng thái → giọng --")

    listOf(
        KitSpec("bo góc card", Tokens.radiusCard.value, 10f, "dp"),
        KitSpec("bo góc chip", Lab.chipRadius.value, 4f, "dp"),
        KitSpec("hàng bảng tối thiểu", Lab.tableRowMinHeight.value, 44f, "dp"),
        KitSpec("số KPI", Lab.kpiNumberSize.value, 32f, "sp"),
        KitSpec("nhãn KPI", Lab.kpiLabelSize.value, 13f, "sp"),
        KitSpec("chữ bảng", Lab.tableTextSize.value, 13f, "sp"),
        KitSpec("chữ chip", Lab.chipTextSize.value, 13f, "sp"),
        KitSpec("sparkline", Lab.sparklineHeight.value, 40f, "dp"),
        KitSpec("vạch nav active", Lab.navActiveBar.value, 3f, "dp"),
        KitSpec("nav item cao", Lab.navItemHeight.value, 44f, "dp"),
        KitSpec("padding chip ngang", Lab.chipPadH.value, 8f, "dp"),
        KitSpec("padding chip dọc", Lab.chipPadV.value, 2f, "dp"),
        KitSpec("dời entrance", Lab.enterOffset.value, 8f, "dp"),
        KitSpec("tint chip", Lab.CHIP_TINT, 0.12f, "alpha"),
        KitSpec("nền item nav đang mở", Lab.NAV_ACTIVE_TINT, 0.10f, "alpha"),
        KitSpec("nav dọc từ", Lab.SIDEBAR_MIN_WIDTH_DP.toFloat(), 900f, "dp"),
    ).forEach { spec ->
        report(
            spec.name,
            kotlin.math.abs(spec.actual - spec.expected) < 0.001f,
            "%.2f %s (spec %.2f %s)".format(spec.actual, spec.unit, spec.expected, spec.unit),
        )
    }

    report(
        "entrance trong 160–220ms",
        Lab.ENTER_MS in 160..220,
        "${Lab.ENTER_MS}ms",
    )
    report(
        "nội dung rộng tối đa >= ngưỡng split rộng",
        Lab.CONTENT_MAX_WIDTH_DP >= Tokens.BREAKPOINT_SPLIT_WIDE_DP,
        "${Lab.CONTENT_MAX_WIDTH_DP}dp vs ${Tokens.BREAKPOINT_SPLIT_WIDE_DP}dp",
    )

    // ---- Ánh xạ trạng thái → giọng: hai bảng phải nói CÙNG một chuyện ----
    // Bảng theo MÃ state (`stateToneOf`) là "việc còn đang chạy hay đã kết thúc".
    // Bảng theo NHÃN (`labToneOfStatusLabel`) là "chặng này đã xong chưa" — nên `picked_up`
    // cố ý KHÁC nhau (Info vs Ok); in cả hai ra để RV thấy, không để lệch âm thầm.
    val expectedByLabel = mapOf(
        "Mới" to Tone.Info,
        "Đã gán tài xế" to Tone.Info,
        "Đang đến lấy" to Tone.Info,
        "Đã lấy được hàng" to Tone.Ok,
        "Đã về kho" to Tone.Ok,
        "Đã huỷ" to Tone.Warn,
        "Lấy hàng thất bại" to Tone.Bad,
    )
    JobState.ALL.forEach { state ->
        val byCode = labToneOfState(state)
        val label = stateLabelOf(state)
        val byLabel = labToneOfStatusLabel(label)
        val codeExpected = stateToneOf(state)
        val labelExpected = expectedByLabel[label] ?: Tone.Info
        report(
            "state=$state",
            byCode == codeExpected && byLabel == labelExpected,
            "\"$label\" · theo mã ${byCode.name} (chuẩn ${codeExpected.name}) · " +
                "theo nhãn ${byLabel.name} (chuẩn ${labelExpected.name})",
        )
    }

    // Nhãn riêng của hai màn còn lại (không có trong `fields.Selection`) — phải đọc được.
    report(
        "\"Đã nhận\" ⇒ Ok",
        labToneOfStatusLabel("Đã nhận") == Tone.Ok,
        labToneOfStatusLabel("Đã nhận").name,
    )
    report(
        "\"Chờ nhận\" ⇒ Info",
        labToneOfStatusLabel("Chờ nhận") == Tone.Info,
        labToneOfStatusLabel("Chờ nhận").name,
    )
    // Nhãn lạ (BE đổi chữ) không được làm chip thành ô trống hay ném lỗi.
    report(
        "nhãn lạ ⇒ Info",
        labToneOfStatusLabel("Trạng thái mới của BE") == Tone.Info &&
            labToneOfStatusLabel("") == Tone.Info,
        "không nổ, không tô màu lỗi",
    )
}

/**
 * Ảnh JPEG **nhiễu ngẫu nhiên** (nén kém nhất) để test vòng lặp nén.
 * Chất lượng ghi 0.95 để ảnh càng lớn càng tốt — đây là "ảnh xấu nhất" mà tài xế có thể gửi.
 * Seed cố định ⇒ self-check tất định, chạy 2 lần ra cùng con số.
 */
private fun taoAnhNhieuJpeg(rong: Int, cao: Int): ByteArray {
    val random = Random(20261028L)
    val pixels = IntArray(rong * cao) { 0xFF000000.toInt() or random.nextInt(0x1000000) }
    val image = BufferedImage(rong, cao, BufferedImage.TYPE_INT_RGB)
    image.setRGB(0, 0, rong, cao, pixels, 0, rong)

    val out = ByteArrayOutputStream()
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val params = writer.defaultWriteParam.apply {
        compressionMode = ImageWriteParam.MODE_EXPLICIT
        compressionQuality = 0.95f
    }
    try {
        MemoryCacheImageOutputStream(out).use { stream ->
            writer.output = stream
            writer.write(null, IIOImage(image, null, null), params)
        }
    } finally {
        writer.dispose()
    }
    return out.toByteArray()
}

/**
 * Điểm vào KHÔNG mở GUI — chạy bằng `.\gradlew.bat --offline :composeApp:selfCheck`.
 * Mã thoát 0 = đạt, 1 = có mục hỏng (dùng được trong CI/script demo).
 */
fun main() {
    val ok = runSelfCheck()
    if (!ok) kotlin.system.exitProcess(1)
}
