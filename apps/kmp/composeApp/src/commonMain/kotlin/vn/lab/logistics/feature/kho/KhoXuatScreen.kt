package vn.lab.logistics.feature.kho

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.lab.logistics.core.model.KhoXuatChuyen
import vn.lab.logistics.core.model.KhoXuatKien
import vn.lab.logistics.core.model.ScanOutcome
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
import vn.lab.logistics.ui.kit.LabRowDivider
import vn.lab.logistics.ui.kit.LabSectionTitle
import vn.lab.logistics.ui.kit.LabStatusChip
import vn.lab.logistics.ui.kit.LabToolbar
import vn.lab.logistics.ui.platform.scanQrCodeWithLiveCamera

/**
 * =====================================================================================
 * MÀN **KHO · XUẤT KHO** — kho quét tem xuất rồi xác nhận hàng rời kho (chốt 2026-11-02).
 *
 * Vì sao màn này tách khỏi "Kho · Nhận hàng": hai mốc khác nhau của cùng chuỗi tem (nhận = kiện vào
 * kho; xuất = kiện rời kho). Gộp một màn sẽ phải nhồi 2 luồng vào một chỗ, và thủ kho dễ bấm nhầm
 * (validate nhầm phiếu xuất = sai tồn kho).
 *
 * Bố cục dùng lại duo UI: danh sách chuyến (trái) + chi tiết chuyến (phải), overlay khi hẹp.
 * =====================================================================================
 */
@Composable
fun KhoXuatScreen(
    vm: KhoXuatViewModel,
    widthDp: Int,
    onExitToLogin: () -> Unit,
    /** Điều hướng sang màn Nhận hàng — giữ nguyên cấu trúc nav 3 tab (không thêm tab thứ 4). */
    onOpenNhan: (() -> Unit)? = null,
    dataSourceLabel: String = "Mẫu",
    onFallbackToSample: (() -> Unit)? = null,
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
                title = "Kho · Xuất kho",
                subtitle = "${vm.chuyenList.size} chuyến chờ xuất · Nguồn: $dataSourceLabel",
                stacked = compact,
                actions = {
                    ToolbarAction("Tải lại") { vm.load(scope) }
                    if (onOpenNhan != null) ToolbarAction("Nhận hàng", onOpenNhan)
                    ToolbarAction("Đăng xuất", onExitToLogin)
                },
            )
        },
        snackbar = snackbar,
        overlayTitle = vm.detail?.chuyen ?: "Chi tiết chuyến chờ xuất",
        listPane = {
            KhoXuatListPane(
                vm = vm,
                scope = scope,
                onOpen = { id -> vm.open(id, scope) },
                onFallbackToSample = onFallbackToSample,
            )
        },
        detailPane = { selected ->
            KhoXuatDetailPane(
                vm = vm,
                selected = selected,
                scope = scope,
                compact = compact,
            )
        },
    )

    // Hộp thoại BẮT BUỘC nhập lý do khi xuất thiếu kiện (quyết định M9).
    if (vm.lyDoDialogOpen) {
        XuatThieuDialog(vm = vm, scope = scope)
    }

    val currentChuyen = vm.detail
    if (vm.printDialogOpen && currentChuyen != null) {
        val ticketItems = currentChuyen.kien.map { kien ->
            val maXuat = kien.maXuat?.takeIf { it.isNotBlank() }
            QrTicketItem(
                qrCode = maXuat ?: kien.ma,
                maKien = kien.ma,
                maXuat = maXuat,
                daQuet = kien.daXuat,
                statusLabel = kien.nhanTrangThai,
            )
        }
        QrLabelPrintDialog(
            title = "Màn in tem mã QR xuất kho",
            subtitle = listOfNotNull(currentChuyen.chuyen, currentChuyen.diemDen).joinToString(" · "),
            metaLine = "Tiến độ xuất: ${currentChuyen.daQuet}/${currentChuyen.soKien} kiện",
            items = ticketItems,
            loading = vm.detailLoading,
            busy = vm.scanBusy,
            onScanItem = { code -> vm.quetMa(code, scope) },
            onSwitchToScanner = { vm.switchPrintToScanner() },
            onDismiss = { vm.closePrintDialog() },
        )
    }

    if (vm.scannerDialogOpen && currentChuyen != null) {
        val ticketItems = currentChuyen.kien.map { kien ->
            val maXuat = kien.maXuat?.takeIf { it.isNotBlank() }
            QrTicketItem(
                qrCode = maXuat ?: kien.ma,
                maKien = kien.ma,
                maXuat = maXuat,
                daQuet = kien.daXuat,
                statusLabel = kien.nhanTrangThai,
            )
        }
        QrPhoneScannerDialog(
            title = "Quét mã QR xuất kho",
            subtitle = listOfNotNull(currentChuyen.chuyen, currentChuyen.diemDen).joinToString(" · "),
            nextStepHint = "Đã quét đủ ${currentChuyen.daQuet}/${currentChuyen.soKien} tem QR xuất cho chuyến ${currentChuyen.chuyen}. Bấm Xác nhận OK để hoàn tất phiếu xuất kho (WH/OUT) và chuyển qua bước tiếp theo.",
            confirmDoneLabel = "Xác nhận OK · Hoàn tất xuất kho",
            items = ticketItems,
            daQuet = currentChuyen.daQuet,
            tong = currentChuyen.soKien,
            busy = vm.scanBusy || vm.busyXuat,
            error = vm.scanError,
            onScanCode = { code -> vm.quetMa(code, scope) },
            onOpenPrint = { vm.openPrintDialog() },
            onConfirmDone = {
                vm.closeScannerDialog()
                vm.xacNhan(scope)
            },
            onDismiss = { vm.closeScannerDialog() },
        )
    }
}

