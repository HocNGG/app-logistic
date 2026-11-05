package vn.lab.logistics.feature.driver

import vn.lab.logistics.core.model.DriverJob
import vn.lab.logistics.core.model.JobState
import vn.lab.logistics.ui.components.Tone
import vn.lab.logistics.ui.format.formatPackages
import vn.lab.logistics.ui.format.formatWindow

/**
 * Ánh xạ `DriverJob` → câu chữ tiếng Việt để hiển thị.
 *
 * Ba luật ở đây được ĐO trên API thật (2026-10-28), không phải suy đoán:
 *  1. `stateLabel` của SERVER được ưu tiên; chỉ khi vắng mới dùng bảng nhãn cứng của app
 *     ([stateLabelOf]). Nhờ vậy BE thêm state mới thì app cũ vẫn hiện đúng tiếng Việt.
 *  2. `windowFrom`/`windowTo` là chuỗi `HH:mm` và có thể là **`"-"`** ⇒ phải hiện
 *     "Chưa hẹn giờ", không hiện dấu gạch trần.
 *  3. `expectedPackages` **có thể vắng** (null, OQ-22) ⇒ hiện "Chưa rõ số kiện",
 *     tuyệt đối không bịa "0 kiện".
 *
 * Nhãn dưới đây **chép đúng cột thứ 2 của `fields.Selection`** trong model — app và Odoo phải
 * nói cùng một ngôn ngữ, không tự đặt tên khác:
 *   new → Mới · assigned → Đã gán tài xế · en_route → Đang đến lấy · picked_up → Đã lấy được hàng
 *   confirmed → Đã về kho · cancelled → Đã huỷ · failed → Lấy hàng thất bại
 *
 * Gặp state lạ (BE thêm sau này) thì hiển thị nguyên chuỗi + giọng `Info` — app cũ không vỡ
 * khi backend mới ra.
 */

/** Nhãn hiển thị của một mã trạng thái (state lạ → trả nguyên mã). */
fun stateLabelOf(state: String): String = when (state) {
    JobState.NEW -> "Mới"
    JobState.ASSIGNED -> "Đã gán tài xế"
    JobState.EN_ROUTE -> "Đang đến lấy"
    JobState.PICKED_UP -> "Đã lấy được hàng"
    JobState.CONFIRMED -> "Đã về kho"
    JobState.CANCELLED -> "Đã huỷ"
    JobState.FAILED -> "Lấy hàng thất bại"
    else -> state
}

/**
 * "Giọng" màu theo nghĩa nghiệp vụ:
 *  - `Ok`   — kết thúc tốt (`confirmed`: hàng đã về kho)
 *  - `Bad`  — lỗi thật (`failed`: phải báo CS, đơn về kho chờ/chuyến sau)
 *  - `Warn` — bị huỷ (`cancelled`): không phải lỗi của tài xế, nhưng CS cần biết
 *  - `Info` — còn đang chạy (`new` · `assigned` · `en_route` · `picked_up`) hoặc state lạ
 */
fun stateToneOf(state: String): Tone = when (state) {
    JobState.CONFIRMED -> Tone.Ok
    JobState.FAILED -> Tone.Bad
    JobState.CANCELLED -> Tone.Warn
    else -> Tone.Info
}

/**
 * Nhãn trạng thái để hiện trên [vn.lab.logistics.ui.components.StatusPill]:
 * **ưu tiên `stateLabel` của server**, fallback về bảng nhãn cứng.
 *
 * (Tên hàm là `statusLabel` — không trùng tên với property `stateLabel` để tránh nhầm lẫn
 * giữa "nhãn thô server gửi" và "câu chữ app hiển thị".)
 */
fun DriverJob.statusLabel(): String =
    stateLabel?.takeIf { it.isNotBlank() } ?: stateLabelOf(state)

fun DriverJob.stateTone(): Tone = stateToneOf(state)

/**
 * Tài xế còn được bấm "Bắt đầu đi" hay không.
 * Chỉ `new` (chờ nhận) và `assigned` (đã gán) mới nhận việc; các state sau là bước khác
 * (chụp ảnh/ký/xác nhận), còn `confirmed`/`cancelled`/`failed` là trạng thái kết thúc.
 */
fun DriverJob.canStart(): Boolean = state == JobState.NEW || state == JobState.ASSIGNED

/**
 * Việc còn cho phép gửi ảnh + chữ ký hay không — ĐỌC TỪ CONTROLLER, không đoán:
 * `addons/lab_api/controllers/driver.py::capture` chỉ nhận khi
 * `job.state in ("assigned", "en_route")`, còn lại trả **409 INVALID_STATE**.
 */
