package vn.lab.logistics

import vn.lab.logistics.core.net.ApiException
import vn.lab.logistics.core.net.assignRequestBody
import vn.lab.logistics.core.net.parseAssignResult
import vn.lab.logistics.core.net.parseCsJobsPayload
import vn.lab.logistics.core.net.parseKhoChoNhanPayload
import vn.lab.logistics.core.net.parseKhoReceiveResult
import vn.lab.logistics.core.model.JobState
import vn.lab.logistics.feature.cs.accountLine
import vn.lab.logistics.feature.cs.displayLabel
import vn.lab.logistics.ui.format.formatPackages
import vn.lab.logistics.ui.format.formatWeightKg
import vn.lab.logistics.ui.format.formatWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * =====================================================================================
 * TEST PARSE 4 ENDPOINT CỦA 2 MÀN MỚI — CS (`cs/jobs`, `assign`) và Kho (`cho-nhan`, `nhan`).
 *
 * Payload dưới đây chép theo HỢP ĐỒNG đã chốt với BE-01, giữ đúng các "cái bẫy" đã gặp ở nhóm
 * `driver/jobs` (đo 2026-10-28):
 *  - `windowFrom`/`windowTo` là chuỗi `HH:mm` **hoặc `"-"`** khi chưa hẹn giờ;
 *  - `expectedPackages` **VẮNG** ở nhiều việc ⇒ `Int?`, không được nổ và không được bịa "0 kiện";
 *  - `contactPhone` trả **chuỗi rỗng** (controller dùng `or ""`) ⇒ phải map về `null`;
 *  - `driverName` VẮNG khi việc chưa gán tài xế;
 *  - kho: `packages`/`weightKg`/`pickingName` có thể vắng.
 *
 * Vì sao phải là test JVM (không chỉ self-check): đây là hợp đồng với BE-01, BE đổi tên khoá là
 * app hiện "Chưa rõ …" hàng loạt mà build vẫn xanh — assert mới bắt được.
 * =====================================================================================
 */
class CsKhoPayloadTest {

    // ==================================================================================
    // 1. CS — GET /lab/api/v1/cs/jobs
    // ==================================================================================

    @Test
    fun `parse cs jobs du truong co drivers`() {
        val payload = parseCsJobsPayload(CS_JOBS_PAYLOAD)

        assertEquals(2, payload.items.size)
        assertEquals(2, payload.drivers.size)
        assertEquals(12, payload.drivers[0].id)
        assertEquals("Trần Văn Hùng", payload.drivers[0].name)

        // `login` là trường MỚI của hợp đồng: ô chọn tài xế hiện "Trần Văn Hùng · taixe01" để
        // trưởng nhóm biết máy tài xế đang đăng nhập bằng tài khoản nào.
        assertEquals("taixe01", payload.drivers[0].login)
        assertEquals("Trần Văn Hùng · taixe01", payload.drivers[0].displayLabel())
        assertEquals("Tài khoản: taixe01", payload.drivers[0].accountLine())
        // Server chưa trả `login` ⇒ chỉ tên, KHÔNG có dấu "·" treo và không hiện dòng tài khoản.
        assertNull(payload.drivers[1].login)
        assertEquals("Nguyễn Thị Mai", payload.drivers[1].displayLabel())
        assertNull(payload.drivers[1].accountLine())

        val chuaGan = payload.items[0]
        assertEquals(501, chuaGan.id)
        assertEquals("LH/26/0101", chuaGan.ref)
        assertEquals("Sen Đá Shop", chuaGan.shop)
        assertEquals("0903 221 447", chuaGan.contactPhone)
        assertEquals(JobState.NEW, chuaGan.state)
        assertEquals("Mới", chuaGan.stateLabel)
        assertEquals(4, chuaGan.expectedPackages)
        assertNull(chuaGan.driverName, "việc chưa gán thì driverName phải là null")

        val daGan = payload.items[1]
        assertEquals(JobState.ASSIGNED, daGan.state)
        assertEquals("Trần Văn Hùng", daGan.driverName)
        assertNull(daGan.expectedPackages, "expectedPackages vắng ⇒ null, không được nổ")
    }

