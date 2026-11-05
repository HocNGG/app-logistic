package vn.lab.logistics.feature.kho

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.lab.logistics.core.model.JobState
import vn.lab.logistics.core.model.KienCuaViec
import vn.lab.logistics.core.model.KhoKien
import vn.lab.logistics.core.model.KhoJob
import vn.lab.logistics.core.model.ScanOutcome
import vn.lab.logistics.core.model.ScanResult
import vn.lab.logistics.core.net.toVietnameseMessage
import vn.lab.logistics.core.repo.KhoRepo
import vn.lab.logistics.feature.capture.newClientUuid
import vn.lab.logistics.ui.components.UiState
import vn.lab.logistics.ui.components.extractScanCode

/**
 * =====================================================================================
 * ViewModel màn **Kho — Nhận hàng** — nơi giữ state để duo UI sống sót khi kéo cửa sổ.
 *
 * [received] là thứ KHÔNG được mất: sau khi server xác nhận nhận hàng, việc đó phải hiện ngay
 * **đã nhận + số phiếu nhập kho**, kể cả khi pane chi tiết bị dispose do đổi bề rộng. Map này
 * lưu theo `jobId` nên refresh danh sách vẫn giữ đúng dấu "đã nhận" (server thật sẽ thôi trả
 * việc đó trong `cho-nhan`, và lúc đó dòng cũng tự biến mất — đúng nghiệp vụ).
 *
 * THAO TÁC GHI duy nhất: [nhan] → `POST /lab/api/v1/kho/jobs/<id>/nhan`.
 * Server sinh `stock.picking` nhập kho và trả `pickingName`; app **chỉ hiển thị** số đó
 * (Luật 4 REA: chứng từ do Odoo sinh, app không tự đánh số).
 * =====================================================================================
 */
