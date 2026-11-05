package vn.lab.logistics.feature.capture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.lab.logistics.core.media.compressJpeg
import vn.lab.logistics.core.media.decodePreviewImage
import vn.lab.logistics.core.media.isSupportedImage
import vn.lab.logistics.core.model.DriverJob
import vn.lab.logistics.core.net.toVietnameseMessage
import vn.lab.logistics.core.repo.CaptureSubmission
import vn.lab.logistics.core.repo.DriverWorkRepo
import vn.lab.logistics.feature.driver.stateLabelOf
import vn.lab.logistics.platformDefaults
import vn.lab.logistics.ui.components.SignatureState
import vn.lab.logistics.ui.components.UiState
import vn.lab.logistics.ui.platform.captureImageFromCamera
import vn.lab.logistics.ui.platform.pickImageFile

/**
 * =====================================================================================
 * C3 + C5 — ViewModel của màn CAPTURE (3 bước trong 1 màn).
 *
 * TRẠNG THÁI (4 trạng thái bắt buộc, vẽ bằng `StateHost`):
 *  - `Loading` — đang GỬI biên nhận lên server (chỉ lúc gửi, không phải lúc mở màn);
 *  - `Empty`   — chưa chụp gì (`CaptureDraft.hasAnyEvidence == false`) ⇒ mời chọn ảnh đầu tiên;
 *  - `Error`   — lỗi nghiệp vụ/mạng: câu tiếng Việt + "Thử lại"; **dữ liệu đã nhập còn nguyên**
 *                trong RAM và `clientUuid` KHÔNG đổi ⇒ gửi lại không tạo bộ ảnh thứ hai;
 *  - `Content` — có ít nhất một bằng chứng ⇒ hiện nội dung bước đang làm.
 *
 * RANH GIỚI (Luật 10): file này là `commonMain` thuần — không `java.awt`, không `ImageIO`.
 * Nén ảnh và chọn tệp đi qua `expect` ở `core/media` + `ui/platform`.
 *
 * KHÔNG GHI ĐĨA: ảnh đã nén và nét chữ ký chỉ sống trong các state dưới đây, tới lúc gửi.
 * =====================================================================================
 */

/** Ba bước của màn capture, đúng đặc tả docs/04 §5.1 (`DriverCapture` → `DriverSign` → `DriverConfirm`). */
enum class CaptureStep(val label: String, val shortLabel: String) {
    Photos(label = "Chụp ảnh hiện trường", shortLabel = "Ảnh"),
    Signature(label = "Ký biên nhận", shortLabel = "Chữ ký"),
    Confirm(label = "Xác nhận số kiện & kg", shortLabel = "Xác nhận"),
    ;

    /** Số thứ tự 1-based để vẽ thanh tiến trình. */
    val ordinal1: Int get() = ordinal + 1

    companion object {
        /** Đủ 3 bước theo đúng thứ tự khai báo — nguồn duy nhất cho thanh tiến trình. */
        val ALL: List<CaptureStep> = CaptureStep.entries.toList()

        /** Tổng số bước (dùng cho "Bước 2/3"). */
        val COUNT: Int get() = ALL.size
    }
}

/** Kết quả một lần gửi thành công — mang số liệu THẬT đã gửi để Snackbar đọc lại. */
data class CaptureSuccess(
    val jobId: Int,
    val ref: String,
    val packages: Int,
    val weightKg: Double,
    /** State server trả (`picked_up` khi thành công). */
    val state: String?,
    /** Nhãn tiếng Việt của server (fallback bảng nhãn app). */
    val stateLabel: String,
    /** `true` khi server nhận ra `clientUuid` trùng (gửi lại sau lỗi mạng). */
    val duplicate: Boolean,
)

/**
 * Câu Snackbar sau khi gửi xong (C4):
 * `Đã gửi biên nhận cho LH/26/0005 — 4 kiện, 12,5 kg`.
 */
fun CaptureSuccess.toSnackbarMessage(): String {
    val coBan = "Đã gửi biên nhận cho $ref — $packages kiện, ${formatKg(weightKg)} kg"
    return if (duplicate) "$coBan (gửi lại — máy chủ đã chống trùng, không tạo bộ ảnh thứ hai)" else coBan
}

/**
 * @param work repo ghi (Odoo thật hoặc chế độ mẫu — ViewModel không biết đang ở impl nào).
 * @param job việc đang capture.
 * @param pickImage mở hộp thoại chọn ảnh — tham số hoá để test/self-check không mở GUI.
 * @param newUuid sinh `clientUuid` — tham số hoá để test bất biến "retry giữ nguyên uuid".
 */
