package vn.lab.logistics.feature.capture

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CoroutineScope
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.platformDefaults
import vn.lab.logistics.design.Tokens
import vn.lab.logistics.ui.components.CalloutBox
import vn.lab.logistics.ui.components.SignaturePad
import vn.lab.logistics.ui.components.StateHost
import vn.lab.logistics.ui.components.StatusPill
import vn.lab.logistics.ui.components.Tone
import vn.lab.logistics.ui.components.UiState
import vn.lab.logistics.ui.kit.LabChip
import vn.lab.logistics.ui.kit.LabStatusChip
import vn.lab.logistics.ui.kit.LabToolbar

/**
 * =====================================================================================
 * C3 — MÀN CAPTURE: **3 bước trong MỘT màn** (ảnh → chữ ký → xác nhận số kiện & kg).
 *
 * Đây là "cảnh wow" của video demo ("một đơn, bốn màn hình"): danh sách → bấm "Bắt đầu đi"
 * → chụp ảnh + ký biên nhận → danh sách với state mới do SERVER trả.
 *
 * BỐN TRẠNG THÁI (bắt buộc, vẽ qua [StateHost]):
 *  - Loading — đang gửi biên nhận, nút Gửi bị khoá;
 *  - Empty   — chưa chụp gì: mời chọn ảnh Hàng hoá (không phải màn trắng);
 *  - Error   — câu tiếng Việt của server/mạng + "Thử lại" **giữ nguyên dữ liệu** và **giữ
 *              nguyên `clientUuid`** (chống trùng), kèm "Sửa lại thông tin (giữ dữ liệu)";
 *  - Content — nội dung bước đang làm.
 *
 * CHỐNG BẤM NHẦM (docs/04 §5.1: một tay, đeo găng):
 *  - nút chính ≥ [Tokens.touchPrimary] (56dp), nút phụ ≥ [Tokens.touchSecondary] (48dp);
 *  - mọi nút bị khoá đều in LÝ DO ngay trên nó (không có nút "im lặng");
 *  - `Esc` = quay lại bước trước / thoát về danh sách, dữ liệu không mất.
 *
 * RESPONSIVE: ≥900dp xếp 3 ô ảnh NGANG (như bảng bằng chứng), hẹp hơn xếp DỌC.
 * =====================================================================================
 */
