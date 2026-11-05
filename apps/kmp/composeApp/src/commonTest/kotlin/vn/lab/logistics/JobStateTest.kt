package vn.lab.logistics

import vn.lab.logistics.core.model.JobState
import vn.lab.logistics.feature.driver.stateLabelOf
import vn.lab.logistics.feature.driver.stateToneOf
import vn.lab.logistics.ui.components.Tone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit test cho "hợp đồng" dễ lệch nhất giữa app và Odoo:
 * ánh xạ 7 mã trạng thái của `lab.yeu.cau.lay_hang` (model BE-01) → nhãn tiếng Việt + giọng màu.
 *
 * Vì sao phải có test (không chỉ selfCheck): `selfCheck` là chương trình in bảng, còn đây là
 * assert thật — CI/Gradle sẽ fail nếu ai đổi nhãn hoặc đổi tone mà không cập nhật test.
 *
 * ⚠️ Khi BE-01 đổi `fields.Selection` của model: sửa `JobState` + file này + `selfCheck` cùng lúc.
 * Đối chiếu lần cuối: 2026-10-28.
 */
class JobStateTest {

    @Test
    fun `co dung 7 ma trang thai`() {
        assertEquals(7, JobState.ALL.size, "model BE-01 có đúng 7 state")
    }

    @Test
    fun `moi ma co nhan rieng va khong rong`() {
        val labels = JobState.ALL.map { stateLabelOf(it) }
        labels.forEach { label ->
            assertTrue(label.isNotBlank(), "nhãn không được rỗng")
            assertTrue(label != "null", "nhãn không được là chuỗi null")
        }
        assertEquals(
            JobState.ALL.size,
            labels.toSet().size,
            "mỗi state phải có nhãn KHÁC nhau (không được trùng)",
        )
    }

    @Test
    fun `nhan phai khac ma tho`() {
        JobState.ALL.forEach { code ->
            assertNotEquals(code, stateLabelOf(code), "phải dịch sang tiếng Việt, không hiển thị mã thô")
        }
    }

    @Test
    fun `nhan dung nguyen van cua model Odoo`() {
        // Chép từ cột thứ 2 của fields.Selection trong addons/lab_laixe/models/lab_yeu_cau_lay_hang.py
        assertEquals("Mới", stateLabelOf(JobState.NEW))
        assertEquals("Đã gán tài xế", stateLabelOf(JobState.ASSIGNED))
        assertEquals("Đang đến lấy", stateLabelOf(JobState.EN_ROUTE))
        assertEquals("Đã lấy được hàng", stateLabelOf(JobState.PICKED_UP))
        assertEquals("Đã về kho", stateLabelOf(JobState.CONFIRMED))
        assertEquals("Đã huỷ", stateLabelOf(JobState.CANCELLED))
        assertEquals("Lấy hàng thất bại", stateLabelOf(JobState.FAILED))
    }

    @Test
    fun `gio ng mau dung nghia nghiep vu`() {
        assertEquals(Tone.Ok, stateToneOf(JobState.CONFIRMED), "hàng đã về kho = kết thúc tốt")
        assertEquals(Tone.Bad, stateToneOf(JobState.FAILED), "lấy hàng thất bại = lỗi thật")
        assertEquals(Tone.Warn, stateToneOf(JobState.CANCELLED), "bị huỷ = cần biết, không phải lỗi tài xế")
        listOf(JobState.NEW, JobState.ASSIGNED, JobState.EN_ROUTE, JobState.PICKED_UP).forEach {
            assertEquals(Tone.Info, stateToneOf(it), "$it còn đang chạy -> Info")
        }
    }

    @Test
    fun `state la khong lam vo app`() {
        val la = "state_moi_cua_be"
        assertEquals(la, stateLabelOf(la), "state lạ -> hiển thị nguyên chuỗi")
        assertEquals(Tone.Info, stateToneOf(la), "state lạ -> Info, không nổ")
    }
}
