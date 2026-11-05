package vn.lab.logistics.feature.driver

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.lab.logistics.core.model.DriverJob
import vn.lab.logistics.core.net.toVietnameseMessage
import vn.lab.logistics.core.repo.DriverJobsRepo
import vn.lab.logistics.core.repo.DriverWorkRepo
import vn.lab.logistics.core.repo.JobStateChange
import vn.lab.logistics.core.repo.MIN_FAIL_REASON_LENGTH
import vn.lab.logistics.core.repo.isFailReasonValid
import vn.lab.logistics.ui.components.UiState

/**
 * =====================================================================================
 * ViewModel màn "Việc hôm nay" — NƠI GIỮ STATE, để duo UI sống sót khi kéo cửa sổ.
 *
 * DISPLACEMENT (docs/04 §4, skill 03 §4): khi bề rộng đổi ngưỡng, `DuoScaffold` đổi nhánh
 * layout → subtree bị dispose. Ba thứ sau PHẢI nằm ở đây, KHÔNG được `remember` trong pane:
 *   1. [listState] — vị trí scroll của danh sách (tạo MỘT lần cho cả vòng đời màn hình)
 *   2. [cursorId]  — dòng đang trỏ (bàn phím J/K hoặc click)
 *   3. [openedId]  — việc đang mở ở pane chi tiết (quyết định overlay có hiện hay không)
 * Cùng với [searchQuery] (text đang gõ) → kéo 1600 → 700 → 1600 dp không mất ngữ cảnh.
 *
 * WAVE "CAPTURE" — hai thao tác GHI nằm ở đây, không ở Composable:
 *   - [accept] : "Bắt đầu đi"  → `POST …/accept` (controller: `action_assign` + `action_start`)
 *   - [fail]   : "Không lấy được" → `POST …/fail` (controller: `action_fail(ly_do=…)`)
 * Sau khi server trả state mới, ViewModel **áp state đó** rồi tải lại danh sách (nguồn sự thật
 * vẫn là server). Màn hình chỉ việc vẽ `UiState` + Snackbar từ [notice]/[actionError].
 *
 * Không giữ `CoroutineScope` riêng (lý do: `Dispatchers.Main` cần coroutines-swing — build
 * offline không có). Caller truyền scope của Compose.
 * =====================================================================================
 */