class DriverCaptureViewModel(
    private val work: DriverWorkRepo,
    val job: DriverJob,
    private val pickImage: () -> ByteArray? = { pickImageFile() },
    newUuid: () -> String = { newClientUuid() },
    private val captureImage: () -> ByteArray? = { captureImageFromCamera() },
) {

    /**
     * PHIÊN capture (C5). Một instance ViewModel = một phiên ⇒ mở lại màn capture là capture
     * MỚI (uuid mới); bấm "Thử lại" **trong** màn này giữ nguyên [clientUuid] vì object này
     * không đổi. Toàn bộ luật nằm ở [openCaptureSession] (hàm thuần, có test).
     */
    private val session: CaptureSession =
        openCaptureSession(current = null, jobId = job.id, newUuid = newUuid)

    /** Idempotency key gửi lên server — đọc ra cho test/self-check. */
    val clientUuid: String get() = session.clientUuid

    /** Nét chữ ký — nằm Ở ĐÂY để chuyển bước không mất chữ ký. */
    val signature: SignatureState = SignatureState()

    private val photos = mutableStateMapOf<PhotoSlot, PickedPhoto>()
    private val previews = mutableStateMapOf<PhotoSlot, ImageBitmap>()

    var step by mutableStateOf(CaptureStep.Photos)
        private set

    var packagesText by mutableStateOf("")
        private set

    var weightText by mutableStateOf("")
        private set

    var noteText by mutableStateOf("")
        private set

    var state by mutableStateOf<UiState<Unit>>(UiState.Empty)
        private set

    /** Lỗi cục bộ (chọn tệp sai, chữ ký rỗng…) — khác [state] Error của lần gửi. */
    var formError by mutableStateOf<String?>(null)
        private set

    /** Kết quả gửi thành công — màn hình đọc rồi điều hướng về danh sách. */
    var submitted by mutableStateOf<CaptureSuccess?>(null)
        private set

    // ------------------------------------------------------------------ đọc dữ liệu

    fun photo(slot: PhotoSlot): PickedPhoto? = photos[slot]

    fun preview(slot: PhotoSlot): ImageBitmap? = previews[slot]

    /** Ảnh Hàng hoá là ảnh THẬT SỰ được gửi lên server (endpoint chỉ nhận 1 ảnh). */
    fun primaryPhoto(): PickedPhoto? = photos[PhotoSlot.HangHoa]

    /** Dữ liệu người dùng đã nhập — dùng cho validate + hiển thị lý do khoá nút. */
    fun draft(): CaptureDraft = CaptureDraft(
        hasHangHoa = photos.containsKey(PhotoSlot.HangHoa),
        hasKienHang = photos.containsKey(PhotoSlot.KienHang),
        hasBienNhan = photos.containsKey(PhotoSlot.BienNhan),
        hasSignature = !signature.isEmpty,
        packagesText = packagesText,
        weightText = weightText,
    )

    fun blockers(): List<String> = CaptureValidation.blockers(draft())

    fun clearFormError() {
        formError = null
    }

    /**
     * Rời màn lỗi để SỬA lại dữ liệu (`Error → Content/Empty`). Ảnh, chữ ký, số kiện, kg và
     * `clientUuid` giữ nguyên — đây là đường lui bắt buộc để người dùng không bị kẹt ở màn lỗi.
     */
    fun resumeEditing() {
        formError = null
        state = if (draft().hasAnyEvidence) UiState.Content(Unit) else UiState.Empty
    }

    // ------------------------------------------------------------------ bước 1: ảnh

    /**
     * Chọn ảnh cho [slot]: hộp thoại (EDT) → kiểm magic bytes → nén ≤ 5 MB → dựng preview.
     *
     * Nặng nhất là nén (một ảnh 12MP mất vài trăm ms) nên chạy ở `Dispatchers.Default` để
     * giao diện không bị đứng.
     *
     * Wave C — LUỒNG GỌI HỘP THOẠI khác nhau theo nền tảng (`platformDefaults()`):
     *  - **desktop**: `java.awt.FileDialog` PHẢI mở trên EDT và tự chặn EDT tới khi chọn xong
     *    ⇒ gọi trực tiếp như trước (hành vi đã kiểm chứng, không đổi).
     *  - **Android**: bộ chọn ảnh là Activity khác, kết quả CHỈ về main thread. `pickImageFile()`
     *    là hàm chặn ⇒ phải gọi ở luồng nền, nếu không sẽ tự khoá callback và treo tới hết
     *    thời gian chờ (xem `androidMain/.../FilePicker.android.kt`).
     */
    fun pick(slot: PhotoSlot, scope: CoroutineScope) = acquire(
        slot = slot,
        scope = scope,
        source = pickImage,
        fallbackError = "Không đọc được tệp ảnh. Vui lòng chọn ảnh khác.",
        notImageError = "Tệp vừa chọn không phải ảnh JPEG hoặc PNG. " +
            "Vui lòng chọn ảnh chụp từ điện thoại/máy ảnh.",
    )

    /**
     * CHỤP ẢNH TRỰC TIẾP bằng camera cho [slot] (yêu cầu 2026-11-03). Sau khi chụp, đi CÙNG
     * đường với [pick]: kiểm magic bytes → nén ≤ 5 MB → preview → `photos[slot]`, nên mọi luật
     * (bắt buộc, ≤ 5 MB, chống trùng) giữ nguyên — chỉ khác NGUỒN ảnh.
     *
     * Huỷ camera ⇒ không đổi gì (ảnh cũ của ô này vẫn còn).
     */
    fun capture(slot: PhotoSlot, scope: CoroutineScope) = acquire(
        slot = slot,
        scope = scope,
        source = captureImage,
        fallbackError = "Không chụp được ảnh. Vui lòng thử lại hoặc chọn ảnh có sẵn.",
        notImageError = "Ảnh vừa chụp không đọc được. Vui lòng chụp lại.",
    )

    private fun acquire(
        slot: PhotoSlot,
        scope: CoroutineScope,
        source: () -> ByteArray?,
        fallbackError: String,
        notImageError: String,
    ): Job {
        return scope.launch {
            formError = null
            val raw: ByteArray? = try {
                if (platformDefaults().imagePickerRunsOnCallerMainThread) {
                    source()
                } else {
                    withContext(Dispatchers.Default) { source() }
                }
            } catch (e: Throwable) {
                formError = e.message ?: fallbackError
                null
            }
            if (raw == null) return@launch // Người dùng bấm Huỷ (hoặc đã báo lỗi ở trên).

            // Kiểm magic bytes + nén đều chạy ở luồng nền: ảnh 12MP giải mã mất vài trăm ms,
            // đặt trên EDT là giao diện đứng hình ngay giữa buổi demo.
            val ketQua = withContext(Dispatchers.Default) {
                runCatching {
                    require(isSupportedImage(raw)) { notImageError }
                    val jpeg = compressJpeg(raw)
                    Triple(jpeg, decodePreviewImage(jpeg), jpeg.size)
                }
            }
            ketQua.fold(
                onSuccess = { (jpeg, preview, size) ->
                    photos[slot] = PickedPhoto(
                        slot = slot,
                        jpegBytes = jpeg,
                        sourceName = "Anh ${slot.label} (${size / 1024} KB)",
                    )
                    preview?.let { previews[slot] = it }
                    refreshState()
                },
                onFailure = { error ->
                    formError = error.message
                        ?: "Không nén được ảnh này. Vui lòng chọn ảnh khác."
                },
            )
        }
    }

    fun remove(slot: PhotoSlot) {
        photos.remove(slot)
        previews.remove(slot)
        formError = null
        refreshState()
    }

    // ------------------------------------------------------------------ bước 2: chữ ký

    /** Chữ ký vừa vẽ xong → cập nhật trạng thái (Empty ⇄ Content). */
    fun onSignatureChanged() {
        refreshState()
    }

    // ------------------------------------------------------------------ bước 3: số liệu

    fun onPackagesChange(value: String) {
        // Chỉ chữ số: số kiện là số nguyên dương, không cho gõ "4 kiện" vào rồi parse lỗi.
        packagesText = value.filter { it.isDigit() }.take(6)
        refreshState()
    }

    fun onWeightChange(value: String) {
        // Cho phép cả ',' và '.' (bàn phím tiếng Việt gõ phẩy) — validation tự chuẩn hoá.
        weightText = value.filter { it.isDigit() || it == ',' || it == '.' }.take(9)
        refreshState()
    }

    fun onNoteChange(value: String) {
        noteText = value.take(MAX_NOTE_LENGTH)
    }

    // ------------------------------------------------------------------ điều hướng bước

    /** Sang bước sau nếu bước hiện tại đã đủ điều kiện (ảnh bắt buộc / có chữ ký). */
    fun nextStep(): Boolean {
        val loi = stepBlockers()
        if (loi != null) {
            formError = loi
            return false
        }
        formError = null
        step = when (step) {
            CaptureStep.Photos -> CaptureStep.Signature
            CaptureStep.Signature -> CaptureStep.Confirm
            CaptureStep.Confirm -> CaptureStep.Confirm
        }
        refreshState()
        return true
    }

    fun previousStep() {
        formError = null
        step = when (step) {
            CaptureStep.Confirm -> CaptureStep.Signature
            CaptureStep.Signature -> CaptureStep.Photos
            CaptureStep.Photos -> CaptureStep.Photos
        }
        refreshState()
    }

    /** Nhảy tới bước đã xong (bấm vào số bước trên thanh tiến trình). */
    fun goToStep(target: CaptureStep) {
        if (target.ordinal > step.ordinal && stepBlockers() != null) return
        formError = null
        step = target
        refreshState()
    }

    /** Lý do CHƯA được sang bước sau của bước hiện tại (null = được đi). */
    fun stepBlockers(): String? {
        val draft = draft()
        return when (step) {
            CaptureStep.Photos -> when {
                !draft.hasHangHoa -> "Cần chọn ảnh Hàng hoá trước khi sang bước chữ ký."
                !draft.hasKienHang -> "Cần chọn ảnh Kiện hàng trước khi sang bước chữ ký."
                else -> null
            }

            CaptureStep.Signature -> if (draft.hasSignature) {
                null
            } else {
                "Cần ký biên nhận trước khi sang bước xác nhận."
            }

            CaptureStep.Confirm -> null
        }
    }

    // ------------------------------------------------------------------ GỬI

    /**
     * "Gửi & xác nhận" — CHỈ gửi khi đủ ảnh + chữ ký + số kiện + kg (bất biến an toàn C6-a).
     *
     * RETRY C5: nút "Thử lại" của `StateHost` gọi LẠI chính hàm này. Vì [session] không bị
     * tạo lại, `clientUuid` y hệt lần trước ⇒ backend trả `duplicate: true` thay vì tạo bộ ảnh
     * thứ hai (đọc `driver.py::capture`). Dữ liệu (ảnh, chữ ký, số liệu) cũng còn nguyên
     * trong ViewModel nên không phải chụp/ký lại.
     */
    fun submit(scope: CoroutineScope) {
        if (state is UiState.Loading) return

        val draft = draft()
        val numbers = CaptureValidation.numbers(draft)
        val lyDoChan = CaptureValidation.blockers(draft)
        if (lyDoChan.isNotEmpty() || numbers == null) {
            formError = lyDoChan.firstOrNull() ?: "Thiếu số liệu bắt buộc."
            return
        }
        val photo = primaryPhoto()?.jpegBytes
        if (photo == null) {
            formError = "Thiếu ảnh Hàng hoá (bắt buộc)."
            return
        }

        formError = null
        state = UiState.Loading
        scope.launch {
            // Xuất PNG chữ ký ở luồng nền (ImageIO) — không chặn giao diện.
            val signaturePng = withContext(Dispatchers.Default) { signature.toPngBytes() }
            if (signaturePng == null) {
                // Phòng thủ (blockers đã chặn trước): khung vẽ chưa được đo ⇒ không có PNG.
                // KHÔNG để người dùng kẹt ở màn lỗi: quay về nội dung, dữ liệu còn nguyên.
                formError = "Chưa có chữ ký (hoặc pad chưa vẽ xong) — không thể gửi biên nhận."
                state = UiState.Content(Unit)
                return@launch
            }

            val result = work.capture(
                jobId = job.id,
                submission = CaptureSubmission(
                    photoJpeg = photo,
                    signaturePng = signaturePng,
                    packages = numbers.packages,
                    weightKg = numbers.weightKg,
                    clientUuid = session.clientUuid,
                    note = noteText.trim().takeIf { it.isNotEmpty() },
                ),
            )
            result.fold(
                onSuccess = { change ->
                    submitted = CaptureSuccess(
                        jobId = job.id,
                        ref = job.ref,
                        packages = numbers.packages,
                        weightKg = numbers.weightKg,
                        state = change.state,
                        stateLabel = change.stateLabel
                            ?: change.state?.let { stateLabelOf(it) }
                            ?: "đã cập nhật",
                        duplicate = change.duplicate,
                    )
                    state = UiState.Content(Unit)
                },
                onFailure = { error ->
                    // Giữ nguyên mọi thứ đã nhập; "Thử lại" gọi lại submit() với CÙNG clientUuid.
                    state = UiState.Error(
                        message = error.toVietnameseMessage(),
                        retry = { submit(scope) },
                    )
                },
            )
        }
    }

    // ------------------------------------------------------------------ nội bộ

    /**
     * `Empty` ⇄ `Content` theo dữ liệu đã có. KHÔNG đụng tới `Loading`/`Error`: đang gửi hoặc
     * đang báo lỗi thì chỉ có hành động của người dùng mới đổi được trạng thái.
     */
    private fun refreshState() {
        if (state is UiState.Loading || state is UiState.Error) return
        state = if (draft().hasAnyEvidence) UiState.Content(Unit) else UiState.Empty
    }

    private companion object {
        /** Ghi chú hiện trường tối đa — đủ cho một câu nghiệp vụ, không phải ô "viết văn". */
        const val MAX_NOTE_LENGTH = 300
    }
}
