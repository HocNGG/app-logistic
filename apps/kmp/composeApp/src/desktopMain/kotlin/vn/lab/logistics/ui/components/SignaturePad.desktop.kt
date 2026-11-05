package vn.lab.logistics.ui.components

import java.awt.BasicStroke
import java.awt.RenderingHints
import java.awt.geom.Line2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.awt.Color as AwtColor

/**
 * =====================================================================================
 * `actual` DESKTOP của [encodeSignaturePng] — dùng `java.awt`/`javax.imageio`.
 *
 * Chỉ file này (và `core/media/ImageCodec.desktop.kt`) được phép chạm API Java của ảnh.
 *
 * BA YÊU CẦU CỦA C1, kiểm bằng code:
 *  1. **Nền trong suốt** → `BufferedImage.TYPE_INT_ARGB` (khởi tạo toàn `0x00000000`),
 *     KHÔNG `fillRect` nền trắng.
 *  2. **Nét 3–4dp** → độ dày do caller tính từ bề rộng pad ([signatureStrokePx]).
 *  3. **Màu `Brand.ink`** → [SIGNATURE_INK_ARGB] suy từ token, không hex rải rác.
 *
 * Đầu ra phải giữ magic bytes PNG (`89 50 4E 47 …`) vì backend
 * `tools.giai_ma_anh()` kiểm magic bytes — sai là 400 ngay.
 * =====================================================================================
 */
actual fun encodeSignaturePng(
    strokes: List<SignatureStroke>,
    widthPx: Int,
    heightPx: Int,
    strokeWidthPx: Float,
): ByteArray? {
    if (strokes.isEmpty() || widthPx <= 0 || heightPx <= 0) return null

    val image = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        graphics.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON,
        )
        graphics.color = AwtColor(SIGNATURE_INK_ARGB, true)
        graphics.stroke = BasicStroke(
            strokeWidthPx.coerceAtLeast(1f),
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND,
        )
        strokes.forEach { stroke ->
            val points = stroke.points
            when {
                points.isEmpty() -> Unit
                points.size == 1 -> {
                    val radius = strokeWidthPx.coerceAtLeast(1f) / 2f
                    graphics.fillOval(
                        (points[0].x - radius).toInt(),
                        (points[0].y - radius).toInt(),
                        (radius * 2).toInt().coerceAtLeast(1),
                        (radius * 2).toInt().coerceAtLeast(1),
                    )
                }

                else -> for (index in 1 until points.size) {
                    graphics.draw(
                        Line2D.Float(
                            points[index - 1].x,
                            points[index - 1].y,
                            points[index].x,
                            points[index].y,
                        ),
                    )
                }
            }
        }
    } finally {
        graphics.dispose()
    }

    val out = ByteArrayOutputStream()
    val written = ImageIO.write(image, "png", out)
    if (!written) return null
    return out.toByteArray()
}