class DriverJobsViewModel(
    private val repo: DriverJobsRepo,
    private val work: DriverWorkRepo,
) {

    /** Vị trí scroll — state của CALLER, truyền vào `LazyColumn(state = ...)`. */
    val listState: LazyListState = LazyListState()

    var state by mutableStateOf<UiState<List<DriverJob>>>(UiState.Loading)
        private set

    /** Dòng đang trỏ tới (viền focus). */
    var cursorId by mutableStateOf<Int?>(null)
        private set

    /** Việc đang mở ở pane chi tiết. `null` = chưa mở (overlay ẩn). */
    var openedId by mutableStateOf<Int?>(null)
        private set

    /** Text đang gõ ở ô tìm — state lifted để không mất khi đổi ngưỡng. */
    var searchQuery by mutableStateOf("")
        private set

    /** Ô tìm đang có focus → chặn phím tắt J/K để không "nuốt" chữ người dùng đang gõ. */
    var searchFocused by mutableStateOf(false)

    /** Đang gửi lệnh ghi cho việc nào (để khoá nút — chống bấm 2 lần làm 2 request). */
    var busyJobId by mutableStateOf<Int?>(null)
        private set

    /** Thông báo MỘT LẦN cho Snackbar. Màn hình đọc rồi gọi [clearNotice]. */
    var notice by mutableStateOf<String?>(null)
        private set

    /** Lỗi của thao tác ghi (accept/fail) — hiện trong khối cảnh báo ở pane chi tiết. */
    var actionError by mutableStateOf<String?>(null)
        private set

    /** Đã tải danh sách ít nhất một lần chưa (để vào lại màn không "nháy trắng" — xem [loadIfNeeded]). */
    private var loadedOnce: Boolean = false

    /** Toàn bộ việc (đã tải được). */
    val jobs: List<DriverJob>
        get() = (state as? UiState.Content)?.data ?: emptyList()

    /** Việc hiển thị sau khi lọc theo [searchQuery]. */
    fun visibleJobs(): List<DriverJob> {
        val query = searchQuery.trim()
        if (query.isEmpty()) return jobs
        return jobs.filter { job ->
            listOfNotNull(
                job.ref,
                job.shop,
                job.address,
                job.contactName,
                job.contactPhone,
                job.note,
            ).any { it.contains(query, ignoreCase = true) }
        }
    }

    /** Việc đang hiển thị ở pane chi tiết (ưu tiên việc đã mở, sau đó tới dòng đang trỏ). */
    fun detailJob(): DriverJob? {
        val id = openedId ?: cursorId ?: return null
        return jobs.firstOrNull { it.id == id }
    }

    fun onSearchChange(value: String) {
        searchQuery = value
    }

    fun clearSearch() {
        searchQuery = ""
    }

    fun clearNotice() {
        notice = null
    }

    fun clearActionError() {
        actionError = null
    }

    /**
     * Tải danh sách. Refresh KHÔNG được reset lựa chọn/scroll (checklist AGENTS/agents-fe §7):
     * nếu việc đang chọn còn tồn tại trong dữ liệu mới thì giữ nguyên.
     */
    fun load(scope: CoroutineScope) = fetch(scope, showLoading = true)

    /**
     * Tải LẦN ĐẦU khi màn hình vừa mở.
     *
     * Vì sao không phải `load()` mỗi lần vào màn: quay về từ màn CAPTURE thì `App.kt` đã tải lại
     * danh sách (để thấy state mới). Nếu màn hình lại `load()` lần nữa, danh sách "nháy trắng"
     * đúng lúc Snackbar "Đã gửi biên nhận…" hiện ra — xấu và làm người xem tưởng mất dữ liệu.
     */
    fun loadIfNeeded(scope: CoroutineScope) {
        if (!loadedOnce) load(scope)
    }

    /**
     * Tải lại NGẦM sau một thao tác ghi: không đặt [UiState.Loading] để danh sách không
     * "nháy trắng" ngay sau khi tài xế bấm Gửi (và không mất việc đang mở).
     */
    private fun reload(scope: CoroutineScope) = fetch(scope, showLoading = false)

    private fun fetch(scope: CoroutineScope, showLoading: Boolean) {
        loadedOnce = true
        if (showLoading) state = UiState.Loading
        scope.launch {
            val result = repo.todayJobs()
            state = result.fold(
                onSuccess = { list ->
                    keepSelectionIn(list)
                    if (list.isEmpty()) UiState.Empty else UiState.Content(list)
                },
                onFailure = { error ->
                    UiState.Error(
                        message = error.message ?: "Không tải được danh sách việc hôm nay.",
                        retry = { load(scope) },
                    )
                },
            )
        }
    }

    // ==================================================================================
    // THAO TÁC GHI (C4) — nguồn dữ liệu do [work] quyết định: Odoo thật hay chế độ mẫu.
    // ==================================================================================

    /**
     * "Bắt đầu đi": `new → assigned → en_route` phía server (controller tự gọi đủ 2 `action_*`).
     *
     * Sau khi thành công: áp state server trả, phát [notice] cho Snackbar, rồi tải lại danh sách.
     * Việc đang `en_route` thì pane chi tiết hiện nút "Chụp ảnh & ký biên nhận" (C4).
     */
    fun accept(jobId: Int, scope: CoroutineScope) {
        if (busyJobId != null) return
        actionError = null
        busyJobId = jobId
        scope.launch {
            val result = work.accept(jobId)
            busyJobId = null
            result.fold(
                onSuccess = { change ->
                    applyStateChange(change)
                    notice = "Đã bắt đầu đi — ${refOf(jobId)} · ${labelOf(change, jobId)}. " +
                        "Tới shop thì bấm \"Chụp ảnh & ký biên nhận\"."
                    reload(scope)
                },
                onFailure = { error -> actionError = error.toVietnameseMessage() },
            )
        }
    }

    /**
     * "Không lấy được": lý do bắt buộc ≥ [MIN_FAIL_REASON_LENGTH] ký tự.
     *
     * Chặn Ở APP trước rồi mới gọi server: backend chỉ kiểm "không rỗng", còn nghiệp vụ của
     * dự án đòi lý do đọc được để CS xử lý (docs/07 ngoại lệ "shop đóng cửa").
     */
    fun fail(jobId: Int, reason: String, scope: CoroutineScope) {
        if (busyJobId != null) return
        if (!isFailReasonValid(reason)) {
            actionError = "Phải nhập lý do không lấy được hàng (ít nhất " +
                "$MIN_FAIL_REASON_LENGTH ký tự)."
            return
        }
        actionError = null
        busyJobId = jobId
        scope.launch {
            val result = work.fail(jobId, reason.trim())
            busyJobId = null
            result.fold(
                onSuccess = { change ->
                    applyStateChange(change)
                    notice = "Đã báo không lấy được ${refOf(jobId)} — ${labelOf(change, jobId)}. " +
                        "CS đã thấy lý do bạn nhập."
                    reload(scope)
                },
                onFailure = { error -> actionError = error.toVietnameseMessage() },
            )
        }
    }

    /** Áp state server trả vào danh sách đang hiển thị (KHÔNG tự đoán state). */
    private fun applyStateChange(change: JobStateChange) {
        val current = jobs
        if (current.isEmpty()) return
        val updated = current.map { job ->
            if (job.id != change.jobId) {
                job
            } else {
                job.copy(
                    state = change.state ?: job.state,
                    stateLabel = change.stateLabel ?: job.stateLabel,
                )
            }
        }
        state = UiState.Content(updated)
    }

    /** Mã việc để hiện trong Snackbar (fallback `#id` nếu danh sách chưa có). */
    private fun refOf(jobId: Int): String = jobs.firstOrNull { it.id == jobId }?.ref ?: "#$jobId"

    /** Nhãn tiếng Việt của state mới: ưu tiên server, fallback bảng nhãn của app. */
    private fun labelOf(change: JobStateChange, jobId: Int): String =
        change.stateLabel
            ?: change.state?.let { stateLabelOf(it) }
            ?: jobs.firstOrNull { it.id == jobId }?.statusLabel()
            ?: "đã cập nhật"

    // ==================================================================================
    // Điều hướng trong danh sách
    // ==================================================================================

    /** Mở một việc: đặt con trỏ và mở pane chi tiết (ở overlay = hiện pane phủ). */
    fun open(id: Int) {
        cursorId = id
        openedId = id
    }

    /**
     * Đóng pane chi tiết nhưng GIỮ [cursorId] và [listState] — đây là điểm quay về
     * (docs/04 §4 "giữ scroll danh sách" khi đóng overlay).
     */
    fun closeDetail() {
        openedId = null
    }

    /**
     * Di chuyển con trỏ trong danh sách ĐANG HIỂN THỊ (sau lọc) và cuộn tới dòng đó.
     * @param syncDetail `true` ở split (chi tiết đi theo con trỏ), `false` ở overlay
     *        (chỉ mở chi tiết khi người dùng bấm Enter) — tránh mở overlay liên tục khi gõ J/K.
     */
    fun moveCursor(delta: Int, syncDetail: Boolean, scope: CoroutineScope) {
        val list = visibleJobs()
        if (list.isEmpty()) return
        val current = list.indexOfFirst { it.id == cursorId }
        val index = ((if (current < 0) 0 else current) + delta).coerceIn(0, list.lastIndex)
        cursorId = list[index].id
        if (syncDetail) openedId = cursorId
        scope.launch { listState.animateScrollToItem(index) }
    }

    private fun keepSelectionIn(list: List<DriverJob>) {
        if (list.isEmpty()) {
            cursorId = null
            openedId = null
            return
        }
        val first = list.first().id
        val keepCursor = list.firstOrNull { it.id == cursorId }?.id ?: first
        cursorId = keepCursor
        openedId = list.firstOrNull { it.id == openedId }?.id ?: keepCursor
    }
}
