package vn.lab.logistics.feature.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.lab.logistics.core.model.DriverJob
import vn.lab.logistics.core.model.JobState
import vn.lab.logistics.core.repo.MIN_FAIL_REASON_LENGTH
import vn.lab.logistics.core.repo.isFailReasonValid
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Lab
import vn.lab.logistics.design.Tokens
import vn.lab.logistics.ui.components.CalloutBox
import vn.lab.logistics.ui.components.DuoArrangement
import vn.lab.logistics.ui.components.DuoScaffold
import vn.lab.logistics.ui.components.KeyboardHelpDialog
import vn.lab.logistics.ui.components.StateHost
import vn.lab.logistics.ui.components.Tone
import vn.lab.logistics.ui.kit.LabCard
import vn.lab.logistics.ui.kit.LabCellStyle
import vn.lab.logistics.ui.kit.LabEmptyState
import vn.lab.logistics.ui.kit.LabField
import vn.lab.logistics.ui.kit.LabRowDivider
import vn.lab.logistics.ui.kit.LabStatusChip
import vn.lab.logistics.ui.kit.LabToolbar
import vn.lab.logistics.ui.kit.labOutlinedButtonBorder
import vn.lab.logistics.ui.kit.labOutlinedButtonColors
import vn.lab.logistics.ui.kit.labTextFieldColors

/**
 * =====================================================================================
 * MÀN DUO UI — "Việc hôm nay" của tài xế (docs/04 §5.1, §4).
 *
 * Dữ liệu đi qua interface [vn.lab.logistics.core.repo.DriverJobsRepo] — có thể là repo MẪU hoặc
 * repo gọi THẬT `GET /lab/api/v1/driver/jobs`. Màn hình này **không biết** đang ở nguồn nào;
 * nó chỉ nhận [dataSourceLabel] (hiện trong dòng phụ của toolbar) và [onFallbackToSample]
 * (đường lui bắt buộc của Luật 8).
 *
 * GIAO DIỆN ĐÃ LÀM LẠI (brief hạng mục 2): thay khung tự chế bằng bộ kit —
 *  - toolbar [LabToolbar] (tiêu đề + dòng phụ + ô tìm + hành động) thay thanh công cụ tự xếp;
 *  - danh sách cao ≥ 44dp, **chỉ kẻ đường ngang** ([LabRowDivider]), chip trạng thái bo 4dp,
 *    dòng đang mở có nền nhấn 10% — cùng ngôn ngữ với nav và bảng của dashboard;
 *  - pane chi tiết là các [LabCard] (thông tin lấy hàng / ghi chú / hành động), không còn
 *    field rời rạc không có khung.
 * KHÔNG đổi: luồng ghi (accept / capture / fail), phím tắt, state lifted trong ViewModel,
 * đường lui về dữ liệu mẫu.
 *
 * PHÍM TẮT (docs/04 §11) — chỉ bật khi con trỏ KHÔNG nằm trong ô nhập liệu:
 *   Ctrl/Cmd+K → focus ô tìm · J/↓ · K/↑ → di chuyển · Enter → mở chi tiết · Esc → đóng
 *   R → tải lại · ? → bảng trợ giúp
 *
 * TÀI KHOẢN ĐANG ĐĂNG NHẬP TRÊN MÁY: góc trên là "Xin chào <tên>" + dòng nhỏ
 * "Tài khoản: <login>" ([driverGreeting] / [driverSubtitle], dữ liệu từ `AppSettings`) — máy
 * dùng chung theo ca thì tài xế phải biết mình đang gửi biên nhận bằng tài khoản nào.
 *
 * LUỒNG CAPTURE (C4) — mỗi state chỉ có MỘT hành động chính:
 *  - "Bắt đầu đi"              (state ∈ new/assigned)      → `vm.accept` → `POST …/accept`
 *  - "Chụp ảnh & ký biên nhận" (state ∈ assigned/en_route) → mở màn capture
 *  - "Không lấy được"          (state ∈ assigned/en_route/picked_up) → lý do ≥ 5 ký tự → `vm.fail`
 * State mới LUÔN do server trả (app không tự đổi), rồi danh sách được tải lại.
 * =====================================================================================
 */
