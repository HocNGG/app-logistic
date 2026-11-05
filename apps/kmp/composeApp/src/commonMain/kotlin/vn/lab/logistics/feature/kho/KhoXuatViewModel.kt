package vn.lab.logistics.feature.kho

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.lab.logistics.core.model.KhoXuatChuyen
import vn.lab.logistics.core.model.KhoXuatKien
import vn.lab.logistics.core.model.ScanOutcome
import vn.lab.logistics.core.model.ScanResult
import vn.lab.logistics.core.model.XuatKetQua
import vn.lab.logistics.core.net.toVietnameseMessage
import vn.lab.logistics.core.repo.KhoRepo
import vn.lab.logistics.feature.capture.newClientUuid
import vn.lab.logistics.ui.components.UiState
import vn.lab.logistics.ui.components.extractScanCode

/**
 * =====================================================================================
 * ViewModel màn **Kho · Xuất kho** — chặng cuối của chuỗi tem (chốt 2026-11-02).
 *
 * Luồng nghiệp vụ:
 *  1. Chuyến đã chốt booking ⇒ BE sinh phiếu `WH/OUT` + **mỗi kiện một mã tem xuất** `XUAT/26/…`;
 *  2. Kho mở chuyến, **quét từng tem xuất** khi đưa kiện lên xe (mốc `xuat`);
 *  3. Bấm **Xác nhận xuất**:
 *     - đủ kiện ⇒ validate `WH/OUT`, trả số phiếu;
 *     - thiếu kiện ⇒ **bắt buộc lý do** (quyết định M9) rồi mới cho xuất; kiện thiếu sang `cancelled`;
 *  4. Server có thể CHẶN (409 `CON_KIEN_CHUA_QUET`) ⇒ UI hiện danh sách còn thiếu + mời nhập lý do.
 *
 * VÌ SAO state nằm ở đây (không `remember` trong composable): kéo cửa sổ/đổi bề rộng làm subtree
 * bị dispose; nếu để trong composable thì danh sách kiện đã quét + lý do đang gõ sẽ mất.
 * =====================================================================================
 */