@Composable
private fun ToolbarAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.height(Tokens.touchSecondary)) {
        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            color = AppTheme.lab.ink,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------------------
// Pane trái: danh sách chuyến chờ xuất
// ---------------------------------------------------------------------------------------
@Composable
private fun KhoXuatListPane(
    vm: KhoXuatViewModel,
    scope: CoroutineScope,
    onOpen: (Int) -> Unit,
    onFallbackToSample: (() -> Unit)?,
) {
    StateHost(
        state = vm.state,
        loadingLabel = "Đang tải chuyến chờ xuất…",
        emptyTitle = "Không có chuyến nào chờ xuất kho",
        emptyHint = "Chuyến có kiện đã in tem xuất sẽ hiện ở đây sau khi chốt booking.",
        errorHint = "Kiểm tra Odoo đã chạy và tài khoản đăng nhập có vai trò Kho, rồi bấm Thử lại.",
        secondaryActionLabel = if (onFallbackToSample != null) "Về dữ liệu mẫu" else null,
        onSecondaryAction = onFallbackToSample,
    ) { list ->
        LazyColumn(state = vm.listState, modifier = Modifier.fillMaxSize()) {
            items(list, key = { it.chuyenId }) { chuyen ->
                KhoXuatListItem(
                    chuyen = chuyen,
                    isOpened = vm.openedId == chuyen.chuyenId,
                    onClick = { onOpen(chuyen.chuyenId) },
                )
                LabRowDivider()
            }
        }
    }
}

@Composable
private fun KhoXuatListItem(
    chuyen: KhoXuatChuyen,
    isOpened: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isOpened) AppTheme.lab.accent.copy(alpha = Lab.NAV_ACTIVE_TINT)
                else Tokens.transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Tokens.s4, vertical = Tokens.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = chuyen.chuyen,
                style = MaterialTheme.typography.subtitle2,
                color = AppTheme.lab.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Tokens.s2))
            LabStatusChip(label = chuyen.stateLabel ?: "Chờ xuất")
        }
        chuyen.diemDen?.let { diem ->
            Text(
                text = "Điểm đến: $diem",
                style = LabCellStyle,
                color = AppTheme.lab.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Tokens.s1),
            )
        }
        Text(
            text = chuyen.tienDoLabel,
            style = LabCellStyle,
            color = AppTheme.lab.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Tokens.s1),
        )
        Spacer(Modifier.height(Tokens.s2))
        LinearProgressIndicator(
            progress = chuyen.tiLe,
            modifier = Modifier.fillMaxWidth().height(Tokens.s1),
            color = AppTheme.lab.accent,
            backgroundColor = AppTheme.lab.surface2,
        )
        Spacer(Modifier.height(Tokens.s2))
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(Tokens.touchSecondary),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = AppTheme.lab.accent,
                contentColor = AppTheme.lab.onAccent,
            ),
        ) {
            Text("Mở")
        }
    }
}