@Composable
fun DriverCaptureScreen(
    vm: DriverCaptureViewModel,
    widthDp: Int,
    dataSourceLabel: String,
    onBack: () -> Unit,
    onSubmitted: (CaptureSuccess) -> Unit,
    modifier: Modifier = Modifier,
    /** `true` khi nguồn dữ liệu = Mẫu ⇒ hiện cảnh báo "chế độ mẫu: chỉ mô phỏng". */
    sampleMode: Boolean = false,
) {
    val scope = rememberCoroutineScope()

    // Gửi xong → báo cho nơi lắp ráp (App.kt) về danh sách + refresh + Snackbar.
    LaunchedEffect(vm.submitted) {
        vm.submitted?.let { capture -> onSubmitted(capture) }
    }

    // Vẽ / hoàn tác / xoá nét xong thì trạng thái Empty ⇄ Content phải cập nhật theo.
    LaunchedEffect(vm.signature.strokeCount) { vm.onSignatureChanged() }

    val wide = widthDp >= Tokens.BREAKPOINT_SPLIT_NARROW_DP
    // Điện thoại (< 600dp): thanh công cụ chỉ còn "Quay lại + mã việc + 1 nhãn trạng thái",
    // và thanh hành động dùng nhãn ngắn — xem ghi chú trong từng composable.
    val compact = widthDp < Tokens.BREAKPOINT_COMPACT_DP
    val hasContent = vm.state is UiState.Content<*>
    // Điện thoại có camera ⇒ hiện nút "Chụp ảnh" (chụp trực tiếp) cạnh "Chọn ảnh" (từ thư viện).
    val canCapture = remember { platformDefaults().cameraCaptureSupported }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    if (vm.step == CaptureStep.Photos) onBack() else vm.previousStep()
                    true
                } else {
                    false
                }
            },
    ) {
        Scaffold(
            backgroundColor = MaterialTheme.colors.background,
            topBar = {
                CaptureToolbar(
                    ref = vm.job.ref,
                    shop = vm.job.shop,
                    stateLabel = vm.job.stateLabel ?: vm.job.state,
                    dataSourceLabel = dataSourceLabel,
                    onBack = onBack,
                    compact = compact,
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                StepProgressBar(current = vm.step, compact = compact)
                // `--lab-line-2` (trắng 14%) — token "viền mạnh" của ADR-009 §6, thay cho
                // alpha tay của `onSurface` (cùng giá trị nhưng đọc được là token).
                Divider(color = AppTheme.lab.lineStrong)

                StateHost(
                    state = vm.state,
                    modifier = Modifier.weight(1f),
                    emptyTitle = "Chưa có ảnh nào cho ${vm.job.ref}",
                    emptyHint = if (canCapture) {
                        "Chụp ảnh Hàng hoá ngay bằng camera, hoặc chọn ảnh có sẵn. Ảnh chỉ nằm " +
                            "trong RAM của máy này — bấm Gửi mới được đưa lên Odoo."
                    } else {
                        "Bấm \"Chọn ảnh Hàng hoá\" để bắt đầu. Ảnh chỉ nằm trong RAM " +
                            "của máy này — bấm Gửi mới được đưa lên Odoo."
                    },
                    // Có camera ⇒ hành động chính là CHỤP; chọn ảnh có sẵn là hành động phụ.
                    emptyActionLabel = if (canCapture) "Chụp ảnh Hàng hoá" else "Chọn ảnh Hàng hoá",
                    onEmptyAction = {
                        if (canCapture) vm.capture(PhotoSlot.HangHoa, scope) else vm.pick(PhotoSlot.HangHoa, scope)
                    },
                    emptySecondaryActionLabel = if (canCapture) "Chọn ảnh có sẵn" else null,
                    onEmptySecondaryAction = if (canCapture) {
                        { vm.pick(PhotoSlot.HangHoa, scope) }
                    } else {
                        null
                    },
                    loadingLabel = "Đang gửi biên nhận lên máy chủ…",
                    errorHint = "Dữ liệu bạn đã nhập VẪN CÒN trên máy. Bấm \"Thử lại\" để gửi " +
                        "lại — mã chống trùng giữ nguyên nên máy chủ không tạo bộ ảnh thứ hai.",
                    secondaryActionLabel = "Sửa lại thông tin (giữ dữ liệu)",
                    onSecondaryAction = vm::resumeEditing,
                ) { _ ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(Tokens.s3),
                    ) {
                        if (sampleMode) {
                            CalloutBox(
                                title = "Chế độ mẫu — chỉ mô phỏng",
                                body = "Đang ở nguồn dữ liệu Mẫu: bấm gửi sẽ KHÔNG gọi Odoo. " +
                                    "Bật \"Nguồn dữ liệu: Odoo thật\" trong màn \"Cài đặt demo\" " +
                                    "ở chân nav trái để gửi biên nhận thật.",
                                tone = Tone.Warn,
                            )
                            Spacer(Modifier.height(Tokens.s3))
                        }

                        vm.formError?.let { message ->
                            CalloutBox(
                                title = "Chưa gửi được",
                                body = message,
                                tone = Tone.Bad,
                                actionLabel = "Đã hiểu",
                                onAction = vm::clearFormError,
                            )
                            Spacer(Modifier.height(Tokens.s3))
                        }

                        when (vm.step) {
                            CaptureStep.Photos -> PhotosStep(vm = vm, scope = scope, wide = wide)
                            CaptureStep.Signature -> SignatureStep(vm = vm)
                            CaptureStep.Confirm -> ConfirmStep(vm = vm, wide = wide)
                        }

                        Spacer(Modifier.height(Tokens.s5))
                    }
                }

                if (hasContent) {
                    CaptureActionBar(vm = vm, scope = scope, compact = compact)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Thanh trên + thanh tiến trình bước
// ---------------------------------------------------------------------------------------
/**
 * Thanh trên của màn capture.
 *
 * @param compact `true` ở điện thoại (< 600dp): ẩn tên shop + nhãn "Nguồn" để hàng ngang chỉ
 *        còn **Quay lại + mã việc + trạng thái** (~280dp ở màn 360dp). Bản desktop giữ nguyên.
 */
@Composable
private fun CaptureToolbar(
    ref: String,
    shop: String,
    stateLabel: String,
    dataSourceLabel: String,
    onBack: () -> Unit,
    compact: Boolean = false,
) {
    LabToolbar(
        title = ref,
        subtitle = if (compact) null else shop,
        filters = {
            LabStatusChip(label = stateLabel)
            LabChip(text = if (compact) dataSourceLabel else "Nguồn: $dataSourceLabel", tone = Tone.Info)
        },
        actions = {
            TextButton(
                onClick = onBack,
                modifier = Modifier.height(Tokens.touchSecondary),
            ) {
                Text(
                    text = if (compact) "‹ Quay lại" else "‹ Việc hôm nay",
                    style = MaterialTheme.typography.body2,
                    color = AppTheme.lab.accent,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

/**
 * Thanh tiến trình 3 bước — người dùng luôn biết mình đang ở đâu và còn mấy bước.
 * Vẽ bằng `Box` (không dùng `LinearProgressIndicator`) để màu/độ dày theo token của dự án.
 */
@Composable
private fun StepProgressBar(current: CaptureStep, compact: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.s3, vertical = Tokens.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CaptureStep.ALL.forEach { step ->
                val isCurrent = step == current
                val isDone = step.ordinal < current.ordinal
                Surface(
                    shape = RoundedCornerShape(Tokens.rPill),
                    color = when {
                        isCurrent -> MaterialTheme.colors.primary
                        isDone -> AppTheme.semantic.okBg
                        else -> MaterialTheme.colors.onSurface.copy(alpha = ALPHA_STEP_IDLE)
                    },
                    elevation = Tokens.elevNone,
                ) {
                    Text(
                        text = "${step.ordinal1}. ${step.shortLabel}",
                        style = MaterialTheme.typography.caption,
                        color = when {
                            isCurrent -> MaterialTheme.colors.onPrimary
                            isDone -> AppTheme.semantic.okFg
                            else -> MaterialTheme.colors.onSurface.copy(alpha = ALPHA_MUTED)
                        },
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = Tokens.s2, vertical = Tokens.s1),
                    )
                }
                if (step != CaptureStep.ALL.last()) {
                    Spacer(Modifier.width(Tokens.s2))
                }
            }
            // Điện thoại: 3 chip đã tự nói "1. / 2. / 3." nên bỏ chữ "Bước n/3" để không tràn.
            if (!compact) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Bước ${current.ordinal1}/${CaptureStep.COUNT}",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = ALPHA_MUTED),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        Spacer(Modifier.height(Tokens.s2))
        // Thanh tiến trình: phần đã đi được tô màu primary.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Tokens.progressThickness)
                .clip(RoundedCornerShape(Tokens.rPill))
                .background(MaterialTheme.colors.onSurface.copy(alpha = ALPHA_STEP_IDLE)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(current.ordinal1.toFloat() / CaptureStep.COUNT)
                    .fillMaxHeight()
                    .background(MaterialTheme.colors.primary),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// BƯỚC 1 — 3 ô ảnh
// ---------------------------------------------------------------------------------------
@Composable
private fun PhotosStep(
    vm: DriverCaptureViewModel,
    scope: CoroutineScope,
    wide: Boolean,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "Chụp bằng chứng tại shop",
            style = MaterialTheme.typography.subtitle1,
            color = MaterialTheme.colors.onSurface,
        )
        Text(
            text = "Ảnh Hàng hoá và Kiện hàng là BẮT BUỘC. Ảnh được nén ≤ 5 MB ngay trên máy " +
                "trước khi gửi (máy chủ chỉ nhận JPEG/PNG ≤ 5 MB).",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = ALPHA_MUTED),
            modifier = Modifier.padding(top = Tokens.s1),
        )
        Spacer(Modifier.height(Tokens.s3))

        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.s3),
            ) {
                PhotoSlot.entries.forEach { slot ->
                    Box(Modifier.weight(1f)) {
                        PhotoSlotCard(vm = vm, slot = slot, scope = scope)
                    }
                }
            }
        } else {
            PhotoSlot.entries.forEach { slot ->
                PhotoSlotCard(vm = vm, slot = slot, scope = scope)
                Spacer(Modifier.height(Tokens.s3))
            }
        }

        CalloutBox(
            title = "Máy chủ nhận 1 ảnh cho mỗi lần xác nhận",
            body = "Endpoint capture hiện chỉ có một trường ảnh: app gửi ảnh Hàng hoá cùng " +
                "chữ ký. Ảnh Kiện hàng và Biên nhận giấy đang được giữ trong phiên trên máy " +
                "này — cần BE mở rộng endpoint để gửi kèm (đã ghi yêu cầu).",
            tone = Tone.Info,
            modifier = Modifier.padding(top = Tokens.s2),
        )
    }
}

@Composable
private fun PhotoSlotCard(
    vm: DriverCaptureViewModel,
    slot: PhotoSlot,
    scope: CoroutineScope,
) {
    val photo = vm.photo(slot)
    val preview = vm.preview(slot)
    val shape = RoundedCornerShape(Tokens.rMd)
    val canCapture = remember { platformDefaults().cameraCaptureSupported }

    Surface(
        shape = shape,
        color = MaterialTheme.colors.surface,
        elevation = Tokens.elevCard,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(Tokens.s3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = slot.label,
                    style = MaterialTheme.typography.subtitle2,
                    color = MaterialTheme.colors.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.width(Tokens.s2))
                StatusPill(
                    text = if (slot.required) "Bắt buộc" else "Tuỳ chọn",
                    tone = if (slot.required) Tone.Warn else Tone.Info,
                )
                Spacer(Modifier.weight(1f))
                if (photo != null) {
                    StatusPill(text = "Đã có · ${photo.sizeKb} KB", tone = Tone.Ok)
                }
            }
            Text(
                text = slot.hint,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = ALPHA_MUTED),
                modifier = Modifier.padding(top = Tokens.s1),
            )
            Spacer(Modifier.height(Tokens.s2))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Tokens.photoPreviewHeight)
                    .clip(shape)
                    .background(MaterialTheme.colors.onSurface.copy(alpha = ALPHA_PHOTO_BG)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    preview != null -> Image(
                        bitmap = preview,
                        contentDescription = "Ảnh xem trước: ${slot.label}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    photo != null -> Text(
                        text = "Không xem trước được ảnh (đã nén ${photo.sizeKb} KB)",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ALPHA_MUTED),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(Tokens.s2),
                    )

                    else -> Text(
                        text = "Chưa có ảnh",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ALPHA_MUTED),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Tokens.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { if (canCapture) vm.capture(slot, scope) else vm.pick(slot, scope) },
                    modifier = Modifier.height(Tokens.touchSecondary),
                ) {
                    Text(
                        when {
                            canCapture && photo == null -> "Chụp ảnh"
                            canCapture -> "Chụp lại"
                            photo == null -> "Chọn ảnh"
                            else -> "Thay ảnh"
                        },
                    )
                }
                if (canCapture) {
                    Spacer(Modifier.width(Tokens.s2))
                    OutlinedButton(
                        onClick = { vm.pick(slot, scope) },
                        modifier = Modifier.height(Tokens.touchSecondary),
                    ) {
                        Text("Chọn ảnh")
                    }
                }
                if (photo != null) {
                    Spacer(Modifier.width(Tokens.s2))
                    OutlinedButton(
                        onClick = { vm.remove(slot) },
                        modifier = Modifier.height(Tokens.touchSecondary),
                    ) {
                        Text("Xoá")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// BƯỚC 2 — chữ ký
// ---------------------------------------------------------------------------------------
@Composable
private fun SignatureStep(vm: DriverCaptureViewModel) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "Chữ ký người giao hàng",
            style = MaterialTheme.typography.subtitle1,
            color = MaterialTheme.colors.onSurface,
        )
        Text(
            text = "Ký bằng CHUỘT hoặc NGÓN TAY (cảm ứng). Nét vẽ chỉ nằm trong RAM của máy; " +
                "bấm \"Gửi & xác nhận\" mới xuất PNG và gửi kèm biên nhận.",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = ALPHA_MUTED),
            modifier = Modifier.padding(top = Tokens.s1),
        )
        Spacer(Modifier.height(Tokens.s3))
        SignaturePad(state = vm.signature)
        if (vm.signature.isEmpty) {
            CalloutBox(
                title = "Chưa ký",
                body = "Biên nhận bắt buộc có chữ ký — máy chủ từ chối khi thiếu " +
                    "(400 \"Chữ ký: không có dữ liệu\").",
                tone = Tone.Warn,
                modifier = Modifier.padding(top = Tokens.s3),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// BƯỚC 3 — xác nhận số kiện & kg
// ---------------------------------------------------------------------------------------
@Composable
private fun ConfirmStep(vm: DriverCaptureViewModel, wide: Boolean) {
    val draft = vm.draft()
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "Số liệu thực tế tại shop",
            style = MaterialTheme.typography.subtitle1,
            color = MaterialTheme.colors.onSurface,
        )
        Text(
            text = "Máy chủ dùng đúng 2 con số này để ghi nhận việc lấy hàng — app không tự " +
                "tính cước hay tồn kho.",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = ALPHA_MUTED),
            modifier = Modifier.padding(top = Tokens.s1),
        )
        Spacer(Modifier.height(Tokens.s3))

        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.s3),
            ) {
                Box(Modifier.weight(1f)) { PackagesField(vm) }
                Box(Modifier.weight(1f)) { WeightField(vm) }
            }
        } else {
            PackagesField(vm)
            Spacer(Modifier.height(Tokens.s2))
            WeightField(vm)
        }

        Spacer(Modifier.height(Tokens.s3))
        OutlinedTextField(
            value = vm.noteText,
            onValueChange = vm::onNoteChange,
            label = { Text("Ghi chú cho CS (tuỳ chọn)") },
            placeholder = { Text("vd 1 thùng móp góc, shop giao thiếu 1 kiện") },
            modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.touchPrimary * 2),
        )
        Text(
            text = "Ghi chú đang giữ trong phiên trên máy: endpoint capture hiện chỉ nhận 5 khoá " +
                "(ảnh, chữ ký, kiện, kg, mã chống trùng) nên CS chưa đọc được — đã ghi yêu cầu BE.",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = ALPHA_MUTED),
            modifier = Modifier.padding(top = Tokens.s1),
        )

        Spacer(Modifier.height(Tokens.s4))
        EvidenceSummary(vm = vm, draft = draft)
    }
}

