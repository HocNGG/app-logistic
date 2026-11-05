package vn.lab.logistics.feature.kho

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Tokens
import vn.lab.logistics.ui.components.CalloutBox
import vn.lab.logistics.ui.components.QrCodeBox
import vn.lab.logistics.ui.components.Tone
import vn.lab.logistics.ui.components.extractScanCode
import vn.lab.logistics.ui.kit.LabChip
import vn.lab.logistics.ui.platform.PrintableQrLabel
import vn.lab.logistics.ui.platform.printQrLabels
import vn.lab.logistics.ui.platform.saveQrLabelsAsImage
import vn.lab.logistics.ui.platform.scanQrCodeWithLiveCamera

/**
 * Thông tin 1 tem QR (dùng chung cho cả **Tem Nhận Kho** `KIEN/26/…` lẫn **Tem Xuất Kho** `XUAT/26/…`).
 */
data class QrTicketItem(
    /** Mã chính đưa vào QR (Nhận: `KIEN/26/…`, Xuất: `XUAT/26/…` hoặc `KIEN/26/…`). */
    val qrCode: String,
    /** Mã kiện gốc (`KIEN/26/…`). */
    val maKien: String,
    /** Mã tem xuất (`XUAT/26/…`) nếu ở bước Xuất kho. */
    val maXuat: String? = null,
    /** Đã quét xong ở mốc hiện tại hay chưa. */
    val daQuet: Boolean,
    /** Nhãn trạng thái hiển thị trên chip. */
    val statusLabel: String,
)

/**
 * =====================================================================================
 * 1. MÀN IN TEM MÃ QR (NHẬN / XUẤT) — tự động mở sau khi bấm "Nhận hàng & in tem" hoặc
 *    bấm "Màn in tem QR".
 *
 * Đúng thực tế:
 *  - Hiển thị bản xem trước các tem QR chuẩn ISO/IEC 18004.
 *  - Cho **2 option thật**:
 *      (1) **In tem QR** (`printQrLabels` — mở trình in hệ thống / xuất PDF khổ tem).
 *      (2) **Lưu ảnh tem về máy** (`saveQrLabelsAsImage` — lưu ảnh PNG vào Bộ sưu tập
 *          `Pictures/AppLogistics` trên điện thoại hoặc lưu file PNG trên máy tính).
 *  - Sau khi in hoặc lưu ảnh xong, bấm **"Quét mã QR bằng Camera →"** để mở Camera quét thật.
 * =====================================================================================
 */
