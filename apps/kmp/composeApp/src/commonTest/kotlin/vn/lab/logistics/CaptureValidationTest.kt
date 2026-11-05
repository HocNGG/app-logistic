package vn.lab.logistics

import vn.lab.logistics.core.media.Base64Codec
import vn.lab.logistics.core.media.ImageKind
import vn.lab.logistics.core.media.sniffImageKind
import vn.lab.logistics.core.repo.isFailReasonValid
import vn.lab.logistics.feature.capture.CaptureDraft
import vn.lab.logistics.feature.capture.CaptureValidation
import vn.lab.logistics.feature.capture.formatKg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * =====================================================================================
 * C6 — TEST LOGIC THUẦN của màn CAPTURE (chạy được offline, không cần Odoo, không cần GUI).
 *
 * Bất biến an toàn được kiểm ở đây là thứ KHÔNG được phép hồi quy:
 *  (1) không gửi khi thiếu ảnh bắt buộc / chữ ký / số kiện / kg;
 *  (2) số kiện là số nguyên > 0, kg > 0, chấp nhận dấu phẩy thập phân kiểu Việt Nam;
 *  (3) `sniffImageKind` chỉ nhận JPEG/PNG theo magic bytes (backend `tools.giai_ma_anh`
 *      cũng kiểm đúng 2 chữ ký byte này — nếu app lệch là 400 ngay);
 *  (4) `Base64Codec` xuất bảng chuẩn + padding (backend dùng `b64decode(validate=True)`);
 *  (5) lý do "không lấy được" phải ≥ 5 ký tự.
 *
 * Đây là bản assert song song với khối 9 của `SelfCheck` (selfCheck in bảng, test này fail build).
 * =====================================================================================
 */
class CaptureValidationTest {

    // ------------------------------------------------------------------ (1) chặn khi thiếu

    @Test
    fun `draft rong bi chan va co du 4 ly do`() {
        val blockers = CaptureValidation.blockers(CaptureDraft())

        assertTrue(blockers.size >= 4, "phải nêu đủ 4 lý do, nhận ${blockers.size}")
        assertFalse(CaptureValidation.canSubmit(CaptureDraft()), "rỗng thì không được gửi")
        assertTrue(
            blockers.any { it.contains("Hàng hoá") },
            "lý do phải nêu tên ô ảnh bắt buộc: $blockers",
        )
        assertTrue(blockers.any { it.contains("chữ ký") }, "lý do phải nêu chữ ký: $blockers")
    }

    @Test
    fun `thieu tung o bat buoc deu bi chan`() {
        val du = draftDayDu()
        assertTrue(CaptureValidation.canSubmit(du), "draft đầy đủ phải gửi được")

        listOf(
            "thiếu ảnh Hàng hoá" to du.copy(hasHangHoa = false),
            "thiếu ảnh Kiện hàng" to du.copy(hasKienHang = false),
            "chưa ký" to du.copy(hasSignature = false),
            "số kiện 0" to du.copy(packagesText = "0"),
            "số kiện rỗng" to du.copy(packagesText = ""),
            "kg 0" to du.copy(weightText = "0"),
            "kg chữ" to du.copy(weightText = "mười hai"),
        ).forEach { (ten, draft) ->
            assertFalse(CaptureValidation.canSubmit(draft), "$ten ⇒ phải bị chặn")
            assertTrue(
                CaptureValidation.blockers(draft).isNotEmpty(),
                "$ten ⇒ phải có ít nhất 1 lý do",
            )
        }
    }

    @Test
    fun `anh bien nhan giay la tuy chon`() {
        val khongCoBienNhan = draftDayDu().copy(hasBienNhan = false)
        assertTrue(
            CaptureValidation.canSubmit(khongCoBienNhan),
            "biên nhận giấy là tuỳ chọn — không được chặn gửi",
        )
    }

    // ------------------------------------------------------------------ (2) parse số

    @Test
    fun `so kien phai la so nguyen duong`() {
        assertEquals(4, CaptureValidation.packages("4"))
        assertEquals(12, CaptureValidation.packages(" 12 "))
        assertNull(CaptureValidation.packages("0"), "0 kiện là chưa lấy được gì")
        assertNull(CaptureValidation.packages("-2"))
        assertNull(CaptureValidation.packages(""))
        assertNull(CaptureValidation.packages("4 kiện"))
        assertNull(CaptureValidation.packages("4.5"), "số kiện không được là số thực")
    }

