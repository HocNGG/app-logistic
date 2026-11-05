package vn.lab.logistics.core.net

/**
 * =====================================================================================
 * GHI LOG KỸ THUẬT — phần DÙNG CHUNG, phần thân theo nền tảng.
 *
 * VÌ SAO PHẢI CÓ (bài học thật, 2026-10-28):
 * Lần chạy đầu tiên trên máy ảo Android, bấm "Đăng nhập" chỉ hiện câu tiếng Việt
 * "Không kết nối được Odoo ở http://10.0.2.2:8069 …" — đúng theo Luật 6 (người dùng không
 * thấy stacktrace), nhưng **không có cách nào biết** exception thật là gì: `ApiException.Network`
 * bọc `Throwable` nên mất hết chi tiết, và log Odoo không có request nào ⇒ không phân biệt được
 * "bị chặn cleartext", "sai proxy của máy ảo" hay "lỗi engine".
 *
 * Nguyên tắc:
 *  - Người dùng: vẫn chỉ thấy câu tiếng Việt (không đổi `toVietnameseMessage`).
 *  - Dev/Orchestrator: đọc **logcat** (Android, tag `AppLogictics`) hoặc `stderr` (desktop).
 *  - **KHÔNG BAO GIỜ** log mật khẩu / API key / token / body request: chỉ log method + URL +
 *    mã HTTP + `Throwable.toString()` (tự mang tên class + message).
 * =====================================================================================
 */

/** Ghi mốc kỹ thuật (bắt đầu/kết thúc một lời gọi mạng). Không bao giờ chứa bí mật. */
expect fun logTechnicalInfo(tag: String, message: String)

/**
 * Ghi lỗi kỹ thuật kèm stacktrace.
 * @param error `null` khi chỉ muốn ghi một dòng lỗi không có exception.
 */
expect fun logTechnicalError(tag: String, message: String, error: Throwable? = null)

/**
 * Ghi lỗi của MỘT lời gọi HTTP tới [url] (đây là chỗ sinh ra câu "Không kết nối được Odoo ở …").
 *
 * Khác [logTechnicalError] ở chỗ: bản Android còn **tự kiểm mạng độc lập với OkHttp** — mở một
 * socket TCP thật tới `host:port` của [url] rồi ghi kết quả ra logcat, kèm chính sách cleartext
 * của hệ thống cho host đó. Nhờ vậy một lần build là phân biệt được ba nguyên nhân hoàn toàn
 * khác nhau mà trước đây đều chỉ hiện một câu giống nhau:
 *   1. mạng/đường đi bị chặn (socket tự kiểm THẤT BẠI);
 *   2. cleartext bị chặn (`cleartextChoHost=false` nhưng socket THÀNH CÔNG);
 *   3. lỗi phía HTTP client/engine (socket THÀNH CÔNG, `cleartextChoHost=true`, exception lạ).
 *
 * @param url URL tuyệt đối đã dựng (base + path) — KHÔNG chứa thông tin đăng nhập.
 * @param error exception thật đã bị bọc vào `ApiException.Network`.
 */
expect fun logNetworkFailure(url: String, error: Throwable)

/** Tag logcat/thẻ log dùng chung cho mọi tầng của app (Orchestrator: `adb logcat -s AppLogictics`). */
const val APP_LOG_TAG: String = "AppLogictics"
