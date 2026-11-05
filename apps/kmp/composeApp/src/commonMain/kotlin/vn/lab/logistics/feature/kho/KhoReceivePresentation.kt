package vn.lab.logistics.feature.kho

import vn.lab.logistics.core.model.KhoJob
import vn.lab.logistics.core.model.ScanOutcome
import vn.lab.logistics.ui.components.Tone
import vn.lab.logistics.ui.format.formatPackages
import vn.lab.logistics.ui.format.formatWeightKg

/**
 * Ánh xạ [KhoJob] → câu chữ tiếng Việt cho màn kho (hàm THUẦN, không Compose).
 *
 * Luật: số nào server chưa có thì hiện câu "Chưa rõ …", **không bịa số 0** — thủ kho dựa vào
 * số kiện/kg để đối chiếu với tài xế, một số 0 giả sẽ làm sai cả biên bản.
 *
 * Thuật ngữ glossary docs/00 §10: **kiện**, **khối lượng**, **phiếu nhập kho**.
 */

/** `"4 kiện"` / `"Chưa rõ số kiện"`. */
fun KhoJob.packagesLabel(): String = formatPackages(packages)

/** `"12,5 kg"` / `"Chưa rõ khối lượng"` (dấu phẩy thập phân kiểu Việt Nam). */
fun KhoJob.weightLabel(): String = formatWeightKg(weightKg)

/** Tên tài xế đã mang hàng về, hoặc câu nói rõ chưa xác định. */
fun KhoJob.driverLabel(): String = driverName?.takeIf { it.isNotBlank() } ?: "Chưa rõ tài xế"

/** Số phiếu nhập kho đã có (server trả trong payload thì coi như đã nhận trước đó). */
fun KhoJob.pickingLabelOrNull(): String? = pickingName?.takeIf { it.isNotBlank() }

/** Mã đơn bán (`S0xxxx`) gắn với việc này, hoặc `null` nếu chưa có. */
fun KhoJob.saleOrderLabelOrNull(): String? = saleOrderName?.takeIf { it.isNotBlank() }

/**
 * Nhãn trạng thái theo đúng ngôn ngữ nghiệp vụ Bước 6 & 7:
 *  - Chưa nhận (`picked_up`) ⇒ **"Đã lấy được hàng"** (hoặc `stateLabel` của server)
 *  - Đã nhận (`confirmed`)   ⇒ **"Đã về kho"**
 */
fun KhoJob.statusLabel(received: Boolean): String =
    if (received) "Đã về kho" else stateLabel?.takeIf { it.isNotBlank() } ?: "Đã lấy được hàng"

/**
 * Bộ lọc danh sách nhận hàng của kho (Bước 6 & 7):
 *  - **Tất cả**: toàn bộ việc chờ nhận + vừa về kho
 *  - **Đã lấy được hàng** (`picked_up`): việc tài xế vừa lấy về, chờ kho bấm "Nhận hàng & in tem" (Bước 6)
 *  - **Đã về kho** (`confirmed`): việc đã nhận (hoặc nhận tại quầy), mở để quét tem nhận (Bước 7)
 */
enum class KhoJobFilter(val label: String) {
    ALL("Tất cả"),
    PICKED_UP("Đã lấy được hàng"),
    CONFIRMED("Đã về kho"),
    ;

    fun matches(job: KhoJob, isReceived: (Int) -> Boolean): Boolean = when (this) {
        ALL -> true
        PICKED_UP -> !isReceived(job.id)
        CONFIRMED -> isReceived(job.id)
    }

    fun countIn(jobs: List<KhoJob>, isReceived: (Int) -> Boolean): Int =
        jobs.count { matches(it, isReceived) }
}

/**
 * Xếp việc **mới nhất / mã `LH/26/...` mới nhất ở đầu danh sách**:
 *  - Khoá 1: số thứ tự cuối mã `ref` (vd `LH/26/5302` → `5302`) giảm dần
 *  - Khoá 2: `id` giảm dần
 */
fun sortKhoJobsNewestFirst(jobs: List<KhoJob>): List<KhoJob> = jobs.sortedWith(
    compareByDescending<KhoJob> { refSequenceOrNull(it.ref) ?: Int.MIN_VALUE }
        .thenByDescending { it.id },
)

private fun refSequenceOrNull(ref: String): Int? =
    ref.substringAfterLast('/', "").trim().toIntOrNull()

/** Lọc theo [filter] và giữ thứ tự đơn mới nhất lên đầu. */
fun filterAndSortKhoJobs(
    jobs: List<KhoJob>,
    filter: KhoJobFilter,
    isReceived: (Int) -> Boolean,
): List<KhoJob> = sortKhoJobsNewestFirst(jobs.filter { filter.matches(it, isReceived) })

// ---------------------------------------------------------------------------------------
// QUÉT TEM — ánh xạ kết quả sang giọng màu của chip (tách khỏi model: model không biết UI)
// ---------------------------------------------------------------------------------------

/**
 * Giọng màu cho chip kết quả quét (mọi cặp màu đã đo ở selfCheck khối 1).
 *
 *  - `ok` ⇒ Ok (xanh) — kiện vào kho, **có** tính vào tiến độ;
 *  - `trung` ⇒ Warn (vàng) — không phải lỗi, nhưng **không tăng tiến độ**: người quét phải biết
 *    để không tưởng máy bỏ qua kiện;
 *  - `sai_moc` · `khong_tim_thay` ⇒ Bad (đỏ) — dừng lại kiểm tem.
 */
fun scanToneOf(outcome: ScanOutcome): Tone = when (outcome) {
    ScanOutcome.OK -> Tone.Ok
    ScanOutcome.TRUNG -> Tone.Warn
    ScanOutcome.SAI_MOC -> Tone.Bad
    ScanOutcome.KHONG_TIM_THAY -> Tone.Bad
}

/** `"Đã quét 3/7 kiện"`; server chưa biết tổng thì nói rõ thay vì hiện `0/0`. */
fun tienDoLabel(daQuet: Int, tong: Int): String =
    if (tong <= 0) "Chưa biết tổng số kiện — quét tem để bắt đầu" else "Đã quét $daQuet/$tong kiện"
