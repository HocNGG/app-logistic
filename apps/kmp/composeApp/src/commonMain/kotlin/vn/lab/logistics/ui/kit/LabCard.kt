package vn.lab.logistics.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Tokens

/**
 * =====================================================================================
 * CARD — mặt nổi chuẩn của app (spec `ui-dashboard-app-screens.md` §2).
 *
 * Một card gồm: nền `--lab-card` `#202A3A`, **viền 1px** `--lab-line`, bo [Tokens.radiusCard]
 * (10dp). **KHÔNG bóng** — ADR-009 §6: "card dùng BỀ MẶT phân tầng, KHÔNG bóng (bóng chỉ cho
 * dialog/menu)". Card nằm trên nền `#080D17` nên đã có bước tông ~4 bậc để tách; viền 8% chỉ
 * để khép mép trên nền tối, không phải để "khoanh" khối.
 *
 * Vì sao bỏ hẳn `Modifier.shadow` ở đây: trên nền navy gần đen, bóng đen là **vô hình** — nó
 * chỉ tốn một lớp composite mỗi khung hình. Trước đây card tối vẽ bóng và không viền; nay đổi
 * lại đúng luật trên (bề mặt + viền, không bóng) cho MỌI chế độ vì app chỉ còn chế độ tối.
 *
 * Card thay thế toàn bộ "khung tự chế" cũ (Surface + viền + padding viết tay ở từng màn hình),
 * nhờ vậy mọi màn có cùng một độ dày viền, cùng bo góc và cùng nhịp padding.
 *
 * @param title tiêu đề card (tuỳ chọn) — sẽ nằm ở dải đầu, có đường kẻ mảnh dưới tiêu đề.
 * @param subtitle dòng phụ dưới tiêu đề (13sp, `ink2`) — dùng cho mốc thời gian / ghi chú.
 * @param trailing chỗ đặt chip/nút ở góc phải của dải đầu (bộ lọc, số lượng, menu).
 * @param padding padding của phần thân; mặc định 16dp theo thang 4dp.
 * @param content nội dung thân card (ColumnScope nên tự xếp dọc được).
 */
@Composable
fun LabCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    padding: PaddingValues = PaddingValues(Tokens.s4),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape: Shape = RoundedCornerShape(Tokens.radiusCard)
    Column(
        modifier = modifier
            .clip(shape)
            .background(AppTheme.lab.card)
            .border(Tokens.dividerLine, AppTheme.lab.line, shape),
    ) {
        if (title != null) {
            LabCardHeader(title = title, subtitle = subtitle, trailing = trailing)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(padding),
            content = content,
        )
    }
}

/** Dải đầu của [LabCard]: tiêu đề + dòng phụ bên trái, slot hành động bên phải, kẻ mảnh dưới. */
@Composable
private fun LabCardHeader(
    title: String,
    subtitle: String?,
    trailing: (@Composable () -> Unit)?,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Tokens.s4,
                    end = Tokens.s3,
                    top = Tokens.s3,
                    bottom = Tokens.s3,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.subtitle1,
                    color = AppTheme.lab.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.caption,
                        color = AppTheme.lab.ink2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Tokens.s1 / 2),
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(Tokens.s2))
                trailing()
            }
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(Tokens.dividerLine)
                .background(AppTheme.lab.line),
        )
    }
}

/**
 * Tiêu đề KHỐI trong một trang (giữa hai card, trước bảng…). Không phải `Text` tự đặt cỡ chữ:
 * cả app dùng đúng một cỡ cho cấp này (16sp đậm + dòng phụ 13sp `ink2`).
 */
@Composable
fun LabSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.lab.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (hint != null) {
            Spacer(Modifier.width(Tokens.s2))
            Text(
                text = hint,
                style = MaterialTheme.typography.caption,
                color = AppTheme.lab.ink2,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Dòng "nhãn — giá trị" của pane chi tiết (thay `DetailField` cũ, cùng cỡ chữ toàn app). */
@Composable
fun LabField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(bottom = Tokens.s3)) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = AppTheme.lab.ink2,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body1,
            color = AppTheme.lab.ink,
            modifier = Modifier.padding(top = Tokens.s1),
        )
    }
}

/** Thân của một pane danh sách: cùng nhịp padding cho mọi màn (không tự đặt 20dp). */
@Composable
fun LabPaneContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(Tokens.s4), content = content)
}
