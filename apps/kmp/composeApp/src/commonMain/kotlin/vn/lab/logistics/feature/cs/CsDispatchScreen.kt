package vn.lab.logistics.feature.cs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.lab.logistics.core.model.CsDriver
import vn.lab.logistics.core.model.CsJob
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Lab
import vn.lab.logistics.design.Tokens
import vn.lab.logistics.ui.components.CalloutBox
import vn.lab.logistics.ui.components.DuoArrangement
import vn.lab.logistics.ui.components.DuoScaffold
import vn.lab.logistics.ui.components.StateHost
import vn.lab.logistics.ui.components.Tone
import vn.lab.logistics.ui.kit.LabCard
import vn.lab.logistics.ui.kit.LabCellStyle
import vn.lab.logistics.ui.kit.LabEmptyState
import vn.lab.logistics.ui.kit.LabField
import vn.lab.logistics.ui.kit.LabFilterChip
import vn.lab.logistics.ui.kit.LabRowDivider
import vn.lab.logistics.ui.kit.LabSectionTitle
import vn.lab.logistics.ui.kit.LabStatusChip
import vn.lab.logistics.ui.kit.LabToolbar
import vn.lab.logistics.ui.kit.labOutlinedButtonBorder
import vn.lab.logistics.ui.kit.labOutlinedButtonColors

/**
 * =====================================================================================
 * MÀN **CS — ĐIỀU PHỐI**: danh sách việc cần gán tài xế + pane chi tiết có ô chọn tài xế.
 *
 * Luồng nghiệp vụ (docs/00, bước "CS gán tài xế") KHÔNG đổi:
 *   việc mới về → CS mở việc → chọn tài xế → bấm **"Gán tài xế"** → server ghi và trả trạng thái
 *   mới ("Đã gán tài xế") → Snackbar tiếng Việt xác nhận → item đổi trạng thái ngay tại chỗ.
 *
 * DUO UI (docs/04 §4) — cùng [DuoScaffold] với màn tài xế:
 *  - ≥ 1280dp: danh sách 400dp + chi tiết;
 *  - 900–1280dp: danh sách 300dp + chi tiết;
 *  - < 900dp: 1 pane, chọn việc thì pane chi tiết PHỦ LÊN và có nút "‹ Quay lại".
 *  - < 600dp: toolbar xếp hành động xuống hàng riêng, pane chi tiết xếp nút dọc, mọi nút ≥ 48dp.
 *
 * GIAO DIỆN: bộ kit (`LabToolbar` · `LabCard` · `LabStatusChip` · `LabField` · `LabEmptyState`) —
 * cùng ngôn ngữ với màn tài xế, không còn 3 kiểu "thanh công cụ" khác nhau trong một app.
 *
 * BỐN TRẠNG THÁI (agents/fe/AGENT.md §4): Loading · Empty · Error · Content — qua [StateHost].
 * App KHÔNG tự đổi trạng thái việc và KHÔNG tự chọn tài xế hộ CS.
 *
 * ==================== DÙNG CHUNG CHO 2 VAI TRÒ (CS và TRƯỞNG NHÓM TÀI XẾ) ==============
 * Vai trò `driver_leader` mở CÙNG màn này, chỉ khác [title]/[subtitle] do `App.kt` truyền vào
 * ("Phân công tài xế" + "<tên> · <tài khoản>"). Không nhân bản màn: cả hai vai trò làm ĐÚNG một
 * việc (chọn việc → chọn tài xế → Gán), dùng CÙNG endpoint, cùng luật trạng thái do server trả.
 * Hai bản sao chỉ tạo ra hai chỗ để lệch nhau khi nghiệp vụ đổi.
 * =====================================================================================
 */
@Composable
fun CsDispatchScreen(
    vm: CsDispatchViewModel,
    widthDp: Int,
    onExitToLogin: () -> Unit,
    dataSourceLabel: String = "Mẫu",
    onFallbackToSample: (() -> Unit)? = null,
    /** Tiêu đề trang; mặc định của vai trò CS. Vai trò trưởng nhóm truyền "Phân công tài xế". */
    title: String = "CS · Điều phối",
    /**
     * Dòng phụ dưới tiêu đề. `null` ⇒ dòng mặc định ("<n> việc cần gán tài xế · Nguồn: …").
     * Vai trò trưởng nhóm truyền "<tên> · <tài khoản>" để biết đang phân công bằng tài khoản nào.
     */
    subtitle: String? = null,
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
                title = title,
                // Dòng mặc định của vai trò CS; vai trò trưởng nhóm truyền subtitle riêng
                // ("<tên> · <tài khoản>") để biết đang phân công bằng tài khoản nào.
                subtitle = subtitle
                    ?: "${vm.jobs.size} việc cần gán tài xế · Nguồn: $dataSourceLabel",
                stacked = compact,
                actions = {
                    ToolbarAction("Tải lại") { vm.load(scope) }
                    ToolbarAction("Đăng xuất", onExitToLogin)
                },
            )
        },
        snackbar = snackbar,
        overlayTitle = vm.detailJob()?.ref ?: "Chi tiết việc",
        listPane = {
            CsListPane(
                vm = vm,
                onOpen = { id -> vm.open(id) },
                onFallbackToSample = onFallbackToSample,
            )
        },
        detailPane = { selected ->
            CsDetailPane(
                vm = vm,
                selected = selected,
                scope = scope,
                compact = compact,
            )
        },
    )
}

