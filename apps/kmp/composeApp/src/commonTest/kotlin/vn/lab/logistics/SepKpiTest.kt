package vn.lab.logistics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import vn.lab.logistics.core.model.KpiRange
import vn.lab.logistics.core.model.SepKpi
import vn.lab.logistics.core.model.formatAxisDate
import vn.lab.logistics.core.model.formatDateTimeVi
import vn.lab.logistics.core.model.formatViDecimal
import vn.lab.logistics.core.model.formatViInt
import vn.lab.logistics.core.model.formatViPercent
import vn.lab.logistics.core.net.ApiException
import vn.lab.logistics.core.net.parseSepKpiPayload
import vn.lab.logistics.feature.dashboard.toneOfStateLabel
import vn.lab.logistics.ui.components.Tone

/**
 * =====================================================================================
 * DASHBOARD CỦA SẾP — test JVM (offline, `:composeApp:desktopTest`).
 *
 * Ba nhóm bất biến phải khoá bằng assert (spec `ui-dashboard-app-screens.md`):
 *  1. **Parser chịu được thiếu field**: BE đang làm endpoint song song; thiếu `kpi`, `trend`,
 *     `byState`, `recent` ⇒ màn vẫn phải mở được với "chưa có dữ liệu", KHÔNG ném lỗi, KHÔNG
 *     tự điền 0 (`null` ≠ `0` về nghiệp vụ).
 *  2. **Chip Δ chỉ khi có mốc thật**: 1 điểm thời gian ⇒ không có Δ (không bịa "+x%");
 *     ≥ 2 điểm ⇒ Δ = hôm nay − hôm trước.
 *  3. **Định dạng số vi-VN**: `1.234`, `48,6%`, ngày `dd/MM[/yyyy]` — sai là số liệu đọc sai.
 * =====================================================================================
 */
class SepKpiTest {

    // ------------------------------------------------------------------ parser: payload đầy đủ

    @Test
    fun `parse payload day du`() {
        val kpi = parseSepKpiPayload(PAYLOAD_FULL)
        assertEquals(KpiRange.SevenDays, kpi.range)
        assertEquals(7, kpi.counters?.pendingAssign)
        assertEquals(3, kpi.counters?.enRoute)
        assertEquals(9, kpi.counters?.pickedUpToday)
        assertEquals(12, kpi.counters?.confirmedToday)
        assertEquals(63.5, kpi.counters?.avgLoadPct)
        assertEquals(3, kpi.trend.size)
        assertEquals(2, kpi.byState.size)
        assertEquals(2, kpi.recent.size)
        assertEquals("LH/26/0101", kpi.recent.first().ref)
        assertEquals("Đã về kho", kpi.recent.first().stateLabel)
        assertTrue(kpi.hasAnyCounter)
        assertEquals(20, kpi.stateTotal)
    }

    @Test
    fun `chi tra range thi cac khoi khac rong chu khong no`() {
        val kpi = parseSepKpiPayload("""{"ok":true,"data":{"range":"today"}}""")
        assertEquals(KpiRange.Today, kpi.range)
        assertNull(kpi.counters, "thiếu kpi ⇒ null, không phải object rỗng")
        assertTrue(kpi.trend.isEmpty())
        assertTrue(kpi.byState.isEmpty())
        assertTrue(kpi.recent.isEmpty())
        assertEquals(false, kpi.hasAnyCounter)
    }

    @Test
    fun `du lieu null thi tra payload rong`() {
        val kpi = parseSepKpiPayload("""{"ok":true,"data":null}""")
        assertEquals(SepKpi(), kpi)
    }

    @Test
    fun `envelope loi thi nem ApiException tieng Viet`() {
        val loi = assertFailsWith<ApiException> {
            parseSepKpiPayload("""{"ok":false,"error":{"code":"VALIDATION","message":"Kỳ báo cáo không hợp lệ."}}""")
        }
        assertEquals("Kỳ báo cáo không hợp lệ.", loi.message)
    }