    @Test
    fun `cs jobs thieu truong tuy chon khong vo`() {
        // Payload tối giản: chỉ có id — mọi trường khác vắng hoàn toàn.
        val payload = parseCsJobsPayload("""{"ok":true,"data":{"items":[{"id":9}]}}""")

        val job = payload.items.single()
        assertEquals(9, job.id)
        assertEquals("#9", job.ref, "thiếu mã việc thì hiện #id, không hiện chuỗi rỗng")
        assertTrue(job.shop.isNotBlank(), "thiếu tên shop vẫn phải có câu tiếng Việt")
        assertTrue(job.address.isNotBlank(), "thiếu địa chỉ vẫn phải có câu tiếng Việt")
        assertNull(job.contactPhone)
        assertNull(job.expectedPackages)
        assertNull(job.driverName)
        assertEquals(JobState.NEW, job.state, "state vắng ⇒ new để UI không vỡ")
        assertTrue(payload.drivers.isEmpty(), "không có drivers thì trả danh sách rỗng, không null")
    }

    @Test
    fun `cs jobs chuoi rong duoc coi la null`() {
        val payload = parseCsJobsPayload(
            """{"ok":true,"data":{"items":[{"id":7,"shop":"A","address":"B",
               "contactPhone":"","windowFrom":"-","windowTo":"-","state":"new","stateLabel":"",
               "driverName":""}],"drivers":[]}}""",
        )
        val job = payload.items.single()
        assertNull(job.contactPhone, "controller trả chuỗi rỗng ⇒ UI phải hiện \"Chưa có số điện thoại\"")
        assertNull(job.stateLabel, "nhãn rỗng ⇒ để app tự dịch từ mã state")
        assertNull(job.driverName, "tên tài xế rỗng ⇒ coi như chưa gán")
        assertEquals("Chưa hẹn giờ", formatWindow(job.windowFrom, job.windowTo))
    }

    @Test
    fun `cs drivers co id khong hop le thi bo`() {
        val payload = parseCsJobsPayload(
            """{"ok":true,"data":{"items":[],"drivers":[{"id":0,"name":"Không id"},
               {"id":-3,"name":"Âm"},{"id":5,"name":"Hợp lệ"}]}}""",
        )
        assertEquals(1, payload.drivers.size, "chỉ giữ tài xế có id dương")
        assertEquals("Hợp lệ", payload.drivers.single().name)
        // Tài xế không có `login` trong payload ⇒ null, KHÔNG phải chuỗi rỗng.
        assertNull(payload.drivers.single().login)
    }

    @Test
    fun `cs drivers login rong duoc coi nhu khong co tai khoan`() {
        val payload = parseCsJobsPayload(
            """{"ok":true,"data":{"items":[],"drivers":[
               {"id":7,"name":"Lê Quốc Bảo","login":""},
               {"id":8,"name":"Trần Văn Hùng","login":"   "},
               {"id":9,"name":"Nguyễn Thị Mai","login":"taixe02"}]}}""",
        )
        assertEquals(3, payload.drivers.size)

        // Chuỗi rỗng/toàn khoảng trắng ⇒ null (controller Odoo trả `or ""`).
        listOf(payload.drivers[0], payload.drivers[1]).forEach { driver ->
            assertNull(driver.login, "login rỗng ⇒ null")
            assertNull(driver.accountLine(), "không hiện dòng \"Tài khoản: \"")
            assertEquals(driver.name, driver.displayLabel(), "không có dấu \"·\" treo khi thiếu login")
        }
        assertEquals("Nguyễn Thị Mai · taixe02", payload.drivers[2].displayLabel())
    }

