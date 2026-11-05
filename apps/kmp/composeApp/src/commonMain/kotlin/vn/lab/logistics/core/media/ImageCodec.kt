package vn.lab.logistics.core.media

import androidx.compose.ui.graphics.ImageBitmap

/**
 * =====================================================================================
 * ẢNH CỦA TÀI XẾ — phần DÙNG CHUNG (commonMain), phần nền tảng nằm ở `desktopMain`.
 *
 * LUẬT (AGENTS.md Luật 10 + agents/fe/AGENT.md §3.2): `javax.imageio`, `java.awt`,
 * `BufferedImage` **chỉ** được xuất hiện trong `desktopMain`. Vì vậy mọi thứ chạm JPEG/PNG
 * đều khai báo `expect` ở đây và cài `actual` ở `core/media/ImageCodec.desktop.kt`.
 *
 * RÀNG BUỘC LẤY TỪ BACKEND (đọc `addons/lab_api/tools.py::giai_ma_anh`, không đoán):
 *   - mỗi ảnh/chữ ký ≤ **5 MB** (`ANH_TOI_DA_BYTES = 5 * 1024 * 1024`);
 *   - backend kiểm **magic bytes**: JPEG `FF D8 FF`, PNG `89 50 4E 47 0D 0A 1A 0A`.
 *     Gửi sai định dạng ⇒ `400 VALIDATION "Ảnh hàng hoá: chỉ chấp nhận ảnh JPEG hoặc PNG."`
 *   - ⇒ ảnh nén ra **bắt buộc** vẫn là JPEG thật, không chỉ "đổi đuôi".
 *
 * VÌ SAO PHẢI NÉN: ảnh chụp điện thoại 12MP ≈ 4–8 MB. Gửi nguyên là 400 (quá 5MB) hoặc
 * 15s timeout ngoài hiện trường. Nén về ≤1920px cạnh dài + JPEG q≈80 ⇒ ~0,4–1,5 MB.
 * =====================================================================================
 */

/** Giới hạn CỨNG của backend cho mỗi ảnh/chữ ký (`tools.ANH_TOI_DA_BYTES`). */
const val MAX_IMAGE_BYTES: Int = 5 * 1024 * 1024

/** Cạnh dài tối đa sau khi nén — đủ nét cho biên nhận, vẫn nhẹ để gửi 3G. */
const val DEFAULT_MAX_SIDE: Int = 1920

/** Chất lượng JPEG mặc định (thang 0–100 của ImageIO). */
const val DEFAULT_JPEG_QUALITY: Int = 80

/** Loại ảnh app chấp nhận — suy từ magic bytes, KHÔNG tin đuôi tệp. */
enum class ImageKind(val mime: String, val extension: String) {
    Jpeg("image/jpeg", "jpg"),
    Png("image/png", "png"),
}

/**
 * Nhận loại ảnh bằng **magic bytes** (hàm THUẦN, test được ở `commonTest`).
 *
 * Đây là bản duy nhất của luật "không tin đuôi tệp": người dùng đổi tên `bao-cao.pdf`
 * thành `anh.jpg` thì hàm này trả `null` và UI báo lỗi ngay tại máy, không đợi server 400.
 */
fun sniffImageKind(bytes: ByteArray): ImageKind? {
    if (bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() &&
        bytes[1] == 0xD8.toByte() &&
        bytes[2] == 0xFF.toByte()
    ) {
        return ImageKind.Jpeg
    }
    if (bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() &&
        bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() &&
        bytes[3] == 0x47.toByte() &&
        bytes[4] == 0x0D.toByte() &&
        bytes[5] == 0x0A.toByte() &&
        bytes[6] == 0x1A.toByte() &&
        bytes[7] == 0x0A.toByte()
    ) {
        return ImageKind.Png
    }
    return null
}

/**
 * Nén ảnh về **JPEG ≤ 5 MB**, cạnh dài ≤ [maxSide].
 *
 * `actual` (desktopMain) dùng `ImageIO`: giảm `quality` trước, khi quality chạm sàn thì
 * thu nhỏ thêm 20% rồi lặp — tất cả trong vòng lặp hữu hạn, cuối cùng vẫn quá lớn thì ném
 * [IllegalStateException] tiếng Việt để UI báo "chụp lại ở chế độ thường".
 *
 * @param bytes ảnh gốc (JPEG hoặc PNG, đã qua [isSupportedImage]).
 * @throws IllegalArgumentException khi tệp không phải ảnh JPEG/PNG đọc được.
 */
expect fun compressJpeg(
    bytes: ByteArray,
    maxSide: Int = DEFAULT_MAX_SIDE,
    quality: Int = DEFAULT_JPEG_QUALITY,
): ByteArray

/**
 * Tệp có phải ảnh JPEG/PNG **thật** (magic bytes + giải mã được) hay không.
 * `actual` desktop kiểm thêm `ImageIO.read` để chặn tệp hỏng/đổi đuôi.
 */
expect fun isSupportedImage(bytes: ByteArray): Boolean

/**
 * Giải mã ảnh để **xem trước trong RAM** (không ghi ra đĩa — yêu cầu cứng của C1–C3).
 *
 * Trả `null` khi không giải mã được (UI hiện khung "không xem trước được" chứ không sập).
 * Kiểu trả về là `ImageBitmap` của Compose ⇒ `commonMain` không cần biết skiko hay awt.
 */
expect fun decodePreviewImage(bytes: ByteArray): ImageBitmap?
