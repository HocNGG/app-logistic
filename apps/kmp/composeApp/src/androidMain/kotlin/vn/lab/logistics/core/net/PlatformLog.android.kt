package vn.lab.logistics.core.net

import android.security.NetworkSecurityPolicy
import android.util.Log
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

/**
 * Bản `actual` cho ANDROID: `android.util.Log` (đọc bằng `adb logcat -s AppLogictics`).
 *
 * `Log.e` tự in `Throwable.toString()` + stacktrace ⇒ thấy ngay exception THẬT đằng sau câu
 * tiếng Việt "Không kết nối được Odoo ở …":
 *  - `java.net.UnknownServiceException: CLEARTEXT communication … not permitted` ⇒ bị chặn cleartext;
 *  - `java.net.SocketTimeoutException` / `Failed to connect to /…` ⇒ sai đường mạng hoặc proxy;
 *  - `NoClassDefFoundError` / `NoSuchMethodError` ⇒ lệch phiên bản thư viện.
 */
actual fun logTechnicalInfo(tag: String, message: String) {
    Log.i(tag, message)
}

actual fun logTechnicalError(tag: String, message: String, error: Throwable?) {
    if (error == null) {
        Log.e(tag, message)
    } else {
        Log.e(tag, message, error)
    }
}

/**
 * Log lỗi một lời gọi HTTP + **tự kiểm mạng độc lập với OkHttp** (xem KDoc của `expect`).
 *
 * Bài học 2026-10-28: câu "Không kết nối được Odoo ở http://10.0.2.2:8069" xuất hiện mà log Odoo
 * KHÔNG có request nào; `toybox nc` từ shell máy ảo thì nối được. Chạy thêm một socket TCP từ
 * CHÍNH tiến trình app là cách duy nhất phân biệt "mạng app bị chặn" và "OkHttp/cleartext chặn"
 * mà không phải sửa code rồi build lại nhiều lần.
 *
 * Ba dòng log phát ra khi lỗi:
 *  1. `✗ <url> thất bại: <exception>` + stacktrace;
 *  2. `chẩn đoán cleartext: host=… cleartextChoHost=…` (đọc từ NetworkSecurityPolicy);
 *  3. `tự kiểm TCP host:port → THÀNH CÔNG/THẤT BẠI sau N ms` (chạy ở thread riêng, không chặn
 *     coroutine đang báo lỗi; timeout 3s).
 */
actual fun logNetworkFailure(url: String, error: Throwable) {
    Log.e(APP_LOG_TAG, "✗ $url thất bại: $error", error)

    val uri = runCatching { URI(url) }.getOrNull()
    val host = uri?.host
    if (host.isNullOrBlank()) {
        Log.e(APP_LOG_TAG, "không tách được host từ URL để tự kiểm mạng: $url")
        return
    }
    val port = uri.port.takeIf { it > 0 } ?: DEFAULT_HTTP_PORT

    // Chẩn đoán 1: chính sách cleartext của hệ thống cho ĐÚNG host này (OkHttp gọi hàm này
    // trước khi mở socket — chặn ở đây thì máy chủ không bao giờ thấy request).
    runCatching {
        val permitted = NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(host)
        Log.i(
            APP_LOG_TAG,
            "chẩn đoán cleartext: host=$host · cleartextChoHost=$permitted" +
                " · cleartextToanCuc=${NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted()}",
        )
    }.onFailure { Log.e(APP_LOG_TAG, "không đọc được chính sách cleartext", it) }

    // Chẩn đoán 2: socket TCP thật, KHÔNG đi qua OkHttp/HTTP ⇒ tách bạch mạng và HTTP client.
    Thread {
        val startedAt = System.nanoTime()
        val reachable = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), TCP_PROBE_TIMEOUT_MS)
            }
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        if (reachable.isSuccess) {
            Log.i(
                APP_LOG_TAG,
                "tự kiểm TCP $host:$port → THÀNH CÔNG sau ${elapsedMs}ms " +
                    "⇒ mạng THÔNG, lỗi nằm ở tầng HTTP (cleartext/OkHttp/engine), không phải mạng",
            )
        } else {
            Log.e(
                APP_LOG_TAG,
                "tự kiểm TCP $host:$port → THẤT BẠI sau ${elapsedMs}ms " +
                    "⇒ không mở được kết nối từ CHÍNH app (mạng/đường đi/proxy)",
                reachable.exceptionOrNull(),
            )
        }
    }.start()
}

/** Cổng mặc định khi URL không ghi cổng (Odoo dev luôn dùng 8069). */
private const val DEFAULT_HTTP_PORT = 8069

/** Timeout của phép tự kiểm socket — đủ để phát hiện "treo", không làm log chậm. */
private const val TCP_PROBE_TIMEOUT_MS = 3_000
