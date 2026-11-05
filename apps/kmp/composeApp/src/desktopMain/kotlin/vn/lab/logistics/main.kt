package vn.lab.logistics

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import vn.lab.logistics.core.settings.AppSettings
import vn.lab.logistics.core.settings.FileSettingsStorage
import vn.lab.logistics.design.Tokens

/**
 * ĐIỂM VÀO CỦA APP DESKTOP.
 *
 * Đây là toàn bộ "phép thuật": `application { Window { ... } }` tạo ra MỘT CỬA SỔ
 * trên hệ điều hành. Không có server, không có cổng, không có trình duyệt.
 *
 * Vì vậy muốn "xem app" thì chạy lệnh Gradle `run` và nhìn cửa sổ này.
 * Xem docs/10-kmp-chay-the-nao.md để hiểu vì sao.
 *
 * Wave B: đây cũng là chỗ DUY NHẤT cắm bản lưu cấu hình ra tệp (`FileSettingsStorage`).
 * `AppSettings` chỉ ghi các khoá không bí mật (địa chỉ máy chủ, DB, tài khoản, token 8 giờ…);
 * mật khẩu/API key không bao giờ xuống đĩa.
 *
 * Trước khi mở cửa sổ, chạy [runSelfCheck] — in ra console 9 khối kiểm tra (WCAG AA, ba ngưỡng
 * duo UI, luật lineHeight tiếng Việt, 7 mã state, brand palette, hình học logo, parse payload
 * THẬT của `driver/jobs`, grep "không hardcode URL/token", và **bất biến an toàn CAPTURE**:
 * không gửi khi thiếu ảnh/chữ ký, ảnh nén ≤ 5 MB, `clientUuid` giữ nguyên khi thử lại).
 * Muốn chạy riêng mà không mở cửa sổ: `gradlew.bat --offline :composeApp:selfCheck`.
 */
fun main() {
    runSelfCheck()

    // Tham số mặc định của `FileSettingsStorage()` = `~/.applogictics/session.properties`.
    val settings = AppSettings(FileSettingsStorage())

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "AppLogictics — Gom hàng lẻ",
            state = rememberWindowState(
                width = Tokens.windowDefaultWidth,
                height = Tokens.windowDefaultHeight,
            ),
        ) {
            App(settings)
        }
    }
}
