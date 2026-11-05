package vn.lab.logistics.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import vn.lab.logistics.design.Brand
import vn.lab.logistics.design.Tokens

/**
 * =====================================================================================
 * LOGO — vẽ bằng COMPOSE CANVAS, KHÔNG dùng asset nhị phân.
 *
 * Vì sao vẽ chứ không nhúng PNG (skill ui-3d-premium §6):
 *  - Logo phải "vẽ được bằng hình học" ⇒ sắc nét ở 30dp (thanh nav) và 132dp (hero) mà
 *    không cần nhiều kích thước ảnh, không nhoè, không tăng kích thước gói cài.
 *  - Không phụ thuộc file ngoài ⇒ app chạy được cả khi mất mạng/thiếu asset.
 *
 * NGUỒN TOẠ ĐỘ: `assets/brand/logo-mark.svg` (viewBox 0 0 64 64). Toạ độ dưới đây CHÉP
 * NGUYÊN từ SVG — sửa SVG thì phải sửa lại đây (SelfCheck kiểm 3 đỉnh khối kiện).
 *
 * NGỮ NGHĨA (docs/11 §3 — đồ hoạ phải có nghĩa nghiệp vụ, không phải khối xoay vô hồn):
 *  - Khối isometric 3 mặt = một **kiện hàng** — đơn vị nghiệp vụ cơ bản.
 *  - Đường nét đứt + chấm sáng = **hành trình** (⑨ → ⑩ → ⑪), không phải trang trí.
 *  - Ba sắc độ lime / hổ phách (ADR-009 §6) = **ba module tự viết** (chuyến · lấy hàng · hành
 *    trình). Không còn sky/indigo/cyan của bảng màu cũ.
 *  - Dải băng ngang mặt trên = dây đai kiện hàng, đồng thời là "band" của chuyến.
 *
 * Hệ toạ độ SVG (gốc trên-trái) trùng hệ toạ độ DrawScope ⇒ chỉ cần scale `minDimension/64`.
 * =====================================================================================
 */

/** Cạnh của viewBox trong `logo-mark.svg`. Mọi toạ độ dưới đây chia cho hằng số này. */
const val LOGO_VIEWBOX = 64f

// ---------------------------------------------------------------------------------------
// 6 mốc hình học của khối kiện — CHÉP NGUYÊN từ `assets/brand/logo-mark.svg`.
// Khai thành hằng số để `SelfCheck` khẳng định được logo vẫn CÂN trong khung (kiểm hình học
// bằng máy, không bằng mắt): đỉnh trên + đáy dưới đối xứng qua tâm, hai vai bằng nhau.
// ---------------------------------------------------------------------------------------
/** X của đỉnh khối (SVG: 32). */
const val LOGO_APEX_X = 32f
/** Y của đỉnh khối (SVG: 6). */
const val LOGO_APEX_Y = 6f
/** X vai trái (SVG: 8). */
const val LOGO_LEFT_X = 8f
/** X vai phải (SVG: 56). */
const val LOGO_RIGHT_X = 56f
/** Y của hai vai (SVG: 19). */
const val LOGO_SHOULDER_Y = 19f
/** Y đáy khối (SVG: 58). */
const val LOGO_BOTTOM_Y = 58f

/**
 * Logo mark (chỉ khối kiện + hành trình), không kèm chữ.
 *
 * @param size cạnh của khung vẽ; logo tự scale theo [LOGO_VIEWBOX].
 * @param glow vẽ thêm quầng sáng phía sau (dùng ở hero, tắt ở thanh nav cho nhẹ).
 */
@Composable
fun LogoMark(
    modifier: Modifier = Modifier,
    size: Dp = Tokens.logoNavSize,
    glow: Boolean = false,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension / LOGO_VIEWBOX
        if (glow) {
            // Quầng sáng = alpha của sắc sky, KHÔNG phải màu mới (skill 07 §6).
            val center = Offset(32f * s, 26f * s)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Brand.forest.copy(alpha = 0.30f), Brand.ground.copy(alpha = 0f)),
                    center = center,
                    radius = 32f * s,
                ),
                radius = 32f * s,
                center = center,
            )
        }
        drawKhoiKien(this, s)
        drawHanhTrinh(this, s)
    }
}