fun DriverJob.canCapture(): Boolean =
    state == JobState.ASSIGNED || state == JobState.EN_ROUTE

/**
 * Việc còn cho phép báo "không lấy được" hay không — `action_fail` của model chỉ nhận
 * `("assigned", "en_route", "picked_up")`; controller map `UserError` đó thành **409**.
 * Đã `picked_up` vẫn báo được (hàng lấy rồi mới phát hiện thiếu) — đúng nghiệp vụ docs/07.
 */
fun DriverJob.canFail(): Boolean =
    state == JobState.ASSIGNED ||
        state == JobState.EN_ROUTE ||
        state == JobState.PICKED_UP

/** Câu giải thích thay cho nút hành động khi việc không còn nhận được nữa. */
fun DriverJob.nextStepHint(): String = when (state) {
    JobState.EN_ROUTE -> "Đang trên đường tới shop — tới nơi thì chụp ảnh + ký biên nhận."
    JobState.PICKED_UP -> "Đã lấy được hàng — mang về kho rồi xác nhận để chuyển sang \"Đã về kho\"."
    JobState.CONFIRMED -> "Việc đã hoàn tất: hàng đã về kho."
    JobState.CANCELLED -> "Việc đã huỷ — không cần tới shop. CS đã được thông báo."
    JobState.FAILED -> "Lấy hàng thất bại — đã báo CS để đưa đơn về kho chờ hoặc chuyến sau."
    else -> "Việc chưa sẵn sàng để nhận."
}

/**
 * "08:30–10:00" · "Từ 08:30" · "Trước 10:00" · "Chưa hẹn giờ".
 *
 * ⚠️ Server gửi `"-"` (chuỗi) khi job chưa đặt khung giờ — đây **không phải** giờ, phải coi
 * như "không có" để hiện câu tiếng Việt thay vì "–" trần trên màn demo.
 *
 * Cài đặt nằm ở [formatWindow] (dùng chung với màn CS — hai màn hiển thị cùng một dữ liệu).
 */
fun DriverJob.windowLabel(): String = formatWindow(windowFrom, windowTo)

/**
 * Nhãn số kiện — đúng thuật ngữ glossary docs/00 §10 ("kiện", không phải "thùng"/"hộp").
 * `expectedPackages = null` ⇒ "Chưa rõ số kiện" (KHÔNG bịa 0 — OQ-22).
 */
fun DriverJob.packagesLabel(): String = formatPackages(expectedPackages)

// =====================================================================================
// TÀI KHOẢN ĐANG ĐĂNG NHẬP TRÊN MÁY NÀY (góc trên màn tài xế)
//
// Vì sao phải hiện: một bộ máy có thể được hai tài xế dùng chung theo ca. Nếu màn hình chỉ có
// "Việc hôm nay" thì người cầm máy không biết mình đang gửi biên nhận bằng tài khoản của ai —
// sai tài khoản là sai người chịu trách nhiệm ảnh + chữ ký, không có cách nào truy lại sau.
// Tên + tài khoản đều lấy từ `AppSettings` (đã lưu sau khi đăng nhập), không gọi thêm API.
// =====================================================================================

/**
 * Câu chào góc trên: `"Xin chào Trần Văn Hùng"`; tên rỗng (chế độ mẫu, chưa có tài khoản) ⇒
 * `"Việc hôm nay"` — KHÔNG bịa tên, KHÔNG hiện "Xin chào " trống.
 */
fun driverGreeting(accountName: String): String {
    val ten = accountName.trim()
    return if (ten.isEmpty()) "Việc hôm nay" else "Xin chào $ten"
}

/**
 * Dòng phụ dưới câu chào: `"Tài khoản: taixe01 · 2 việc · Nguồn: Odoo thật"`.
 *
 * Tài khoản đứng ĐẦU dòng vì đó là thứ dễ bị cắt mất nhất khi cửa sổ hẹp (dòng này chỉ 1 dòng,
 * cắt bằng ellipsis) — và cũng là thứ quan trọng nhất cần đọc đúng.
 * Chưa biết tài khoản ⇒ bỏ hẳn phần đó, không hiện "Tài khoản: ".
 */
fun driverSubtitle(accountLogin: String, jobCount: Int, dataSourceLabel: String): String {
    val account = accountLogin.trim().takeIf { it.isNotEmpty() }?.let { "Tài khoản: $it" }
    val rest = "$jobCount việc · Nguồn: $dataSourceLabel"
    return if (account == null) rest else "$account · $rest"
}