    @Test
    fun `401 va trang HTML deu la phien het han`() {
        assertFailsWith<ApiException.SessionExpired> { parseSepKpiPayload("{}", status = 401) }
        assertFailsWith<ApiException.SessionExpired> {
            parseSepKpiPayload("<!DOCTYPE html><html><body>login</body></html>")
        }
    }

    // ------------------------------------------------------------------ parser: dữ liệu bẩn

    @Test
    fun `trend thieu date bi bo con lai van parse`() {
        val kpi = parseSepKpiPayload(
            """{"ok":true,"data":{"trend":[{"date":"2026-10-01","created":2},{"created":5},{"date":"2026-10-02"}]}}""",
        )
        assertEquals(2, kpi.trend.size)
        assertEquals("2026-10-01", kpi.trend.first().date)
        assertEquals(2, kpi.trend.first().created)
        assertEquals(0, kpi.trend.last().created, "thiếu created ⇒ 0 cho ĐIỂM đó (điểm có date là thật)")
    }

    @Test
    fun `byState thieu nhan hoac count bi bo`() {
        val kpi = parseSepKpiPayload(
            """{"ok":true,"data":{"byState":[{"state":"new","label":"Mới","count":4},{"state":"x"},{"label":"Không có count"}]}}""",
        )
        assertEquals(1, kpi.byState.size)
        assertEquals("Mới", kpi.byState.first().label)
        assertEquals(4, kpi.byState.first().count)
    }

    @Test
    fun `recent thieu id bi bo va cat toi da 10 dong`() {
        val rows = (1..13).joinToString(",") { index ->
            """{"id":$index,"ref":"LH/26/0$index","shop":"Shop $index","stateLabel":"Mới"}"""
        }
        val kpi = parseSepKpiPayload("""{"ok":true,"data":{"recent":[$rows,{"ref":"không id"}]}}""")
        assertEquals(10, kpi.recent.size, "bảng chỉ 10 dòng gần nhất (spec §6)")
        assertEquals("LH/26/01", kpi.recent.first().ref)
    }

    @Test
    fun `recent thieu shop hoac stateLabel thi dung cau thay the khong trong`() {
        val kpi = parseSepKpiPayload("""{"ok":true,"data":{"recent":[{"id":9,"ref":null,"shop":null,"stateLabel":null}]}}""")
        assertEquals(1, kpi.recent.size)
        assertEquals("#9", kpi.recent.first().ref)
        assertEquals("Shop chưa có tên", kpi.recent.first().shop)
        assertEquals("Chưa rõ trạng thái", kpi.recent.first().stateLabel)
    }

    // ------------------------------------------------------------------ chip Δ (không bịa số)

    @Test
    fun `mot diem thoi gian thi khong co delta`() {
        val kpi = parseSepKpiPayload("""{"ok":true,"data":{"trend":[{"date":"2026-10-29","confirmed":12}]}}""")
        assertNull(kpi.confirmedDelta(), "không có mốc so sánh ⇒ KHÔNG hiện chip Δ")
    }

    @Test
    fun `hai diem thoi gian thi delta la chenh lech that`() {
        val kpi = parseSepKpiPayload(
            """{"ok":true,"data":{"trend":[{"date":"2026-10-28","confirmed":9},{"date":"2026-10-29","confirmed":12}]}}""",
        )
        assertEquals(3, kpi.confirmedDelta())
        assertEquals(-4, parseSepKpiPayload(
            """{"ok":true,"data":{"trend":[{"date":"2026-10-28","confirmed":9},{"date":"2026-10-29","confirmed":5}]}}""",
        ).confirmedDelta())
    }

    // ------------------------------------------------------------------ range

