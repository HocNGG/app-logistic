package vn.lab.logistics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import vn.lab.logistics.core.net.ApiException
import vn.lab.logistics.core.net.CaptureRequest
import vn.lab.logistics.core.net.captureRequestBody
import vn.lab.logistics.core.net.parseDriverJobsPayload
import vn.lab.logistics.core.net.parseLoginPayload
import vn.lab.logistics.core.net.parseSubmitResult
import vn.lab.logistics.feature.driver.packagesLabel
import vn.lab.logistics.feature.driver.statusLabel
import vn.lab.logistics.feature.driver.windowLabel

/**
 * =====================================================================================
 * TEST PARSE PAYLOAD THẬT của nhóm endpoint `/lab/api/v1` — Wave B (Luật 7: không có code không test).
 *
 * Mọi payload dưới đây CHÉP từ lần gọi thật (2026-10-28, Odoo 17, DB `odoo17_lab`), giữ cả
 * các "bẫy" đã đo:
 *  - `windowFrom`/`windowTo` = `"01:30"` hoặc `"-"` (chuỗi, không phải Datetime);
 *  - `expectedPackages` **vắng** ở phần lớn job (OQ-22) ⇒ `Int?`, không được nổ;
 *  - `contactName`/`note`/`address` = `""` khi không có dữ liệu (controller `or ""`);
 *  - `driver_id` = `false` trong payload login (portal không đọc được `hr.employee`, ADR-004);
 *  - 401 + `error.code = TOKEN_*` VÀ "200 + trang HTML login Odoo" đều là **phiên hết hạn**.
 * =====================================================================================
 */
class LabApiParsingTest {

    // ------------------------------------------------------------------ 1. GET driver/jobs

    @Test
    fun `parse payload that co 2 viec`() {
        val jobs = parseDriverJobsPayload(JOBS_PAYLOAD)

        assertEquals(2, jobs.size)
        val coGio = jobs[0]
        val chuaHen = jobs[1]

        assertEquals("LH/26/0005", coGio.ref)
        assertEquals("Sen Đá Shop", coGio.shop)
        assertEquals("01:30", coGio.windowFrom)
        assertEquals("03:00", coGio.windowTo)
        assertEquals("assigned", coGio.state)
        assertTrue(coGio.windowLabel().contains("01:30") && coGio.windowLabel().contains("03:00"))

        // Job #2 KHÔNG có `expectedPackages` ⇒ null, KHÔNG được nổ và KHÔNG được bịa "0 kiện".
        assertNull(coGio.expectedPackages)
        assertEquals("Chưa rõ số kiện", coGio.packagesLabel())

        // `windowFrom`/`windowTo` = "-" ⇒ "Chưa hẹn giờ" (không hiện dấu gạch trần).
        assertEquals("Chưa hẹn giờ", chuaHen.windowLabel())

        // `stateLabel` của server được ưu tiên.
        assertEquals("Đang đến lấy", chuaHen.statusLabel())

        // Chuỗi rỗng ⇒ null (UI hiện "Chưa có …" thay vì khoảng trắng).
        assertNull(chuaHen.contactName)
        assertNull(chuaHen.note)
    }

    @Test
    fun `expectedPackages co gia tri thi hien so kien`() {
        val jobs = parseDriverJobsPayload(
            """{"ok":true,"data":[{"id":5,"ref":"LH/26/0009","shop":"Shop A","state":"assigned","expectedPackages":3}]}""",
        )
        assertEquals(3, jobs.single().expectedPackages)
        assertEquals("3 kiện", jobs.single().packagesLabel())
    }

    // ------------------------------------------------------------------ 2. Lỗi phiên

    @Test
    fun `401 TOKEN_REQUIRED la phien het han`() {
        val error = assertFailsWith<ApiException.SessionExpired> {
            parseDriverJobsPayload(
                """{"ok":false,"data":null,"error":{"code":"TOKEN_REQUIRED","message":"Thiếu mã truy cập X-Lab-Token. Vui lòng đăng nhập lại."}}""",
                status = 401,
            )
        }
        assertTrue((error.message ?: "").isNotBlank(), "phải có câu tiếng Việt cho người dùng")
    }

    @Test
    fun `200 nhung code TOKEN_EXPIRED van la phien het han`() {
        assertFailsWith<ApiException.SessionExpired> {
            parseDriverJobsPayload(
                """{"ok":false,"error":{"code":"TOKEN_EXPIRED","message":"Mã truy cập đã hết hạn (8 giờ)."}}""",
            )
        }
    }