/** Ba mặt của khối kiện isometric — đúng 3 path của SVG, cùng thứ tự vẽ. */
private fun drawKhoiKien(scope: DrawScope, s: Float) = with(scope) {
    // Mặt trên: 32,6 → 56,19 → 32,32 → 8,19  (lime → lime sáng)
    val top = Path().apply {
        moveTo(LOGO_APEX_X * s, LOGO_APEX_Y * s)
        lineTo(LOGO_RIGHT_X * s, LOGO_SHOULDER_Y * s)
        lineTo(LOGO_APEX_X * s, 32f * s)
        lineTo(LOGO_LEFT_X * s, LOGO_SHOULDER_Y * s)
        close()
    }
    drawPath(
        path = top,
        brush = Brush.linearGradient(
            colors = listOf(Brand.forest, Brand.forestHover),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height),
        ),
    )

    // Mặt trái: 8,19 → 32,32 → 32,58 → 8,45  (lime sáng → lime đậm)
    val left = Path().apply {
        moveTo(LOGO_LEFT_X * s, LOGO_SHOULDER_Y * s)
        lineTo(LOGO_APEX_X * s, 32f * s)
        lineTo(LOGO_APEX_X * s, LOGO_BOTTOM_Y * s)
        lineTo(LOGO_LEFT_X * s, 45f * s)
        close()
    }
    drawPath(
        path = left,
        brush = Brush.verticalGradient(
            colors = listOf(Brand.forestHover, Brand.forestDeep),
            startY = LOGO_SHOULDER_Y * s,
            endY = LOGO_BOTTOM_Y * s,
        ),
    )

    // Mặt phải: 56,19 → 56,45 → 32,58 → 32,32  (hổ phách → hổ phách đậm)
    val right = Path().apply {
        moveTo(LOGO_RIGHT_X * s, LOGO_SHOULDER_Y * s)
        lineTo(LOGO_RIGHT_X * s, 45f * s)
        lineTo(LOGO_APEX_X * s, LOGO_BOTTOM_Y * s)
        lineTo(LOGO_APEX_X * s, 32f * s)
        close()
    }
    drawPath(
        path = right,
        brush = Brush.verticalGradient(
            colors = listOf(Brand.amber, Brand.amberDeep),
            startY = LOGO_SHOULDER_Y * s,
            endY = LOGO_BOTTOM_Y * s,
        ),
    )

    // Dải băng chuyến trên mặt khối: 32,6 → 44,12.5 → 44,25.5 → 32,32 → 20,25.5 → 20,12.5
    val band = Path().apply {
        moveTo(32f * s, 6f * s)
        lineTo(44f * s, 12.5f * s)
        lineTo(44f * s, 25.5f * s)
        lineTo(32f * s, 32f * s)
        lineTo(20f * s, 25.5f * s)
        lineTo(20f * s, 12.5f * s)
        close()
    }
    drawPath(
        path = band,
        color = Brand.ground,
        alpha = 0.35f,
        style = Stroke(width = 1.4f * s),
    )
}

/** Đường hành trình: nét đứt hình cung + chấm sáng ở điểm đến (⑨ → ⑩ → ⑪). */
private fun drawHanhTrinh(scope: DrawScope, s: Float) = with(scope) {
    val journey = Path().apply {
        moveTo(20f * s, 46f * s)
        cubicTo(14f * s, 40f * s, 14f * s, 30f * s, 22f * s, 25f * s)
    }
    drawPath(
        path = journey,
        color = Brand.forestPale,
        style = Stroke(
            width = 2.6f * s,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f * s, 5f * s)),
        ),
    )
    drawCircle(color = Brand.forestPale, radius = 2.8f * s, center = Offset(22f * s, 25f * s))
}

/**
 * Logo + tên app: dùng ở thanh trên cùng (góc trên trái) và ở hero.
 *
 * @param subtitle dòng phụ dưới tên app (để trống ở thanh nav hẹp).
 */
@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    markSize: Dp = Tokens.logoNavSize,
    title: String = "AppLogictics",
    subtitle: String? = null,
    titleColor: Color = MaterialTheme.colors.onSurface,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.s2),
    ) {
        LogoMark(size = markSize)
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.subtitle1,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.caption,
                    color = titleColor.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Tokens.s1 / 2),
                )
            }
        }
    }
}
