package vn.lab.logistics

import android.os.Build

/**
 * =====================================================================================
 * Bản `actual` cho ANDROID của [platformInfo] — hiện ở màn Welcome và màn Đăng nhập
 * (`platformInfo()`), nhờ vậy khi quay video demo thấy ngay app đang chạy trên máy ảo nào.
 *
 * Ví dụ in ra: `Android 17 (API 37) · Google sdk_gphone64_x86_64`
 * =====================================================================================
 */
actual fun platformInfo(): String =
    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
        "${Build.MANUFACTURER} ${Build.MODEL}"

/**
 * =====================================================================================
 * Bản `actual` cho ANDROID của [platformDefaults].
 *
 * BA KHÁC BIỆT SO VỚI DESKTOP (và lý do):
 *
 * 1. `baseUrl = http://10.0.2.2:8069` — trong Android Emulator, `10.0.2.2` là ĐỊA CHỈ ALIAS
 *    của loopback MÁY HOST (QEMU NAT). Máy ảo KHÔNG thấy `localhost` của máy thật: `localhost`
 *    trong máy ảo là chính máy ảo ⇒ gọi Odoo ở đó sẽ "connection refused".
 *    ⇒ **Cách đổi địa chỉ (không sửa code):** mở app → tab **Đăng nhập** → ô "Máy chủ Odoo",
 *      nhập `http://<IP-lan-máy-thật>:8069` (hoặc `http://10.0.2.2:8069` khi dùng máy ảo).
 *      Giá trị được lưu bền vào SharedPreferences (xem `FileSettingsStorage.android.kt`).
 *      Muốn đổi MẶC ĐỊNH thì sửa đúng hằng [ANDROID_DEFAULT_BASE_URL] dưới đây.
 *    ⚠️ ASSUMPTION: cổng 8069 của Odoo được công bố trên máy host. Orchestrator đã đo được
 *    `HTTP/1.0 200 OK` từ máy ảo tới `http://10.0.2.2:8069` ⇒ giả định này đã đúng trên máy này.
 *
 * 2. `demoStartWidthDp = 400` — desktop mở ở 1280dp (2 pane rộng). Trên Pixel_7 (1080×2400,
 *    density 420 ⇒ ~411dp bề rộng), khung 1280dp sẽ tràn và phải cuộn ngang ⇒ không dùng được.
 *    400dp nằm gọn trong màn hình và rơi vào ngưỡng `< 900dp` của duo UI, tức là màn hình
 *    Android trình diễn đúng "1 pane + điều hướng" — vẫn kéo được thanh "Bề rộng giả lập"
 *    trong thanh demo để chứng minh 3 ngưỡng.
 *
 * 3. `imagePickerRunsOnCallerMainThread = false` — bộ chọn ảnh của Android là một ACTIVITY
 *    khác (DocumentsUI) và kết quả chỉ được trả về trên MAIN THREAD. Hàm `pickImageFile()`
 *    là hàm CHẶN (giữ nguyên chữ ký `expect` cho desktop), nên nếu gọi nó trên main thread
 *    thì callback không bao giờ chạy ⇒ treo. Vì vậy `DriverCaptureViewModel.pick()` đọc cờ
 *    này và đẩy lời gọi sang `Dispatchers.Default` trên Android (desktop vẫn gọi trực tiếp
 *    trên EDT vì `java.awt.FileDialog` bắt buộc chạy ở EDT).
 * =====================================================================================
 */
actual fun platformDefaults(): PlatformDefaults = PlatformDefaults(
    baseUrl = ANDROID_DEFAULT_BASE_URL,
    demoStartWidthDp = ANDROID_DEMO_START_WIDTH_DP,
    imagePickerRunsOnCallerMainThread = false,
    // App tài xế trên điện thoại là APP THẬT: mở ra phải gọi Odoo ngay, không bắt người dùng
    // đi tìm công tắc "Nguồn dữ liệu". Người dùng đổi lựa chọn thì được lưu bền.
    useRealApiByDefault = true,
    // Điện thoại có camera: tài xế chụp ảnh bằng chứng trực tiếp tại shop (ngoài chọn từ thư viện).
    cameraCaptureSupported = true,
)

/** Đồng hồ của Android — dùng để kiểm hạn token (`AppSettings.hasValidSession`). */
actual fun nowEpochMillis(): Long = System.currentTimeMillis()

/**
 * `10.0.2.2` = loopback của MÁY HOST nhìn từ Android Emulator (QEMU). Đây là chỗ DUY NHẤT
 * trong `androidMain` chứa literal địa chỉ — mọi nơi khác lấy qua `AppSettings.baseUrl`.
 *
 * `internal` (không `private`) để `core/net/OdooHttp.android.kt` dùng lại ĐÚNG địa chỉ này khi
 * in chẩn đoán cleartext/proxy ra logcat — không chép literal lần thứ hai.
 */
internal const val ANDROID_DEFAULT_BASE_URL: String = "http://10.0.2.2:8069"

/** Bề rộng khung giả lập lúc mở app — vừa màn hình Pixel_7 (~411dp), thuộc ngưỡng 1 pane. */
private const val ANDROID_DEMO_START_WIDTH_DP: Int = 400