class KhoReceiveViewModel(
    private val repo: KhoRepo,
) {

    /** Vị trí scroll — state của CALLER, truyền vào `LazyColumn(state = …)`. */
    val listState: LazyListState = LazyListState()

    var state by mutableStateOf<UiState<List<KhoJob>>>(UiState.Loading)
        private set

    /** Việc đang mở ở pane chi tiết. `null` = chưa mở (overlay ẩn). */
    var openedId by mutableStateOf<Int?>(null)
        private set

    /** Đang gọi API nhận cho việc nào (khoá nút — chống bấm 2 lần sinh 2 phiếu nhập kho). */
    var busyJobId by mutableStateOf<Int?>(null)
        private set

    /** Thông báo MỘT LẦN cho Snackbar (đọc rồi gọi [clearNotice]). */
    var notice by mutableStateOf<String?>(null)
        private set

    /** Lỗi của thao tác nhận — hiện trong khối cảnh báo ở danh sách. */
    var actionError by mutableStateOf<String?>(null)
        private set

    /** Việc đang mở Màn in tem QR nhận (`null` = đang đóng). Tự bật ngay sau khi bấm "Nhận hàng & in tem". */
    var printDialogJobId by mutableStateOf<Int?>(null)
        private set

    /** Việc đang mở Máy quét QR điện thoại (`null` = đang đóng). */
    var scannerDialogJobId by mutableStateOf<Int?>(null)
        private set

    /** `jobId` → số phiếu nhập kho của những việc ĐÃ NHẬN trong phiên này (rỗng = chưa có). */
    private val received = mutableStateMapOf<Int, String>()

    /** `jobId` → mã đơn bán (`S0xxxx`) do server trả khi bấm Nhận hàng. */
    private val saleOrderNames = mutableStateMapOf<Int, String>()

    private var loadedOnce: Boolean = false

    /** Bộ lọc đang chọn trên thanh chip: Tất cả · Đã lấy được hàng · Đã về kho. */
    var filter by mutableStateOf(KhoJobFilter.ALL)
        private set

    val jobs: List<KhoJob>
        get() = (state as? UiState.Content)?.data ?: emptyList()

    /** Danh sách việc sau khi lọc theo [filter] và xếp mã mới nhất lên đầu. */
    val visibleJobs: List<KhoJob>
        get() = filterAndSortKhoJobs(jobs, filter, ::isReceived)

    fun selectFilter(newFilter: KhoJobFilter) {
        filter = newFilter
    }

    /** Việc đang hiển thị ở pane chi tiết. */
    fun detailJob(): KhoJob? {
        val id = openedId ?: return null
        return jobs.firstOrNull { it.id == id }
    }

    /** Việc này đã được kho nhận (trong phiên này hoặc từ server ở trạng thái `confirmed` / có phiếu nhập). */
    fun isReceived(jobId: Int): Boolean {
        if (received.containsKey(jobId)) return true
        val job = jobs.firstOrNull { it.id == jobId } ?: return false
        return job.state == JobState.CONFIRMED || job.pickingLabelOrNull() != null
    }

    /**
     * Số phiếu nhập kho của việc: lấy kết quả phiên này trước, sau đó tới `pickingName` trong
     * payload (server đã có sẵn phiếu). `null` = chưa nhận / server chưa trả số phiếu.
     */
    fun pickingNameOf(jobId: Int): String? {
        received[jobId]?.takeIf { it.isNotBlank() }?.let { return it }
        return jobs.firstOrNull { it.id == jobId }?.pickingLabelOrNull()
    }

    /** Mã đơn bán (`S0xxxx`) của việc — ưu tiên kết quả vừa nhận, sau đó tới payload `cho-nhan`. */
    fun saleOrderNameOf(jobId: Int): String? {
        saleOrderNames[jobId]?.takeIf { it.isNotBlank() }?.let { return it }
        return jobs.firstOrNull { it.id == jobId }?.saleOrderLabelOrNull()
    }

    /**
     * Đã quét đủ kiện (hoặc đã bấm Hoàn tất nhận) cho [jobId] ⇒ khoá không cho quét tiếp.
     */
    fun isScanCompleted(jobId: Int): Boolean {
        val session = scanOf(jobId)
        if (session.duKien || session.daHoanTat) return true
        val job = jobs.firstOrNull { it.id == jobId } ?: return false
        return job.totalPackages > 0 && job.scannedPackages >= job.totalPackages
    }

    /** Số việc CÒN chờ nhận (đã nhận thì không tính) — hiện ở đầu danh sách. */
    fun pendingCount(): Int = jobs.count { !isReceived(it.id) }

    fun clearNotice() {
        notice = null
    }

    fun clearActionError() {
        actionError = null
    }

    // ==================================================================================
    // Tải dữ liệu
    // ==================================================================================

    /** Tải danh sách (nút "Tải lại"). Giữ việc đang mở và giữ dấu "đã nhận". */
    fun load(scope: CoroutineScope) = fetch(scope, showLoading = true)

    /** Tải LẦN ĐẦU khi màn hình vừa mở. */
    fun loadIfNeeded(scope: CoroutineScope) {
        if (!loadedOnce) load(scope)
    }

    private fun fetch(scope: CoroutineScope, showLoading: Boolean) {
        loadedOnce = true
        if (showLoading) state = UiState.Loading
        scope.launch {
            val result = repo.choNhan()
            result.fold(
                onSuccess = { list -> applyLoaded(list) },
                onFailure = { error ->
                    state = UiState.Error(
                        message = error.message ?: "Không tải được danh sách hàng chờ nhận.",
                        retry = { load(scope) },
                    )
                },
            )
        }
    }

    private fun applyLoaded(list: List<KhoJob>) {
        if (list.isEmpty()) {
            openedId = null
            state = UiState.Empty
            return
        }
        val sorted = sortKhoJobsNewestFirst(list)
        sorted.forEach { job ->
            if (job.totalPackages > 0) {
                val existing = scans[job.id]
                val daQuet = maxOf(existing?.daQuet ?: 0, job.scannedPackages)
                val tong = if ((existing?.tong ?: 0) > 0) existing!!.tong else job.totalPackages
                val duKien = tong > 0 && daQuet >= tong
                scans[job.id] = (existing ?: ScanSession()).copy(
                    daQuet = daQuet,
                    tong = tong,
                    daHoanTat = (existing?.daHoanTat == true) || duKien,
                )
            }
        }
        openedId = sorted.firstOrNull { it.id == openedId }?.id ?: sorted.first().id
        state = UiState.Content(sorted)
    }

    // ==================================================================================
    // Chọn việc
    // ==================================================================================

    fun open(id: Int) {
        openedId = id
        actionError = null
    }

    fun closeDetail() {
        openedId = null
        actionError = null
        printDialogJobId = null
        scannerDialogJobId = null
    }

    fun openPrintDialog(jobId: Int) {
        printDialogJobId = jobId
        scannerDialogJobId = null
    }

    fun closePrintDialog() {
        printDialogJobId = null
    }

    fun openScannerDialog(jobId: Int) {
        scannerDialogJobId = jobId
        printDialogJobId = null
    }

    fun closeScannerDialog() {
        scannerDialogJobId = null
    }

    fun switchPrintToScanner(jobId: Int) {
        printDialogJobId = null
        scannerDialogJobId = jobId
    }

    // ==================================================================================
    // THAO TÁC GHI: nhận hàng
    // ==================================================================================

    /**
     * Kho xác nhận đã nhận hàng cho [jobId].
     *
     * Bất biến: mỗi việc chỉ nhận MỘT lần trong phiên (chống bấm 2 lần sinh 2 phiếu nhập kho) —
     * chặn ở app bằng [isReceived] + [busyJobId]; server vẫn là nơi kiểm tra state cuối cùng.
     */
    fun nhan(jobId: Int, scope: CoroutineScope) {
        if (busyJobId != null || isReceived(jobId)) return
        val job = jobs.firstOrNull { it.id == jobId } ?: return

        actionError = null
        busyJobId = jobId
        scope.launch {
            val result = repo.nhan(jobId)
            busyJobId = null
            result.fold(
                onSuccess = { outcome ->
                    received[jobId] = outcome.pickingName.orEmpty()
                    outcome.saleOrderName?.takeIf { it.isNotBlank() }?.let {
                        saleOrderNames[jobId] = it
                    }
                    // Tự mở pane chi tiết + tự đưa vào màn in tem QR nhận để thủ kho in & sang Bước 7 quét QR.
                    openedId = jobId
                    printDialogJobId = jobId
                    if (scans[jobId]?.chuaNhan == true) {
                        updateScan(jobId) { it.copy(kienView = null, kienLoading = false, error = null) }
                    }
                    val donBanText = outcome.saleOrderName?.takeIf { it.isNotBlank() }?.let { " · đơn bán $it" }.orEmpty()
                    notice = if (outcome.pickingName != null) {
                        "Đã nhận ${job.ref} — phiếu nhập kho ${outcome.pickingName}$donBanText."
                    } else {
                        "Đã nhận ${job.ref} — ${outcome.stateLabel ?: "đã chuyển sang đã nhận"}. " +
                            "Máy chủ chưa trả số phiếu nhập kho."
                    }
                },
                onFailure = { error -> actionError = error.toVietnameseMessage() },
            )
        }
    }

    // ==================================================================================
    // QUÉT TEM KIỆN (mốc "nhan") — chốt 2026-11-02
    // ==================================================================================

    /**
     * Trạng thái quét của MỘT việc. Giữ trong ViewModel (map theo `jobId`) chứ không `remember`
     * trong composable: đổi bề rộng màn hình làm subtree bị dispose, `remember` sẽ xoá sạch danh
     * sách kiện đã quét — người dùng tưởng mất dữ liệu và quét lại từ đầu.
     */
    data class ScanSession(
        /**
         * Kiện của việc + tiến độ do SERVER đếm (`GET /kho/jobs/<id>/kien`).
         * `null` = chưa tải xong / tải lỗi — KHÁC với "việc chưa nhận" (`kienView.chuaNhan`).
         */
        val kienView: KienCuaViec? = null,
        val kienLoading: Boolean = false,
        /** Các lần quét, MỚI NHẤT ở đầu (người quét nhìn kết quả vừa rồi trước). */
        val lines: List<ScanResult> = emptyList(),
        val daQuet: Int = 0,
        val tong: Int = 0,
        val input: String = "",
        val busy: Boolean = false,
        val error: String? = null,
        val daHoanTat: Boolean = false,
    ) {
        /** Đã quét đủ số kiện cần thiết (chỉ tin khi server đã cho biết `tong`). */
        val duKien: Boolean get() = tong > 0 && daQuet >= tong

        /** Tỉ lệ 0f..1f cho thanh tiến độ; `0f` khi server chưa biết tổng. */
        val tiLe: Float get() = if (tong <= 0) 0f else (daQuet.toFloat() / tong).coerceIn(0f, 1f)

        /** Việc CHƯA được kho nhận (server trả `soKien = 0`) ⇒ empty state riêng. */
        val chuaNhan: Boolean get() = kienView?.chuaNhan == true
    }

    private val scans = mutableStateMapOf<Int, ScanSession>()

    /** Trạng thái quét của việc [jobId] (rỗng nếu chưa quét lần nào). */
    fun scanOf(jobId: Int): ScanSession = scans[jobId] ?: ScanSession()

    private fun updateScan(jobId: Int, block: (ScanSession) -> ScanSession) {
        scans[jobId] = block(scanOf(jobId))
    }

    fun onScanInputChange(jobId: Int, value: String) {
        if (isScanCompleted(jobId)) return
        updateScan(jobId) { it.copy(input = value, error = null) }
    }

    /** Điền mã kiện [ma] vào ô quét và gửi luôn (phục vụ bấm 1 chạm trên dòng kiện ở Bước 7). */
    fun quetMa(jobId: Int, ma: String, scope: CoroutineScope) {
        if (isScanCompleted(jobId)) return
        onScanInputChange(jobId, ma)
        quet(jobId, scope)
    }

    /**
     * Tải danh sách kiện MỘT LẦN khi mở việc đã nhận (gọi từ `LaunchedEffect` của màn hình).
     *
     * Endpoint `GET /kho/jobs/<id>/kien` (BE ship 2026-11-03) trả cả `soKien`/`daQuet`/trạng thái
     * từng kiện ⇒ màn hình hiện được danh sách NGAY, không phải chờ tới lần quét đầu.
     */
    fun loadKienIfNeeded(jobId: Int, scope: CoroutineScope) {
        val current = scanOf(jobId)
        if (current.kienLoading || current.kienView != null) return
        updateScan(jobId) { it.copy(kienLoading = true) }
        scope.launch {
            val result = repo.kienCuaViec(jobId)
            result.fold(
                onSuccess = { view ->
                    updateScan(jobId) {
                        val tongMoi = if (view.soKien > 0) view.soKien else it.tong
                        val daQuetMoi = if (view.soKien > 0) view.daQuet else it.daQuet
                        it.copy(
                            kienLoading = false,
                            kienView = view,
                            // Tiến độ: ưu tiên số của server; nếu chưa có kiện thì giữ số cũ.
                            tong = tongMoi,
                            daQuet = daQuetMoi,
                            // Nếu trên server việc này đã được quét đủ kiện từ trước ⇒ đánh dấu hoàn tất luôn.
                            daHoanTat = it.daHoanTat || (tongMoi > 0 && daQuetMoi >= tongMoi),
                        )
                    }
                },
                onFailure = { error ->
                    updateScan(jobId) {
                        it.copy(
                            kienLoading = false,
                            kienView = null,
                            error = error.toVietnameseMessage(),
                        )
                    }
                },
            )
        }
    }

    /** Tải LẠI danh sách kiện (nút "Tải lại" trong khối kiện) — bỏ qua cache của lần trước. */
    fun reloadKien(jobId: Int, scope: CoroutineScope) {
        updateScan(jobId) { it.copy(kienView = null, kienLoading = false, error = null) }
        loadKienIfNeeded(jobId, scope)
    }

    /**
     * Gửi một mã vừa quét/gõ cho [jobId] (mốc `nhan`).
     *
     * Bất biến:
     *  - mỗi lần gửi sinh **một** `clientUuid` mới (server chống trùng theo uuid);
     *  - `ok` mới tăng tiến độ; `trung` / `sai_moc` / `khong_tim_thay` KHÔNG tăng;
     *  - tiến độ lấy từ `tienDo` của SERVER (`daQuet`/`tong`), app không tự đếm để khỏi lệch khi
     *    có người khác quét cùng việc trên máy khác.
     */
    fun quet(jobId: Int, scope: CoroutineScope) {
        val current = scanOf(jobId)
        if (current.busy || current.duKien || current.daHoanTat || isScanCompleted(jobId)) {
            return
        }
        val ma = extractScanCode(current.input)
        if (ma.isEmpty()) {
            updateScan(jobId) { it.copy(error = "Nhập hoặc quét mã kiện trước khi gửi.") }
            return
        }
        updateScan(jobId) { it.copy(busy = true, error = null) }
        scope.launch {
            val result = repo.quet(
                ma = ma,
                moc = MOC_NHAN,
                clientUuid = newClientUuid(),
            )
            result.fold(
                onSuccess = { scan ->
                    updateScan(jobId) { session ->
                        val wrongMsg = when (scan.outcome) {
                            ScanOutcome.KHONG_TIM_THAY -> "⚠️ Quét lộn mã: Không tìm thấy mã kiện '${scan.maQuet}' trong hệ thống."
                            ScanOutcome.SAI_MOC -> "⚠️ Quét lộn mã: Mã '${scan.maQuet}' không ở trạng thái cho phép nhận kho (${scan.giaiThich})."
                            ScanOutcome.TRUNG -> "⚠️ Kiện '${scan.maQuet}' đã được quét trước đó (không tính thêm vào tiến độ)."
                            ScanOutcome.OK -> null
                        }
                        session.copy(
                            busy = false,
                            error = wrongMsg,
                            // Giữ mã trong ô khi kết quả KHÔNG phải ok (để người quét thấy mình
                            // vừa quét gì và sửa); xoá ô khi thành công để sẵn sàng quét kiện sau.
                            input = if (scan.outcome == ScanOutcome.OK) "" else session.input,
                            lines = (listOf(scan) + session.lines).take(MAX_SCAN_LINES),
                            daQuet = if (scan.tinhVaoTienDo) {
                                if (scan.daQuet > 0) scan.daQuet else session.daQuet + 1
                            } else {
                                session.daQuet
                            },
                            tong = if (scan.tong > 0) scan.tong else session.tong,
                            // Đánh dấu kiện vừa quét NGAY tại chỗ (không gọi lại cả danh sách):
                            // chip chuyển "Đã quét" và tiến độ khớp với số server vừa trả.
                            kienView = session.kienView?.let { view ->
                                val kien = view.kien.map { k ->
                                    if (scan.outcome == ScanOutcome.OK && k.ma == scan.kien) {
                                        k.copy(daQuet = true, quetLuc = k.quetLuc)
                                    } else {
                                        k
                                    }
                                }
                                view.copy(
                                    kien = kien,
                                    soKien = if (scan.tong > 0) scan.tong else view.soKien,
                                    daQuet = if (scan.daQuet > 0) scan.daQuet else view.daQuet,
                                )
                            },
                        )
                    }
                },
                onFailure = { error ->
                    updateScan(jobId) {
                        it.copy(busy = false, error = error.toVietnameseMessage())
                    }
                },
            )
        }
    }

    /**
     * Kết thúc nhận hàng cho [jobId] — CHỈ điều hướng + thông báo.
     *
     * Vì sao không gọi lại `nhan`: nhận hàng đã được server xác nhận ở bước trước (việc đã
     * `confirmed`), gọi lại chỉ có nguy cơ sinh phiếu thứ hai. Quét tem là bước XÁC NHẬN NỘI BỘ
     * (đã ghi `lab.scan.event` ở từng lần quét), không phải một lần nhận nữa.
     */
    fun hoanTatNhan(jobId: Int) {
        val job = jobs.firstOrNull { it.id == jobId } ?: return
        val session = scanOf(jobId)
        if (!session.duKien) return
        updateScan(jobId) { it.copy(daHoanTat = true) }
        notice = "Đã xác nhận đủ ${session.tong} kiện cho ${job.ref}."
        closeDetail()
    }

    fun clearScanError(jobId: Int) {
        updateScan(jobId) { it.copy(error = null) }
    }

    private companion object {
        /** Mốc quét của màn nhận hàng. Mốc `xuat` dùng ở màn Xuất kho (chưa làm). */
        const val MOC_NHAN = "nhan"

        /** Giữ tối đa 30 dòng kết quả gần nhất — đủ cho người quét nhìn lại, không phình state. */
        const val MAX_SCAN_LINES = 30
    }
}