// ---------------------------------------------------------------------------------------
// Pane phải: chi tiết chuyến + quét tem xuất + xác nhận
// ---------------------------------------------------------------------------------------
@Composable
private fun KhoXuatDetailPane(
    vm: KhoXuatViewModel,
    selected: Boolean,
    scope: CoroutineScope,
    compact: Boolean = false,
) {
    val chuyen = vm.detail
    if (!selected || chuyen == null) {
        LabEmptyState(
            title = "Chọn một chuyến ở danh sách",
            hint = "Bấm trực tiếp vào chuyến để quét tem xuất và xác nhận hàng rời kho.",
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(if (compact) Tokens.s4 else Tokens.s5),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = chuyen.chuyen,
                    style = MaterialTheme.typography.h5,
                    color = AppTheme.lab.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                chuyen.diemDen?.let { diem ->
                    Text(
                        text = "Điểm đến: $diem",
                        style = MaterialTheme.typography.subtitle1,
                        color = AppTheme.lab.ink2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Tokens.s1),
                    )
                }
            }
            Spacer(Modifier.width(Tokens.s3))
            LabStatusChip(label = chuyen.stateLabel ?: "Chờ xuất")
        }

        Spacer(Modifier.height(Tokens.s4))

        LabCard(title = "Tổng quan chuyến") {
            LabField("Số kiện của chuyến", "${chuyen.soKien} kiện")
            LabField("Đã xuất", "${chuyen.daQuet}/${chuyen.soKien} kiện")
            if (chuyen.conLai > 0) {
                LabField("Còn lại", "${chuyen.conLai} kiện chưa xuất")
            }
        }

        if (vm.detailLoading) {
            Spacer(Modifier.height(Tokens.s2))
            Text(
                text = "Đang lấy trạng thái kiện mới nhất…",
                style = MaterialTheme.typography.caption,
                color = AppTheme.lab.ink2,
            )
        }

        vm.actionError?.let { message ->
            Spacer(Modifier.height(Tokens.s3))
            CalloutBox(
                title = "Chưa xuất được",
                body = message,
                tone = Tone.Bad,
                actionLabel = "Đóng",
                onAction = vm::clearActionError,
            )
        }

        Spacer(Modifier.height(Tokens.s3))

        // ---- Danh sách kiện của chuyến ----
        var cameraScanning by remember { mutableStateOf(false) }
        var cameraError by remember { mutableStateOf<String?>(null) }

        LabSectionTitle(text = "Tem mã QR xuất & Quét QR", hint = chuyen.tienDoLabel)
        Spacer(Modifier.height(Tokens.s2))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.s2),
        ) {
            Button(
                onClick = { vm.openPrintDialog() },
                modifier = Modifier.weight(1f).height(Tokens.touchSecondary),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AppTheme.lab.surface2,
                    contentColor = AppTheme.lab.ink,
                ),
            ) {
                Text(
                    text = "In / Lưu ảnh tem QR xuất",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!chuyen.duKien) {
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
                                        vm.quetMa(decoded, scope)
                                    }
                                },
                                onFailure = { err ->
                                    cameraError = err.message ?: "Không quét được mã QR từ Camera."
                                },
                            )
                        }
                    },
                    enabled = !vm.scanBusy && !cameraScanning,
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
        LabCard(title = "Mã kiện · mã tem xuất") {
            if (chuyen.kien.isEmpty()) {
                Text(
                    text = "Chuyến này chưa có kiện nào được in tem xuất.",
                    style = MaterialTheme.typography.body2,
                    color = AppTheme.lab.ink2,
                )
            } else {
                chuyen.kien.forEach { kien ->
                    KienRow(kien = kien)
                }
            }
        }

        // ---- Quét tem xuất ----
        Spacer(Modifier.height(Tokens.s3))
        if (chuyen.duKien) {
            CalloutBox(
                title = "Đã quét đủ ${chuyen.daQuet}/${chuyen.soKien} tem QR xuất — OK!",
                body = "Đã quét đủ toàn bộ kiện của chuyến ${chuyen.chuyen}. Bấm Xác nhận OK bên dưới để hoàn tất xuất kho và chuyển qua bước tiếp theo.",
                tone = Tone.Ok,
            )
        } else {
            LabCard(title = "Quét mã QR bằng Camera hoặc nhập tay mã xuất") {
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
                                        vm.quetMa(decoded, scope)
                                    }
                                },
                                onFailure = { err ->
                                    cameraError = err.message ?: "Không quét được mã QR từ Camera."
                                },
                            )
                        }
                    },
                    enabled = !vm.scanBusy && !cameraScanning,
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
                    value = vm.scanInput,
                    onValueChange = vm::onScanInputChange,
                    label = { Text("Hoặc nhập tay mã tem xuất (XUAT/26/…) / mã kiện") },
                    placeholder = { Text("Không muốn quét Camera thì gõ mã rồi Enter") },
                    singleLine = true,
                    isError = vm.scanError != null || cameraError != null,
                    enabled = !vm.scanBusy && !cameraScanning,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.quet(scope) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Tokens.s2))
                Button(
                    onClick = { vm.quet(scope) },
                    enabled = !vm.scanBusy && !cameraScanning,
                    modifier = Modifier.fillMaxWidth().height(Tokens.touchSecondary),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = AppTheme.lab.surface2,
                        contentColor = AppTheme.lab.ink,
                    ),
                ) {
                    Text(if (vm.scanBusy) "Đang gửi…" else "Gửi mã nhập tay")
                }
                (cameraError ?: vm.scanError)?.let { message ->
                    Spacer(Modifier.height(Tokens.s2))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.caption,
                        color = AppTheme.lab.danger,
                    )
                }
            }
        }

        if (vm.scanLines.isNotEmpty()) {
            Spacer(Modifier.height(Tokens.s3))
            LabCard(title = "Kết quả quét gần đây") {
                vm.scanLines.forEach { line ->
                    Column(Modifier.fillMaxWidth().padding(bottom = Tokens.s2)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LabChip(text = line.nhanKetQua, tone = scanToneOf(line.outcome))
                            Spacer(Modifier.width(Tokens.s2))
                            Text(
                                text = line.maXuat ?: line.kien,
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
                    }
                }
            }
        }

        // ---- Tiến độ + nút xác nhận ----
        Spacer(Modifier.height(Tokens.s3))
        LabCard(title = "Tiến độ xuất kho") {
            Text(
                text = chuyen.tienDoLabel,
                style = MaterialTheme.typography.subtitle1,
                color = AppTheme.lab.ink,
            )
            Spacer(Modifier.height(Tokens.s2))
            LinearProgressIndicator(
                progress = chuyen.tiLe,
                modifier = Modifier.fillMaxWidth().height(Tokens.s2),
                color = AppTheme.lab.accent,
                backgroundColor = AppTheme.lab.surface2,
            )
        }

        Spacer(Modifier.height(Tokens.s4))
        Button(
            onClick = { vm.xacNhan(scope) },
            enabled = !vm.busyXuat,
            modifier = Modifier.fillMaxWidth().height(Tokens.touchPrimary),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = AppTheme.lab.accent,
                contentColor = AppTheme.lab.onAccent,
            ),
        ) {
            Text(
                when {
                    vm.busyXuat -> "Đang xác nhận…"
                    chuyen.duKien -> "Xác nhận OK · Hoàn tất xuất kho & Chuyển bước tiếp theo"
                    else -> "Xác nhận xuất (thiếu kiện)"
                },
            )
        }
        Text(
            text = if (chuyen.duKien) {
                "Đủ ${chuyen.soKien} kiện — xác nhận sẽ validate phiếu xuất kho (WH/OUT) và trừ tồn."
            } else {
                "Còn ${chuyen.conLai} kiện chưa quét. Có thể xuất thiếu nhưng BẮT BUỘC nhập lý do — " +
                    "kiện thiếu sẽ được đánh dấu Không xuất."
            },
            style = MaterialTheme.typography.caption,
            color = AppTheme.lab.ink2,
            modifier = Modifier.padding(top = Tokens.s2),
        )
        Spacer(Modifier.height(Tokens.s4))
    }
}