    @Test
    fun `range doc dung ba gia tri cua hop dong va gia tri la thi tra null`() {
        assertEquals(KpiRange.Today, KpiRange.fromApiValue("today"))
        assertEquals(KpiRange.SevenDays, KpiRange.fromApiValue("7d"))
        assertEquals(KpiRange.ThirtyDays, KpiRange.fromApiValue(" 30D "))
        assertNull(KpiRange.fromApiValue("quy-nay"))
        assertNull(KpiRange.fromApiValue(null))
        assertEquals(KpiRange.Today, KpiRange.DEFAULT)
    }

    // ------------------------------------------------------------------ định dạng vi-VN

    @Test
    fun `so nguyen dinh dang vi-VN co dau cham phan cach nghin`() {
        assertEquals("0", formatViInt(0))
        assertEquals("999", formatViInt(999))
        assertEquals("1.234", formatViInt(1234))
        assertEquals("1.234.567", formatViInt(1_234_567))
        assertEquals("-1.234", formatViInt(-1234))
        assertEquals("12.345", formatViInt(12_345L))
    }

    @Test
    fun `so le va phan tram dinh dang vi-VN co dau phay thap phan`() {
        assertEquals("48,6", formatViDecimal(48.6))
        assertEquals("48,6%", formatViPercent(48.6))
        assertEquals("48%", formatViPercent(48.0, decimals = 0))
        assertEquals("12,25", formatViDecimal(12.25, decimals = 2))
        assertEquals("1.234,5", formatViDecimal(1234.5))
        assertEquals("0,0", formatViDecimal(0.0))
    }

    @Test
    fun `ngay hien thi kieu Viet Nam`() {
        assertEquals("28/10", formatAxisDate("2026-10-28"))
        assertEquals("29/10/2026 08:10", formatDateTimeVi("2026-10-29 08:10:00"))
        assertEquals("29/10/2026", formatDateTimeVi("2026-10-29"))
        assertNull(formatDateTimeVi(null))
        assertNull(formatDateTimeVi("   "))
        // Chuỗi lạ: giữ nguyên để không nuốt dữ liệu của server.
        assertEquals("hôm qua", formatDateTimeVi("hôm qua"))
    }

    // ------------------------------------------------------------------ chip trạng thái

    @Test
    fun `nhan trang thai tieng Viet map dung tone`() {
        assertEquals(Tone.Ok, toneOfStateLabel("Đã về kho"))
        assertEquals(Tone.Ok, toneOfStateLabel("Đã lấy được hàng"))
        assertEquals(Tone.Bad, toneOfStateLabel("Lấy hàng thất bại"))
        assertEquals(Tone.Warn, toneOfStateLabel("Đã huỷ"))
        assertEquals(Tone.Info, toneOfStateLabel("Đang đến lấy"))
        // Nhãn lạ (BE đổi chữ) vẫn phải ra chip đọc được, không vỡ.
        assertEquals(Tone.Info, toneOfStateLabel("Trạng thái mới của BE"))
    }

    private companion object {
        val PAYLOAD_FULL = """
            {"ok":true,"data":{
              "range":"7d",
              "kpi":{"pendingAssign":7,"enRoute":3,"pickedUpToday":9,"confirmedToday":12,"avgLoadPct":63.5},
              "trend":[
                {"date":"2026-10-27","created":4,"picked":3,"confirmed":9},
                {"date":"2026-10-28","created":6,"picked":4,"confirmed":10},
                {"date":"2026-10-29","created":5,"picked":2,"confirmed":12}
              ],
              "byState":[
                {"state":"confirmed","label":"Đã về kho","count":12},
                {"state":"en_route","label":"Đang đến lấy","count":8}
              ],
              "recent":[
                {"id":501,"ref":"LH/26/0101","shop":"Shop Thời trang Mộc Lan","stateLabel":"Đã về kho",
                 "driverName":"Trần Văn Hùng","windowFrom":"01:30","updatedAt":"2026-10-29 08:10:00"},
                {"id":502,"ref":"LH/26/0102","shop":"Nhà thuốc An Khang","stateLabel":"Đang đến lấy",
                 "driverName":null,"windowFrom":null,"updatedAt":null}
              ]
            }}
        """.trimIndent()
    }
}
