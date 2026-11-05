package vn.lab.logistics.feature.cs

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.lab.logistics.core.model.AssignOutcome
import vn.lab.logistics.core.model.CsDriver
import vn.lab.logistics.core.model.CsJob
import vn.lab.logistics.core.model.CsJobsPayload
import vn.lab.logistics.core.model.JobState
import vn.lab.logistics.core.net.toVietnameseMessage
import vn.lab.logistics.core.repo.CsJobsRepo
import vn.lab.logistics.feature.driver.stateLabelOf
import vn.lab.logistics.ui.components.UiState

/**
 * =====================================================================================
 * ViewModel màn **CS — Điều phối** — NƠI GIỮ STATE, để duo UI sống sót khi kéo cửa sổ.
 *
 * DISPLACEMENT (docs/04 §4): khi bề rộng đổi ngưỡng, `DuoScaffold` đổi nhánh layout → subtree
 * bị dispose. Ba thứ sau PHẢI nằm ở đây, KHÔNG được `remember` trong pane:
 *   1. [listState]        — vị trí scroll danh sách;
 *   2. [openedId]         — việc đang mở ở pane chi tiết (quyết định overlay có hiện hay không);
 *   3. [selectedDriverId] — tài xế đang chọn trong ô chọn (kéo cửa sổ không được mất lựa chọn).
 *
 * THAO TÁC GHI duy nhất: [assign] → `POST /lab/api/v1/cs/jobs/<id>/assign`.
 * Sau khi server trả `{state, stateLabel, driverName}`, ViewModel **áp state của server** vào
 * item (app KHÔNG tự đoán trạng thái), phát [notice] cho Snackbar tiếng Việt, và KHÔNG tải lại
 * danh sách — CS cần thấy ngay việc vừa gán để đổi tài xế nếu cần.
 *
 * Không giữ `CoroutineScope` riêng (lý do: `Dispatchers.Main` cần coroutines-swing — build
 * offline không có). Caller truyền scope của Compose.
 * =====================================================================================
 */