@Composable
private fun KienRow(
    kien: KhoXuatKien,
    canScan: Boolean = false,
    onScan: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Tokens.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = kien.ma,
                style = MaterialTheme.typography.subtitle2,
                color = AppTheme.lab.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            kien.maXuat?.let { maXuat ->
                Text(
                    text = "Tem xuất: $maXuat",
                    style = MaterialTheme.typography.caption,
                    color = AppTheme.lab.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(Tokens.s2))
        LabChip(text = kien.nhanTrangThai, tone = kienToneOf(kien.trangThai))
        if (canScan) {
            Spacer(Modifier.width(Tokens.s1))
            TextButton(
                onClick = onScan,
                modifier = Modifier.height(Tokens.touchSecondary),
            ) {
                Text(
                    text = "Quét",
                    style = MaterialTheme.typography.body2,
                    color = AppTheme.lab.accent,
                )
            }
        }
    }
}

/** Chip trạng thái kiện: đã xuất = Ok · đã in tem = Info · trong kho = Warn · huỷ = Bad. */
private fun kienToneOf(trangThai: String?): Tone = when (trangThai) {
    "shipped" -> Tone.Ok
    "packed" -> Tone.Info
    "in_stock" -> Tone.Warn
    "cancelled" -> Tone.Bad
    else -> Tone.Info
}

