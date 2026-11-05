package vn.lab.logistics.feature.kho

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.lab.logistics.core.model.KhoJob
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Lab
import vn.lab.logistics.design.Tokens
import vn.lab.logistics.ui.components.CalloutBox
import vn.lab.logistics.ui.components.DuoScaffold
import vn.lab.logistics.ui.components.StateHost
import vn.lab.logistics.ui.components.Tone
import vn.lab.logistics.ui.kit.LabCard
import vn.lab.logistics.ui.kit.LabCellStyle
import vn.lab.logistics.ui.kit.LabChip
import vn.lab.logistics.ui.kit.LabEmptyState
import vn.lab.logistics.ui.kit.LabField
import vn.lab.logistics.ui.kit.LabFilterChip
import vn.lab.logistics.ui.kit.LabRowDivider
import vn.lab.logistics.ui.kit.LabSectionTitle
import vn.lab.logistics.ui.kit.LabStatusChip
import vn.lab.logistics.ui.kit.LabToolbar
import vn.lab.logistics.ui.platform.scanQrCodeWithLiveCamera

/**
 * =====================================================================================
 * MÀN **KHO — NHẬN HÀNG**: danh sách việc tài xế đã lấy hàng (`picked_up`) đang chờ kho nhận.
 *
 * Luồng nghiệp vụ (docs/00, bước "Kho nhận hàng" + Luật 4 REA) KHÔNG đổi:
 *   tài xế chụp ảnh + ký → việc sang `picked_up` → hiện ở màn này → kho bấm **"Nhận hàng"** →
 *   Odoo tạo `stock.picking` nhập kho và trả **số phiếu nhập kho** (`pickingName`) → item chuyển
 *   sang "Đã nhận" kèm số phiếu, Snackbar tiếng Việt xác nhận.
 *
 * Điểm quan trọng về nghiệp vụ: app **không đánh số phiếu**, không tự cộng tồn kho — số phiếu và
 * chứng từ do Odoo sinh. Màn này chỉ hiển thị lại đúng thứ server trả.
 *
 * GIAO DIỆN: bộ kit (`LabToolbar` · `LabCard` · `LabStatusChip` · `LabChip` · `LabEmptyState`) —
 * cùng ngôn ngữ với màn tài xế/CS. Hàng đang mở có nền nhấn 10%; số phiếu nhập kho hiện bằng
 * chip chữ (không chỉ màu). Điện thoại (< 600dp): toolbar xếp hành động xuống hàng riêng, 1 pane.
 *
 * BỐN TRẠNG THÁI: Loading · Empty · Error · Content qua [StateHost].
 * =====================================================================================
 */