    @Test
    fun `khoi luong chap nhan dau phay thap phan kieu Viet Nam`() {
        assertEquals(12.5, CaptureValidation.weightKg("12,5"))
        assertEquals(12.5, CaptureValidation.weightKg("12.5"))
        assertEquals(1.0, CaptureValidation.weightKg("1"))
        assertNull(CaptureValidation.weightKg("0"))
        assertNull(CaptureValidation.weightKg(""))
        assertNull(CaptureValidation.weightKg("abc"))
    }

    @Test
    fun `numbers tra ve so lieu da chuan hoa`() {
        val soLieu = assertNotNull(CaptureValidation.numbers(draftDayDu()))
        assertEquals(4, soLieu.packages)
        assertEquals(12.5, soLieu.weightKg)

        assertNull(CaptureValidation.numbers(draftDayDu().copy(weightText = "0")))
    }

    @Test
    fun `formatKg hien theo cach nguoi Viet doc`() {
        assertEquals("12,5", formatKg(12.5))
        assertEquals("12", formatKg(12.0))
        assertEquals("0,5", formatKg(0.5))
        assertEquals("13", formatKg(13.04))
        assertEquals("4,8", formatKg(4.76))
    }

    // ------------------------------------------------------------------ (3) magic bytes

    @Test
    fun `sniffImageKind nhan JPEG va PNG that`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00)
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00,
        )

        assertEquals(ImageKind.Jpeg, sniffImageKind(jpeg))
        assertEquals(ImageKind.Png, sniffImageKind(png))
        assertEquals("image/jpeg", ImageKind.Jpeg.mime)
        assertEquals("image/png", ImageKind.Png.mime)
    }

    @Test
    fun `sniffImageKind tu choi tep khong phai anh`() {
        assertNull(sniffImageKind(ByteArray(0)), "mảng rỗng")
        // "%PDF" — tệp PDF đổi tên thành .jpg: magic bytes không phải JPEG/PNG ⇒ phải chặn.
        assertNull(sniffImageKind(byteArrayOf(0x25, 0x50, 0x44, 0x46)), "PDF đổi đuôi .jpg")
        assertNull(sniffImageKind(ByteArray(64) { 7 }), "dữ liệu rác")
        assertNull(sniffImageKind(byteArrayOf(0xFF.toByte(), 0xD8.toByte())), "JPEG cụt 2 byte")
    }

    // ------------------------------------------------------------------ (4) base64

    @Test
    fun `Base64Codec dung bang chuan va padding`() {
        assertEquals("", Base64Codec.encode(ByteArray(0)))
        assertEquals("TWFu", Base64Codec.encode("Man".encodeToByteArray()))
        assertEquals("TWE=", Base64Codec.encode("Ma".encodeToByteArray()))
        assertEquals("TQ==", Base64Codec.encode("M".encodeToByteArray()))
        assertEquals("TWFueQ==", Base64Codec.encode("Many".encodeToByteArray()))
        // 3 byte nhị phân bất kỳ = "AAEC" ở bảng chuẩn (kiểm byte 0 không bị cắt).
        assertEquals("AAEC", Base64Codec.encode(byteArrayOf(0x00, 0x01, 0x02)))
    }

    @Test
    fun `Base64Codec xu ly dung byte am cua JPEG`() {
        // 3 magic bytes của JPEG — đây là đầu payload thật gửi lên server.
        val jpegMagic = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        assertEquals("/9j/", Base64Codec.encode(jpegMagic))
    }

    // ------------------------------------------------------------------ (5) lý do fail

    @Test
    fun `ly do khong lay duoc phai dai it nhat 5 ky tu`() {
        assertFalse(isFailReasonValid(""), "rỗng")
        assertFalse(isFailReasonValid("   "), "toàn khoảng trắng")
        assertFalse(isFailReasonValid("abc"), "3 ký tự")
        assertFalse(isFailReasonValid("  a  "), "sau khi trim chỉ còn 1")
        assertTrue(isFailReasonValid("abcde"))
        assertTrue(isFailReasonValid("Shop đóng cửa"))
    }

    // ------------------------------------------------------------------ helper

    private fun draftDayDu(): CaptureDraft = CaptureDraft(
        hasHangHoa = true,
        hasKienHang = true,
        hasSignature = true,
        packagesText = "4",
        weightText = "12,5",
    )
}