    @Test
    fun `cs jobs data khong phai object thi bao loi tieng Viet`() {
        val error = assertFailsWith<ApiException.Server> {
            parseCsJobsPayload("""{"ok":true,"data":[]}""")
        }
        assertTrue((error.message ?: "").contains("CS"), "câu lỗi phải nói rõ là danh sách của CS")
    }

    // ==================================================================================
    // 2. CS — POST /lab/api/v1/cs/jobs/<id>/assign
    // ==================================================================================

    @Test
    fun `body assign dung khoa driverId`() {
        val body = assignRequestBody(driverId = 12)
        assertEquals("""{"driverId":12}""", body, "hợp đồng chỉ có MỘT khoá driverId")
    }

    @Test
    fun `parse ket qua assign co du ba truong`() {
        val outcome = parseAssignResult(
            """{"ok":true,"data":{"state":"assigned","stateLabel":"Đã gán tài xế","driverName":"Trần Văn Hùng"}}""",
        )
        assertEquals(JobState.ASSIGNED, outcome.state)
        assertEquals("Đã gán tài xế", outcome.stateLabel)
        assertEquals("Trần Văn Hùng", outcome.driverName)
    }

    @Test
    fun `parse ket qua assign thieu truong thi tra null de UI fallback`() {
        val outcome = parseAssignResult("""{"ok":true,"data":{}}""")
        assertNull(outcome.state)
        assertNull(outcome.stateLabel)
        assertNull(outcome.driverName)
    }

    // ==================================================================================
    // 3. Kho — GET /lab/api/v1/kho/cho-nhan
    // ==================================================================================

    @Test
    fun `parse kho cho nhan du truong`() {
        val jobs = parseKhoChoNhanPayload(KHO_PAYLOAD)

        assertEquals(2, jobs.size)
        val day = jobs[0]
        assertEquals(601, day.id)
        assertEquals("LH/26/0201", day.ref)
        assertEquals("Sen Đá Shop", day.shop)
        assertEquals(4, day.packages)
        assertEquals(12.5, day.weightKg)
        assertEquals("Trần Văn Hùng", day.driverName)
        assertNull(day.pickingName, "chưa nhận thì chưa có số phiếu nhập kho")

        assertEquals("12,5 kg", formatWeightKg(day.weightKg))
        assertEquals("4 kiện", formatPackages(day.packages))
    }

    @Test
    fun `kho thieu packages va weight thi tra null khong bia so 0`() {
        val jobs = parseKhoChoNhanPayload(KHO_PAYLOAD)
        val thieu = jobs[1]

        assertNull(thieu.packages)
        assertNull(thieu.weightKg)
        assertNull(thieu.driverName)
        assertEquals("Chưa rõ số kiện", formatPackages(thieu.packages))
        assertEquals("Chưa rõ khối lượng", formatWeightKg(thieu.weightKg))
    }

    @Test
    fun `kho item hoan toan rong van parse duoc`() {
        val jobs = parseKhoChoNhanPayload("""{"ok":true,"data":{"items":[{"id":3}]}}""")
        val job = jobs.single()
        assertEquals("#3", job.ref)
        assertTrue(job.shop.isNotBlank())
        assertNull(job.packages)
        assertNull(job.weightKg)
        assertNull(job.pickingName)
    }

    @Test
    fun `kho cho nhan data sai dang bao loi tieng Viet`() {
        assertFailsWith<ApiException.Server> {
            parseKhoChoNhanPayload("""{"ok":true,"data":"hỏng"}""")
        }
    }

    // ==================================================================================
    // 4. Kho — POST /lab/api/v1/kho/jobs/<id>/nhan
    // ==================================================================================

