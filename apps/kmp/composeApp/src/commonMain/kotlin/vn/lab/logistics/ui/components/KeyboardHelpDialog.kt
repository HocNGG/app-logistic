package vn.lab.logistics.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Tokens

/**
 * Bảng trợ giúp phím tắt — mở bằng phím `?` (docs/04-kmp-app.md §11).
 *
 * Bắt buộc có: người dùng ERP gõ phím nhanh hơn click; nếu không có bảng trợ giúp thì
 * phím tắt là "bí mật của người viết code". Đóng được bằng `Esc` hoặc nút "Đóng".
 */
@Composable
fun KeyboardHelpDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Phím tắt", style = MaterialTheme.typography.h6) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ShortcutRow("Ctrl + K", "Tìm kiếm / mở ô tìm nhanh")
                ShortcutRow("J hoặc ↓", "Xuống dòng kế tiếp trong danh sách")
                ShortcutRow("K hoặc ↑", "Lên dòng phía trên")
                ShortcutRow("Enter", "Mở chi tiết việc đang chọn")
                ShortcutRow("Esc", "Đóng chi tiết / đóng hộp thoại")
                ShortcutRow("R", "Tải lại dữ liệu pane hiện tại")
                ShortcutRow("?", "Mở bảng trợ giúp này")
                ShortcutRow("Tab / Shift+Tab", "Di chuyển focus (có viền focus rõ)")
                Text(
                    text = "Phím tắt chỉ hoạt động khi con trỏ không nằm trong ô nhập liệu — " +
                        "để không \"nuốt\" chữ bạn đang gõ.",
                    style = MaterialTheme.typography.caption,
                    color = AppTheme.lab.ink2,
                    modifier = Modifier.padding(top = Tokens.s3),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng") }
        },
    )
}

@Composable
private fun ShortcutRow(keys: String, action: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = keys,
            style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.width(KEY_COLUMN_WIDTH),
        )
        Text(
            text = action,
            style = MaterialTheme.typography.body2,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Bề rộng cột phím — token cục bộ của component (không rò ra màn hình). */
private val KEY_COLUMN_WIDTH = Tokens.s7 + Tokens.s6 + Tokens.s5
