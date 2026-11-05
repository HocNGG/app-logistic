package vn.lab.logistics.core.net

import android.security.NetworkSecurityPolicy
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import vn.lab.logistics.ANDROID_DEFAULT_BASE_URL

/**
 * =====================================================================================
 * Bản `actual` cho ANDROID: **engine OkHttp** + 2 dòng chẩn đoán trong logcat.
 *
 * Desktop dùng CIO (`OdooHttp.desktop.kt`) vì CIO thuần Kotlin và chạy ngay trên Windows.
 * Android thì dùng OkHttp — engine được khuyến nghị cho nền tảng này:
 *  - đi kèm sẵn trên hệ sinh thái Android, xử lý TLS/HTTP2 của hệ điều hành;
 *  - tôn trọng cấu hình proxy/DNS của máy (máy ảo gọi `http://10.0.2.2:8069` qua NAT);
 *  - đã được kiểm chứng với `HttpCookies` — vẫn giữ được `session_id` mà Odoo 17 CHỈ trả
 *    trong `Set-Cookie` (docs/02 §6.3).
 *
 * ĐIỀU QUAN TRỌNG: KHÔNG cấu hình lại plugin ở đây. Toàn bộ plugin (cookie jar, JSON,
 * timeout 15s/5s) nằm ở `commonMain` ([installOdooPlugins]) để desktop, Android và test dùng
 * CÙNG một cấu hình — chỉ engine là khác theo nền tảng.
 *
 * Vòng đời: `App()` (commonMain) tạo MỘT client bằng `remember` và dùng cho tới khi Activity
 * bị huỷ hẳn; OkHttp tự giải phóng connection pool của nó.
 *
 * CHẨN ĐOÁN (thêm sau lỗi "Không kết nối được Odoo ở http://10.0.2.2:8069" mà log Odoo KHÔNG
 * thấy request nào): khi tạo client, in ra logcat engine thật + chính sách cleartext của hệ
 * thống cho host mặc định + proxy hệ thống. Đọc bằng: `adb logcat -s AppLogictics`.
 * =====================================================================================
 */
actual fun newOdooHttpClient(): HttpClient {
    // 1. Chẩn đoán môi trường mạng TRƯỚC khi tạo client (chỉ tốn 1 dòng log).
    logAndroidNetworkDiagnostics()

    // 2. Tạo client. Nếu bước này ném lỗi thì app không bao giờ gọi được Odoo ⇒ phải có log,
    //    nếu không người dùng chỉ thấy "không kết nối được" cho mọi request.
    return try {
        HttpClient(OkHttp) {
            installOdooPlugins()
        }.also { client ->
            logTechnicalInfo(
                APP_LOG_TAG,
                "HttpClient đã tạo · engine=${client.engine.javaClass.name}",
            )
        }
    } catch (e: Throwable) {
        logTechnicalError(APP_LOG_TAG, "Tạo HttpClient(OkHttp) thất bại", e)
        throw e
    }
}

/**
 * In ra logcat: chính sách cleartext + proxy hệ thống cho host mặc định của app.
 *
 * Vì sao đúng hai thứ này: một request HTTP thường (không TLS) tới `10.0.2.2:8069` có thể bị chặn
 * ở ĐÚNG hai chỗ mà CẢ HAI đều không để lại dấu vết phía máy chủ Odoo:
 *  1. **cleartext bị chặn** (network security config / `usesCleartextTraffic`) → OkHttp ném
 *     `UnknownServiceException: CLEARTEXT communication … not permitted` TRƯỚC khi mở socket;
 *  2. **proxy hệ thống** (máy ảo khởi động với `-http-proxy`) → OkHttp đi qua proxy nên request
 *     không bao giờ tới `10.0.2.2`; còn `toybox nc` trong shell máy ảo KHÔNG dùng proxy ⇒ hai
 *     phép thử cho kết quả khác nhau dù mạng vẫn "thông".
 * Không đọc được thông tin nào ⇒ chỉ mất một dòng chẩn đoán, app vẫn chạy.
 */
private fun logAndroidNetworkDiagnostics() {
    runCatching {
        val uri = URI(ANDROID_DEFAULT_BASE_URL)
        val host = uri.host ?: "?"
        val policy = NetworkSecurityPolicy.getInstance()
        val proxies: List<Proxy> = ProxySelector.getDefault()?.select(uri) ?: emptyList()
        val proxyText = if (proxies.isEmpty()) {
            "không có (kết nối trực tiếp)"
        } else {
            proxies.joinToString(separator = " | ") { proxy ->
                val address = proxy.address()
                val detail = if (address is InetSocketAddress) {
                    "${address.hostString}:${address.port}"
                } else {
                    address?.toString() ?: "?"
                }
                "${proxy.type()} $detail"
            }
        }
        logTechnicalInfo(
            APP_LOG_TAG,
            "chẩn đoán mạng · host=$host" +
                " · cleartextChoHost=${policy.isCleartextTrafficPermitted(host)}" +
                " · cleartextToanCuc=${policy.isCleartextTrafficPermitted()}" +
                " · proxyHeThong=$proxyText",
        )
    }.onFailure { error ->
        logTechnicalError(APP_LOG_TAG, "Không đọc được chẩn đoán mạng Android", error)
    }
}