    @Test
    fun `parse ket qua nhan kho co so phieu nhap kho`() {
        val outcome = parseKhoReceiveResult(
            """{"ok":true,"data":{"state":"confirmed","stateLabel":"Đã về kho","pickingName":"WH/IN/00042","saleOrderName":"S01500"}}""",
        )
        assertEquals(JobState.CONFIRMED, outcome.state)
        assertEquals("Đã về kho", outcome.stateLabel)
        assertEquals("WH/IN/00042", outcome.pickingName)
        assertEquals("S01500", outcome.saleOrderName)
    }

    @Test
    fun `parse ket qua nhan kho thieu so phieu thi null`() {
        val outcome = parseKhoReceiveResult("""{"ok":true,"data":{"state":"confirmed","pickingName":""}}""")
        assertEquals(JobState.CONFIRMED, outcome.state)
        assertNull(outcome.pickingName, "chuỗi rỗng ⇒ null để UI nói rõ máy chủ chưa trả số phiếu")
    }

    // ==================================================================================
    // 5. Lỗi nghiệp vụ — LUÔN là câu tiếng Việt của BE (Luật 6)
    // ==================================================================================

    @Test
    fun `loi nghiep vu giu nguyen cau tieng Viet cua BE`() {
        val error = assertFailsWith<ApiException.Server> {
            parseAssignResult(
                """{"ok":false,"error":{"code":"INVALID_STATE","message":"Việc đã huỷ nên không gán tài xế được."}}""",
                status = 409,
            )
        }
        assertTrue((error.message ?: "").contains("đã huỷ"), "phải giữ câu của BE: ${error.message}")
    }

    @Test
    fun `401 la phien het han voi ca 4 endpoint`() {
        val unauthorized =
            """{"ok":false,"data":null,"error":{"code":"TOKEN_REQUIRED","message":"Thiếu mã truy cập."}}"""
        assertFailsWith<ApiException.SessionExpired> { parseCsJobsPayload(unauthorized, status = 401) }
        assertFailsWith<ApiException.SessionExpired> { parseAssignResult(unauthorized, status = 401) }
        assertFailsWith<ApiException.SessionExpired> { parseKhoChoNhanPayload(unauthorized, status = 401) }
        assertFailsWith<ApiException.SessionExpired> { parseKhoReceiveResult(unauthorized, status = 401) }
    }

    private companion object {
        /**
         * Payload CS theo hợp đồng: 1 việc chưa gán (đủ trường) + 1 việc đã gán (thiếu kiện).
         *
         * Tài xế #12 có `login` (hợp đồng MỚI với BE-01), tài xế #13 KHÔNG có — giữ cả hai để
         * test chứng minh app chịu được payload cũ (server chưa trả `login`).
         */
        const val CS_JOBS_PAYLOAD = """
{"ok":true,"data":{"items":[
{"id":501,"ref":"LH/26/0101","shop":"Sen Đá Shop","address":"45 Nguyễn Trãi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh","contactPhone":"0903 221 447","windowFrom":"08:30","windowTo":"10:00","state":"new","stateLabel":"Mới","expectedPackages":4},
{"id":502,"ref":"LH/26/0102","shop":"Shop Hoa Sài Gòn","address":"12 Lý Tự Trọng, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh","contactPhone":"0918 447 991","windowFrom":"10:30","windowTo":"12:00","state":"assigned","stateLabel":"Đã gán tài xế","driverName":"Trần Văn Hùng"}
],"drivers":[{"id":12,"name":"Trần Văn Hùng","login":"taixe01"},{"id":13,"name":"Nguyễn Thị Mai"}]}}
"""

        /** Payload kho theo hợp đồng: 1 việc đủ số liệu + 1 việc thiếu số kiện/kg/tài xế. */
        const val KHO_PAYLOAD = """
{"ok":true,"data":{"items":[
{"id":601,"ref":"LH/26/0201","shop":"Sen Đá Shop","packages":4,"weightKg":12.5,"driverName":"Trần Văn Hùng"},
{"id":602,"ref":"LH/26/0202","shop":"Shop Hoa Sài Gòn"}
]}}
"""
    }
}
