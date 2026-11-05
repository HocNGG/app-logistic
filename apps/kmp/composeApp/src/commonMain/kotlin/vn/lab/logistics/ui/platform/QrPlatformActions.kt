package vn.lab.logistics.ui.platform

/**
 * =====================================================================================
 * TÁC VỤ HỆ ĐIỀU HÀNH CHO TEM MÃ QR (`expect` ở `commonMain`):
 *
 *  1. [printQrLabels]           — Mở trình quản lý in của hệ điều hành (Android `PrintManager` /
 *                                 Desktop `PrinterJob`) để in tem QR hoặc xuất PDF.
 *  2. [saveQrLabelsAsImage]     — Vẽ toàn bộ phiếu tem QR ra ảnh PNG sắc nét và lưu về máy
 *                                 (Android: lưu vào Bộ sưu tập `Pictures/AppLogistics`;
 *                                 Desktop: hộp thoại `Save File`).
 *  3. [scanQrCodeWithLiveCamera]— Mở Camera thời gian thực (giống máy quét mã QR siêu thị: khung
 *                                 ngắm trực tiếp, tia quét laser, đèn flash, tự động bíp và chỉ
 *                                 chấp nhận khi camera giải mã được đúng mã QR thật).
 * =====================================================================================
 */

data class PrintableQrLabel(
    val header: String,
    val qrContent: String,
    val primaryCode: String,
    val secondaryCode: String? = null,
    val subtitle: String,
    val meta: String = "",
    val indexLabel: String,
)

expect suspend fun printQrLabels(
    jobTitle: String,
    labels: List<PrintableQrLabel>,
): Result<String>

expect suspend fun saveQrLabelsAsImage(
    filePrefix: String,
    labels: List<PrintableQrLabel>,
): Result<String>

expect suspend fun scanQrCodeWithLiveCamera(
    expectedCodes: List<String>? = null,
): Result<String?>

