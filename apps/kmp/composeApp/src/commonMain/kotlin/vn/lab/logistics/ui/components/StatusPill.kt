package vn.lab.logistics.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Tokens
import vn.lab.logistics.ui.kit.LabChip
import vn.lab.logistics.ui.kit.toneFill
import vn.lab.logistics.ui.kit.toneText

/**
 * Bốn "giọng" trạng thái nghiệp vụ. Đây là **enum DUY NHẤT** của app được phép map trạng thái →
 * màu (skill 07 §6), nên `feature/…` không có mã màu hex nào: chỉ gọi `LabChip(text, tone)`.

 * Vì sao vẫn nằm ở `ui/components` (không chuyển sang `ui/kit`): nó đã được import ở 6 file
 * nghiệp vụ + 2 file test; chuyển chỗ chỉ tạo diff rỗng nghĩa. Bộ kit dùng LẠI enum này thay vì
 * tạo enum thứ hai — một từ vựng, một bảng màu.
 */
enum class Tone { Ok, Warn, Bad, Info }

/** Nền của một giọng (tint 12% của màu trạng thái trên mặt card) — ủy quyền cho bộ kit. */
@Composable
fun Tone.background(): Color = toneFill(this)

/** Chữ của một giọng (màu đậm, đã đo ≥ 4,5:1 trên nền tint tương ứng). */
@Composable
fun Tone.foreground(): Color = toneText(this)

/**
 * Nhãn trạng thái nhỏ: "Chờ gán", "Đang lấy", "Đã về kho"…
 *
 * GIỮ TÊN để không phải sửa 6 file gọi, nhưng **hình thức theo spec §5**: bo góc 4dp (không phải
 * pill 999dp), tint 12%, chữ 13sp. Việc vẽ đã chuyển sang [LabChip] — component duy nhất của app
 * được map trạng thái → màu.
 */
@Composable
fun StatusPill(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
) {
    LabChip(text = text, tone = tone, modifier = modifier)
}

/**
 * Khung cảnh báo (lỗi đăng nhập, cảnh báo kết nối không mã hoá, ghi chú nghiệp vụ).
 *
 * Hình thức: nền tint của giọng, viền 1px cùng màu nhạt, bo 10dp; tiêu đề 14sp đậm + thân 14sp,
 * nút hành động nằm dưới. Cùng bảng màu với [StatusPill] để người dùng chỉ phải học 4 màu.
 */
@Composable
fun CalloutBox(
    title: String,
    body: String? = null,
    tone: Tone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(Tokens.radiusCard)
    Column(
        modifier = modifier
            .background(toneFill(tone), shape)
            .border(Tokens.dividerLine, toneText(tone).copy(alpha = 0.24f), shape)
            .padding(horizontal = Tokens.s4, vertical = Tokens.s3),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle2,
            color = toneText(tone),
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.body2,
                color = toneText(tone),
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Tokens.s1),
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.padding(top = Tokens.s1),
            ) {
                Text(actionLabel, color = toneText(tone))
            }
        }
    }
}

/** Chữ phụ của app (nhãn, ghi chú) — một đường duy nhất, không tự đặt alpha 0.72. */
@Composable
fun mutedText(): Color = AppTheme.lab.ink2
