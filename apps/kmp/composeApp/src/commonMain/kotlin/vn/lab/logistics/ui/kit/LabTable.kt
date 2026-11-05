package vn.lab.logistics.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Lab
import vn.lab.logistics.design.Tokens

/**
 * =====================================================================================
 * BẢNG — luật cứng của spec `ui-dashboard-app-screens.md` §5:
 *  - header nền **`--lab-s2` `#192231`** (bề mặt bậc 2 — ADR-009 §6: "bề mặt 2 = khối nội dung,
 *    filter bar, header bảng"), chữ 13sp chữ thường `ink2`, KHÔNG in hoa;
 *  - hàng cao ≥ 44dp; **chỉ kẻ đường NGANG 1px** (`line`) — không kẻ dọc, không zebra sọc;
 *  - số căn phải + `tabular-nums`; đơn vị nằm ở header, không lặp trong ô;
 *  - hàng focus được (bàn phím đi hết bảng) và có viền focus nhìn thấy rõ.
 *
 * Ba mảnh: [LabTable] (khung + header), [LabTableRow] (một hàng bấm được), [LabCell] /
 * [LabNumCell] (ô chữ / ô số). Danh sách việc của 3 màn cũng dùng đúng bộ này — nhờ vậy
 * "danh sách" và "bảng" không còn là hai kiểu trình bày khác nhau trong cùng một app.
 * =====================================================================================
 */

/**
 * Khung bảng: header (nếu có) + các hàng do caller truyền. Bo góc theo card, viền 1px.
 *
 * @param header hàng tiêu đề cột — `null` với danh sách không có cột cố định.
 * @param bordered `false` khi bảng nằm TRONG một [LabCard] (đã có viền) — tránh viền đôi.
 */
@Composable
fun LabTable(
    modifier: Modifier = Modifier,
    bordered: Boolean = true,
    header: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(Tokens.radiusCard)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (bordered) Modifier.border(Tokens.dividerLine, AppTheme.lab.line, shape) else Modifier,
            ),
    ) {
        if (header != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppTheme.lab.surface2)
                    .padding(horizontal = Lab.tableCellPadH, vertical = Lab.tableHeaderPadV),
                verticalAlignment = Alignment.CenterVertically,
                content = header,
            )
            LabRowDivider()
        }
        content()
    }
}

/** Một hàng bảng. Chiều cao tối thiểu 44dp (ngón tay đeo găng vẫn bấm được). */
@Composable
fun LabTableRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Lab.chipRadius)
    // ⚠️ ĐO TƯƠNG PHẢN CỦA HÀNG ĐANG CHỌN (không đoán): nền = nhấn pha `Lab.NAV_ACTIVE_TINT`
    // (10%) LÊN BỀ MẶT BÊN DƯỚI. Hai trường hợp, hai số khác nhau:
    //   • trên nền trang `#080D17` (chỗ 4 màn danh sách đang dùng): chữ phụ `ink2` = 5.81:1 ✓
    //   • trên card `#202A3A`: chữ phụ `ink2` chỉ còn 4.09:1 ✗ (chữ chính `ink` vẫn 10.58:1 ✓)
    // ⇒ Hàng bảng có `selected = true` phải nằm TRÊN NỀN TRANG, không lồng trong [LabCard]; hoặc
    // dùng chữ chính cho mọi ô của hàng đang chọn. (Đây là lý do 4 màn danh sách đặt danh sách
    // trực tiếp trên nền trang chứ không bọc trong card.)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Lab.tableRowMinHeight)
            .background(
                if (selected) {
                    AppTheme.lab.accent.copy(alpha = Lab.NAV_ACTIVE_TINT)
                } else {
                    Tokens.transparent
                },
            )
            .then(
                if (selected) {
                    Modifier.border(Tokens.dividerLine, AppTheme.lab.accent.copy(alpha = 0.35f), shape)
                } else {
                    Modifier
                },
            )
            .onFocusChanged { focused = it.isFocused }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick).focusable() else Modifier)
            .then(
                if (focused) {
                    Modifier.border(Tokens.focusRing, AppTheme.lab.accent, shape)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Lab.tableCellPadH, vertical = Lab.tableCellPadV),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Đường kẻ NGANG 1px giữa hai hàng — mảnh nhất có thể, đúng spec "chỉ kẻ đường ngang". */
@Composable
fun LabRowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Tokens.dividerLine)
            .background(AppTheme.lab.line),
    )
}

/** Ô chữ thường. `weight` để caller chia cột; mặc định co theo nội dung. */
@Composable
fun RowScope.LabCell(
    text: String,
    modifier: Modifier = Modifier,
    weight: Float? = null,
    color: androidx.compose.ui.graphics.Color = AppTheme.lab.ink,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        style = LabCellStyle,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = if (weight != null) modifier.weight(weight) else modifier,
    )
}

/**
 * Ô SỐ: căn phải + tabular-nums (spec §5). Dùng cho số kiện, khối lượng, phần trăm, số việc.
 * Đơn vị KHÔNG lặp trong ô — đặt ở header cột.
 */
@Composable
fun RowScope.LabNumCell(
    text: String,
    modifier: Modifier = Modifier,
    weight: Float? = null,
    color: androidx.compose.ui.graphics.Color = AppTheme.lab.ink,
) {
    Text(
        text = text,
        style = LabNumStyle,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.End,
        modifier = if (weight != null) modifier.weight(weight) else modifier,
    )
}

/** Ô header của bảng: 13sp, chữ thường, `ink2` — không in hoa, không giãn cách. */
@Composable
fun RowScope.LabHeaderCell(
    text: String,
    weight: Float,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    Text(
        text = text,
        style = LabCellStyle.copy(fontWeight = FontWeight.Medium),
        color = AppTheme.lab.ink2,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        modifier = modifier.weight(weight),
    )
}

/** Khoảng cách dọc giữa các khối của một trang — một giá trị, không tự đặt 18/20/28dp. */
@Composable
fun LabVGap(height: androidx.compose.ui.unit.Dp = Tokens.s4) {
    Box(Modifier.fillMaxWidth().height(height))
}

/** Khoảng cách ngang nhỏ giữa hai phần tử trong một hàng. */
@Composable
fun LabHGap(width: androidx.compose.ui.unit.Dp = Tokens.s2) {
    Box(Modifier.width(width))
}

/** Kiểu chữ của ô bảng/danh sách: 13sp, dòng 19sp (giữ dấu tiếng Việt không bị cắt). */
val LabCellStyle = TextStyle(
    fontSize = Lab.tableTextSize,
    lineHeight = Lab.tableTextLine,
    fontWeight = FontWeight.Normal,
)

/** Kiểu chữ của ô SỐ: tabular-nums để các cột số thẳng hàng. */
val LabNumStyle = LabCellStyle.copy(fontFeatureSettings = "tnum")

/** Độ dày viền focus mặc định của bảng — giữ hằng để self-check đọc được. */
val LabFocusRing = Tokens.focusRing

/** Nhịp padding chuẩn của một ô — 12dp ngang, 8dp dọc. */
val LabCellPadH = Lab.tableCellPadH
val LabCellPadV = Lab.tableCellPadV

/** Chiều cao tối thiểu của một hàng — 44dp theo spec §5. */
val LabRowHeight = Lab.tableRowMinHeight
