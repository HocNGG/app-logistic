package vn.lab.logistics.feature.capture

import kotlin.random.Random

/**
 * =====================================================================================
 * C5 — PHIÊN CAPTURE + `clientUuid` CHỐNG TRÙNG.
 *
 * SỰ THẬT CỦA BACKEND (đọc `addons/lab_api/controllers/driver.py::capture`, không đoán):
 * ```python
 * if client_uuid and client_uuid == job.lab_client_uuid and job.state in ("picked_up", "confirmed"):
 *     return ok({... "duplicate": True})      # KHÔNG tạo bộ ảnh thứ hai
 * ```
 * ⇒ `clientUuid` là **idempotency key**: mất mạng giữa đường / người dùng bấm 2 lần, app gửi
 * lại **cùng** uuid thì server trả kết quả cũ thay vì tạo chứng từ thứ hai (Luật 4 — không
 * tạo bản ghi rác). Ngược lại, gửi **uuid mới** cho cùng job sau khi đã `picked_up` sẽ nhận
 * `409 INVALID_STATE` ⇒ mất luôn đường thử lại.
 *
 * LUẬT CỦA APP:
 *  1. Sinh **một** uuid khi bắt đầu một phiên capture (mở màn capture cho một job).
 *  2. Bấm **"Thử lại"** sau lỗi mạng ⇒ **GIỮ NGUYÊN** uuid (đó là toàn bộ giá trị của nó).
 *  3. Chỉ sinh uuid **mới** khi người dùng **bắt đầu capture mới** (job khác, hoặc hoàn tất
 *     rồi mở lại màn capture lần nữa).
 *
 * Toàn bộ luật nằm ở [openCaptureSession] — hàm THUẦN, test được ở `commonTest` (C6).
 * =====================================================================================
 */

/** Một phiên capture gắn với đúng một việc; [clientUuid] sống suốt phiên. */
data class CaptureSession(
    val jobId: Int,
    val clientUuid: String,
)

/**
 * Quyết định uuid của phiên capture.
 *
 * @param current phiên đang có (nếu màn capture vừa bị dựng lại / người dùng bấm Thử lại).
 * @param jobId việc sắp capture.
 * @param newUuid hàm sinh uuid — tham số hoá để test và để self-check dùng giá trị cố định.
 * @return **chính** [current] khi vẫn cùng việc (⇒ retry không đổi uuid), ngược lại phiên mới.
 */
fun openCaptureSession(
    current: CaptureSession?,
    jobId: Int,
    newUuid: () -> String = { newClientUuid() },
): CaptureSession {
    if (current != null && current.jobId == jobId) return current
    return CaptureSession(jobId = jobId, clientUuid = newUuid())
}

/** Khuôn UUID phiên bản 4 (chữ thường) — đúng thứ backend nhận vào cột `lab_client_uuid`. */
private val UUID_V4_PATTERN =
    Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

/**
 * Sinh UUID v4 bằng `kotlin.random` — KHÔNG thêm thư viện, KHÔNG dùng `kotlin.uuid.Uuid`
 * (còn thực nghiệm ở nhiều bản Kotlin, bật lên là thêm opt-in cho cả file).
 *
 * Vị trí bit là theo RFC 4122: 4 bit `version` = 0100 tại byte 6, 2 bit `variant` = 10 tại byte 8.
 */
fun newClientUuid(random: Random = Random.Default): String {
    val bytes = ByteArray(16) { random.nextInt(256).toByte() }
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()

    val hex = "0123456789abcdef"
    val builder = StringBuilder(36)
    for (index in bytes.indices) {
        if (index == 4 || index == 6 || index == 8 || index == 10) builder.append('-')
        val value = bytes[index].toInt() and 0xFF
        builder.append(hex[value ushr 4])
        builder.append(hex[value and 0x0F])
    }
    return builder.toString()
}

/** `true` nếu [value] là UUID v4 hợp lệ. Self-check C6-d dùng chính hàm này. */
fun isValidClientUuid(value: String): Boolean = UUID_V4_PATTERN.matches(value)