@Composable
fun DriverJobsScreen(
    vm: DriverJobsViewModel,
    widthDp: Int,
    onExitToLogin: () -> Unit,
    dataSourceLabel: String = "Mẫu",
    onFallbackToSample: (() -> Unit)? = null,
    /**
     * Tên tài xế đang đăng nhập (từ `AppSettings.driverName`) — hiện thành câu chào
     * "Xin chào <tên>" ở góc trên. Rỗng ⇒ tiêu đề cũ "Việc hôm nay" (chế độ mẫu không có
     * tài khoản thật, không được bịa tên).
     */
    accountName: String = "",
    /**
     * Tên đăng nhập đã lưu sau khi đăng nhập (`AppSettings.login`). Hiện thành dòng nhỏ ngay
     * dưới câu chào: tài xế cầm máy phải biết máy này đang đăng nhập bằng TÀI KHOẢN NÀO —
     * hai tài xế dùng chung một bộ máy mà gửi biên nhận sai tài khoản là lỗi không ai truy ra.
     */
    accountLogin: String = "",
    /** Mở màn capture cho một việc (App.kt quyết định route). */
    onOpenCapture: (DriverJob) -> Unit = {},
    /** Câu thông báo một lần (vd "Đã gửi biên nhận cho LH/26/0005 — 4 kiện, 12,5 kg"). */
    flashMessage: String? = null,
    onFlashShown: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val rootFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    var helpVisible by remember { mutableStateOf(false) }
    var failDialogJob by remember { mutableStateOf<DriverJob?>(null) }
    val arrangement = DuoArrangement.fromWidth(widthDp)
    // Điện thoại (< 600dp): toolbar xếp hành động xuống hàng riêng + pane chi tiết xếp nút dọc.
    val compact = widthDp < Tokens.BREAKPOINT_COMPACT_DP

    LaunchedEffect(vm) {
        // Lần đầu vào màn thì tải; quay về từ màn CAPTURE thì App.kt đã tải lại rồi
        // (tránh nháy trắng đúng lúc Snackbar "Đã gửi biên nhận…" hiện — xem `loadIfNeeded`).
        vm.loadIfNeeded(scope)
        // Phòng thủ: nếu modifier node chưa attach (một số bản Compose), requestFocus ném
        // IllegalStateException → app sẽ chết ngay khi mở. Bỏ qua thay vì sập.
        runCatching { rootFocus.requestFocus() }
    }

    // Hiện Snackbar qua `scope` của màn hình (KHÔNG phải scope của LaunchedEffect): nếu hiện
    // bằng scope của effect thì việc xoá `notice`/`flashMessage` sẽ huỷ luôn snackbar đang chạy.
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.lab.ground)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                handleShortcut(
                    event = event,
                    vm = vm,
                    arrangement = arrangement,
                    scope = scope,
                    searchFocus = searchFocus,
                    rootFocus = rootFocus,
                    notify = notify,
                    showHelp = { helpVisible = true },
                )
            },
    ) {
        DuoScaffold(
            widthDp = widthDp,
            hasSelection = vm.openedId != null,
            onBack = {
                vm.closeDetail()
                rootFocus.requestFocus()
            },
            topBar = {
                LabToolbar(
                    // Góc trên: "Xin chào <tên>" + dòng nhỏ "<tài khoản>". Tên rỗng (chế độ mẫu)
                    // ⇒ giữ tiêu đề nghiệp vụ cũ, KHÔNG bịa tên tài xế.
                    title = driverGreeting(accountName),
                    subtitle = driverSubtitle(
                        accountLogin = accountLogin,
                        jobCount = vm.visibleJobs().size,
                        dataSourceLabel = dataSourceLabel,
                    ),
                    stacked = compact,
                    filters = {
                        SearchField(
                            vm = vm,
                            searchFocus = searchFocus,
                            placeholderText = if (compact) {
                                "Tìm shop, mã việc"
                            } else {
                                "Tìm shop, mã việc, địa chỉ (Ctrl+K)"
                            },
                            modifier = if (compact) Modifier.fillMaxWidth() else Modifier.width(Tokens.searchFieldWidth),
                        )
                    },
                    actions = {
                        ToolbarTextButton("Tải lại", onRefresh = { vm.load(scope) })
                        if (!compact) {
                            ToolbarTextButton("Phím tắt (?)", onRefresh = { helpVisible = true })
                        }
                        ToolbarTextButton("Đăng xuất", onRefresh = onExitToLogin)
                    },
                )
            },
            snackbar = snackbar,
            overlayTitle = vm.detailJob()?.ref ?: "Chi tiết việc",
            listPane = {
                DriverListPane(
                    vm = vm,
                    onOpen = { id -> vm.open(id) },
                    onFallbackToSample = onFallbackToSample,
                )
            },
            detailPane = { selected ->
                DriverDetailPane(
                    vm = vm,
                    selected = selected,
                    scope = scope,
                    onOpenCapture = onOpenCapture,
                    onFailRequested = { job -> failDialogJob = job },
                    notify = notify,
                    compact = compact,
                )
            },
        )

        if (helpVisible) {
            KeyboardHelpDialog(
                onDismiss = {
                    helpVisible = false
                    rootFocus.requestFocus()
                },
            )
        }

        // Hộp thoại "Không lấy được": lý do BẮT BUỘC ≥ 5 ký tự (backend chỉ kiểm "không rỗng",
        // nhưng CS cần lý do đọc được để xử lý ngoại lệ — docs/07).
        failDialogJob?.let { job ->
            FailReasonDialog(
                job = job,
                busy = vm.busyJobId == job.id,
                onDismiss = { failDialogJob = null },
                onConfirm = { reason ->
                    vm.fail(job.id, reason, scope)
                    failDialogJob = null
                },
            )
        }
    }
}

