package vn.lab.logistics.feature.admin

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.lab.logistics.core.model.DemoAccount
import vn.lab.logistics.core.net.LoginSession
import vn.lab.logistics.core.net.toVietnameseMessage
import vn.lab.logistics.core.repo.AdminAccountsRepo
import vn.lab.logistics.ui.components.UiState

/**
 * =====================================================================================
 * ViewModel màn **CHUYỂN TÀI KHOẢN** — nơi giữ state, để danh sách và lựa chọn sống sót khi
 * kéo cửa sổ đổi ngưỡng layout (DISPLACEMENT, docs/04 §4).
 *
 * Ba thứ PHẢI nằm ở đây, không được `remember` trong pane:
 *   1. [listState]      — vị trí scroll danh sách tài khoản;
 *   2. [selectedLogin]  — tài khoản đang chọn (kéo cửa sổ không được mất lựa chọn);
 *   3. [searchQuery]    — chữ đang gõ ở ô tìm nhanh.
 *
 * THAO TÁC GHI duy nhất: [switchTo] → `POST /lab/api/v1/admin/switch`. Hàm này phục vụ CẢ HAI lối
 * chuyển vai trò (dropdown ở thanh trên cùng và nút "Chuyển" của màn chi tiết) nên trạng thái
 * "Đang chuyển…"/lỗi luôn khớp nhau. Thành công thì trả [LoginSession] mới cho CALLER (`App.kt`)
 * — ViewModel KHÔNG tự ghi Settings và KHÔNG tự điều hướng: hai việc đó là của nơi lắp ráp, đúng
 * như toàn bộ app (màn hình không biết về Settings).
 *
 * Không giữ `CoroutineScope` riêng (lý do: `Dispatchers.Main` cần coroutines-swing — build
 * offline không có). Caller truyền scope của Compose.
 * =====================================================================================
 */
class SwitchAccountViewModel(
    private val repo: AdminAccountsRepo,
) {

    /** Vị trí scroll — state của CALLER, truyền vào `LazyColumn(state = …)`. */
    val listState: LazyListState = LazyListState()

    var state by mutableStateOf<UiState<List<DemoAccount>>>(UiState.Loading)
        private set

    /** Ô tìm nhanh (tên / tài khoản / mã vai trò). */
    var searchQuery by mutableStateOf("")
        private set

    /** Tài khoản đang chọn. `null` = chưa chọn (nút "Chuyển" bị khoá). */
    var selectedLogin by mutableStateOf<String?>(null)
        private set

    /** Đang gọi `switch` — khoá nút để bấm hai lần không sinh hai phiên. */
    var busy by mutableStateOf(false)
        private set

    /** Lỗi của thao tác chuyển — hiện trong khối cảnh báo + nút "Thử lại". */
    var actionError by mutableStateOf<String?>(null)
        private set

    private var loadedOnce: Boolean = false

    /** Toàn bộ tài khoản đã tải (chưa lọc). */
    val accounts: List<DemoAccount>
        get() = (state as? UiState.Content)?.data ?: emptyList()

    /** Danh sách đã lọc theo ô tìm nhanh — thứ tự server trả, không tự sắp xếp lại. */
    fun visibleAccounts(): List<DemoAccount> = filterAccounts(accounts, searchQuery)

    /** Tài khoản đang chọn (null = chưa chọn). */
    fun selectedAccount(): DemoAccount? = accounts.firstOrNull { it.login == selectedLogin }

    fun onSearchChange(value: String) {
        searchQuery = value
        actionError = null
    }

    fun clearSearch() {
        searchQuery = ""
    }

    fun select(login: String) {
        selectedLogin = login
        actionError = null
    }

    fun clearActionError() {
        actionError = null
    }

    // ==================================================================================
    // Tải dữ liệu
    // ==================================================================================

    /** Tải lại danh sách (nút "Tải lại"), giữ tài khoản đang chọn nếu còn trong danh sách. */
    fun load(scope: CoroutineScope) = fetch(scope, showLoading = true)

    /** Tải LẦN ĐẦU khi màn vừa mở (vào lại màn không "nháy trắng"). */
    fun loadIfNeeded(scope: CoroutineScope) {
        if (!loadedOnce) load(scope)
    }

    private fun fetch(scope: CoroutineScope, showLoading: Boolean) {
        loadedOnce = true
        if (showLoading) state = UiState.Loading
        scope.launch {
            val result = repo.accounts()
            result.fold(
                onSuccess = { items -> applyLoaded(items) },
                onFailure = { error ->
                    state = UiState.Error(
                        message = error.message ?: "Không tải được danh sách tài khoản demo.",
                        retry = { load(scope) },
                    )
                },
            )
        }
    }

    /** Danh sách rỗng ⇒ [UiState.Empty] (màn hình hiện empty state, không phải màn trắng). */
    private fun applyLoaded(items: List<DemoAccount>) {
        if (items.isEmpty()) {
            state = UiState.Empty
            selectedLogin = null
            return
        }
        // Giữ lựa chọn cũ nếu tài khoản đó còn trong danh sách mới (đừng cướp lựa chọn người dùng).
        if (items.none { it.login == selectedLogin }) selectedLogin = null
        state = UiState.Content(items)
    }

    // ==================================================================================
    // THAO TÁC GHI: chuyển tài khoản
    // ==================================================================================

    /**
     * Chuyển sang tài khoản [login] — ĐƯỜNG GỌI REPO DUY NHẤT của tính năng này.
     *
     * Vì sao nhận `login` (không chỉ dùng [selectedLogin]): từ nay có HAI lối chuyển vai trò —
     * nút dropdown ở thanh trên cùng (bấm 1 dòng là chuyển ngay) và nút "Chuyển" của màn chi tiết.
     * Cả hai gọi đúng hàm này ⇒ cùng một lời gọi `/admin/switch`, cùng trạng thái [busy] ("Đang
     * chuyển…") và cùng cơ chế báo lỗi. Không có đường thứ hai nào tự gọi repo.
     *
     * Thành công ⇒ giao [LoginSession] mới cho [onSwitched]; CALLER (`App.kt`) ghi `AppSettings`
     * rồi điều hướng — ViewModel không tự làm hai việc đó.
     */
    fun switchTo(login: String, scope: CoroutineScope, onSwitched: (LoginSession) -> Unit) {
        val dich = login.trim()
        if (dich.isEmpty()) {
            actionError = "Chọn một tài khoản trong danh sách trước khi chuyển."
            return
        }
        if (busy) return

        // Đồng bộ lựa chọn để màn chi tiết (nếu đang mở) chỉ đúng tài khoản vừa chuyển từ dropdown.
        selectedLogin = dich
        actionError = null
        busy = true
        scope.launch {
            val result = repo.switch(dich)
            busy = false
            result.fold(
                onSuccess = { session -> onSwitched(session) },
                onFailure = { error ->
                    actionError = error.toVietnameseMessage()
                },
            )
        }
    }
}
