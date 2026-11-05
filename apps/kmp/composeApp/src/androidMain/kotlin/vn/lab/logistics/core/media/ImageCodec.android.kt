package vn.lab.logistics.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

/**
 * =====================================================================================
 * `actual` cho ANDROID của bộ mã hoá ảnh — dùng `android.graphics.Bitmap` / `BitmapFactory`.
 *
 * VÌ SAO KHÔNG DÙNG `javax.imageio` NHƯ DESKTOP: `ImageIO`/`BufferedImage` là API của JVM
 * desktop, KHÔNG có trong Android. Ở đây phải là API nền tảng tương ứng, và ba điều backend
 * bắt buộc vẫn y nguyên (đọc `addons/lab_api/tools.py`, không đoán):
 *
 *  1. **Ảnh/chữ ký ≤ 5 MB** (`ANH_TOI_DA_BYTES`) ⇒ phải nén. Vòng lặp nén dưới đây giống hệt
 *     bản desktop (giảm quality 5 đơn vị mỗi vòng; quality chạm sàn 35 thì thu ảnh còn 80%
 *     kích thước rồi đặt lại quality 75; tối đa 14 vòng) để hai nền tảng cho kết quả tương đương.
 *  2. **Magic bytes phải là JPEG `FF D8 FF`** ⇒ `Bitmap.compress(JPEG)` luôn xuất JPEG thật,
 *     kể cả khi nguồn là PNG. Không bao giờ chỉ "đổi đuôi tệp".
 *  3. JPEG không có kênh alpha ⇒ ảnh được dàn phẳng lên nền TRẮNG (giống desktop). Nếu bỏ
 *     bước này, vùng trong suốt của PNG sẽ thành ĐEN trong biên nhận.
 *
 * GIỚI HẠN BỘ NHỚ: ảnh 12MP giải mã trọn là ~48 MB RAM ⇒ luôn giải mã có `inSampleSize` (thu
 * nhỏ ngay trong lúc đọc) rồi mới scale chính xác, tránh OOM trên máy ảo cấu hình thấp.
 * =====================================================================================
 */

actual fun compressJpeg(bytes: ByteArray, maxSide: Int, quality: Int): ByteArray {
    if (bytes.isEmpty()) {
        throw IllegalArgumentException("Ảnh rỗng — vui lòng chọn ảnh khác.")
    }
    if (sniffImageKind(bytes) == null) {
        throw IllegalArgumentException(
            "Tệp không phải ảnh JPEG hoặc PNG hợp lệ. Vui lòng chọn ảnh chụp từ điện thoại/máy ảnh.",
        )
    }
    var current = decodeFlattenedOnWhite(bytes, maxSide)
        ?: throw IllegalArgumentException(
            "Không đọc được ảnh này. Vui lòng chọn tệp JPEG hoặc PNG khác.",
        )

    var currentQuality = quality.coerceIn(MIN_QUALITY_INPUT, MAX_QUALITY_INPUT)

    repeat(MAX_ATTEMPTS) {
        val encoded = encodeJpeg(current, currentQuality)
        if (encoded.size <= MAX_IMAGE_BYTES) return encoded

        if (currentQuality > MIN_QUALITY) {
            currentQuality = (currentQuality - QUALITY_STEP).coerceAtLeast(MIN_QUALITY)
        } else {
            val longest = maxOf(current.width, current.height)
            val shrunk = (longest * SHRINK_RATIO).toInt().coerceAtLeast(MIN_SIDE_PX)
            if (shrunk >= longest) {
                throw IllegalStateException(
                    "Không nén được ảnh xuống ≤ 5 MB ở kích thước tối thiểu. " +
                        "Vui lòng chụp lại ở chế độ thường rồi thử lại.",
                )
            }
            current = scaleToLongestSide(current, shrunk)
            currentQuality = 75
        }
    }
    throw IllegalStateException(
        "Không nén được ảnh xuống ≤ 5 MB sau $MAX_ATTEMPTS lần thử. " +
            "Vui lòng chụp lại ở chế độ thường rồi thử lại.",
    )
}