@Composable
fun QrLabelPrintDialog(
    title: String,
    subtitle: String,
    metaLine: String?,
    items: List<QrTicketItem>,
    loading: Boolean = false,
    busy: Boolean = false,
    onScanItem: (String) -> Unit = {},
    onSwitchToScanner: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var actionBusy by remember { mutableStateOf(false) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }
    var actionIsError by remember { mutableStateOf(false) }

    val printableLabels = remember(items, subtitle, metaLine) {
        items.mapIndexed { idx, item ->
            PrintableQrLabel(
                header = if (item.maXuat != null) "TEM QR XUẤT KHO" else "TEM QR NHẬN KHO",
                qrContent = item.qrCode,
                primaryCode = item.qrCode,
                secondaryCode = item.maKien.takeIf { item.maXuat != null && item.maXuat != item.maKien },
                subtitle = subtitle,
                meta = metaLine.orEmpty(),
                indexLabel = "Kiện ${idx + 1}/${items.size}",
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = AppTheme.lab.card,
            contentColor = AppTheme.lab.ink,
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AppTheme.lab.line),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Tokens.s4),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.h6,
                            color = AppTheme.lab.ink,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.body2,
                            color = AppTheme.lab.ink2,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    LabChip(text = "${items.size} tem QR", tone = Tone.Info)
                }

                if (!metaLine.isNullOrBlank()) {
                    Spacer(Modifier.height(Tokens.s2))
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.caption,
                        color = AppTheme.lab.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(Tokens.s3))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Tokens.s3),
                ) {
                    when {
                        loading -> Text(
                            text = "Đang tạo tem mã QR cho các kiện…",
                            style = MaterialTheme.typography.body2,
                            color = AppTheme.lab.ink2,
                        )

                        items.isEmpty() -> Text(
                            text = "Chưa có kiện nào để tạo tem QR.",
                            style = MaterialTheme.typography.body2,
                            color = AppTheme.lab.ink2,
                        )

                        else -> items.forEachIndexed { index, item ->
                            PrintableQrStampCard(
                                index = index + 1,
                                total = items.size,
                                subtitle = subtitle,
                                item = item,
                            )
                        }
                    }
                }

                actionFeedback?.let { msg ->
                    Spacer(Modifier.height(Tokens.s2))
                    CalloutBox(
                        title = if (actionIsError) "Chưa thực hiện được" else "Đã xử lý tem QR",
                        body = msg,
                        tone = if (actionIsError) Tone.Bad else Tone.Ok,
                    )
                }

                Spacer(Modifier.height(Tokens.s3))

                // 2 OPTION THỰC TẾ CỦA MÀN IN: (1) IN TEM QR · (2) LƯU ẢNH TEM VỀ MÁY
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Tokens.s2),
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                actionBusy = true
                                actionFeedback = null
                                val res = printQrLabels(
                                    jobTitle = "$title - $subtitle",
                                    labels = printableLabels,
                                )
                                actionBusy = false
                                res.fold(
                                    onSuccess = {
                                        actionIsError = false
                                        actionFeedback = it
                                    },
                                    onFailure = {
                                        actionIsError = true
                                        actionFeedback = it.message ?: "Không mở được trình in."
                                    },
                                )
                            }
                        },
                        enabled = !loading && !actionBusy && printableLabels.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(Tokens.touchPrimary),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = AppTheme.lab.accent,
                            contentColor = AppTheme.lab.onAccent,
                        ),
                    ) {
                        Text(
                            text = "In tem QR",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                actionBusy = true
                                actionFeedback = null
                                val prefix = items.firstOrNull()?.qrCode ?: subtitle
                                val res = saveQrLabelsAsImage(
                                    filePrefix = "TemQR_$prefix",
                                    labels = printableLabels,
                                )
                                actionBusy = false
                                res.fold(
                                    onSuccess = {
                                        actionIsError = false
                                        actionFeedback = it
                                    },
                                    onFailure = {
                                        actionIsError = true
                                        actionFeedback = it.message ?: "Không lưu được ảnh tem QR."
                                    },
                                )
                            }
                        },
                        enabled = !loading && !actionBusy && printableLabels.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(Tokens.touchPrimary),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = AppTheme.lab.surface2,
                            contentColor = AppTheme.lab.ink,
                        ),
                    ) {
                        Text(
                            text = "Lưu ảnh về máy",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(Tokens.s2))

                Button(
                    onClick = onSwitchToScanner,
                    enabled = !busy && !actionBusy,
                    modifier = Modifier.fillMaxWidth().height(Tokens.touchSecondary),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = AppTheme.lab.surface2,
                        contentColor = AppTheme.lab.accent,
                    ),
                ) {
                    Text(
                        text = "Chuyển sang Quét mã QR (Camera / Nhập tay) →",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(Tokens.s1))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Đóng màn in", color = AppTheme.lab.ink2)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrintableQrStampCard(
    index: Int,
    total: Int,
    subtitle: String,
    item: QrTicketItem,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .border(1.dp, Color(0xFF1F2937))
            .padding(Tokens.s3),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (item.maXuat != null) "TEM QR XUẤT KHO" else "TEM QR NHẬN KHO",
                style = MaterialTheme.typography.caption,
                color = Color(0xFF111827),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Kiện $index/$total",
                style = MaterialTheme.typography.caption,
                color = Color(0xFF111827),
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(Tokens.s2))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QrCodeBox(
                content = item.qrCode,
                size = 112.dp,
            )
            Spacer(Modifier.width(Tokens.s3))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.qrCode,
                    style = MaterialTheme.typography.subtitle1,
                    color = Color(0xFF111827),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.maXuat != null && item.maXuat != item.maKien) {
                    Text(
                        text = "Kiện gốc: ${item.maKien}",
                        style = MaterialTheme.typography.caption,
                        color = Color(0xFF374151),
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.caption,
                    color = Color(0xFF4B5563),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(Tokens.s2))
                LabChip(
                    text = item.statusLabel,
                    tone = if (item.daQuet) Tone.Ok else Tone.Warn,
                )
            }
        }
    }
}

