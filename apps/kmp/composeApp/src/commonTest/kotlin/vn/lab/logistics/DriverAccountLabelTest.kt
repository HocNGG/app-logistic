package vn.lab.logistics

import vn.lab.logistics.feature.driver.driverGreeting
import vn.lab.logistics.feature.driver.driverSubtitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * =====================================================================================
 * NHÃN "TÀI KHOẢN ĐANG ĐĂNG NHẬP TRÊN MÁY NÀY" — test JVM, chạy offline.
 *
 * Vì sao phải khoá bằng test: dòng chào + dòng tài khoản ở góc trên màn tài xế là thứ DUY NHẤT
 * nói cho người cầm máy biết mình đang làm việc bằng tài khoản nào (một bộ máy có thể dùng chung
 * theo ca). Ba nhánh dễ hỏng, đều được assert ở đây:
 *   (a) có tên ⇒ "Xin chào <tên>";
 *   (b) KHÔNG có tên (chế độ mẫu) ⇒ giữ "Việc hôm nay", không bịa tên, không ra "Xin chào ";
 *   (c) tài khoản đứng ĐẦU dòng phụ (vì dòng này 1 dòng, cắt bằng ellipsis khi cửa sổ hẹp) và
 *       biến mất hoàn toàn khi chưa biết tài khoản (không hiện "Tài khoản: ").
 * =====================================================================================
 */
class DriverAccountLabelTest {

    @Test
    fun `co ten tai xe thi chao dung ten`() {
        assertEquals("Xin chào Trần Văn Hùng", driverGreeting("Trần Văn Hùng"))
        // Tên có khoảng trắng thừa từ server: cắt, không hiện "Xin chào  Hùng".
        assertEquals("Xin chào Hùng", driverGreeting("  Hùng  "))
    }

    @Test
    fun `khong co ten thi giu tieu de nghiep vu, khong bia ten`() {
        assertEquals("Việc hôm nay", driverGreeting(""))
        assertEquals("Việc hôm nay", driverGreeting("   "))
    }

    @Test
    fun `dong phu hien tai khoan truoc so viec va nguon du lieu`() {
        assertEquals(
            "Tài khoản: taixe01 · 2 việc · Nguồn: Odoo thật",
            driverSubtitle(accountLogin = "taixe01", jobCount = 2, dataSourceLabel = "Odoo thật"),
        )
    }

    @Test
    fun `chua biet tai khoan thi bo han phan tai khoan`() {
        val line = driverSubtitle(accountLogin = "", jobCount = 3, dataSourceLabel = "Mẫu")
        assertEquals("3 việc · Nguồn: Mẫu", line)
        assertTrue(!line.contains("Tài khoản"), "không hiện \"Tài khoản: \" rỗng: $line")
    }
}