actual fun isSupportedImage(bytes: ByteArray): Boolean {
    if (sniffImageKind(bytes) == null) return false
    // Magic bytes đúng chưa đủ: tệp có thể bị cắt cụt. Giải mã thật một bản thu nhỏ
    // (`inSampleSize`) — đủ để phát hiện tệp hỏng mà không tốn RAM bằng ảnh gốc.
    return runCatching {
        decodeSampled(bytes, PREVIEW_MAX_SIDE) != null
    }.getOrDefault(false)
}

actual fun decodePreviewImage(bytes: ByteArray): ImageBitmap? = runCatching {
    // Chỉ dựng bitmap trong RAM để xem trước — KHÔNG ghi tệp nào xuống đĩa (C1–C3).
    decodeSampled(bytes, PREVIEW_MAX_SIDE)?.asImageBitmap()
}.getOrNull()

// ---------------------------------------------------------------------------- nội bộ

private const val MAX_ATTEMPTS = 14
/** Sàn chất lượng JPEG (thang 0–100) — dưới mức này ảnh biên nhận đã nhoè. */
private const val MIN_QUALITY = 35
private const val QUALITY_STEP = 5
private const val SHRINK_RATIO = 0.8f
private const val MIN_SIDE_PX = 480
private const val MIN_QUALITY_INPUT = 30
private const val MAX_QUALITY_INPUT = 95
/** Cạnh dài tối đa cho ảnh XEM TRƯỚC trong app (biên nhận không cần 1920px để xem). */
private const val PREVIEW_MAX_SIDE = 1024

/** Ghi JPEG với chất lượng cho trước (`Bitmap.compress` — JPEG THẬT, không phải đổi đuôi). */
private fun encodeJpeg(image: Bitmap, quality: Int): ByteArray {
    val out = ByteArrayOutputStream()
    image.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
    return out.toByteArray()
}

/** Giải mã có thu nhỏ (`inSampleSize`), rồi dàn phẳng lên nền trắng để JPEG đúng màu. */
private fun decodeFlattenedOnWhite(bytes: ByteArray, maxSide: Int): Bitmap? {
    val decoded = decodeSampled(bytes, maxSide) ?: return null
    val scaled = if (maxOf(decoded.width, decoded.height) > maxSide) {
        scaleToLongestSide(decoded, maxSide)
    } else {
        decoded
    }
    val flattened = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(flattened)
    canvas.drawColor(Color.WHITE)
    canvas.drawBitmap(scaled, 0f, 0f, null)
    return flattened
}

/** Đọc ảnh với `inSampleSize` (bội số 2) sao cho cạnh dài ≥ [maxSide] nhưng gần nhất. */
private fun decodeSampled(bytes: ByteArray, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSide)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

/**
 * `inSampleSize` là luỹ thừa của 2: chọn bội số lớn nhất mà ảnh sau khi chia vẫn còn
 * ≥ [maxSide] ở cạnh dài (giữ đủ nét cho biên nhận, giảm mạnh RAM).
 */
private fun sampleSizeFor(width: Int, height: Int, maxSide: Int): Int {
    if (maxSide <= 0) return 1
    var sample = 1
    var longest = maxOf(width, height)
    while (longest / 2 >= maxSide) {
        longest /= 2
        sample *= 2
    }
    return sample
}

/** Thu ảnh về cạnh dài = [longestSide] px (nội suy mặc định của Android đủ tốt cho biên nhận). */
private fun scaleToLongestSide(source: Bitmap, longestSide: Int): Bitmap {
    val ratio = longestSide.toDouble() / maxOf(source.width, source.height).toDouble()
    val width = (source.width * ratio).toInt().coerceAtLeast(1)
    val height = (source.height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, width, height, true)
}
