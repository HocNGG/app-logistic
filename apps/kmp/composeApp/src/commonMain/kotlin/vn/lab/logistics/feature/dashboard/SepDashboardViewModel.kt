package vn.lab.logistics.feature.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.lab.logistics.core.model.KpiRange
import vn.lab.logistics.core.model.SepKpi
import vn.lab.logistics.core.repo.SepDashboardRepo
import vn.lab.logistics.ui.components.UiState

/**
 * =====================================================================================
 * ViewModel của màn **Sếp · Tổng quan** (`manager` / `sep`).
 *
 * BỐN TRẠNG THÁI (Luật 7, `agents/fe/AGENT.md` §3): Empty (chưa tải) · Loading (đang lấy số liệu)
 * · Error (câu tiếng Việt + Thử lại) · Content (có [SepKpi]).
 *
 * Quy tắc:
 *  - Đổi khoảng thời gian ⇒ gọi lại server với `range` mới (server tính theo kỳ, app KHÔNG tự
 *    cộng trừ số của kỳ khác — đó là cách chắc chắn nhất để số trên màn khớp Odoo).
 *  - Bấm cùng một chip đang chọn ⇒ không gọi lại (tránh nhấp nháy + tải vô ích).
 *  - Không có polling/auto-refresh: số liệu tài chính không tự đổi sau lưng người xem.
 * =====================================================================================
 */
class SepDashboardViewModel(
    private val repo: SepDashboardRepo,
) {

    /** Khoảng thời gian đang chọn (mặc định "Hôm nay"). */
    var range: KpiRange by mutableStateOf(KpiRange.DEFAULT)
        private set

    var state: UiState<SepKpi> by mutableStateOf(UiState.Empty)
        private set

    val isLoading: Boolean get() = state is UiState.Loading

    /** Chọn khoảng thời gian rồi tải lại nếu thực sự đổi kỳ. */
    fun selectRange(next: KpiRange, scope: CoroutineScope) {
        if (next == range && state is UiState.Content) return
        range = next
        load(scope)
    }

    /** Tải số liệu của [range] hiện tại. */
    fun load(scope: CoroutineScope) {
        if (state is UiState.Loading) return
        state = UiState.Loading
        scope.launch {
            val result = repo.kpi(range)
            state = result.fold(
                onSuccess = { data ->
                    // Server trả về đúng khoảng đang chọn; nếu payload rỗng hoàn toàn vẫn là
                    // Content (mỗi khối tự hiện "Chưa có dữ liệu"), KHÔNG phải Empty của cả màn:
                    // màn vẫn phải hiện toolbar + các khối để người dùng đổi kỳ khác.
                    UiState.Content(data)
                },
                onFailure = { error ->
                    UiState.Error(
                        message = error.message
                            ?: "Không lấy được số liệu dashboard. Kiểm tra kết nối rồi thử lại.",
                        retry = { load(scope) },
                    )
                },
            )
        }
    }
}