    @Test
    fun `200 kem trang HTML login Odoo la phien het han`() {
        assertFailsWith<ApiException.SessionExpired> {
            parseDriverJobsPayload(
                "<!DOCTYPE html><html><head><title>Odoo</title></head><body>Login</body></html>",
            )
        }
    }

    @Test
    fun `loi nghiep vu giu nguyen cau cua BE`() {
        val error = assertFailsWith<ApiException.Server> {
            parseSubmitResult(
                """{"ok":false,"error":{"code":"INVALID_STATE","message":"Không thể gửi ảnh khi việc đang ở trạng thái \"Đã huỷ\"."}}""",
                status = 409,
            )
        }
        assertTrue((error.message ?: "").contains("Đã huỷ"), "giữ câu tiếng Việt của BE")
    }

    // ------------------------------------------------------------------ 3. POST auth/login

    @Test
    fun `login payload co driver_id false khong lam vo parse`() {
        val session = parseLoginPayload(LOGIN_PAYLOAD)

        assertEquals(12, session.uid)
        assertEquals("Trần Văn Hùng (tài xế)", session.driverName)
        // driver_id luôn false ⇒ app dùng driver_user_id (ADR-004).
        assertEquals(12, session.driverUserId)
        assertEquals(2, session.jobsToday)
        assertEquals("2026-10-28 14:27:56", session.expiresAt)
        assertTrue(session.token.isNotBlank())
    }

    @Test
    fun `login khong co token thi coi la phien het han`() {
        assertFailsWith<ApiException.SessionExpired> {
            parseLoginPayload("""{"ok":true,"data":{"uid":12,"name":"A"}}""")
        }
    }

    // ------------------------------------------------------------------ 4. accept / capture / fail

    @Test
    fun `accept tra ve state moi`() {
        val result = parseSubmitResult("""{"ok":true,"data":{"state":"en_route","stateLabel":"Đang đến lấy"}}""")
        assertEquals("en_route", result.state)
        assertEquals("Đang đến lấy", result.stateLabel)
    }

    @Test
    fun `capture gui dung 5 khoa controller doc`() {
        val body = captureRequestBody(
            CaptureRequest(
                photoBase64 = "iVBORw0KGgo=",
                signatureBase64 = "iVBORw0KGgo=",
                packages = 4,
                weightKg = 12.5,
                clientUuid = "0f8fad5b-d9cb-469f-a165-70867728950e",
            ),
        )
        listOf("photoBase64", "signatureBase64", "packages", "weightKg", "clientUuid").forEach { key ->
            assertTrue(body.contains("\"$key\""), "body capture phải có khoá $key")
        }
        assertTrue(body.contains("12.5"), "weightKg phải là số")
    }

    private companion object {
        /** Payload THẬT của `GET /lab/api/v1/driver/jobs` (2 việc). */
        const val JOBS_PAYLOAD = """
{"ok":true,"data":[
{"id":241,"ref":"LH/26/0005","shop":"Sen Đá Shop","address":"45 Nguyễn Trãi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh","contactName":"Chị Hạnh","contactPhone":"0903 221 447","windowFrom":"01:30","windowTo":"03:00","state":"assigned","stateLabel":"Đã gán tài xế","note":"Gọi trước khi tới 10 phút."},
{"id":242,"ref":"LH/26/0006","shop":"Shop Hoa Sài Gòn","address":"12 Lý Tự Trọng, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh","contactName":"","contactPhone":"","windowFrom":"-","windowTo":"-","state":"en_route","stateLabel":"Đang đến lấy","note":""}
]}
"""

        /**
         * Payload THẬT của `POST /lab/api/v1/auth/login`.
         * Token là **dữ liệu mẫu của lab** (đã hết hạn) — chỉ để chứng minh parse được
         * `driver_id: false` mà không vỡ; không phải bí mật của ai.
         */
        const val LOGIN_PAYLOAD = """
{"ok":true,"data":{"token":"jAj9J0PHsampleTokenForParsingOnly-abc123","tokenType":"LabToken","expiresAt":"2026-10-28 14:27:56","uid":12,"name":"Trần Văn Hùng","driver_id":false,"driver_user_id":12,"driver_name":"Trần Văn Hùng (tài xế)","jobsToday":2}}
"""
    }
}