/** Nút chữ của toolbar — 48dp, màu nhấn, không phải `Button` đầy (không tranh chỗ với hành động chính). */
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
// Pane trái: danh sách việc cần gán
// ---------------------------------------------------------------------------------------
@Composable
private fun CsListPane(
    vm: CsDispatchViewModel,
    onOpen: (Int) -> Unit,
    onFallbackToSample: (() -> Unit)?,
) {
    Column(Modifier.fillMaxSize()) {
        // Chip lọc: Tất cả · Mới · Đã gán (kèm số việc). Chỉ hiện khi đã có dữ liệu.
        if (vm.jobs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.s4, vertical = Tokens.s2),
                horizontalArrangement = Arrangement.spacedBy(Tokens.s2),
            ) {
                CsJobFilter.values().forEach { option ->
                    LabFilterChip(
                        text = "${option.label} (${option.countIn(vm.jobs)})",
                        selected = vm.filter == option,
                        onClick = { vm.selectFilter(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Lỗi của thao tác gán: hiện NGAY TRÊN danh sách (không phải chỉ trong pane chi tiết)
        // để CS đang ở overlay vẫn thấy mình chưa gán được.
        vm.actionError?.let { message ->
            CalloutBox(
                title = "Không gán được tài xế",
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
            emptyTitle = "Không còn việc nào cần gán tài xế",
            emptyHint = "Bấm Tải lại khi CS tạo thêm việc mới.",
            loadingLabel = "Đang tải việc cần gán…",
            errorHint = "Kiểm tra Odoo đã chạy và tài khoản đăng nhập có vai trò CS, rồi bấm Thử lại.",
            secondaryActionLabel = if (onFallbackToSample != null) "Về dữ liệu mẫu" else null,
            onSecondaryAction = onFallbackToSample,
        ) { _ ->
            val shown = vm.visibleJobs
            if (shown.isEmpty()) {
                // Có việc nhưng chip lọc đang chọn không khớp việc nào — không phải màn trắng.
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
                        CsJobRow(
                            job = job,
                            isOpened = job.id == vm.openedId,
                            onClick = { onOpen(job.id) },
                        )
                        LabRowDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun CsJobRow(
    job: CsJob,
    isOpened: Boolean,
    onClick: () -> Unit,
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
            LabStatusChip(label = job.statusLabel())
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
            text = "${job.windowLabel()} · ${job.packagesLabel()}",
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
    }
}

// ---------------------------------------------------------------------------------------
// Pane phải: chi tiết việc + ô chọn tài xế + nút Gán
// ---------------------------------------------------------------------------------------
@Composable
private fun CsDetailPane(
    vm: CsDispatchViewModel,
    selected: Boolean,
    scope: CoroutineScope,
    compact: Boolean = false,
) {
    val job = vm.detailJob()
    if (!selected || job == null) {
        LabEmptyState(
            title = "Chọn một việc ở danh sách",
            hint = "Bấm trực tiếp vào dòng để xem địa chỉ, khung giờ và gán tài xế.",
        )
        return
    }
    val busy = vm.busyJobId == job.id
    val driverDaChon = vm.selectedDriver()

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
            LabStatusChip(label = job.statusLabel())
        }

        Spacer(Modifier.height(Tokens.s4))

        LabCard(title = "Thông tin lấy hàng") {
            LabField("Địa chỉ lấy hàng", job.address)
            LabField("Người liên hệ / SĐT", job.contactPhone ?: "Chưa có số điện thoại")
            LabField("Khung giờ hẹn", job.windowLabel())
            LabField("Số kiện dự kiến", job.packagesLabel())
            LabField("Tài xế hiện tại", job.driverLabel())
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

        LabSectionTitle(text = "Gán tài xế", hint = driverDaChon?.name ?: "chưa chọn")
        Spacer(Modifier.height(Tokens.s2))

        DriverPicker(
            drivers = vm.drivers,
            selectedId = vm.selectedDriverId,
            enabled = !busy,
            onSelect = { id -> vm.selectDriver(id) },
            modifier = Modifier.fillMaxWidth(),
        )

        // Tài khoản đăng nhập của tài xế ĐANG CHỌN. Người phân công cần đọc được tên tài khoản
        // để dặn tài xế đăng nhập app bằng đúng tài khoản đó trên máy đang cầm. Server cũ chưa
        // trả `login` ⇒ ẩn hẳn dòng này (không hiện "Tài khoản: null").
        driverDaChon?.accountLine()?.let { accountLine ->
            Text(
                text = accountLine,
                style = MaterialTheme.typography.caption,
                color = AppTheme.lab.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Tokens.s2),
            )
        }

        Spacer(Modifier.height(Tokens.s3))

        // Nút chính >= 56dp (tay đeo găng) — cùng ngưỡng với nút "Bắt đầu đi" của tài xế.
        // Trạng thái (yêu cầu 2026-11-02): gán THÀNH CÔNG xong thì nút phải khoá lại và đổi
        // chữ thành "Đã gán" — trước đây vẫn sáng "Gán tài xế" khiến người dùng bấm lại.
        val daGan = vm.daGanChoDriverDangChon
        Button(
            onClick = { vm.assign(scope) },
            enabled = !busy && vm.selectedDriverId != null && !daGan,
            modifier = Modifier.fillMaxWidth().height(Tokens.touchPrimary),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = AppTheme.lab.accent,
                contentColor = AppTheme.lab.onAccent,
            ),
        ) {
            Text(
                when {
                    busy -> "Đang gán…"
                    daGan -> "Đã gán"
                    else -> "Gán tài xế"
                },
            )
        }

        Text(
            text = when {
                daGan && driverDaChon != null ->
                    "Việc này đang giao cho ${driverDaChon.name}. Muốn giao lại thì chọn tài xế " +
                        "khác ở ô phía trên — nút sẽ bật lại."
                driverDaChon == null ->
                    "Chọn tài xế ở ô phía trên để bật nút \"Gán tài xế\"."
                else ->
                    "Máy chủ Odoo ghi việc cho ${driverDaChon.name} và trả trạng thái mới. " +
                        "App không tự đổi trạng thái việc."
            },
            style = MaterialTheme.typography.caption,
            color = AppTheme.lab.ink2,
            modifier = Modifier.padding(top = Tokens.s2),
        )

        Text(
            text = "Danh sách tài xế lấy từ Odoo (nhóm Tài xế đang làm việc). " +
                "Đổi tài xế rồi bấm Gán lần nữa là giao lại việc.",
            style = MaterialTheme.typography.caption,
            color = AppTheme.lab.ink2,
            modifier = Modifier.padding(top = Tokens.s4),
        )
    }
}

/**
 * Ô chọn tài xế: `OutlinedButton` mở [DropdownMenu] liệt kê `drivers` của server.
 *
 * Nhãn mỗi dòng là [displayLabel] — `"Trần Văn Hùng · taixe01"`; server cũ không trả `login`
 * thì chỉ hiện tên (không có dấu "·" treo).
 *
 * Vì sao không dùng `ExposedDropdownMenuBox`: đó là API của Material 3, còn app chốt Material 2
 * (ADR-006). Cách này chạy được cả desktop lẫn Android, cao đúng 48dp ([Tokens.touchSecondary]).
 */
@Composable
private fun DriverPicker(
    drivers: List<CsDriver>,
    selectedId: Int?,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val daChon = drivers.firstOrNull { it.id == selectedId }

    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            enabled = enabled && drivers.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(Tokens.touchSecondary),
            colors = labOutlinedButtonColors(),
            border = labOutlinedButtonBorder(enabled = enabled && drivers.isNotEmpty()),
        ) {
            Text(
                text = when {
                    daChon != null -> daChon.displayLabel()
                    drivers.isEmpty() -> "Chưa tải được danh sách tài xế"
                    else -> "Chọn tài xế"
                },
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Không dùng icon library (compose.material-icons không có trong classpath offline):
            // mũi tên typographic, không phải emoji.
            Text("▾", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            drivers.forEach { driver ->
                DropdownMenuItem(
                    onClick = {
                        onSelect(driver.id)
                        open = false
                    },
                ) {
                    Text(
                        text = driver.displayLabel(),
                        style = MaterialTheme.typography.body1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