/**
 * =====================================================================================
 * 2. MÀN QUÉT MÃ QR (`QrPhoneScannerDialog`) — ĐÚNG CƠ CHẾ THỰC TẾ SIÊU THỊ:
 *
 *  - **Cách 1 — Quét bằng Camera thời gian thực (`scanQrCodeWithLiveCamera`):**
 *    Mở khung ngắm Camera với tia laser quét trực tiếp. Chỉ khi Camera nhìn thấy và giải mã
 *    được đúng mã QR hợp lệ thì mới phát tiếng "Bíp" và gửi mã đó lên server xác nhận!
 *    (Trong màn Camera cũng có nút chọn ảnh tem QR vừa lưu trong Bộ sưu tập để giải mã).
 *  - **Cách 2 — Nhập tay mã kiện / mã xuất:**
 *    Nếu không muốn quét Camera, gõ mã kiện (`KIEN/26/…`) hoặc mã xuất (`XUAT/26/…`) vào ô
 *    nhập tay rồi bấm **"Xác nhận mã nhập tay"**.
 *  - Khi đã quét đủ số kiện (`duKien == true`), hiển thị nút **"Xác nhận OK · Chuyển bước tiếp theo"**.
 * =====================================================================================
 */
@Composable
fun QrPhoneScannerDialog(
    title: String,
    subtitle: String,
    nextStepHint: String,
    confirmDoneLabel: String,
    secondaryDoneLabel: String? = null,
    items: List<QrTicketItem>,
    daQuet: Int,
    tong: Int,
    busy: Boolean,
    error: String?,
    onScanCode: (String) -> Unit,
    onOpenPrint: () -> Unit,
    onConfirmDone: () -> Unit,
    onSecondaryDone: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var manualInput by remember { mutableStateOf("") }
    var cameraFeedback by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var scanningCamera by remember { mutableStateOf(false) }
    val duKien = tong > 0 && daQuet >= tong
    val tiLe = if (tong <= 0) 0f else (daQuet.toFloat() / tong).coerceIn(0f, 1f)
    val unscannedItems = items.filterNot { it.daQuet }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = AppTheme.lab.card,
            contentColor = AppTheme.lab.ink,
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AppTheme.lab.line),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Tokens.s4)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.h6,
                            color = AppTheme.lab.ink,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.body2,
                            color = AppTheme.lab.ink2,
                        )
                    }
                    LabChip(
                        text = if (tong > 0) "$daQuet/$tong kiện" else "Đang tải",
                        tone = if (duKien) Tone.Ok else Tone.Info,
                    )
                }

                Spacer(Modifier.height(Tokens.s2))
                LinearProgressIndicator(
                    progress = tiLe,
                    modifier = Modifier.fillMaxWidth().height(Tokens.s2),
                    color = AppTheme.lab.accent,
                    backgroundColor = AppTheme.lab.surface2,
                )

                Spacer(Modifier.height(Tokens.s3))

                if (duKien) {
                    // ĐÃ QUÉT ĐỦ KIỆN -> HIỆN XÁC NHẬN OK & CHUYỂN QUA BƯỚC TIẾP THEO
                    CalloutBox(
                        title = "Quét mã QR hoàn tất ($daQuet/$tong kiện) — OK!",
                        body = nextStepHint,
                        tone = Tone.Ok,
                    )
                    Spacer(Modifier.height(Tokens.s3))
                    Button(
                        onClick = onConfirmDone,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(Tokens.touchPrimary),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = AppTheme.lab.accent,
                            contentColor = AppTheme.lab.onAccent,
                        ),
                    ) {
                        Text(
                            text = confirmDoneLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (secondaryDoneLabel != null && onSecondaryDone != null) {
                        Spacer(Modifier.height(Tokens.s2))
                        Button(
                            onClick = onSecondaryDone,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(Tokens.touchSecondary),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = AppTheme.lab.surface2,
                                contentColor = AppTheme.lab.ink,
                            ),
                        ) {
                            Text(
                                text = secondaryDoneLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    // CÁCH 1: MỞ CAMERA QUÉT MÃ QR THỰC TẾ NHƯ MÁY QUÉT SIÊU THỊ
                    Button(
                        onClick = {
                            scope.launch {
                                scanningCamera = true
                                cameraError = null
                                cameraFeedback = null
                                val expected = items.map { it.qrCode } + items.map { it.maKien }
                                val res = scanQrCodeWithLiveCamera(expected.distinct())
                                scanningCamera = false
                                res.fold(
                                    onSuccess = { decoded ->
                                        if (!decoded.isNullOrBlank()) {
                                            val isKhop = items.isEmpty() || items.any {
                                                val cleanExp = extractScanCode(it.qrCode).uppercase()
                                                val cleanMa = extractScanCode(it.maKien).uppercase()
                                                val c = decoded.uppercase()
                                                c == cleanExp || c == cleanMa || c.contains(cleanExp) || c.contains(cleanMa)
                                            }
                                            if (!isKhop) {
                                                cameraError = "⚠️ Quét lộn mã: Mã '$decoded' không thuộc đơn này! Các mã kiện của đơn: ${items.joinToString { it.maKien }}"
                                            } else {
                                                cameraFeedback = "Camera đã đọc được mã QR: $decoded"
                                                onScanCode(decoded)
                                            }
                                        }
                                    },
                                    onFailure = { err ->
                                        cameraError = err.message ?: "Không quét được mã QR từ Camera."
                                    },
                                )
                            }
                        },
                        enabled = !busy && !scanningCamera,
                        modifier = Modifier.fillMaxWidth().height(Tokens.touchPrimary),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = AppTheme.lab.accent,
                            contentColor = AppTheme.lab.onAccent,
                        ),
                    ) {
                        Text(
                            text = when {
                                scanningCamera -> "Đang mở Camera quét mã QR…"
                                busy -> "Đang kiểm tra mã QR…"
                                else -> "Mở Camera quét mã QR (như siêu thị)"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Text(
                        text = "Hướng Camera vào tem QR đã in hoặc chọn ảnh tem QR vừa lưu trong máy. " +
                            "Nếu quét lộn mã kiện khác, máy sẽ phát âm báo và hiện cảnh báo ngay.",
                        style = MaterialTheme.typography.caption,
                        color = AppTheme.lab.ink2,
                        modifier = Modifier.padding(top = Tokens.s1),
                    )

                    cameraFeedback?.let { msg ->
                        Spacer(Modifier.height(Tokens.s2))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.caption,
                            color = AppTheme.lab.accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    (cameraError ?: error)?.let { err ->
                        Spacer(Modifier.height(Tokens.s2))
                        Surface(
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFF87171)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "⚠️",
                                    style = MaterialTheme.typography.subtitle1,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFFB91C1C),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(Tokens.s3))

                    // CÁCH 2: KHÔNG MUỐN QUÉT CAMERA THÌ NHẬP TAY MÃ ĐÓ LÀ OK
                    Text(
                        text = "Trường hợp không muốn quét Camera — nhập tay mã kiện / mã xuất:",
                        style = MaterialTheme.typography.subtitle2,
                        color = AppTheme.lab.ink,
                    )
                    Spacer(Modifier.height(Tokens.s1))
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it },
                        label = { Text("Nhập tay mã kiện (KIEN/26/…) hoặc mã xuất (XUAT/26/…)") },
                        placeholder = { Text("Ví dụ: ${unscannedItems.firstOrNull()?.qrCode ?: "KIEN/26/00001"}") },
                        singleLine = true,
                        enabled = !busy && !scanningCamera,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val code = extractScanCode(manualInput)
                                if (code.isNotEmpty()) {
                                    val isKhop = items.isEmpty() || items.any {
                                        val cleanExp = extractScanCode(it.qrCode).uppercase()
                                        val cleanMa = extractScanCode(it.maKien).uppercase()
                                        val c = code.uppercase()
                                        c == cleanExp || c == cleanMa || c.contains(cleanExp) || c.contains(cleanMa)
                                    }
                                    if (!isKhop) {
                                        cameraError = "⚠️ Quét lộn mã: Mã '$code' không thuộc đơn này! Các mã kiện của đơn: ${items.joinToString { it.maKien }}"
                                    } else {
                                        cameraError = null
                                        onScanCode(code)
                                        manualInput = ""
                                    }
                                }
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Tokens.s2))
                    Button(
                        onClick = {
                            val code = extractScanCode(manualInput)
                            if (code.isNotEmpty()) {
                                val isKhop = items.isEmpty() || items.any {
                                    val cleanExp = extractScanCode(it.qrCode).uppercase()
                                    val cleanMa = extractScanCode(it.maKien).uppercase()
                                    val c = code.uppercase()
                                    c == cleanExp || c == cleanMa || c.contains(cleanExp) || c.contains(cleanMa)
                                }
                                if (!isKhop) {
                                    cameraError = "⚠️ Quét lộn mã: Mã '$code' không thuộc đơn này! Các mã kiện của đơn: ${items.joinToString { it.maKien }}"
                                } else {
                                    cameraError = null
                                    onScanCode(code)
                                    manualInput = ""
                                }
                            }
                        },
                        enabled = !busy && !scanningCamera && manualInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(Tokens.touchSecondary),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = AppTheme.lab.surface2,
                            contentColor = AppTheme.lab.ink,
                        ),
                    ) {
                        Text("Xác nhận mã nhập tay")
                    }

                    if (unscannedItems.isNotEmpty()) {
                        Spacer(Modifier.height(Tokens.s3))
                        Text(
                            text = "Mã đang chờ quét (${unscannedItems.size} kiện):",
                            style = MaterialTheme.typography.caption,
                            color = AppTheme.lab.ink2,
                        )
                        Spacer(Modifier.height(Tokens.s1))
                        unscannedItems.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .background(AppTheme.lab.surface2)
                                    .border(1.dp, AppTheme.lab.line)
                                    .padding(horizontal = Tokens.s2, vertical = Tokens.s1),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = item.qrCode,
                                        style = MaterialTheme.typography.subtitle2,
                                        color = AppTheme.lab.ink,
                                    )
                                    if (item.maXuat != null && item.maXuat != item.maKien) {
                                        Text(
                                            text = "Kiện gốc: ${item.maKien}",
                                            style = MaterialTheme.typography.caption,
                                            color = AppTheme.lab.ink2,
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { manualInput = item.qrCode },
                                    enabled = !busy,
                                ) {
                                    Text(
                                        text = "Điền vào ô nhập",
                                        style = MaterialTheme.typography.caption,
                                        color = AppTheme.lab.accent,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Tokens.s3))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onOpenPrint) {
                        Text("← Xem / In / Lưu ảnh tem QR", color = AppTheme.lab.accent)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Đóng", color = AppTheme.lab.ink2)
                    }
                }
            }
        }
    }
}