class KhoXuatViewModel(
    private val repo: KhoRepo,
) {

    /** Vị trí scroll của danh sách — state của CALLER, truyền vào `LazyColumn`. */
    val listState: LazyListState = LazyListState()

    var state by mutableStateOf<UiState<List<KhoXuatChuyen>>>(UiState.Loading)
        private set

    /** Chuyến đang mở ở pane chi tiết (`null` = chưa mở). */
    var openedId by mutableStateOf<Int?>(null)
        private set

    /** Chi tiết chuyến đang mở (kèm trạng thái từng kiện). */
    var detail by mutableStateOf<KhoXuatChuyen?>(null)
        private set

    var detailLoading by mutableStateOf(false)
        private set

    /** Đang gọi API xác nhận xuất (khoá nút — chống bấm 2 lần sinh 2 lần validate). */
    var busyXuat by mutableStateOf(false)
        private set

    var notice by mutableStateOf<String?>(null)
        private set

    var actionError by mutableStateOf<String?>(null)
        private set

    // ---- Quét tem xuất ----
    var scanInput by mutableStateOf("")
        private set
    var scanBusy by mutableStateOf(false)
        private set
    var scanError by mutableStateOf<String?>(null)
        private set

    /** Các lần quét, MỚI NHẤT ở đầu. */
    var scanLines by mutableStateOf<List<ScanResult>>(emptyList())
        private set

    /** Màn in tem QR xuất của chuyến đang mở. */
    var printDialogOpen by mutableStateOf(false)
        private set

    /** Màn quét QR xuất bằng điện thoại. */
    var scannerDialogOpen by mutableStateOf(false)
        private set

    // ---- Hộp thoại "xuất thiếu" (M9) ----
    var lyDoDialogOpen by mutableStateOf(false)
        private set
    var lyDoText by mutableStateOf("")
        private set
    var lyDoError by mutableStateOf<String?>(null)
        private set

    /** Kiện còn thiếu do server trả trong 409 `CON_KIEN_CHUA_QUET` (rỗng nếu chưa bị chặn). */
    var kienConThieu by mutableStateOf<List<String>>(emptyList())
        private set

    private var loadedOnce: Boolean = false

    /** `clientUuid` của LẦN XÁC NHẬN đang chờ — giữ nguyên khi người dùng bấm lại/retry. */
    private var pendingXuatUuid: String? = null

    val chuyenList: List<KhoXuatChuyen>
        get() = (state as? UiState.Content)?.data ?: emptyList()

    fun clearNotice() {
        notice = null
    }

    fun clearActionError() {
        actionError = null
    }

    // ==================================================================================
    // Tải dữ liệu
    // ==================================================================================

    fun load(scope: CoroutineScope) = fetch(scope, showLoading = true)

    fun loadIfNeeded(scope: CoroutineScope) {
        if (!loadedOnce) load(scope)
    }

    private fun fetch(scope: CoroutineScope, showLoading: Boolean) {
        loadedOnce = true
        if (showLoading) state = UiState.Loading
        scope.launch {
            val result = repo.choXuat()
            result.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        openedId = null
                        detail = null
                        state = UiState.Empty
                    } else {
                        state = UiState.Content(list)
                        // Giữ chuyến đang mở nếu còn trong danh sách.
                        val giuLai = list.firstOrNull { it.chuyenId == openedId }?.chuyenId
                        if (giuLai != null) open(giuLai, scope) else if (openedId != null) closeDetail()
                    }
                },
                onFailure = { error ->
                    state = UiState.Error(
                        message = error.message ?: "Không tải được danh sách chuyến chờ xuất.",
                        retry = { load(scope) },
                    )
                },
            )
        }
    }

    /** Mở chi tiết một chuyến (gọi API `?chuyenId=` để lấy trạng thái kiện mới nhất). */
    fun open(chuyenId: Int, scope: CoroutineScope) {
        openedId = chuyenId
        actionError = null
        scanError = null
        kienConThieu = emptyList()
        detail = chuyenList.firstOrNull { it.chuyenId == chuyenId }
        detailLoading = true
        scope.launch {
            val result = repo.chiTietXuat(chuyenId)
            detailLoading = false
            result.fold(
                onSuccess = { chuyen -> if (openedId == chuyenId) detail = chuyen },
                onFailure = { error -> actionError = error.toVietnameseMessage() },
            )
        }
    }

    fun openAndPrint(chuyenId: Int, scope: CoroutineScope) {
        open(chuyenId, scope)
        printDialogOpen = true
        scannerDialogOpen = false
    }

    fun openPrintDialog() {
        printDialogOpen = true
        scannerDialogOpen = false
    }

    fun closePrintDialog() {
        printDialogOpen = false
    }

    fun openScannerDialog() {
        scannerDialogOpen = true
        printDialogOpen = false
    }

    fun closeScannerDialog() {
        scannerDialogOpen = false
    }

    fun switchPrintToScanner() {
        printDialogOpen = false
        scannerDialogOpen = true
    }

    fun closeDetail() {
        openedId = null
        detail = null
        scanLines = emptyList()
        scanInput = ""
        scanError = null
        kienConThieu = emptyList()
        lyDoDialogOpen = false
        lyDoText = ""
        lyDoError = null
        pendingXuatUuid = null
        printDialogOpen = false
        scannerDialogOpen = false
    }

    // ==================================================================================
    // Quét tem xuất (mốc "xuat")
    // ==================================================================================

    fun onScanInputChange(value: String) {
        if (detail?.duKien == true) return
        scanInput = value
        scanError = null
    }

    fun quetMa(ma: String, scope: CoroutineScope) {
        if (detail?.duKien == true) return
        onScanInputChange(ma)
        quet(scope)
    }

    /**
     * Gửi mã vừa quét cho chuyến đang mở.
     *
     * Tiến độ lấy từ `tienDo` của SERVER (không tự đếm) — có người quét cùng chuyến trên máy khác
     * thì con số vẫn đúng.
     */
    fun quet(scope: CoroutineScope) {
        val chuyen = detail ?: return
        if (scanBusy || chuyen.duKien) return
        val ma = extractScanCode(scanInput)
        if (ma.isEmpty()) {
            scanError = "Nhập hoặc quét mã tem xuất trước khi gửi."
            return
        }
        scanBusy = true
        scanError = null
        scope.launch {
            val result = repo.quet(ma = ma, moc = MOC_XUAT, clientUuid = newClientUuid())
            scanBusy = false
            result.fold(
                onSuccess = { scan ->
                    scanLines = (listOf(scan) + scanLines).take(MAX_SCAN_LINES)
                    if (scan.outcome == ScanOutcome.OK) {
                        scanInput = ""
                        scanError = null
                    } else {
                        scanError = when (scan.outcome) {
                            ScanOutcome.KHONG_TIM_THAY -> "⚠️ Quét lộn mã: Không tìm thấy mã '${scan.maQuet}' trong hệ thống."
                            ScanOutcome.SAI_MOC -> "⚠️ Quét lộn mã: Mã '${scan.maQuet}' không ở trạng thái cho phép xuất kho (${scan.giaiThich})."
                            ScanOutcome.TRUNG -> "⚠️ Mã '${scan.maQuet}' đã được quét xuất trước đó (không tính thêm vào tiến độ)."
                            ScanOutcome.OK -> null
                        }
                    }
                    // Cập nhật chi tiết tại chỗ: đánh dấu kiện vừa quét + lấy tiến độ server trả.
                    detail = chuyenApDungQuet(chuyen, scan)
                },
                onFailure = { error -> scanError = error.toVietnameseMessage() },
            )
        }
    }

    private fun chuyenApDungQuet(chuyen: KhoXuatChuyen, scan: ScanResult): KhoXuatChuyen {
        val kien = chuyen.kien.map { k ->
            val khopMa = k.ma == scan.kien || (!scan.maXuat.isNullOrBlank() && k.maXuat == scan.maXuat)
            if (scan.outcome == ScanOutcome.OK && khopMa) {
                k.copy(trangThai = "shipped", trangThaiLabel = "Đã xuất kho")
            } else {
                k
            }
        }
        return chuyen.copy(
            kien = kien,
            daQuet = if (scan.tong > 0) scan.daQuet else kien.count { it.daXuat },
            soKien = if (scan.tong > 0) scan.tong else chuyen.soKien,
        )
    }

    // ==================================================================================
    // Xác nhận xuất — 2 nhánh: đủ kiện · thiếu kiện (bắt buộc lý do, M9)
    // ==================================================================================

    /**
     * Nút **Xác nhận xuất**.
     *
     * - Đủ kiện ⇒ gọi server luôn;
     * - Thiếu kiện ⇒ MỞ hộp thoại bắt buộc nhập lý do (không gọi server trước, tránh 409 vô ích —
     *   nhưng 409 vẫn được xử lý nếu xảy ra do người khác quét/xuất cùng lúc).
     */
    fun xacNhan(scope: CoroutineScope) {
        val chuyen = detail ?: return
        if (busyXuat) return
        if (!chuyen.duKien) {
            kienConThieu = chuyen.kien.filterNot { it.daXuat }.map { it.ma }
            lyDoDialogOpen = true
            lyDoError = null
            if (lyDoText.isBlank()) lyDoText = ""
            return
        }
        guiXacNhan(chuyen.chuyenId, lyDo = null, scope = scope)
    }

    /** Người dùng bấm "Nhập lý do" trên hộp thoại/nhánh 409. */
    fun moHopThoaiLyDo() {
        lyDoDialogOpen = true
        lyDoError = null
    }

    fun onLyDoChange(value: String) {
        lyDoText = value
        lyDoError = null
    }

    fun dongHopThoaiLyDo() {
        lyDoDialogOpen = false
        lyDoError = null
    }

    /** Gửi xác nhận kèm lý do xuất thiếu (bắt buộc không rỗng). */
    fun xacNhanThieu(scope: CoroutineScope) {
        val chuyen = detail ?: return
        val lyDo = lyDoText.trim()
        if (lyDo.isEmpty()) {
            lyDoError = "Phải nhập lý do xuất thiếu (vd: kiện bị rách, khách rút 1 kiện)."
            return
        }
        lyDoDialogOpen = false
        guiXacNhan(chuyen.chuyenId, lyDo = lyDo, scope = scope)
    }

    private fun guiXacNhan(chuyenId: Int, lyDo: String?, scope: CoroutineScope) {
        if (busyXuat) return
        busyXuat = true
        actionError = null
        // Giữ nguyên uuid nếu đây là lần gửi LẶP của cùng hành động (chống trùng ở server).
        val uuid = pendingXuatUuid ?: newClientUuid().also { pendingXuatUuid = it }
        scope.launch {
            val result = repo.xacNhanXuat(chuyenId = chuyenId, clientUuid = uuid, lyDoThieu = lyDo)
            busyXuat = false
            result.fold(
                onSuccess = { ketQua ->
                    when (ketQua) {
                        is XuatKetQua.DaXuat -> {
                            pendingXuatUuid = null
                            notice = if (ketQua.soKienThieu > 0) {
                                "Đã xuất ${ketQua.soKienXuat} kiện · phiếu ${ketQua.phieu} · " +
                                    "xuất thiếu ${ketQua.soKienThieu} kiện."
                            } else {
                                "Đã xuất ${ketQua.soKienXuat} kiện · phiếu ${ketQua.phieu}."
                            }
                            closeDetail()
                            fetch(scope, showLoading = false)
                        }

                        is XuatKetQua.ConKienChuaQuet -> {
                            // Server chặn: hiện danh sách còn thiếu + mở hộp thoại lý do.
                            kienConThieu = ketQua.danhSach
                            actionError = ketQua.message
                            lyDoDialogOpen = true
                            lyDoError = null
                        }
                    }
                },
                onFailure = { error -> actionError = error.toVietnameseMessage() },
            )
        }
    }

    /** Kiện đang có trong chi tiết — dùng cho danh sách ở pane phải. */
    val kienList: List<KhoXuatKien>
        get() = detail?.kien ?: emptyList()

    private companion object {
        const val MOC_XUAT = "xuat"
        const val MAX_SCAN_LINES = 30
    }
}
