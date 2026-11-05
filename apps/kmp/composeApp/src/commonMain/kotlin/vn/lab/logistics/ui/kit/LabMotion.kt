package vn.lab.logistics.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import vn.lab.logistics.design.Lab

/**
 * Đường lui BẮT BUỘC của mọi hiệu ứng: người dùng/bản demo bật "giảm chuyển động" (công tắc trong
 * màn "Cài đặt demo") ⇒ mọi animation của bộ kit dừng, nội dung vẫn hiện đủ.
 *
 * Vì sao đặt ở kit (không ở `ui/components`): đây là hợp đồng của tầng chuyển động — mọi component
 * phát animation đều đọc đúng một cờ này. `App` cắm giá trị, kit đọc; không nơi nào khác được tạo
 * cờ thứ hai.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * =====================================================================================
 * CHUYỂN ĐỘNG TỐI THIỂU NHƯNG CÓ — đúng `ui-modern-industrial.md` §6:
 * thời lượng 160–220ms, easing giảm tốc, **luôn có đường lui** cho người bật "giảm chuyển động".
 *
 * Phạm vi (theo spec): chỉ nội dung màn hình khi vào màn, và phản hồi hover/press của control
 * (ripple + đổi màu nền của Material). Bảng, form, danh sách dài **không** có animation — mỗi
 * lần con trỏ đi qua một hàng mà chạy animation là tụt khung hình trên máy demo.
 *
 * Vì sao `Animatable` chứ không `animateFloatAsState`: entrance chỉ chạy MỘT lần khi màn được
 * compose; `animateFloatAsState` sẽ chạy lại mỗi lần recomposition đổi giá trị đích, gây nháy.
 * =====================================================================================
 */

/**
 * Bọc nội dung màn hình bằng hiệu ứng vào màn: mờ dần + trượt lên [Lab.enterOffset] (8dp),
 * [Lab.ENTER_MS] (180ms). Bọc ở GỐC nội dung của mỗi màn, không bọc từng khối.
 *
 * Khi `LocalReducedMotion` bật: không chạy animation, nội dung hiện ngay với alpha = 1 — người
 * dùng vẫn thấy đủ thông tin (đây là "đường lui" bắt buộc, không phải tuỳ chọn).
 */
@Composable
fun LabEnter(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }

    LaunchedEffect(reduced) {
        if (reduced) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = Lab.ENTER_MS, easing = FastOutSlowInEasing),
            )
        }
    }

    Column(
        modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * Lab.enterOffset.toPx()
        },
    ) {
        content()
    }
}
