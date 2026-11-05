package vn.lab.logistics.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Tokens
import kotlin.math.cos
import kotlin.math.sin

/**
 * =====================================================================================
 * ICON VẼ BẰNG CANVAS — không emoji, không thư viện icon.
 *
 * Lý do (skill `ui-modern-industrial.md` §5 + build offline): `compose.material-icons` không có
 * trong classpath offline, và emoji thì mỗi máy render một kiểu (đổi màu, thành khối vuông trên
 * máy ảo không có font emoji) — trên ảnh chụp nghiệm thu sẽ thấy ngay là "app tự vẽ".
 * Vẽ bằng `Canvas` thì icon luôn đúng màu token, sắc nét ở mọi density và không thêm phụ thuộc.
 * =====================================================================================
 */

/**
 * Bánh răng — nút mở màn "Cài đặt demo" ở chân sidebar.
 *
 * @param size cạnh khung vẽ (mặc định 18dp — vừa dòng chữ 14sp bên cạnh).
 */
@Composable
fun LabGearIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    color: Color = AppTheme.lab.ink,
) {
    Canvas(modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val outer = this.size.minDimension * 0.44f
        val inner = this.size.minDimension * 0.30f
        val stroke = Stroke(width = Tokens.dividerLine.toPx() * 2.2f, cap = StrokeCap.Round)
        drawCircle(color = color, radius = inner, center = center, style = stroke)
        // 6 răng: mỗi răng là một đoạn thẳng hướng tâm — đủ đọc ra "bánh răng" ở 18dp.
        repeat(6) { index ->
            val angle = (Math.PI / 3.0) * index
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            drawLine(
                color = color,
                start = Offset(center.x + dx * inner, center.y + dy * inner),
                end = Offset(center.x + dx * outer, center.y + dy * outer),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(color = color, radius = this.size.minDimension * 0.07f, center = center)
    }
}