/** Nút chữ của toolbar — gọn nhẹ, cùng màu, không phải `Button` đầy. */
@Composable
private fun ToolbarTextButton(label: String, onRefresh: () -> Unit) {
    TextButton(
        onClick = onRefresh,
        modifier = Modifier.height(36.dp),
    ) {
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

/** Ô tìm kiếm dùng chung cho cả 2 cách bố trí toolbar (không chép code 2 lần). */
@Composable
private fun SearchField(
    vm: DriverJobsViewModel,
    searchFocus: FocusRequester,
    placeholderText: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = vm.searchQuery,
        onValueChange = vm::onSearchChange,
        modifier = modifier
            .focusRequester(searchFocus)
            .onFocusChanged { vm.searchFocused = it.isFocused },
        singleLine = true,
        textStyle = MaterialTheme.typography.body2,
        colors = labTextFieldColors(),
        placeholder = {
            Text(
                text = placeholderText,
                style = MaterialTheme.typography.body2,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

// ---------------------------------------------------------------------------------------
// Pane trái: danh sách việc (bảng chỉ kẻ đường ngang)
// ---------------------------------------------------------------------------------------
@Composable
private fun DriverListPane(
    vm: DriverJobsViewModel,
    onOpen: (Int) -> Unit,
    onFallbackToSample: (() -> Unit)?,
) {
    Column(Modifier.fillMaxSize()) {
        val query = vm.searchQuery.trim()
        StateHost(
            state = vm.state,
            modifier = Modifier.weight(1f),
            emptyTitle = if (query.isEmpty()) {
                "Hôm nay chưa có việc nào"
            } else {
                "Không tìm thấy việc khớp \"$query\""
            },
            emptyHint = if (query.isEmpty()) {
                "Bấm Tải lại để lấy danh sách mới từ máy chủ."
            } else {
                "Xoá ô tìm kiếm để xem tất cả."
            },
            loadingLabel = "Đang tải việc hôm nay…",
            errorHint = "Kiểm tra Odoo đã chạy và tài khoản tài xế đã đăng nhập, rồi bấm Thử lại.",
            // Đang ở Odoo thật mà lỗi → luôn có đường lui về dữ liệu mẫu (Luật 8).
            secondaryActionLabel = if (onFallbackToSample != null) "Về dữ liệu mẫu" else null,
            onSecondaryAction = onFallbackToSample,
        ) { _ ->
            LazyColumn(
                state = vm.listState, // state của VIEWMODEL — không tạo ở đây (displacement)
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items = vm.visibleJobs(), key = { job -> job.id }) { job ->
                    DriverJobRow(
                        job = job,
                        isCursor = job.id == vm.cursorId,
                        isOpened = job.id == vm.openedId,
                        onClick = { onOpen(job.id) },
                    )
                    LabRowDivider()
                }
            }
        }

        // Chân pane: nhắc phím tắt cho người mới (không chiếm chỗ khi cửa sổ hẹp).
        LabRowDivider()
        Text(
            text = "J/K hoặc ↑/↓ để di chuyển · Enter mở · ? xem phím tắt",
            style = MaterialTheme.typography.caption,
            color = AppTheme.lab.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.s4, vertical = Tokens.s2),
        )
    }
}

@Composable
private fun DriverJobRow(
    job: DriverJob,
    isCursor: Boolean,
    isOpened: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Tokens.radiusChip)
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
            .border(
                width = if (isCursor) Tokens.focusRing else Tokens.borderNone,
                color = if (isCursor) AppTheme.lab.accent else Tokens.transparent,
                shape = shape,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = job.windowLabel(),
                style = LabCellStyle,
                color = AppTheme.lab.ink2,
                maxLines = 1,
            )
            Text(
                text = " · ",
                style = LabCellStyle,
                color = AppTheme.lab.ink2,
            )
            Text(
                text = job.packagesLabel(),
                style = LabCellStyle,
                color = AppTheme.lab.ink2,
                maxLines = 1,
            )
            if (job.note != null) {
                Text(
                    text = " · có ghi chú",
                    style = LabCellStyle,
                    color = AppTheme.lab.ink2,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Pane phải: chi tiết việc
// ---------------------------------------------------------------------------------------
@Composable
private fun DriverDetailPane(
    vm: DriverJobsViewModel,
    selected: Boolean,
    scope: CoroutineScope,
    onOpenCapture: (DriverJob) -> Unit,
    onFailRequested: (DriverJob) -> Unit,
    notify: (String) -> Unit,
    compact: Boolean = false,
) {
    val job = vm.detailJob()
    if (!selected || job == null) {
        LabEmptyState(
            title = "Chọn một việc ở danh sách",
            hint = "Dùng J/K hoặc ↑/↓ rồi bấm Enter, hoặc bấm trực tiếp vào một dòng.",
        )
        return
    }
    val busy = vm.busyJobId == job.id
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(if (compact) Tokens.s4 else Tokens.s5),
    ) {
        // ---------------------------------------------------------------- tiêu đề việc
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

        // ------------------------------------------------------- thông tin lấy hàng
        LabCard(title = "Thông tin lấy hàng") {
            LabField("Địa chỉ lấy hàng", job.address)
            LabField("Người liên hệ", job.contactName ?: "Chưa có tên người liên hệ")
            LabField("Điện thoại", job.contactPhone ?: "Chưa có số điện thoại")
            LabField("Khung giờ hẹn", job.windowLabel())
            LabField("Số kiện dự kiến", job.packagesLabel())
        }

        if (job.note != null) {
            Spacer(Modifier.height(Tokens.s3))
            CalloutBox(
                title = "Ghi chú hiện trường",
                body = job.note,
                tone = Tone.Info,
            )
        }
        if (job.state == JobState.FAILED) {
            Spacer(Modifier.height(Tokens.s3))
            CalloutBox(
                title = "Lấy hàng thất bại",
                body = "Báo CS để đơn về kho chờ hoặc chuyển sang chuyến sau. " +
                    "Không tự xoá việc khỏi danh sách.",
                tone = Tone.Bad,
            )
        }
        if (job.state == JobState.CANCELLED) {
            Spacer(Modifier.height(Tokens.s3))
            CalloutBox(
                title = "Việc đã huỷ",
                body = "CS đã huỷ việc này — không cần tới shop. Không tự xoá khỏi danh sách.",
                tone = Tone.Warn,
            )
        }

        // Lỗi của thao tác ghi (409 INVALID_STATE, 401 phiên hết hạn, mạng…) — luôn là câu
        // tiếng Việt do `ApiException.toVietnameseMessage()` sinh ra, không lộ stacktrace.
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

        // ------------------------------------------------------------ hành động CHÍNH
        // MỖI STATE CHỈ MỘT HÀNH ĐỘNG CHÍNH (một tay, đeo găng — docs/04 §5.1).
        // Thứ tự `when` quan trọng: `assigned` phải hiện "Bắt đầu đi" (accept) TRƯỚC,
        // nhận việc xong (en_route) mới hiện "Chụp ảnh & ký biên nhận".
        when {
            job.canStart() -> {
                Button(
                    onClick = { vm.accept(job.id, scope) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(Tokens.touchPrimary),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = AppTheme.lab.accent,
                        contentColor = AppTheme.lab.onAccent,
                    ),
                ) {
                    Text(if (busy) "Đang nhận việc…" else "Bắt đầu đi")
                }
                Text(
                    text = "Nhận việc rồi chụp ảnh + ký biên nhận khi tới shop " +
                        "(máy chủ chuyển việc sang \"Đang đến lấy\").",
                    style = MaterialTheme.typography.caption,
                    color = AppTheme.lab.ink2,
                    modifier = Modifier.padding(top = Tokens.s2),
                )
            }

            job.canCapture() -> {
                Button(
                    onClick = { onOpenCapture(job) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(Tokens.touchPrimary),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = AppTheme.lab.accent,
                        contentColor = AppTheme.lab.onAccent,
                    ),
                ) {
                    Text("Chụp ảnh & ký biên nhận")
                }
                Text(
                    text = "Gửi ảnh + chữ ký + số kiện + kg thực tế. Máy chủ chuyển việc sang " +
                        "\"Đã lấy được hàng\".",
                    style = MaterialTheme.typography.caption,
                    color = AppTheme.lab.ink2,
                    modifier = Modifier.padding(top = Tokens.s2),
                )
            }

            else -> {
                Text(
                    text = job.nextStepHint(),
                    style = MaterialTheme.typography.body1,
                    color = AppTheme.lab.ink,
                )
            }
        }

        // 3 nút phụ: desktop xếp NGANG, điện thoại xếp DỌC full-width.
        if (compact) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = Tokens.s3),
                verticalArrangement = Arrangement.spacedBy(Tokens.s2),
            ) {
                DetailSecondaryButtons(
                    job = job,
                    busy = busy,
                    fullWidth = true,
                    notify = notify,
                    onFailRequested = onFailRequested,
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Tokens.s3),
                modifier = Modifier.padding(top = Tokens.s3),
            ) {
                DetailSecondaryButtons(
                    job = job,
                    busy = busy,
                    fullWidth = false,
                    notify = notify,
                    onFailRequested = onFailRequested,
                )
            }
        }

        Text(
            text = "Mọi con số trên màn này đến từ Odoo — app không tự tính cước hay tồn kho. " +
                "Nếu ô nào ghi \"Chưa rõ…\" thì server chưa có dữ liệu đó (OQ-22), app không bịa số.",
            style = MaterialTheme.typography.caption,
            color = AppTheme.lab.ink2,
            modifier = Modifier.padding(top = Tokens.s4),
        )
    }
}

/**
 * Ba nút phụ của pane chi tiết ("Gọi shop" · "Mở bản đồ" · "Không lấy được").
 *
 * Desktop xếp NGANG (`fullWidth = false`), điện thoại xếp DỌC + `fillMaxWidth()`.
 * Nhãn luôn 1 dòng + ellipsis để không bao giờ xuống dòng theo từng ký tự.
 */
@Composable
private fun DetailSecondaryButtons(
    job: DriverJob,
    busy: Boolean,
    fullWidth: Boolean,
    notify: (String) -> Unit,
    onFailRequested: (DriverJob) -> Unit,
) {
    val buttonModifier = if (fullWidth) {
        Modifier.fillMaxWidth().height(Tokens.touchSecondary)
    } else {
        Modifier.height(Tokens.touchSecondary)
    }

    OutlinedButton(
        onClick = { notify("Gọi ${job.contactPhone ?: "shop"} — app chưa mở ứng dụng gọi điện") },
        modifier = buttonModifier,
        colors = labOutlinedButtonColors(),
        border = labOutlinedButtonBorder(),
    ) {
        Text("Gọi shop", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
    OutlinedButton(
        onClick = { notify("Mở bản đồ cho địa chỉ: ${job.address}") },
        modifier = buttonModifier,
        colors = labOutlinedButtonColors(),
        border = labOutlinedButtonBorder(),
    ) {
        Text("Mở bản đồ", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
    // "Không lấy được" chỉ hiện khi server còn nhận (assigned/en_route/picked_up).
    if (job.canFail()) {
        OutlinedButton(
            onClick = { onFailRequested(job) },
            enabled = !busy,
            modifier = buttonModifier,
            colors = labOutlinedButtonColors(),
            border = labOutlinedButtonBorder(enabled = !busy),
        ) {
            Text("Không lấy được", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------------------------------
// Bàn phím
// ---------------------------------------------------------------------------------------
/**
 * Xử lý phím tắt toàn màn hình. Trả `true` = đã xử lý (không cho sự kiện đi tiếp).
 *
 * Thứ tự kiểm tra QUAN TRỌNG: Ctrl/Cmd+K trước, rồi mới tới J/K — và nếu ô tìm đang focus thì
 * nhường hết phím cho ô nhập liệu (chỉ giữ Esc). Không làm vậy, gõ "shop" vào ô tìm sẽ bị phím
 * tắt "nuốt" mất — lỗi focus kinh điển của app bàn phím-first.
 */
private fun handleShortcut(
    event: KeyEvent,
    vm: DriverJobsViewModel,
    arrangement: DuoArrangement,
    scope: CoroutineScope,
    searchFocus: FocusRequester,
    rootFocus: FocusRequester,
    notify: (String) -> Unit,
    showHelp: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    if ((event.isCtrlPressed || event.isMetaPressed) && event.key == Key.K) {
        searchFocus.requestFocus()
        return true
    }

    if (vm.searchFocused) {
        if (event.key == Key.Escape) {
            vm.searchFocused = false
            vm.clearSearch()
            rootFocus.requestFocus()
            return true
        }
        return false
    }

    val syncDetail = arrangement != DuoArrangement.Overlay
    return when (event.key) {
        Key.J, Key.DirectionDown -> {
            vm.moveCursor(delta = 1, syncDetail = syncDetail, scope = scope)
            true
        }

        Key.K, Key.DirectionUp -> {
            vm.moveCursor(delta = -1, syncDetail = syncDetail, scope = scope)
            true
        }

        Key.Enter, Key.NumPadEnter -> {
            val id = vm.cursorId
            if (id != null) {
                vm.open(id)
            } else {
                notify("Chưa có việc nào để mở")
            }
            true
        }

        Key.Escape -> {
            if (vm.openedId != null) {
                vm.closeDetail()
                rootFocus.requestFocus()
                true
            } else {
                false
            }
        }

        Key.R -> {
            vm.load(scope)
            true
        }

        // Phím "?" trên bàn phím US = Shift + "/". Không có Key.QuestionMark trong
        // androidx.compose.ui.input.key → nhận "/" và ghi chú rõ: hiện "/" chưa dùng cho việc
        // gì khác nên mở trợ giúp luôn, kể cả khi người dùng quên giữ Shift.
        Key.Slash -> {
            showHelp()
            true
        }

        else -> false
    }
}

// ---------------------------------------------------------------------------------------
// Hộp thoại "Không lấy được" — lý do bắt buộc
// ---------------------------------------------------------------------------------------
/**
 * C4 — nhập lý do "không lấy được" rồi gọi `POST …/fail`.
 *
 * Vì sao bắt buộc ≥ [MIN_FAIL_REASON_LENGTH] ký tự dù backend chỉ kiểm "không rỗng": CS dùng lý
 * do này để chạy nhánh ngoại lệ (docs/07: shop đóng cửa → chuyến sau / kho chờ). Một lý do "a"
 * làm hỏng đúng cái dữ liệu mà ngoại lệ cần. Nút Gửi bị khoá tới khi đủ ký tự và in rõ còn
 * thiếu bao nhiêu — người đeo găng không phải đoán.
 */
@Composable
private fun FailReasonDialog(
    job: DriverJob,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    val valid = isFailReasonValid(reason)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Không lấy được ${job.ref}",
                style = MaterialTheme.typography.h6,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "Lý do bắt buộc (ít nhất $MIN_FAIL_REASON_LENGTH ký tự) — CS dùng lý " +
                        "do này để xử lý ngoại lệ.",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface,
                )
                Spacer(Modifier.height(Tokens.s2))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(MAX_FAIL_REASON_LENGTH) },
                    label = { Text("Lý do") },
                    placeholder = { Text("vd Shop đóng cửa, gọi 3 lần không liên hệ được") },
                    isError = reason.isNotEmpty() && !valid,
                    colors = labTextFieldColors(error = reason.isNotEmpty() && !valid),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (reason.isNotEmpty() && !valid) {
                    Text(
                        text = "Cần ít nhất $MIN_FAIL_REASON_LENGTH ký tự " +
                            "(hiện ${reason.trim().length}).",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.error,
                        modifier = Modifier.padding(top = Tokens.s1),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason) },
                enabled = valid && !busy,
                modifier = Modifier.height(Tokens.touchSecondary),
            ) {
                Text(if (busy) "Đang gửi…" else "Gửi báo cáo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(Tokens.touchSecondary)) {
                Text("Huỷ (Esc)")
            }
        },
    )
}

/** Lý do tối đa 300 ký tự — đủ một câu nghiệp vụ, không phải ô "viết văn". */
private const val MAX_FAIL_REASON_LENGTH = 300
