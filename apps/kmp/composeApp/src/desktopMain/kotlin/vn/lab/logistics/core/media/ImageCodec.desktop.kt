package vn.lab.logistics.core.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream
import org.jetbrains.skia.Image as SkiaImage

/**
 * =====================================================================================
 * `actual` cho DESKTOP — nơi DUY NHẤT trong app được dùng `javax.imageio` / `java.awt`.
 *
 * Vì sao không đặt ở `commonMain`: `BufferedImage`, `ImageIO`, `Graphics2D` là API JVM,
 * không tồn tại ở nền tảng khác (Luật cứng của C2 + agents/fe/AGENT.md §3.2).
 *
 * HAI ĐIỀU BACKEND BẮT BUỘC (đọc `addons/lab_api/tools.py`, không đoán):
 *  1. ảnh ≤ 5 MB → phải nén; 2. magic bytes phải là JPEG (`FF D8 FF`) hoặc PNG.
 *     Vì vậy [compressJpeg] LUÔN xuất JPEG thật (ImageIO writer), kể cả khi nguồn là PNG.
 *
 * VÒNG LẶP NÉN (hữu hạn, không "thử mãi"): giảm `quality` 0.05 mỗi vòng; khi quality chạm
 * sàn 0.35 thì thu nhỏ thêm 20% rồi đặt lại quality 0.75. Sau [MAX_ATTEMPTS] vòng vẫn quá
 * 5 MB (gần như không xảy ra với ảnh ≤1920px) thì ném lỗi tiếng Việt để UI báo chụp lại.
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
    val source = ImageIO.read(ByteArrayInputStream(bytes))
        ?: throw IllegalArgumentException(
            "Không đọc được ảnh này. Vui lòng chọn tệp JPEG hoặc PNG khác.",
        )

    var current = if (maxOf(source.width, source.height) > maxSide) {
        scaleToLongestSide(source, maxSide)
    } else {
        toRgb(source)
    }
    var currentQuality = quality.coerceIn(MIN_QUALITY_INPUT, MAX_QUALITY_INPUT) / 100f

    repeat(MAX_ATTEMPTS) {
        val encoded = encodeJpeg(current, currentQuality)
        if (encoded.size <= MAX_IMAGE_BYTES) return encoded

        if (currentQuality > MIN_QUALITY) {
            currentQuality = (currentQuality - 0.05f).coerceAtLeast(MIN_QUALITY)
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
            currentQuality = 0.75f
        }
    }
    throw IllegalStateException(
        "Không nén được ảnh xuống ≤ 5 MB sau $MAX_ATTEMPTS lần thử. " +
            "Vui lòng chụp lại ở chế độ thường rồi thử lại.",
    )
}

actual fun isSupportedImage(bytes: ByteArray): Boolean {
    if (sniffImageKind(bytes) == null) return false
    // Magic bytes đúng chưa đủ: tệp có thể bị cắt cụt. Thử giải mã thật.
    return runCatching { ImageIO.read(ByteArrayInputStream(bytes)) != null }.getOrDefault(false)
}

actual fun decodePreviewImage(bytes: ByteArray): ImageBitmap? = runCatching {
    // skiko (đi kèm Compose Desktop) — không ghi tệp nào xuống đĩa, chỉ dựng bitmap trong RAM.
    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

// ---------------------------------------------------------------------------- nội bộ

private const val MAX_ATTEMPTS = 14
private const val MIN_QUALITY = 0.35f
private const val SHRINK_RATIO = 0.8f
private const val MIN_SIDE_PX = 480
private const val MIN_QUALITY_INPUT = 30
private const val MAX_QUALITY_INPUT = 95

/** Ghi JPEG với chất lượng cho trước (ImageIO writer — KHÔNG phải đổi đuôi tệp). */
private fun encodeJpeg(image: BufferedImage, quality: Float): ByteArray {
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val params = writer.defaultWriteParam
    params.compressionMode = ImageWriteParam.MODE_EXPLICIT
    params.compressionQuality = quality.coerceIn(0f, 1f)

    val out = ByteArrayOutputStream()
    try {
        MemoryCacheImageOutputStream(out).use { stream ->
            writer.output = stream
            writer.write(null, IIOImage(image, null, null), params)
        }
    } finally {
        writer.dispose()
    }
    return out.toByteArray()
}

/** Thu ảnh về cạnh dài = [longestSide] px, nền trắng (JPEG không có kênh alpha). */
private fun scaleToLongestSide(source: BufferedImage, longestSide: Int): BufferedImage {
    val ratio = longestSide.toDouble() / maxOf(source.width, source.height).toDouble()
    val width = (source.width * ratio).toInt().coerceAtLeast(1)
    val height = (source.height * ratio).toInt().coerceAtLeast(1)
    val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = target.createGraphics()
    try {
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, width, height)
        graphics.drawImage(source, 0, 0, width, height, null)
    } finally {
        graphics.dispose()
    }
    return target
}

/** Bỏ kênh alpha (ảnh PNG trong suốt) để JPEG không bị sai màu. */
private fun toRgb(source: BufferedImage): BufferedImage =
    if (source.type == BufferedImage.TYPE_INT_RGB) {
        source
    } else {
        scaleToLongestSide(source, maxOf(source.width, source.height))
    }
