package vn.lab.logistics.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextFieldColors
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Tokens

/**
 * =====================================================================================
 * MÀU Ô NHẬP LIỆU — một chỗ duy nhất, để mọi ô nhập trong app (đăng nhập, tìm kiếm, màn capture,
 * hộp thoại lý do) có cùng viền, cùng màu chữ và cùng focus ring.
 *
 * Vì sao cần: `OutlinedTextField` của Material 2 lấy màu từ `MaterialTheme.colors`, mà theme chỉ
 * có 12 vai. Trên nền navy `#080D17`, viền mặc định của Material bị "xám lạnh"; trên nền tối
 * thì viền chìm hẳn. Bộ màu này bám token ADR-009 §6:
 *  - viền thường 1px `line` (trắng 8%), viền focus 2px `accent` (focus NHÌN THẤY ĐƯỢC);
 *  - nền `card`, chữ `ink`, nhãn/placeholder `ink2`, lỗi `danger`.
 * =====================================================================================
 */
@Composable
fun labTextFieldColors(error: Boolean = false): TextFieldColors =
    TextFieldDefaults.outlinedTextFieldColors(
        textColor = AppTheme.lab.ink,
        backgroundColor = AppTheme.lab.card,
        cursorColor = AppTheme.lab.accent,
        focusedBorderColor = AppTheme.lab.accent,
        unfocusedBorderColor = if (error) AppTheme.lab.danger else AppTheme.lab.line,
        errorBorderColor = AppTheme.lab.danger,
        focusedLabelColor = AppTheme.lab.accent,
        unfocusedLabelColor = AppTheme.lab.ink2,
        errorLabelColor = AppTheme.lab.danger,
        placeholderColor = AppTheme.lab.ink2,
        leadingIconColor = AppTheme.lab.ink2,
        trailingIconColor = AppTheme.lab.ink2,
    )

/**
 * Màu của nút phụ (`OutlinedButton`): nền trong suốt, chữ `ink`, viền `lineStrong` (trắng 14%
 * — token "viền mạnh" của §6, vì viền 8% của `line` quá mảnh để đọc ra một cái NÚT trên nền
 * navy). Chữ lúc disabled dùng `ink3` — đúng vai "mực mờ/chú thích" của ADR §7.
 */
@Composable
fun labOutlinedButtonColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
    backgroundColor = Color.Transparent,
    contentColor = AppTheme.lab.ink,
    disabledContentColor = AppTheme.lab.ink3,
)

/** Viền của nút phụ. */
@Composable
fun labOutlinedButtonBorder(enabled: Boolean = true): BorderStroke = BorderStroke(
    width = Tokens.dividerLine,
    color = if (enabled) AppTheme.lab.lineStrong else AppTheme.lab.line,
)

/** Nhãn nhỏ phía trên một ô nhập — 14sp, `ink`, có nhịp trên/dưới thống nhất. */
@Composable
fun LabFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.subtitle2,
        color = AppTheme.lab.ink,
        modifier = modifier.padding(PaddingValues(top = Tokens.s1, bottom = Tokens.s1)),
    )
}
