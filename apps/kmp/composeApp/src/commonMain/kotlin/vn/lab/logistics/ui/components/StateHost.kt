package vn.lab.logistics.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Tokens
import vn.lab.logistics.ui.kit.LabEmptyState

/**
 * =====================================================================================
 * BỐN TRẠNG THÁI — dùng chung cho MỌI màn hình (docs/04 §3, agents/fe/AGENT.md §4).
 *
 * Sai lầm chết người #1 của app demo: chỉ làm nhánh "có dữ liệu" → live demo gặp mạng chậm
 * là màn trắng, mất điểm ngay. Vì vậy mọi màn bắt buộc:
 *
 *   Loading → spinner + nhãn "Đang tải…" (biết app còn sống)
 *   Empty   → câu giải thích + gợi ý hành động (không phải màn trắng)
 *   Error   → thông báo TIẾNG VIỆT + nút "Thử lại" (không bao giờ chỉ có "Something went wrong")
 *   Content → nội dung thật
 * =====================================================================================
 */
sealed interface UiState<out T> {
    /** Đang tải dữ liệu. */
    data object Loading : UiState<Nothing>

    /** Tải xong nhưng không có bản ghi nào (hoặc: chưa bắt đầu, vd form trống). */
    data object Empty : UiState<Nothing>

    /** Lỗi — `retry` là hành động tiếp theo, không được để null mà không có lý do. */
    data class Error(val message: String, val retry: (() -> Unit)? = null) : UiState<Nothing>

    /** Có dữ liệu. */
    data class Content<T>(val data: T) : UiState<T>
}

/**
 * Hiển thị [state] theo đúng 4 nhánh. Màn hình chỉ cần viết nhánh [UiState.Content].
 *
 * @param errorHint câu gợi ý dưới thông báo lỗi (mặc định nói về kết nối Odoo).
 * @param errorActionLabel nhãn nút hành động của nhánh lỗi ("Thử lại").
 * @param secondaryActionLabel hành động THỨ HAI ở nhánh lỗi. Dùng cho đường lui bắt buộc của
 *        Luật 8: đang dùng **Odoo thật** mà lỗi ⇒ luôn có nút "Về dữ liệu mẫu" để demo
 *        không bao giờ rơi vào màn chết. Màn CAPTURE dùng nút này cho "Sửa lại thông tin
 *        (giữ dữ liệu)" — vì ở đó dữ liệu đã nhập phải còn nguyên sau khi lỗi.
 * @param emptyActionLabel hành động của nhánh [UiState.Empty] (vd "Chọn ảnh Hàng hoá").
 *        Không có ⇒ khối rỗng chỉ giải thích, không có nút (hành vi cũ, không phá caller cũ).
 */
@Composable
fun <T> StateHost(
    state: UiState<T>,
    modifier: Modifier = Modifier,
    emptyTitle: String,
    emptyHint: String = "Bấm R để tải lại",
    emptyActionLabel: String? = null,
    onEmptyAction: (() -> Unit)? = null,
    emptySecondaryActionLabel: String? = null,
    onEmptySecondaryAction: (() -> Unit)? = null,
    loadingLabel: String = "Đang tải…",
    errorHint: String = "Kiểm tra kết nối tới máy chủ Odoo rồi thử lại.",
    errorActionLabel: String = "Thử lại",
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        UiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AppTheme.lab.accent)
                Text(
                    text = loadingLabel,
                    style = MaterialTheme.typography.body2,
                    color = AppTheme.lab.ink2,
                    modifier = Modifier.padding(top = Tokens.s3),
                )
            }
        }

        UiState.Empty -> LabEmptyState(
            title = emptyTitle,
            hint = emptyHint,
            actionLabel = emptyActionLabel ?: "Tải lại",
            onAction = onEmptyAction,
            secondaryLabel = emptySecondaryActionLabel,
            onSecondary = onEmptySecondaryAction,
            modifier = modifier,
        )

        is UiState.Error -> LabEmptyState(
            title = state.message,
            hint = errorHint,
            actionLabel = errorActionLabel,
            onAction = state.retry,
            secondaryLabel = secondaryActionLabel,
            onSecondary = onSecondaryAction,
            tone = Tone.Bad,
            modifier = modifier,
        )

        is UiState.Content<*> -> {
            // ⚠️ PHẢI bọc trong Box(modifier): trước đây nhánh này gọi `content(...)` trực tiếp
            // nên `modifier` (mọi màn đều truyền `Modifier.weight(1f)`) BỊ BỎ QUA. Hệ quả đo thật
            // 2026-10-29 trên AVD 720×1280 (360×640 dp), màn "Chuyển tài khoản": LazyColumn được
            // đo bằng cả chiều cao còn lại (không phải phần đã chia) nên đẩy thanh dưới cùng
            // (nút "Chọn tài khoản ▾" + "Chuyển", cao 160dp) ra NGOÀI màn hình — logcat:
            // `LABDBG availH=486.0` · `LABDBG bar=273x624` nhưng thanh không hề hiện.
            // Bọc Box(modifier) ⇒ `weight(1f)` có hiệu lực ⇒ phần tử sau StateHost luôn thấy được.
            // UiState<out T> bị xoá kiểu khi runtime → cần ép lại về T (an toàn vì chỉ có
            // một lớp Content và `data` mang đúng T mà caller đã tạo).
            Box(modifier) {
                @Suppress("UNCHECKED_CAST")
                content(state.data as T)
            }
        }
    }
}
