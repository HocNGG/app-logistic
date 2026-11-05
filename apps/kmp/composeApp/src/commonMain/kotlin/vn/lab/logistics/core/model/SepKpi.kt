package vn.lab.logistics.core.model

/**
 * =====================================================================================
 * DASHBOARD CỦA SẾP (`manager` / `sep`) — model + định dạng số.
 *
 * Nguồn dữ liệu: `GET /lab/api/v1/sep/kpi?range=today|7d|30d` (hợp đồng chốt với BE-01):
 * ```
 * {range, kpi:{pendingAssign,enRoute,pickedUpToday,confirmedToday,avgLoadPct},
 *  trend:[{date,created,picked,confirmed}], byState:[{state,label,count}],
 *  recent:[{id,ref,shop,stateLabel,driverName,windowFrom,updatedAt}]}
 * ```
 *
 * NGUYÊN TẮC SỐ LIỆU (spec `ui-dashboard-app-screens.md` §3, §7):
 *  - Mọi trường đều **nullable**: server thiếu field thì UI ghi "chưa có dữ liệu", KHÔNG hiện 0
 *    giả và KHÔNG bịa số. Một `null` khác hẳn một `0` về ý nghĩa nghiệp vụ.
 *  - Chip Δ chỉ xuất hiện khi tính được từ **chuỗi thời gian thật** (xem [SepKpi.confirmedDelta]);
 *    không có mốc so sánh thì không có chip.
 *  - Số/ngày định dạng **vi-VN** (`1.234`, `48,6%`) bằng hàm thuần ở đây — test JVM kiểm được,
 *    không phụ thuộc `java.text` (commonMain không có API đó).
 * =====================================================================================
 */

/** Khoảng thời gian trên toolbar dashboard (spec §6). Ba lựa chọn, không thêm. */
enum class KpiRange(val apiValue: String, val label: String) {
    Today("today", "Hôm nay"),
    SevenDays("7d", "7 ngày"),
    ThirtyDays("30d", "30 ngày"),
    ;

    companion object {
        /** Mặc định mở dashboard: "Hôm nay" — câu hỏi đầu tiên của sếp mỗi sáng. */
        val DEFAULT: KpiRange = Today

        /** Đọc lại giá trị server trả (`range`), lạ/thiếu ⇒ `null` để UI giữ lựa chọn của người dùng. */
        fun fromApiValue(value: String?): KpiRange? {
            val v = value?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.apiValue == v }
        }
    }
}

/** 5 chỉ số KPI. `null` = server không trả ⇒ UI ghi "chưa có dữ liệu". */
data class SepKpiCounters(
    val pendingAssign: Int? = null,
    val enRoute: Int? = null,
    val pickedUpToday: Int? = null,
    val confirmedToday: Int? = null,
    /** Tải chuyến trung bình (%). Có thể là số lẻ nên giữ [Double]. */
    val avgLoadPct: Double? = null,
)

/** Một điểm của xu hướng theo ngày. */
data class SepTrendPoint(
    val date: String,
    val created: Int = 0,
    val picked: Int = 0,
    val confirmed: Int = 0,
)

/** Một lát của donut "cơ cấu trạng thái". */
data class SepStateSlice(
    val state: String,
    val label: String,
    val count: Int,
)

/** Một dòng của bảng "việc gần nhất". */
data class SepRecentJob(
    val id: Int,
    val ref: String,
    val shop: String,
    val stateLabel: String,
    val driverName: String? = null,
    val windowFrom: String? = null,
    val updatedAt: String? = null,
)