@Composable
fun KhoReceiveScreen(
    vm: KhoReceiveViewModel,
    widthDp: Int,
    onExitToLogin: () -> Unit,
    /** Điều hướng sang màn con **Kho · Xuất kho** (giữ nguyên nav 3 tab, không thêm tab thứ 4). */
    onOpenXuat: (() -> Unit)? = null,
    dataSourceLabel: String = "Mẫu",
    onFallbackToSample: (() -> Unit)? = null,
    /** Câu thông báo một lần do màn khác chuyển về (đồng bộ hành vi với màn tài xế). */
    flashMessage: String? = null,
    onFlashShown: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val compact = widthDp < Tokens.BREAKPOINT_COMPACT_DP

    LaunchedEffect(vm) { vm.loadIfNeeded(scope) }

    val notify: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }

    LaunchedEffect(vm.notice) {
        val message = vm.notice
        if (message != null) {
            notify(message)
            vm.clearNotice()
        }
    }

    LaunchedEffect(flashMessage) {
        val message = flashMessage
        if (message != null) {
            notify(message)
            onFlashShown()
        }
    }

    DuoScaffold(
        modifier = modifier.background(AppTheme.lab.ground),
        widthDp = widthDp,
        hasSelection = vm.openedId != null,
        onBack = { vm.closeDetail() },
        topBar = {
            LabToolbar(
                title = "Kho · Nhận hàng",
                subtitle = "${vm.pendingCount()} việc chờ nhận · Nguồn: $dataSourceLabel",
                stacked = compact,
                actions = {
                    ToolbarAction("Tải lại") { vm.load(scope) }
                    if (onOpenXuat != null) ToolbarAction("Xuất kho", onOpenXuat)
                    ToolbarAction("Đăng xuất", onExitToLogin)
                },
            )
        },
        snackbar = snackbar,
        overlayTitle = vm.detailJob()?.ref ?: "Chi tiết hàng chờ nhận",
        listPane = {
            KhoListPane(
                vm = vm,
                scope = scope,
                onOpen = { id -> vm.open(id) },
                onFallbackToSample = onFallbackToSample,
            )
        },
        detailPane = { selected ->
            KhoDetailPane(
                vm = vm,
                selected = selected,
                scope = scope,
                compact = compact,
                onOpenXuat = onOpenXuat,
            )
        },
    )

    val printJobId = vm.printDialogJobId
    if (printJobId != null) {
        val printJob = vm.jobs.firstOrNull { it.id == printJobId }
        if (printJob != null) {
            LaunchedEffect(printJobId) { vm.loadKienIfNeeded(printJobId, scope) }
            val session = vm.scanOf(printJobId)
            val saleOrder = vm.saleOrderNameOf(printJobId)
            val picking = vm.pickingNameOf(printJobId)
            val ticketItems = session.kienView?.kien?.map { kien ->
                QrTicketItem(
                    qrCode = kien.ma,
                    maKien = kien.ma,
                    maXuat = null,
                    daQuet = kien.daQuet,
                    statusLabel = listOfNotNull(kien.nhanQuet, kien.gioQuetLabel).joinToString(" · "),
                )
            } ?: emptyList()

            QrLabelPrintDialog(
                title = "Màn in tem mã QR nhận kho",
                subtitle = "${printJob.ref} · ${printJob.shop}",
                metaLine = listOfNotNull(
                    picking?.let { "Phiếu nhập: $it" },
                    saleOrder?.let { "Đơn bán: $it" },
                    printJob.weightLabel(),
                ).joinToString(" · "),
                items = ticketItems,
                loading = session.kienLoading,
                busy = session.busy,
                onScanItem = { code -> vm.quetMa(printJobId, code, scope) },
                onSwitchToScanner = { vm.switchPrintToScanner(printJobId) },
                onDismiss = { vm.closePrintDialog() },
            )
        }
    }

    val scannerJobId = vm.scannerDialogJobId
    if (scannerJobId != null) {
        val scannerJob = vm.jobs.firstOrNull { it.id == scannerJobId }
        if (scannerJob != null) {
            LaunchedEffect(scannerJobId) { vm.loadKienIfNeeded(scannerJobId, scope) }
            val session = vm.scanOf(scannerJobId)
            val saleOrder = vm.saleOrderNameOf(scannerJobId)
            val ticketItems = session.kienView?.kien?.map { kien ->
                QrTicketItem(
                    qrCode = kien.ma,
                    maKien = kien.ma,
                    maXuat = null,
                    daQuet = kien.daQuet,
                    statusLabel = listOfNotNull(kien.nhanQuet, kien.gioQuetLabel).joinToString(" · "),
                )
            } ?: emptyList()
            val donBanHint = saleOrder?.let { "Mã đơn bán: $it (dùng để gom vào Chuyến bay ở Bước 8)." } ?: ""

            QrPhoneScannerDialog(
                title = "Quét mã QR nhận kho",
                subtitle = "${scannerJob.ref} · ${scannerJob.shop}",
                nextStepHint = "Đã quét đủ ${session.daQuet}/${session.tong} kiện vào kho. $donBanHint Bấm Xác nhận OK để hoàn tất và chuyển qua bước tiếp theo.",
                confirmDoneLabel = "Xác nhận OK · Chuyển bước tiếp theo",
                secondaryDoneLabel = if (onOpenXuat != null) "Xác nhận OK & Mở màn Xuất kho" else null,
                items = ticketItems,
                daQuet = session.daQuet,
                tong = session.tong,
                busy = session.busy,
                error = session.error,
                onScanCode = { code -> vm.quetMa(scannerJobId, code, scope) },
                onOpenPrint = { vm.openPrintDialog(scannerJobId) },
                onConfirmDone = { vm.hoanTatNhan(scannerJobId) },
                onSecondaryDone = if (onOpenXuat != null) {
                    {
                        vm.hoanTatNhan(scannerJobId)
                        onOpenXuat()
                    }
                } else {
                    null
                },
                onDismiss = { vm.closeScannerDialog() },
            )
        }
    }
}

