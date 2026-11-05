package vn.lab.logistics

import vn.lab.logistics.feature.capture.CaptureSession
import vn.lab.logistics.feature.capture.isValidClientUuid
import vn.lab.logistics.feature.capture.newClientUuid
import vn.lab.logistics.feature.capture.openCaptureSession
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * =====================================================================================
 * C5/C6 — TEST `clientUuid` CHỐNG TRÙNG.
 *
 * Vì sao đây là test quan trọng nhất của màn capture: backend dùng `clientUuid` làm
 * **idempotency key** (`addons/lab_api/controllers/driver.py::capture`):
 * ```python
 * if client_uuid and client_uuid == job.lab_client_uuid and job.state in ("picked_up","confirmed"):
 *     return ok({..., "duplicate": True})     # không tạo bộ ảnh thứ hai
 * ```
 * ⇒ Nếu app sinh uuid MỚI khi người dùng bấm "Thử lại", lần gửi thứ hai hoặc bị 409
 * (`state` đã `picked_up`) hoặc tạo bằng chứng trùng. Bất biến phải khoá bằng test.
 * =====================================================================================
 */
class CaptureSessionTest {

    @Test
    fun `uuid sinh ra la UUID v4 hop le`() {
        repeat(50) {
            val uuid = newClientUuid()
            assertTrue(isValidClientUuid(uuid), "uuid không hợp lệ: $uuid")
            assertEquals(36, uuid.length, "UUID phải dài 36 ký tự: $uuid")
            assertEquals('4', uuid[14], "phải là phiên bản 4: $uuid")
            assertTrue(uuid[19] in "89ab", "variant phải là 8/9/a/b: $uuid")
        }
    }

    @Test
    fun `uuid sinh ra khong trung nhau`() {
        val bo = (1..300).map { newClientUuid() }.toSet()
        assertEquals(300, bo.size, "300 lần sinh phải ra 300 uuid khác nhau")
    }

    @Test
    fun `uuid co the tai lap khi truyen Random co seed`() {
        val a = newClientUuid(Random(42))
        val b = newClientUuid(Random(42))
        assertEquals(a, b, "cùng seed phải ra cùng uuid (test tất định)")
    }

    @Test
    fun `isValidClientUuid tu choi chuoi sai`() {
        assertFalse(isValidClientUuid(""), "rỗng")
        assertFalse(isValidClientUuid("khong-phai-uuid"))
        assertFalse(
            isValidClientUuid("0f8fad5b-d9cb-169f-a165-70867728950e"),
            "version 1 không phải uuid v4",
        )
        assertFalse(
            isValidClientUuid("0f8fad5b-d9cb-469f-c165-70867728950e"),
            "variant 'c' không hợp lệ",
        )
        assertFalse(
            isValidClientUuid("0F8FAD5B-D9CB-469F-A165-70867728950E"),
            "app luôn sinh chữ thường; chữ hoa là dấu hiệu dữ liệu lạ",
        )
    }

    // ------------------------------------------------------------------ bất biến retry

    @Test
    fun `thu lai thi GIU NGUYEN clientUuid`() {
        var soLanSinh = 0
        val phien: CaptureSession = openCaptureSession(current = null, jobId = 241) {
            soLanSinh++
            "0f8fad5b-d9cb-469f-a165-70867728950e"
        }
        assertEquals(1, soLanSinh, "mở phiên mới thì sinh uuid đúng 1 lần")

        // Bấm "Thử lại" (hoặc màn hình bị dựng lại) — KHÔNG được sinh uuid mới.
        val thuLai = openCaptureSession(current = phien, jobId = 241) {
            soLanSinh++
            "11111111-1111-4111-8111-111111111111"
        }

        assertEquals(1, soLanSinh, "Thử lại KHÔNG được sinh uuid mới")
        assertEquals("0f8fad5b-d9cb-469f-a165-70867728950e", thuLai.clientUuid)
        assertTrue(phien === thuLai, "cùng việc thì trả đúng object phiên cũ")
    }

    @Test
    fun `capture moi thi sinh clientUuid moi`() {
        val phien = CaptureSession(jobId = 241, clientUuid = "0f8fad5b-d9cb-469f-a165-70867728950e")

        val viecKhac = openCaptureSession(current = phien, jobId = 242) {
            "11111111-1111-4111-8111-111111111111"
        }
        assertEquals(242, viecKhac.jobId)
        assertTrue(viecKhac.clientUuid != phien.clientUuid, "việc khác ⇒ uuid mới")

        // Mở lại màn capture cho CÙNG việc cũng là phiên mới (current = null từ ViewModel mới).
        val phienMoi = openCaptureSession(current = null, jobId = 241) {
            "22222222-2222-4222-8222-222222222222"
        }
        assertTrue(phienMoi.clientUuid != phien.clientUuid, "phiên mới ⇒ uuid mới")
    }

    @Test
    fun `openCaptureSession dung gia tri mac dinh khi khong truyen ham sinh`() {
        val phien = openCaptureSession(current = null, jobId = 7)
        assertTrue(isValidClientUuid(phien.clientUuid), "mặc định vẫn phải là UUID v4")
        assertEquals(7, phien.jobId)
    }
}
