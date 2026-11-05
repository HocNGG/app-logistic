package vn.lab.logistics.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import java.io.ByteArrayOutputStream

/**
 * =====================================================================================
 * `actual` cho ANDROID của [encodeSignaturePng] — vẽ bằng `android.graphics.Canvas`.
 *
 * Ba yêu cầu của C1, kiểm bằng code (giống bản desktop nhưng API nền tảng khác):
 *
 *  1. **Nền TRONG SUỐT** → `Bitmap.Config.ARGB_8888` khởi tạo toàn `0x00000000`, KHÔNG
 *     `drawColor(WHITE)`. (Nếu tô nền, chữ ký dán lên biên nhận sẽ thành một ô trắng.)
 *  2. **Nét ~3–4dp** → độ dày do caller tính từ bề rộng pad ([signatureStrokePx]) và truyền vào
 *     [strokeWidthPx]; ở đây chỉ `coerceAtLeast(1f)`.
 *  3. **Màu `Brand.ink`** → [SIGNATURE_INK_ARGB] suy từ token thiết kế, không viết hex tại chỗ.
 *
 * Đầu ra phải giữ magic bytes PNG (`89 50 4E 47 …`) vì `tools.giai_ma_anh()` của backend kiểm
 * magic bytes — `Bitmap.compress(PNG)` cho đúng PNG thật.
 *
 * Chấm (nét chỉ có 1 điểm — người dùng nhấn rồi nhấc ngay) được vẽ bằng chế độ FILL, vì
 * `Style.STROKE` với `drawCircle` sẽ ra một VÒNG TRÒN RỖNG thay vì một chấm mực.
 * =====================================================================================
 */
actual fun encodeSignaturePng(
    strokes: List<SignatureStroke>,
    widthPx: Int,
    heightPx: Int,
    strokeWidthPx: Float,
): ByteArray? {
    if (strokes.isEmpty() || widthPx <= 0 || heightPx <= 0) return null

    val width = strokeWidthPx.coerceAtLeast(1f)
    val image = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)

    val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SIGNATURE_INK_ARGB
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = width
    }
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SIGNATURE_INK_ARGB
        style = Paint.Style.FILL
    }

    strokes.forEach { stroke ->
        val points = stroke.points
        when {
            points.isEmpty() -> Unit

            points.size == 1 -> canvas.drawCircle(points[0].x, points[0].y, width / 2f, dot)

            else -> for (index in 1 until points.size) {
                canvas.drawLine(
                    points[index - 1].x,
                    points[index - 1].y,
                    points[index].x,
                    points[index].y,
                    pen,
                )
            }
        }
    }

    val out = ByteArrayOutputStream()
    val written = image.compress(Bitmap.CompressFormat.PNG, 100, out)
    return if (written) out.toByteArray() else null
}
