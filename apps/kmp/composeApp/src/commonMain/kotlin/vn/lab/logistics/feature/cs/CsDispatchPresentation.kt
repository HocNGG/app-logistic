package vn.lab.logistics.feature.cs

import vn.lab.logistics.core.model.CsDriver
import vn.lab.logistics.core.model.CsJob
import vn.lab.logistics.core.model.JobState
import vn.lab.logistics.feature.driver.stateLabelOf
import vn.lab.logistics.feature.driver.stateToneOf
import vn.lab.logistics.ui.components.Tone
import vn.lab.logistics.ui.format.formatPackages
import vn.lab.logistics.ui.format.formatWindow

/**
 * Ánh xạ [CsJob] → câu chữ tiếng Việt (hàm THUẦN, không Compose).
 *
 * Ba luật ở đây giống hệt màn tài xế vì CÙNG một model `lab.yeu.cau.lay_hang`:
 *  1. `stateLabel` của SERVER được ưu tiên; chỉ khi vắng mới dùng bảng nhãn của app
 *     ([stateLabelOf]) ⇒ BE thêm state mới thì app cũ vẫn hiện tiếng Việt.
 *  2. `windowFrom/To` là chuỗi `HH:mm`, có thể là `"-"` ⇒ "Chưa hẹn giờ".
 *  3. `expectedPackages` có thể VẮNG ⇒ "Chưa rõ số kiện" (không bịa "0 kiện" — OQ-22).
 *
 * Thuật ngữ theo glossary docs/00 §10: **kiện**, **chuyến** — không dùng "thùng"/"hộp".
 */

/** Nhãn trạng thái: ưu tiên nhãn của server, fallback bảng nhãn của app. */
fun CsJob.statusLabel(): String =
    stateLabel?.takeIf { it.isNotBlank() } ?: stateLabelOf(state)

/** Giọng màu theo nghĩa nghiệp vụ (dùng chung bảng với màn tài xế). */
fun CsJob.stateTone(): Tone = stateToneOf(state)

/** `"08:30–10:00"` · `"Từ 08:30"` · `"Trước 10:00"` · `"Chưa hẹn giờ"`. */
fun CsJob.windowLabel(): String = formatWindow(windowFrom, windowTo)

/** `"4 kiện"` / `"Chưa rõ số kiện"`. */
fun CsJob.packagesLabel(): String = formatPackages(expectedPackages)

/** Tên tài xế đang phụ trách, hoặc câu nói rõ việc chưa được gán. */
fun CsJob.driverLabel(): String = driverName?.takeIf { it.isNotBlank() } ?: "Chưa gán tài xế"

/** Việc này đã có tài xế chưa (để UI hiện pill "Đã gán" thay vì "Cần gán"). */
fun CsJob.hasDriver(): Boolean = !driverName.isNullOrBlank()

/**
 * Bộ lọc danh sách việc cần gán (yêu cầu 2026-11-03): **Tất cả · Mới · Đã gán**.
 *
 * Lọc theo `state` của SERVER (`new` = chưa gán, `assigned` = đã gán) — app không tự suy từ tên
 * tài xế. Endpoint chỉ trả hai state này (`TRANG_THAI_CAN_DIEU_PHOI`), nên ba chip luôn cộng đủ.
 */
enum class CsJobFilter(val label: String) {
    ALL("Tất cả"),
    NEW("Mới"),
    ASSIGNED("Đã gán"),
    ;

    fun matches(job: CsJob): Boolean = when (this) {
        ALL -> true
        NEW -> job.state == JobState.NEW
        ASSIGNED -> job.state == JobState.ASSIGNED
    }
}

/**
 * Việc tạo **MỚI NHẤT xếp đầu** (yêu cầu khách 2026-11-03 — bản trước xếp theo `id`).
 *
 * Khoá 1: `createDate` GIẢM DẦN — mốc tạo thật của server (`yyyy-MM-dd HH:mm:ss`, UTC). Chuỗi ISO
 * cùng độ dài nên so từ điển = so thời gian, không cần parse ngày.
 * Khoá 2: `id` GIẢM DẦN — chốt thứ tự khi hai việc cùng giây (hoặc cùng vắng mốc), để danh sách
 * **ổn định** giữa hai lần tải (không tự đảo chỗ, người dùng không mất dấu việc vừa xem).
 * Khoá 3: việc **VẮNG** `createDate` xuống cuối — server cũ chưa trả trường này thì chúng không
 * được chen lên trước những việc có mốc thật.
 *
 * Vì sao không dùng `id` làm khoá chính nữa: `id` của Odoo chỉ *thường* trùng thứ tự tạo; sau
 * import/backfill thì "id lớn hơn" không còn nghĩa "tạo sau" — đúng cái làm danh sách lộn xộn.
 */
fun sortNewestFirst(jobs: List<CsJob>): List<CsJob> = jobs.sortedWith(
    compareBy<CsJob> { it.createDate == null } // false (có mốc) trước, true (vắng) sau
        .thenByDescending { it.createDate ?: "" }
        .thenByDescending { it.id },
)

/** Danh sách hiển thị = lọc theo [filter] rồi xếp mới nhất lên đầu. */
fun filterAndSort(jobs: List<CsJob>, filter: CsJobFilter): List<CsJob> =
    sortNewestFirst(jobs.filter(filter::matches))

/** Số việc của mỗi chip lọc (hiện cạnh nhãn: "Mới (3)"). */
fun CsJobFilter.countIn(jobs: List<CsJob>): Int = jobs.count(this::matches)

/**
 * Nhãn MỘT DÒNG của tài xế trong ô chọn: `"Trần Văn Hùng · taixe01"`.
 *
 * Vì sao cần `login` trên UI: một nhóm có thể có hai tài xế cùng tên hiển thị gần giống nhau,
 * và người phân công phải nói được với tài xế "đăng nhập bằng tài khoản taixe01" — nếu ô chọn
 * chỉ có tên thì không ai đối chiếu được với máy đang cầm.
 *
 * `login` vắng (server cũ, hoặc tài xế chưa có tài khoản) ⇒ **chỉ tên**, không hiện dấu "·" treo.
 */
fun CsDriver.displayLabel(): String {
    val account = login?.trim()?.takeIf { it.isNotEmpty() } ?: return name
    return "$name · $account"
}

/**
 * Dòng tài khoản dưới ô chọn: `"Tài khoản: taixe01"`; `null` khi chưa biết tài khoản
 * (UI ẩn hẳn dòng này thay vì hiện "Tài khoản: null").
 */
fun CsDriver.accountLine(): String? =
    login?.trim()?.takeIf { it.isNotEmpty() }?.let { "Tài khoản: $it" }
