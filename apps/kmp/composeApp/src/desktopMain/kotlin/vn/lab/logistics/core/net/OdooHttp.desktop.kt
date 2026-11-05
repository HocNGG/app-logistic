package vn.lab.logistics.core.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

/**
 * Bản `actual` cho desktop: **engine CIO** (thuần Kotlin/JVM, không cần native lib như OkHttp
 * hay Curl → chạy được ngay trên Windows để demo).
 *
 * Mọi plugin (cookie jar, JSON, timeout) nằm ở `commonMain` (`installOdooPlugins`) để app và
 * test dùng CÙNG một cấu hình — chỉ engine là khác theo nền tảng.
 *
 * Vòng đời: app desktop tạo **một** client duy nhất (`App.kt`, `remember`) và dùng cho tới khi
 * đóng cửa sổ; mỗi `remember` mới sẽ tạo client mới và cái cũ được JVM thu hồi cùng tiến trình.
 */
actual fun newOdooHttpClient(): HttpClient = HttpClient(CIO) {
    installOdooPlugins()
}