/** Toàn bộ payload dashboard đã chuẩn hoá. */
data class SepKpi(
    val range: KpiRange? = null,
    val counters: SepKpiCounters? = null,
    val trend: List<SepTrendPoint> = emptyList(),
    val byState: List<SepStateSlice> = emptyList(),
    val recent: List<SepRecentJob> = emptyList(),
) {

    /** `true` khi KHÔNG có con số nào để vẽ (dùng cho empty state của từng khối). */
    val hasAnyCounter: Boolean
        get() = counters.let {
            it != null && listOf(it.pendingAssign, it.enRoute, it.pickedUpToday, it.confirmedToday)
                .any { value -> value != null }
        }

    /**
     * Δ "đã về kho" so với điểm NGAY TRƯỚC trong chuỗi thời gian (hôm qua với `7d`/`30d`).
     *
     * Chỉ trả về số khi có **≥ 2 điểm** thật; còn lại `null` ⇒ UI KHÔNG hiện chip Δ
     * (spec §3: cấm "↑10.5%" tự bịa). Với `range=today` server thường chỉ trả 1 điểm ⇒ không có
     * mốc so sánh, đúng như thiết kế.
     */
    fun confirmedDelta(): Int? {
        if (trend.size < 2) return null
        val last = trend.last().confirmed
        val prev = trend[trend.size - 2].confirmed
        return last - prev
    }

    /** Tổng số việc trong `byState` — dùng để tính tỷ lệ trên donut. */
    val stateTotal: Int get() = byState.sumOf { it.count }
}

// ---------------------------------------------------------------------------------------
// Định dạng số kiểu vi-VN (hàm thuần, test JVM — không dùng java.text vì commonMain)
// ---------------------------------------------------------------------------------------

/** `1234` → `"1.234"` (dấu chấm phân cách nghìn theo vi-VN). Số âm giữ dấu trừ. */
fun formatViInt(value: Long): String {
    val negative = value < 0
    val digits = value.toString().removePrefix("-")
    val builder = StringBuilder()
    digits.forEachIndexed { index, char ->
        if (index > 0 && (digits.length - index) % 3 == 0) builder.append('.')
        builder.append(char)
    }
    return if (negative) "-$builder" else builder.toString()
}

/** `1234` → `"1.234"`. */
fun formatViInt(value: Int): String = formatViInt(value.toLong())

/**
 * `48.6` → `"48,6"` (dấu phẩy thập phân vi-VN), `48.0` + `decimals=0` → `"48"`.
 * Không bao giờ trả về chuỗi rỗng; `null` do caller quyết định.
 */
fun formatViDecimal(value: Double, decimals: Int = 1): String {
    require(decimals in 0..3) { "decimals phải 0..3" }
    val negative = value < 0
    val factor = when (decimals) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        else -> 1000.0
    }
    val rounded = kotlin.math.round(kotlin.math.abs(value) * factor) / factor
    val whole = rounded.toLong()
    val text = if (decimals == 0) {
        formatViInt(whole)
    } else {
        // Phần lẻ tính bằng số nguyên để tránh sai số dấu phẩy động khi in.
        val fraction = kotlin.math.round((rounded - whole) * factor).toLong()
        val fractionText = fraction.toString().padStart(decimals, '0')
        formatViInt(whole) + "," + fractionText
    }
    return if (negative) "-$text" else text
}

/** `48.6` → `"48,6%"`. */
fun formatViPercent(value: Double, decimals: Int = 1): String =
    formatViDecimal(value, decimals) + "%"

/** Ngày ISO `2026-10-28` (hoặc `2026-10-28T09:00:00`) → `28/10` cho nhãn trục; lạ ⇒ nguyên chuỗi. */
fun formatAxisDate(date: String): String {
    val day = date.take(10)
    if (day.length != 10 || day[4] != '-' || day[7] != '-') return date
    val dd = day.substring(8, 10)
    val mm = day.substring(5, 7)
    return "$dd/$mm"
}

/** `2026-10-28 14:27:56` → `28/10/2026 14:27`; chuỗi lạ/thiếu ⇒ `null` (ô để trống, không bịa). */
fun formatDateTimeVi(raw: String?): String? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val datePart = text.take(10)
    if (datePart.length != 10 || datePart[4] != '-' || datePart[7] != '-') return text
    val dd = datePart.substring(8, 10)
    val mm = datePart.substring(5, 7)
    val yyyy = datePart.substring(0, 4)
    val timePart = text.substringAfter(' ', "").take(5)
    return if (timePart.length == 5 && timePart[2] == ':') "$dd/$mm/$yyyy $timePart" else "$dd/$mm/$yyyy"
}