class CsDispatchViewModel(
    private val repo: CsJobsRepo,
) {

    /** Vị trí scroll — state của CALLER, truyền vào `LazyColumn(state = …)`. */
    val listState: LazyListState = LazyListState()

    var state by mutableStateOf<UiState<CsJobsPayload>>(UiState.Loading)
        private set

    /** Việc đang mở ở pane chi tiết. `null` = chưa mở (overlay ẩn). */
    var openedId by mutableStateOf<Int?>(null)
        private set

    /** Tài xế đang chọn trong ô chọn tài xế của pane chi tiết. */
    var selectedDriverId by mutableStateOf<Int?>(null)
        private set

    /** Đang gọi API gán cho việc nào (khoá nút — chống bấm 2 lần sinh 2 request). */
    var busyJobId by mutableStateOf<Int?>(null)
        private set

    /** Thông báo MỘT LẦN cho Snackbar. Màn hình đọc rồi gọi [clearNotice]. */
    var notice by mutableStateOf<String?>(null)
        private set

    /** Lỗi của thao tác gán — hiện trong khối cảnh báo ở pane chi tiết. */
    var actionError by mutableStateOf<String?>(null)
        private set

    private var loadedOnce: Boolean = false

    /** Toàn bộ `data` đã tải (items + drivers). */
    val payload: CsJobsPayload
        get() = (state as? UiState.Content)?.data ?: CsJobsPayload()

    val jobs: List<CsJob> get() = payload.items

    /**
     * Chip lọc đang chọn. Nằm ở ViewModel (không `remember` trong pane) để kéo cửa sổ làm
     * duo UI đổi nhánh layout cũng không làm mất lựa chọn.
     */
    var filter by mutableStateOf(CsJobFilter.ALL)
        private set

    /** Danh sách đang hiển thị: đã lọc theo [filter], việc tạo mới nhất xếp đầu. */
    val visibleJobs: List<CsJob> get() = filterAndSort(jobs, filter)

    fun selectFilter(newFilter: CsJobFilter) {
        filter = newFilter
    }

    val drivers: List<CsDriver> get() = payload.drivers

    /** Việc đang hiển thị ở pane chi tiết. */
    fun detailJob(): CsJob? {
        val id = openedId ?: return null
        return jobs.firstOrNull { it.id == id }
    }

    /** Tài xế đang chọn (null = chưa chọn ⇒ nút "Gán tài xế" bị khoá). */
    fun selectedDriver(): CsDriver? = drivers.firstOrNull { it.id == selectedDriverId }

    /**
     * Tài xế ĐANG phụ trách việc đang mở.
     *
     * Server trả `driverName` (chuỗi), không trả id ⇒ khớp về id trong danh sách tài xế
     * ([driverIdMatching]). `null` = việc chưa có tài xế.
     */
    val assignedDriverId: Int?
        get() = driverIdMatching(drivers, detailJob()?.driverName)

    /**
     * Việc đang mở đã gán ĐÚNG tài xế đang chọn hay chưa.
     *
     * VÌ SAO CẦN (yêu cầu 2026-11-02): trước đây gán xong, Snackbar báo "Đã gán …" nhưng nút vẫn
     * sáng "Gán tài xế" ⇒ người dùng bấm lại, tưởng máy chưa ăn. Giờ: đúng cặp (việc, tài xế) rồi
     * thì nút chuyển thành **"Đã gán"** và **bị khoá**.
     *
     * Vẫn cho giao LẠI: chỉ cần chọn tài xế khác trong ô chọn, [assignedDriverId] khác
     * [selectedDriverId] ⇒ nút bật lại bình thường.
     */
    val daGanChoDriverDangChon: Boolean
        get() {
            val job = detailJob() ?: return false
            val dangChon = selectedDriverId ?: return false
            if (job.driverName.isNullOrBlank()) return false
            return assignedDriverId == dangChon
        }

    fun clearNotice() {
        notice = null
    }

    fun clearActionError() {
        actionError = null
    }

    // ==================================================================================
    // Tải dữ liệu
    // ==================================================================================

    /** Tải danh sách (nút "Tải lại"). Refresh KHÔNG được làm mất việc đang mở (checklist §7). */
    fun load(scope: CoroutineScope) = fetch(scope, showLoading = true)

    /** Tải LẦN ĐẦU khi màn hình vừa mở (vào lại màn không "nháy trắng"). */
    fun loadIfNeeded(scope: CoroutineScope) {
        if (!loadedOnce) load(scope)
    }

    private fun fetch(scope: CoroutineScope, showLoading: Boolean) {
        loadedOnce = true
        if (showLoading) state = UiState.Loading
        scope.launch {
            val result = repo.jobs()
            result.fold(
                onSuccess = { payload -> applyLoaded(payload) },
                onFailure = { error ->
                    state = UiState.Error(
                        message = error.message ?: "Không tải được danh sách việc cần gán tài xế.",
                        retry = { load(scope) },
                    )
                },
            )
        }
    }

    /**
     * Áp payload vừa tải: giữ việc đang mở nếu nó còn trong danh sách; danh sách rỗng ⇒
     * [UiState.Empty] (màn hình hiện empty state, không phải màn trắng).
     */
    private fun applyLoaded(payload: CsJobsPayload) {
        if (payload.items.isEmpty()) {
            openedId = null
            selectedDriverId = null
            state = UiState.Empty
            return
        }
        // Mặc định mở việc MỚI NHẤT (đầu danh sách hiển thị), cùng thứ tự với `visibleJobs`.
        val giuLai = payload.items.firstOrNull { it.id == openedId }?.id
            ?: sortNewestFirst(payload.items).first().id
        openedId = giuLai
        // Chỉ chọn lại tài xế khi lựa chọn cũ không còn trong danh sách (đừng cướp lựa chọn
        // người dùng vừa bấm).
        if (payload.drivers.none { it.id == selectedDriverId }) {
            selectedDriverId = driverIdMatching(
                drivers = payload.drivers,
                name = payload.items.firstOrNull { it.id == giuLai }?.driverName,
            )
        }
        state = UiState.Content(payload)
    }

    // ==================================================================================
    // Chọn việc / chọn tài xế
    // ==================================================================================

    /** Mở một việc: mở pane chi tiết và chọn sẵn tài xế đang phụ trách (nếu có). */
    fun open(id: Int) {
        actionError = null
        if (openedId != id) {
            openedId = id
            selectedDriverId = driverIdMatching(
                drivers = drivers,
                name = jobs.firstOrNull { it.id == id }?.driverName,
            )
        } else {
            openedId = id
        }
    }

    /** Đóng pane chi tiết nhưng GIỮ [listState] — điểm quay về khi đóng overlay. */
    fun closeDetail() {
        openedId = null
        actionError = null
    }

    /** Chọn tài xế trong ô chọn. */
    fun selectDriver(driverId: Int?) {
        selectedDriverId = driverId
        actionError = null
    }

    // ==================================================================================
    // THAO TÁC GHI: gán tài xế
    // ==================================================================================

    /**
     * Gán tài xế đang chọn cho việc đang mở.
     *
     * Chặn Ở APP khi chưa chọn tài xế: gọi server với `driverId` rỗng chỉ làm CS tưởng đã gán.
     * Sau đó server là nguồn sự thật duy nhất của trạng thái ([AssignOutcome]).
     */
    fun assign(scope: CoroutineScope) {
        val job = detailJob() ?: return
        if (busyJobId != null) return

        val driver = selectedDriver()
        if (driver == null) {
            actionError = "Chọn tài xế trước khi bấm \"Gán tài xế\"."
            return
        }

        actionError = null
        busyJobId = job.id
        scope.launch {
            val result = repo.assign(job.id, driver.id)
            busyJobId = null
            result.fold(
                onSuccess = { outcome ->
                    applyAssign(job.id, outcome, driver.name)
                    notice = "Đã gán ${driver.name} cho ${job.ref} — ${nhanMoi(outcome)}."
                },
                onFailure = { error -> actionError = error.toVietnameseMessage() },
            )
        }
    }

    /** Áp state server trả vào item đang hiển thị (KHÔNG tự đoán state). */
    private fun applyAssign(jobId: Int, outcome: AssignOutcome, fallbackDriverName: String) {
        val current = jobs
        if (current.isEmpty()) return
        val updated = current.map { job ->
            if (job.id != jobId) {
                job
            } else {
                job.copy(
                    state = outcome.state ?: JobState.ASSIGNED,
                    // `null` ⇒ xoá nhãn cũ để UI suy lại từ mã state ("Đã gán tài xế").
                    stateLabel = outcome.stateLabel,
                    driverName = outcome.driverName ?: fallbackDriverName,
                )
            }
        }
        state = UiState.Content(payload.copy(items = updated))
    }

    /** Nhãn trạng thái mới để đọc trong Snackbar (ưu tiên câu của server). */
    private fun nhanMoi(outcome: AssignOutcome): String =
        outcome.stateLabel ?: stateLabelOf(outcome.state ?: JobState.ASSIGNED)

    /**
     * Tìm id tài xế khớp với `driverName` server trả. So khớp chặt trước (bằng nhau), rồi mới
     * lỏng (chứa nhau) vì `driver_name` của Odoo đôi khi kèm hậu tố như "(tài xế)".
     */
    private fun driverIdMatching(drivers: List<CsDriver>, name: String?): Int? {
        val target = name?.trim()?.lowercase()
        if (target.isNullOrEmpty()) return null
        drivers.firstOrNull { it.name.trim().lowercase() == target }?.let { return it.id }
        return drivers.firstOrNull { driver ->
            val ten = driver.name.trim().lowercase()
            ten.isNotEmpty() && (ten.contains(target) || target.contains(ten))
        }?.id
    }
}