// ---------------------------------------------------------------------------------------
// Hộp thoại nhập lý do xuất thiếu (quyết định M9 — bắt buộc)
// ---------------------------------------------------------------------------------------
@Composable
private fun XuatThieuDialog(
    vm: KhoXuatViewModel,
    scope: CoroutineScope,
) {
    AlertDialog(
        onDismissRequest = { vm.dongHopThoaiLyDo() },
        title = { Text("Xuất thiếu kiện") },
        text = {
            Column {
                Text(
                    text = if (vm.kienConThieu.isEmpty()) {
                        "Chưa quét đủ kiện của chuyến. Nhập lý do để vẫn xuất phần đã quét."
                    } else {
                        "Còn ${vm.kienConThieu.size} kiện chưa quét tem xuất. " +
                            "Nhập lý do để xuất phần còn lại (kiện thiếu sẽ được đánh dấu Không xuất)."
                    },
                    style = MaterialTheme.typography.body2,
                    color = AppTheme.lab.ink,
                )
                if (vm.kienConThieu.isNotEmpty()) {
                    Spacer(Modifier.height(Tokens.s2))
                    Text(
                        text = vm.kienConThieu.joinToString(", "),
                        style = MaterialTheme.typography.caption,
                        color = AppTheme.lab.ink2,
                    )
                }
                Spacer(Modifier.height(Tokens.s3))
                OutlinedTextField(
                    value = vm.lyDoText,
                    onValueChange = vm::onLyDoChange,
                    label = { Text("Lý do xuất thiếu (bắt buộc)") },
                    placeholder = { Text("vd: kiện rách, khách rút 1 kiện, kiện kẹt ở kho") },
                    singleLine = true,
                    isError = vm.lyDoError != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                vm.lyDoError?.let { loi ->
                    Spacer(Modifier.height(Tokens.s1))
                    Text(
                        text = loi,
                        style = MaterialTheme.typography.caption,
                        color = AppTheme.lab.danger,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.xacNhanThieu(scope) }) {
                Text("Xuất thiếu")
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.dongHopThoaiLyDo() }) {
                Text("Quay lại")
            }
        },
    )
}
