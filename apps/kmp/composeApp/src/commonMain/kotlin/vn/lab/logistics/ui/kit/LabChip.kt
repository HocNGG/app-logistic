package vn.lab.logistics.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Lab
import vn.lab.logistics.design.Tokens
import vn.lab.logistics.ui.components.Tone

/**
 * =====================================================================================
 * CHIP — nhãn trạng thái/bộ lọc NHỎ NHẤT của app (spec `ui-dashboard-app-screens.md` §5).
 *
 * Hình thức (đúng spec, không tự thêm): chữ 13sp, padding `2px 8px`, bo góc **4dp** (không phải
 * pill), nền tint 12% của màu trạng thái, chữ màu đậm tương ứng. **Luôn có chữ** — màu không
 * bao giờ là tín hiệu duy nhất (a11y + người mù màu).
 *
 * Vì sao KHÔNG dùng `StatusPill` cũ (bo 999dp): pill là *tell* của "badge AI" và trái spec §5;
 * file `ui/components/StatusPill.kt` nay chỉ còn là cầu nối gọi lại component này.
 * =====================================================================================
 */

/**
 * Chip trạng thái mang [Tone] có sẵn (khi caller đã biết giọng).
 *
 * @param text nhãn tiếng Việt — luôn hiển thị, kể cả khi màu đã gợi ý nghĩa.
 * @param tone giọng nghiệp vụ (Ok/Warn/Bad/Info), xem [labToneOfStatusLabel].
 */
@Composable
fun LabChip(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Lab.chipRadius))
            .background(toneFill(tone))
            .padding(horizontal = Lab.chipPadH, vertical = Lab.chipPadV),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = Lab.chipTextSize,
                lineHeight = Lab.chipTextLine,
                fontWeight = FontWeight.Medium,
            ),
            color = toneText(tone),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Chip trạng thái tự suy giọng từ NHÃN tiếng Việt ([labToneOfStatusLabel]).
 *
 * Đây là component màn hình nên dùng: nhãn "Đã về kho" là Ok, "Lấy hàng thất bại" là Bad — không
 * màn nào phải tự viết `when (state)`. Nhờ vậy thêm một trạng thái mới ở BE chỉ cần đặt đúng
 * nhãn, màu đi theo.
 */
@Composable
fun LabStatusChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    LabChip(text = label, tone = labToneOfStatusLabel(label), modifier = modifier)
}

/**
 * Chip lọc / chip chọn khoảng thời gian (toolbar dashboard, "Cài đặt demo", chip lọc việc cần gán).
 *
 * Trạng thái hiển thị bằng **ba** tín hiệu cùng lúc — không chỉ màu: nền, viền 1px và màu chữ.
 * `selected = false` ⇒ nền trong suốt + viền `lineStrong` (mực 12% — vẫn đọc ra một control bấm
 * được trên nền sáng), chữ `ink2`.
 *
 * `selected = true` ⇒ **PILL ĐEN** (`ink`) + **chữ trắng** (`onInk`, 19,6:1) — đúng luật v5:
 * mọi thứ "đang chọn / đang hành động" (CTA, nav active, chip lọc) dùng mực đặc, KHÔNG dùng lime.
 *
 * VÌ SAO KHÔNG còn lime cho chip đang chọn (đổi 2026-11-03): bản trước chọn chip bằng
 * `accent` cho cả nền-tint lẫn CHỮ. Trên nền sáng v5, chữ lime `#C2EE2F` chỉ ~1,35:1 ⇒ chip
 * đang chọn là chip KHÓ ĐỌC NHẤT trên màn hình (self-check đo được). Lime giữ vai trò phụ
 * (gạch nhấn, thanh tiến độ), không phải ngôn ngữ của "đang chọn".
 *
 * @param selected chip đang được chọn.
 * @param onClick `null` ⇒ chip chỉ để đọc (không focus, không bấm được).
 */
@Composable
fun LabFilterChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(Lab.chipRadius)
    val bg = if (selected) AppTheme.lab.ink else Tokens.transparent
    val border = if (selected) AppTheme.lab.ink else AppTheme.lab.lineStrong
    val fg = if (selected) AppTheme.lab.onInk else AppTheme.lab.ink2
    val clickable = if (onClick != null) {
        Modifier.clickable(onClick = onClick).focusable()
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = Tokens.touchSecondary)
            .clip(shape)
            .background(bg)
            .border(BorderStroke(Tokens.dividerLine, border), shape)
            .then(clickable)
            .padding(horizontal = Tokens.s3, vertical = Lab.chipPadV),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.body2,
                color = fg,
                fontWeight = if (selected) FontWeight.SemiBold else null,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