@Composable
private fun ToolbarAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.height(36.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            color = AppTheme.lab.accent,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------------------
// Pane trái: hàng chờ nhận
// ---------------------------------------------------------------------------------------
@Composable
private fun KhoListPane(
    vm: KhoReceiveViewModel,
    scope: CoroutineScope,
    onOpen: (Int) -> Unit,
    onFallbackToSample: (() -> Unit)?,
) {
    Column(Modifier.fillMaxSize()) {
        // Chip lọc: Tất cả · Đã lấy được hàng (Bước 6) · Đã về kho (Bước 7).
        if (vm.jobs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.s4, vertical = Tokens.s2),
                horizontalArrangement = Arrangement.spacedBy(Tokens.s2),
            ) {
                KhoJobFilter.values().forEach { option ->
                    LabFilterChip(
                        text = "${option.label} (${option.countIn(vm.jobs, vm::isReceived)})",
                        selected = vm.filter == option,
                        onClick = { vm.selectFilter(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        vm.actionError?.let { message ->
            CalloutBox(
                title = "Không nhận được hàng",
                body = message,
                tone = Tone.Bad,
                actionLabel = "Đóng",
                onAction = vm::clearActionError,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.s4, vertical = Tokens.s2),
            )
        }

        StateHost(
            state = vm.state,
            modifier = Modifier.weight(1f),
            emptyTitle = "Không có việc nào chờ kho nhận",
            emptyHint = "Khi tài xế chụp ảnh + ký biên nhận xong, việc sẽ hiện ở đây. Bấm Tải lại.",
            loadingLabel = "Đang tải hàng chờ nhận…",
            errorHint = "Kiểm tra Odoo đã chạy và tài khoản đăng nhập có vai trò Kho, rồi bấm Thử lại.",
            secondaryActionLabel = if (onFallbackToSample != null) "Về dữ liệu mẫu" else null,
            onSecondaryAction = onFallbackToSample,
        ) { _ ->
            val shown = vm.visibleJobs
            if (shown.isEmpty()) {
                LabEmptyState(
                    title = "Không có việc ${vm.filter.label.lowercase()}",
                    hint = "Chọn \"Tất cả\" để xem toàn bộ việc.",
                )
            } else {
                LazyColumn(
                    state = vm.listState, // state của VIEWMODEL — không tạo ở đây (displacement)
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = shown, key = { job -> job.id }) { job ->
                        val session = vm.scanOf(job.id)
                        KhoJobRow(
                            job = job,
                            isOpened = job.id == vm.openedId,
                            busy = vm.busyJobId == job.id,
                            received = vm.isReceived(job.id),
                            pickingName = vm.pickingNameOf(job.id),
                            saleOrderName = vm.saleOrderNameOf(job.id),
                            scanCompleted = vm.isScanCompleted(job.id),
                            scannedCount = if (session.tong > 0) session.daQuet else job.scannedPackages,
                            totalCount = if (session.tong > 0) session.tong else job.totalPackages,
                            onClick = { onOpen(job.id) },
                            onNhan = { vm.nhan(job.id, scope) },
                        )
                        LabRowDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun KhoJobRow(
    job: KhoJob,
    isOpened: Boolean,
    busy: Boolean,
    received: Boolean,
    pickingName: String?,
    saleOrderName: String?,
    scanCompleted: Boolean,
    scannedCount: Int,
    totalCount: Int,
    onClick: () -> Unit,
    onNhan: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Lab.tableRowMinHeight)
            .background(
                if (isOpened) {
                    AppTheme.lab.accent.copy(alpha = Lab.NAV_ACTIVE_TINT)
                } else {
                    Tokens.transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Tokens.s4, vertical = Tokens.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = job.ref,
                style = MaterialTheme.typography.subtitle2,
                color = AppTheme.lab.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Tokens.s2))
            LabStatusChip(label = job.statusLabel(received))
        }
        Text(
            text = job.shop,
            style = MaterialTheme.typography.body1,
            color = AppTheme.lab.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Tokens.s1),
        )
        Text(
            text = "${job.packagesLabel()} · ${job.weightLabel()}",
            style = LabCellStyle,
            color = AppTheme.lab.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Tokens.s1),
        )
        Text(
            text = "Tài xế: ${job.driverLabel()}",
            style = LabCellStyle,
            color = AppTheme.lab.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (received) {
            Spacer(Modifier.height(Tokens.s2))
            val donBanChip = saleOrderName?.let { " · Đơn bán: $it" }.orEmpty()
            LabChip(
                text = "Phiếu nhập kho: ${pickingName ?: "đã về kho"}$donBanChip",
                tone = Tone.Ok,
            )
            Button(
                onClick = onClick,
                enabled = !scanCompleted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Tokens.s2)
                    .height(Tokens.touchSecondary),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AppTheme.lab.surface2,
                    contentColor = AppTheme.lab.ink,
                ),
            ) {
                Text(
                    text = when {
                        scanCompleted && totalCount > 0 ->
                            "Đã quét đủ $totalCount/$totalCount kiện · Hoàn tất nhận"
                        scanCompleted -> "Đã hoàn tất quét tem nhận"
                        totalCount > 0 -> "Quét tem nhận ($scannedCount/$totalCount kiện)"
                        else -> "Quét tem nhận"
                    },
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Button(
                onClick = onNhan,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Tokens.s2)
                    .height(Tokens.touchSecondary),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AppTheme.lab.accent,
                    contentColor = AppTheme.lab.onAccent,
                ),
            ) {
                Text(
                    text = if (busy) "Đang nhận…" else "Nhận hàng & in tem",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Pane phải: chi tiết + nút Nhận hàng (>= 56dp)
// ---------------------------------------------------------------------------------------
@Composable
private fun KhoDetailPane(
    vm: KhoReceiveViewModel,
    selected: Boolean,
    scope: CoroutineScope,
    compact: Boolean = false,
    onOpenXuat: (() -> Unit)? = null,
) {
    val job = vm.detailJob()
    if (!selected || job == null) {
        LabEmptyState(
            title = "Chọn một việc ở danh sách",
            hint = "Bấm trực tiếp vào dòng để xem số kiện, khối lượng và nhận hàng.",
        )
        return
    }
    val busy = vm.busyJobId == job.id
    val received = vm.isReceived(job.id)
    val pickingName = vm.pickingNameOf(job.id)
    val saleOrderName = vm.saleOrderNameOf(job.id)
    val scanCompleted = vm.isScanCompleted(job.id)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(if (compact) Tokens.s4 else Tokens.s5),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = job.ref,
                    style = MaterialTheme.typography.h5,
                    color = AppTheme.lab.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = job.shop,
                    style = MaterialTheme.typography.subtitle1,
                    color = AppTheme.lab.ink2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Tokens.s1),
                )
            }
            Spacer(Modifier.width(Tokens.s3))
            LabStatusChip(label = job.statusLabel(received))
        }

        Spacer(Modifier.height(Tokens.s4))

        LabCard(title = "Hàng tài xế mang về") {
            LabField("Số kiện tài xế đã nhập", job.packagesLabel())
            LabField("Khối lượng", job.weightLabel())
            LabField("Tài xế mang hàng về", job.driverLabel())
        }

        if (received && pickingName != null) {
            Spacer(Modifier.height(Tokens.s3))
            val donBanTitle = saleOrderName?.let { " · Đơn bán $it" }.orEmpty()
            val donBanBody = saleOrderName?.let {
                " Mã đơn bán để CS tìm và gom vào Chuyến bay (Bước 8): $it (hoặc gõ ${job.ref})."
            }.orEmpty()
            CalloutBox(
                title = "Phiếu nhập kho $pickingName$donBanTitle",
                body = "Odoo đã tạo phiếu nhập kho cho chuyến này.$donBanBody",
                tone = Tone.Ok,
            )
        }

        // QUÉT TEM KIỆN — chỉ hiện sau khi việc đã về kho (tem sinh tại kho, chốt 2026-11-02).
        if (received) {
            Spacer(Modifier.height(Tokens.s4))
            KhoScanSection(vm = vm, job = job, scope = scope, onOpenXuat = onOpenXuat)
        }

        vm.actionError?.let { message ->
            Spacer(Modifier.height(Tokens.s3))
            CalloutBox(
                title = "Không thực hiện được",
                body = message,
                tone = Tone.Bad,
                actionLabel = "Đóng",
                onAction = vm::clearActionError,
            )
        }

        Spacer(Modifier.height(Tokens.s4))

        Button(
            onClick = { vm.nhan(job.id, scope) },
            enabled = !busy && !received,
            modifier = Modifier.fillMaxWidth().height(Tokens.touchPrimary),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = AppTheme.lab.accent,
                contentColor = AppTheme.lab.onAccent,
            ),
        ) {
            Text(
                text = when {
                    received && scanCompleted -> "Đã hoàn tất nhận & quét đủ kiện"
                    received -> "Đã về kho — quét tem ở trên"
                    busy -> "Đang nhận…"
                    else -> "Nhận hàng & in tem"
                },
            )
        }

        Text(
            text = if (received) {
                "Việc này đã nhận. Odoo đã có phiếu nhập kho — không bấm nhận lại để tránh " +
                    "sinh phiếu thứ hai."
            } else {
                "Kiểm đếm hàng thực tế khớp với ${job.packagesLabel()} và ${job.weightLabel()} " +
                    "rồi mới bấm Nhận hàng."
            },
            style = MaterialTheme.typography.caption,
            color = AppTheme.lab.ink2,
            modifier = Modifier.padding(top = Tokens.s2),
        )
        Spacer(Modifier.height(Tokens.s4))
    }
}

// ---------------------------------------------------------------------------------------
// QUÉT TEM KIỆN (mốc "nhan") — hiện sau khi việc đã ở trạng thái Đã về kho
//
// VÌ SAO TEM CHỈ SINH/TIÊU Ở KHO (chốt 2026-11-02): tài xế không dán, không quét. Kho nhận hàng →
// hệ thống sinh N kiện (`KIEN/26/…`) → in tem → DÁN → **quét lại đúng tem đó** để xác nhận kiện đã
// vào kho. Mỗi lần quét ghi 1 dòng `lab.scan.event` (audit + bảng tổng kết).
// ---------------------------------------------------------------------------------------
@Composable
private fun KhoScanSection(
    vm: KhoReceiveViewModel,
    job: KhoJob,
    scope: CoroutineScope,
    onOpenXuat: (() -> Unit)? = null,
) {
    val session = vm.scanOf(job.id)
    var cameraScanning by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    // Danh sách kiện: tải MỘT lần khi mở việc đã nhận — endpoint `GET /kho/jobs/<id>/kien`
    // (BE ship 2026-11-03) trả cả `soKien`/`daQuet`/trạng thái từng kiện nên màn hình có dữ
    // liệu NGAY, không phải chờ lần quét đầu.
    LaunchedEffect(job.id) { vm.loadKienIfNeeded(job.id, scope) }

    LabSectionTitle(
        text = "Tem mã QR nhận & Quét QR",
        hint = tienDoLabel(session.daQuet, session.tong),
    )
    Spacer(Modifier.height(Tokens.s2))

    // Thanh tác vụ nhanh: Mở màn in / lưu ảnh tem QR nhận · Mở Camera quét QR
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Tokens.s2),
    ) {
        Button(
            onClick = { vm.openPrintDialog(job.id) },
            modifier = Modifier.weight(1f).height(Tokens.touchSecondary),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = AppTheme.lab.surface2,
                contentColor = AppTheme.lab.ink,
            ),
        ) {
            Text(
                text = "In / Lưu ảnh tem QR",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!session.duKien && !session.daHoanTat) {
            Button(
                onClick = {
                    scope.launch {
                        cameraScanning = true
                        cameraError = null
                        val res = scanQrCodeWithLiveCamera()
                        cameraScanning = false
                        res.fold(
                            onSuccess = { decoded ->
                                if (!decoded.isNullOrBlank()) {
                                    vm.quetMa(job.id, decoded, scope)
                                }
                            },
                            onFailure = { err ->
                                cameraError = err.message ?: "Không quét được mã QR từ Camera."
                            },
                        )
                    }
                },
                enabled = !session.busy && !cameraScanning,
                modifier = Modifier.weight(1f).height(Tokens.touchSecondary),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AppTheme.lab.accent,
                    contentColor = AppTheme.lab.onAccent,
                ),
            ) {
                Text(
                    text = if (cameraScanning) "Đang mở Camera…" else "Quét QR bằng Camera",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Spacer(Modifier.height(Tokens.s2))

    LabCard(title = "Kiện của việc") {
        when {
            session.kienLoading -> Text(
                text = "Đang tải danh sách kiện…",
                style = MaterialTheme.typography.body2,
                color = AppTheme.lab.ink2,
            )

            session.kienView == null -> Column {
                Text(
                    text = "Chưa lấy được danh sách kiện của việc này. Vẫn quét/nhập mã ở ô bên " +
                        "dưới — mỗi lần quét được ghi nhận ngay.",
                    style = MaterialTheme.typography.body2,
                    color = AppTheme.lab.ink2,
                )
                Spacer(Modifier.height(Tokens.s2))
                TextButton(
                    onClick = { vm.reloadKien(job.id, scope) },
                    modifier = Modifier.height(Tokens.touchSecondary),
                ) {
                    Text("Tải lại danh sách kiện", color = AppTheme.lab.ink)
                }
            }

            session.chuaNhan -> Text(
                text = "Việc này chưa được kho nhận — chưa có kiện nào được sinh. " +
                    "Bấm \"Nhận hàng\" trước, hệ thống sẽ sinh kiện và in tem nhận.",
                style = MaterialTheme.typography.body2,
                color = AppTheme.lab.ink2,
            )

            else -> Column {
                // `session` là local val nên trình biên dịch đã biết `kienView != null` ở nhánh này.
                session.kienView.kien.forEach { kien ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = Tokens.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = kien.ma,
                            style = MaterialTheme.typography.subtitle2,
                            color = AppTheme.lab.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(Tokens.s2))
                        // Chip nói rõ đã quét hay chưa + GIỜ quét (UTC) để đối chiếu với máy quét.
                        LabChip(
                            text = listOfNotNull(kien.nhanQuet, kien.gioQuetLabel).joinToString(" · "),
                            tone = if (kien.daQuet) Tone.Ok else Tone.Warn,
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(Tokens.s3))

    if (session.duKien || session.daHoanTat) {
        CalloutBox(
            title = "Đã quét đủ ${session.daQuet}/${session.tong} kiện",
            body = "Đơn hàng đã quét đủ số kiện nhận kho — hệ thống đã khoá không cho quét tiếp.",
            tone = Tone.Ok,
        )
    } else {
        LabCard(title = "Quét mã QR bằng Camera hoặc nhập tay mã kiện") {
            Button(
                onClick = {
                    scope.launch {
                        cameraScanning = true
                        cameraError = null
                        val res = scanQrCodeWithLiveCamera()
                        cameraScanning = false
                        res.fold(
                            onSuccess = { decoded ->
                                if (!decoded.isNullOrBlank()) {
                                    vm.quetMa(job.id, decoded, scope)
                                }
                            },
                            onFailure = { err ->
                                cameraError = err.message ?: "Không quét được mã QR từ Camera."
                            },
                        )
                    }
                },
                enabled = !session.busy && !cameraScanning,
                modifier = Modifier.fillMaxWidth().height(Tokens.touchPrimary),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AppTheme.lab.accent,
                    contentColor = AppTheme.lab.onAccent,
                ),
            ) {
                Text(
                    text = if (cameraScanning) {
                        "Đang mở Camera quét mã QR…"
                    } else {
                        "Mở Camera quét mã QR (như siêu thị)"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(Tokens.s2))
            OutlinedTextField(
                value = session.input,
                onValueChange = { vm.onScanInputChange(job.id, it) },
                label = { Text("Hoặc nhập tay mã kiện (KIEN/26/…)") },
                placeholder = { Text("Không muốn quét Camera thì gõ mã kiện rồi Enter") },
                singleLine = true,
                isError = session.error != null || cameraError != null,
                enabled = !session.busy && !cameraScanning,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.quet(job.id, scope) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Tokens.s2))
            Button(
                onClick = { vm.quet(job.id, scope) },
                enabled = !session.busy && !cameraScanning,
                modifier = Modifier.fillMaxWidth().height(Tokens.touchSecondary),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AppTheme.lab.surface2,
                    contentColor = AppTheme.lab.ink,
                ),
            ) {
                Text(if (session.busy) "Đang gửi…" else "Gửi mã nhập tay")
            }

            (cameraError ?: session.error)?.let { message ->
                Spacer(Modifier.height(Tokens.s2))
                Text(
                    text = message,
                    style = MaterialTheme.typography.caption,
                    color = AppTheme.lab.danger,
                )
            }
        }
    }

    // Tiến độ: thanh + câu đếm. Màu thanh lấy từ token (lime = NHẤN, không dùng làm chữ).
    if (session.tong > 0) {
        Spacer(Modifier.height(Tokens.s3))
        LabCard(title = "Tiến độ nhận kiện") {
            Text(
                text = tienDoLabel(session.daQuet, session.tong),
                style = MaterialTheme.typography.subtitle1,
                color = AppTheme.lab.ink,
            )
            Spacer(Modifier.height(Tokens.s2))
            LinearProgressIndicator(
                progress = session.tiLe,
                modifier = Modifier.fillMaxWidth().height(Tokens.s2),
                color = AppTheme.lab.accent,
                backgroundColor = AppTheme.lab.surface2,
            )
            if (session.lines.isNotEmpty() && !session.duKien) {
                Spacer(Modifier.height(Tokens.s2))
                Text(
                    text = "Còn ${session.tong - session.daQuet} kiện chưa quét.",
                    style = MaterialTheme.typography.caption,
                    color = AppTheme.lab.ink2,
                )
            }
        }
    }

    // Kết quả từng lần quét — MỚI NHẤT ở trên.
    if (session.lines.isNotEmpty()) {
        Spacer(Modifier.height(Tokens.s3))
        LabCard(title = "Kết quả quét gần đây") {
            session.lines.forEach { line ->
                Column(Modifier.fillMaxWidth().padding(bottom = Tokens.s2)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LabChip(text = line.nhanKetQua, tone = scanToneOf(line.outcome))
                        Spacer(Modifier.width(Tokens.s2))
                        Text(
                            text = line.kien,
                            style = MaterialTheme.typography.subtitle2,
                            color = AppTheme.lab.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    line.giaiThich?.let { giaiThich ->
                        Text(
                            text = giaiThich,
                            style = MaterialTheme.typography.caption,
                            color = AppTheme.lab.ink2,
                            modifier = Modifier.padding(top = Tokens.s1),
                        )
                    }
                    val phu = listOfNotNull(
                        line.job?.let { "YC $it" },
                        line.chuyen?.let { "Chuyến $it" },
                        line.maXuat?.let { "Tem xuất $it" },
                    )
                    if (phu.isNotEmpty()) {
                        Text(
                            text = phu.joinToString(" · "),
                            style = MaterialTheme.typography.caption,
                            color = AppTheme.lab.ink2,
                        )
                    }
                }
            }
        }
    }

    // Hoàn tất: CHỈ bật khi server đã cho biết tổng và đã quét đủ (và chưa bấm hoàn tất). N=0 ⇒ ẩn.
    if (session.tong > 0) {
        Spacer(Modifier.height(Tokens.s4))
        Button(
            onClick = { vm.hoanTatNhan(job.id) },
            enabled = session.duKien && !session.daHoanTat,
            modifier = Modifier.fillMaxWidth().height(Tokens.touchPrimary),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = AppTheme.lab.accent,
                contentColor = AppTheme.lab.onAccent,
            ),
        ) {
            Text(
                when {
                    session.daHoanTat -> "Đã hoàn tất nhận (${session.daQuet}/${session.tong} kiện)"
                    session.duKien -> "Xác nhận OK · Chuyển qua bước tiếp theo"
                    else -> "Còn kiện chưa quét"
                },
            )
        }
        if (session.duKien && !session.daHoanTat && onOpenXuat != null) {
            Spacer(Modifier.height(Tokens.s2))
            Button(
                onClick = {
                    vm.hoanTatNhan(job.id)
                    onOpenXuat()
                },
                modifier = Modifier.fillMaxWidth().height(Tokens.touchSecondary),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AppTheme.lab.surface2,
                    contentColor = AppTheme.lab.ink,
                ),
            ) {
                Text("Xác nhận OK & Mở màn Xuất kho (Bước 9)")
            }
        }
        Text(
            text = "Hoàn tất chỉ xác nhận NỘI BỘ và đóng chi tiết — KHÔNG gọi lại API nhận hàng " +
                "(việc đã ở trạng thái Đã về kho, gọi lại chỉ có nguy cơ sinh phiếu thứ hai).",
            style = MaterialTheme.typography.caption,
            color = AppTheme.lab.ink2,
            modifier = Modifier.padding(top = Tokens.s2),
        )
    }
}