@Composable
private fun PackagesField(vm: DriverCaptureViewModel) {
    OutlinedTextField(
        value = vm.packagesText,
        onValueChange = vm::onPackagesChange,
        label = { Text("Số kiện thực tế") },
        placeholder = { Text("vd 4") },
        isError = vm.packagesText.isNotEmpty() &&
            CaptureValidation.packages(vm.packagesText) == null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WeightField(vm: DriverCaptureViewModel) {
    OutlinedTextField(
        value = vm.weightText,
        onValueChange = vm::onWeightChange,
        label = { Text("Khối lượng thực tế (kg)") },
        placeholder = { Text("vd 12,5") },
        isError = vm.weightText.isNotEmpty() &&
            CaptureValidation.weightKg(vm.weightText) == null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Tóm tắt bằng chứng trước khi gửi — để tài xế soát lại, không phải tin vào trí nhớ. */
@Composable
private fun EvidenceSummary(vm: DriverCaptureViewModel, draft: CaptureDraft) {
    Surface(
        shape = RoundedCornerShape(Tokens.rMd),
        color = MaterialTheme.colors.onSurface.copy(alpha = ALPHA_SUMMARY_BG),
        elevation = Tokens.elevNone,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(Tokens.s3)) {
            Text(
                text = "Tóm tắt biên nhận",
                style = MaterialTheme.typography.subtitle2,
                color = MaterialTheme.colors.onSurface,
            )
            Spacer(Modifier.height(Tokens.s2))
            SummaryLine("Ảnh Hàng hoá", if (draft.hasHangHoa) "Đã có" else "Thiếu", draft.hasHangHoa)
            SummaryLine("Ảnh Kiện hàng", if (draft.hasKienHang) "Đã có" else "Thiếu", draft.hasKienHang)
            SummaryLine(
                "Biên nhận giấy",
                if (draft.hasBienNhan) "Đã có" else "Không có (tuỳ chọn)",
                ok = true,
            )
            SummaryLine(
                "Chữ ký",
                if (draft.hasSignature) "${vm.signature.strokeCount} nét" else "Chưa ký",
                ok = draft.hasSignature,
            )
            SummaryLine(
                "Số kiện",
                "${vm.packagesText.ifBlank { "Chưa nhập" }} kiện",
                ok = CaptureValidation.packages(vm.packagesText) != null,
            )
            SummaryLine(
                "Khối lượng",
                "${vm.weightText.ifBlank { "Chưa nhập" }} kg",
                ok = CaptureValidation.weightKg(vm.weightText) != null,
            )
            Spacer(Modifier.height(Tokens.s2))
            Text(
                text = "Mã chống trùng (clientUuid): ${vm.clientUuid}",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = ALPHA_MUTED),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Một dòng "nhãn — giá trị — pill". Nhãn và giá trị chia bề rộng bằng `weight` (không đặt
 * bề rộng cứng): bề rộng cứng `formWidth / 3` = 173dp chỉ hợp với desktop, trên màn 360dp nó
 * chiếm quá nửa hàng và ép giá trị xuống 0dp.
 */
@Composable
private fun SummaryLine(label: String, value: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.s1 / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = ALPHA_MUTED),
            modifier = Modifier.weight(1.1f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Tokens.s2))
        StatusPill(text = if (ok) "OK" else "Thiếu", tone = if (ok) Tone.Ok else Tone.Bad)
    }
}

// ---------------------------------------------------------------------------------------
// Thanh hành động (dưới cùng) — nút chính + LÝ DO bị khoá
// ---------------------------------------------------------------------------------------
/**
 * Thanh hành động dưới cùng — nút chính ≥ 56dp + LÝ DO bị khoá.
 *
 * @param compact `true` ở điện thoại: dùng nhãn NGẮN ("‹ Trước", "Ký ›", "Xác nhận ›", "Gửi")
 *        để hai nút cùng hàng không vượt ~336dp khả dụng của màn 360dp. Nhãn desktop giữ nguyên.
 */
@Composable
private fun CaptureActionBar(
    vm: DriverCaptureViewModel,
    scope: CoroutineScope,
    compact: Boolean = false,
) {
    val submitting = vm.state is UiState.Loading
    val blockers = if (vm.step == CaptureStep.Confirm) vm.blockers() else emptyList()
    val stepReason = vm.stepBlockers()

    Surface(color = MaterialTheme.colors.surface, elevation = Tokens.elevCard) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.s3, vertical = Tokens.s2)) {
            if (blockers.isNotEmpty()) {
                Text(
                    text = "Chưa gửi được:",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.error,
                    maxLines = 1,
                )
                blockers.take(MAX_BLOCKER_LINES).forEach { reason ->
                    Text(
                        text = "• $reason",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (stepReason != null) {
                Text(
                    text = stepReason,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Tokens.s1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (vm.step != CaptureStep.Photos) {
                    OutlinedButton(
                        onClick = vm::previousStep,
                        modifier = Modifier.height(Tokens.touchSecondary),
                    ) {
                        Text(
                            text = if (compact) "‹ Trước" else "‹ Quay lại bước",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(Tokens.s2))
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        when (vm.step) {
                            CaptureStep.Confirm -> vm.submit(scope)
                            else -> vm.nextStep()
                        }
                    },
                    enabled = when (vm.step) {
                        // Bất biến an toàn C6-a: thiếu ảnh/chữ ký/số liệu ⇒ nút Gửi BỊ KHOÁ.
                        CaptureStep.Confirm -> blockers.isEmpty() && !submitting
                        else -> stepReason == null
                    },
                    modifier = Modifier.height(Tokens.touchPrimary),
                ) {
                    Text(
                        text = when (vm.step) {
                            CaptureStep.Photos ->
                                if (compact) "Ký ›" else "Tiếp: Ký biên nhận ›"
                            CaptureStep.Signature ->
                                if (compact) "Xác nhận ›" else "Tiếp: Xác nhận ›"
                            CaptureStep.Confirm -> "Gửi & xác nhận"
                        },
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Độ mờ chữ phụ / nền chip chờ — alpha của `onSurface`, không phải màu mới (skill 07 §6). */
private const val ALPHA_MUTED = 0.72f
private const val ALPHA_STEP_IDLE = 0.12f
private const val ALPHA_PHOTO_BG = 0.06f
private const val ALPHA_SUMMARY_BG = 0.05f

/** Số dòng lý do tối đa hiện dưới nút (>4 gần như không xảy ra và sẽ phá chỗ). */
private const val MAX_BLOCKER_LINES = 4
